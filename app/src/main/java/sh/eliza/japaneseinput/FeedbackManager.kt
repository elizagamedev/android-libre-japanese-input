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

import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View

/** FeedbackManager manages feed back events, like haptic and sound. */
class FeedbackManager internal constructor(private val feedbackListener: FeedbackListener) {
  /** Event types. */
  enum class FeedbackEvent(
    /** The haptic feedback type, defined in [HapticFeedbackConstants]. */
    val hapticFeedbackType: Int?,
    /** The effect type of the feedback sound, defined in [AudioManager]. */
    val soundEffectType: Int?,
  ) {
    /** Fired when a key is down. */
    KEY_DOWN(HapticFeedbackConstants.KEYBOARD_TAP, AudioManager.FX_KEYPRESS_STANDARD),

    /** Fired when the delete key is down. */
    KEY_DELETE_DOWN(HapticFeedbackConstants.KEYBOARD_TAP, AudioManager.FX_KEYPRESS_DELETE),

    /** Fired when the return key is down. */
    KEY_RETURN_DOWN(HapticFeedbackConstants.KEYBOARD_TAP, AudioManager.FX_KEYPRESS_RETURN),

    /** Fired when the spacebar key is down. */
    KEY_SPACEBAR_DOWN(HapticFeedbackConstants.KEYBOARD_TAP, AudioManager.FX_KEYPRESS_SPACEBAR),

    /** Fired when a special key is down. */
    KEY_SPECIAL_DOWN(HapticFeedbackConstants.CONTEXT_CLICK, AudioManager.FX_KEY_CLICK),

    /** Fired when a candidate is selected by using candidate view. */
    CANDIDATE_SELECTED(HapticFeedbackConstants.CONTEXT_CLICK, AudioManager.FX_KEY_CLICK),

    /** Fired when the input view is expanded (the candidate view is fold). */
    INPUTVIEW_EXPAND(HapticFeedbackConstants.CONTEXT_CLICK, AudioManager.FX_KEY_CLICK),

    /** Fired when the input view is fold (the candidate view is expand). */
    INPUTVIEW_FOLD(HapticFeedbackConstants.CONTEXT_CLICK, AudioManager.FX_KEY_CLICK),

    /** Fired when the symbol input view is closed. */
    SYMBOL_INPUTVIEW_CLOSED(HapticFeedbackConstants.CONTEXT_CLICK, AudioManager.FX_KEY_CLICK),

    /** Fired when a minor category is selected. */
    SYMBOL_INPUTVIEW_MINOR_CATEGORY_SELECTED(
      HapticFeedbackConstants.CONTEXT_CLICK,
      AudioManager.FX_KEY_CLICK
    ),

    /** Fired when a major category is selected. */
    SYMBOL_INPUTVIEW_MAJOR_CATEGORY_SELECTED(
      HapticFeedbackConstants.CONTEXT_CLICK,
      AudioManager.FX_KEY_CLICK
    ),

    /** Fired when microphone button is touched. */
    MICROPHONE_BUTTON_DOWN(HapticFeedbackConstants.CONTEXT_CLICK, AudioManager.FX_KEY_CLICK),

    /** Fired when the hardware composition button in narrow frame is touched. */
    NARROW_FRAME_HARDWARE_COMPOSITION_BUTTON_DOWN(
      HapticFeedbackConstants.CONTEXT_CLICK,
      AudioManager.FX_KEY_CLICK,
    ),

    /** Fired when the widen button in narrow frame is touched. */
    NARROW_FRAME_WIDEN_BUTTON_DOWN(
      HapticFeedbackConstants.CONTEXT_CLICK,
      AudioManager.FX_KEY_CLICK,
    )
  }

  internal interface FeedbackListener {
    /** Called when vibrate feedback is fired. */
    fun onVibrate(view: View, hapticFeedbackType: Int)

    /**
     * Called when sound feedback is fired.
     *
     * @param soundEffectType the effect type of the sound to be played. If
     * FeedbackManager.NO_SOUND, no sound will be played.
     */
    fun onSound(soundEffectType: Int, volume: Float)
  }

  // TODO(matsuzakit): This initial value should be changed
  //     after implementing setting screen.
  var isHapticFeedbackEnabled = false
  var isSoundFeedbackEnabled = false
  var soundFeedbackVolume = 0.4f // System default volume parameter.

  fun fireFeedback(view: View, event: FeedbackEvent) {
    if (isHapticFeedbackEnabled && event.hapticFeedbackType != null) {
      feedbackListener.onVibrate(view, event.hapticFeedbackType)
    }
    if (isSoundFeedbackEnabled && event.soundEffectType != null) {
      feedbackListener.onSound(event.soundEffectType, soundFeedbackVolume)
    }
  }

  fun release() {
    isSoundFeedbackEnabled = false
  }
}
