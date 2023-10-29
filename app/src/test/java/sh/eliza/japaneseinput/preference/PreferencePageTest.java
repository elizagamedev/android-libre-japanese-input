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

package sh.eliza.japaneseinput.preference;

import android.test.suitebuilder.annotation.SmallTest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import sh.eliza.japaneseinput.R;
import sh.eliza.japaneseinput.testing.Parameter;

/** */
public class PreferencePageTest extends TestCase {
  @SmallTest
  public void testResourceIdList() {
    class TestData extends Parameter {
      final PreferencePage preferencePage;
      final boolean isDebug;
      final boolean isSendingInformationFeaturesEnabled;
      final List<Integer> expectedResources;

      TestData(
          PreferencePage preferencePage,
          boolean isDebug,
          boolean isSendingInformationFeaturesEnabled,
          List<Integer> expectedResources) {
        this.preferencePage = preferencePage;
        this.isDebug = isDebug;
        this.isSendingInformationFeaturesEnabled = isSendingInformationFeaturesEnabled;
        this.expectedResources = expectedResources;
      }
    }
    TestData[] testDataList = {
      new TestData(PreferencePage.ABOUT, true, true, List.of(R.xml.pref_about)),
      new TestData(PreferencePage.ABOUT, true, false, List.of(R.xml.pref_about)),
      new TestData(PreferencePage.ABOUT, false, true, List.of(R.xml.pref_about)),
      new TestData(PreferencePage.ABOUT, false, false, List.of(R.xml.pref_about)),
      new TestData(PreferencePage.CONVERSION, true, true, List.of(R.xml.pref_conversion)),
      new TestData(PreferencePage.CONVERSION, true, false, List.of(R.xml.pref_conversion)),
      new TestData(PreferencePage.CONVERSION, false, true, List.of(R.xml.pref_conversion)),
      new TestData(PreferencePage.CONVERSION, false, false, List.of(R.xml.pref_conversion)),
      new TestData(PreferencePage.DEVELOPMENT, true, true, List.of(R.xml.pref_development)),
      new TestData(PreferencePage.DEVELOPMENT, true, false, List.of(R.xml.pref_development)),
      new TestData(PreferencePage.DEVELOPMENT, false, true, List.of(R.xml.pref_development)),
      new TestData(PreferencePage.DEVELOPMENT, false, false, List.of(R.xml.pref_development)),
      new TestData(PreferencePage.DICTIONARY, true, true, List.of(R.xml.pref_dictionary)),
      new TestData(PreferencePage.DICTIONARY, true, false, List.of(R.xml.pref_dictionary)),
      new TestData(PreferencePage.DICTIONARY, false, true, List.of(R.xml.pref_dictionary)),
      new TestData(PreferencePage.DICTIONARY, false, false, List.of(R.xml.pref_dictionary)),
      new TestData(
          PreferencePage.FLAT,
          true,
          true,
          Arrays.asList(
              R.xml.pref_software_keyboard,
              R.xml.pref_input_support,
              R.xml.pref_conversion,
              R.xml.pref_dictionary,
              R.xml.pref_user_feedback,
              R.xml.pref_about,
              R.xml.pref_development)),
      // R.xml.pref_user_feedback is omitted.
      new TestData(
          PreferencePage.FLAT,
          true,
          false,
          Arrays.asList(
              R.xml.pref_software_keyboard,
              R.xml.pref_input_support,
              R.xml.pref_conversion,
              R.xml.pref_dictionary,
              R.xml.pref_about,
              R.xml.pref_development)),
      // R.xml.pref_development is omitted.
      new TestData(
          PreferencePage.FLAT,
          false,
          true,
          Arrays.asList(
              R.xml.pref_software_keyboard,
              R.xml.pref_input_support,
              R.xml.pref_conversion,
              R.xml.pref_dictionary,
              R.xml.pref_user_feedback,
              R.xml.pref_about)),
      // R.xml.pref_user_feedback and R.xml.pref_development are omitted.
      new TestData(
          PreferencePage.FLAT,
          false,
          false,
          Arrays.asList(
              R.xml.pref_software_keyboard,
              R.xml.pref_input_support,
              R.xml.pref_conversion,
              R.xml.pref_dictionary,
              R.xml.pref_about)),
      new TestData(PreferencePage.INPUT_SUPPORT, true, true, List.of(R.xml.pref_input_support)),
      new TestData(PreferencePage.INPUT_SUPPORT, true, false, List.of(R.xml.pref_input_support)),
      new TestData(PreferencePage.INPUT_SUPPORT, false, true, List.of(R.xml.pref_input_support)),
      new TestData(PreferencePage.INPUT_SUPPORT, false, false, List.of(R.xml.pref_input_support)),
      new TestData(
          PreferencePage.SOFTWARE_KEYBOARD,
          true,
          true,
          List.of(R.xml.pref_software_keyboard_advanced)),
      new TestData(
          PreferencePage.SOFTWARE_KEYBOARD,
          true,
          false,
          List.of(R.xml.pref_software_keyboard_advanced)),
      new TestData(
          PreferencePage.SOFTWARE_KEYBOARD,
          false,
          true,
          List.of(R.xml.pref_software_keyboard_advanced)),
      new TestData(
          PreferencePage.SOFTWARE_KEYBOARD,
          false,
          false,
          List.of(R.xml.pref_software_keyboard_advanced)),
      new TestData(PreferencePage.USER_FEEDBACK, true, true, List.of(R.xml.pref_user_feedback)),
      new TestData(PreferencePage.USER_FEEDBACK, true, false, List.of(R.xml.pref_user_feedback)),
      new TestData(PreferencePage.USER_FEEDBACK, false, true, List.of(R.xml.pref_user_feedback)),
      new TestData(PreferencePage.USER_FEEDBACK, false, false, List.of(R.xml.pref_user_feedback)),
    };

    for (TestData testData : testDataList) {
      assertEquals(
          testData.toString(),
          testData.expectedResources,
          PreferencePage.getResourceIdList(
              testData.preferencePage,
              testData.isDebug,
              testData.isSendingInformationFeaturesEnabled));
    }

    // Safety harness.
    // If this class and PreferencePage conflict, below test will fail.
    Set<PreferencePage> testedPreferencePages = EnumSet.noneOf(PreferencePage.class);
    for (TestData testData : testDataList) {
      testedPreferencePages.add(testData.preferencePage);
    }
    for (PreferencePage preferencePage : PreferencePage.values()) {
      assertTrue(
          String.format("%s is needed to be tested.", preferencePage.name()),
          testedPreferencePages.remove(preferencePage));
    }
    // testedPreferencePages is always empty here.
  }
}
