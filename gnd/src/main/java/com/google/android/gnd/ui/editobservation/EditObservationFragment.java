/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.ui.editobservation;

import static com.google.android.gnd.ui.editobservation.AddPhotoDialogAdapter.PhotoStorageResource.PHOTO_SOURCE_CAMERA;
import static com.google.android.gnd.ui.editobservation.AddPhotoDialogAdapter.PhotoStorageResource.PHOTO_SOURCE_STORAGE;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gnd.MainActivity;
import com.google.android.gnd.R;
import com.google.android.gnd.databinding.EditObservationBottomSheetBinding;
import com.google.android.gnd.databinding.EditObservationFragBinding;
import com.google.android.gnd.databinding.MultipleChoiceInputFieldBinding;
import com.google.android.gnd.databinding.PhotoInputFieldBinding;
import com.google.android.gnd.databinding.TextInputFieldBinding;
import com.google.android.gnd.model.form.Element;
import com.google.android.gnd.model.form.Element.Type;
import com.google.android.gnd.model.form.Field;
import com.google.android.gnd.model.form.Form;
import com.google.android.gnd.model.form.MultipleChoice.Cardinality;
import com.google.android.gnd.model.observation.Response;
import com.google.android.gnd.model.observation.TextResponse;
import com.google.android.gnd.ui.common.AbstractFragment;
import com.google.android.gnd.ui.common.BackPressListener;
import com.google.android.gnd.ui.common.EphemeralPopups;
import com.google.android.gnd.ui.common.Navigator;
import com.google.android.gnd.ui.common.TwoLineToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import dagger.hilt.android.AndroidEntryPoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java8.util.Optional;
import java8.util.function.Consumer;
import javax.inject.Inject;
import timber.log.Timber;

@AndroidEntryPoint
public class EditObservationFragment extends AbstractFragment implements BackPressListener {

  private final List<AbstractFieldViewModel> fieldViewModelList = new ArrayList<>();

  @Inject Navigator navigator;
  @Inject FieldViewFactory fieldViewFactory;

  private EditObservationViewModel viewModel;
  @Nullable
  private SingleSelectDialogFactory singleSelectDialogFactory;
  @Nullable
  private MultiSelectDialogFactory multiSelectDialogFactory;
  private EditObservationFragBinding binding;

  @Nullable
  private static AbstractFieldViewModel getViewModel(@Nullable ViewDataBinding binding) {
    if (binding == null) {
      return null;
    } else if (binding instanceof TextInputFieldBinding) {
      return ((TextInputFieldBinding) binding).getViewModel();
    } else if (binding instanceof MultipleChoiceInputFieldBinding) {
      return ((MultipleChoiceInputFieldBinding) binding).getViewModel();
    } else if (binding instanceof PhotoInputFieldBinding) {
      return ((PhotoInputFieldBinding) binding).getViewModel();
    } else {
      throw new IllegalArgumentException("Unknown binding type: " + binding.getClass());
    }
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    singleSelectDialogFactory = new SingleSelectDialogFactory(getContext());
    multiSelectDialogFactory = new MultiSelectDialogFactory(getContext());
    viewModel = getViewModel(EditObservationViewModel.class);
  }

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = EditObservationFragBinding.inflate(inflater, container, false);
    binding.setLifecycleOwner(this);
    binding.setViewModel(viewModel);
    binding.setFragment(this);
    binding.saveObservationBtn.setOnClickListener(this::onSaveClick);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    TwoLineToolbar toolbar = binding.editObservationToolbar;
    ((MainActivity) getActivity()).setActionBar(toolbar, R.drawable.ic_close_black_24dp);
    toolbar.setNavigationOnClickListener(__ -> onCloseButtonClick());
    // Observe state changes.
    viewModel.getForm().observe(getViewLifecycleOwner(), this::rebuildForm);
    viewModel
        .getSaveResults()
        .observe(getViewLifecycleOwner(), e -> e.ifUnhandled(this::handleSaveResult));
    // Initialize view model.
    viewModel.initialize(EditObservationFragmentArgs.fromBundle(getArguments()));
  }

  private void handleSaveResult(@NonNull EditObservationViewModel.SaveResult saveResult) {
    switch (saveResult) {
      case HAS_VALIDATION_ERRORS:
        showValidationErrorsAlert();
        break;
      case NO_CHANGES_TO_SAVE:
        EphemeralPopups.showFyi(getContext(), R.string.no_changes_to_save);
        navigator.navigateUp();
        break;
      case SAVED:
        EphemeralPopups.showSuccess(getContext(), R.string.saved);
        navigator.navigateUp();
        break;
      default:
        Timber.e("Unknown save result type: %s", saveResult);
        break;
    }
  }

  private void addFieldViewModel(@NonNull Field field, @NonNull AbstractFieldViewModel fieldViewModel) {
    fieldViewModel.init(field, viewModel.getSavedOrOriginalResponse(field.getId()));

    if (fieldViewModel instanceof PhotoFieldViewModel) {
      observeSelectPhotoClicks((PhotoFieldViewModel) fieldViewModel);
      observePhotoAdded((PhotoFieldViewModel) fieldViewModel);
    } else if (fieldViewModel instanceof MultipleChoiceFieldViewModel) {
      observeMultipleChoiceClicks((MultipleChoiceFieldViewModel) fieldViewModel);
    }

    fieldViewModel
        .getResponse()
        .observe(this, response -> viewModel.onResponseChanged(field, response));

    fieldViewModelList.add(fieldViewModel);
  }

  public void onSaveClick(@NonNull View view) {
    hideKeyboard(view);
    viewModel.onSave(getValidationErrors());
  }

  private void hideKeyboard(@NonNull View view) {
    if (getActivity() != null) {
      InputMethodManager inputMethodManager =
          (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  @NonNull
  private Map<String, String> getValidationErrors() {
    HashMap<String, String> errors = new HashMap<>();
    for (AbstractFieldViewModel fieldViewModel : fieldViewModelList) {
      fieldViewModel
          .validate()
          .ifPresent(error -> errors.put(fieldViewModel.getField().getId(), error));
    }
    return errors;
  }

  private void rebuildForm(@NonNull Form form) {
    LinearLayout formLayout = binding.editObservationLayout;
    formLayout.removeAllViews();
    fieldViewModelList.clear();
    for (Element element : form.getElementsSorted()) {
      if (element.getType() == Type.FIELD) {
        Field field = element.getField();
        ViewDataBinding binding = fieldViewFactory.addFieldView(field.getType(), formLayout);
        addFieldViewModel(field, getViewModel(binding));
      } else {
        throw new IllegalArgumentException(element.getType() + " elements not yet supported");
      }
    }
  }

  private void observeMultipleChoiceClicks(@NonNull MultipleChoiceFieldViewModel viewModel) {
    viewModel
        .getShowDialogClicks()
        .observe(
            this,
            __ ->
                onShowDialog(
                    viewModel.getField(),
                    viewModel.getResponse().getValue(),
                    viewModel::setResponse));
  }

  private void onShowDialog(
      @NonNull Field field, Optional<Response> currentResponse, Consumer<Optional<Response>> consumer) {
    Cardinality cardinality = field.getMultipleChoice().getCardinality();
    switch (cardinality) {
      case SELECT_MULTIPLE:
        multiSelectDialogFactory.create(field, currentResponse, consumer).show();
        break;
      case SELECT_ONE:
        singleSelectDialogFactory.create(field, currentResponse, consumer).show();
        break;
      default:
        Timber.e("Unknown cardinality: %s", cardinality);
        break;
    }
  }

  private void observeSelectPhotoClicks(@NonNull PhotoFieldViewModel fieldViewModel) {
    fieldViewModel
        .getShowDialogClicks()
        .observe(this, __ -> onShowPhotoSelectorDialog(fieldViewModel.getField()));
  }

  private void observePhotoAdded(@NonNull PhotoFieldViewModel fieldViewModel) {
    viewModel
        .getPhotoFieldUpdates()
        .observe(
            this,
            map -> {
              Field field = fieldViewModel.getField();
              if (map.containsKey(field)) {
                fieldViewModel.setResponse(TextResponse.fromString(map.get(field)));
              }
            });
  }

  private void onShowPhotoSelectorDialog(Field field) {
    EditObservationBottomSheetBinding addPhotoBottomSheetBinding =
        EditObservationBottomSheetBinding.inflate(getLayoutInflater());
    addPhotoBottomSheetBinding.setViewModel(viewModel);
    addPhotoBottomSheetBinding.setField(field);

    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
    bottomSheetDialog.setContentView(addPhotoBottomSheetBinding.getRoot());
    bottomSheetDialog.setCancelable(true);
    bottomSheetDialog.show();

    AddPhotoDialogAdapter.ItemClickListener listener =
        type -> {
          bottomSheetDialog.dismiss();
          switch (type) {
            case PHOTO_SOURCE_CAMERA:
              viewModel.showPhotoCapture(field);
              break;
            case PHOTO_SOURCE_STORAGE:
              viewModel.showPhotoSelector(field);
              break;
            default:
              throw new IllegalArgumentException("Unknown type: " + type);
          }
        };

    RecyclerView recyclerView = addPhotoBottomSheetBinding.recyclerView;
    recyclerView.setHasFixedSize(true);
    recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    recyclerView.setAdapter(new AddPhotoDialogAdapter(listener));
  }

  @Override
  public boolean onBack() {
    if (viewModel.hasUnsavedChanges()) {
      showUnsavedChangesDialog();
      return true;
    }
    return false;
  }

  private void onCloseButtonClick() {
    if (viewModel.hasUnsavedChanges()) {
      showUnsavedChangesDialog();
    } else {
      navigator.navigateUp();
    }
  }

  private void showUnsavedChangesDialog() {
    new AlertDialog.Builder(getContext())
        .setMessage(R.string.unsaved_changes)
        .setPositiveButton(R.string.close_without_saving, (d, i) -> navigator.navigateUp())
        .setNegativeButton(R.string.continue_editing, (d, i) -> {})
        .create()
        .show();
  }

  private void showValidationErrorsAlert() {
    new AlertDialog.Builder(getContext())
        .setMessage(R.string.invalid_data_warning)
        .setPositiveButton(R.string.invalid_data_confirm, (a, b) -> {})
        .create()
        .show();
  }
}
