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

package com.google.android.gnd.ui.map.gms;

import static com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_DEVELOPER_ANIMATION;
import static java8.util.stream.StreamSupport.stream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import com.cocoahero.android.gmaps.addons.mapbox.MapBoxOfflineTileProvider;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gnd.R;
import com.google.android.gnd.model.feature.Point;
import com.google.android.gnd.persistence.local.LocalValueStore;
import com.google.android.gnd.ui.MarkerIconFactory;
import com.google.android.gnd.ui.map.MapAdapter;
import com.google.android.gnd.ui.map.MapFeature;
import com.google.android.gnd.ui.map.MapGeoJson;
import com.google.android.gnd.ui.map.MapPin;
import com.google.android.gnd.ui.map.MapPolygon;
import com.google.common.collect.ImmutableSet;
import com.google.maps.android.collections.MarkerManager;
import com.google.maps.android.data.Layer;
import com.google.maps.android.data.geojson.GeoJsonLayer;
import com.google.maps.android.data.geojson.GeoJsonLineStringStyle;
import com.google.maps.android.data.geojson.GeoJsonPointStyle;
import com.google.maps.android.data.geojson.GeoJsonPolygonStyle;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import timber.log.Timber;

/**
 * Wrapper around {@link GoogleMap}, exposing Google Maps SDK functionality to Ground as a {@link
 * MapAdapter}.
 */
class GoogleMapsMapAdapter implements MapAdapter {

  // TODO: Make it configurable
  private static final int POLYLINE_STROKE_WIDTH_PX = 12;

  private final GoogleMap map;
  private final Context context;
  private final MarkerIconFactory markerIconFactory;
  private final PublishSubject<MapPin> markerClickSubject = PublishSubject.create();
  private final PublishSubject<Point> dragInteractionSubject = PublishSubject.create();
  private final BehaviorSubject<Point> cameraMoves = BehaviorSubject.create();
  // TODO: This is a limitation of the MapBox tile provider we're using;
  // since one need to call `close` explicitly, we cannot generically expose these as TileProviders;
  // instead we must retain explicit reference to the concrete type.
  private final PublishSubject<MapBoxOfflineTileProvider> tileProviders = PublishSubject.create();

  /**
   * Manager for handling click events for markers.
   *
   * <p>This isn't needed if the map only has a single layer. But in our case, multiple GeoJSON
   * layers might be added and we wish to have independent clickable features for each layer.
   */
  private final MarkerManager markerManager;
  // TODO: Add managers for polyline and polygon layers

  /**
   * References to Google Maps SDK Markers present on the map. Used to sync and update markers with
   * current view and data state.
   */
  private final MarkerManager.Collection markers;

  /**
   * References to Google Maps SDK Markers present on the map. Used to sync and update polylines
   * with current view and data state.
   */
  private Set<Polyline> polylines = new HashSet<>();

  /**
   * References to Google Maps SDK GeoJSON present on the map. Used to sync and update GeoJSON with
   * current view and data state.
   */
  private Set<GeoJsonLayer> geoJsonLayers = new HashSet<>();

  @Nullable private LatLng cameraTargetBeforeDrag;

  public GoogleMapsMapAdapter(
      GoogleMap map, Context context, MarkerIconFactory markerIconFactory,
      LocalValueStore localValueStore) {
    this.map = map;
    this.context = context;
    this.markerIconFactory = markerIconFactory;

    // init markers
    markerManager = new MarkerManager(map);
    markers = markerManager.newCollection();
    markers.setOnMarkerClickListener(this::onMarkerClick);

    map.setMapType(localValueStore.getSavedMapType(GoogleMap.MAP_TYPE_HYBRID));
    UiSettings uiSettings = map.getUiSettings();
    uiSettings.setRotateGesturesEnabled(false);
    uiSettings.setTiltGesturesEnabled(false);
    uiSettings.setMyLocationButtonEnabled(false);
    uiSettings.setMapToolbarEnabled(false);
    uiSettings.setCompassEnabled(false);
    uiSettings.setIndoorLevelPickerEnabled(false);
    map.setOnCameraIdleListener(this::onCameraIdle);
    map.setOnCameraMoveStartedListener(this::onCameraMoveStarted);
    map.setOnCameraMoveListener(this::onCameraMove);
    onCameraMove();
  }

  private static Point fromLatLng(LatLng latLng) {
    return Point.newBuilder().setLatitude(latLng.latitude).setLongitude(latLng.longitude).build();
  }

  private static LatLng toLatLng(Point point) {
    return new LatLng(point.getLatitude(), point.getLongitude());
  }

  private boolean onMarkerClick(Marker marker) {
    if (map.getUiSettings().isZoomGesturesEnabled()) {
      markerClickSubject.onNext((MapPin) marker.getTag());
      // Allow map to pan to marker.
      return false;
    } else {
      // Prevent map from panning to marker.
      return true;
    }
  }

  @Override
  public Observable<MapPin> getMapPinClicks() {
    return markerClickSubject;
  }

  @Override
  public Observable<Point> getDragInteractions() {
    return dragInteractionSubject;
  }

  @Override
  public Observable<Point> getCameraMoves() {
    return cameraMoves;
  }

  @Override
  public Observable<MapBoxOfflineTileProvider> getTileProviders() {
    return tileProviders;
  }

  @Override
  public void enable() {
    map.getUiSettings().setAllGesturesEnabled(true);
  }

  @Override
  public void disable() {
    map.getUiSettings().setAllGesturesEnabled(false);
  }

  @Override
  public void moveCamera(Point point) {
    map.moveCamera(CameraUpdateFactory.newLatLng(toLatLng(point)));
  }

  @Override
  public void moveCamera(Point point, float zoomLevel) {
    map.moveCamera(CameraUpdateFactory.newLatLngZoom(toLatLng(point), zoomLevel));
  }

  private void addMapPin(MapPin mapPin) {
    LatLng position = toLatLng(mapPin.getPosition());
    String color = mapPin.getStyle().getColor();
    BitmapDescriptor icon = markerIconFactory.getMarkerIcon(parseColor(color));
    Marker marker =
        markers.addMarker(new MarkerOptions().position(position).icon(icon).alpha(1.0f));
    marker.setTag(mapPin);
  }

  private void addMapPolyline(MapPolygon mapPolygon) {
    for (ImmutableSet<Point> vertices : mapPolygon.getVertices()) {
      PolylineOptions options = new PolylineOptions();

      // Read-only
      options.clickable(false);

      // Add vertices to PolylineOptions
      stream(vertices).map(GoogleMapsMapAdapter::toLatLng).forEach(options::add);

      // Add to map
      Polyline polyline = map.addPolyline(options);
      polyline.setTag(mapPolygon);

      // Style polyline
      polyline.setStartCap(new RoundCap());
      polyline.setEndCap(new RoundCap());
      polyline.setWidth(POLYLINE_STROKE_WIDTH_PX);
      polyline.setColor(parseColor(mapPolygon.getStyle().getColor()));
      polyline.setJointType(JointType.ROUND);

      polylines.add(polyline);
    }
  }

  private void addMapGeoJson(MapGeoJson mapFeature) {
    // Pass markerManager here otherwise markers in the previous layers won't be clickable.
    GeoJsonLayer layer =
        new GeoJsonLayer(map, mapFeature.getGeoJson(), markerManager, null, null, null);

    int width = POLYLINE_STROKE_WIDTH_PX;
    int color = parseColor(mapFeature.getStyle().getColor());

    GeoJsonPointStyle pointStyle = layer.getDefaultPointStyle();
    pointStyle.setLineStringWidth(width);
    pointStyle.setPolygonFillColor(color);

    GeoJsonPolygonStyle polygonStyle = layer.getDefaultPolygonStyle();
    polygonStyle.setLineStringWidth(width);
    polygonStyle.setPolygonFillColor(color);

    GeoJsonLineStringStyle lineStringStyle = layer.getDefaultLineStringStyle();
    lineStringStyle.setLineStringWidth(width);
    lineStringStyle.setPolygonFillColor(color);

    layer.addLayerToMap();
    geoJsonLayers.add(layer);
  }

  private void removeAllMarkers() {
    markers.clear();
  }

  private void removeAllPolylines() {
    stream(polylines).forEach(Polyline::remove);
    polylines.clear();
  }

  private void removeAllGeoJsonLayers() {
    stream(geoJsonLayers).forEach(Layer::removeLayerFromMap);
    geoJsonLayers.clear();
  }

  @Override
  public Point getCameraTarget() {
    return fromLatLng(map.getCameraPosition().target);
  }

  @Override
  public float getCurrentZoomLevel() {
    return map.getCameraPosition().zoom;
  }

  @Override
  @SuppressLint("MissingPermission")
  public void enableCurrentLocationIndicator() {
    if (!map.isMyLocationEnabled()) {
      map.setMyLocationEnabled(true);
    }
  }

  @Override
  public void setMapFeatures(ImmutableSet<MapFeature> updatedFeatures) {
    if (updatedFeatures.isEmpty()) {
      removeAllMarkers();
      removeAllPolylines();
      removeAllGeoJsonLayers();
      return;
    }
    Set<MapFeature> featuresToAdd = new HashSet<>(updatedFeatures);

    for (Marker marker : markers.getMarkers()) {
      MapPin pin = (MapPin) marker.getTag();
      if (updatedFeatures.contains(pin)) {
        // If pin already exists on map, don't add it.
        featuresToAdd.remove(pin);
      } else {
        // Remove existing pins not in list of updatedFeatures.
        removeMarker(marker);
      }
    }

    Iterator<Polyline> polylineIterator = polylines.iterator();
    while (polylineIterator.hasNext()) {
      Polyline polyline = polylineIterator.next();
      MapPolygon polygon = (MapPolygon) polyline.getTag();
      if (updatedFeatures.contains(polygon)) {
        // If polygon already exists on map, don't add it.
        featuresToAdd.remove(polygon);
      } else {
        // Remove existing polyline not in list of updatedFeatures.
        removePolygon(polyline);
        polylineIterator.remove();
      }
    }

    stream(featuresToAdd)
        .forEach(
            mapFeature -> {
              if (mapFeature instanceof MapPin) {
                addMapPin((MapPin) mapFeature);
              } else if (mapFeature instanceof MapPolygon) {
                addMapPolyline((MapPolygon) mapFeature);
              } else if (mapFeature instanceof MapGeoJson) {
                addMapGeoJson((MapGeoJson) mapFeature);
              }
            });
  }

  @Override
  public int getMapType() {
    return map.getMapType();
  }

  @Override
  public void setMapType(int mapType) {
    map.setMapType(mapType);
  }

  private void removeMarker(Marker marker) {
    Timber.v("Removing marker %s", marker.getId());
    marker.remove();
  }

  private void removePolygon(Polyline polyline) {
    Timber.v("Removing polyline %s", polyline.getId());
    polyline.remove();
  }

  private int parseColor(@Nullable String colorHexCode) {
    try {
      return Color.parseColor(String.valueOf(colorHexCode));
    } catch (IllegalArgumentException e) {
      Timber.w("Invalid color code in layer style: %s", colorHexCode);
      return context.getResources().getColor(R.color.colorMapAccent);
    }
  }

  private void onCameraIdle() {
    cameraTargetBeforeDrag = null;
  }

  private void onCameraMoveStarted(int reason) {
    if (reason == REASON_DEVELOPER_ANIMATION) {
      // MapAdapter was panned by the app, not the user.
      return;
    }
    cameraTargetBeforeDrag = map.getCameraPosition().target;
  }

  private void onCameraMove() {
    LatLng cameraTarget = map.getCameraPosition().target;
    Point target = fromLatLng(cameraTarget);
    cameraMoves.onNext(target);
    if (cameraTargetBeforeDrag != null && !cameraTarget.equals(cameraTargetBeforeDrag)) {
      dragInteractionSubject.onNext(target);
    }
  }

  @Override
  public LatLngBounds getViewport() {
    return map.getProjection().getVisibleRegion().latLngBounds;
  }

  private void addTileOverlay(String filePath) {
    File mbtilesFile = new File(context.getFilesDir(), filePath);

    if (!mbtilesFile.exists()) {
      Timber.i("mbtiles file %s does not exist", mbtilesFile.getAbsolutePath());
      return;
    }

    try {
      MapBoxOfflineTileProvider tileProvider = new MapBoxOfflineTileProvider(mbtilesFile);
      tileProviders.onNext(tileProvider);
      map.addTileOverlay(new TileOverlayOptions().tileProvider(tileProvider));
    } catch (Exception e) {
      Timber.e(e, "Couldn't initialize tile provider for mbtiles file %s", mbtilesFile);
    }
  }

  @Override
  public void addTileOverlays(ImmutableSet<String> mbtilesFiles) {
    stream(mbtilesFiles).forEach(this::addTileOverlay);
  }
}
