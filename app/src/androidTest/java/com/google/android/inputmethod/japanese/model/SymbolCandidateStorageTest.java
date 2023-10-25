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

package org.mozc.android.inputmethod.japanese.model;

import static org.easymock.EasyMock.expect;

import org.mozc.android.inputmethod.japanese.emoji.EmojiProviderType;
import org.mozc.android.inputmethod.japanese.model.SymbolCandidateStorage.SymbolHistoryStorage;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateList;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates.CandidateWord;
import org.mozc.android.inputmethod.japanese.testing.InstrumentationTestCaseWithMock;
import org.mozc.android.inputmethod.japanese.testing.Parameter;
import com.google.common.base.Optional;

import android.test.suitebuilder.annotation.SmallTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Tests for SymbolCandidateStorage.
 *
 */
public class SymbolCandidateStorageTest extends InstrumentationTestCaseWithMock {

  @SmallTest
  public void testToCandidateList() {
    CandidateList candidateList = SymbolCandidateStorage.toCandidateList(
        Arrays.asList(
            "1",
            "\uff12", // ２
            "33"),
        Optional.<Map<String, String>>absent());

    assertEquals(3, candidateList.getCandidatesCount());
    // "1" has description.
    assertTrue(candidateList.getCandidates(0).getAnnotation().getDescription().length() != 0);
    // "２" does not have description because it is not HANKAKU character.
    assertEquals(0, candidateList.getCandidates(1).getAnnotation().getDescription().length());
    // "33" does not have description because its length is not 1.
    assertEquals(0, candidateList.getCandidates(2).getAnnotation().getDescription().length());
  }

  @SmallTest
  public void testGetCandidateListHistory() {
    SymbolHistoryStorage historyStorage = createMock(SymbolHistoryStorage.class);
    expect(historyStorage.getAllHistory(SymbolMajorCategory.SYMBOL))
        .andStubReturn(Collections.singletonList("SYMBOL_HISTORY"));
    expect(historyStorage.getAllHistory(SymbolMajorCategory.EMOTICON))
        .andStubReturn(Collections.singletonList("EMOTICON_HISTORY"));
    expect(historyStorage.getAllHistory(SymbolMajorCategory.EMOJI))
        .andStubReturn(Collections.singletonList("EMOJI_HISTORY"));
    replayAll();

    class TestData extends Parameter {
      final SymbolMinorCategory minorCategory;
      final String expectedHistory;

      TestData(SymbolMinorCategory minorCategory, String expectedHistory) {
        this.minorCategory = minorCategory;
        this.expectedHistory = expectedHistory;
      }
    }
    TestData[] testDataList = {
        new TestData(SymbolMinorCategory.SYMBOL_HISTORY, "SYMBOL_HISTORY"),
        new TestData(SymbolMinorCategory.EMOTICON_HISTORY, "EMOTICON_HISTORY"),
        new TestData(SymbolMinorCategory.EMOJI_HISTORY, "EMOJI_HISTORY"),
    };

    SymbolCandidateStorage candidateStorage = new SymbolCandidateStorage(historyStorage);
    for (TestData testData : testDataList) {
      assertEquals(
          testData.toString(),
          CandidateList.newBuilder()
              .addCandidates(CandidateWord.newBuilder()
                  .setId(0)
                  .setIndex(0)
                  .setValue(testData.expectedHistory))
              .build(),
          candidateStorage.getCandidateList(testData.minorCategory));
    }
  }


  @SmallTest
  public void testMinorCategoryGetCandidateListEmoji() {
    class TestData extends Parameter {
      final EmojiProviderType emojiProviderType;
      final String providerEmojiCodePoint;

      TestData(EmojiProviderType emojiProviderType, String providerEmoji) {
        this.emojiProviderType = emojiProviderType;
        this.providerEmojiCodePoint = providerEmoji;
      }
    }

    // Test by carriers' marks.
    TestData[] testDataList = {
        new TestData(EmojiProviderType.DOCOMO, "\uDBBB\uDE10"),  // i-mode icon.
        new TestData(EmojiProviderType.KDDI, "\uDBBB\uDE40"),  // ez-mark icon.
        new TestData(EmojiProviderType.SOFTBANK, "\uDBB9\uDCC5"),  // shibuya icon.
    };
    String unicodeEmojiCodePoint = "\uD83C\uDF15";  // full moon symbol

    SymbolCandidateStorage candidateStorage =
        new SymbolCandidateStorage(createMock(SymbolHistoryStorage.class));
    for (TestData testData : testDataList) {
      candidateStorage.setEmojiProviderType(testData.emojiProviderType);

      for (boolean unicodeEmojiEnabled : new boolean[] {true, false}) {
        for (boolean carrierEmojiEnabled : new boolean[] {true, false}) {
          String testLabel = testData.toString() + ", unicode:" + unicodeEmojiEnabled
              + ", carrier:" + carrierEmojiEnabled;
          candidateStorage.setEmojiEnabled(unicodeEmojiEnabled, carrierEmojiEnabled);
          CandidateList candidateList =
              candidateStorage.getCandidateList(SymbolMinorCategory.EMOJI_NATURE);

          if (carrierEmojiEnabled) {
            // The emoji list should contain only the current provider's icon.
            for (TestData targetData : testDataList) {
              assertEquals(testLabel + ", " + targetData.toString(),
                           testData.emojiProviderType == targetData.emojiProviderType,
                           containsCandidate(candidateList, targetData.providerEmojiCodePoint));
            }
          } else {
            // The emoji list should contain only the current provider's icon.
            for (TestData targetData : testDataList) {
              assertFalse(testLabel + ", " + targetData.toString(),
                          containsCandidate(candidateList, targetData.providerEmojiCodePoint));
            }
          }
          assertEquals(testLabel, unicodeEmojiEnabled,
                       containsCandidate(candidateList, unicodeEmojiCodePoint));
        }
      }
    }
  }

  private static boolean containsCandidate(CandidateList candidateList, String value) {
    for (CandidateWord candidateWord : candidateList.getCandidatesList()) {
      if (value.equals(candidateWord.getValue())) {
        return true;
      }
    }
    return false;
  }
}
