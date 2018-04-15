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

package com.google.gnd.view.sheet;

import android.content.Context;
import android.support.design.widget.CoordinatorLayout;
import android.util.AttributeSet;
import android.view.View;

import com.google.gnd.view.OnSheetSlideBehavior;

public class SheetButtonBehavior extends OnSheetSlideBehavior<View> {
  private static final float HIDE_THRESHOLD = 0.3f;
  private static final float SHOW_THRESHOLD = 0.5f;

  public SheetButtonBehavior(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onSheetScrolled(
      CoordinatorLayout parent, View wrapper, SheetSlideMetrics metrics) {
    metrics.showWithSheet(wrapper, HIDE_THRESHOLD, SHOW_THRESHOLD);
  }
}
