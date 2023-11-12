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
package sh.eliza.japaneseinput.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.Layout.Alignment
import android.text.StaticLayout
import android.text.TextPaint
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateList
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateWord
import sh.eliza.japaneseinput.ui.CandidateLayout.Row
import sh.eliza.japaneseinput.ui.CandidateLayout.Span
import sh.eliza.japaneseinput.view.Skin

private val STATE_EMPTY = intArrayOf()

// This is actually not the constant, but we should treat this as constant.
// Should not edit its contents.
private val STATE_FOCUSED = intArrayOf(android.R.attr.state_focused)

/** Locale field for [Paint.setTextLocale]. */
private val TEXT_LOCALE = Locale.JAPAN

private fun createTextPaint(color: Int, textAlign: Align) =
  TextPaint().apply {
    this.isAntiAlias = true
    this.color = color
    this.textAlign = textAlign
    textLocale = TEXT_LOCALE
  }

private fun isFocused(
  candidateWord: CandidateWord,
  focusedIndex: Int,
  pressedCandidateIndex: Int
): Boolean {
  val index = candidateWord.index
  return index == focusedIndex || index == pressedCandidateIndex
}

/**
 * Renders the [CandidateLayout] instance to the [Canvas]. After set all the parameter, clients can
 * render the CandidateLayout as follows.
 *
 * ```
 * CandidateList candidateList = ...;
 * CandidateLayoutRenderer renderer = ...;
 * CandidateLayout candidateLayout = layouter.layout(candidateList);
 * // it is necessary to set the original CandidateList before the actual rendering.
 * renderer.setCandidateList(candidateList);
 * renderer.drawCandidateLayout(canvas, candidateLayout, pressedCandidateIndex);
 * // If the original candidateList is same, it's ok to invoke the rendering
 * // twice or more, without re-invoking the setCandidateList.
 * renderer.drawCandidateLayout(canvas, candidateLayout, pressedCandidateIndex);
 * ```
 */
class CandidateLayoutRenderer {
  /**
   * The layout value width may be shorter than the rendered value without any settings. This policy
   * sets how to compress (scale) the value.
   */
  enum class ValueScalingPolicy {
    /** Scales uniformly (in other words, the aspect ratio will be kept). */
    UNIFORM,

    /** Scales only horizontally, so the height of the text will be kept. */
    HORIZONTAL
  }

  /** Specifies if the description can keep its own region or not. */
  enum class DescriptionLayoutPolicy {
    /** The description's region will be shared with the value's. */
    OVERLAY,

    /**
     * The description can keep its region exclusively (i.e., the value and description won't be
     * overlapped).
     */
    EXCLUSIVE,

    /** Like View.GONE, the descriptor is not shown and doesn't occupy any are. */
    GONE
  }

  private val valuePaint = createTextPaint(Color.BLACK, Align.LEFT)
  private val focusedValuePaint = createTextPaint(Color.BLACK, Align.LEFT)
  private val descriptionPaint = createTextPaint(Color.GRAY, Align.RIGHT)
  private val separatorPaint = Paint()

  /**
   * The cache of Rect instance for the clip used in drawCandidateList method to reduce the number
   * of resource allocation.
   */
  private val clipBounds = Rect()
  private var valueTextSize = 0f
  private var valueHorizontalPadding = 0f
  private var descriptionTextSize = 0f
  private var descriptionHorizontalPadding = 0f
  private var descriptionVerticalPadding = 0f
  private var valueScalingPolicy = ValueScalingPolicy.UNIFORM
  private var descriptionLayoutPolicy = DescriptionLayoutPolicy.OVERLAY
  private var spanBackgroundDrawable = Optional.absent<Drawable>()

  private var focusedIndex = -1

  fun setSkin(skin: Skin) {
    valuePaint.color = skin.candidateValueTextColor
    focusedValuePaint.color = skin.candidateValueFocusedTextColor
    descriptionPaint.color = skin.candidateDescriptionTextColor
  }

  fun setValueTextSize(valueTextSize: Float) {
    this.valueTextSize = valueTextSize
    valuePaint.textSize = valueTextSize
  }

  fun setValueHorizontalPadding(valueHorizontalPadding: Float) {
    this.valueHorizontalPadding = valueHorizontalPadding
  }

  fun setDescriptionTextSize(descriptionTextSize: Float) {
    this.descriptionTextSize = descriptionTextSize
    descriptionPaint.textSize = descriptionTextSize
  }

  fun setDescriptionHorizontalPadding(descriptionHorizontalPadding: Float) {
    this.descriptionHorizontalPadding = descriptionHorizontalPadding
  }

  fun setDescriptionVerticalPadding(descriptionVerticalPadding: Float) {
    this.descriptionVerticalPadding = descriptionVerticalPadding
  }

  fun setValueScalingPolicy(valueScalingPolicy: ValueScalingPolicy) {
    this.valueScalingPolicy = valueScalingPolicy
  }

  fun setDescriptionLayoutPolicy(descriptionLayoutPolicy: DescriptionLayoutPolicy) {
    this.descriptionLayoutPolicy = descriptionLayoutPolicy
  }

  fun setSpanBackgroundDrawable(drawable: Optional<Drawable>) {
    spanBackgroundDrawable = drawable
  }

  fun setCandidateList(candidateList: Optional<CandidateList>) {
    focusedIndex =
      if (candidateList.isPresent && candidateList.get().hasFocusedIndex())
        candidateList.get().focusedIndex
      else -1
  }

  fun setSeparatorWidth(separatorWidth: Float) {
    separatorPaint.strokeWidth = separatorWidth
  }

  fun setSeparatorColor(color: Int) {
    separatorPaint.color = color
  }

  /** Renders the `candidateLayout` to the given `canvas`. */
  fun drawCandidateLayout(
    canvas: Canvas,
    candidateLayout: CandidateLayout,
    pressedCandidateIndex: Int,
  ) {
    val clipBounds = clipBounds
    canvas.getClipBounds(clipBounds)
    val drawSeparators = Color.alpha(separatorPaint.color) != 0
    val focusedIndex = focusedIndex
    for (row in candidateLayout.rowList) {
      val top = row.top
      if (top >= clipBounds.bottom) {
        break
      }
      if (top + row.height < clipBounds.top) {
        continue
      }
      val separatorMargin = row.height * 0.2f
      val separatorTop = row.top + separatorMargin
      val separatorBottom = row.top + row.height - separatorMargin
      for (span in row.spanList) {
        if (span.left > clipBounds.right) {
          break
        }
        if (span.right < clipBounds.left) {
          continue
        }
        // Even if span.getCandidateWord() is absent, draw the span in order to draw the background.
        drawSpan(
          canvas,
          row,
          span,
          span.candidateWord.isPresent &&
            isFocused(span.candidateWord.get(), focusedIndex, pressedCandidateIndex),
        )
        if (drawSeparators && span.left != 0f) {
          val separatorX = span.left
          canvas.drawLine(separatorX, separatorTop, separatorX, separatorBottom, separatorPaint)
        }
      }
    }
  }

  private fun drawSpan(
    canvas: Canvas,
    row: Row,
    span: Span,
    isFocused: Boolean,
  ) {
    drawSpanBackground(canvas, row, span, isFocused)
    if (!span.candidateWord.isPresent) {
      return
    }
    drawText(canvas, row, span, isFocused)
    drawDescription(canvas, row, span)
  }

  private fun drawSpanBackground(canvas: Canvas, row: Row, span: Span, isFocused: Boolean) {
    if (!spanBackgroundDrawable.isPresent) {
      // No background available.
      return
    }
    val spanBackgroundDrawable = spanBackgroundDrawable.get()
    spanBackgroundDrawable.setBounds(
      span.left.toInt(),
      row.top.toInt(),
      span.right.toInt(),
      (row.top + row.height).toInt()
    )
    spanBackgroundDrawable.state = if (isFocused) STATE_FOCUSED else STATE_EMPTY
    spanBackgroundDrawable.draw(canvas)
  }

  private fun drawText(canvas: Canvas, row: Row, span: Span, isFocused: Boolean) {
    Preconditions.checkState(span.candidateWord.isPresent)
    val valueText = span.candidateWord.get().value
    if (valueText == null || valueText.isEmpty()) {
      // No value is available.
      return
    }
    // Calculate layout or get cached one.
    // If isFocused is true, special paint should be applied.
    // The resulting drawing is so special that it will not re reused.
    // Therefore if isFocused is true cache is not used and always calculate the layout.
    // In this case calculated layout is not cached.
    val layout =
      if (!isFocused && span.cachedLayout.isPresent) {
        span.cachedLayout.get()
      } else {
        // Set the scaling of the text.
        val descriptionWidth =
          if (descriptionLayoutPolicy == DescriptionLayoutPolicy.EXCLUSIVE) span.descriptionWidth
          else 0f
        // Ensure that StaticLayout instance has positive width.
        val displayValueWidth = max(1f, span.width - valueHorizontalPadding * 2 - descriptionWidth)
        val textScale = min(1f, displayValueWidth / span.valueWidth)
        val textPaint = if (isFocused) focusedValuePaint else valuePaint
        if (valueScalingPolicy == ValueScalingPolicy.HORIZONTAL) {
          textPaint.textSize = valueTextSize
          textPaint.textScaleX = textScale
        } else {
          // Calculate the max limit of the "text size", in which we can render the candidate text
          // inside the given span.
          // Rendered text should be inside the givenWidth.
          // Adjustment by font size can keep aspect ratio,
          // which is important for Emoticon especially.
          // Calculate the width with the default text size.
          textPaint.textSize = valueTextSize * textScale
        }
        // Layout's width is theoretically `span.getWidth() - descriptionWidth`.
        // However because of the spec of Paint#setTextScaleX() and Paint#setTextSize(),
        // Paint#measureText() might return larger width than what both above methods expect it to
        // be.
        // As a workaround, if theoretical width is smaller than the result of
        // Paint#measureText(),
        // employ the width returned by Paint#measureText().
        // This workaround is to avoid from unexpected line-break.
        // NOTE: Canvas#scale() cannot be used here because we have to use StaticLayout to draw
        //       Emoji and StaticLayout requires width in its constructor.
        val layout =
          StaticLayout.Builder.obtain(
              valueText,
              /*start=*/ 0,
              /*end=*/ valueText.length,
              textPaint,
              ceil(max(span.width - descriptionWidth, textPaint.measureText(valueText)).toDouble())
                .toInt(),
            )
            .run {
              setAlignment(Alignment.ALIGN_CENTER)
              setLineSpacing(/* spacingAdd = */ 0f, /* spacingMult = */ 1f)
              setIncludePad(false)
              build()
            }
        if (!isFocused) {
          span.setCachedLayout(layout)
        }
        layout
      }

    // Actually render the image to the canvas.
    val saveCount = canvas.save()
    try {
      canvas.translate(span.left, row.top + (row.height - layout.height) / 2)
      layout.draw(canvas)
    } finally {
      canvas.restoreToCount(saveCount)
    }
  }

  private fun drawDescription(canvas: Canvas, row: Row, span: Span) {
    val descriptionList = span.splitDescriptionList
    if (span.descriptionWidth <= 0 ||
        descriptionList.isEmpty() ||
        descriptionLayoutPolicy == DescriptionLayoutPolicy.GONE
    ) {
      // No description available or the layout policy is GONE.
      return
    }

    // Set the x-orientation scale based on the description's width to fit the span's region.
    val descriptionPaint = descriptionPaint
    descriptionPaint.textSize = descriptionTextSize
    val centerOrRight: Float
    if (descriptionLayoutPolicy == DescriptionLayoutPolicy.OVERLAY) {
      val displayWidth = span.width - descriptionHorizontalPadding * 2
      descriptionPaint.textScaleX = min(1f, displayWidth / span.descriptionWidth)
      descriptionPaint.textAlign = Align.CENTER
      centerOrRight = (span.left + span.right) / 2f
    } else {
      descriptionPaint.textScaleX = 1f
      descriptionPaint.textAlign = Align.RIGHT
      centerOrRight = span.right - descriptionHorizontalPadding
    }

    // Render first "N" description lines based on the layout height.
    val descriptionTextSize = descriptionTextSize
    val descriptionHeight = row.height - descriptionVerticalPadding * 2
    val numDescriptionLines =
      min((descriptionHeight / descriptionTextSize).toInt(), descriptionList.size)
    var top =
      (row.top + row.height) -
        descriptionVerticalPadding -
        descriptionTextSize * (numDescriptionLines - 1)
    for (description in descriptionList.subList(0, numDescriptionLines)) {
      canvas.drawText(description, centerOrRight, top, descriptionPaint)
      top += descriptionTextSize
    }
  }
}
