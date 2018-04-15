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

package com.google.gnd;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.WindowInsetsCompat;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.gnd.model.DataModel;
import com.google.gnd.model.FeatureType;
import com.google.gnd.service.DataService;
import com.google.gnd.system.LocationManager;
import com.google.gnd.system.PermissionManager;
import com.google.gnd.view.AddFeatureDialog;
import com.google.gnd.view.map.GoogleMapsView;
import com.google.gnd.view.sheet.DataSheetScrollView;
import com.google.gnd.view.util.ViewUtil;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import java8.util.function.Consumer;

import static android.support.design.widget.BottomSheetBehavior.STATE_COLLAPSED;
import static android.support.design.widget.BottomSheetBehavior.STATE_EXPANDED;
import static com.google.gnd.system.LocationManager.LocationFailureReason.SETTINGS_CHANGE_FAILED;

public class MainActivity extends AbstractGndActivity {
  private MainPresenter mainPresenter;
  private AddFeatureDialog addFeatureDialog;
  private DataModel model;

  @BindView(R.id.add_feature_btn)
  FloatingActionButton addFeatureBtn;

  private ProgressDialog progressDialog;
  private Menu toolbarMenu;
  private WindowInsetsCompat insets;

  @Inject
  PermissionManager permissionManager;

  @Inject
  DataService dataService;

  @Inject
  LocationManager locationManager;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    this.model = new DataModel(dataService);
    this.addFeatureDialog = new AddFeatureDialog(this);
    this.mainPresenter = new MainPresenter(this, model, permissionManager, locationManager);

    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);
    initToolbar();
    updatePaddingForWindowInsets();
    mainPresenter.onCreate(savedInstanceState);
    View decorView = getWindow().getDecorView();
    if (Build.VERSION.SDK_INT >= 19) {
      // Sheet doesn't scroll properly w/translucent status due to obscure Android bug. This should
      // be resolved once add/edit is in its own fragment that uses fitsSystemWindows. For now we
      // just expand the sheet when focus + layout change (i.e., keyboard appeared).
      decorView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
        View newFocus  = getCurrentFocus();
        if (newFocus != null) {
          DataSheetScrollView dataSheetView = getDataSheetView();
            BottomSheetBehavior behavior = (BottomSheetBehavior) ((CoordinatorLayout.LayoutParams) dataSheetView
                .getLayoutParams())
                .getBehavior();
            if (behavior.getState() == STATE_COLLAPSED) {
              behavior.setState(STATE_EXPANDED);
            }
        }
      });
    }
  }

  private void updatePaddingForWindowInsets() {
    FrameLayout toolbarWrapper = findViewById(R.id.toolbar_wrapper);
    // TODO: Each view should consume its own insets and update the insets for consumption by
    // child views.
    ViewCompat.setOnApplyWindowInsetsListener(
        toolbarWrapper,
        (v, insets) -> {
          MainActivity.this.insets = insets;
          int bottomPadding = insets.getSystemWindowInsetBottom();
          int topPadding = insets.getSystemWindowInsetTop();
          View dataSheetWrapper = findViewById(R.id.data_sheet_wrapper);
          View dataSheetLayout = findViewById(R.id.data_sheet_layout);
          View bottomSheetScrim = findViewById(R.id.bottom_sheet_scrim);
          View mapBtnLayout = findViewById(R.id.map_btn_layout);
          View recordBtnLayout = findViewById(R.id.record_btn_layout);
          dataSheetLayout.setMinimumHeight(
              ViewUtil.getScreenHeight(MainActivity.this) - topPadding);
          dataSheetWrapper.setPadding(0, topPadding, 0, bottomPadding);
          toolbarWrapper.setPadding(0, topPadding, 0, 0);
          bottomSheetScrim.setMinimumHeight(bottomPadding);
          mapBtnLayout.setTranslationY(-bottomPadding);
          recordBtnLayout.setTranslationY(-bottomPadding);
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return insets.replaceSystemWindowInsets(0, 0, 0, insets.getSystemWindowInsetBottom());
          } else {
            return insets;
          }
        });
  }

  public void showProjectLoadingDialog() {
    progressDialog = new ProgressDialog(this);
    progressDialog.setMessage(getResources().getString(R.string.project_loading_please_wait));
    progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    progressDialog.setCancelable(false);
    progressDialog.setCanceledOnTouchOutside(false);
    progressDialog.show();
  }

  public void dismissLoadingDialog() {
    progressDialog.dismiss();
  }

  public void enableAddFeatureButton() {
    addFeatureBtn.setBackgroundTintList(
        ColorStateList.valueOf(getResources().getColor(R.color.colorAccent)));
  }

  public void showUserActionFailureMessage(int resId) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
  }

  public void showErrorMessage(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
  }

  private void initToolbar() {
    setSupportActionBar(getToolbar());
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);
  }

  public boolean onCreateOptionsMenu(Menu menu) {
    toolbarMenu = menu;
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.feature_header_menu, menu);

    return true;
  }

  public Menu getToolbarMenu() {
    return toolbarMenu;
  }

  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.toolbar_save_link:
        return mainPresenter.onToolbarSaveButtonClick();
    }
    return super.onOptionsItemSelected(item);
  }

  public FloatingActionButton getLocationLockButton() {
    return (FloatingActionButton) findViewById(R.id.gps_lock_btn);
  }

  public FloatingActionButton getAddFeatureButton() {
    return addFeatureBtn;
  }

  public FloatingActionButton getAddRecordButton() {
    return (FloatingActionButton) findViewById(R.id.add_record_btn);
  }

  public DataSheetScrollView getDataSheetView() {
    return findViewById(R.id.data_sheet);
  }

  public void hideSoftInput() {
    View view = this.getCurrentFocus();
    if (view != null) {
      InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  public GoogleMapsView getMapView() {
    return (GoogleMapsView) findViewById(R.id.map);
  }

  public Toolbar getToolbar() {
    return findViewById(R.id.toolbar);
  }

  public ViewGroup getToolbarWrapper() {
    return findViewById(R.id.toolbar_wrapper);
  }

  public MenuItem getToolbarSaveButton() {
    return toolbarMenu.findItem(R.id.toolbar_save_link);
  }

  public void showAddFeatureDialog(
      List<FeatureType> featureTypesList, Consumer<FeatureType> onSelect) {
    addFeatureDialog.show(
        featureTypesList,
        ft -> {
          onSelect.accept(ft);
        });
  }

  @Override
  protected void onStart() {
    super.onStart();
    mainPresenter.onStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    mainPresenter.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mainPresenter.onPause();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mainPresenter.onStop();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    mainPresenter.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mainPresenter.onLowMemory();
  }

  /**
   * The Android permissions API requires this callback to live in an Activity; here we dispatch the
   * result back to the PermissionManager for handling.
   *
   * @param requestCode
   * @param permissions
   * @param grantResults
   */
  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    switch (requestCode) {
      case LocationManager.REQUEST_CHECK_SETTINGS:
        switch (resultCode) {
          case Activity.RESULT_OK:
            mainPresenter.getMapPresenter().onLocationLockSettingsIssueResolved();
            break;
          case Activity.RESULT_CANCELED:
            mainPresenter.getMapPresenter().onLocationFailure(SETTINGS_CHANGE_FAILED);
            break;
          default:
            break;
        }
        break;
    }
  }

  public void onReady() {}

  public WindowInsetsCompat getInsets() {
    return insets;
  }
}
