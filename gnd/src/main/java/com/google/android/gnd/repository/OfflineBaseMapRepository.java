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

package com.google.android.gnd.repository;

import static com.google.android.gnd.util.ImmutableListCollector.toImmutableList;
import static com.google.android.gnd.util.ImmutableSetCollector.toImmutableSet;
import static java8.util.stream.StreamSupport.stream;

import androidx.annotation.NonNull;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gnd.model.Project;
import com.google.android.gnd.model.basemap.OfflineBaseMap;
import com.google.android.gnd.model.basemap.OfflineBaseMap.State;
import com.google.android.gnd.model.basemap.OfflineBaseMapSource;
import com.google.android.gnd.model.basemap.tile.TileSource;
import com.google.android.gnd.persistence.geojson.GeoJsonParser;
import com.google.android.gnd.persistence.local.LocalDataStore;
import com.google.android.gnd.persistence.sync.TileSourceDownloadWorkManager;
import com.google.android.gnd.persistence.uuid.OfflineUuidGenerator;
import com.google.android.gnd.rx.Loadable;
import com.google.android.gnd.rx.Schedulers;
import com.google.android.gnd.system.GeocodingManager;
import com.google.android.gnd.ui.util.FileUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import timber.log.Timber;

public class OfflineBaseMapRepository {
  private final TileSourceDownloadWorkManager tileSourceDownloadWorkManager;
  private final LocalDataStore localDataStore;
  private final ProjectRepository projectRepository;
  private final GeoJsonParser geoJsonParser;
  private final FileUtil fileUtil;
  private final Schedulers schedulers;
  private final GeocodingManager geocodingManager;

  private final OfflineUuidGenerator uuidGenerator;

  @Inject
  public OfflineBaseMapRepository(
      TileSourceDownloadWorkManager tileSourceDownloadWorkManager,
      LocalDataStore localDataStore,
      ProjectRepository projectRepository,
      GeoJsonParser geoJsonParser,
      OfflineUuidGenerator uuidGenerator,
      FileUtil fileUtil,
      Schedulers schedulers,
      GeocodingManager geocodingManager) {
    this.tileSourceDownloadWorkManager = tileSourceDownloadWorkManager;
    this.localDataStore = localDataStore;
    this.geoJsonParser = geoJsonParser;
    this.uuidGenerator = uuidGenerator;
    this.projectRepository = projectRepository;
    this.fileUtil = fileUtil;
    this.schedulers = schedulers;
    this.geocodingManager = geocodingManager;
  }

  /**
   * Download the offline basemap source for the active project.
   *
   * <p>Only the first basemap source is used. Sources are always re-downloaded and overwritten on
   * subsequent calls.
   */
  @NonNull
  private File downloadOfflineBaseMapSource(@NonNull OfflineBaseMapSource offlineBaseMapSource)
      throws IOException {

    URL baseMapUrl = offlineBaseMapSource.getUrl();
    Timber.d("Basemap url: %s, file: %s", baseMapUrl, baseMapUrl.getFile());
    File localFile = fileUtil.getOrCreateFile(baseMapUrl.getFile());

    FileUtils.copyURLToFile(baseMapUrl, localFile);
    return localFile;
  }

  /** Enqueue a single area and its tile sources for download. */
  private Completable enqueueDownload(@NonNull OfflineBaseMap area, @NonNull ImmutableList<TileSource> tileSources) {
    return localDataStore
        .insertOrUpdateOfflineArea(area.toBuilder().setState(State.IN_PROGRESS).build())
        .andThen(
            Completable.merge(
                stream(tileSources.asList())
                    .map(localDataStore::insertOrUpdateTileSource)
                    .collect(toImmutableList())))
        .doOnError(__ -> Timber.e("failed to add/update a tile in the database"))
        .andThen(tileSourceDownloadWorkManager.enqueueTileSourceDownloadWorker());
  }

  /**
   * Determine the tile sources that need to be downloaded for a given area, then enqueue tile
   * source downloads.
   */
  @NonNull
  private Completable enqueueTileSourceDownloads(@NonNull OfflineBaseMap area) {
    return getBaseMapTileSources(area)
        .flatMapCompletable(tileSources -> enqueueDownload(area, tileSources))
        .doOnComplete(() -> Timber.d("area download completed"))
        .doOnError(throwable -> Timber.e(throwable, "failed to download area"))
        .subscribeOn(schedulers.io());
  }

  /**
   * Get a list of tile sources specified in the first basemap source of the active project that
   * intersect a given area.
   */
  @NonNull
  private Single<ImmutableList<TileSource>> getBaseMapTileSources(@NonNull OfflineBaseMap offlineBaseMap) {
    LatLngBounds bounds = offlineBaseMap.getBounds();

    return projectRepository
        .getActiveProjectOnceAndStream()
        .compose(Loadable::values)
        .map(Project::getOfflineBaseMapSources)
        .doOnError(
            throwable -> Timber.e(throwable, "no basemap sources specified for the active project"))
        .map(ImmutableList::asList)
        .flatMap(Flowable::fromIterable)
        .firstOrError()
        .map(this::downloadOfflineBaseMapSource)
        .map(json -> geoJsonParser.intersectingTiles(bounds, json))
        .doOnError(
            throwable ->
                Timber.e(throwable, "couldn't retrieve basemap sources for the active project"));
  }

  @NonNull
  public Completable addAreaAndEnqueue(@NonNull LatLngBounds bounds) {
    return geocodingManager
        .getOfflineAreaName(bounds)
        .map(
            name ->
                OfflineBaseMap.newBuilder()
                    .setBounds(bounds)
                    .setId(uuidGenerator.generateUuid())
                    .setState(State.PENDING)
                    .setName(name)
                    .build())
        .flatMapCompletable(this::enqueueTileSourceDownloads);
  }

  public Flowable<ImmutableList<OfflineBaseMap>> getOfflineAreasOnceAndStream() {
    return localDataStore.getOfflineAreasOnceAndStream();
  }

  public Single<OfflineBaseMap> getOfflineArea(String offlineAreaId) {
    return localDataStore.getOfflineAreaById(offlineAreaId);
  }

  @NonNull
  public Flowable<ImmutableSet<TileSource>> getIntersectingDownloadedTileSourcesOnceAndStream(
      @NonNull OfflineBaseMap offlineBaseMap) {
    return getBaseMapTileSources(offlineBaseMap)
        .flatMapPublisher(
            tiles ->
                getDownloadedTileSourcesOnceAndStream()
                    .map(ts -> stream(ts).filter(tiles::contains).collect(toImmutableSet())))
        // If no tile sources are found, we report the area takes up 0.0mb on the device.
        .doOnError(
            throwable -> Timber.d(throwable, "no tile sources found for area %s", offlineBaseMap))
        .onErrorReturn(__ -> ImmutableSet.of());
  }

  @NonNull
  public Flowable<ImmutableSet<TileSource>> getDownloadedTileSourcesOnceAndStream() {
    return localDataStore
        .getTileSourcesOnceAndStream()
        .map(
            set ->
                stream(set)
                    .filter(tileSource -> tileSource.getState() == TileSource.State.DOWNLOADED)
                    .collect(toImmutableSet()));
  }
}
