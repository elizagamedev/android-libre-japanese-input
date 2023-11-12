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

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityRecord
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateWord
import sh.eliza.japaneseinput.accessibility.AccessibilityEventUtil.createAccessibilityEvent
import sh.eliza.japaneseinput.ui.CandidateLayout

/**
 * Represents candidate window's virtual structure.
 *
 * Note about virtual view ID: This class uses `CandidateWord`'s `id` as virtual view ID.
 */
internal class CandidateWindowAccessibilityNodeProvider(
  private val view: View,
) : AccessibilityNodeProviderCompat() {
  private val accessibilityManager =
    view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
  private var layout: CandidateLayout? = null

  // Caches only Row. Caches Span might be slow on construction.
  private var virtualViewIdToRow: SparseArray<CandidateLayout.Row>? = null

  // Virtual ID of focused (in the light of accessibility) view.
  private var virtualFocusedViewId = UNDEFINED_VIRTUAL_VIEW_ID

  private val context: Context
    get() = view.context

  /** Sets updated layout and resets virtual view structure. */
  fun setCandidateLayout(layout: CandidateLayout?) {
    this.layout = layout
    resetVirtualStructure()
  }

  /**
   * Returns a `Row` which contains a `CandidateWord` of which the `id` is given `virtualViewId`.
   */
  private fun getRowByVirtualViewId(virtualViewId: Int): CandidateLayout.Row? {
    if (virtualViewIdToRow == null) {
      val layout = layout ?: return null
      val virtualViewIdToRow = SparseArray<CandidateLayout.Row>()
      for (row in layout.rowList) {
        for (span in row.spanList) {
          if (span.candidateWord.isPresent) {
            // - Skip reserved empty span, which is for folding button.
            // - Use append method expecting that the id is in ascending order.
            //   Even if not, it works well.
            virtualViewIdToRow.append(candidateIdToVirtualId(span.candidateWord.get().id), row)
          } else {
            virtualViewIdToRow.append(FOLD_BUTTON_VIRTUAL_VIEW_ID, row)
          }
        }
      }
      this.virtualViewIdToRow = virtualViewIdToRow
    }
    return virtualViewIdToRow?.get(virtualViewId)
  }

  private fun createNodeInfoForVirtualViewId(virtualViewId: Int): AccessibilityNodeInfoCompat? {
    Preconditions.checkArgument(virtualViewId >= 0)
    val row = getRowByVirtualViewId(virtualViewId) ?: return null
    val candidateId = virtualViewIdToCandidateId(virtualViewId)
    for (span in row.spanList) {
      val candidateWord = span.candidateWord.orNull()
      if (candidateWord != null && candidateWord.id != candidateId) {
        continue
      }
      val info = createNodeInfoForSpan(virtualViewId, row, span)
      info.contentDescription =
        if (candidateWord != null) getContentDescription(candidateWord) else null
      info.addAction(
        if (virtualFocusedViewId == virtualViewId) {
          AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS
        } else {
          AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS
        }
      )
      return info
    }
    return null
  }

  private fun createNodeInfoForSpan(
    virtualViewId: Int,
    row: CandidateLayout.Row,
    span: CandidateLayout.Span
  ): AccessibilityNodeInfoCompat {
    val parentLocationOnScreen = IntArray(2)
    view.getLocationOnScreen(parentLocationOnScreen)
    val boundsInScreen =
      Rect(span.left.toInt(), row.top.toInt(), span.right.toInt(), (row.top + row.height).toInt())
    boundsInScreen.offset(parentLocationOnScreen[0], parentLocationOnScreen[1])
    return AccessibilityNodeInfoCompat.obtain().apply {
      packageName = context.packageName
      className = CandidateLayout.Span::class.java.name
      setBoundsInScreen(boundsInScreen)
      setParent(view)
      setSource(view, virtualViewId)
      isEnabled = true
      isVisibleToUser = true
    }
  }

  override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfoCompat? {
    if (virtualViewId == UNDEFINED_VIRTUAL_VIEW_ID) {
      return null
    }
    if (virtualViewId == View.NO_ID) {
      // Required to return the information about entire view.
      val info = AccessibilityNodeInfoCompat.obtain(view)
      ViewCompat.onInitializeAccessibilityNodeInfo(view, info)
      val layout = layout ?: return info
      for (row in layout.rowList) {
        for (span in row.spanList) {
          val candidateWord = span.candidateWord.orNull()
          info.addChild(
            view,
            if (candidateWord != null) {
              // Skip reserved empty span, which is for folding button.
              candidateIdToVirtualId(candidateWord.id)
            } else {
              FOLD_BUTTON_VIRTUAL_VIEW_ID
            }
          )
        }
      }
      return info
    }
    return createNodeInfoForVirtualViewId(virtualViewId)
  }

  private fun resetVirtualStructure() {
    virtualViewIdToRow = null
    if (accessibilityManager.isEnabled) {
      val event = createAccessibilityEvent()
      view.onInitializeAccessibilityEvent(event)
      event.eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
      accessibilityManager.sendAccessibilityEvent(event)
    }
  }

  /**
   * Returns a `CandidateWord` based on given position if available.
   *
   * @param x horizontal location in screen coordinate (pixel)
   * @param y vertical location in screen coordinate (pixel)
   */
  fun getCandidateWord(x: Int, y: Int): CandidateWord? {
    val layout = layout ?: return null
    for (row in layout.rowList) {
      if (y < row.top || y >= row.top + row.height) {
        continue
      }
      for (span in row.spanList) {
        if (x >= span.left && x < span.right) {
          return span.candidateWord.orNull()
        }
      }
    }
    return null
  }

  fun sendAccessibilityEventForCandidateWordIfAccessibilityEnabled(
    candidateWord: CandidateWord,
    eventType: Int
  ) {
    if (accessibilityManager.isEnabled) {
      val event = createAccessibilityEvent(candidateWord, eventType)
      accessibilityManager.sendAccessibilityEvent(event)
    }
  }

  private fun createAccessibilityEvent(
    candidateWord: CandidateWord,
    eventType: Int
  ): AccessibilityEvent {
    val event =
      createAccessibilityEvent(eventType).apply {
        packageName = context.packageName
        className = candidateWord.javaClass.name
        contentDescription = getContentDescription(candidateWord)
        isEnabled = true
      }
    (event as AccessibilityRecord).setSource(view, candidateIdToVirtualId(candidateWord.id))
    return event
  }

  /** Returns content description based on value and annotation. */
  private fun getContentDescription(candidateWord: CandidateWord): String {
    var contentDescription = Strings.nullToEmpty(candidateWord.value)
    if (candidateWord.hasAnnotation() && candidateWord.annotation.hasDescription()) {
      contentDescription += " " + candidateWord.annotation.description
    }
    return contentDescription
  }

  private fun getCandidateWordFromVirtualViewId(virtualViewId: Int): CandidateWord? {
    val row = getRowByVirtualViewId(virtualViewId) ?: return null
    val candidateId = virtualViewIdToCandidateId(virtualViewId)
    for (span in row.spanList) {
      val candidateWord = span.candidateWord.orNull()
      if (candidateWord != null && candidateWord.id == candidateId) {
        return candidateWord
      }
    }
    return null
  }

  override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
    val candidateWord = getCandidateWordFromVirtualViewId(virtualViewId)
    return (candidateWord != null &&
      performActionForCandidateWordInternal(candidateWord, virtualViewId, action))
  }

  fun performActionForCandidateWord(
    candidateWord: CandidateWord,
    actionAccessibilityFocus: Int
  ): Boolean {
    return performActionForCandidateWordInternal(
      candidateWord,
      candidateIdToVirtualId(candidateWord.id),
      actionAccessibilityFocus
    )
  }

  private fun performActionForCandidateWordInternal(
    candidateWord: CandidateWord,
    virtualViewId: Int,
    action: Int
  ): Boolean {
    Preconditions.checkArgument(virtualViewId >= 0)
    return when (action) {
      AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS -> {
        if (virtualFocusedViewId == virtualViewId) {
          // If focused virtual view is unchanged, do nothing.
          return false
        }
        // Framework requires the candidate window to have focus.
        // Return FOCUSED event to the framework as response.
        virtualFocusedViewId = virtualViewId
        if (accessibilityManager.isEnabled) {
          accessibilityManager.sendAccessibilityEvent(
            createAccessibilityEvent(
              candidateWord,
              AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED
            )
          )
        }
        true
      }
      AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
        // Framework requires the candidate window to clear focus.
        // Return FOCUSE_CLEARED event to the framework as response.
        if (virtualFocusedViewId != virtualViewId) {
          return false
        }
        virtualFocusedViewId = UNDEFINED_VIRTUAL_VIEW_ID
        if (accessibilityManager.isEnabled) {
          accessibilityManager.sendAccessibilityEvent(
            createAccessibilityEvent(
              candidateWord,
              AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            )
          )
        }
        true
      }
      else -> false
    }
  }
}

private const val UNDEFINED_VIRTUAL_VIEW_ID = Int.MAX_VALUE
private const val FOLD_BUTTON_VIRTUAL_VIEW_ID = Int.MAX_VALUE - 1

// Negative virtual id makes Talkback confused (to be more exact, negative values
// seems to be _reserved_).
// We used to use candidate ID as virtual view id but it violates Talkback's
// assumption.
// By adding this offset we can avoid negative virtual view ID.
private const val VIRTUAL_VIEW_ID_OFFSET = 10000

private fun virtualViewIdToCandidateId(virtualViewId: Int): Int {
  return if (virtualViewId == UNDEFINED_VIRTUAL_VIEW_ID ||
      virtualViewId == FOLD_BUTTON_VIRTUAL_VIEW_ID
  ) {
    virtualViewId
  } else virtualViewId - VIRTUAL_VIEW_ID_OFFSET
}

private fun candidateIdToVirtualId(candidateId: Int): Int {
  Preconditions.checkArgument(
    candidateId != UNDEFINED_VIRTUAL_VIEW_ID && candidateId != FOLD_BUTTON_VIRTUAL_VIEW_ID
  )
  return candidateId + VIRTUAL_VIEW_ID_OFFSET
}
