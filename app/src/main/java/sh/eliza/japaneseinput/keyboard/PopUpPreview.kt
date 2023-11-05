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
package sh.eliza.japaneseinput.keyboard

import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.View
import android.widget.ImageView.ScaleType
import androidx.appcompat.widget.AppCompatImageView
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import java.util.ArrayList
import kotlin.math.min
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.keyboard.BackgroundDrawableFactory.DrawableType
import sh.eliza.japaneseinput.ui.PopUpLayouter
import sh.eliza.japaneseinput.view.DrawableCache
import sh.eliza.japaneseinput.view.Skin

/**
 * This class represents a popup preview which is shown at key-pressing timing.
 *
 * Note that this should be package private class because it is just an implementation of the
 * preview used in [KeyboardView]. However, we annotate `public` to this class just for testing
 * purpose. The methods we want to mock also annotated as `protected`.
 *
 * Production clients shouldn't use this class from outside of this package.
 */
private class PopUpPreview(
  parent: View,
  private val backgroundDrawableFactory: BackgroundDrawableFactory,
  private val drawableCache: DrawableCache
) {
  /**
   * For performance reason, we want to reuse instances of [PopUpPreview]. We start to support
   * multi-touch, so the number of PopUpPreview we need can be two or more (though, we expect the
   * number of the previews is at most two in most cases).
   *
   * This class provides simple functionality of instance pooling of PopUpPreview.
   */
  class Pool(
    private val parent: View,
    looper: Looper,
    private val backgroundDrawableFactory: BackgroundDrawableFactory,
    resources: Resources
  ) {
    // Typically 2 or 3 popups are shown in maximum.
    private val pool = SparseArray<PopUpPreview>(3)
    private val freeList: MutableList<PopUpPreview> = ArrayList()
    private val drawableCache = DrawableCache(resources)
    private val dismissHandler =
      Handler(looper) { message ->
        val preview = Preconditions.checkNotNull(message).obj as PopUpPreview
        preview.dismiss()
        freeList.add(preview)
        true
      }

    fun getInstance(pointerId: Int): PopUpPreview {
      var preview = pool[pointerId]
      if (preview == null) {
        preview =
          if (freeList.isEmpty()) {
            PopUpPreview(parent, backgroundDrawableFactory, drawableCache)
          } else {
            freeList.removeAt(freeList.size - 1)
          }
        pool.put(pointerId, preview)
      }
      return preview
    }

    fun releaseDelayed(pointerId: Int, delay: Long) {
      val preview = pool[pointerId] ?: return
      pool.remove(pointerId)
      dismissHandler.sendMessageDelayed(dismissHandler.obtainMessage(0, preview), delay)
    }

    fun releaseAll() {
      // Remove all messages.
      dismissHandler.removeMessages(0)
      for (i in 0 until pool.size()) {
        pool.valueAt(i).dismiss()
      }
      pool.clear()
      for (preview in freeList) {
        preview.dismiss()
      }
      freeList.clear()
      drawableCache.clear()
    }

    fun setSkin(skin: Skin) {
      drawableCache.setSkin(skin)
    }
  }

  private val popUp = run {
    val popUpView =
      AppCompatImageView(parent.context).apply {
        visibility = View.GONE
        scaleType = ScaleType.CENTER_CROP
        adjustViewBounds = true
      }
    PopUpLayouter(parent, popUpView)
  }

  /** Shows the pop-up preview of the given `key` and `optionalPopup` if needed. */
  fun showIfNecessary(key: Key, optionalPopup: Optional<PopUp>, isDelayedPopup: Boolean) {
    if (!optionalPopup.isPresent) {
      hidePopupView()
      return
    }
    val popup = optionalPopup.get()
    val popUpIconDrawable =
      drawableCache.getDrawable(
        if (isDelayedPopup) popup.popUpLongPressIconResourceId else popup.popUpIconResourceId
      )
    if (!popUpIconDrawable.isPresent) {
      hidePopupView()
      return
    }
    val popupView = popUp.contentView
    val resources = popupView.context.resources
    val density = resources.displayMetrics.density
    val popUpWindowPadding = (BackgroundDrawableFactory.POPUP_WINDOW_PADDING * density).toInt()
    val width =
      (min(key.width, resources.getDimensionPixelSize(R.dimen.popup_width_limitation)) +
        popUpWindowPadding * 2)
    val height = popup.height + popUpWindowPadding * 2
    popupView.setImageDrawable(popUpIconDrawable.get())
    popupView.setBackgroundDrawable(
      backgroundDrawableFactory.getDrawable(DrawableType.POPUP_BACKGROUND_WINDOW)
    )
    Preconditions.checkState(popup.iconWidth != 0 || popup.iconHeight != 0)
    val horizontalPadding =
      if (popup.iconWidth == 0) popUpWindowPadding else (width - popup.iconWidth) / 2
    val verticalPadding =
      if (popup.iconHeight == 0) popUpWindowPadding else (height - popup.iconHeight) / 2
    popupView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

    // Calculate the location to show the pop-up in window's coordinate system.
    val centerX = key.x + key.width / 2
    val centerY = key.y + key.height / 2
    val left = centerX + popup.xOffset - width / 2
    val top = centerY + popup.yOffset - height / 2
    popUp.setBounds(left, top, left + width, top + height)
    popupView.visibility = View.VISIBLE
  }

  /** Hides the pop up preview. */
  fun dismiss() {
    hidePopupView()
  }

  private fun hidePopupView() {
    popUp.contentView.run {
      visibility = View.GONE
      setImageDrawable(null)
      setBackgroundDrawable(null)
    }
  }
}
