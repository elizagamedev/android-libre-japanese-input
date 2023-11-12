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
package sh.eliza.japaneseinput

import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import com.google.common.base.Preconditions
import com.google.common.base.Strings
import com.google.protobuf.ByteString
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification

/** Utility class */
object MozcUtil {
  // Cursor position values.
  // According to the restriction of IMF,
  // the cursor position must be at the head or tail of precomposition.
  const val CURSOR_POSITION_HEAD = 0
  const val CURSOR_POSITION_TAIL = 1

  // Tag for logging.
  // This constant value affects only the logs printed by Java layer.
  // If you want to change the tag name, see also kProductPrefix in base/const.h.
  const val LOGTAG = "Mozc"

  private var uptimeMillis: Long? = null
  private const val SHOW_INPUT_METHOD_PICKER_WHAT = 0

  /**
   * Lazy creation of a handler.
   *
   * Creating a handler must be done in threads which has a Looper. This lazy creation enables
   * worker threads (which don't have a Looper) to access this class.
   */
  private val showInputMethodPickerHandler by lazy {
    Handler(Looper.getMainLooper(), InputMethodPickerShowingCallback())
  }

  const val IME_OPTION_NO_MICROPHONE_COMPAT = "nm"
  const val IME_OPTION_NO_MICROPHONE = "com.google.android.inputmethod.latin.noMicrophoneKey"
  private const val USER_DICTIONARY_EXPORT_DIR = "user_dictionary_export"

  private fun checkApplicationFlag(context: Context, flag: Int): Boolean {
    val manager = context.packageManager
    return try {
      val appInfo = manager.getApplicationInfo(context.packageName, 0)
      appInfo.flags and flag != 0
    } catch (e: PackageManager.NameNotFoundException) {
      MozcLog.w("PackageManager#getApplicationInfo cannot find this application.")
      false
    }
  }

  /**
   * Gets version name.
   *
   * @return version name string or empty. Non-null is guaranteed.
   */
  fun getVersionName(context: Context): String {
    val manager = context.packageManager
    try {
      val packageInfo = manager.getPackageInfo(context.packageName, 0)
      return Strings.nullToEmpty(packageInfo.versionName)
    } catch (e: PackageManager.NameNotFoundException) {
      MozcLog.e("Package info error", e)
    }
    return ""
  }

  /**
   * Gets raw (ABI dependent) version code.
   *
   * @return version code. Note that this version code contains ABI specific mask.
   */
  fun getVersionCode(context: Context): Long {
    val manager = context.packageManager
    try {
      val packageInfo = manager.getPackageInfo(context.packageName, 0)
      return if (Build.VERSION.SDK_INT >= 28) {
        packageInfo.longVersionCode
      } else {
        @Suppress("deprecation") packageInfo.versionCode.toLong()
      }
    } catch (e: PackageManager.NameNotFoundException) {
      MozcLog.e("Package info error", e)
    }
    return 0
  }

  // /**
  //  * Gets ABI independent version code.
  //  *
  //  * ABI independent version code is equivalent to "Build number" of Mozc project.
  //  *
  //  * Must be consistent with mozc_version.py
  //  */
  // @JvmStatic
  // fun getAbiIndependentVersionCode(context: Context): Int {
  //   // Version code format:
  //   // 00000BBBBB or
  //   // 0006BBBBBA
  //   // A: ABI (0: Fat, 6: x86_64, 5:arm64, 4:mips64, 3: x86, 2: armeabi-v7a, 1:mips)
  //   // B: ANDROID_VERSION_CODE
  //   val rawVersionCode = getVersionCode(context).toInt()
  //   val versionCode = Integer.toString(getVersionCode(context).toInt())
  //   return if (versionCode.length == 7 && versionCode[0] == '6') {
  //     Integer.valueOf(versionCode.substring(1, versionCode.length - 1))
  //   } else rawVersionCode
  // }

  /**
   * @param context an application context. Shouldn't be `null`.
   * @return `true` if Mozc is enabled. Otherwise `false`.
   */
  fun isMozcEnabled(context: Context): Boolean {
    val inputMethodManager =
      context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val packageName = context.packageName
    for (inputMethodInfo in inputMethodManager.enabledInputMethodList) {
      if (inputMethodInfo.serviceName.startsWith(packageName)) {
        return true
      }
    }
    return false
  }

  /**
   * @param context an application context. Shouldn't be `null`.
   * @return `true` if the default IME is Mozc. Otherwise `false`.
   */
  fun isMozcDefaultIme(context: Context): Boolean {
    val mozcInputMethodInfo = getMozcInputMethodInfo(context)
    if (mozcInputMethodInfo == null) {
      MozcLog.w("Mozc's InputMethodInfo is not found.")
      return false
    }
    val currentIme =
      Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return mozcInputMethodInfo.id == currentIme
  }

  private fun getMozcInputMethodInfo(context: Context): InputMethodInfo? {
    val inputMethodManager =
      context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val packageName = context.packageName
    for (inputMethodInfo in inputMethodManager.inputMethodList) {
      if (inputMethodInfo.packageName == packageName) {
        return inputMethodInfo
      }
    }

    // Not found.
    return null
  }

  /** Returns true is touch UI should be shown. */
  fun isTouchUI(context: Context) =
    context.resources.configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH

  @JvmStatic
  fun requestShowInputMethodPicker(context: Context): Boolean {
    return showInputMethodPickerHandler.sendMessage(
      showInputMethodPickerHandler.obtainMessage(SHOW_INPUT_METHOD_PICKER_WHAT, context)
    )
  }

  fun cancelShowInputMethodPicker(context: Context) {
    showInputMethodPickerHandler.removeMessages(SHOW_INPUT_METHOD_PICKER_WHAT, context)
  }

  fun hasShowInputMethodPickerRequest(context: Context): Boolean {
    return showInputMethodPickerHandler.hasMessages(SHOW_INPUT_METHOD_PICKER_WHAT, context)
  }

  /**
   * Sets the given `token` and some layout parameters required to show the dialog from the IME
   * service correctly to the `dialog`.
   */
  fun setWindowToken(token: IBinder, dialog: Dialog) {
    val window = dialog.window!!
    val layoutParams =
      window.attributes.apply {
        this.token = token
        type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        flags = flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
      }
    window.attributes = layoutParams
  }

  private fun getRequestBuilderInternal(
    specification: KeyboardSpecification,
    configuration: Configuration
  ): ProtoCommands.Request.Builder {
    return ProtoCommands.Request.newBuilder()
      .setKeyboardName(specification.keyboardSpecificationName.formattedKeyboardName(configuration))
      .setSpecialRomanjiTable(specification.specialRomanjiTable)
      .setSpaceOnAlphanumeric(specification.spaceOnAlphanumeric)
      .setKanaModifierInsensitiveConversion(specification.isKanaModifierInsensitiveConversion)
      .setCrossingEdgeBehavior(specification.crossingEdgeBehavior)
  }

  private fun setHardwareKeyboardRequest(
    builder: ProtoCommands.Request.Builder,
    resources: Resources
  ) {
    builder
      .setMixedConversion(false)
      .setZeroQuerySuggestion(false)
      .setUpdateInputModeFromSurroundingText(true)
      .setAutoPartialSuggestion(false)
      .setCandidatePageSize(resources.getInteger(R.integer.floating_candidate_candidate_num))
  }

  @JvmStatic
  fun setSoftwareKeyboardRequest(builder: ProtoCommands.Request.Builder) {
    builder
      .setMixedConversion(true)
      .setZeroQuerySuggestion(true)
      .setUpdateInputModeFromSurroundingText(false)
      .setAutoPartialSuggestion(true)
  }

  fun getRequestBuilder(
    resources: Resources,
    specification: KeyboardSpecification,
    configuration: Configuration,
  ): ProtoCommands.Request.Builder {
    val builder = getRequestBuilderInternal(specification, configuration)
    if (specification.isHardwareKeyboard) {
      setHardwareKeyboardRequest(builder, resources)
    } else {
      setSoftwareKeyboardRequest(builder)
    }
    return builder
  }

  fun isPasswordField(inputType: Int): Boolean {
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val inputVariation = inputType and InputType.TYPE_MASK_VARIATION
    return (inputClass == InputType.TYPE_CLASS_TEXT &&
      (inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
        inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
        inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
  }

  /**
   * Returns true if voice input is preferred by EditorInfo.inputType.
   *
   * Caller should check that voice input is allowed or not by isVoiceInputAllowed().
   */
  fun isVoiceInputPreferred(editorInfo: EditorInfo): Boolean {
    // Check privateImeOptions to ensure the text field supports voice input.
    if (editorInfo.privateImeOptions != null) {
      for (option in
        editorInfo
          .privateImeOptions
          .split(",".toRegex())
          .dropLastWhile { it.isEmpty() }
          .toTypedArray()) {
        if (option == IME_OPTION_NO_MICROPHONE || option == IME_OPTION_NO_MICROPHONE_COMPAT) {
          return false
        }
      }
    }
    val inputType = editorInfo.inputType
    if (isNumberKeyboardPreferred(inputType) || isPasswordField(inputType)) {
      return false
    }
    if (inputType and EditorInfo.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
      when (inputType and EditorInfo.TYPE_MASK_VARIATION) {
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI -> return false
        else -> {}
      }
    }
    return true
  }

  /** Returns true if number keyboard is preferred by EditorInfo.inputType. */
  @JvmStatic
  fun isNumberKeyboardPreferred(inputType: Int): Boolean {
    val typeClass = inputType and InputType.TYPE_MASK_CLASS
    // As of API Level 21, following condition equals to "typeClass != InputType.TYPE_CLASS_TEXT".
    // However type-class might be added in future so safer expression is employed here.
    return typeClass == InputType.TYPE_CLASS_DATETIME ||
      typeClass == InputType.TYPE_CLASS_NUMBER ||
      typeClass == InputType.TYPE_CLASS_PHONE
  }

  fun utf8CStyleByteStringToString(value: ByteString): String {
    // Find '\0' terminator. (if value doesn't contain '\0', the size should be as same as
    // value's.)
    var index = 0
    while (index < value.size() && value.byteAt(index).toInt() != 0) {
      ++index
    }
    val bytes = ByteArray(index)
    value.substring(0, bytes.size).copyTo(bytes, 0)
    return String(bytes, StandardCharsets.UTF_8)
  }

  /**
   * Simple utility to close [Closeable] instance.
   *
   * A typical usage is as follows:
   *
   * <pre>`Closeable stream = ...; boolean succeeded = false; try { // Read data from stream here.
   * ...
   *
   * succeeded = true; } finally { close(stream, !succeeded); } `</pre> *
   *
   * @param closeable
   * @param ignoreException
   * @throws IOException
   */
  @JvmStatic
  @Throws(IOException::class)
  fun close(closeable: Closeable, ignoreException: Boolean) {
    try {
      closeable.close()
    } catch (e: IOException) {
      if (!ignoreException) {
        throw e
      }
    }
  }

  // /**
  //  * Simple utility to close [ParcelFileDescriptor] instance. See [MozcUtil.close] for details.
  //  *
  //  * On later OS ParcelFileDescriptor implements [Closeable] but on earlier OS it doesn't.
  //  */
  // @Throws(IOException::class)
  // fun close(descriptor: ParcelFileDescriptor, ignoreIOException: Boolean) {
  //   Preconditions.checkNotNull(descriptor)
  //   try {
  //     descriptor.close()
  //   } catch (e: IOException) {
  //     if (!ignoreIOException) {
  //       throw e
  //     }
  //   }
  // }

  /** Closes the given `closeable`, and ignore any `IOException`s. */
  @JvmStatic
  fun closeIgnoringIOException(closeable: Closeable) {
    try {
      closeable.close()
    } catch (e: IOException) {
      MozcLog.e("Failed to close.", e)
    }
  }

  /** Closes the given `closeable`, and ignore any `IOException`s. */
  @JvmStatic
  fun closeIgnoringIOException(zipFile: ZipFile) {
    try {
      zipFile.close()
    } catch (e: IOException) {
      MozcLog.e("Failed to close.", e)
    }
  }

  /**
   * Get temporary directory for user dictionary export feature.
   *
   * This method creates a new directory if it doesn't exist.
   */
  @JvmStatic
  fun getUserDictionaryExportTempDirectory(context: Context): File {
    val directory = File(context.cacheDir.absolutePath, USER_DICTIONARY_EXPORT_DIR)
    if (directory.exists()) {
      check(directory.isDirectory)
    } else {
      directory.mkdir()
    }
    return directory
  }

  /**
   * Delete contents of the directory.
   *
   * The root directory itself is NOT deleted.
   *
   * @return true if all entries are successfully deleted.
   */
  @JvmStatic
  fun deleteDirectoryContents(directory: File): Boolean {
    require(directory.isDirectory)
    val files = directory.listFiles() ?: return true
    var result = true
    for (entry in files) {
      if (entry.isDirectory) {
        result = result and deleteDirectoryContents(entry)
      }
      result = result and entry.delete()
    }
    return result
  }

  /** If value &gt;= max returns max. If value &lt;= min returns min. Otherwise returns value. */
  @JvmStatic fun clamp(value: Int, min: Int, max: Int) = max(min(value, max), min)

  /** If value &gt;= max returns max. If value &lt;= min returns min. Otherwise returns value. */
  @JvmStatic fun clamp(value: Float, min: Float, max: Float) = max(min(value, max), min)

  /** Get a dimension for the specified orientation. */
  @JvmStatic
  fun getDimensionForOrientation(context: Context, id: Int, orientation: Int): Float {
    val configuration = context.resources.configuration
    if (configuration.orientation == orientation) {
      return context.resources.getDimension(id)
    }
    configuration.orientation = orientation
    val newContext = context.createConfigurationContext(configuration)
    return newContext.resources.getDimension(id)
  }

  internal class InputMethodPickerShowingCallback : Handler.Callback {
    override fun handleMessage(msg: Message): Boolean {
      val context = Preconditions.checkNotNull(msg).obj as Context
      (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .showInputMethodPicker()
      return false
    }
  }
}
