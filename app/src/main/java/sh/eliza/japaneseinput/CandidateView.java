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

package sh.eliza.japaneseinput;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateList;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateWord;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input;
import sh.eliza.japaneseinput.MozcView.InputFrameFoldButtonClickListener;
import sh.eliza.japaneseinput.keyboard.BackgroundDrawableFactory.DrawableType;
import sh.eliza.japaneseinput.ui.CandidateLayoutRenderer.DescriptionLayoutPolicy;
import sh.eliza.japaneseinput.ui.CandidateLayoutRenderer.ValueScalingPolicy;
import sh.eliza.japaneseinput.ui.ConversionCandidateLayouter;
import sh.eliza.japaneseinput.ui.InputFrameFoldButtonView;
import sh.eliza.japaneseinput.ui.ScrollGuideView;
import sh.eliza.japaneseinput.ui.SpanFactory;
import sh.eliza.japaneseinput.view.Skin;

/** The view to show candidates. */
public class CandidateView extends InOutAnimatedFrameLayout implements MemoryManageable {

  /** Adapter for conversion candidate selection. */
  private static class ConversionCandidateSelectListener implements CandidateSelectListener {
    private final ViewEventListener viewEventListener;

    ConversionCandidateSelectListener(ViewEventListener viewEventListener) {
      this.viewEventListener = Preconditions.checkNotNull(viewEventListener);
    }

    @Override
    public void onCandidateSelected(
        View view, CandidateWord candidateWord, Optional<Integer> rowIndex) {
      viewEventListener.onConversionCandidateSelected(
          view, candidateWord.getId(), Preconditions.checkNotNull(rowIndex));
    }
  }

  static class ConversionCandidateWordView extends CandidateWordView {
    /** Delimiter to split description text into lines. */
    private static final String DESCRIPTION_DELIMITER = " \t\n\r\f";

    ScrollGuideView scrollGuideView = null;
    InputFrameFoldButtonView inputFrameFoldButtonView = null;
    private int foldButtonBackgroundVisibilityThreshold = 0;

    // TODO(hidehiko): Simplify the interface as this is needed just for expandSuggestion.
    private ViewEventListener viewEventListener;

    // If viewEventListener.onExpandSuggestion() has been called and now we shouldn't call
    // this method any more until currentCandidateList is replaced with completely different one,
    // this flag is true.
    private boolean isExpanded = false;

    {
      setSpanBackgroundDrawableType(DrawableType.CANDIDATE_BACKGROUND);
      layouter = new ConversionCandidateLayouter();
    }

    public ConversionCandidateWordView(Context context, AttributeSet attributeSet) {
      super(context, attributeSet, CandidateWordView.Orientation.VERTICAL);
      Resources resources = getResources();
      scroller.setDecayRate(
          resources.getInteger(R.integer.candidate_scroller_velocity_decay_rate) / 1000000f);
      scroller.setMinimumVelocity(
          resources.getInteger(R.integer.candidate_scroller_minimum_velocity));
    }

    private void setCandidateTextDimension(float candidateTextSize, float descriptionTextSize) {
      Preconditions.checkArgument(candidateTextSize > 0);
      Preconditions.checkArgument(descriptionTextSize > 0);

      Resources resources = getResources();

      float valueHorizontalPadding =
          resources.getDimension(R.dimen.candidate_horizontal_padding_size);
      float valueVerticalPadding = resources.getDimension(R.dimen.candidate_vertical_padding_size);
      float descriptionHorizontalPadding =
          resources.getDimension(R.dimen.symbol_description_right_padding);
      float descriptionVerticalPadding =
          resources.getDimension(R.dimen.symbol_description_bottom_padding);
      float separatorWidth = resources.getDimensionPixelSize(R.dimen.candidate_separator_width);

      candidateLayoutRenderer.setValueTextSize(candidateTextSize);
      candidateLayoutRenderer.setValueHorizontalPadding(valueHorizontalPadding);
      candidateLayoutRenderer.setValueScalingPolicy(ValueScalingPolicy.HORIZONTAL);
      candidateLayoutRenderer.setDescriptionTextSize(descriptionTextSize);
      candidateLayoutRenderer.setDescriptionHorizontalPadding(descriptionHorizontalPadding);
      candidateLayoutRenderer.setDescriptionVerticalPadding(descriptionVerticalPadding);
      candidateLayoutRenderer.setDescriptionLayoutPolicy(DescriptionLayoutPolicy.EXCLUSIVE);
      candidateLayoutRenderer.setSeparatorWidth(separatorWidth);

      SpanFactory spanFactory = new SpanFactory();
      spanFactory.setValueTextSize(candidateTextSize);
      spanFactory.setDescriptionTextSize(descriptionTextSize);
      spanFactory.setDescriptionDelimiter(DESCRIPTION_DELIMITER);

      // This resource is ppm. Let's divide by 1,000,000.
      float candidateWidthCompressionRate =
          resources.getInteger(R.integer.candidate_width_compress_rate) / 1000000f;
      float candidateTextMinimumWidth =
          resources.getDimension(R.dimen.candidate_text_minimum_width);
      float candidateChunkMinimumWidth =
          candidateTextSize + resources.getDimension(R.dimen.candidate_vertical_padding_size) * 2;

      ConversionCandidateLayouter layouter = (ConversionCandidateLayouter) this.layouter;
      layouter.setSpanFactory(spanFactory);
      layouter.setValueWidthCompressionRate(candidateWidthCompressionRate);
      layouter.setMinValueWidth(candidateTextMinimumWidth);
      layouter.setMinChunkWidth(candidateChunkMinimumWidth);
      layouter.setValueHeight(candidateTextSize);
      layouter.setValueHorizontalPadding(valueHorizontalPadding);
      layouter.setValueVerticalPadding(valueVerticalPadding);

      foldButtonBackgroundVisibilityThreshold = (int) (1.8 * valueVerticalPadding);
    }

    @Override
    ConversionCandidateLayouter getCandidateLayouter() {
      return (ConversionCandidateLayouter) super.getCandidateLayouter();
    }

    void setViewEventListener(ViewEventListener viewEventListener) {
      this.viewEventListener = viewEventListener;
    }

    @Override
    void reset() {
      super.reset();
      isExpanded = false;
    }

    @Override
    protected void onScrollChanged(int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
      super.onScrollChanged(scrollX, scrollY, oldScrollX, oldScrollY);
      updateScrollGuide();
      if (inputFrameFoldButtonView != null) {
        inputFrameFoldButtonView.showBackgroundForScrolled(
            scrollY > foldButtonBackgroundVisibilityThreshold);
      }
      expandSuggestionIfNeeded();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      updateScrollGuide();
      expandSuggestionIfNeeded();
    }

    void expandSuggestionIfNeeded() {
      if (calculatedLayout != null) {
        // If not yet expanded and current clip bounds is approaching the bottom of calculated
        // layout, expand the candidates. "/3" is a heuristic.
        if (!isExpanded && getScrollY() + getHeight() > calculatedLayout.getContentHeight() / 3) {
          isExpanded = true;
          if (viewEventListener != null) {
            viewEventListener.onExpandSuggestion();
          }
        }
      }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
      // User taps the candidate view, so we may want to expand the suggestion.
      expandSuggestionIfNeeded();
      return super.onTouchEvent(e);
    }

    void updateScrollGuide() {
      if (calculatedLayout != null && scrollGuideView != null) {
        // Draw scroll guide.
        scrollGuideView.invalidate();
      }
    }

    @Override
    void update(CandidateList candidateList) {
      super.update(candidateList);
      isExpanded = false;
      updateScrollPositionBasedOnFocusedIndex();
      updateScrollGuide();
    }

    @Override
    protected Drawable getViewBackgroundDrawable(Skin skin) {
      return skin.conversionCandidateViewBackgroundDrawable;
    }

    @Override
    public void setSkin(Skin skin) {
      super.setSkin(skin);
      candidateLayoutRenderer.setSeparatorColor(skin.candidateBackgroundSeparatorColor);
    }
  }

  public CandidateView(Context context) {
    super(context);
  }

  public CandidateView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    // Connect candidate word view and its scroll guide.
    ScrollGuideView scrollGuideView = getScrollGuideView();
    ConversionCandidateWordView conversionCandidateWordView = getConversionCandidateWordView();
    scrollGuideView.setScroller(conversionCandidateWordView.scroller);
    conversionCandidateWordView.scrollGuideView = scrollGuideView;
    conversionCandidateWordView.inputFrameFoldButtonView = getInputFrameFoldButton();

    reset();
  }

  @Override
  public void setVisibility(int visibility) {
    boolean isHiding = (getVisibility() == View.VISIBLE) && (visibility != View.VISIBLE);
    super.setVisibility(visibility);
    if (isHiding) {
      // Release candidate list when the out-animation is finished, as it won't be used any more.
      update(null);
    }
  }

  private InputFrameFoldButtonView getInputFrameFoldButton() {
    return findViewById(R.id.input_frame_fold_button);
  }

  private ConversionCandidateWordView getConversionCandidateWordView() {
    return findViewById(R.id.candidate_word_view);
  }

  private ConversionCandidateWordContainerView getConversionCandidateWordContainerView() {
    return findViewById(R.id.conversion_candidate_word_container_view);
  }

  private ScrollGuideView getScrollGuideView() {
    return findViewById(R.id.candidate_scroll_guide_view);
  }

  private LinearLayout getCandidateWordFrame() {
    return findViewById(R.id.candidate_word_frame);
  }

  /** Updates the view based on {@code Command}. */
  void update(Command outCommand) {
    if (outCommand == null) {
      getConversionCandidateWordView().update(null);
      return;
    }

    Input input = outCommand.getInput();
    CandidateList allCandidateWords = outCommand.getOutput().getAllCandidateWords();
    // TODO(exv): fix this
    // if (input.getType() == CommandType.SEND_COMMAND
    //     && input.getCommand().getType() == SessionCommand.CommandType.EXPAND_SUGGESTION) {
    //   getConversionCandidateWordView().updateForExpandSuggestion(allCandidateWords);
    // } else {
    //   getConversionCandidateWordView().update(allCandidateWords);
    // }
    // TODO(exv): above block replaced with this
    getConversionCandidateWordView().update(allCandidateWords);
  }

  /** Register callback object. */
  void setViewEventListener(ViewEventListener listener) {
    Preconditions.checkNotNull(listener);
    ConversionCandidateWordView conversionCandidateWordView = getConversionCandidateWordView();
    conversionCandidateWordView.setViewEventListener(listener);
    conversionCandidateWordView.setCandidateSelectListener(
        new ConversionCandidateSelectListener(listener));
  }

  void setInputFrameFoldButtonOnClickListener(InputFrameFoldButtonClickListener listener) {
    getInputFrameFoldButton().setOnClickListener(Preconditions.checkNotNull(listener));
  }

  void reset() {
    getInputFrameFoldButton().setChecked(false);
    getConversionCandidateWordView().reset();
  }

  void setInputFrameFoldButtonChecked(boolean checked) {
    getInputFrameFoldButton().setChecked(checked);
  }

  void setSkin(Skin skin) {
    Preconditions.checkNotNull(skin);
    getScrollGuideView().setSkin(skin);
    getConversionCandidateWordView().setSkin(skin);
    getInputFrameFoldButton().setSkin(skin);
    getCandidateWordFrame().setBackgroundColor(skin.candidateBackgroundBottomColor);
    invalidate();
  }

  void setCandidateTextDimension(float candidateTextSize, float descriptionTextSize) {
    Preconditions.checkArgument(candidateTextSize > 0);
    Preconditions.checkArgument(descriptionTextSize > 0);

    getConversionCandidateWordView()
        .setCandidateTextDimension(candidateTextSize, descriptionTextSize);
    getConversionCandidateWordContainerView().setCandidateTextDimension(candidateTextSize);
  }

  void enableFoldButton(boolean enabled) {
    getInputFrameFoldButton().setVisibility(enabled ? VISIBLE : GONE);
    getConversionCandidateWordView()
        .getCandidateLayouter()
        .reserveEmptySpanForInputFoldButton(enabled);
  }

  @Override
  public void trimMemory() {
    getConversionCandidateWordView().trimMemory();
  }
}
