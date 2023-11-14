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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.Window
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import com.google.common.base.Optional
import java.util.Locale
import kotlin.math.max
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.CompositionMode
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input.TouchEvent
import sh.eliza.japaneseinput.FeedbackManager.FeedbackEvent
import sh.eliza.japaneseinput.KeycodeConverter.KeyEventInterface
import sh.eliza.japaneseinput.ViewManagerInterface.LayoutAdjustment
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboard
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboard.CompositionSwitchMode
import sh.eliza.japaneseinput.keyboard.KeyEntity
import sh.eliza.japaneseinput.keyboard.KeyEventHandler
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification
import sh.eliza.japaneseinput.keyboard.KeyboardActionListener
import sh.eliza.japaneseinput.keyboard.KeyboardFactory
import sh.eliza.japaneseinput.keyboard.ProbableKeyEventGuesser
import sh.eliza.japaneseinput.model.JapaneseSoftwareKeyboardModel
import sh.eliza.japaneseinput.model.JapaneseSoftwareKeyboardModel.KeyboardMode
import sh.eliza.japaneseinput.model.SymbolCandidateStorage
import sh.eliza.japaneseinput.model.SymbolCandidateStorage.SymbolHistoryStorage
import sh.eliza.japaneseinput.model.SymbolMajorCategory
import sh.eliza.japaneseinput.preference.ClientSidePreference.HardwareKeyMap
import sh.eliza.japaneseinput.preference.ClientSidePreference.InputStyle
import sh.eliza.japaneseinput.preference.ClientSidePreference.KeyboardLayout
import sh.eliza.japaneseinput.ui.MenuDialog
import sh.eliza.japaneseinput.ui.MenuDialog.MenuDialogListener
import sh.eliza.japaneseinput.util.ImeSwitcher
import sh.eliza.japaneseinput.view.Skin

private const val NEXUS_KEYBOARD_VENDOR_ID = 0x0D62
private const val NEXUS_KEYBOARD_PRODUCT_ID = 0x160B

/** Manages Input, Candidate and Extracted views. */
class ViewManager
private constructor(
  context: Context,
  listener: ViewEventListener,
  symbolHistoryStorage: SymbolHistoryStorage?,
  imeSwitcher: ImeSwitcher,
  menuDialogListener: MenuDialogListener?,
  guesser: ProbableKeyEventGuesser?,
  hardwareKeyboard: HardwareKeyboard
) : ViewManagerInterface {
  /** An small wrapper to inject keyboard view resizing when a user selects a candidate. */
  internal inner class ViewManagerEventListener(delegated: ViewEventListener) :
    ViewEventDelegator(delegated) {
    override fun onConversionCandidateSelected(
      view: View,
      candidateId: Int,
      rowIndex: Optional<Int>
    ) {
      // Restore the keyboard frame if hidden.
      mozcView?.resetKeyboardFrameVisibility()
      super.onConversionCandidateSelected(view, candidateId, rowIndex)
    }
  }

  /** Converts S/W Keyboard's keycode to KeyEvent instance. */
  fun onKey(primaryCode: Int, touchEventList: List<TouchEvent>) {
    if (primaryCode == keycodeCapslock || primaryCode == keycodeAlt) {
      // Ignore those key events because they are handled by KeyboardView,
      // but send touchEventList for logging usage stats.
      eventListener.onKeyEvent(null, null, null, touchEventList)
      return
    }

    // Keyboard switch event.
    if (primaryCode == keycodeChartypeToKana ||
        primaryCode == keycodeChartypeToAbc ||
        primaryCode == keycodeChartypeToAbc123
    ) {
      if (primaryCode == keycodeChartypeToKana) {
        japaneseSoftwareKeyboardModel.keyboardMode = KeyboardMode.KANA
      } else if (primaryCode == keycodeChartypeToAbc) {
        japaneseSoftwareKeyboardModel.keyboardMode = KeyboardMode.ALPHABET
      } else {
        japaneseSoftwareKeyboardModel.keyboardMode = KeyboardMode.ALPHABET_NUMBER
      }
      propagateSoftwareKeyboardChange(touchEventList)
      return
    }
    if (primaryCode == keycodeGlobe) {
      imeSwitcher.switchToNextInputMethod(false)
      return
    }
    if (primaryCode == keycodeMenuDialog || primaryCode == keycodeImePickerDialog) {
      // We need to reset the keyboard, otherwise it would miss the ACTION_UP event.
      mozcView?.resetKeyboardViewState()
      eventListener.onShowMenuDialog(touchEventList)
      if (primaryCode == keycodeMenuDialog) {
        showMenuDialog()
      } else {
        showImePickerDialog()
      }
      return
    }
    if (primaryCode == keycodeSymbol) {
      mozcView?.showSymbolInputView(Optional.absent())
      return
    }
    if (primaryCode == keycodeSymbolEmoji) {
      mozcView?.showSymbolInputView(Optional.of(SymbolMajorCategory.EMOJI))
      return
    }
    if (primaryCode == keycodeUndo) {
      eventListener.onUndo(touchEventList)
      return
    }
    val mozcKeyEvent = primaryKeyCodeConverter.createMozcKeyEvent(primaryCode, touchEventList)
    eventListener.onKeyEvent(
      mozcKeyEvent.orNull(),
      primaryKeyCodeConverter.getPrimaryCodeKeyEvent(primaryCode),
      activeSoftwareKeyboardModel.keyboardSpecification,
      touchEventList
    )
  }

  /**
   * A simple KeyboardActionListener implementation which just delegates onKey event to
   * ViewManager's onKey method.
   */
  internal inner class KeyboardActionAdapter : KeyboardActionListener {
    override fun onCancel() {}
    override fun onKey(primaryCode: Int, touchEventList: List<TouchEvent>) {
      this@ViewManager.onKey(primaryCode, touchEventList)
    }

    override fun onPress(view: View, primaryCode: Int) {
      when (primaryCode) {
        KeyEntity.INVALID_KEY_CODE -> null
        keycodeBackspace -> FeedbackEvent.KEY_DELETE_DOWN
        keycodeEnter -> FeedbackEvent.KEY_RETURN_DOWN
        keycodeSpace -> FeedbackEvent.KEY_SPACEBAR_DOWN
        else ->
          if (specialKeys.contains(primaryCode)) {
            FeedbackEvent.KEY_SPECIAL_DOWN
          } else {
            FeedbackEvent.KEY_DOWN
          }
      }?.let { eventListener.onFireFeedbackEvent(view, it) }
    }

    override fun onRelease(view: View, primaryCode: Int) {}
  }

  private inner class ViewLayerEventHandler {
    private var isEmojiKeyDownAvailable = false
    private var isEmojiInvoking = false
    private var pressedKeyNum = 0

    private var disableDeviceCheck = false
    private fun hasPhysicalEmojiKey(event: KeyEvent): Boolean {
      val device = InputDevice.getDevice(event.deviceId)
      return disableDeviceCheck ||
        device != null &&
          device.vendorId == NEXUS_KEYBOARD_VENDOR_ID &&
          device.productId == NEXUS_KEYBOARD_PRODUCT_ID
    }

    private fun isEmojiKey(event: KeyEvent): Boolean {
      if (!hasPhysicalEmojiKey(event)) {
        return false
      }
      if (event.keyCode != KeyEvent.KEYCODE_ALT_LEFT && event.keyCode != KeyEvent.KEYCODE_ALT_RIGHT
      ) {
        return false
      }
      return if (event.action == KeyEvent.ACTION_UP) {
        event.hasNoModifiers()
      } else {
        event.hasModifiers(KeyEvent.META_ALT_ON)
      }
    }

    fun evaluateKeyEvent(event: KeyEvent): Boolean {
      if (event.action == KeyEvent.ACTION_DOWN) {
        ++pressedKeyNum
      } else if (event.action == KeyEvent.ACTION_UP) {
        pressedKeyNum = max(0, pressedKeyNum - 1)
      } else {
        return false
      }
      if (isEmojiKey(event)) {
        if (event.action == KeyEvent.ACTION_DOWN) {
          isEmojiKeyDownAvailable = true
          isEmojiInvoking = false
        } else if (isEmojiKeyDownAvailable && pressedKeyNum == 0) {
          isEmojiKeyDownAvailable = false
          isEmojiInvoking = true
        }
      } else {
        isEmojiKeyDownAvailable = false
        isEmojiInvoking = false
      }
      return isEmojiInvoking
    }

    operator fun invoke() {
      if (!isEmojiInvoking) {
        return
      }
      isEmojiInvoking = false
      val mozcView = mozcView
      if (mozcView != null) {
        if (isSymbolInputViewVisible) {
          hideSubInputView()
          if (!narrowMode) {
            setNarrowMode()
          }
        } else {
          isSymbolInputViewShownByEmojiKey = true
          if (narrowMode) {
            // Turned narrow mode off on all devices for Emoji palette.
            setNarrowModeWithoutVersionCheck(false)
          }
          mozcView.showSymbolInputView(Optional.of(SymbolMajorCategory.EMOJI))
        }
      }
    }

    fun reset() {
      isEmojiKeyDownAvailable = false
      isEmojiInvoking = false
      pressedKeyNum = 0
    }
  }

  // Registered by the user (typically MozcService)
  private val eventListener: ViewEventListener

  // The view of the MechaMozc.
  private var mozcView: MozcView? = null

  // Menu dialog and its listener.
  private val menuDialogListener: MenuDialogListener?

  private var menuDialog: MenuDialog? = null

  // IME switcher instance to detect that voice input is available or not.
  private val imeSwitcher: ImeSwitcher

  /** Key event handler to handle events on Mozc server. */
  private val keyEventHandler: KeyEventHandler

  /** Key event handler to handle events on view layer. */
  private val viewLayerKeyEventHandler = ViewLayerEventHandler()

  /**
   * Model to represent the current software keyboard state. All the setter methods don't affect
   * symbolNumberSoftwareKeyboardModel but japaneseSoftwareKeyboardModel.
   */
  private val japaneseSoftwareKeyboardModel = JapaneseSoftwareKeyboardModel()

  /**
   * Model to represent the number software keyboard state. Its keyboard mode is set in the
   * constructor to KeyboardMode.SYMBOL_NUMBER and will never be changed.
   */
  private val symbolNumberSoftwareKeyboardModel = JapaneseSoftwareKeyboardModel()

  private val hardwareKeyboard: HardwareKeyboard

  /** True if symbol input view is visible. */
  private var isSymbolInputViewVisible = false

  /** True if symbol input view is shown by the Emoji key on physical keyboard. */
  private var isSymbolInputViewShownByEmojiKey = false

  /** The factory of parsed keyboard data. */
  private val keyboardFactory = KeyboardFactory()
  private val symbolCandidateStorage: SymbolCandidateStorage

  /** Current fullscreen mode */
  private var fullscreenMode = false

  /** Current narrow mode */
  private var narrowMode = false

  /** Current narrow mode */
  private var narrowModeByConfiguration = false

  /** Current popup enabled state. */
  private var popupEnabled = true

  /** Current Globe button enabled state. */
  private var globeButtonEnabled = false

  /** True if CursorAnchorInfo is enabled. */
  private var cursorAnchroInfoEnabled = false

  /** True if hardware keyboard exists. */
  private var hardwareKeyboardExist = false

  /**
   * True if voice input is eligible.
   *
   * This conditions is calculated based on following conditions.
   *
   * * VoiceIME's status: If VoiceIME is not available, this flag becomes false.
   * * EditorInfo: If current editor does not want to use voice input, this flag becomes false.
   *
   * * Voice input might be explicitly forbidden by the editor.
   * * Voice input should be useless for the number input editors.
   * * Voice input should be useless for password field.
   */
  private var isVoiceInputEligible = false
  private var isVoiceInputEnabledByPreference = true
  private var flickSensitivity = 0

  /** Current skin type. */
  private var skin = Skin.getFallbackInstance()
  private var layoutAdjustment = LayoutAdjustment.FILL

  /** Percentage of keyboard height */
  private var keyboardHeightRatio = 100

  // Keycodes defined in resource files.
  // Printable keys are not defined. Refer them using character literal.
  // These are "constant values" as a matter of practice,
  // but such name like "KEYCODE_LEFT" makes Lint unhappy
  // because they are not "static final".
  private val keycodeChartypeToKana: Int
  private val keycodeChartypeToAbc: Int
  private val keycodeChartypeToAbc123: Int
  private val keycodeGlobe: Int
  private val keycodeSymbol: Int
  private val keycodeSymbolEmoji: Int
  private val keycodeUndo: Int
  private val keycodeCapslock: Int
  private val keycodeAlt: Int
  private val keycodeMenuDialog: Int
  private val keycodeImePickerDialog: Int
  private val keycodeBackspace: Int
  private val keycodeEnter: Int
  private val keycodeSpace: Int
  private val specialKeys: Set<Int>

  /** Handles software keyboard event and sends it to the service. */
  private val keyboardActionListener: KeyboardActionAdapter
  private val primaryKeyCodeConverter: PrimaryKeyCodeConverter

  constructor(
    context: Context,
    listener: ViewEventListener,
    symbolHistoryStorage: SymbolHistoryStorage?,
    imeSwitcher: ImeSwitcher,
    menuDialogListener: MenuDialogListener
  ) : this(
    context,
    listener,
    symbolHistoryStorage,
    imeSwitcher,
    menuDialogListener,
    ProbableKeyEventGuesser(context.assets),
    HardwareKeyboard()
  )

  init {
    primaryKeyCodeConverter = PrimaryKeyCodeConverter(context, guesser)
    symbolNumberSoftwareKeyboardModel.keyboardMode = KeyboardMode.SYMBOL_NUMBER

    // Prefetch keycodes from resource
    val res = context.resources
    keycodeChartypeToKana = res.getInteger(R.integer.key_chartype_to_kana)
    keycodeChartypeToAbc = res.getInteger(R.integer.key_chartype_to_abc)
    keycodeChartypeToAbc123 = res.getInteger(R.integer.key_chartype_to_abc_123)
    keycodeGlobe = res.getInteger(R.integer.key_globe)
    keycodeSymbol = res.getInteger(R.integer.key_symbol)
    keycodeSymbolEmoji = res.getInteger(R.integer.key_symbol_emoji)
    keycodeUndo = res.getInteger(R.integer.key_undo)
    keycodeCapslock = res.getInteger(R.integer.key_capslock)
    keycodeAlt = res.getInteger(R.integer.key_alt)
    keycodeMenuDialog = res.getInteger(R.integer.key_menu_dialog)
    keycodeImePickerDialog = res.getInteger(R.integer.key_ime_picker_dialog)
    keycodeBackspace = res.getInteger(R.integer.key_backspace)
    keycodeEnter = res.getInteger(R.integer.key_enter)
    keycodeSpace = res.getInteger(R.integer.uchar_space)
    specialKeys =
      setOf(
        res.getInteger(R.integer.key_up),
        res.getInteger(R.integer.key_down),
        res.getInteger(R.integer.key_left),
        res.getInteger(R.integer.key_right),
        keycodeChartypeToKana,
        keycodeChartypeToAbc,
        keycodeChartypeToAbc123,
        keycodeGlobe,
        keycodeSymbol,
        keycodeSymbolEmoji,
        keycodeUndo,
      )

    // Inject some logics into the listener.
    eventListener = ViewManagerEventListener(listener)
    keyboardActionListener = KeyboardActionAdapter()
    // Prepare callback object.
    keyEventHandler =
      KeyEventHandler(
        Looper.getMainLooper(),
        keyboardActionListener,
        res.getInteger(R.integer.config_repeat_key_delay),
        res.getInteger(R.integer.config_repeat_key_interval),
        res.getInteger(R.integer.config_long_press_key_delay)
      )
    this.imeSwitcher = imeSwitcher
    this.menuDialogListener = menuDialogListener
    symbolCandidateStorage = SymbolCandidateStorage(symbolHistoryStorage)
    this.hardwareKeyboard = hardwareKeyboard
  }

  /**
   * Creates new input view.
   *
   * "Input view" is a software keyboard in almost all cases.
   *
   * Previously created input view is not accessed any more after calling this method.
   *
   * @param context
   * @return newly created view.
   */
  override fun createMozcView(context: Context): MozcView {
    val contextWrapper =
      ContextThemeWrapper(
        context,
        com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar
      )
    @SuppressLint("InflateParams")
    val mozcView =
      (LayoutInflater.from(contextWrapper).inflate(R.layout.mozc_view, null) as MozcView).apply {
        // Suppress update of View's internal state
        // until all the updates done in this method are finished. Just in case.
        visibility = View.GONE
        setKeyboardHeightRatio(keyboardHeightRatio)
        setCursorAnchorInfoEnabled(cursorAnchroInfoEnabled)
        val widenButtonClickListener = OnClickListener {
          eventListener.onFireFeedbackEvent(it, FeedbackEvent.NARROW_FRAME_WIDEN_BUTTON_DOWN)
          setNarrowMode()
        }
        val leftAdjustButtonClickListener = OnClickListener {
          eventListener.onUpdateKeyboardLayoutAdjustment(LayoutAdjustment.LEFT)
        }
        val rightAdjustButtonClickListener = OnClickListener {
          eventListener.onUpdateKeyboardLayoutAdjustment(LayoutAdjustment.RIGHT)
        }
        val microphoneButtonClickListener = OnClickListener {
          eventListener.onFireFeedbackEvent(it, FeedbackEvent.MICROPHONE_BUTTON_DOWN)
          imeSwitcher.switchToVoiceIme(Locale.JAPANESE)
        }
        setEventListener(
          eventListener,
          widenButtonClickListener,
          // User pushes these buttons to move position in order to see hidden text in editing
          // rather than to change his/her favorite position. So we should not apply it to
          // preferences.
          leftAdjustButtonClickListener,
          rightAdjustButtonClickListener,
          microphoneButtonClickListener
        )
        setKeyEventHandler(keyEventHandler)
        propagateSoftwareKeyboardChange(emptyList())
        isFullscreenMode = fullscreenMode
        setLayoutAdjustmentAndNarrowMode(layoutAdjustment, narrowMode)
        // At the moment, it is necessary to set the storage to the view, *before* setting emoji
        // provider type.
        // TODO(hidehiko): Remove the restriction.
        setSymbolCandidateStorage(symbolCandidateStorage)
        setPopupEnabled(popupEnabled)
        setFlickSensitivity(flickSensitivity)
        setSkin(skin)
      }
    // Clear the menu dialog.
    menuDialog = null
    reset()
    mozcView.visibility = View.VISIBLE
    this.mozcView = mozcView
    return mozcView
  }

  private fun showMenuDialog() {
    val mozcView = mozcView
    if (mozcView == null) {
      MozcLog.w("mozcView is not initialized.")
      return
    }
    val menuDialog = MenuDialog(mozcView.context, Optional.fromNullable(menuDialogListener))
    val windowToken = mozcView.windowToken
    if (windowToken == null) {
      MozcLog.w("Unknown window token")
    } else {
      menuDialog.setWindowToken(windowToken)
    }
    menuDialog.show()
    this.menuDialog = menuDialog
  }

  private fun showImePickerDialog() {
    val mozcView = mozcView
    if (mozcView == null) {
      MozcLog.w("mozcView is not initialized.")
      return
    }
    if (!MozcUtil.requestShowInputMethodPicker(mozcView.context)) {
      MozcLog.e("Failed to send message to launch the input method picker dialog.")
    }
  }

  private fun maybeDismissMenuDialog() {
    val menuDialog = menuDialog
    menuDialog?.dismiss()
  }

  /**
   * Renders views which this instance own based on Command.Output.
   *
   * Note that showing/hiding views is Service's responsibility.
   */
  override fun render(outCommand: Command?) {
    if (outCommand == null) {
      return
    }
    val mozcView = mozcView ?: return
    if (outCommand.output.allCandidateWords.candidatesCount == 0 &&
        !outCommand.input.requestSuggestion
    ) {
      // The server doesn't return the suggestion result, because there is following
      // key sequence, which will trigger the suggest and the new suggestion will overwrite
      // the current suggest. In order to avoid chattering the candidate window,
      // we skip the following rendering.
      return
    }
    mozcView.setCommand(outCommand)
    if (outCommand.output.allCandidateWords.candidatesCount == 0) {
      // If the candidate is empty (i.e. the CandidateView will go to GONE),
      // reset the keyboard so that a user can type keyboard.
      mozcView.resetKeyboardFrameVisibility()
    }
  }

  /** @return the current keyboard specification. */
  override fun getKeyboardSpecification(): KeyboardSpecification {
    return activeSoftwareKeyboardModel.keyboardSpecification
  }

  /** Set `EditorInfo` instance to the current view. */
  override fun setEditorInfo(attribute: EditorInfo) {
    mozcView?.let {
      it.setEmojiEnabled(true)
      it.setPasswordField(MozcUtil.isPasswordField(attribute.inputType))
      it.setEditorInfo(attribute)
    }
    isVoiceInputEligible = MozcUtil.isVoiceInputPreferred(attribute)
    japaneseSoftwareKeyboardModel.setInputType(attribute.inputType)
    // TODO(hsumita): Set input type on Hardware keyboard, too. Otherwise, Hiragana input can be
    //                enabled unexpectedly. (e.g. Number text field.)
    propagateSoftwareKeyboardChange(emptyList())
  }

  private fun shouldVoiceImeBeEnabled(): Boolean {
    // Disable voice IME if hardware keyboard exists to avoid a framework bug.
    return (isVoiceInputEligible &&
      isVoiceInputEnabledByPreference &&
      !hardwareKeyboardExist &&
      imeSwitcher.isVoiceImeAvailable)
  }

  override fun setTextForActionButton(text: CharSequence?) {
    // TODO(mozc-team): Implement action button handling.
  }

  override fun hideSubInputView(): Boolean {
    // Try to hide a sub view from front to back.
    val mozcView = mozcView
    return mozcView?.hideSymbolInputView() ?: false
  }

  /**
   * Creates and sets a keyboard represented by the resource id to the input frame.
   *
   * Note that this method requires inputFrameView is not null, and its first child is the
   * JapaneseKeyboardView.
   */
  private fun updateKeyboardView() {
    val mozcView = mozcView ?: return
    val size = mozcView.keyboardSize
    val keyboard =
      keyboardFactory[
        mozcView.resources,
        japaneseSoftwareKeyboardModel.keyboardSpecification,
        size.width(),
        size.height()]
    mozcView.setKeyboard(keyboard)
    primaryKeyCodeConverter.setKeyboard(keyboard)
  }

  /** Propagates the change of S/W keyboard to the view layer and the H/W keyboard configuration. */
  private fun propagateSoftwareKeyboardChange(touchEventList: List<TouchEvent>) {
    val specification = japaneseSoftwareKeyboardModel.keyboardSpecification

    // TODO(team): The purpose of the following call of onKeyEvent() is to tell the change of
    // software keyboard specification to Mozc server through the event listener registered by
    // MozcService. Obviously, calling onKeyEvent() for this purpose is abuse and should be fixed.
    eventListener.onKeyEvent(null, null, specification, touchEventList)

    // Update H/W keyboard specification to keep a consistency with S/W keyboard.
    hardwareKeyboard.setCompositionMode(
      if (specification.compositionMode == CompositionMode.HIRAGANA) CompositionSwitchMode.KANA
      else CompositionSwitchMode.ALPHABET
    )
    updateKeyboardView()
  }

  private fun propagateHardwareKeyboardChange() {
    propagateHardwareKeyboardChangeAndSendKey(null)
  }

  /**
   * Propagates the change of S/W keyboard to the view layer and the H/W keyboard configuration, and
   * the send key event to Mozc server.
   */
  private fun propagateHardwareKeyboardChangeAndSendKey(event: KeyEvent?) {
    val specification = hardwareKeyboard.keyboardSpecification
    if (event == null) {
      eventListener.onKeyEvent(null, null, specification, emptyList())
    } else {
      eventListener.onKeyEvent(
        hardwareKeyboard.getMozcKeyEvent(event),
        hardwareKeyboard.getKeyEventInterface(event),
        specification,
        emptyList()
      )
    }

    // Update S/W keyboard specification to keep a consistency with H/W keyboard.
    japaneseSoftwareKeyboardModel.keyboardMode =
      if (specification.compositionMode == CompositionMode.HIRAGANA) KeyboardMode.KANA
      else KeyboardMode.ALPHABET
    updateKeyboardView()
  }

  /**
   * Set this keyboard layout to the specified one.
   *
   * @param keyboardLayout New keyboard layout.
   * @throws NullPointerException If `keyboardLayout` is `null`.
   */
  override fun setKeyboardLayout(keyboardLayout: KeyboardLayout) {
    if (japaneseSoftwareKeyboardModel.keyboardLayout !== keyboardLayout) {
      // If changed, clear the keyboard cache.
      keyboardFactory.clear()
    }
    japaneseSoftwareKeyboardModel.keyboardLayout = keyboardLayout
    propagateSoftwareKeyboardChange(emptyList())
  }

  /**
   * Set the input style.
   *
   * @param inputStyle new input style.
   * @throws NullPointerException If `inputStyle` is `null`. TODO(hidehiko): Refactor out following
   * keyboard switching logic into another class.
   */
  override fun setInputStyle(inputStyle: InputStyle) {
    if (japaneseSoftwareKeyboardModel.inputStyle !== inputStyle) {
      // If changed, clear the keyboard cache.
      keyboardFactory.clear()
    }
    japaneseSoftwareKeyboardModel.inputStyle = inputStyle
    propagateSoftwareKeyboardChange(emptyList())
  }

  override fun setQwertyLayoutForAlphabet(qwertyLayoutForAlphabet: Boolean) {
    if (japaneseSoftwareKeyboardModel.isQwertyLayoutForAlphabet != qwertyLayoutForAlphabet) {
      // If changed, clear the keyboard cache.
      keyboardFactory.clear()
    }
    japaneseSoftwareKeyboardModel.isQwertyLayoutForAlphabet = qwertyLayoutForAlphabet
    propagateSoftwareKeyboardChange(emptyList())
  }

  override fun setFullscreenMode(fullscreenMode: Boolean) {
    this.fullscreenMode = fullscreenMode
    mozcView?.setFullscreenMode(fullscreenMode)
  }

  override fun isFullscreenMode(): Boolean {
    return fullscreenMode
  }

  override fun setFlickSensitivity(flickSensitivity: Int) {
    this.flickSensitivity = flickSensitivity
    mozcView?.setFlickSensitivity(flickSensitivity)
  }

  /**
   * Updates whether Globe button should be enabled or not based on
   * `InputMethodManager#shouldOfferSwitchingToNextInputMethod(IBinder)`
   */
  override fun updateGlobeButtonEnabled() {
    globeButtonEnabled = imeSwitcher.shouldOfferSwitchingToNextInputMethod()
    mozcView?.setGlobeButtonEnabled(globeButtonEnabled)
  }

  /**
   * Updates whether Microphone button should be enabled or not based on availability of voice input
   * method.
   */
  override fun updateMicrophoneButtonEnabled() {
    mozcView?.setMicrophoneButtonEnabled(shouldVoiceImeBeEnabled())
  }

  /**
   * Sets narrow mode.
   *
   * The behavior of this method depends on API level. We decided to respect the configuration on
   * API 21 or later, so this method ignores the argument in such case. If you really want to bypass
   * the version check, please use [.setNarrowModeWithoutVersionCheck] instead.
   */
  private fun setNarrowMode() {
    setNarrowModeWithoutVersionCheck(narrowModeByConfiguration)
  }

  private fun setNarrowModeWithoutVersionCheck(newNarrowMode: Boolean) {
    if (narrowMode == newNarrowMode) {
      return
    }
    narrowMode = newNarrowMode
    if (newNarrowMode) {
      hideSubInputView()
    }
    mozcView?.setLayoutAdjustmentAndNarrowMode(layoutAdjustment, newNarrowMode)
    updateMicrophoneButtonEnabled()
    eventListener.onNarrowModeChanged(newNarrowMode)
  }

  /**
   * Returns true if we should transit to narrow mode, based on returned `Command` and
   * `KeyEventInterface` from the server.
   *
   * If all of the following conditions are satisfied, narrow mode is shown.
   *
   * * The key event is from h/w keyboard.
   * * The key event has printable character without modifier.
   */
  override fun maybeTransitToNarrowMode(command: Command, keyEventInterface: KeyEventInterface?) {
    // Surely we don't anything when on narrow mode already.
    if (narrowMode) {
      return
    }
    // Do nothing for the input from software keyboard.
    if (keyEventInterface == null || !keyEventInterface.nativeEvent.isPresent) {
      return
    }
    // Do nothing if input doesn't have a key. (e.g. pure modifier key)
    if (!command.input.hasKey()) {
      return
    }

    // Passed all the check. Transit to narrow mode.
    setNarrowMode()
  }

  override fun isNarrowMode() = narrowMode

  override fun isFloatingCandidateMode() = mozcView?.isFloatingCandidateMode ?: false

  override fun setPopupEnabled(popupEnabled: Boolean) {
    this.popupEnabled = popupEnabled
    mozcView?.setPopupEnabled(popupEnabled)
  }

  override fun switchHardwareKeyboardCompositionMode(mode: CompositionSwitchMode) {
    val oldMode = hardwareKeyboard.compositionMode
    hardwareKeyboard.setCompositionMode(mode)
    val newMode = hardwareKeyboard.compositionMode
    if (oldMode != newMode) {
      propagateHardwareKeyboardChange()
    }
  }

  override fun setHardwareKeyMap(hardwareKeyMap: HardwareKeyMap) {
    hardwareKeyboard.setHardwareKeyMap(hardwareKeyMap)
  }

  override fun setSkin(skin: Skin) {
    this.skin = skin
    mozcView?.setSkin(skin)
  }

  override fun setMicrophoneButtonEnabledByPreference(microphoneButtonEnabled: Boolean) {
    isVoiceInputEnabledByPreference = microphoneButtonEnabled
    updateMicrophoneButtonEnabled()
  }

  /**
   * Set layout adjustment and show animation if required.
   *
   * Note that this method does *NOT* update SharedPreference. If you want to update it, use
   * ViewEventListener#onUpdateKeyboardLayoutAdjustment(), which updates SharedPreference and
   * indirectly calls this method.
   */
  override fun setLayoutAdjustment(layoutAdjustment: LayoutAdjustment) {
    val mozcView = mozcView
    if (mozcView != null) {
      mozcView.setLayoutAdjustmentAndNarrowMode(layoutAdjustment, narrowMode)
      if (this.layoutAdjustment != layoutAdjustment) {
        mozcView.startLayoutAdjustmentAnimation()
      }
    }
    this.layoutAdjustment = layoutAdjustment
  }

  override fun setKeyboardHeightRatio(keyboardHeightRatio: Int) {
    this.keyboardHeightRatio = keyboardHeightRatio
    mozcView?.setKeyboardHeightRatio(keyboardHeightRatio)
  }

  /**
   * Reset the status of the current input view.
   *
   * This method must be called when the IME is turned on. Note that this method can be called
   * before [.createMozcView] so null-check is mandatory.
   */
  override fun reset() {
    mozcView?.reset()
    viewLayerKeyEventHandler.reset()

    // Reset menu dialog.
    maybeDismissMenuDialog()
  }

  override fun computeInsets(
    context: Context,
    outInsets: InputMethodService.Insets,
    window: Window
  ) {
    // The IME's area is prioritized than app's.
    // - contentTopInsets
    //   - This is the top part of the UI that is the main content.
    //   - This affects window layout.
    //       So if this value is changed, resizing the application behind happens.
    //   - This value is relative to the top edge of the input method window.
    // - visibleTopInsets
    //   - This is the top part of the UI that is visibly covering the application behind it.
    //       Changing this value will not cause resizing the application.
    //   - This is *not* to clip IME's drawing area.
    //   - This value is relative to the top edge of the input method window.
    // Thus it seems that we have to guarantee contentTopInsets <= visibleTopInsets.
    // If contentTopInsets < visibleTopInsets, the app's UI is drawn on IME's area
    // but almost all (or completely all?) application does not draw anything
    // on such "outside" area from the app's window.
    // Conclusion is that we should guarantee contentTopInsets == visibleTopInsets.
    //
    // On Honeycomb or later version, we cannot take touch events outside of IME window.
    // As its workaround, we cover the almost screen by transparent view, and we need to consider
    // the gap between the top of user visible IME window, and the transparent view's top here.
    // Note that touch events for the transparent view will be ignored by IME and automatically
    // sent to the application if it is not for the IME.
    val contentView = window.findViewById<View>(Window.ID_ANDROID_CONTENT)
    val contentViewWidth = contentView.width
    val contentViewHeight = contentView.height
    val mozcView = mozcView
    if (mozcView == null) {
      outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
      outInsets.contentTopInsets =
        (contentViewHeight - context.resources.getDimensionPixelSize(R.dimen.input_frame_height))
      outInsets.visibleTopInsets = outInsets.contentTopInsets
    } else {
      mozcView.setInsets(contentViewWidth, contentViewHeight, outInsets)
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    primaryKeyCodeConverter.setConfiguration(newConfig)
    hardwareKeyboardExist = newConfig.keyboard != Configuration.KEYBOARD_NOKEYS
    if (newConfig.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_UNDEFINED) {
      narrowModeByConfiguration =
        newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
      setNarrowMode()
    }
  }

  override fun isKeyConsumedOnViewAsynchronously(event: KeyEvent): Boolean {
    return viewLayerKeyEventHandler.evaluateKeyEvent(event)
  }

  override fun consumeKeyOnViewSynchronously(event: KeyEvent) {
    viewLayerKeyEventHandler.invoke()
  }

  override fun onHardwareKeyEvent(event: KeyEvent) {
    // Maybe update the composition mode based on the event.
    // For example, zen/han key toggles the composition mode (hiragana <--> alphabet).
    val compositionMode = hardwareKeyboard.compositionMode
    hardwareKeyboard.setCompositionModeByKey(event)
    val currentCompositionMode = hardwareKeyboard.compositionMode
    if (compositionMode != currentCompositionMode) {
      propagateHardwareKeyboardChangeAndSendKey(event)
    } else {
      eventListener.onKeyEvent(
        hardwareKeyboard.getMozcKeyEvent(event),
        hardwareKeyboard.getKeyEventInterface(event),
        hardwareKeyboard.keyboardSpecification,
        emptyList()
      )
    }
  }

  override fun isGenericMotionToConsume(event: MotionEvent): Boolean {
    return false
  }

  override fun consumeGenericMotion(event: MotionEvent): Boolean {
    return false
  }

  /**
   * Returns active (shown) JapaneseSoftwareKeyboardModel. If symbol picker is shown, symbol-number
   * keyboard's is returned.
   */
  private val activeSoftwareKeyboardModel: JapaneseSoftwareKeyboardModel
    get() =
      if (isSymbolInputViewVisible) {
        symbolNumberSoftwareKeyboardModel
      } else {
        japaneseSoftwareKeyboardModel
      }

  override fun trimMemory() {
    mozcView?.trimMemory()
  }

  override fun onStartInputView(editorInfo: EditorInfo) {
    mozcView?.onStartInputView(editorInfo)
  }

  override fun setCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
    mozcView?.setCursorAnchorInfo(cursorAnchorInfo)
  }

  override fun setCursorAnchorInfoEnabled(enabled: Boolean) {
    cursorAnchroInfoEnabled = enabled
    mozcView?.setCursorAnchorInfoEnabled(enabled)
  }

  override fun onShowSymbolInputView() {
    isSymbolInputViewVisible = true
    mozcView?.resetKeyboardViewState()
  }

  override fun onCloseSymbolInputView() {
    if (isSymbolInputViewShownByEmojiKey) {
      setNarrowMode()
    }
    isSymbolInputViewVisible = false
    isSymbolInputViewShownByEmojiKey = false
  }
}
