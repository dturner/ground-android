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

package com.google.android.gnd.ui.offlinebasemap.viewer;

import static java8.util.stream.StreamSupport.stream;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import com.google.android.gnd.model.basemap.OfflineBaseMap;
import com.google.android.gnd.model.basemap.tile.TileSource;
import com.google.android.gnd.repository.OfflineBaseMapRepository;
import com.google.android.gnd.ui.common.AbstractViewModel;
import com.google.common.collect.ImmutableSet;
import dagger.hilt.android.qualifiers.ApplicationContext;
import io.reactivex.processors.BehaviorProcessor;
import java.io.File;
import java.lang.ref.WeakReference;
import javax.inject.Inject;

/**
 * View model for the OfflineAreaViewerFragment. Manges offline area deletions and calculates the
 * storage size of an area on the user's device.
 */
public class OfflineBaseMapViewerViewModel extends AbstractViewModel {

  @NonNull
  private final BehaviorProcessor<OfflineBaseMapViewerFragmentArgs> argsProcessor;
  @NonNull
  private final OfflineBaseMapRepository offlineBaseMapRepository;
  @NonNull
  private final WeakReference<Context> context;
  public LiveData<Double> areaStorageSize;
  private LiveData<OfflineBaseMap> offlineArea;

  @Inject
  public OfflineBaseMapViewerViewModel(
      @NonNull OfflineBaseMapRepository offlineBaseMapRepository, @ApplicationContext Context context) {
    this.argsProcessor = BehaviorProcessor.create();
    this.offlineBaseMapRepository = offlineBaseMapRepository;
    this.context = new WeakReference<>(context);
    this.areaStorageSize =
        LiveDataReactiveStreams.fromPublisher(
            this.argsProcessor.switchMap(
                args ->
                    this.offlineBaseMapRepository
                        .getOfflineArea(args.getOfflineAreaId())
                        .toFlowable()
                        .flatMap(
                            offlineBaseMapRepository
                                ::getIntersectingDownloadedTileSourcesOnceAndStream)
                        .map(this::tileSourcesToTotalStorageSize)));
    this.offlineArea =
        LiveDataReactiveStreams.fromPublisher(
            this.argsProcessor.switchMap(
                args ->
                    this.offlineBaseMapRepository
                        .getOfflineArea(args.getOfflineAreaId())
                        .toFlowable()));
  }

  private Double tileSourcesToTotalStorageSize(@NonNull ImmutableSet<TileSource> tileSources) {
    return stream(tileSources).map(this::tileSourceStorageSize).reduce((x, y) -> x + y).orElse(0.0);
  }

  private double tileSourceStorageSize(@NonNull TileSource tileSource) {
    Context context1 = context.get();
    if (context1 == null) {
      return 0.0;
    } else {
      File tileFile = new File(context1.getFilesDir(), tileSource.getPath());
      return (double) tileFile.length() / (1024 * 1024);
    }
  }

  /**
   * Removes the offline area associated with this viewmodel from the device by removing all tile
   * sources that are not included in other areas and removing the area from the db.
   */
  public void onRemoveClick() {
    // TODO: Delete the area.
  }

  /** Returns the offline area associated with this view model. */
  public LiveData<OfflineBaseMap> getOfflineArea() {
    return offlineArea;
  }

  /** Gets a single offline area by the id passed to the OfflineAreaViewerFragment's arguments. */
  public void loadOfflineArea(@NonNull OfflineBaseMapViewerFragmentArgs args) {
    this.argsProcessor.onNext(args);
  }
}
