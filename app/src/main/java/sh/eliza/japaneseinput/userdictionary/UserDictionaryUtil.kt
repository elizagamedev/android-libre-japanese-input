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

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.IntentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.io.RandomAccessFile
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.Entry
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.PosType
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryCommandStatus
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryCommandStatus.Status
import sh.eliza.japaneseinput.MozcUtil
import sh.eliza.japaneseinput.R

/** Utilities (of, especially, UI related stuff) for the user dictionary tool. */
internal object UserDictionaryUtil {
  interface DictionaryNameDialogListener {
    /**
     * Callback to be called when the positive button is clicked.
     *
     * @param dictionaryName the text which is filled in EditText on the dialog.
     * @return result status of the executed command.
     */
    fun onPositiveButtonClicked(dictionaryName: String?): UserDictionaryCommandStatus.Status
  }

  interface WordRegisterDialogListener {
    /**
     * Callback to be called when the positive button is clicked.
     *
     * @return result status of the executed command.
     */
    fun onPositiveButtonClicked(
      word: String?,
      reading: String?,
      pos: PosType?
    ): UserDictionaryCommandStatus.Status
  }

  /**
   * Word Register Dialog implementation for adding/editing entries.
   *
   * The dialog should have:
   *
   * * A EditText for "word" editing,
   * * A EditText for "reading" editing, and
   * * A Spinner for "pos" selecting.
   */
  class WordRegisterDialog(
    context: Context,
    titleResourceId: Int,
    listener: WordRegisterDialogListener,
    snackbarManager: SnackbarManager
  ) :
    UserDictionaryBaseDialog(
      context,
      titleResourceId,
      R.layout.user_dictionary_tool_word_register_dialog_view,
      object : UserDictionaryBaseDialogListener {
        override fun onPositiveButtonClicked(view: View): UserDictionaryCommandStatus.Status {
          return listener.onPositiveButtonClicked(
            getText(view, R.id.user_dictionary_tool_word_register_dialog_word),
            getText(view, R.id.user_dictionary_tool_word_register_dialog_reading),
            getPos(view, R.id.user_dictionary_tool_word_register_dialog_pos)
          )
        }
      },
      snackbarManager
    ) {
    /**
     * Sets the entry so that users can see it when the dialog is shown. To make editing convenient,
     * select all region.
     */
    fun setEntry(entry: ProtoUserDictionaryStorage.UserDictionary.Entry) {
      val wordEditText =
        findViewById<EditText>(R.id.user_dictionary_tool_word_register_dialog_word)!!
      wordEditText.setText(entry.value)

      findViewById<EditText>(R.id.user_dictionary_tool_word_register_dialog_reading)!!.setText(
        entry.key
      )

      val posSpinner = findViewById<Spinner>(R.id.user_dictionary_tool_word_register_dialog_pos)!!
      val numItems = posSpinner.count
      for (i in 0 until numItems) {
        if ((posSpinner.getItemAtPosition(i) as PosItem).posType == entry.pos) {
          posSpinner.setSelection(i)
          break
        }
      }

      // Focus "word" field by default.
      wordEditText.requestFocus()
    }
  }

  /** Dialog implementation which has one edit box for dictionary name editing. */
  class DictionaryNameDialog(
    context: Context,
    titleResourceId: Int,
    listener: DictionaryNameDialogListener,
    snackbarManager: SnackbarManager
  ) :
    UserDictionaryBaseDialog(
      context,
      titleResourceId,
      R.layout.user_dictionary_tool_dictionary_name_dialog_view,
      object : UserDictionaryBaseDialogListener {
        override fun onPositiveButtonClicked(view: View): UserDictionaryCommandStatus.Status {
          return listener.onPositiveButtonClicked(
            getText(view, R.id.user_dictionary_tool_dictionary_name_dialog_name)
          )
        }
      },
      snackbarManager
    ) {
    /**
     * Sets the dictionaryName so that users can see it when the dialog is shown. To make editing
     * convenient, select all region.
     */
    fun setDictionaryName(dictionaryName: String) {
      val editText = findViewById<EditText>(R.id.user_dictionary_tool_dictionary_name_dialog_name)!!
      editText.setText(dictionaryName)
      editText.selectAll()
    }
  }

  /**
   * Base implementation of popup dialog on user dictionary tool.
   *
   * This class has:
   *
   * * Title messaging
   * * Content view based on the given resource id
   * * Cancel and OK buttons
   *
   * When the OK button is clicked, UserDictionaryBaseDialogListener callback is invoked. The
   * expected usage is invoke something action by interacting with the mozc server. If the
   * interaction fails (in more precise, the returned status is not
   * USER_DICTIONARY_COMMAND_SUCCESS), this class will show a toast message, and the popup dialog
   * won't be dismissed.
   */
  sealed class UserDictionaryBaseDialog(
    context: Context,
    titleResourceId: Int,
    viewResourceId: Int,
    private val listener: UserDictionaryBaseDialogListener,
    private val snackbarManager: SnackbarManager
  ) : AlertDialog(context) {
    init {
      // Initialize the view. Set the title, the content view and ok, cancel buttons.
      setTitle(titleResourceId)
      setView(LayoutInflater.from(context).inflate(viewResourceId, null))

      // Set a dummy Message instance to fix crashing issue on Android 2.1.
      // On Android 2.1, com.android.internal.app.AlertController wrongly checks the message
      // for the availability of button view. So without the dummy message,
      // getButton(BUTTON_POSITIVE) used in onCreate would return null.
      setButton(BUTTON_POSITIVE, context.getText(android.R.string.ok), Message.obtain())
      setButton(
        BUTTON_NEGATIVE,
        context.getText(android.R.string.cancel),
        null as DialogInterface.OnClickListener?
      )
      setCancelable(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

      // To override the default behavior that the dialog is dismissed after user's clicking
      // a button regardless of any action inside listener, we set the callback directly
      // to the button and manage dismissing behavior.
      // Note that it is necessary to do this here, instead of in the constructor,
      // because the UI is initialized in super class's onCreate method, and we cannot obtain
      // the button until the initialization.
      getButton(BUTTON_POSITIVE).setOnClickListener { view ->
        val status = listener.onPositiveButtonClicked(view)
        snackbarManager.maybeShowMessageShortly(status)
        if (status == UserDictionaryCommandStatus.Status.USER_DICTIONARY_COMMAND_SUCCESS) {
          // Dismiss the dialog, iff the operation is successfully done.
          dismiss()
        }
      }
    }
  }

  /** Returns import source uri based on the Intent. */
  @JvmStatic
  fun getImportUri(intent: Intent): Uri? =
    when (intent.action) {
      Intent.ACTION_SEND ->
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
      Intent.ACTION_VIEW -> intent.data
      else -> null
    }

  /** Returns a name for a new dictionary based on import URI. */
  @JvmStatic
  fun generateDictionaryNameByUri(importUri: Uri, dictionaryNameList: List<String>): String? {
    var name = importUri.lastPathSegment ?: return null

    // Strip extension
    val index = name.lastIndexOf('.')
    if (index > 0) {
      // Keep file path beginning with '.'.
      name = name.substring(0, index)
    }
    if (!dictionaryNameList.contains(name)) {
      // No-dupped dictionary name.
      return name
    }

    // The names extracted from uri is dupped. So look for alternative names by adding
    // number suffix, such as "pathname (1)".
    var suffix = 1
    while (true) {
      val candidate = "$name ($suffix)"
      if (!dictionaryNameList.contains(candidate)) {
        return candidate
      }
      // The limit of the number of dictionaries is much smaller than Integer.MAX_VALUE,
      // so this while loop should stop eventually.
      ++suffix
    }
  }

  /** Returns string resource id for the given `pos`. */
  @JvmStatic fun getPosStringResourceId(pos: PosType): Int = POS_RESOURCE_MAP[pos]!!

  /** Returns string resource id for the given `pos` for dictionary export. */
  @JvmStatic
  fun getPosStringResourceIdForDictionaryExport(pos: PosType): Int =
    POS_RESOURCE_MAP_FOR_DICTIONARY_EXPORT[pos]!!

  /** Returns the instance for Word Register Dialog. */
  @JvmStatic
  fun createWordRegisterDialog(
    context: Context,
    titleResourceId: Int,
    listener: WordRegisterDialogListener,
    snackbarManager: SnackbarManager
  ) = WordRegisterDialog(context, titleResourceId, listener, snackbarManager)

  /** Returns a new instance for Dictionary Name Dialog. */
  @JvmStatic
  fun createDictionaryNameDialog(
    context: Context,
    titleResourceId: Int,
    listener: DictionaryNameDialogListener,
    snackbarManager: SnackbarManager
  ) = DictionaryNameDialog(context, titleResourceId, listener, snackbarManager)

  /** Returns a new instance for a dialog to select a file in a zipfile. */
  @JvmStatic
  fun createZipFileSelectionDialog(
    context: Context,
    titleResourceId: Int,
    positiveButtonListener: DialogInterface.OnClickListener,
    negativeButtonListener: DialogInterface.OnClickListener,
    cancelListener: DialogInterface.OnCancelListener
  ): Dialog =
    createSimpleSpinnerDialog(
      context,
      titleResourceId,
      positiveButtonListener,
      negativeButtonListener,
      cancelListener
    )

  /** Returns a new instance for a dialog to select import destination. */
  @JvmStatic
  fun createImportDictionarySelectionDialog(
    context: Context,
    titleResourceId: Int,
    positiveButtonListener: DialogInterface.OnClickListener,
    negativeButtonListener: DialogInterface.OnClickListener,
    cancelListener: DialogInterface.OnCancelListener
  ): Dialog =
    createSimpleSpinnerDialog(
      context,
      titleResourceId,
      positiveButtonListener,
      negativeButtonListener,
      cancelListener
    )

  /** Sets the parameter to show IME when the given dialog is shown. */
  @JvmStatic
  fun showInputMethod(dialog: Dialog) {
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
  }

  /**
   * Returns the `String` instance with detecting the Japanese encoding.
   *
   * @throws UnsupportedEncodingException if it fails to detect the encoding.
   */
  @JvmStatic
  @Throws(UnsupportedEncodingException::class)
  fun toStringWithEncodingDetection(buffer: ByteBuffer): String {
    for (encoding in JAPANESE_ENCODING_LIST) {
      buffer.position(0)
      try {
        val charset = Charset.forName(encoding)
        val result =
          charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(buffer)
        if (result.length > 0 && result[0].code == 0xFEFF) {
          result.position(result.position() + 1) // Skip BOM
        }
        return result.toString()
      } catch (e: Exception) {
        // Ignore exceptions, and retry next encoding.
      }
    }
    throw UnsupportedEncodingException("Failed to detect encoding")
  }

  /** Reads the text file with detecting the file encoding. */
  @JvmStatic
  @Throws(IOException::class)
  fun readFromFile(path: String): String {
    val file = RandomAccessFile(path, "r")
    var succeeded = false
    return try {
      val result = readFromFileInternal(file)
      succeeded = true
      result
    } finally {
      MozcUtil.close(file, !succeeded)
    }
  }

  /**
   * Spinner implementation which has a list of POS.
   *
   * To keep users out from confusing UI, this class hides the IME when the list dialog is shown by
   * user's tap.
   */
  class PosSpinner
  @JvmOverloads
  constructor(
    context: Context,
    attrs: AttributeSet? = null,
  ) : AppCompatSpinner(context, attrs) {
    override fun onFinishInflate() {
      super.onFinishInflate()
      // Set adapter containing a list of POS types.
      val adapter =
        ArrayAdapter(context, android.R.layout.simple_spinner_item, createPosItemList(resources))
          .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
      setAdapter(adapter)
    }

    override fun performClick(): Boolean {
      // When the spinner is tapped (i.e. when the popup dialog is shown),
      // we hide the soft input method.
      // This is because the list of the POS is long so if the soft input is shown
      // continuously, a part of list would be out of the display.
      val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.hideSoftInputFromWindow(windowToken, 0)
      return super.performClick()
    }
  }
}

/** A map from PosType to the string resource id for i18n. */
private val POS_RESOURCE_MAP =
  mapOf(
      PosType.NOUN to R.string.japanese_pos_noun,
      PosType.ABBREVIATION to R.string.japanese_pos_abbreviation,
      PosType.SUGGESTION_ONLY to R.string.japanese_pos_suggestion_only,
      PosType.PROPER_NOUN to R.string.japanese_pos_proper_noun,
      PosType.PERSONAL_NAME to R.string.japanese_pos_personal_name,
      PosType.FAMILY_NAME to R.string.japanese_pos_family_name,
      PosType.FIRST_NAME to R.string.japanese_pos_first_name,
      PosType.ORGANIZATION_NAME to R.string.japanese_pos_organization_name,
      PosType.PLACE_NAME to R.string.japanese_pos_place_name,
      PosType.SA_IRREGULAR_CONJUGATION_NOUN to R.string.japanese_pos_sa_irregular_conjugation_noun,
      PosType.ADJECTIVE_VERBAL_NOUN to R.string.japanese_pos_adjective_verbal_noun,
      PosType.NUMBER to R.string.japanese_pos_number,
      PosType.ALPHABET to R.string.japanese_pos_alphabet,
      PosType.SYMBOL to R.string.japanese_pos_symbol,
      PosType.EMOTICON to R.string.japanese_pos_emoticon,
      PosType.ADVERB to R.string.japanese_pos_adverb,
      PosType.PRENOUN_ADJECTIVAL to R.string.japanese_pos_prenoun_adjectival,
      PosType.CONJUNCTION to R.string.japanese_pos_conjunction,
      PosType.INTERJECTION to R.string.japanese_pos_interjection,
      PosType.PREFIX to R.string.japanese_pos_prefix,
      PosType.COUNTER_SUFFIX to R.string.japanese_pos_counter_suffix,
      PosType.GENERIC_SUFFIX to R.string.japanese_pos_generic_suffix,
      PosType.PERSON_NAME_SUFFIX to R.string.japanese_pos_person_name_suffix,
      PosType.PLACE_NAME_SUFFIX to R.string.japanese_pos_place_name_suffix,
      PosType.WA_GROUP1_VERB to R.string.japanese_pos_wa_group1_verb,
      PosType.KA_GROUP1_VERB to R.string.japanese_pos_ka_group1_verb,
      PosType.SA_GROUP1_VERB to R.string.japanese_pos_sa_group1_verb,
      PosType.TA_GROUP1_VERB to R.string.japanese_pos_ta_group1_verb,
      PosType.NA_GROUP1_VERB to R.string.japanese_pos_na_group1_verb,
      PosType.MA_GROUP1_VERB to R.string.japanese_pos_ma_group1_verb,
      PosType.RA_GROUP1_VERB to R.string.japanese_pos_ra_group1_verb,
      PosType.GA_GROUP1_VERB to R.string.japanese_pos_ga_group1_verb,
      PosType.BA_GROUP1_VERB to R.string.japanese_pos_ba_group1_verb,
      PosType.HA_GROUP1_VERB to R.string.japanese_pos_ha_group1_verb,
      PosType.GROUP2_VERB to R.string.japanese_pos_group2_verb,
      PosType.KURU_GROUP3_VERB to R.string.japanese_pos_kuru_group3_verb,
      PosType.SURU_GROUP3_VERB to R.string.japanese_pos_suru_group3_verb,
      PosType.ZURU_GROUP3_VERB to R.string.japanese_pos_zuru_group3_verb,
      PosType.RU_GROUP3_VERB to R.string.japanese_pos_ru_group3_verb,
      PosType.ADJECTIVE to R.string.japanese_pos_adjective,
      PosType.SENTENCE_ENDING_PARTICLE to R.string.japanese_pos_sentence_ending_particle,
      PosType.PUNCTUATION to R.string.japanese_pos_punctuation,
      PosType.FREE_STANDING_WORD to R.string.japanese_pos_free_standing_word,
      PosType.SUPPRESSION_WORD to R.string.japanese_pos_suppression_word,
    )
    .apply {
      // Subtract one to account for NO_POS.
      check(size == PosType.values().size - 1)
    }

/** A map from PosType to the string resource id dictionary export. */
private val POS_RESOURCE_MAP_FOR_DICTIONARY_EXPORT =
  mapOf(
      PosType.NOUN to R.string.japanese_pos_for_dictionary_export_noun,
      PosType.ABBREVIATION to R.string.japanese_pos_for_dictionary_export_abbreviation,
      PosType.SUGGESTION_ONLY to R.string.japanese_pos_for_dictionary_export_suggestion_only,
      PosType.PROPER_NOUN to R.string.japanese_pos_for_dictionary_export_proper_noun,
      PosType.PERSONAL_NAME to R.string.japanese_pos_for_dictionary_export_personal_name,
      PosType.FAMILY_NAME to R.string.japanese_pos_for_dictionary_export_family_name,
      PosType.FIRST_NAME to R.string.japanese_pos_for_dictionary_export_first_name,
      PosType.ORGANIZATION_NAME to R.string.japanese_pos_for_dictionary_export_organization_name,
      PosType.PLACE_NAME to R.string.japanese_pos_for_dictionary_export_place_name,
      PosType.SA_IRREGULAR_CONJUGATION_NOUN to
        R.string.japanese_pos_for_dictionary_export_sa_irregular_conjugation_noun,
      PosType.ADJECTIVE_VERBAL_NOUN to
        R.string.japanese_pos_for_dictionary_export_adjective_verbal_noun,
      PosType.NUMBER to R.string.japanese_pos_for_dictionary_export_number,
      PosType.ALPHABET to R.string.japanese_pos_for_dictionary_export_alphabet,
      PosType.SYMBOL to R.string.japanese_pos_for_dictionary_export_symbol,
      PosType.EMOTICON to R.string.japanese_pos_for_dictionary_export_emoticon,
      PosType.ADVERB to R.string.japanese_pos_for_dictionary_export_adverb,
      PosType.PRENOUN_ADJECTIVAL to R.string.japanese_pos_for_dictionary_export_prenoun_adjectival,
      PosType.CONJUNCTION to R.string.japanese_pos_for_dictionary_export_conjunction,
      PosType.INTERJECTION to R.string.japanese_pos_for_dictionary_export_interjection,
      PosType.PREFIX to R.string.japanese_pos_for_dictionary_export_prefix,
      PosType.COUNTER_SUFFIX to R.string.japanese_pos_for_dictionary_export_counter_suffix,
      PosType.GENERIC_SUFFIX to R.string.japanese_pos_for_dictionary_export_generic_suffix,
      PosType.PERSON_NAME_SUFFIX to R.string.japanese_pos_for_dictionary_export_person_name_suffix,
      PosType.PLACE_NAME_SUFFIX to R.string.japanese_pos_for_dictionary_export_place_name_suffix,
      PosType.WA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_wa_group1_verb,
      PosType.KA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_ka_group1_verb,
      PosType.SA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_sa_group1_verb,
      PosType.TA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_ta_group1_verb,
      PosType.NA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_na_group1_verb,
      PosType.MA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_ma_group1_verb,
      PosType.RA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_ra_group1_verb,
      PosType.GA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_ga_group1_verb,
      PosType.BA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_ba_group1_verb,
      PosType.HA_GROUP1_VERB to R.string.japanese_pos_for_dictionary_export_ha_group1_verb,
      PosType.GROUP2_VERB to R.string.japanese_pos_for_dictionary_export_group2_verb,
      PosType.KURU_GROUP3_VERB to R.string.japanese_pos_for_dictionary_export_kuru_group3_verb,
      PosType.SURU_GROUP3_VERB to R.string.japanese_pos_for_dictionary_export_suru_group3_verb,
      PosType.ZURU_GROUP3_VERB to R.string.japanese_pos_for_dictionary_export_zuru_group3_verb,
      PosType.RU_GROUP3_VERB to R.string.japanese_pos_for_dictionary_export_ru_group3_verb,
      PosType.ADJECTIVE to R.string.japanese_pos_for_dictionary_export_adjective,
      PosType.SENTENCE_ENDING_PARTICLE to
        R.string.japanese_pos_for_dictionary_export_sentence_ending_particle,
      PosType.PUNCTUATION to R.string.japanese_pos_for_dictionary_export_punctuation,
      PosType.FREE_STANDING_WORD to R.string.japanese_pos_for_dictionary_export_free_standing_word,
      PosType.SUPPRESSION_WORD to R.string.japanese_pos_for_dictionary_export_suppression_word,
    )
    .apply {
      // Subtract one to account for NO_POS.
      check(size != PosType.values().size - 1)
    }

/**
 * List of Japanese encodings to convert text for data importing. These encodings are tries in the
 * order, in other words, UTF-8 is the most high-prioritized encoding.
 */
private val JAPANESE_ENCODING_LIST = listOf("UTF-8", "EUC-JP", "ISO-2022-JP", "Shift_JIS", "UTF-16")

/** Returns the text content of the view with the given resourceId. */
private fun getText(view: View, resourceId: Int): String {
  val textView = view.rootView.findViewById<View>(resourceId) as TextView
  return textView.text.toString()
}

/** Returns the PosType of the view with the given resourceId. */
private fun getPos(view: View, resourceId: Int): PosType {
  val spinner = view.rootView.findViewById<View>(resourceId) as Spinner
  return (spinner.selectedItem as PosItem).posType
}

@Throws(IOException::class)
private fun readFromFileInternal(file: RandomAccessFile): String {
  val channel = file.channel
  var succeeded = false
  return try {
    val result =
      UserDictionaryUtil.toStringWithEncodingDetection(
        channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
      )
    succeeded = true
    result
  } finally {
    MozcUtil.close(channel, !succeeded)
  }
}

/** Callback which is called when the "positive button" on a dialog is clicked. */
private interface UserDictionaryBaseDialogListener {
  fun onPositiveButtonClicked(view: View): UserDictionaryCommandStatus.Status
}

/** Simple class to show the internationalized POS names on a spinner. */
private class PosItem(val posType: PosType, val name: String) {
  // This is a hook to show the appropriate (internationalized) items on spinner.
  override fun toString(): String {
    return name
  }
}

private fun createSimpleSpinnerDialog(
  context: Context,
  titleResourceId: Int,
  positiveButtonListener: DialogInterface.OnClickListener,
  negativeButtonListener: DialogInterface.OnClickListener,
  cancelListener: DialogInterface.OnCancelListener
): Dialog {
  val view =
    LayoutInflater.from(context)
      .inflate(R.layout.user_dictionary_tool_simple_spinner_dialog_view, null)
  return MaterialAlertDialogBuilder(context)
    .setTitle(titleResourceId)
    .setView(view)
    .setPositiveButton(android.R.string.ok, positiveButtonListener)
    .setNegativeButton(android.R.string.cancel, negativeButtonListener)
    .setOnCancelListener(cancelListener)
    .setCancelable(true)
    .create()
}

private fun createPosItemList(resources: Resources): List<PosItem> =
  PosType.values().map {
    PosItem(it, resources.getText(UserDictionaryUtil.getPosStringResourceId(it)).toString())
  }
