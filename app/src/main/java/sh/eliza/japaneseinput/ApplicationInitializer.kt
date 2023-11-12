// Copyright 2010-2018, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package sh.eliza.japaneseinput

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.preference.PreferenceManager
import kotlin.math.ceil
import sh.eliza.japaneseinput.preference.PreferenceUtil
import sh.eliza.japaneseinput.preference.PreferenceUtil.PreferenceManagerStaticInterface
import sh.eliza.japaneseinput.util.LauncherIconManagerFactory.LauncherIconManager

/** The entry point of the application. */
class ApplicationInitializer(
  val context: Context,
) {
  val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

  /**
   * Initializes the application.
   *
   * Updates some preferences. Here we use three preference items.
   *
   * * pref_welcome_activity_shown: True if the "Welcome" activity has shown at least once.
   * * pref_last_launch_abi_independent_version_code: The latest version number which has launched
   * at least once.
   * * pref_launched_at_least_once: Deprecated. True if the the IME has launched at least once.
   *
   * Some preferences should be set at the first time launch. If the IME is a system application
   * (preinstalled), it shouldn't show "Welcome" activity. If an update is performed (meaning that
   * the IME becomes non-system app), the activity should be shown at the first time launch.
   *
   * We have to do migration process. If pref_launched_at_least_once exists,
   * pref_welcome_activity_shown is recognized as true and
   * pref_last_launch_abi_independent_version_code is recognized as
   * LAUNCHED_AT_LEAST_ONCE_DEPRECATED_VERSION_CODE. And then pref_launched_at_least_once is
   * removed.
   *
   * @param abiIndependentVersionCode ABI independent version code, typically obtained from [ ]
   * [MozcUtil.getAbiIndependentVersionCode]
   */
  fun initialize(
    launcherIconManager: LauncherIconManager,
    preferenceManager: PreferenceManagerStaticInterface,
  ) {
    val lastVersionCode =
      if (sharedPreferences.contains(PreferenceUtil.PREF_LAST_LAUNCH_ABI_INDEPENDENT_VERSION_CODE)
      ) {
        sharedPreferences
          .getInt(PreferenceUtil.PREF_LAST_LAUNCH_ABI_INDEPENDENT_VERSION_CODE, 0)
          .toLong()
      } else {
        null
      }

    val editor = sharedPreferences.edit()
    try {
      val tempDirectory = MozcUtil.getUserDictionaryExportTempDirectory(context)
      if (tempDirectory.isDirectory) {
        MozcUtil.deleteDirectoryContents(tempDirectory)
      }

      // Preferences: Update if this is the first launch
      if (lastVersionCode != null) {
        // Store full-screen relating preferences.
        val resources = context.resources
        val portraitMetrics =
          getPortraitDisplayMetrics(resources.displayMetrics, resources.configuration.orientation)
        storeDefaultFullscreenMode(
          editor,
          portraitMetrics.heightPixels,
          portraitMetrics.widthPixels,
          ceil(
              MozcUtil.getDimensionForOrientation(
                context,
                R.dimen.input_frame_height,
                Configuration.ORIENTATION_PORTRAIT
              )
              // .toDouble()
              )
            .toInt(),
          ceil(
              MozcUtil.getDimensionForOrientation(
                context,
                R.dimen.input_frame_height,
                Configuration.ORIENTATION_LANDSCAPE
              )
              // .toDouble()
              )
            .toInt(),
          resources.getDimensionPixelOffset(R.dimen.fullscreen_threshold)
        )
      }
      // Update launcher icon visibility and relating preference.
      launcherIconManager.updateLauncherIconVisibility(context)
      // Save default preference to the storage.
      // NOTE: This method must NOT be called before updateLauncherIconVisibility() above.
      //       Above method requires PREF_LAUNCHER_ICON_VISIBILITY_KEY is not filled with
      //       the default value.
      PreferenceUtil.setDefaultValues(preferenceManager, context)
    } finally {
      editor.putInt(
        PreferenceUtil.PREF_LAST_LAUNCH_ABI_INDEPENDENT_VERSION_CODE,
        MozcUtil.getVersionCode(context).toInt()
      )
      editor.apply()
    }
  }
}

/**
 * Returns a modified `DisplayMetrics` which equals to portrait modes's one.
 *
 * If current orientation is PORTRAIT, given `currentMetrics` is returned. Otherwise
 * `currentMetrics`'s `heightPixels` and `widthPixels` are swapped.
 */
private fun getPortraitDisplayMetrics(
  currentMetrics: DisplayMetrics,
  currentOrientation: Int
): DisplayMetrics {
  val result = DisplayMetrics()
  result.setTo(currentMetrics)
  if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
    result.heightPixels = currentMetrics.widthPixels
    result.widthPixels = currentMetrics.heightPixels
  }
  return result
}

/** Stores the default value of "fullscreen mode" to the shared preference. */
private fun storeDefaultFullscreenMode(
  editor: SharedPreferences.Editor,
  portraitDisplayHeight: Int,
  landscapeDisplayHeight: Int,
  portraitInputFrameHeight: Int,
  landscapeInputFrameHeight: Int,
  fullscreenThreshold: Int
) {
  editor.putBoolean(
    "pref_portrait_fullscreen_key",
    portraitDisplayHeight - portraitInputFrameHeight < fullscreenThreshold
  )
  editor.putBoolean(
    "pref_landscape_fullscreen_key",
    landscapeDisplayHeight - landscapeInputFrameHeight < fullscreenThreshold
  )
}
