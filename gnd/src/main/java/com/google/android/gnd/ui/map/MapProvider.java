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

package com.google.android.gnd.ui.map;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.common.collect.ImmutableMap;
import io.reactivex.Single;

/**
 * Common interface for various map provider libraries.
 *
 * <p>Map Type refers to the basemap shown below map features and offline satellite imagery. It's
 * called "map styles" in Mapbox and "basemaps" in Leaflet.
 */
public interface MapProvider {
  void restore(Fragment fragment);

  @Nullable
  Fragment getFragment();

  @Nullable
  Single<MapAdapter> getMapAdapter();

  int getMapType();

  // TODO: Use ENUM instead of int with a superset of basemap types.
  //  https://github.com/google/ground-android/pull/406#discussion_r398726351
  void setMapType(int mapType);

  ImmutableMap<Integer, String> getMapTypes();
}
