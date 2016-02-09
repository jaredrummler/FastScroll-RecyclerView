/*
 * Copyright (C) 2016 Jared Rummler <jared.rummler@gmail.com>
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
 *
 */

package com.jaredrummler.fastscrollrecyclerview;

import android.content.res.Resources;
import android.os.Build;
import android.view.View;

final class Utilities {

  /**
   * This method converts dp unit to equivalent device specific value in pixels.
   *
   * @param dp
   *     A value in dp (Device independent pixels) which will be converted to pixels.
   * @return an int value to represent Pixels equivalent to dp according to device
   */
  static int dpToPx(Resources res, float dp) {
    return Math.round(dp * res.getDisplayMetrics().density);
  }

  static boolean isRtl(Resources res) {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
        res.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
  }

}
