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

import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import android.content.res.TypedArray
import android.os.Build
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.Px
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.util.EnumMap
import sh.eliza.japaneseinput.MozcLog
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification
import sh.eliza.japaneseinput.preference.ClientSidePreference.KeyboardLayout
import sh.eliza.japaneseinput.preference.PreferenceUtil.PREF_CURRENT_KEYBOARD_LAYOUT_KEY
import sh.eliza.japaneseinput.preference.PreferenceUtil.PREF_LANDSCAPE_KEYBOARD_LAYOUT_KEY
import sh.eliza.japaneseinput.preference.PreferenceUtil.PREF_PORTRAIT_KEYBOARD_LAYOUT_KEY
import sh.eliza.japaneseinput.preference.PreferenceUtil.isLandscapeKeyboardSettingActive
import sh.eliza.japaneseinput.view.Skin
import sh.eliza.japaneseinput.view.SkinType

/** Preference class for KeyboardLayout. */
class KeyboardLayoutPreference
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : Preference(context, attrs) {
  data class Item(
    val keyboardLayout: KeyboardLayout,
    val specification: KeyboardSpecification,
    val titleResId: Int,
    val descriptionResId: Int,
  )

  internal class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val root = view.findViewById<View>(R.id.pref_inputstyle_item_root)
    val image = view.findViewById<ImageView>(R.id.pref_inputstyle_item_image)
    val title = view.findViewById<TextView>(R.id.pref_inputstyle_item_title)
  }

  internal inner class ImageAdapter(resources: Resources) : RecyclerView.Adapter<ViewHolder>() {
    private val drawableMap =
      EnumMap<KeyboardLayout, KeyboardPreviewDrawable>(KeyboardLayout::class.java)

    init {
      for (item in ITEM_LIST) {
        drawableMap[item.keyboardLayout] =
          KeyboardPreviewDrawable(resources, item.keyboardLayout, item.specification)
      }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int) =
      ViewHolder(
        LayoutInflater.from(viewGroup.context)
          .inflate(R.layout.pref_keyboard_layout_item, viewGroup, /*attachToRoot=*/ false)
      )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val isEnabled = this@KeyboardLayoutPreference.isEnabled()

      // Needs to set the properties to the view even if a cached view is available,
      // because the cached view might use to be used for another item.
      val item = ITEM_LIST[position]
      holder.image.setImageDrawable(drawableMap[item.keyboardLayout])
      holder.image.isEnabled = isEnabled
      holder.title.setText(item.titleResId)
      holder.title.setTextColor(
        ContextCompat.getColor(
          context,
          if (isEnabled) R.color.pref_inputstyle_title else R.color.pref_inputstyle_title_disabled
        )
      )
      holder.title.isEnabled = isEnabled
      updateBackground(holder.root, position, getActiveIndex())
      holder.root.setOnClickListener {
        // Update the value if necessary.
        val newValue = ITEM_LIST[position].keyboardLayout
        if (callChangeListener(newValue)) {
          setValue(newValue, notifyChanged = true)
          @Suppress("NotifyDataSetChanged") notifyDataSetChanged()
        }
      }
    }

    override fun getItemCount() = ITEM_LIST.size

    fun setSkin(skin: Skin) {
      for (drawable in drawableMap.values) {
        drawable.setSkin(skin)
      }
    }
  }

  private inner class GalleryEventListener(private val descriptionView: TextView) :
    ViewPager2.OnPageChangeCallback() {

    override fun onPageSelected(position: Int) {
      // Update the description.
      val item = ITEM_LIST[position]
      descriptionView.text =
        Html.fromHtml(
          descriptionView.context.resources.getString(item.descriptionResId),
          Build.VERSION.SDK_INT
        )
    }

    override fun onPageScrollStateChanged(state: Int) {}

    override fun onPageScrolled(
      position: Int,
      positionOffset: Float,
      @Px positionOffsetPixels: Int,
    ) {}
  }

  private val imageAdapter = ImageAdapter(context.resources)
  private val sharedPreferenceChangeListener = OnSharedPreferenceChangeListener { _, key ->
    when (key) {
      context.resources.getString(R.string.pref_skin_type_key) -> updateSkin()
      this.key -> {
        // HACK: Reset the backing value and view pager if someone else changes it.
        onSetInitialValue(null)
        @Suppress("NotifyDataSetChanged") imageAdapter.notifyDataSetChanged()
        viewPager?.setCurrentItem(getActiveIndex(), /*smoothScroll=*/ false)
      }
    }
  }
  private var viewPager: ViewPager2? = null

  private var value = KeyboardLayout.TWELVE_KEYS

  private fun setValue(value: KeyboardLayout, notifyChanged: Boolean) {
    if (this.value != value) {
      this.value = value
      persistString(value.name)
      if (notifyChanged) {
        notifyChanged()
      }
    }
  }

  private fun getActiveIndex(): Int {
    for (i in ITEM_LIST.indices) {
      if (ITEM_LIST[i].keyboardLayout == value) {
        return i
      }
    }
    MozcLog.e("Current value is not found in the ITEM_LIST: $value")
    return 0
  }

  override protected fun onGetDefaultValue(a: TypedArray, index: Int): Any {
    return toKeyboardLayoutInternal(a.getString(index))
  }

  override protected fun onSetInitialValue(defaultValue: Any?) {
    setValue(toKeyboardLayoutInternal(getPersistedString(null)), notifyChanged = false)
  }

  /**
   * Parses the name and returns the [KeyboardLayout] instance. If invalid name or `null` is given,
   * the default value `TWELVE_KEYS` will be returned.
   */
  private fun toKeyboardLayoutInternal(keyboardLayoutName: String?): KeyboardLayout {
    if (keyboardLayoutName != null) {
      try {
        return ClientSidePreference.KeyboardLayout.valueOf(keyboardLayoutName)
      } catch (e: IllegalArgumentException) {
        MozcLog.e("Invalid keyboard layout name: $keyboardLayoutName", e)
      }
    }

    // Fallback. Use TWELVE_KEYS by default.
    return KeyboardLayout.TWELVE_KEYS
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)
    val descriptionView =
      (holder.findViewById(R.id.pref_inputstyle_description) as TextView).apply {
        movementMethod = LinkMovementMethod.getInstance()
      }
    viewPager =
      (holder.findViewById(R.id.pref_inputstyle_view_pager) as ViewPager2).apply {
        setAdapter(imageAdapter)
        registerOnPageChangeCallback(GalleryEventListener(descriptionView))
        setCurrentItem(getActiveIndex(), /*smoothScroll=*/ false)
      }
    updateSkin()
  }

  override protected fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
    super.onAttachedToHierarchy(preferenceManager)
    updateSkin()

    // HACK: Select between portrait and landscape as necessary by changing our key.
    if (key == PREF_CURRENT_KEYBOARD_LAYOUT_KEY) {
      key =
        if (isLandscapeKeyboardSettingActive(
            sharedPreferences!!,
            context.resources.configuration.orientation
          )
        ) {
          PREF_LANDSCAPE_KEYBOARD_LAYOUT_KEY
        } else {
          PREF_PORTRAIT_KEYBOARD_LAYOUT_KEY
        }
      onSetInitialValue(null)
    }

    sharedPreferences!!.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
  }

  override protected fun onPrepareForRemoval() {
    sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    super.onPrepareForRemoval()
  }

  fun updateSkin() {
    val resources = context.resources
    val skinType =
      PreferenceUtil.getEnum(
        sharedPreferences,
        resources.getString(R.string.pref_skin_type_key),
        SkinType::class.java,
        SkinType.valueOf(resources.getString(R.string.pref_skin_type_default))
      )
    imageAdapter.setSkin(skinType.getSkin(resources))
  }
}

private val ITEM_LIST =
  listOf(
    KeyboardLayoutPreference.Item(
      KeyboardLayout.TWELVE_KEYS,
      KeyboardSpecification.TWELVE_KEY_TOGGLE_FLICK_KANA,
      R.string.pref_keyboard_layout_title_12keys,
      R.string.pref_keyboard_layout_description_12keys
    ),
    KeyboardLayoutPreference.Item(
      KeyboardLayout.QWERTY,
      KeyboardSpecification.QWERTY_KANA,
      R.string.pref_keyboard_layout_title_qwerty,
      R.string.pref_keyboard_layout_description_qwerty
    ),
    KeyboardLayoutPreference.Item(
      KeyboardLayout.GODAN,
      KeyboardSpecification.GODAN_KANA,
      R.string.pref_keyboard_layout_title_godan,
      R.string.pref_keyboard_layout_description_godan
    )
  )

private fun updateBackground(view: View, position: Int, activePosition: Int) {
  if (position == activePosition) {
    view.setBackgroundResource(android.R.drawable.dialog_frame)
  } else {
    view.setBackground(null)
  }
}
