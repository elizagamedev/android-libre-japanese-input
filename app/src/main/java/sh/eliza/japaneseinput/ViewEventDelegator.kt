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
import com.google.common.base.Preconditions
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import sh.eliza.japaneseinput.FeedbackManager.FeedbackEvent
import sh.eliza.japaneseinput.KeycodeConverter.KeyEventInterface
import sh.eliza.japaneseinput.ViewManagerInterface.LayoutAdjustment
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboard.CompositionSwitchMode
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification
import sh.eliza.japaneseinput.model.SymbolMajorCategory

/**
 * This class delegates all method calls to a ViewEventListener, passed to the constructor. Typical
 * usage is to hook/override some of listener's methods to change their behavior.
 *
 * <pre>`void foo(ViewEventListener listener) { childView.setListener(new
 * ViewEventDelegator(listener) {
 *
 * public void onFireFeedbackEvent(FeedbackEvent event) { // Disables all the feedback event. } }) }
 * `</pre> *
 */
abstract class ViewEventDelegator(private val delegated: ViewEventListener) : ViewEventListener {
  override fun onKeyEvent(
    mozcKeyEvent: ProtoCommands.KeyEvent?,
    keyEvent: KeyEventInterface?,
    keyboardSpecification: KeyboardSpecification?,
    touchEventList: List<ProtoCommands.Input.TouchEvent>
  ) {
    delegated.onKeyEvent(mozcKeyEvent, keyEvent, keyboardSpecification, touchEventList)
  }

  override fun onUndo(touchEventList: List<ProtoCommands.Input.TouchEvent>) {
    delegated.onUndo(touchEventList)
  }

  override fun onConversionCandidateSelected(
    view: View,
    candidateId: Int,
    rowIndex: Optional<Int>
  ) {
    delegated.onConversionCandidateSelected(view, candidateId, Preconditions.checkNotNull(rowIndex))
  }

  override fun onPageUp(view: View) {
    delegated.onPageUp(view)
  }

  override fun onPageDown(view: View) {
    delegated.onPageDown(view)
  }

  override fun onSymbolCandidateSelected(
    view: View,
    majorCategory: SymbolMajorCategory,
    candidate: String,
    updateHistory: Boolean
  ) {
    delegated.onSymbolCandidateSelected(view, majorCategory, candidate, updateHistory)
  }

  override fun onFireFeedbackEvent(view: View, event: FeedbackEvent) {
    delegated.onFireFeedbackEvent(view, event)
  }

  override fun onSubmitPreedit() {
    delegated.onSubmitPreedit()
  }

  override fun onExpandSuggestion() {
    delegated.onExpandSuggestion()
  }

  override fun onShowMenuDialog(touchEventList: List<ProtoCommands.Input.TouchEvent?>?) {
    delegated.onShowMenuDialog(touchEventList)
  }

  override fun onShowSymbolInputView(touchEventList: List<ProtoCommands.Input.TouchEvent?>?) {
    delegated.onShowSymbolInputView(touchEventList)
  }

  override fun onCloseSymbolInputView() {
    delegated.onCloseSymbolInputView()
  }

  override fun onHardwareKeyboardCompositionModeChange(mode: CompositionSwitchMode?) {
    delegated.onHardwareKeyboardCompositionModeChange(mode)
  }

  override fun onActionKey() {
    delegated.onActionKey()
  }

  override fun onNarrowModeChanged(newNarrowMode: Boolean) {
    delegated.onNarrowModeChanged(newNarrowMode)
  }

  override fun onUpdateKeyboardLayoutAdjustment(layoutAdjustment: LayoutAdjustment) {
    delegated.onUpdateKeyboardLayoutAdjustment(layoutAdjustment)
  }

  override fun onShowMushroomSelectionDialog() {
    delegated.onShowMushroomSelectionDialog()
  }
}
