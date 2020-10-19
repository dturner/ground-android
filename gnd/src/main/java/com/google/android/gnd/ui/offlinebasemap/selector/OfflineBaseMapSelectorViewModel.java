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

package com.google.android.gnd.ui.offlinebasemap.selector;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.LiveDataReactiveStreams;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gnd.repository.OfflineBaseMapRepository;
import com.google.android.gnd.rx.Event;
import com.google.android.gnd.ui.common.AbstractViewModel;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import javax.inject.Inject;
import timber.log.Timber;

public class OfflineBaseMapSelectorViewModel extends AbstractViewModel {

  enum DownloadMessage {
    STARTED,
    FAILURE
  }

  private final FlowableProcessor<LatLngBounds> downloadClicks = PublishProcessor.create();
  @NonNull
  private final LiveData<Event<DownloadMessage>> messages;

  @Inject
  OfflineBaseMapSelectorViewModel(@NonNull OfflineBaseMapRepository offlineBaseMapRepository) {
    this.messages =
        LiveDataReactiveStreams.fromPublisher(
            downloadClicks.switchMapSingle(
                viewport ->
                    offlineBaseMapRepository
                        .addAreaAndEnqueue(viewport)
                        .toSingleDefault(DownloadMessage.STARTED)
                        .onErrorReturn(this::onEnqueueError)
                        .map(Event::create)));
  }

  @NonNull
  private DownloadMessage onEnqueueError(@NonNull Throwable e) {
    Timber.e("Failed to add area and queue downloads: %s", e.getMessage());
    return DownloadMessage.FAILURE;
  }

  @NonNull
  public LiveData<Event<DownloadMessage>> getDownloadMessages() {
    return this.messages;
  }

  // TODO: Use an abstraction over LatLngBounds
  public void onDownloadClick(LatLngBounds viewport) {
    Timber.d("viewport:%s", viewport);
    downloadClicks.onNext(viewport);
  }
}
