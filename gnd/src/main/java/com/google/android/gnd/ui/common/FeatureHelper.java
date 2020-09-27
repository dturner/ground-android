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

package com.google.android.gnd.ui.common;

import android.content.Context;
import androidx.annotation.NonNull;
import com.google.android.gnd.R;
import com.google.android.gnd.model.AuditInfo;
import com.google.android.gnd.model.User;
import com.google.android.gnd.model.feature.Feature;
import com.google.android.gnd.model.layer.Layer;
import java8.util.Optional;

/** Helper class for converting {@link Feature} to string for UI components. */
public class FeatureHelper {

  public static String getCreatedBy(Context context, @NonNull Optional<Feature> feature) {
    return getUserName(feature).map(name -> context.getString(R.string.added_by, name)).orElse("");
  }

  public static String getTitle(@NonNull Optional<Feature> feature) {
    return getCaption(feature).orElseGet(() -> getLayerName(feature).orElse(""));
  }

  private static Optional<String> getUserName(@NonNull Optional<Feature> feature) {
    return feature.map(Feature::getCreated).map(AuditInfo::getUser).map(User::getDisplayName);
  }

  private static Optional<String> getCaption(@NonNull Optional<Feature> feature) {
    return feature.map(Feature::getCaption).map(String::trim).filter(caption -> !caption.isEmpty());
  }

  private static Optional<String> getLayerName(@NonNull Optional<Feature> feature) {
    return feature.map(Feature::getLayer).map(Layer::getName);
  }
}
