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

package sh.eliza.japaneseinput;

import android.content.Context;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import com.google.common.annotations.VisibleForTesting;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command;
import sh.eliza.japaneseinput.KeycodeConverter.KeyEventInterface;
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboard.CompositionSwitchMode;
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification;
import sh.eliza.japaneseinput.keyboard.KeyboardActionListener;
import sh.eliza.japaneseinput.model.JapaneseSoftwareKeyboardModel;
import sh.eliza.japaneseinput.preference.ClientSidePreference.HardwareKeyMap;
import sh.eliza.japaneseinput.preference.ClientSidePreference.InputStyle;
import sh.eliza.japaneseinput.preference.ClientSidePreference.KeyboardLayout;
import sh.eliza.japaneseinput.util.CursorAnchorInfoWrapper;
import sh.eliza.japaneseinput.view.Skin;

/** Interface for ViewManager which manages Input, Candidate and Extracted views. */
public interface ViewManagerInterface extends MemoryManageable {

  /** Keyboard layout position. */
  enum LayoutAdjustment {
    FILL,
    RIGHT,
    LEFT,
  }

  /**
   * Creates new input view.
   *
   * <p>"Input view" is a software keyboard in almost all cases.
   *
   * <p>Previously created input view is not accessed any more after calling this method.
   *
   * @param context
   * @return newly created view.
   */
  View createMozcView(Context context);

  /**
   * Renders views which this instance own based on Command.Output.
   *
   * <p>Note that showing/hiding views is Service's responsibility.
   */
  void render(Command outCommand);

  /**
   * @return true if {@code event} should be consumed by Mozc client side and should be processed
   *     asynchronously.
   */
  boolean isKeyConsumedOnViewAsynchronously(KeyEvent event);

  /**
   * Consumes and handles the given key event.
   *
   * @throws IllegalArgumentException If {@code KeyEvent} is not the key to consume.
   */
  void consumeKeyOnViewSynchronously(KeyEvent event);

  void onHardwareKeyEvent(KeyEvent keyEvent);

  /**
   * @return whether the view should consume the generic motion event or not.
   */
  boolean isGenericMotionToConsume(MotionEvent event);

  /**
   * Consumes and handles the given generic motion event.
   *
   * @throws IllegalArgumentException If {@code MotionEvent} is not the key to consume.
   */
  boolean consumeGenericMotion(MotionEvent event);

  /**
   * @return the current keyboard specification.
   */
  KeyboardSpecification getKeyboardSpecification();

  /** Set {@code EditorInfo} instance to the current view. */
  void setEditorInfo(EditorInfo attribute);

  /** Set text for IME action button label. */
  void setTextForActionButton(CharSequence text);

  boolean hideSubInputView();

  /**
   * Set this keyboard layout to the specified one.
   *
   * @param keyboardLayout New keyboard layout.
   * @throws NullPointerException If {@code keyboardLayout} is {@code null}.
   */
  void setKeyboardLayout(KeyboardLayout keyboardLayout);

  /**
   * Set the input style.
   *
   * @param inputStyle new input style.
   * @throws NullPointerException If {@code inputStyle} is {@code null}. TODO(hidehiko): Refactor
   *     out following keyboard switching logic into another class.
   */
  void setInputStyle(InputStyle inputStyle);

  void setQwertyLayoutForAlphabet(boolean qwertyLayoutForAlphabet);

  void setFullscreenMode(boolean fullscreenMode);

  boolean isFullscreenMode();

  void setFlickSensitivity(int flickSensitivity);

  void maybeTransitToNarrowMode(Command command, KeyEventInterface keyEvent);

  boolean isNarrowMode();

  boolean isFloatingCandidateMode();

  void setPopupEnabled(boolean popupEnabled);

  void switchHardwareKeyboardCompositionMode(CompositionSwitchMode mode);

  void setHardwareKeyMap(HardwareKeyMap hardwareKeyMap);

  void setSkin(Skin skin);

  void setMicrophoneButtonEnabledByPreference(boolean microphoneButtonEnabled);

  void setLayoutAdjustment(LayoutAdjustment layoutAdjustment);

  void setKeyboardHeightRatio(int keyboardHeightRatio);

  void onConfigurationChanged(Configuration newConfig);

  void onStartInputView(EditorInfo editorInfo);

  void setCursorAnchorInfo(CursorAnchorInfoWrapper info);

  void setCursorAnchorInfoEnabled(boolean enabled);

  /** Reset the status of the current input view. */
  void reset();

  void computeInsets(Context context, InputMethodService.Insets outInsets, Window window);

  void onShowSymbolInputView();

  void onCloseSymbolInputView();

  @VisibleForTesting
  ViewEventListener getEventListener();

  @VisibleForTesting
  JapaneseSoftwareKeyboardModel getActiveSoftwareKeyboardModel();

  @VisibleForTesting
  boolean isPopupEnabled();

  @VisibleForTesting
  int getFlickSensitivity();

  @VisibleForTesting
  Skin getSkin();

  @VisibleForTesting
  boolean isMicrophoneButtonEnabledByPreference();

  @VisibleForTesting
  LayoutAdjustment getLayoutAdjustment();

  @VisibleForTesting
  int getKeyboardHeightRatio();

  @VisibleForTesting
  HardwareKeyMap getHardwareKeyMap();

  /** Used for testing to inject key events. */
  @VisibleForTesting
  KeyboardActionListener getKeyboardActionListener();

  void updateGlobeButtonEnabled();

  void updateMicrophoneButtonEnabled();
}
