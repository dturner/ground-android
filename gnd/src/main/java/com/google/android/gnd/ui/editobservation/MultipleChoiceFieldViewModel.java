/*
 * Copyright 2020 Google LLC
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

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import com.google.android.gnd.rx.Nil;
import javax.inject.Inject;

public class MultipleChoiceFieldViewModel extends AbstractFieldViewModel {

  private final MutableLiveData<Nil> showDialogClicks = new MutableLiveData<>();

  @Inject
  MultipleChoiceFieldViewModel(@NonNull Application application) {
    super(application);
  }

  public void onShowDialog() {
    showDialogClicks.setValue(Nil.NIL);
  }

  @NonNull
  MutableLiveData<Nil> getShowDialogClicks() {
    return showDialogClicks;
  }
}
