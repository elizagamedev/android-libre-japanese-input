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
package sh.eliza.japaneseinput.accessibility

import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityRecord
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import com.google.common.base.Preconditions
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateWord
import sh.eliza.japaneseinput.ui.CandidateLayout

/**
 * Delegate object for candidate view to support accessibility.
 *
 * This class is similar to `KeyboardAccessibilityDelegate` but the behavior is different. It is not
 * good idea to extract common behavior as super class.
 */
// TODO(matsuzakit): It seems that TYPE_VIEW_SCROLLED event from IME cannot reach to accessibility
//                   service. Alternative solution might be required.
class CandidateWindowAccessibilityDelegate(
  private val view: View,
) : AccessibilityDelegateCompat() {
  private val nodeProvider: CandidateWindowAccessibilityNodeProvider =
    CandidateWindowAccessibilityNodeProvider(view)
  private var lastHoverCandidateWord: CandidateWord? = null

  // Size of whole content in pixel.
  private var contentSize = 0

  // Size of view in pixel.
  private var viewSize = 0

  override fun getAccessibilityNodeProvider(view: View): AccessibilityNodeProviderCompat {
    return nodeProvider
  }

  /**
   * Sets (updated) candidate layout.
   *
   * Should be called when the view's candidate layout is updated.
   */
  fun setCandidateLayout(layout: CandidateLayout?, contentSize: Int, viewSize: Int) {
    Preconditions.checkArgument(contentSize >= 0)
    Preconditions.checkArgument(viewSize >= 0)
    nodeProvider.setCandidateLayout(layout)
    this.contentSize = contentSize
    this.viewSize = viewSize
    sendAccessibilityEvent(view, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
  }

  /**
   * Dispatched from `View#dispatchHoverEvent`.
   *
   * @return `true` if the event was handled by the view, false otherwise
   */
  fun dispatchHoverEvent(event: MotionEvent): Boolean {
    val candidateWord =
      nodeProvider.getCandidateWord(event.x.toInt(), event.y.toInt() + view.scrollY)
    val lastHoverCandidateWord = lastHoverCandidateWord
    when (event.action) {
      MotionEvent.ACTION_HOVER_ENTER -> {
        Preconditions.checkState(lastHoverCandidateWord == null)
        if (candidateWord != null) {
          // Notify the user that we are entering new virtual view.
          nodeProvider.sendAccessibilityEventForCandidateWordIfAccessibilityEnabled(
            candidateWord,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
          )
          // Make virtual view focus on the candidate.
          nodeProvider.performActionForCandidateWord(
            candidateWord,
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
          )
        }
        this.lastHoverCandidateWord = candidateWord
      }
      MotionEvent.ACTION_HOVER_EXIT -> {
        if (candidateWord != null) {
          // Notify the user that we are exiting from the candidate.
          nodeProvider.sendAccessibilityEventForCandidateWordIfAccessibilityEnabled(
            candidateWord,
            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT
          )
          // Make virtual view unfocused.
          nodeProvider.performActionForCandidateWord(
            candidateWord,
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
          )
        }
        this.lastHoverCandidateWord = null
      }
      MotionEvent.ACTION_HOVER_MOVE -> {
        if (candidateWord != lastHoverCandidateWord) {
          if (lastHoverCandidateWord != null) {
            // Notify the user that we are exiting from lastHoverCandidateWord.
            nodeProvider.sendAccessibilityEventForCandidateWordIfAccessibilityEnabled(
              lastHoverCandidateWord,
              AccessibilityEvent.TYPE_VIEW_HOVER_EXIT
            )
            // Make virtual view unfocused.
            nodeProvider.performActionForCandidateWord(
              lastHoverCandidateWord,
              AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS
            )
          }
          if (candidateWord != null) {
            // Notify the user that we are entering new virtual view.
            nodeProvider.sendAccessibilityEventForCandidateWordIfAccessibilityEnabled(
              candidateWord,
              AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
            )
            // Make virtual view focus on the candidate.
            nodeProvider.performActionForCandidateWord(
              candidateWord,
              AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
            )
          }
          this.lastHoverCandidateWord = candidateWord
        }
      }
    }
    return candidateWord != null
  }

  override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) {
    super.onInitializeAccessibilityEvent(host, event)
    (event as AccessibilityRecord).run {
      isScrollable = viewSize < contentSize
      scrollX = 0
      scrollY = view.scrollY
      maxScrollX = 0
      maxScrollY = contentSize
    }
  }

  override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
    super.onInitializeAccessibilityNodeInfo(host, info)
    info.run {
      className = javaClass.name
      isScrollable = viewSize < contentSize
      isFocusable = true
    }
    val scrollY = view.scrollY
    if (scrollY > 0) {
      info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }
    if (scrollY < contentSize - viewSize) {
      info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
  }
}
