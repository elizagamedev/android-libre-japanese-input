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
package sh.eliza.japaneseinput.preference

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnCancelListener
import android.content.DialogInterface.OnClickListener
import android.content.DialogInterface.OnDismissListener
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.common.annotations.VisibleForTesting
import sh.eliza.japaneseinput.ApplicationInitializerFactory
import sh.eliza.japaneseinput.ApplicationInitializerFactory.ApplicationInitializer
import sh.eliza.japaneseinput.MozcUtil
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboardSpecification
import sh.eliza.japaneseinput.preference.KeyboardPreviewDrawable.BitmapCache
import sh.eliza.japaneseinput.preference.KeyboardPreviewDrawable.CacheReferenceKey
import sh.eliza.japaneseinput.util.LauncherIconManagerFactory

private fun createAlertDialog(
  context: Context,
  titleResourceId: Int,
  message: String,
  clickListener: DialogInterface.OnClickListener
) =
  AlertDialog.Builder(context).run {
    setTitle(titleResourceId)
    setMessage(message)
    setPositiveButton(R.string.pref_ime_alert_next, clickListener)
    setNegativeButton(R.string.pref_ime_alert_cancel, clickListener)
    create()
  }

/**
 * This class handles the preferences of mozc.
 *
 * The same information might be stored in both SharedPreference and Config. In this case
 * SharedPreference is the master and Config is just a copy.
 */
class MozcPreferenceFragment(@IdRes private val preferencesResource: Int) :
  PreferenceFragmentCompat() {

  // TODO(exv): review this commented out code

  // override fun onCreate(savedInstanceState: Bundle?) {
  //   // If single-pane is preferred on this device and no fragment is specified,
  //   // puts an extra string in order to show the only fragment, "All".
  //   // By this, header list will not be shown.
  //   // It seems that #onCreate is the only injection point.
  //   val redirectingIntent = maybeCreateRedirectingIntent(intent, onIsMultiPane(), preferencePage)
  //   if (redirectingIntent != null) {
  //     intent = redirectingIntent
  //   }
  //   super.onCreate(savedInstanceState)
  // }

  // fun onBuildHeaders(target: List<Header?>?) {
  //   loadHeaders(target, onIsMultiPane())
  // }

  // fun loadHeaders(target: List<Header?>?, isMultiPane: Boolean) {
  //   if (!isMultiPane) {
  //     // It is not needed to load the header for single pane preference,
  //     // because the view will be switched to the contents directly by above hack.
  //     return
  //   }
  //   loadHeadersFromResource(
  //     if (resources.getBoolean(R.bool.sending_information_features_enabled))
  //       R.xml.preference_headers_multipane
  //     else R.xml.preference_headers_multipane_without_stats,
  //     target
  //   )
  //   if (MozcUtil.isDebug(this)) {
  //     // For debug build, we load additional header.
  //     loadHeadersFromResource(R.xml.preference_headers_multipane_development, target)
  //   }
  // }

  private class ImeEnableDialogClickListener
  internal constructor(
    private val context: Context,
  ) : DialogInterface.OnClickListener {
    override fun onClick(dialog: DialogInterface, which: Int) {
      if (which == DialogInterface.BUTTON_POSITIVE) {
        // Open the keyboard & language setting page.
        val intent = Intent()
        intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        context.startActivity(intent)
      }
    }
  }

  private class ImeSwitchDialogListener
  internal constructor(
    private val context: Context,
  ) :
    DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener,
    DialogInterface.OnCancelListener {

    // This variable should be reset every dialog shown timing, but it is not supported
    // on API level 7. So, instead, we check all possible finishing pass.
    // TODO(hidehiko): use OnShownListener after we upgrade the API level.
    private var showInputMethodPicker = false

    override fun onClick(dialog: DialogInterface, which: Int) {
      showInputMethodPicker = which == DialogInterface.BUTTON_POSITIVE
    }

    override fun onCancel(arg0: DialogInterface) {
      showInputMethodPicker = false
    }

    override fun onDismiss(dialog: DialogInterface) {
      if (showInputMethodPicker) {
        // Send an input method picker shown event after the dismissing of this dialog is
        // completed. Otherwise, the new dialog will be dismissed, too.
        showInputMethodPicker = false
        MozcUtil.requestShowInputMethodPicker(context)
      }
    }
  }

  @VisibleForTesting lateinit var imeEnableDialog: AlertDialog
  @VisibleForTesting lateinit var imeSwitchDialog: AlertDialog

  private val sharedPreferenceChangeListener =
    object : OnSharedPreferenceChangeListener {
      override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (PreferenceUtil.PREF_LAUNCHER_ICON_VISIBILITY_KEY == key) {
          LauncherIconManagerFactory.getDefaultInstance().updateLauncherIconVisibility(context)
        }
      }
    }

  // Cache the SharedPreferences instance, otherwise PreferenceManager.getDefaultSharedPreferences
  // would try to read it from the storage every time.
  private lateinit var sharedPreferences: SharedPreferences
  private val cacheKey = CacheReferenceKey()

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(preferencesResource, rootKey)
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    initializeAlertDialog(context)
  }

  override fun onResume() {
    super.onResume()
    sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

    HardwareKeyboardSpecification.maybeSetDetectedHardwareKeyMap(
      sharedPreferences,
      getResources().getConfiguration(),
      false
    )

    onPostResumeInternal(ApplicationInitializerFactory.createInstance(context!!))
  }

  @VisibleForTesting
  fun onPostResumeInternal(initializer: ApplicationInitializer) {
    val context = context!!
    initializer.initialize(
      MozcUtil.getAbiIndependentVersionCode(context),
      LauncherIconManagerFactory.getDefaultInstance(),
      PreferenceUtil.defaultPreferenceManagerStatic
    )
  }

  override fun onPause() {
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    super.onPause()
  }

  // Note: when an activity A starts another activity B, the order of their onStart/onStop
  // invocation sequence is:
  //   B's onStart -> A's onStop.
  // (of course, some other methods, such as onPause or onResume etc, are also invoked accordingly).
  // Thus, we can keep and pass the cached keyboard preview bitmaps by reference counting
  // in onStart/onStop methods from the single-pane preference top page to
  // software keyboard advanced settings, and vice versa.
  // Note that if a user tries to move an activity other than Mozc's preference even from
  // software keyboard advanced settings preference page, the counter will simply get 0, and all the
  // cached bitmap resources will be released.
  override fun onStart() {
    super.onStart()
    // Tell bitmap cache that this Activity uses it.
    BitmapCache.instance.addReference(cacheKey)
  }

  override fun onStop() {
    // Release all the bitmap cache (if necessary) to utilize the used memory.
    BitmapCache.instance.removeReference(cacheKey)
    super.onStop()
  }

  private fun initializeAlertDialog(context: Context) {
    val resource = getResources()
    imeEnableDialog =
      createAlertDialog(
        context,
        R.string.pref_ime_enable_alert_title,
        resource.getString(
          R.string.pref_ime_enable_alert_message,
          resource.getString(R.string.app_name)
        ),
        ImeEnableDialogClickListener(context)
      )

    val listener = ImeSwitchDialogListener(context)
    imeSwitchDialog =
      createAlertDialog(
          context,
          R.string.pref_ime_switch_alert_title,
          resource.getString(
            R.string.pref_ime_switch_alert_message,
            resource.getString(R.string.app_name)
          ),
          listener
        )
        .apply {
          setOnCancelListener(listener)
          setOnDismissListener(listener)
        }
  }
}
