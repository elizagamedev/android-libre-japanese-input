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
package sh.eliza.japaneseinput.userdictionary

import android.view.View
import com.google.android.material.snackbar.Snackbar
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryCommandStatus
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryCommandStatus.Status
import sh.eliza.japaneseinput.R

/** Manages snackbar message. */
class SnackbarManager(
  private val view: View,
) {
  /** Displays the message of the `resourceId` with short duration. */
  fun showMessageShortly(resourceId: Int) {
    showMessageShortlyInternal(resourceId)
  }

  /** Displays the message for the `status` with short duration. */
  fun maybeShowMessageShortly(status: UserDictionaryCommandStatus.Status) {
    if (status == UserDictionaryCommandStatus.Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      return
    }
    val value = ERROR_MESSAGE_MAP[status] ?: R.string.user_dictionary_tool_status_error_general
    showMessageShortlyInternal(value)
  }

  private fun showMessageShortlyInternal(resourceId: Int) {
    Snackbar.make(view, resourceId, Snackbar.LENGTH_SHORT).show()
  }
}

/** Mapping from Status to the resource id. */
private val ERROR_MESSAGE_MAP =
  mapOf(
    UserDictionaryCommandStatus.Status.FILE_NOT_FOUND to
      R.string.user_dictionary_tool_status_error_file_not_found,
    UserDictionaryCommandStatus.Status.INVALID_FILE_FORMAT to
      R.string.user_dictionary_tool_status_error_invalid_file_format,
    UserDictionaryCommandStatus.Status.FILE_SIZE_LIMIT_EXCEEDED to
      R.string.user_dictionary_tool_status_error_file_size_limit_exceeded,
    UserDictionaryCommandStatus.Status.DICTIONARY_SIZE_LIMIT_EXCEEDED to
      R.string.user_dictionary_tool_status_error_dictionary_size_limit_exceeded,
    UserDictionaryCommandStatus.Status.ENTRY_SIZE_LIMIT_EXCEEDED to
      R.string.user_dictionary_tool_status_error_entry_size_limit_exceeded,
    UserDictionaryCommandStatus.Status.DICTIONARY_NAME_EMPTY to
      R.string.user_dictionary_tool_status_error_dictionary_name_empty,
    UserDictionaryCommandStatus.Status.DICTIONARY_NAME_TOO_LONG to
      R.string.user_dictionary_tool_status_error_dictionary_name_too_long,
    UserDictionaryCommandStatus.Status.DICTIONARY_NAME_CONTAINS_INVALID_CHARACTER to
      R.string.user_dictionary_tool_status_error_dictionary_name_contains_invalid_character,
    UserDictionaryCommandStatus.Status.DICTIONARY_NAME_DUPLICATED to
      R.string.user_dictionary_tool_status_error_dictionary_name_duplicated,
    UserDictionaryCommandStatus.Status.READING_EMPTY to
      R.string.user_dictionary_tool_status_error_reading_empty,
    UserDictionaryCommandStatus.Status.READING_TOO_LONG to
      R.string.user_dictionary_tool_status_error_reading_too_long,
    UserDictionaryCommandStatus.Status.READING_CONTAINS_INVALID_CHARACTER to
      R.string.user_dictionary_tool_status_error_reading_contains_invalid_character,
    UserDictionaryCommandStatus.Status.WORD_EMPTY to
      R.string.user_dictionary_tool_status_error_word_empty,
    UserDictionaryCommandStatus.Status.WORD_TOO_LONG to
      R.string.user_dictionary_tool_status_error_word_too_long,
    UserDictionaryCommandStatus.Status.WORD_CONTAINS_INVALID_CHARACTER to
      R.string.user_dictionary_tool_status_error_word_contains_invalid_character,
    UserDictionaryCommandStatus.Status.IMPORT_TOO_MANY_WORDS to
      R.string.user_dictionary_tool_status_error_import_too_many_words,
    UserDictionaryCommandStatus.Status.IMPORT_INVALID_ENTRIES to
      R.string.user_dictionary_tool_status_error_import_invalid_entries,
    UserDictionaryCommandStatus.Status.NO_UNDO_HISTORY to
      R.string.user_dictionary_tool_status_error_no_undo_history,
  )
