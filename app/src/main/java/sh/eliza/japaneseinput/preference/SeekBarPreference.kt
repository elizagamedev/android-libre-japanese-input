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
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import sh.eliza.japaneseinput.R

/**
 * Preference to configure the flick sensitivity.
 *
 * This preference has a seekbar and its indicators to manipulate flick sensitivity, in addition to
 * other regular preferences.
 */
class SeekBarPreference : Preference {
  private inner class SeekBarChangeListener
  internal constructor(private val sensitivityTextView: TextView?) : OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
      if (sensitivityTextView != null) {
        sensitivityTextView.text = (progress + offset).toString()
      }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {
      setValue(seekBar.getProgress() + offset)
    }
  }

  init {
    setLayoutResource(R.layout.pref_seekbar)
  }

  private var max = 0
  private var offset = 0
  private var value = 0
  private var unit: String? = null
  private var lowText: String? = null
  private var middleText: String? = null
  private var highText: String? = null

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    val attributeArray =
      intArrayOf(
        android.R.attr.max,
        android.R.attr.progress,
        R.attr.seekbar_offset,
        R.attr.seekbar_unit,
        R.attr.seekbar_low_text,
        R.attr.seekbar_middle_text,
        R.attr.seekbar_high_text
      )
    val typedArray = context.obtainStyledAttributes(attrs, attributeArray)
    try {
      offset = typedArray.getInt(2, 0)
      max = typedArray.getInt(0, 0)
      value = typedArray.getInt(1, 0) + offset
      unit = typedArray.getString(3)
      lowText = typedArray.getString(4)
      middleText = typedArray.getString(5)
      highText = typedArray.getString(6)
    } finally {
      typedArray.recycle()
    }
  }

  // protected fun onCreateView(parent: ViewGroup?): View {
  //   val inflater: LayoutInflater = LayoutInflater.from(getContext())
  //   val view: View = inflater.inflate(R.layout.pref_seekbar, parent, false)
  //   val preferenceFrame = view.findViewById<View>(R.id.preference_frame) as ViewGroup
  //   // Note: Invoking getLayoutResource() is a hack to obtain the default resource id.
  //   inflater.inflate(getLayoutResource(), preferenceFrame)
  //   initializeOriginalView(view)
  //   return view
  // }

  // /** Initializes the original preferecen's parameters. */
  // private fun initializeOriginalView(view: View) {
  //   val summaryView = view.findViewById<View>(android.R.id.summary) ?: return
  //   shrinkBottomMarginAndPadding(summaryView)
  //   val parentView = summaryView.parent as? View ?: return
  //   shrinkBottomMarginAndPadding(parentView)
  //   val rootView = parentView.parent as? View ?: return
  //   rootView.minimumHeight = 0
  //   val widgetFrame = view.findViewById<View>(android.R.id.widget_frame)
  //   if (widgetFrame != null) {
  //     widgetFrame.visibility = View.GONE
  //   }
  // }

  private fun shrinkBottomMarginAndPadding(view: View) {
    val params = view.layoutParams
    if (params is MarginLayoutParams) {
      (params as MarginLayoutParams).bottomMargin = 0
      view.layoutParams = params
    }
    view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)
  }

  override protected fun onGetDefaultValue(a: TypedArray, index: Int): Any {
    return a.getInt(index, 0)
  }

  override protected fun onSetInitialValue(defaultValue: Any?) {
    // TODO(exv): is this logic okay?
    setValue(getPersistedInt(defaultValue as? Int ?: value))
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)

    val valueView =
      (holder.findViewById(R.id.pref_seekbar_value) as TextView?)?.apply { text = value.toString() }

    (holder.findViewById(R.id.pref_seekbar_seekbar) as SeekBar?)?.run {
      setMax(max - offset)
      setProgress(value - offset)
      setOnSeekBarChangeListener(SeekBarChangeListener(valueView))
    }

    (holder.findViewById(R.id.pref_seekbar_unit) as TextView?)?.run {
      if (unit == null) {
        visibility = View.GONE
      } else {
        visibility = View.VISIBLE
        text = unit
      }
    }

    fun setTextView(text: String?, resourceId: Int) {
      if (text == null) {
        return
      }
      val textView = holder.findViewById(resourceId) as TextView ?: return
      textView.text = text
    }

    setTextView(lowText, R.id.pref_seekbar_low_text)
    setTextView(middleText, R.id.pref_seekbar_middle_text)
    setTextView(highText, R.id.pref_seekbar_high_text)
  }

  fun setValue(value: Int) {
    this.value = value
    persistInt(value)
    notifyDependencyChange(shouldDisableDependents())
    notifyChanged()
  }
}
