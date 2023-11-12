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
package sh.eliza.japaneseinput.util

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.IBinder
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import java.util.Locale

private const val GOOGLE_PACKAGE_ID_PREFIX = "com.google.android"
private const val VOICE_IME_MODE = "voice"

class ImeSwitcher(
  private val inputMethodService: InputMethodService,
) {
  private val inputMethodManager =
    inputMethodService.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

  private val token: IBinder
    get() = inputMethodService.window.window!!.attributes.token

  /**
   * Returns true if at least one voice IME is available.
   *
   * Eligible voice IME subtype must match following conditions.
   *
   * * The voice IME's id has prefix "com.google.android" (for security and avoiding from activating
   * unexpected IME).
   * * The subtype's mode is "voice".
   * * The subtype is auxiliary (usually a user wants to get back to this IME when (s)he types back
   * key).
   *
   * You don't have to call this method so frequently. Just call after IME's activation. The only
   * way to make available an IME is to use system preference screen and when a user returns from it
   * to an application the IME receives onStartInput.
   */
  val isVoiceImeAvailable: Boolean
    // Here we don't take account of locale. Any voice IMEs are acceptable.
    get() = getVoiceInputMethod(null) != null

  /**
   * Switches to voice ime if possible
   *
   * @param locale preferred locale. This method uses the subtype of which locale is given one but
   * this is best effort.
   * @return true if target subtype is found. Note that even if switching itself fails this method
   * might return true.
   */
  fun switchToVoiceIme(locale: Locale?): Boolean {
    val inputMethod = getVoiceInputMethod(locale) ?: return false
    if (Build.VERSION.SDK_INT >= 28) {
      inputMethodService.switchInputMethod(inputMethod.inputMethodInfo.id, inputMethod.subtype)
    } else {
      @Suppress("deprecation")
      inputMethodManager.setInputMethodAndSubtype(
        token,
        inputMethod.inputMethodInfo.id,
        inputMethod.subtype
      )
    }
    return true
  }

  /**
   * Switches to *next* IME (including subtype).
   *
   * @param onlyCurrentIme if true this method find next candidates which are in this IME.
   * @return true if the switching succeeds
   */
  fun switchToNextInputMethod(onlyCurrentIme: Boolean): Boolean {
    return if (Build.VERSION.SDK_INT >= 28) {
      inputMethodService.switchToNextInputMethod(onlyCurrentIme)
    } else {
      @Suppress("deprecation") inputMethodManager.switchToNextInputMethod(token, onlyCurrentIme)
    }
  }

  /** @see InputMethodManager.shouldOfferSwitchingToNextInputMethod */
  fun shouldOfferSwitchingToNextInputMethod(): Boolean {
    return if (Build.VERSION.SDK_INT >= 28) {
      inputMethodService.shouldOfferSwitchingToNextInputMethod()
    } else {
      @Suppress("deprecation") inputMethodManager.shouldOfferSwitchingToNextInputMethod(token)
    }
  }

  /**
   * @param locale if null locale test is omitted (for performance)
   * @return null if no candidate is found
   */
  private fun getVoiceInputMethod(locale: Locale?): InputMethodSubtypeInfo? {
    val languageTag = locale?.toLanguageTag()
    var fallBack: InputMethodSubtypeInfo? = null
    for ((inputMethodInfo, value) in inputMethodManager.shortcutInputMethodsAndSubtypes) {
      if (!inputMethodInfo.component.packageName.startsWith(GOOGLE_PACKAGE_ID_PREFIX)) {
        continue
      }
      for (subtype in value) {
        if (!subtype.isAuxiliary || subtype.mode != VOICE_IME_MODE) {
          continue
        }
        if (languageTag == null || subtype.languageTag == languageTag) {
          return InputMethodSubtypeInfo(inputMethodInfo, subtype)
        }
        fallBack = InputMethodSubtypeInfo(inputMethodInfo, subtype)
      }
    }
    return fallBack
  }
}

/** A container of an InputMethodInfo and an InputMethodSubtype. */
private class InputMethodSubtypeInfo(
  val inputMethodInfo: InputMethodInfo,
  val subtype: InputMethodSubtype,
)
