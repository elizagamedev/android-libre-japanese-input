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
package sh.eliza.japaneseinput.preference

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import com.google.common.base.Optional
import java.io.IOException
import java.util.EnumMap
import java.util.WeakHashMap
import org.xmlpull.v1.XmlPullParserException
import sh.eliza.japaneseinput.MozcLog
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.keyboard.BackgroundDrawableFactory
import sh.eliza.japaneseinput.keyboard.Keyboard
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification
import sh.eliza.japaneseinput.keyboard.KeyboardParser
import sh.eliza.japaneseinput.keyboard.KeyboardViewBackgroundSurface
import sh.eliza.japaneseinput.preference.ClientSidePreference.KeyboardLayout
import sh.eliza.japaneseinput.view.DrawableCache
import sh.eliza.japaneseinput.view.Skin

/** A Drawable to render keyboard preview. */
class KeyboardPreviewDrawable
internal constructor(
  private val resources: Resources,
  private val keyboardLayout: KeyboardLayout,
  private val specification: KeyboardSpecification
) : Drawable() {
  /**
   * The key of the activity which uses the Bitmap cache.
   *
   * In order to utilize memories, it is necessary to tell to the BitmapCache what activities use it
   * now. The current heuristic is register the activity in its onStart and unregister it in its
   * onStop. In theory, it should work well as long as the onStop corresponding to the
   * already-invoked onStart is invoked without errors.
   *
   * As the last resort, we use a finalizer guardian. The instance of the key should be referred
   * only by an Activity instance in its field. Then: - the activity can register and unregister in
   * its onStart/onStop methods. - Even if onStop is NOT invoked accidentally, the Activity will be
   * collected by GC, and the key is also collected at the same time. Then the finalize of the key
   * will be invoked, and it will unregister itself from Bitmap cache. - Overriding the finalizer
   * may cause the delay of memory collecting. However, the CacheReferenceKey is small enough (at
   * least compared to Activity as the Activity refers many other instances, too). So the risk (or
   * damage) of the remaining phantom instances should be low enough. Note: Regardless of the
   * finalizer, if onStop is correctly invoked, the bitmap cache will be released correctly.
   */
  internal class CacheReferenceKey {
    protected fun finalize() {
      BitmapCache.instance.removeReference(this)
    }
  }

  /**
   * Global cache of bitmap for the keyboard preview.
   *
   * Assuming that the size of each bitmap preview is same, and skin type is globally unique, we can
   * use global bitmap cache to keep the memory usage low. This cache also manages the referencing
   * Activities. See [sh.eliza.japaneseinput.preference.MozcMainPreferenceActivity] for the details.
   */
  internal class BitmapCache private constructor() {
    private val map: MutableMap<KeyboardLayout, Bitmap> = EnumMap(KeyboardLayout::class.java)
    private var skin = Skin.getFallbackInstance()
    private val referenceMap = WeakHashMap<CacheReferenceKey, Any>()
    operator fun get(
      keyboardLayout: KeyboardLayout?,
      width: Int,
      height: Int,
      skin: Skin
    ): Bitmap? {
      if (keyboardLayout == null || width <= 0 || height <= 0) {
        return null
      }
      if (skin != this.skin) {
        return null
      }
      var result = map[keyboardLayout]
      if (result != null) {
        // Check the size.
        if (result.width != width || result.height != height) {
          result = null
        }
      }
      return result
    }

    fun put(keyboardLayout: KeyboardLayout?, skin: Skin, bitmap: Bitmap?) {
      if (keyboardLayout == null || bitmap == null) {
        return
      }
      if (skin != this.skin) {
        clear()
        this.skin = skin
      }
      val oldBitmap = map.put(keyboardLayout, bitmap)
      oldBitmap?.recycle()
    }

    fun addReference(key: CacheReferenceKey) {
      referenceMap[key] = DUMMY_VALUE
    }

    fun removeReference(key: CacheReferenceKey) {
      referenceMap.remove(key)
      if (referenceMap.isEmpty()) {
        // When all referring activities are gone, we don't need keep the cache.
        // To reduce the memory usage, release all the cached bitmap.
        clear()
      }
    }

    private fun clear() {
      for (bitmap in map.values) {
        bitmap.recycle()
      }
      map.clear()
      skin = Skin.getFallbackInstance()
    }

    companion object {
      val instance = BitmapCache()
      private val DUMMY_VALUE = Any()
    }
  }

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var skin = Skin.getFallbackInstance()
  private var enabled = true

  override fun draw(canvas: Canvas) {
    val bounds = bounds
    if (bounds.isEmpty) {
      return
    }

    // Look up cache.
    val cache = BitmapCache.instance
    var bitmap = cache[keyboardLayout, bounds.width(), bounds.height(), skin]
    if (bitmap == null) {
      bitmap =
        createBitmap(
          resources,
          specification,
          bounds.width(),
          bounds.height(),
          resources.getDimensionPixelSize(R.dimen.pref_inputstyle_reference_width),
          skin
        )
      if (bitmap != null) {
        cache.put(keyboardLayout, skin, bitmap)
      }
    }
    if (bitmap != null) {
      canvas.drawBitmap(bitmap, bounds.left.toFloat(), bounds.top.toFloat(), paint)
    }
    if (!enabled) {
      // To represent disabling, gray it out.
      val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -0x80000000 }
      canvas.drawRect(bounds, paint)
    }
  }

  fun setSkin(skin: Skin) {
    this.skin = skin
    invalidateSelf()
  }

  override fun getOpacity(): Int {
    return PixelFormat.TRANSLUCENT
  }

  override fun setAlpha(alpha: Int) {
    // Do nothing.
  }

  override fun setColorFilter(cf: ColorFilter?) {
    // Do nothing.
  }

  // Hard coded size to adapt the old implementation.
  override fun getIntrinsicWidth(): Int {
    return resources.getDimensionPixelSize(R.dimen.pref_inputstyle_image_width)
  }

  override fun getIntrinsicHeight(): Int {
    return resources.getDimensionPixelSize(R.dimen.pref_inputstyle_image_width) * 348 / 480
  }

  override fun isStateful(): Boolean {
    return true
  }

  override fun onStateChange(state: IntArray): Boolean {
    val enabled = isEnabled(state)
    if (this.enabled == enabled) {
      return false
    }
    this.enabled = enabled
    invalidateSelf()
    return true
  }
}

/**
 * @param width width of returned `Bitmap`
 * @param height height of returned `Bitmap`
 * @param virtualWidth virtual width of keyboard. This value is used when rendering. virtualHeight
 * is internally calculated based on given arguments keeping aspect ratio.
 */
private fun createBitmap(
  resources: Resources,
  specification: KeyboardSpecification,
  width: Int,
  height: Int,
  virtualWidth: Int,
  skin: Skin
): Bitmap? {
  // Scaling is required because some icons are draw with specified fixed size.
  val scale = width / virtualWidth.toFloat()
  val virtualHeight = (height / scale).toInt()
  val keyboard =
    getParsedKeyboard(resources, specification, virtualWidth, virtualHeight) ?: return null
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap).apply { scale(scale, scale) }
  val drawableCache = DrawableCache(resources).apply { setSkin(skin) }

  // Fill background.
  skin.windowBackgroundDrawable.constantState?.newDrawable()?.run {
    setBounds(0, 0, virtualWidth, virtualHeight)
    draw(canvas)
  }

  // Draw keyboard layout.
  val backgroundDrawableFactory = BackgroundDrawableFactory(resources).apply { setSkin(skin) }
  KeyboardViewBackgroundSurface(backgroundDrawableFactory, drawableCache).run {
    reset(Optional.of(keyboard), emptySet())
    draw(canvas)
  }
  return bitmap
}

/** Create a Keyboard instance which fits the current bitmap. */
private fun getParsedKeyboard(
  resources: Resources,
  specification: KeyboardSpecification,
  width: Int,
  height: Int
): Keyboard? {
  val parser = KeyboardParser(resources, width, height, specification)
  try {
    return parser.parseKeyboard()
  } catch (e: XmlPullParserException) {
    MozcLog.e("Failed to parse keyboard layout: ", e)
  } catch (e: IOException) {
    MozcLog.e("Failed to parse keyboard layout: ", e)
  }
  return null
}

private fun isEnabled(state: IntArray): Boolean {
  for (i in state.indices) {
    if (state[i] == android.R.attr.state_enabled) {
      return true
    }
  }
  return false
}
