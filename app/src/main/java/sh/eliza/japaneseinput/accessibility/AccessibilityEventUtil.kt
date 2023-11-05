package sh.eliza.japaneseinput.accessibility

import android.os.Build
import android.view.accessibility.AccessibilityEvent

object AccessibilityEventUtil {
  fun createAccessibilityEvent() =
    if (Build.VERSION.SDK_INT >= 30) {
      AccessibilityEvent()
    } else {
      @Suppress("deprecation") AccessibilityEvent.obtain()
    }

  fun createAccessibilityEvent(eventType: Int) =
    if (Build.VERSION.SDK_INT >= 30) {
      AccessibilityEvent(eventType)
    } else {
      @Suppress("deprecation") AccessibilityEvent.obtain(eventType)
    }
}
