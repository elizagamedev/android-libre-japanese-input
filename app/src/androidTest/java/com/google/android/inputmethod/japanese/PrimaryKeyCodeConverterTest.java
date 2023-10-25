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

package org.mozc.android.inputmethod.japanese;

import static org.easymock.EasyMock.expect;

import org.mozc.android.inputmethod.japanese.KeycodeConverter.KeyEventInterface;
import org.mozc.android.inputmethod.japanese.keyboard.ProbableKeyEventGuesser;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input.TouchEvent;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent.ProbableKeyEvent;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent.SpecialKey;
import org.mozc.android.inputmethod.japanese.R;
import org.mozc.android.inputmethod.japanese.testing.InstrumentationTestCaseWithMock;
import org.mozc.android.inputmethod.japanese.testing.Parameter;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 * TODO(matsuzakit): Move to util package.
 */
public class PrimaryKeyCodeConverterTest extends InstrumentationTestCaseWithMock {

  @SmallTest
  public void testCreateKeyEventForInvalidKeyCode() {
    Context context = getInstrumentation().getTargetContext();
    PrimaryKeyCodeConverter primaryKeyCodeConverter = new PrimaryKeyCodeConverter(context);

    // Invalid Keycode.
    Optional<ProtoCommands.KeyEvent> keyEvent = primaryKeyCodeConverter.createMozcKeyEvent(
        Integer.MIN_VALUE, Collections.<TouchEvent>emptyList());

    assertFalse(keyEvent.isPresent());
  }

  @SmallTest
  public void testCreateKeyEvent() {
    Context context = getInstrumentation().getTargetContext();
    ProbableKeyEventGuesser guesser = createMockBuilder(ProbableKeyEventGuesser.class)
        .addMockedMethod("getProbableKeyEvents")
        .withConstructor(AssetManager.class)
        .withArgs(getInstrumentation().getTargetContext().getAssets())
        .createMock();
    PrimaryKeyCodeConverter primaryKeyCodeConverter =
        new PrimaryKeyCodeConverter(context, guesser);

    List<TouchEvent> stubTouchEventList =
        Collections.singletonList(TouchEvent.getDefaultInstance());

    class TestData extends Parameter {
      final int keyCode;
      final List<TouchEvent> touchEventList;
      final ProtoCommands.KeyEvent expectKeyEvent;
      final int expectKeyCode;

      TestData(
          int keyCode, List<TouchEvent> touchEventList,
          ProtoCommands.KeyEvent expectKeyEvent, int expectKeyCode) {
        this.keyCode = keyCode;
        this.touchEventList = Preconditions.checkNotNull(touchEventList);
        this.expectKeyEvent = Preconditions.checkNotNull(expectKeyEvent);
        this.expectKeyCode = expectKeyCode;
      }
    }

    Resources resources = context.getResources();

    TestData[] testDataList = {
        // White space.
        new TestData(
            ' ', Collections.<TouchEvent>emptyList(),
            ProtoCommands.KeyEvent.newBuilder().setSpecialKey(SpecialKey.SPACE).build(),
            KeyEvent.KEYCODE_SPACE),
        // Enter.
        new TestData(
            resources.getInteger(R.integer.key_enter), Collections.<TouchEvent>emptyList(),
            ProtoCommands.KeyEvent.newBuilder().setSpecialKey(SpecialKey.VIRTUAL_ENTER).build(),
            KeyEvent.KEYCODE_ENTER),
        // Delete.
        new TestData(
            resources.getInteger(R.integer.key_backspace), Collections.<TouchEvent>emptyList(),
            ProtoCommands.KeyEvent.newBuilder().setSpecialKey(SpecialKey.BACKSPACE).build(),
            KeyEvent.KEYCODE_DEL),
        // Left.
        new TestData(
            resources.getInteger(R.integer.key_left), Collections.<TouchEvent>emptyList(),
            ProtoCommands.KeyEvent.newBuilder().setSpecialKey(SpecialKey.VIRTUAL_LEFT).build(),
            KeyEvent.KEYCODE_DPAD_LEFT),
        // Right.
        new TestData(
            resources.getInteger(R.integer.key_right), Collections.<TouchEvent>emptyList(),
            ProtoCommands.KeyEvent.newBuilder().setSpecialKey(SpecialKey.VIRTUAL_RIGHT).build(),
            KeyEvent.KEYCODE_DPAD_RIGHT),
        // Normal character with no correction stats.
        new TestData(
            'a', Collections.<TouchEvent>emptyList(),
            ProtoCommands.KeyEvent.newBuilder().setKeyCode('a').build(),
            KeyEvent.KEYCODE_A),
        // Normal character with correction stats.
        new TestData(
            'a', stubTouchEventList,
            ProtoCommands.KeyEvent.newBuilder()
            .setKeyCode('a')
            .addAllProbableKeyEvent(
                Arrays.asList(
                    ProbableKeyEvent.newBuilder()
                    .setKeyCode('b')
                    .setProbability(0.1d)
                    .build()))
            .build(),
            KeyEvent.KEYCODE_A)
    };

    expect(guesser.getProbableKeyEvents(stubTouchEventList)).andReturn(Arrays.asList(
        ProbableKeyEvent.newBuilder()
        .setKeyCode('b')
        .setProbability(0.1d)
        .build()));
    replayAll();

    for (TestData testData : testDataList) {
      Optional<ProtoCommands.KeyEvent> keyEvent = primaryKeyCodeConverter.createMozcKeyEvent(
          testData.keyCode, testData.touchEventList);
      assertEquals(testData.toString(), testData.expectKeyEvent, keyEvent.orNull());

      KeyEventInterface primaryCodeKeyEvent = primaryKeyCodeConverter.getPrimaryCodeKeyEvent(
          testData.keyCode);
      assertEquals(testData.toString(), testData.expectKeyCode, primaryCodeKeyEvent.getKeyCode());
    }

    verifyAll();
  }
}
