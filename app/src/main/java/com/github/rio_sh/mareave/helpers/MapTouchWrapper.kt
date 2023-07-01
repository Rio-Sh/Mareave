/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Changes from original file
 * - Add gestureDetector variable
 * - Refactor onIntercept()
 */
package com.github.rio_sh.mareave.helpers

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

/**
 * Class used to get finer granularity when tapping on GoogleMap views.
 * This wrapper view will intercept the touch events for a tap.
 */
class MapTouchWrapper : FrameLayout {
  private var touchSlop = 0
  private var listener: ((Point) -> Unit)? = null
  private var gestureDetector: GestureDetector
  constructor(context: Context) : super(context) {
    setup(context)
  }

  constructor(context: Context, attributeSet: AttributeSet?) : super(context, attributeSet) {
    setup(context)
  }

  init {
    gestureDetector = GestureDetector(context, GestureListener())
  }

  private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean {
      return true
    }
    override fun onLongPress(e: MotionEvent) {
      val tapped = Point(e.x.toInt(), e.y.toInt())
      listener?.invoke(tapped)
    }
  }

  private fun setup(context: Context) {
    val vc = ViewConfiguration.get(context)
    touchSlop = vc.scaledTouchSlop
  }

  fun setup(listener: ((Point) -> Unit)?) {
    this.listener = listener
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    if (listener == null) {
      return false
    }
    gestureDetector.onTouchEvent(event)
    return false
  }
}