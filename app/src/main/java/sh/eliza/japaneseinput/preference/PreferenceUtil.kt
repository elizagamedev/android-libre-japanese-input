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
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import com.google.common.base.Preconditions
import sh.eliza.japaneseinput.MozcLog
import sh.eliza.japaneseinput.MozcUtil
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.preference.ClientSidePreference.KeyboardLayout

/** Utilities for Mozc preferences. */
object PreferenceUtil {
  @JvmStatic
  val defaultPreferenceManagerStatic by lazy {
    // As construction cost of defaultPrereferenceManagerStatic is cheap and it is invariant,
    // no lock mechanism is employed here.
    object : PreferenceManagerStaticInterface {
      override fun setDefaultValues(context: Context, id: Int, readAgain: Boolean) {
        PreferenceManager.setDefaultValues(context, id, readAgain)
      }
    }
  }

  // Keys for Keyboard Layout.
  const val PREF_CURRENT_KEYBOARD_LAYOUT_KEY = "pref_current_keyboard_layout_key"
  const val PREF_SOFTWARE_KEYBOARD_ADVANED_PORTRAIT_KEY =
    "pref_software_keyboard_advanced_portrait_key"
  const val PREF_PORTRAIT_KEYBOARD_LAYOUT_KEY = "pref_portrait_keyboard_layout_key"
  const val PREF_PORTRAIT_INPUT_STYLE_KEY = "pref_portrait_input_style_key"
  const val PREF_PORTRAIT_QWERTY_LAYOUT_FOR_ALPHABET_KEY =
    "pref_portrait_qwerty_layout_for_alphabet_key"
  const val PREF_PORTRAIT_FLICK_SENSITIVITY_KEY = "pref_portrait_flick_sensitivity_key"
  const val PREF_PORTRAIT_LAYOUT_ADJUSTMENT_KEY = "pref_portrait_layout_adjustment_key"
  const val PREF_PORTRAIT_KEYBOARD_HEIGHT_RATIO_KEY = "pref_portrait_keyboard_height_ratio_key"
  const val PREF_SOFTWARE_KEYBOARD_ADVANED_LANDSCAPE_KEY =
    "pref_software_keyboard_advanced_landscape_key"
  const val PREF_LANDSCAPE_KEYBOARD_LAYOUT_KEY = "pref_landscape_keyboard_layout_key"
  const val PREF_LANDSCAPE_INPUT_STYLE_KEY = "pref_landscape_input_style_key"
  const val PREF_LANDSCAPE_QWERTY_LAYOUT_FOR_ALPHABET_KEY =
    "pref_landscape_qwerty_layout_for_alphabet_key"
  const val PREF_LANDSCAPE_FLICK_SENSITIVITY_KEY = "pref_landscape_flick_sensitivity_key"
  const val PREF_LANDSCAPE_LAYOUT_ADJUSTMENT_KEY = "pref_landscape_layout_adjustment_key"
  const val PREF_LANDSCAPE_KEYBOARD_HEIGHT_RATIO_KEY = "pref_landscape_keyboard_height_ratio_key"
  const val PREF_USE_PORTRAIT_KEYBOARD_SETTINGS_FOR_LANDSCAPE_KEY =
    "pref_use_portrait_keyboard_settings_for_landscape_key"

  // Full screen keys.
  const val PREF_PORTRAIT_FULLSCREEN_KEY = "pref_portrait_fullscreen_key"
  const val PREF_LANDSCAPE_FULLSCREEN_KEY = "pref_landscape_fullscreen_key"

  // Keys for generic preferences.
  const val PREF_HARDWARE_KEYMAP = "pref_hardware_keymap"
  const val PREF_VOICE_INPUT_KEY = "pref_voice_input_key"
  const val PREF_HAPTIC_FEEDBACK_KEY = "pref_haptic_feedback_key"
  const val PREF_HAPTIC_FEEDBACK_DURATION_KEY = "pref_haptic_feedback_duration_key"
  const val PREF_SOUND_FEEDBACK_KEY = "pref_sound_feedback_key"
  const val PREF_SOUND_FEEDBACK_VOLUME_KEY = "pref_sound_feedback_volume_key"
  const val PREF_POPUP_FEEDBACK_KEY = "pref_popup_feedback_key"
  const val PREF_SPACE_CHARACTER_FORM_KEY = "pref_space_character_form_key"
  const val PREF_KANA_MODIFIER_INSENSITIVE_CONVERSION_KEY =
    "pref_kana_modifier_insensitive_conversion"
  const val PREF_TYPING_CORRECTION_KEY = "pref_typing_correction"
  const val PREF_EMOJI_PROVIDER_TYPE = "pref_emoji_provider_type"
  const val PREF_DICTIONARY_PERSONALIZATION_KEY = "pref_dictionary_personalization_key"
  const val PREF_DICTIONARY_USER_DICTIONARY_TOOL_KEY = "pref_dictionary_user_dictionary_tool_key"
  const val PREF_OTHER_INCOGNITO_MODE_KEY = "pref_other_anonimous_mode_key"
  const val PREF_OTHER_USAGE_STATS_KEY = "pref_other_usage_stats_key"
  const val PREF_ABOUT_VERSION = "pref_about_version"
  const val PREF_LAUNCHER_ICON_VISIBILITY_KEY = "pref_launcher_icon_visibility"

  // Application lifecycle
  const val PREF_LAST_LAUNCH_ABI_INDEPENDENT_VERSION_CODE =
    "pref_last_launch_abi_independent_version_code"
  private val CURRENT_KEYBOARD_LAYOUT_PREFERENCE_CHANGE_LISTENER: OnPreferenceChangeListener =
    CurrentKeyboardLayoutPreferenceChangeListener()

  @JvmStatic
  fun isLandscapeKeyboardSettingActive(
    sharedPreferences: SharedPreferences,
    deviceOrientation: Int
  ): Boolean {
    Preconditions.checkNotNull<SharedPreferences>(sharedPreferences)
    return if (sharedPreferences.getBoolean(
        PREF_USE_PORTRAIT_KEYBOARD_SETTINGS_FOR_LANDSCAPE_KEY,
        true
      )
    ) {
      // Always use portrait configuration.
      false
    } else deviceOrientation == Configuration.ORIENTATION_LANDSCAPE
  }

  /** Initializes some preferences which need special initialization. */
  fun initializeSpecialPreferences(preferenceManager: PreferenceManager?) {
    if (preferenceManager == null) {
      return
    }
    initializeSpecialPreferencesInternal(PreferenceManagerInterfaceImpl(preferenceManager))
  }

  @JvmStatic
  fun initializeSpecialPreferencesInternal(preferenceManager: PreferenceManagerInterface) {
    initializeCurrentKeyboardLayoutPreference(
      preferenceManager.findPreference(PREF_CURRENT_KEYBOARD_LAYOUT_KEY)
    )
    initializeVersionPreference(preferenceManager.findPreference(PREF_ABOUT_VERSION))
    initializeLayoutAdjustmentPreference(preferenceManager)
  }

  private fun initializeCurrentKeyboardLayoutPreference(preference: Preference?) {
    if (preference !is KeyboardLayoutPreference) {
      return
    }

    // Initialize the value based on the current orientation.
    val sharedPreferences = preference.sharedPreferences!!
    val isLandscapeKeyboardSettingActive =
      isLandscapeKeyboardSettingActive(
        sharedPreferences,
        preference.context.resources.configuration.orientation
      )
    preference.value =
      getKeyboardLayout(
        sharedPreferences,
        if (isLandscapeKeyboardSettingActive) PREF_LANDSCAPE_KEYBOARD_LAYOUT_KEY
        else PREF_PORTRAIT_KEYBOARD_LAYOUT_KEY
      )
    preference.setOnPreferenceChangeListener(CURRENT_KEYBOARD_LAYOUT_PREFERENCE_CHANGE_LISTENER)
  }

  /** Returns parsed [KeyboardLayout] instance, or TWELVE_KEYS if any error is found. */
  private fun getKeyboardLayout(
    sharedPreferences: SharedPreferences?,
    key: String?
  ): KeyboardLayout {
    if (sharedPreferences == null || key == null) {
      return KeyboardLayout.TWELVE_KEYS
    }
    val value: String = sharedPreferences.getString(key, null) ?: return KeyboardLayout.TWELVE_KEYS
    try {
      return ClientSidePreference.KeyboardLayout.valueOf(value)
    } catch (e: IllegalArgumentException) {
      MozcLog.e("Invalid KeyboardLayout: $value")
    }
    return KeyboardLayout.TWELVE_KEYS
  }

  private fun initializeVersionPreference(preference: Preference?) {
    if (preference == null) {
      return
    }
    preference.setSummary(MozcUtil.getVersionName(preference.getContext()))
  }

  fun shouldRemoveLayoutAdjustmentPreferences(
    preferenceManager: PreferenceManagerInterface
  ): Boolean {
    val preference =
      preferenceManager.findPreference(PREF_SOFTWARE_KEYBOARD_ADVANED_PORTRAIT_KEY) ?: return false
    val resouces = preference.getContext().getResources()
    val smallestWidth =
      (resouces.getDimensionPixelSize(R.dimen.ime_window_partial_width) +
        resouces.getDimensionPixelSize(R.dimen.side_frame_width))
    val displayMetrics = resouces.displayMetrics
    return (displayMetrics.widthPixels < smallestWidth ||
      displayMetrics.heightPixels < smallestWidth)
  }

  fun removePreference(preferenceManager: PreferenceManagerInterface, key: CharSequence) {
    val parentKey =
      when (key) {
        PREF_PORTRAIT_LAYOUT_ADJUSTMENT_KEY -> PREF_SOFTWARE_KEYBOARD_ADVANED_PORTRAIT_KEY
        PREF_LANDSCAPE_LAYOUT_ADJUSTMENT_KEY -> PREF_SOFTWARE_KEYBOARD_ADVANED_LANDSCAPE_KEY
        else -> return
      }
    val preference = preferenceManager.findPreference(key)
    val parentPreference = preferenceManager.findPreference(parentKey) as? PreferenceGroup
    if (preference == null || parentPreference == null) {
      return
    }
    parentPreference.removePreference(preference)
  }

  @JvmStatic
  fun initializeLayoutAdjustmentPreference(preferenceManager: PreferenceManagerInterface) {
    if (shouldRemoveLayoutAdjustmentPreferences(preferenceManager)) {
      removePreference(preferenceManager, PREF_PORTRAIT_LAYOUT_ADJUSTMENT_KEY)
      removePreference(preferenceManager, PREF_LANDSCAPE_LAYOUT_ADJUSTMENT_KEY)
    }
  }

  /**
   * Gets an enum value in the SharedPreference.
   *
   * @param sharedPreference a [SharedPreferences] to be loaded.
   * @param key a key name
   * @param type a class of enum value
   * @param defaultValue default value if the [SharedPreferences] doesn't have corresponding entry.
   * @param conversionRecoveryValue default value if unknown value is stored. For example, if the
   * value is "ALPHA" and `type` doesn't have "ALPHA" entry, this argument is returned.
   */
  fun <T : Enum<T>> getEnum(
    sharedPreference: SharedPreferences?,
    key: String,
    type: Class<T>,
    defaultValue: T,
    conversionRecoveryValue: T
  ) =
    if (sharedPreference == null) {
      defaultValue
    } else {
      val name = sharedPreference.getString(key, null)
      if (name != null) {
        try {
          java.lang.Enum.valueOf(type, name)
        } catch (e: IllegalArgumentException) {
          conversionRecoveryValue
        }
      } else {
        defaultValue
      }
    }

  /**
   * Same as [getEnum].
   *
   * `defaultValue` is used as `conversionRecoveryValue`
   */
  fun <T : Enum<T>> getEnum(
    sharedPreference: SharedPreferences?,
    key: String,
    type: Class<T>,
    defaultValue: T
  ) = getEnum(sharedPreference, key, type, defaultValue, defaultValue)

  @JvmStatic
  fun setDefaultValues(
    preferenceManager: PreferenceManagerStaticInterface,
    context: Context,
  ) {
    // 'true' here means the preferences which have not set yet are *always* set here.
    // This doesn't mean *Reset all the preferences*.
    // (if 'false' the process will be done once on the first launch
    //  so even if new preferences are added their default values will not be set here)
    preferenceManager.setDefaultValues(context, R.xml.pref_main, true)
    preferenceManager.setDefaultValues(context, R.xml.pref_software_keyboard_advanced, true)
  }

  /** Simple `PreferenceManager` wrapper for testing purpose. */
  interface PreferenceManagerInterface {
    fun findPreference(key: CharSequence): Preference?
  }

  /**
   * Simple `PreferenceManager` wrapper for testing purpose. This interface wraps static method so
   * no constructor is required.
   */
  interface PreferenceManagerStaticInterface {
    fun setDefaultValues(context: Context, id: Int, readAgain: Boolean)
  }

  private class PreferenceManagerInterfaceImpl(
    private val preferenceManager: PreferenceManager,
  ) : PreferenceManagerInterface {
    override fun findPreference(key: CharSequence): Preference? {
      return preferenceManager.findPreference(key)
    }
  }

  private class CurrentKeyboardLayoutPreferenceChangeListener : OnPreferenceChangeListener {
    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
      if (newValue !is KeyboardLayout) {
        return false
      }

      // Write back to with the appropriate preference key.
      val sharedPreferences = preference.getSharedPreferences()!!
      val isLandscapeKeyboardSettingActive =
        isLandscapeKeyboardSettingActive(
          sharedPreferences,
          preference.getContext().getResources().getConfiguration().orientation
        )
      sharedPreferences
        .edit()
        .putString(
          if (isLandscapeKeyboardSettingActive) PREF_LANDSCAPE_KEYBOARD_LAYOUT_KEY
          else PREF_PORTRAIT_KEYBOARD_LAYOUT_KEY,
          newValue.name
        )
        .apply()
      return true
    }
  }
}
