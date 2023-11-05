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
import android.content.SharedPreferences
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
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.BaseAdapter
import android.widget.Gallery
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder
import com.google.common.base.Preconditions
import java.util.Arrays
import java.util.Collections
import java.util.EnumMap
import sh.eliza.japaneseinput.MozcLog
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification
import sh.eliza.japaneseinput.preference.ClientSidePreference.KeyboardLayout
import sh.eliza.japaneseinput.view.Skin
import sh.eliza.japaneseinput.view.SkinType

/** Preference class for KeyboardLayout. */
class KeyboardLayoutPreference
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : Preference(context, attrs) {
  class Item(
    val keyboardLayout: KeyboardLayout,
    val specification: KeyboardSpecification,
    val titleResId: Int,
    val descriptionResId: Int,
  )

  internal inner class ImageAdapter(resources: Resources) : BaseAdapter() {
    private val drawableMap =
      EnumMap<KeyboardLayout, KeyboardPreviewDrawable>(KeyboardLayout::class.java)

    init {
      for (item in itemList) {
        drawableMap[item.keyboardLayout] =
          KeyboardPreviewDrawable(resources, item.keyboardLayout, item.specification)
      }
    }

    override fun getCount(): Int {
      return itemList.size
    }

    override fun getItem(item: Int): Any {
      return itemList[item]
    }

    override fun getItemId(item: Int): Long {
      return item.toLong()
    }

    override fun getView(position: Int, convertView: View?, parentView: ViewGroup): View {
      val view =
        if (convertView == null) {
          LayoutInflater.from(context)
            .inflate(R.layout.pref_keyboard_layout_item, parentView, false)
        } else {
          convertView
        }
      val isEnabled = this@KeyboardLayoutPreference.isEnabled()

      // Needs to set the properties to the view even if a cached view is available,
      // because the cached view might use to be used for another item.
      val item = itemList[position]
      val imageView = view.findViewById<View>(R.id.pref_inputstyle_item_image) as ImageView
      imageView.setImageDrawable(drawableMap[item.keyboardLayout])
      imageView.isEnabled = isEnabled
      val titleView = view.findViewById<View>(R.id.pref_inputstyle_item_title) as TextView
      titleView.setText(item.titleResId)
      titleView.setTextColor(
        ContextCompat.getColor(
          context,
          if (isEnabled) R.color.pref_inputstyle_title else R.color.pref_inputstyle_title_disabled
        )
      )
      titleView.isEnabled = parentView.isEnabled
      updateBackground(view, position, getActiveIndex())
      return view
    }

    fun setSkin(skin: Skin) {
      Preconditions.checkNotNull<Skin>(skin)
      for (drawable in drawableMap.values) {
        drawable.setSkin(skin)
      }
    }
  }

  private inner class GalleryEventListener : OnItemSelectedListener, OnItemClickListener {
    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
      // Update the value if necessary.
      val newValue: KeyboardLayout = itemList[position].keyboardLayout
      if (callChangeListener(newValue)) {
        value = newValue
        updateAllItemBackground(parent, getActiveIndex())
      }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
      // Update the description.
      val descriptionView =
        (parent.parent as View).findViewById<TextView>(R.id.pref_inputstyle_description)
      if (descriptionView != null) {
        val item = parent.getItemAtPosition(position) as Item
        descriptionView.text =
          Html.fromHtml(
            descriptionView.context.resources.getString(item.descriptionResId),
            Build.VERSION.SDK_INT
          )
      }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
      // Do nothing.
    }
  }

  private val imageAdapter = ImageAdapter(context.resources)
  private val galleryEventListener = GalleryEventListener()
  private val sharedPreferenceChangeListener =
    object : OnSharedPreferenceChangeListener {
      override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == getContext().getResources().getString(R.string.pref_skin_type_key)) {
          updateSkin()
        }
      }
    }

  var value = KeyboardLayout.TWELVE_KEYS
    set(value) {
      if (field != value) {
        field = value
        persistString(value.name)
        notifyChanged()
      }
    }

  private fun getActiveIndex(): Int {
    for (i in itemList.indices) {
      if (itemList[i].keyboardLayout == value) {
        return i
      }
    }
    MozcLog.e("Current value is not found in the itemList: $value")
    return 0
  }

  override protected fun onGetDefaultValue(a: TypedArray, index: Int): Any {
    return toKeyboardLayoutInternal(a.getString(index))
  }

  override protected fun onSetInitialValue(defaultValue: Any?) {
    // TODO(exv): okay to ignore defaultValue here?
    value = toKeyboardLayoutInternal(getPersistedString(null))
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
    (holder.findViewById(R.id.pref_inputstyle_description) as TextView).run {
      movementMethod = LinkMovementMethod.getInstance()
    }
    (holder.findViewById(R.id.pref_inputstyle_gallery) as Gallery).run {
      setAdapter(imageAdapter)
      setOnItemSelectedListener(galleryEventListener)
      setOnItemClickListener(galleryEventListener)
      setSelection(getActiveIndex())
    }
    updateSkin()
  }

  override protected fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
    super.onAttachedToHierarchy(preferenceManager)
    updateSkin()
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

  companion object {
    @JvmField
    val itemList =
      Collections.unmodifiableList(
        Arrays.asList(
          Item(
            KeyboardLayout.TWELVE_KEYS,
            KeyboardSpecification.TWELVE_KEY_TOGGLE_FLICK_KANA,
            R.string.pref_keyboard_layout_title_12keys,
            R.string.pref_keyboard_layout_description_12keys
          ),
          Item(
            KeyboardLayout.QWERTY,
            KeyboardSpecification.QWERTY_KANA,
            R.string.pref_keyboard_layout_title_qwerty,
            R.string.pref_keyboard_layout_description_qwerty
          ),
          Item(
            KeyboardLayout.GODAN,
            KeyboardSpecification.GODAN_KANA,
            R.string.pref_keyboard_layout_title_godan,
            R.string.pref_keyboard_layout_description_godan
          )
        )
      )

    private fun updateAllItemBackground(gallery: AdapterView<*>, activeIndex: Int) {
      for (i in 0 until gallery.getChildCount()) {
        val child: View = gallery.getChildAt(i)
        val position: Int = gallery.getPositionForView(child)
        updateBackground(child, position, activeIndex)
      }
    }

    private fun updateBackground(view: View, position: Int, activePosition: Int) {
      if (position == activePosition) {
        view.setBackgroundResource(android.R.drawable.dialog_frame)
      } else {
        view.setBackground(null)
      }
    }
  }
}
