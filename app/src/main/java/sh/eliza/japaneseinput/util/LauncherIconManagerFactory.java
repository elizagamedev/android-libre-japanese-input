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

package sh.eliza.japaneseinput.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import androidx.preference.PreferenceManager;
import com.google.common.base.Preconditions;
import sh.eliza.japaneseinput.LauncherActivity;
import sh.eliza.japaneseinput.preference.PreferenceUtil;

/** Manager of launcher icon's visibility. */
public class LauncherIconManagerFactory {

  /** Interface for the manager. */
  public interface LauncherIconManager {
    /**
     * Updates launcher icon's visibility by checking the value in preferences or by checking
     * whether the app is (updated) system application or not.
     *
     * @param context The application's context.
     */
    void updateLauncherIconVisibility(Context context);
  }

  private static class DefaultImplementation implements LauncherIconManager {

    @Override
    public void updateLauncherIconVisibility(Context context) {
      Preconditions.checkNotNull(context);
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
      boolean visible = shouldLauncherIconBeVisible(context, sharedPreferences);
      updateComponentEnableSetting(context, LauncherActivity.class, visible);
      sharedPreferences
          .edit()
          .putBoolean(PreferenceUtil.PREF_LAUNCHER_ICON_VISIBILITY_KEY, visible)
          .apply();
    }

    /**
     * Enables/disables component.
     *
     * @param context The application's context
     * @param component The component to be enabled/disabled
     * @param enabled true for enabled, false for disabled
     */
    private void updateComponentEnableSetting(
        Context context, Class<?> component, boolean enabled) {
      Preconditions.checkNotNull(context);
      Preconditions.checkNotNull(component);
      PackageManager packageManager = context.getPackageManager();
      ComponentName componentName = new ComponentName(context.getApplicationContext(), component);
      int newState =
          enabled
              ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
              : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
      if (newState != packageManager.getComponentEnabledSetting(componentName)) {
        packageManager.setComponentEnabledSetting(
            componentName, newState, PackageManager.DONT_KILL_APP);
      }
    }

    private static boolean shouldLauncherIconBeVisible(
        Context context, SharedPreferences sharedPreferences) {
      return sharedPreferences.getBoolean(PreferenceUtil.PREF_LAUNCHER_ICON_VISIBILITY_KEY, true);
    }
  }

  private static final LauncherIconManager defaultInstance = new DefaultImplementation();

  private LauncherIconManagerFactory() {}

  public static LauncherIconManager getDefaultInstance() {
    return defaultInstance;
  }
}
