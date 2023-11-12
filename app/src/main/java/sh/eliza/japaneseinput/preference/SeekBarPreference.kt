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
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import kotlin.math.max
import kotlin.math.min
import sh.eliza.japaneseinput.R

/**
 * Preference to configure the flick sensitivity.
 *
 * This preference has a seekbar and its indicators to manipulate flick sensitivity, in addition to
 * other regular preferences.
 */
class SeekBarPreference
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : Preference(context, attrs) {
  private var seekBarValue = 0
  private var seekBar: SeekBar? = null
  private var seekBarValueTextView: TextView? = null
  private var min = 0
  private var max = 1
  private var unit: String? = null
  private var lowText: String? = null
  private var middleText: String? = null
  private var highText: String? = null

  /** Listener reacting to the [SeekBar] changing value by the user */
  private val seekBarChangeListener =
    object : OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        updateLabelValue(progress + min)
      }

      override fun onStartTrackingTouch(seekBar: SeekBar) {}

      override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (seekBar.progress + min != seekBarValue) {
          syncValue(seekBar)
        }
      }
    }

  /**
   * Listener reacting to the user pressing DPAD left/right keys; it transfers the key presses to
   * the [SeekBar] to be handled accordingly.
   */
  private val seekBarKeyListener =
    object : View.OnKeyListener {
      override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
          return false
        }

        // We don't want to propagate the click keys down to the SeekBar view since it will
        // create the ripple effect for the thumb.
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
          return false
        }

        val seekBar = seekBar
        if (seekBar === null) {
          return false
        }
        return seekBar.onKeyDown(keyCode, event)
      }
    }

  init {
    context.theme.obtainStyledAttributes(attrs, R.styleable.MozcSeekBarPreference, 0, 0).run {
      try {
        min = getInteger(R.styleable.MozcSeekBarPreference_min, 0)
        max = getInteger(R.styleable.MozcSeekBarPreference_max, 1)
        unit = getString(R.styleable.MozcSeekBarPreference_unit)
        lowText = getString(R.styleable.MozcSeekBarPreference_low_text)
        middleText = getString(R.styleable.MozcSeekBarPreference_middle_text)
        highText = getString(R.styleable.MozcSeekBarPreference_high_text)
      } finally {
        recycle()
      }
    }

    isIconSpaceReserved = true
    layoutResource = R.layout.pref_seekbar
  }

  override fun onSetInitialValue(defaultValue: Any?) {
    setValue(getPersistedInt(defaultValue as? Int ?: 0), notifyChanged = false)
  }

  override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
    return a.getInt(index, 0)
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)

    holder.isDividerAllowedAbove = false
    holder.isDividerAllowedBelow = true

    holder.itemView.setOnKeyListener(seekBarKeyListener)

    seekBarValueTextView = holder.findViewById(R.id.seekbar_value) as? TextView?

    seekBar =
      (holder.findViewById(R.id.seekbar) as? SeekBar?)?.apply {
        max = this@SeekBarPreference.max - this@SeekBarPreference.min
        progress = seekBarValue - this@SeekBarPreference.min
        setOnSeekBarChangeListener(seekBarChangeListener)
      }

    updateLabelValue(seekBarValue)

    fun setTextView(text: String?, resourceId: Int) {
      if (text == null) {
        return
      }
      val textView = holder.findViewById(resourceId) as? TextView ?: return
      textView.text = text
    }

    setTextView(lowText, R.id.pref_seekbar_low_text)
    setTextView(middleText, R.id.pref_seekbar_middle_text)
    setTextView(highText, R.id.pref_seekbar_high_text)
  }

  private fun setValue(value: Int, notifyChanged: Boolean) {
    val newValue = min(this.max, max(this.min, value))
    if (newValue != seekBarValue) {
      seekBarValue = newValue
      updateLabelValue(newValue)
      persistInt(newValue)
      if (notifyChanged) {
        notifyChanged()
      }
    }
  }

  /**
   * Persist the [SeekBar]'s SeekBar value if callChangeListener returns true, otherwise set the
   * [SeekBar]'s value to the stored value.
   */
  private fun syncValue(seekBar: SeekBar) {
    val newValue = min + seekBar.progress
    if (newValue != seekBarValue) {
      if (callChangeListener(newValue)) {
        setValue(newValue, false)
      } else {
        seekBar.progress = seekBarValue - min
        updateLabelValue(seekBarValue)
      }
    }
  }

  private fun updateLabelValue(value: Int) {
    seekBarValueTextView?.let {
      it.text =
        if (unit !== null) {
          "$value $unit"
        } else {
          value.toString()
        }
    }
  }
}
