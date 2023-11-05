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

import android.view.View
import com.google.common.base.Optional
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input.TouchEvent
import sh.eliza.japaneseinput.FeedbackManager.FeedbackEvent
import sh.eliza.japaneseinput.KeycodeConverter.KeyEventInterface
import sh.eliza.japaneseinput.ViewManagerInterface.LayoutAdjustment
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboard.CompositionSwitchMode
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification
import sh.eliza.japaneseinput.model.SymbolMajorCategory

/** Callback object for view events. */
interface ViewEventListener {
  /**
   * Called when KeyEvent is fired (by soft keyboard)
   *
   * @param mozcKeyEvent the key event to be processed by mozc server.
   * @param keyEvent the original key event
   * @param keyboardSpecification the keyboard specification used to input the key.
   * @param touchEventList `TouchEvent` instances related to this key event for logging usage stats.
   */
  fun onKeyEvent(
    mozcKeyEvent: ProtoCommands.KeyEvent?,
    keyEvent: KeyEventInterface?,
    keyboardSpecification: KeyboardSpecification?,
    touchEventList: List<ProtoCommands.Input.TouchEvent>
  )

  /**
   * Called when Undo is fired (by soft keyboard).
   *
   * @param touchEventList `TouchEvent` instances related to this undo for logging usage stats.
   */
  fun onUndo(touchEventList: List<ProtoCommands.Input.TouchEvent>)

  /**
   * Called when a conversion candidate is selected.
   *
   * @param candidateId the id which Candidate and CandidateWord has.
   * @param rowIndex index of row in which the candidate is. If absent no stats are sent.
   */
  fun onConversionCandidateSelected(view: View, candidateId: Int, rowIndex: Optional<Int>)

  /** Called when page down button is tapped. */
  fun onPageUp(view: View)

  /** Called when page down button is tapped. */
  fun onPageDown(view: View)

  /** Called when a candidate on symbol input view is selected. */
  fun onSymbolCandidateSelected(
    view: View,
    majorCategory: SymbolMajorCategory,
    candidate: String,
    updateHistory: Boolean
  )

  /**
   * Called when a feedback event happens.
   *
   * @param event the event which makes feedback.
   */
  fun onFireFeedbackEvent(view: View, event: FeedbackEvent)

  /** Called when the preedit should be submitted. */
  fun onSubmitPreedit()

  /** Called when expanding suggestion is needed. */
  fun onExpandSuggestion()

  /**
   * Called when the menu dialog is shown.
   *
   * @param touchEventList `TouchEvent` instances which is related to this event for logging usage
   * stats.
   */
  // TODO(matsuzakit): Rename. onFlushTouchEventStats ?
  fun onShowMenuDialog(touchEventList: List<ProtoCommands.Input.TouchEvent?>?)

  /**
   * Called when the symbol input view is shown.
   *
   * @param touchEventList `TouchEvent` instances which is related to this event for logging usage
   * stats.
   */
  fun onShowSymbolInputView(touchEventList: List<ProtoCommands.Input.TouchEvent?>?)

  /** Called when the symbol input view is closed. */
  fun onCloseSymbolInputView()

  /**
   * Called when the hardware_composition_button is clicked.
   *
   * @param mode new mode
   */
  fun onHardwareKeyboardCompositionModeChange(mode: CompositionSwitchMode?)

  /** Called when the key for editor action is pressed. */
  fun onActionKey()

  /** Called when the narrow mode of the view is changed. */
  fun onNarrowModeChanged(newNarrowMode: Boolean)

  /**
   * Called when the keyboard layout preference should be updated.
   *
   * The visible keyboard will also be updated as the result through a callback object.
   */
  fun onUpdateKeyboardLayoutAdjustment(layoutAdjustment: LayoutAdjustment)

  /** Called when the mushroom selection dialog is shown. */
  fun onShowMushroomSelectionDialog()
}
