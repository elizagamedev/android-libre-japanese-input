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

package org.mozc.android.inputmethod.japanese.testing;

import static org.easymock.EasyMock.reportMatcher;

import org.mozc.android.inputmethod.japanese.KeycodeConverter.KeyEventInterface;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.protobuf.Message;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.KeyEvent;

import org.easymock.Capture;
import org.easymock.IArgumentMatcher;

import java.util.Arrays;

/**
 * This class provides Matcher methods for EasyMock (AndroidMock).
 *
 */
public class MozcMatcher {

  /**
   * Capture for Paint, with deep copy.
   *
   * Capture holds only reference so it cannot be used for capturing the snapshot.
   * This subclass hold copied instance instead.
   */
  public static class DeepCopyPaintCapture extends Capture<Paint> {

    @Override
    public void setValue(Paint value) {
      // Don't user Paint(Paint) constructor, which doesn't copy the fields.
      // This behavior is fixed new OS but older ones have this issue.
      Paint newValue = new Paint();
      newValue.set(value);
      super.setValue(newValue);
    }
  }

  /**
   * Matcher implementation for protobuf's MessageBuilder.
   */
  private static class MessageBuilderMatcher implements IArgumentMatcher {
    private final Message expected;

    MessageBuilderMatcher(Message.Builder builder) {
      this.expected = Preconditions.checkNotNull(builder).buildPartial();
    }

    @Override
    public void appendTo(StringBuffer buff) {
      buff.append("matchesBuilder(")
          .append(expected.toString())
          .append(")");
    }

    @Override
    public boolean matches(Object arg) {
      if (!(arg instanceof Message.Builder)) {
        return false;
      }
      return expected.equals(Message.Builder.class.cast(arg).buildPartial());
    }
  }

  /**
   * Matcher implementation for KeyEvent, which matches action and key code.
   */
  private static class KeyEventMatcher implements IArgumentMatcher {

    private final int expectedKeyCode;
    private final Optional<android.view.KeyEvent> expectedNativeKeyEvent;

    KeyEventMatcher(int expectedKeyCode) {
      this.expectedKeyCode = expectedKeyCode;
      this.expectedNativeKeyEvent = Optional.absent();
    }
    KeyEventMatcher(android.view.KeyEvent expectedNativeKeyEvent) {
      this.expectedKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN;
      this.expectedNativeKeyEvent = Optional.of(expectedNativeKeyEvent);
    }

    @Override
    public void appendTo(StringBuffer sb) {
      Preconditions.checkNotNull(sb);
      sb.append("matchesKeyEvent(");
      sb.appendCodePoint(expectedKeyCode);
      sb.append(")");
    }

    @Override
    public boolean matches(Object arg) {
      if (!(arg instanceof KeyEventInterface)) {
        return false;
      }
      KeyEventInterface keyEvent = KeyEventInterface.class.cast(arg);
      if (expectedNativeKeyEvent.isPresent()) {
        Optional<KeyEvent> nativeEvent = keyEvent.getNativeEvent();
        return nativeEvent.isPresent() && (nativeEvent.get() == expectedNativeKeyEvent.get());
      }
      return !keyEvent.getNativeEvent().isPresent() && keyEvent.getKeyCode() == expectedKeyCode;
    }
  }

  /**
   * Matcher implementation for Canvas, which matches expected matrix conversion given by
   * sourcePoints and targetPoints.
   */
  private static class CanvasPointMatcher implements IArgumentMatcher {

    private final float[] sourcePoints;
    private final float[] targetPoints;

    CanvasPointMatcher(float[] sourcePoints, float[] targetPoints) {
      Preconditions.checkNotNull(sourcePoints);
      Preconditions.checkNotNull(targetPoints);
      if (sourcePoints.length != targetPoints.length) {
        throw new IllegalArgumentException(
            "The length of sourcePoints and one of targetPoints shoudld be same. "
                + "sourcePoints.length: " + sourcePoints.length + ", "
                + "targetPoints.length: " + targetPoints.length);
      }
      this.sourcePoints = sourcePoints;
      this.targetPoints = targetPoints;
    }

    @Override
    public void appendTo(StringBuffer buff) {
      buff.append("matchCanvasPoint(")
          .append(Arrays.toString(sourcePoints))
          .append(", ")
          .append(Arrays.toString(targetPoints))
          .append(")");
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean matches(Object arg) {
      if (!(arg instanceof Canvas)) {
        return false;
      }

      Canvas canvas = Canvas.class.cast(arg);
      float[] points = sourcePoints.clone();
      canvas.getMatrix().mapPoints(points);
      return Arrays.equals(targetPoints, points);
    }
  }

  /**
   * Matcher implementation for Optional, which contains a same object.
   */
  private static class SameOptionalMatcher<T> implements IArgumentMatcher {

    private final T expectedValue;

    SameOptionalMatcher(T value) {
      this.expectedValue = Preconditions.checkNotNull(value);
    }

    @Override
    public void appendTo(StringBuffer buff) {
      buff.append("matchOptional(")
          .append(expectedValue)
          .append(")");
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean matches(Object arg) {
      if (!(arg instanceof Optional)) {
        return false;
      }
      Optional targetValue = Optional.class.cast(arg);
      return expectedValue == targetValue.orNull();
    }
  }

  // Disallow instantiation.
  private MozcMatcher() {
  }

  /**
   * Expects a MessageBuilder which contains same information as the given {@code builder}.
   */
  public static <T extends Message.Builder> T matchesBuilder(T builder) {
    reportMatcher(new MessageBuilderMatcher(builder));
    return null;
  }

  /**
   * Expects a {@link KeyEventInterface}, whose key code match the given {@code keyCode}.
   */
  public static KeyEventInterface matchesKeyEvent(int keyCode) {
    reportMatcher(new KeyEventMatcher(keyCode));
    return null;
  }

  /**
   * Expects a {@link KeyEventInterface}, whose key code match the given {@code nativeKeyEvent}.
   */
  public static KeyEventInterface matchesKeyEvent(android.view.KeyEvent nativeKeyEvent) {
    reportMatcher(new KeyEventMatcher(nativeKeyEvent));
    return null;
  }

  /**
   * Expects a {@link Canvas}, whose matrix matches the one given by the pair of
   * {@code sourcePoints} and {@code targetPoints}.
   */
  public static Canvas matchCanvasPoints(float[] sourcePoints, float[] targetPoints) {
    reportMatcher(new CanvasPointMatcher(sourcePoints, targetPoints));
    return null;
  }

  /**
   * Expects a {@link Optional}, which contains a same object.
   */
  public static <T> Optional<T> sameOptional(T value) {
    reportMatcher(new SameOptionalMatcher<T>(value));
    return null;
  }
}
