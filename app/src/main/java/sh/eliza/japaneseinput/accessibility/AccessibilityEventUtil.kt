@file:Suppress("unused")

package sh.eliza.japaneseinput.accessibility

import android.os.Build
import android.view.accessibility.AccessibilityEvent

@Suppress("unused")
object AccessibilityEventUtil {
  fun createAccessibilityEvent(): AccessibilityEvent =
    if (Build.VERSION.SDK_INT >= 30) {
      AccessibilityEvent()
    } else {
      @Suppress("deprecation") AccessibilityEvent.obtain()
    }

  fun createAccessibilityEvent(eventType: Int): AccessibilityEvent =
    if (Build.VERSION.SDK_INT >= 30) {
      AccessibilityEvent(eventType)
    } else {
      @Suppress("deprecation") AccessibilityEvent.obtain(eventType)
    }
}
