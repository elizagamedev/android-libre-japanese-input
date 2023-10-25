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

package org.mozc.android.inputmethod.japanese.model;

import org.mozc.android.inputmethod.japanese.MozcLog;
import org.mozc.android.inputmethod.japanese.MozcUtil;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.DeletionRange;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit.Segment;
import com.google.common.base.MoreObjects;

import android.util.Log;

import java.util.ArrayDeque;

/**
 * This class tracks the caret position based on the callback from MozcService.
 *
 * In order to reset the context at appropriate timing, we need to find an unknown event
 * from connected applications. The only way to know that the text field is updated outside
 * from MozcSerivce is using onUpdateSelection. Unfortunately there is no function to get
 * the current selection position via InputConnection, we need to track all the caret positions
 * by calculating them from the result of the mozc server, initial caret positions and
 * the onUpdateSelection's arguments.
 *
 * However, the behavior of the callback doesn't look standardized. Actually it's called
 * differently from various applications. One of the biggest clients for IME is EditText and
 * WebTextView widgets (i.e. browsers), but they have also different behaviors...
 *
 * This class is introduced to fill the gap as much as possible.
 *
 */
public class SelectionTracker {

  /**
   * This is a update log by Mozc in order to support onUpdatedSelection.
   * See details in the comments in {@link SelectionTracker#onUpdateSelection}.
   */
  static class Record {
    final int candidatesStart;
    final int candidatesEnd;
    final int selectionStart;
    final int selectionEnd;

    Record(int candidatesStart, int candidatesEnd, int selectionStart, int selectionEnd) {
      this.candidatesStart = candidatesStart;
      this.candidatesEnd = candidatesEnd;
      this.selectionStart = selectionStart;
      this.selectionEnd = selectionEnd;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Record)) {
        return false;
      }
      Record other = Record.class.cast(obj);
      return (candidatesStart == other.candidatesStart)
          && (candidatesEnd == other.candidatesEnd)
          && (selectionStart == other.selectionStart)
          && (selectionEnd == other.selectionEnd);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("candidates", candidatesStart).addValue(candidatesEnd)
          .add("selection", selectionStart).addValue(selectionEnd).toString();
    }

    // Skipped to implement hashCode intentionally, as we don't expect use it.
  }

  // In order to keep the update record log small, we'll limit the number of records.
  // It shouldn't be problem basically, because the onUpdateExtractedText should be frequently
  // invoked so that the number of records kept in updateRecordList should be small in typical
  // cases. This also prevents OOM killer due to no-invocation of onUpdateExtractedText
  // from a connected application.
  private static final int MAX_UPDATE_RECORD_QUEUE_SIZE = 50;

  // Return value for the onUpdateSelection.
  public static final int DO_NOTHING = -1;
  public static final int RESET_CONTEXT = -2;

  private final ArrayDeque<Record> recordQueue =
      new ArrayDeque<Record>(MAX_UPDATE_RECORD_QUEUE_SIZE);
  private int initialSelectionStart;
  private int initialSelectionEnd;
  private boolean webTextView;

  private void clear() {
    recordQueue.clear();
    if (MozcLog.isLoggable(Log.DEBUG)) {
      MozcLog.d("clear: " + toString());
    }
  }

  private void offerInternal(int candidatesStart, int candidatesEnd,
                             int selectionStart, int selectionEnd) {
    while (recordQueue.size() >= MAX_UPDATE_RECORD_QUEUE_SIZE) {
      recordQueue.removeFirst();
    }
    recordQueue.offerLast(
        new Record(candidatesStart, candidatesEnd, selectionStart, selectionEnd));
    if (MozcLog.isLoggable(Log.DEBUG)) {
      MozcLog.d("offerInternal: " + toString());
    }
  }

  public void onStartInput(
      int initialSelectionStart, int initialSelectionEnd, boolean webTextView) {
    if (MozcLog.isLoggable(Log.DEBUG)) {
      MozcLog.d(String.format("onStartInput: %d %d %b",
                              initialSelectionStart, initialSelectionEnd, webTextView));
    }
    this.webTextView = webTextView;

    if (initialSelectionStart == -1 && initialSelectionEnd == -1) {
      // Ignores (-1, -1).
      // This case can be observed when the IME is not connected to any field.
      return;
    }

    clear();
    offerInternal(-1, -1, initialSelectionStart, initialSelectionEnd);

    this.initialSelectionStart = initialSelectionStart;
    this.initialSelectionEnd = initialSelectionEnd;
  }

  public void onFinishInput() {
    // The input flow is finished.
    clear();
  }

  public void onConfigurationChanged() {
    // The Configuration such as orientation is changed, so we reset the context.
    clear();
  }

  public void onWindowHidden() {
    Record last = recordQueue.peekLast();
    if (last == null) {
      return;
    }

    // Remember the latest position of the selection (caret). It will be used when the input view
    // is re-shown, and a user re-starts the input.
    clear();
    offerInternal(-1, -1, last.selectionStart, last.selectionEnd);
  }

  public int getLastSelectionStart() {
    Record record = recordQueue.peekLast();
    if (record == null) {
      return -1;
    }
    return record.selectionStart;
  }

  public int getLastSelectionEnd() {
    Record record = recordQueue.peekLast();
    if (record == null) {
      return -1;
    }
    return record.selectionEnd;
  }

  public int getPreeditStartPosition() {
    Record record = recordQueue.peekLast();
    if (record != null) {
      // Use candidatesStart, if exists.
      int candidatesStart = record.candidatesStart;

      if (candidatesStart == -1) {
        // If candidatesStart is -1, i.e. we don't have preedit, use the current caret position.
        candidatesStart = Math.min(record.selectionStart, record.selectionEnd);
      }

      // To avoid un-expected case, we guard the result to positive value.
      if (candidatesStart >= 0) {
        return candidatesStart;
      }
    }

    // There are no record yet. So we'll use initialSelection as a fallback.
    {
      int caretPosition = Math.min(initialSelectionStart, initialSelectionEnd);
      if (caretPosition >= 0) {
        return caretPosition;
      }
    }

    // When both are failed, give up to return the correct position.
    return -1;
  }

  /**
   * Should be invoked when the MozcService sends text to the connected application.
   */
  public void onRender(DeletionRange deletionRange, String commitText, Preedit preedit) {
    if (MozcLog.isLoggable(Log.DEBUG)) {
      MozcLog.d("onRender: " + MoreObjects.firstNonNull(preedit, "").toString());
    }
    int preeditStartPosition = getPreeditStartPosition();
    if (deletionRange != null) {
      // Note that deletionRange#getOffset usually returns negative value.
      preeditStartPosition += deletionRange.getOffset();
    }

    if (commitText != null) {
      preeditStartPosition += commitText.length();
    }

    if (preedit == null) {
      // If we don't render the preedit, just remember the caret position.
      // It should be at preeditStartPosition.
      offerInternal(-1, -1, preeditStartPosition, preeditStartPosition);
      return;
    }

    // Here is the most complicated situation.
    int preeditEndPosition = preeditStartPosition;
    for (Segment segment : preedit.getSegmentList()) {
      preeditEndPosition += segment.getValue().length();
    }

    if (webTextView) {
      // We expect onUpdateSelection invocation with appropriate
      // newSel{Start,End} and candidates{Start,End} followed by this invocation.
      // However, web view invokes the callback with unexpected arguments.
      // The affected application's usage would be so huge (c.f. "browser" is affected), so
      // we have additional handling for it here.
      //
      // For one rendering, web view behavior seems like;
      // 1) move the caret to the beginning or end point of the previous composition string,
      //    with no composition string (candidatesStart = candidatesEnd = -1).
      // 2) then, move the caret to appropriate position, with correct composition string info.
      // As the result, onUpdateSelection is usually invoked twice, with above arguments.
      // Without any handling, MozcService misunderstands "an unknown event is happen in the
      // connected application so that it needs to reset itself."
      //
      // Unfortunately, it is much difficult to handle above case in onUpdateSelection, because
      // 1)-callback's arguments are similar to ones of an actual unknown user's event.
      // So instead, we add a "dummy" update record here, so that 1) event will be known event
      // and MozcService won't reset itself.
      //
      // There is an exceptional case for 1). If the last position is already at the end of the
      // previous composition string, 1) looks "just a staying caret". So, the 1) will be simply
      // skipped. Regardless of such exceptional cases, 2)'s event will happen.
      Record record = recordQueue.peekLast();
      if (record != null &&
          (record.selectionStart != record.selectionEnd ||
           record.selectionStart != record.candidatesEnd)) {
        if (record.candidatesStart == -1) {
          // If no candidates are available, the right position of selection{Start,End} is
          // considered as the dummy caret position.
          int dummyCaretPosition = Math.max(record.selectionStart, record.selectionEnd);
          offerInternal(-1, -1, dummyCaretPosition, dummyCaretPosition);
        } else {
          // Otherwise set the dummy caret position to the begin or end of the candidate
          // based on the cursor position.
          int dummyCaretPosition =
              (preedit.getCursor() <= 0) ? record.candidatesStart : record.candidatesEnd;
          offerInternal(-1, -1, dummyCaretPosition, dummyCaretPosition);
        }
      }
    }

    int caretPosition = preeditStartPosition + preedit.getCursor();
    offerInternal(preeditStartPosition, preeditEndPosition, caretPosition, caretPosition);
  }

  /**
   * @return true if any record has the same candidate length of given {@code record}
   */
  private boolean containsSeeingOnlyCandidateLength(Record record) {
    int recoredLength = Math.abs(record.candidatesStart - record.candidatesEnd);
    for (Record recorded : recordQueue) {
      if (Math.abs(recorded.candidatesStart - recorded.candidatesEnd) == recoredLength) {
        return true;
      }
    }
    return false;
  }

  /**
   * Should be invoked when MozcServer receives the callback {@code onUpdateSelection}.
   * @return the move cursor position, or one of special values
   *    {@code DO_NOTHING, RESET_CONTEXT}. The caller should follow the result.
   */
  public int onUpdateSelection(int oldSelStart, int oldSelEnd,
                               int newSelStart, int newSelEnd,
                               int candidatesStart, int candidatesEnd,
                               boolean isIgnoringMoveToTail) {
    if (MozcLog.isLoggable(Log.DEBUG)) {
      MozcLog.d(String.format("onUpdateSelection: %d %d %d %d %d %d",
                              oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                              candidatesStart, candidatesEnd));
      MozcLog.d(recordQueue.toString());
    }
    Record record = new Record(candidatesStart, candidatesEnd, newSelStart, newSelEnd);

    // There are four cases to come here.
    // 1) Framework invokes this callback when the caret position is updated due to the text
    //    change from IME, i.e. MozcService.
    // 2-1) During composition, users can move the caret position by tapping somewhere around the
    //    current preedit text.
    //    NOTE: ApplicationCompatibility#isIgnoringTapEvent() should be taken care of.
    // 2-2) During composition, users can make a selection region by long-tapping somewhere text.
    // 3) Unexpected cursor/selection moving coming from outside of MozcService.

    // At first, we checks 1) state.
    if (recordQueue.contains(record)) {
      // This is case 1). Discard preceding records, because on some devices, some callbacks are
      // just skipped.
      while (!recordQueue.isEmpty()) {
        Record head = recordQueue.peekFirst();
        if (record.equals(head)) {
          // Note: keep the last record.
          break;
        }
        recordQueue.removeFirst();
      }
      return DO_NOTHING;
    }

    // Here, the event is not caused by MozcService (probably).
    Record lastRecord = recordQueue.peekLast();
    if (lastRecord != null &&
        lastRecord.candidatesStart >= 0 &&
        lastRecord.candidatesStart == candidatesStart &&
        lastRecord.candidatesEnd == candidatesEnd) {
      // This is the case 2).
      // Remember the new position.
      clear();
      offerInternal(candidatesStart, candidatesEnd, newSelStart, newSelEnd);
      if (newSelStart == newSelEnd) {
        // This is case 2-1)
        // In composition with tapping somewhere.
        int newPosition = MozcUtil.clamp(newSelStart - candidatesStart,
                                         0, candidatesEnd - candidatesStart);
        // Ignore move to the tail when isIgnoringMoveToTail is set.
        if (isIgnoringMoveToTail && newPosition == candidatesEnd - candidatesStart) {
          return DO_NOTHING;
        }
        return newPosition;
      }

      // This is case 2-2).
      // Commit the composing text and reset the context. So that, in the next turn,
      // the user can edit the selected region as usual.
      return RESET_CONTEXT;
    }

    // Here is the case 3), i.e. totally unknown state.
    // This can happen, e.g.,
    // - the cursor is moved when there are no preedit
    // - the text message is sent to the chat by tapping sending button
    // - the field is filled by the application's suggestion
    // Thus, we reset the context.
    // But on problematic views, which don't call onUpdateSelection when there is not preedit
    // (e.g. WebView), execution flow reaches here unexpectedly.
    // In such case the context is reset unexpectedly, which causes serious unpleasantness.
    // Therefore fall-back logic is implemented here.
    // If any recorded entry has given candidate length (candidatesEnd - candidatesStart),
    // reset the queue and return DO_NOTHING instead. Such recored entry was recorded in
    // previous call of onRender.
    // For example on problematic views following scenario would be seen.
    // - onRender (commit)
    // - onUpdateSelection (caused by last commit)
    // - undetectable cursor move (causes record inconsistency)
    // - onRender (records invalid entry but its length is correct)
    // - onUpdateSelection (here)
    //   - Records are basically unreliable but the last one has correct length)
    if (candidatesStart != -1 || candidatesEnd != -1) {
      if (MozcLog.isLoggable(Log.DEBUG)) {
        MozcLog.d("Unknown candidates: " + candidatesStart + ":" + candidatesEnd);
      }
      if (webTextView && containsSeeingOnlyCandidateLength(record)) {
        if (MozcLog.isLoggable(Log.DEBUG)) {
          MozcLog.d(String.format(
              "Fall-back is applied as " +
                  "there is a entry of which the candidate length (%d) meets expectation.",
              candidatesEnd - candidatesStart));
        }
        clear();
        offerInternal(candidatesStart, candidatesEnd, newSelStart, newSelEnd);
        return DO_NOTHING;
      }
    }

    // For the next handling, we should remember the newest position.
    clear();
    offerInternal(-1, -1, newSelStart, newSelEnd);

    // Tell the caller to reset the context.
    return RESET_CONTEXT;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("recordQueue", recordQueue)
        .add("initialSelectionStart", initialSelectionStart)
        .add("initialSelectionEnd", initialSelectionEnd)
        .add("webTextView", webTextView)
        .toString();
  }
}
