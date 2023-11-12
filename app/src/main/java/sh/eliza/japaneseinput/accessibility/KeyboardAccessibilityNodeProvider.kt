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
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityRecord
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeProviderCompat
import sh.eliza.japaneseinput.MozcLog
import sh.eliza.japaneseinput.accessibility.AccessibilityEventUtil.createAccessibilityEvent
import sh.eliza.japaneseinput.keyboard.Flick
import sh.eliza.japaneseinput.keyboard.Key
import sh.eliza.japaneseinput.keyboard.KeyState
import sh.eliza.japaneseinput.keyboard.KeyState.MetaState
import sh.eliza.japaneseinput.keyboard.Keyboard

/**
 * Represents keyboard's virtual structure.
 *
 * Note about virtual view ID: This class uses `Key`'s `sourceId` as virtual view ID. It is changed
 * by metastate of the keyboard.
 */
internal class KeyboardAccessibilityNodeProvider(
  // View for keyboard.
  private val view: View,
) : AccessibilityNodeProviderCompat() {
  private val accessibilityManager =
    view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

  // Keyboard model.
  private var keyboard: Keyboard? = null

  // Keys in the keyboard.
  // Don't access directly. Use #getKeys() instead for lazy creation.
  private var keys: Collection<Key>? = null

  // Virtual ID of focused (in the light of accessibility) view.
  private var virtualFocusedViewId = UNDEFINED
  private var metaState = emptySet<MetaState>()

  private val context: Context
    get() = view.context

  /**
   * Returns all the keys in the `keyboard`.
   *
   * Lazy creation is done inside.
   *
   * If `keyboard` is not set, empty collection is returned.
   */
  private fun getKeys(): Collection<Key> {
    val keys = keys
    if (keys != null) {
      return keys
    }
    val keyboard = keyboard ?: return emptyList()
    // Initial size is estimated roughly.
    val tempKeys = ArrayList<Key>(keyboard.rowList.size * 10)
    for (row in keyboard.rowList) {
      for (key in row.keyList) {
        if (!key.isSpacer) {
          tempKeys.add(key)
        }
      }
    }
    this.keys = tempKeys
    return tempKeys
  }

  /**
   * Returns a `Key` based on given position.
   *
   * @param x horizontal location in screen coordinate (pixel)
   * @param y vertical location in screen coordinate (pixel)
   */
  fun getKey(x: Int, y: Int): Key? {
    for (key in getKeys()) {
      val left = key.x
      if (left > x) {
        continue
      }
      val right = left + key.width
      if (right <= x) {
        continue
      }
      val top = key.y
      if (top > y) {
        continue
      }
      val bottom = top + key.height
      if (bottom <= y) {
        continue
      }
      return key
    }
    return null
  }

  override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfoCompat? {
    if (virtualViewId == UNDEFINED) {
      return null
    }
    if (virtualViewId == View.NO_ID) {
      // Required to return the information about keyboardView.
      // In old Android OS AccessibilityNodeInfoCompat.obtain() returns null.
      val info = AccessibilityNodeInfoCompat.obtain(view) ?: return null
      ViewCompat.onInitializeAccessibilityNodeInfo(view, info)
      // Add the virtual children of the root View.
      for (key in getKeys()) {
        // NOTE: sourceID is always non-negative
        // so it can be added to AccessibilityNodeInfoCompat safely.
        info.addChild(view, getSourceId(key))
      }
      return info
    }
    // Required to return the information about child view (== key).
    // Find the view that corresponds to the given id.
    val key = getKeyFromSouceId(virtualViewId)
    if (key == null) {
      MozcLog.e("Virtual view id $virtualViewId is not found")
      return null
    }
    val parentLocationOnScreen = IntArray(2)
    view.getLocationOnScreen(parentLocationOnScreen)
    val boundsInScreen = Rect(key.x, key.y, key.x + key.width, key.y + key.height)
    boundsInScreen.offset(parentLocationOnScreen[0], parentLocationOnScreen[1])
    return AccessibilityNodeInfoCompat.obtain()?.apply {
      packageName = context.packageName
      className = key.javaClass.name
      contentDescription = getContentDescription(key)
      setBoundsInScreen(boundsInScreen)
      setParent(view)
      setSource(view, virtualViewId)
      isEnabled = true
      isVisibleToUser = true
      addAction(
        if (virtualFocusedViewId == virtualViewId) {
          AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS
        } else {
          AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS
        }
      )
    }
  }

  /** Returns source id of the given `key` unmodified-center */
  private fun getSourceId(key: Key) =
    if (key.isSpacer) {
      UNDEFINED
    } else getKeyState(key, metaState).getFlick(Flick.Direction.CENTER).get().keyEntity.sourceId

  /** Returns `Key` from source Id. */
  private fun getKeyFromSouceId(sourceId: Int): Key? {
    val keyboard = keyboard ?: return null
    for (row in keyboard.rowList) {
      for (key in row.keyList) {
        if (sourceId == getSourceId(key)) {
          return key
        }
      }
    }
    return null
  }

  /** Creates a `AccessibilityEvent` from `Key` and `eventType`. */
  private fun createAccessibilityEvent(key: Key, eventType: Int): AccessibilityEvent {
    val event =
      createAccessibilityEvent(eventType).apply {
        packageName = context.packageName
        className = key.javaClass.name
        contentDescription = getContentDescription(key)
        isEnabled = true
      }
    (event as AccessibilityRecord).setSource(view, getSourceId(key))
    return event
  }

  override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
    val key = getKeyFromSouceId(virtualViewId)
    return key != null && performActionForKeyInternal(key, virtualViewId, action)
  }

  fun performActionForKey(key: Key, action: Int): Boolean {
    return performActionForKeyInternal(key, getSourceId(key), action)
  }

  /** Processes accessibility action for key on virtual view structure. */
  private fun performActionForKeyInternal(key: Key, virtualViewId: Int, action: Int): Boolean {
    return when (action) {
      AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS -> {
        if (virtualFocusedViewId == virtualViewId) {
          // If focused virtual view is unchanged, do nothing.
          return false
        }
        // Framework requires the keyboard to have focus.
        // Return FOCUSED event to the framework as response.
        virtualFocusedViewId = virtualViewId
        if (accessibilityManager.isEnabled) {
          accessibilityManager.sendAccessibilityEvent(
            createAccessibilityEvent(key, AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
          )
        }
        true
      }
      AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
        // Framework requires the keyboard to clear focus.
        // Return FOCUSE_CLEARED event to the framework as response.
        if (virtualFocusedViewId != virtualViewId) {
          return false
        }
        virtualFocusedViewId = UNDEFINED
        if (accessibilityManager.isEnabled) {
          accessibilityManager.sendAccessibilityEvent(
            createAccessibilityEvent(
              key,
              AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
            )
          )
        }
        true
      }
      else -> false
    }
  }

  fun sendAccessibilityEventForKeyIfAccessibilityEnabled(key: Key, eventType: Int) {
    if (accessibilityManager.isEnabled) {
      val event = createAccessibilityEvent(key, eventType)
      accessibilityManager.sendAccessibilityEvent(event)
    }
  }

  fun setKeyboard(keyboard: Keyboard?) {
    this.keyboard = keyboard
    resetVirtualStructure()
  }

  fun setMetaState(metaState: Set<MetaState>) {
    this.metaState = metaState
    resetVirtualStructure()
  }

  private fun resetVirtualStructure() {
    keys = null
    if (accessibilityManager.isEnabled) {
      val event = createAccessibilityEvent()
      view.onInitializeAccessibilityEvent(event)
      event.eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
      accessibilityManager.sendAccessibilityEvent(event)
    }
  }

  private fun getContentDescription(key: Key): String? {
    return getKeyState(key, metaState).contentDescription
  }
}

private const val UNDEFINED = Int.MIN_VALUE

private fun getKeyState(key: Key, metaState: Set<MetaState>): KeyState {
  require(!key.isSpacer)
  return key.getKeyState(metaState).get()
}
