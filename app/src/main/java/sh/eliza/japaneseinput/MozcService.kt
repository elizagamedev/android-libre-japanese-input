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
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputBinding
import android.view.inputmethod.InputConnection
import androidx.preference.PreferenceManager
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import java.util.Locale
import java.util.Objects
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCandidates
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Context.InputFieldType
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input.TouchEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit.Segment
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Preedit.Segment.Annotation
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand.UsageStatsEvent
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config.SelectionShortcut
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config.SessionKeymap
import sh.eliza.japaneseinput.FeedbackManager.FeedbackEvent
import sh.eliza.japaneseinput.FeedbackManager.FeedbackListener
import sh.eliza.japaneseinput.KeycodeConverter.KeyEventInterface
import sh.eliza.japaneseinput.ViewManagerInterface.LayoutAdjustment
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboard.CompositionSwitchMode
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboardSpecification
import sh.eliza.japaneseinput.keyboard.Keyboard.KeyboardSpecification
import sh.eliza.japaneseinput.model.SelectionTracker
import sh.eliza.japaneseinput.model.SymbolCandidateStorage.SymbolHistoryStorage
import sh.eliza.japaneseinput.model.SymbolMajorCategory
import sh.eliza.japaneseinput.mushroom.MushroomResultProxy
import sh.eliza.japaneseinput.preference.ClientSidePreference
import sh.eliza.japaneseinput.preference.ClientSidePreference.Companion.createFromSharedPreferences
import sh.eliza.japaneseinput.preference.PreferenceUtil
import sh.eliza.japaneseinput.preference.PreferenceUtil.defaultPreferenceManagerStatic
import sh.eliza.japaneseinput.preference.PreferenceUtil.isLandscapeKeyboardSettingActive
import sh.eliza.japaneseinput.session.SessionExecutor
import sh.eliza.japaneseinput.session.SessionExecutor.EvaluationCallback
import sh.eliza.japaneseinput.session.SessionHandlerFactory
import sh.eliza.japaneseinput.util.ImeSwitcherFactory
import sh.eliza.japaneseinput.util.LauncherIconManagerFactory

/**
 * Implementation of the input method service.
 *
 * This class should NOT use [CursorAnchorInfo] for mocking.
 */
open class MozcService : InputMethodService() {
  /**
   * InputMethod implementation for MozcService. This injects the composing text tracking feature by
   * wrapping InputConnection.
   */
  inner class MozcInputMethod : InputMethodImpl() {
    override fun bindInput(binding: InputBinding) {
      super.bindInput(
        InputBinding(
          ComposingTextTrackingInputConnection.newInstance(binding.connection),
          binding.connectionToken,
          binding.uid,
          binding.pid
        )
      )
    }

    override fun startInput(inputConnection: InputConnection?, attribute: EditorInfo) {
      super.startInput(ComposingTextTrackingInputConnection.newInstance(inputConnection), attribute)
    }

    override fun restartInput(inputConnection: InputConnection?, attribute: EditorInfo) {
      super.restartInput(
        ComposingTextTrackingInputConnection.newInstance(inputConnection),
        attribute
      )
    }
  }

  private class RealFeedbackListener(private val audioManager: AudioManager) : FeedbackListener {
    override fun onVibrate(view: View, hapticFeedbackType: Int) {
      view.performHapticFeedback(hapticFeedbackType)
    }

    override fun onSound(soundEffectType: Int, volume: Float) {
      audioManager.playSoundEffect(soundEffectType, volume)
    }
  }

  /** Adapter implementation of the symbol history manipulation. */
  internal class SymbolHistoryStorageImpl( // TODO(exv): replce this
    // static final Map<SymbolMajorCategory, StorageType> STORAGE_TYPE_MAP;
    // static {
    //   Map<SymbolMajorCategory, StorageType> map =
    //       new EnumMap<SymbolMajorCategory, StorageType>(SymbolMajorCategory.class);
    //   map.put(SymbolMajorCategory.SYMBOL, StorageType.SYMBOL_HISTORY);
    //   map.put(SymbolMajorCategory.EMOTICON, StorageType.EMOTICON_HISTORY);
    //   map.put(SymbolMajorCategory.EMOJI, StorageType.EMOJI_HISTORY);
    //   STORAGE_TYPE_MAP = Collections.unmodifiableMap(map);
    // }
    private val sessionExecutor: SessionExecutor
  ) : SymbolHistoryStorage {
    override fun getAllHistory(majorCategory: SymbolMajorCategory): List<String> {
      // TODO(exv): replace this
      // List<ByteString> historyList =
      //     sessionExecutor.readAllFromStorage(STORAGE_TYPE_MAP.get(majorCategory));
      // List<String> result = new ArrayList<String>(historyList.size());
      // for (ByteString value : historyList) {
      //   result.add(MozcUtil.utf8CStyleByteStringToString(value));
      // }
      return emptyList()
    }

    override fun addHistory(majorCategory: SymbolMajorCategory, value: String) {
      Preconditions.checkNotNull(majorCategory)
      Preconditions.checkNotNull(value)
      // TODO(exv): replace this
      // sessionExecutor.insertToStorage(
      //     STORAGE_TYPE_MAP.get(majorCategory),
      //     value,
      //     Collections.singletonList(ByteString.copyFromUtf8(value)));
    }
  }

  // Called back from ViewManager
  private inner class MozcEventListener : ViewEventListener {
    override fun onConversionCandidateSelected(
      view: View,
      candidateId: Int,
      rowIndex: Optional<Int>
    ) {
      sessionExecutor.submitCandidate(candidateId, rowIndex, renderResultCallback)
      feedbackManager.fireFeedback(view, FeedbackEvent.CANDIDATE_SELECTED)
    }

    override fun onPageUp(view: View) {
      sessionExecutor.pageUp(renderResultCallback)
      feedbackManager.fireFeedback(view, FeedbackEvent.KEY_DOWN)
    }

    override fun onPageDown(view: View) {
      sessionExecutor.pageDown(renderResultCallback)
      feedbackManager.fireFeedback(view, FeedbackEvent.KEY_DOWN)
    }

    override fun onSymbolCandidateSelected(
      view: View,
      majorCategory: SymbolMajorCategory,
      candidate: String,
      updateHistory: Boolean
    ) {
      // Directly commit the text.
      commitText(candidate)
      if (updateHistory) {
        symbolHistoryStorage.addHistory(majorCategory, candidate)
      }
      feedbackManager.fireFeedback(view, FeedbackEvent.CANDIDATE_SELECTED)
    }

    private fun commitText(text: String) {
      val inputConnection = currentInputConnection ?: return
      inputConnection.beginBatchEdit()
      try {
        inputConnection.commitText(text, MozcUtil.CURSOR_POSITION_TAIL)
      } finally {
        inputConnection.endBatchEdit()
      }
    }

    override fun onKeyEvent(
      mozcKeyEvent: ProtoCommands.KeyEvent?,
      keyEvent: KeyEventInterface?,
      keyboardSpecification: KeyboardSpecification?,
      touchEventList: List<ProtoCommands.Input.TouchEvent>
    ) {
      if (mozcKeyEvent == null && keyboardSpecification == null) {
        // We don't send a key event to Mozc native layer since {@code mozcKeyEvent} is null, and we
        // don't need to update the keyboard specification since {@code keyboardSpecification} is
        // also null.
        if (keyEvent == null) {
          // Send a usage information to Mozc native layer.
          sessionExecutor.touchEventUsageStatsEvent(touchEventList)
        } else {
          // Send a key event (which is generated by Mozc in the usual case) to application.
          require(touchEventList.isEmpty())
          sessionExecutor.sendKeyEvent(keyEvent, sendKeyToApplicationCallback)
        }
        return
      }
      sendKeyWithKeyboardSpecification(
        mozcKeyEvent,
        keyEvent,
        keyboardSpecification,
        resources.configuration,
        touchEventList
      )
    }

    override fun onUndo(touchEventList: List<ProtoCommands.Input.TouchEvent>) {
      sessionExecutor.undoOrRewind(touchEventList, renderResultCallback)
    }

    override fun onFireFeedbackEvent(view: View, event: FeedbackEvent) {
      feedbackManager.fireFeedback(view, event)
      if (event == FeedbackEvent.INPUTVIEW_EXPAND) {
        sessionExecutor.sendUsageStatsEvent(UsageStatsEvent.KEYBOARD_EXPAND_EVENT)
      } else if (event == FeedbackEvent.INPUTVIEW_FOLD) {
        sessionExecutor.sendUsageStatsEvent(UsageStatsEvent.KEYBOARD_FOLD_EVENT)
      }
    }

    override fun onSubmitPreedit() {
      sessionExecutor.submit(renderResultCallback)
    }

    override fun onExpandSuggestion() {
      // TODO(exv): replace this
      // sessionExecutor.expandSuggestion(renderResultCallback);
    }

    override fun onShowMenuDialog(touchEventList: List<ProtoCommands.Input.TouchEvent?>?) {
      sessionExecutor.touchEventUsageStatsEvent(touchEventList)
    }

    override fun onShowSymbolInputView(touchEventList: List<ProtoCommands.Input.TouchEvent?>?) {
      changeKeyboardSpecificationAndSendKey(
        null,
        null,
        KeyboardSpecification.SYMBOL_NUMBER,
        resources.configuration,
        emptyList<ProtoCommands.Input.TouchEvent>()
      )
      viewManager.onShowSymbolInputView()
    }

    override fun onCloseSymbolInputView() {
      viewManager.onCloseSymbolInputView()
      // This callback is called in two ways: one is from touch event on symbol input view.
      // The other is from onKeyDown event by hardware keyboard.  ViewManager.isNarrowMode()
      // is abused to distinguish these two triggers where its true value indicates that
      // onCloseSymbolInputView() is called on hardware keyboard event.  In the case of hardware
      // keyboard event, keyboard specification has been already updated so we shouldn't update it.
      if (!viewManager.isNarrowMode) {
        changeKeyboardSpecificationAndSendKey(
          null,
          null,
          viewManager.keyboardSpecification,
          resources.configuration,
          emptyList<ProtoCommands.Input.TouchEvent>()
        )
      }
    }

    override fun onHardwareKeyboardCompositionModeChange(mode: CompositionSwitchMode?) {
      viewManager.switchHardwareKeyboardCompositionMode(mode)
    }

    override fun onActionKey() {
      // false means that the key is for Action and not ENTER.
      sendEditorAction(false)
    }

    override fun onNarrowModeChanged(newNarrowMode: Boolean) {
      if (!newNarrowMode) {
        // Hardware keyboard to software keyboard transition: Submit composition.
        sessionExecutor.submit(renderResultCallback)
      }
      updateImposedConfig()
    }

    override fun onUpdateKeyboardLayoutAdjustment(layoutAdjustment: LayoutAdjustment) {
      val configuration = resources.configuration
      if (configuration == null) {
        return
      }
      val isLandscapeKeyboardSettingActive =
        isLandscapeKeyboardSettingActive(sharedPreferences, configuration.orientation)
      val key =
        if (isLandscapeKeyboardSettingActive) {
          PreferenceUtil.PREF_LANDSCAPE_LAYOUT_ADJUSTMENT_KEY
        } else {
          PreferenceUtil.PREF_PORTRAIT_LAYOUT_ADJUSTMENT_KEY
        }
      sharedPreferences.edit().putString(key, layoutAdjustment.toString()).apply()
    }

    override fun onShowMushroomSelectionDialog() {
      sessionExecutor.sendUsageStatsEvent(UsageStatsEvent.MUSHROOM_SELECTION_DIALOG_OPEN_EVENT)
    }
  }

  /** Callback to render the result received from Mozc server. */
  private inner class RenderResultCallback : EvaluationCallback {
    override fun onCompleted(
      command: Optional<ProtoCommands.Command>,
      triggeringKeyEvent: Optional<KeyEventInterface>
    ) {
      require(Preconditions.checkNotNull(command).isPresent)
      Preconditions.checkNotNull(triggeringKeyEvent)
      // TODO(exv): fix this expand suggestion usage
      // if (command.get().getInput().getCommand().getType()
      //     != SessionCommand.CommandType.EXPAND_SUGGESTION) {
      // For expanding suggestions, we don't need to update our rendering result.
      renderInputConnection(command.get(), triggeringKeyEvent.orNull())
      // }
      // Transit to narrow mode if required (e.g., Typed 'a' key from h/w keyboard).
      viewManager.maybeTransitToNarrowMode(command.get(), triggeringKeyEvent.orNull())
      viewManager.render(command.get())
    }
  }

  /** Callback to send key event to a application. */
  private inner class SendKeyToApplicationCallback : EvaluationCallback {
    override fun onCompleted(
      command: Optional<ProtoCommands.Command>,
      triggeringKeyEvent: Optional<KeyEventInterface>
    ) {
      require(!Preconditions.checkNotNull(command).isPresent)
      sendKeyEvent(triggeringKeyEvent.orNull())
    }
  }

  /** Callback to send key event to view layer. */
  private inner class SendKeyToViewCallback : EvaluationCallback {
    override fun onCompleted(
      command: Optional<ProtoCommands.Command>,
      triggeringKeyEvent: Optional<KeyEventInterface>
    ) {
      require(!Preconditions.checkNotNull(command).isPresent)
      require(Preconditions.checkNotNull(triggeringKeyEvent).isPresent)
      viewManager.consumeKeyOnViewSynchronously(triggeringKeyEvent.get().nativeEvent.orNull())
    }
  }

  /**
   * Callback to invoke onUpdateSelectionInternal with delay for onConfigurationChanged. See
   * onConfigurationChanged for the details.
   */
  private inner class ConfigurationChangeCallback : Handler.Callback {
    override fun handleMessage(msg: Message): Boolean {
      val selectionStart = msg.arg1
      val selectionEnd = msg.arg2
      onUpdateSelectionInternal(selectionStart, selectionEnd, selectionStart, selectionEnd, -1, -1)
      return true
    }
  }

  /** We need to send SYNC_DATA command periodically. This class handles it. */
  @SuppressLint("HandlerLeak")
  private inner class SendSyncDataCommandHandler : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
      sessionExecutor.syncData()
      sendEmptyMessageDelayed(0, SYNC_DATA_COMMAND_PERIOD)
    }
  }

  /**
   * To trim memory, a message is handled to invoke trimMemory method 10 seconds after hiding
   * window.
   *
   * This class handles callback operation. Posting and removing messages should be done in
   * appropriate point.
   */
  @SuppressLint("HandlerLeak")
  private inner class MemoryTrimmingHandler : Handler(Looper.getMainLooper()) {
    override fun handleMessage(msg: Message) {
      trimMemory()
      // Other messages in the queue are removed as they will do the same thing
      // and will affect nothing.
      removeMessages(WHAT)
    }
  }

  // Mozc's session. All session related task should be done via this instance.
  private lateinit var sessionExecutor: SessionExecutor
  private val renderResultCallback = RenderResultCallback()
  private val sendKeyToApplicationCallback = SendKeyToApplicationCallback()
  private val sendKeyToViewCallback = SendKeyToViewCallback()

  // A manager for all views and feedbacks.
  private lateinit var viewManager: ViewManagerInterface
  private lateinit var feedbackManager: FeedbackManager
  private lateinit var symbolHistoryStorage: SymbolHistoryStorage
  private lateinit var sharedPreferences: SharedPreferences

  // A handler for onSharedPreferenceChanged().
  // Note: the handler is needed to be held by the service not to be GC'ed.
  private val sharedPreferenceChangeListener: OnSharedPreferenceChangeListener =
    SharedPreferenceChangeAdapter()

  // Preference information which are propagated. Null if not propagated yet.
  private var propagatedClientSidePreference: ClientSidePreference? = null

  // Track the selection.
  private val selectionTracker = SelectionTracker()

  // A receiver to accept a notification via intents.
  private lateinit var configurationChangedHandler: Handler

  // Handler to process SYNC_DATA command for storing history data.
  private lateinit var sendSyncDataCommandHandler: SendSyncDataCommandHandler

  // Handler to process SYNC_DATA command for storing history data.
  private lateinit var memoryTrimmingHandler: MemoryTrimmingHandler

  // Current KeyboardSpecification, which is determined by the last key event.
  // Note that this might be different from what a user sees.
  // For example when a user is in narrow mode (this field is for H/W keyboard)
  // and (s)he taps widen button to see S/W keyboard,
  // (s)he will see S/W keyboard but this field keep to point H/W keyboard because
  // widen button is not a key event.
  // This behavior is error-prone and might be a kind of bug. At least the name doesn't represent
  // the behavior.
  // TODO(matsuzakit): Clarify the usage of this field (change the behavior to keep the latest
  //   state or change the name to represent current behavior).
  private var currentKeyboardSpecification = KeyboardSpecification.TWELVE_KEY_TOGGLE_KANA
  private var inputBound = false
  private var originalWindowAnimationResourceId: Int? = null
  private var applicationCompatibility = ApplicationCompatibility.getDefaultInstance()

  // Listener called by views.
  // Held for testing.
  private lateinit var viewEventListener: ViewEventListener

  override fun onBindInput() {
    super.onBindInput()
    inputBound = true
  }

  override fun onUnbindInput() {
    inputBound = false
    super.onUnbindInput()
  }

  override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
    viewManager.setCursorAnchorInfo(cursorAnchorInfo)
  }

  override fun onCreateInputMethodInterface(): MozcInputMethod {
    return MozcInputMethod()
  }

  override fun onCreate() {
    super.onCreate()
    MozcLog.d("start MozcService#onCreate " + System.nanoTime())

    // TODO(hidehiko): Restructure around initialization code in order to make tests stable.
    // Callback object mainly used by views.
    viewEventListener = MozcEventListener()
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
    sessionExecutor =
      SessionExecutor.getInstanceInitializedIfNecessary(
        SessionHandlerFactory(Optional.of(sharedPreferences)),
        this
      )
    symbolHistoryStorage = SymbolHistoryStorageImpl(sessionExecutor)

    val context = applicationContext
    ApplicationInitializerFactory.createInstance(this)
      .initialize(
        MozcUtil.getAbiIndependentVersionCode(context),
        LauncherIconManagerFactory.getDefaultInstance(),
        defaultPreferenceManagerStatic
      )

    // Create a ViewManager.
    val imeSwitcher = ImeSwitcherFactory.getImeSwitcher(this)
    viewManager =
      ViewManager(
        applicationContext,
        viewEventListener,
        symbolHistoryStorage,
        imeSwitcher,
        MozcMenuDialogListenerImpl(this, viewEventListener)
      )

    // Setup FeedbackManager.
    feedbackManager =
      FeedbackManager(RealFeedbackListener(getSystemService(AUDIO_SERVICE) as AudioManager))

    // Set a callback for preference changing.
    sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

    prepareEveryTime(sharedPreferences, resources.configuration)

    val propagatedClientSidePreference = propagatedClientSidePreference
    if (propagatedClientSidePreference == null ||
        propagatedClientSidePreference.hardwareKeyMap == null
    ) {
      HardwareKeyboardSpecification.maybeSetDetectedHardwareKeyMap(
        sharedPreferences,
        resources.configuration,
        false
      )
    }

    configurationChangedHandler = Handler(Looper.getMainLooper(), ConfigurationChangeCallback())
    memoryTrimmingHandler = MemoryTrimmingHandler()

    // Start sending SYNC_DATA message to mozc server periodically.
    sendSyncDataCommandHandler = SendSyncDataCommandHandler()
    sendSyncDataCommandHandler.sendEmptyMessageDelayed(WHAT, SYNC_DATA_COMMAND_PERIOD)
    MozcLog.d("end MozcService#onCreate " + System.nanoTime())
  }

  override fun onDestroy() {
    feedbackManager.release()
    sessionExecutor.syncData()

    // Following listeners/handlers have reference to the service.
    // To free the service instance, remove the listeners/handlers.
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    sendSyncDataCommandHandler.removeMessages(WHAT)
    memoryTrimmingHandler.removeMessages(WHAT)
    super.onDestroy()
  }

  /** Prepares something which should be done every time when the session is newly created. */
  private fun prepareEveryTime(
    sharedPreferences: SharedPreferences?,
    deviceConfiguration: Configuration
  ) {
    val isLogging =
      (sharedPreferences != null &&
        sharedPreferences.getBoolean(PREF_TWEAK_LOGGING_PROTOCOL_BUFFERS, false))
    // Force to initialize here.
    sessionExecutor.reset(SessionHandlerFactory(Optional.fromNullable(sharedPreferences)), this)
    sessionExecutor.setLogging(isLogging)
    updateImposedConfig()
    viewManager.onConfigurationChanged(resources.configuration)
    // Make sure that the server and the client have the same keyboard specification.
    // User preference's keyboard will be set after this step.
    changeKeyboardSpecificationAndSendKey(
      null,
      null,
      currentKeyboardSpecification,
      deviceConfiguration,
      emptyList<ProtoCommands.Input.TouchEvent>()
    )
    if (sharedPreferences != null) {
      propagateClientSidePreference(
        createFromSharedPreferences(sharedPreferences, resources, deviceConfiguration.orientation)
      )
      // TODO(hidehiko): here we just set the config based on preferences. When we start
      //   to support sync on Android, we need to revisit the config related design.
      sessionExecutor.config = ConfigUtil.toConfig(sharedPreferences)
      sessionExecutor.preferenceUsageStatsEvent(sharedPreferences, resources)
    }
  }

  override fun onEvaluateInputViewShown(): Boolean {
    super.onEvaluateInputViewShown()
    // TODO(matsuzakit): Implement me
    return true
  }

  override fun onCreateInputView(): View {
    MozcLog.d("start MozcService#onCreateInputView " + System.nanoTime())
    val inputView = viewManager.createMozcView(this)
    MozcLog.d("end MozcService#onCreateInputView " + System.nanoTime())
    return inputView
  }

  private fun resetContext() {
    sessionExecutor.resetContext()
    viewManager.reset()
  }

  private fun resetWindowAnimation() {
    val resourceId = originalWindowAnimationResourceId
    if (resourceId != null) {
      val window = window.window!!
      window.setWindowAnimations(resourceId)
      originalWindowAnimationResourceId = null
    }
  }

  override fun onFinishInput() {
    // Omit rendering because the input view will soon disappear.
    resetContext()
    selectionTracker.onFinishInput()
    applicationCompatibility = ApplicationCompatibility.getDefaultInstance()
    resetWindowAnimation()
    super.onFinishInput()
  }

  override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
    super.onStartInput(attribute, restarting)
    applicationCompatibility = ApplicationCompatibility.getInstance(attribute)

    // Update full screen mode, because the application may be changed.
    val propagatedClientSidePreference = propagatedClientSidePreference
    viewManager.isFullscreenMode =
      applicationCompatibility.isFullScreenModeSupported &&
        propagatedClientSidePreference != null &&
        propagatedClientSidePreference.isFullscreenMode

    // Some applications, e.g. gmail or maps, send onStartInput with restarting = true, when a user
    // rotates a device. In such cases, we don't want to update caret positions, nor reset
    // the context basically. However, some other applications, such as one with a webview widget
    // like a browser, send onStartInput with restarting = true, too. Unfortunately,
    // there seems no way to figure out which one causes this invocation.
    // So, as a point of compromise, we reset the context every time here. Also, we'll send
    // finishComposingText as well, in case the new attached field has already had composing text
    // (we hit such a situation on webview, too).
    // See also onConfigurationChanged for caret position handling on gmail-like applications'
    // device rotation events.
    resetContext()
    val connection = currentInputConnection
    if (connection != null) {
      connection.finishComposingText()
      maybeCommitMushroomResult(attribute, connection)
    }

    // Send the connected field's attributes to the mozc server.
    sessionExecutor.switchInputFieldType(getInputFieldType(attribute))
    // TODO(exv): replace this
    // sessionExecutor.updateRequest(
    //     EmojiUtil.createEmojiRequest(
    //         Build.VERSION.SDK_INT,
    //         (propagatedClientSidePreference != null &&
    // EmojiUtil.isCarrierEmojiAllowed(attribute))
    //             ? propagatedClientSidePreference.getEmojiProviderType() :
    // EmojiProviderType.NONE),
    //     Collections.<TouchEvent>emptyList());
    selectionTracker.onStartInput(
      attribute.initialSelStart,
      attribute.initialSelEnd,
      isWebEditText(attribute)
    )
  }

  /** @return true if connected view is WebEditText (or the application pretends it) */
  private fun isWebEditText(editorInfo: EditorInfo?): Boolean {
    if (editorInfo == null) {
      return false
    }
    if (applicationCompatibility.isPretendingWebEditText) {
      return true
    }

    // TODO(hidehiko): Refine the heuristic to check isWebEditText related stuff.
    MozcLog.d("inputType: " + editorInfo.inputType)
    val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
    return variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
  }

  override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
    val inputConnection = currentInputConnection
    if (inputConnection != null) {
      viewManager.setCursorAnchorInfoEnabled(enableCursorAnchorInfo(inputConnection))
      updateImposedConfig()
    }
    viewManager.onStartInputView(attribute)
    viewManager.setTextForActionButton(getTextForImeAction(attribute.imeOptions))
    viewManager.setEditorInfo(attribute)
    // updateXxxxxButtonEnabled cannot be placed in onStartInput because
    // the view might be created after onStartInput with *reset* status.
    viewManager.updateGlobeButtonEnabled()
    viewManager.updateMicrophoneButtonEnabled()

    // Should reset the window animation since the order of onStartInputView() / onFinishInput() is
    // not stable.
    resetWindowAnimation()
    // Mode indicator is available and narrow frame is NOT available on Lollipop or later.
    // In this case, we temporary disable window animation to show the mode indicator correctly.
    if (viewManager.isNarrowMode) {
      val window = window.window!!
      val animationId = window.attributes.windowAnimations
      if (animationId != 0) {
        originalWindowAnimationResourceId = animationId
        window.setWindowAnimations(0)
      }
    }
  }

  override fun onComputeInsets(outInsets: Insets) {
    viewManager.computeInsets(applicationContext, outInsets, window.window)
  }

  /**
   * KeyDown event handler.
   *
   * This method is called only by the android framework e.g HOME,BACK or H/W keyboard input.
   */
  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    return onKeyDownInternal(keyCode, event, resources.configuration)
  }

  /**
   * GenericMotionEvent handler.
   *
   * This method is called only by the android framework e.g H/W mouse, touch pad input, etc.
   */
  override fun onGenericMotionEvent(event: MotionEvent): Boolean {
    if (!isInputViewShown) {
      return super.onGenericMotionEvent(event)
    }
    return if (viewManager.isGenericMotionToConsume(event)) {
      viewManager.consumeGenericMotion(event)
    } else super.onGenericMotionEvent(event)
  }

  private fun onKeyDownInternal(
    keyCode: Int,
    event: KeyEvent,
    configuration: Configuration
  ): Boolean {
    if (MozcLog.isLoggable(Log.DEBUG)) {
      MozcLog.d(
        String.format(
          Locale.US,
          "onKeyDown keyCode:0x%x, metaState:0x%x, scanCode:0x%x, uniCode:0x%x, deviceId:%d",
          event.keyCode,
          event.metaState,
          event.scanCode,
          event.unicodeChar,
          event.deviceId
        )
      )
    }

    // Send back the event if the input view is not shown.
    if (!isInputViewShown) {
      return super.onKeyDown(keyCode, event)
    }

    // Send back the event if the event is one of the system keys, which invoke
    // system's action. (e.g. back, home, power, volume and so on).
    if (event.isSystem) {
      // Special handle for back key. We need to post it to the server and maybeProcessBackKey
      // should handle it later. The posting the event is done in onKeyUp, so we just consume the
      // down key event here.
      return if (keyCode == KeyEvent.KEYCODE_BACK) {
        true
      } else super.onKeyDown(keyCode, event)
    }

    // Push the event to the asynchronous execution queue if it should be processed
    // directly in the view.
    if (viewManager.isKeyConsumedOnViewAsynchronously(event)) {
      sessionExecutor.sendKeyEvent(
        KeycodeConverter.getKeyEventInterface(event),
        sendKeyToViewCallback
      )
      return true
    }

    // Lazy evaluation.
    // If hardware keyboard is not set in the preference screen,
    // set it based on the configuration.
    val propagatedClientSidePreference = propagatedClientSidePreference
    if (propagatedClientSidePreference == null ||
        propagatedClientSidePreference.hardwareKeyMap == null
    ) {
      HardwareKeyboardSpecification.maybeSetDetectedHardwareKeyMap(
        sharedPreferences,
        configuration,
        true
      )
    }

    // Here we decided to send the event to the server.
    viewManager.onHardwareKeyEvent(event)
    return true
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    if (isInputViewShown) {
      if (event.isSystem) {
        // The back key should be processed as same as the meta keys.
        // See also comments described below.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
          sessionExecutor.sendKeyEvent(
            KeycodeConverter.getKeyEventInterface(event),
            sendKeyToApplicationCallback
          )
          return true
        }
      } else {
        if (viewManager.isKeyConsumedOnViewAsynchronously(event)) {
          sessionExecutor.sendKeyEvent(
            KeycodeConverter.getKeyEventInterface(event),
            sendKeyToViewCallback
          )
          return true
        }

        // The IME is active and the server can handle the event so consume the event.
        // Currently the server does not consume UP event for not meta keys.
        // For meta keys, to guarantee that this up event is sent to InputConnection after down key,
        // this is sent to evaluation handler.
        if (KeycodeConverter.isMetaKey(event)) {
          sessionExecutor.sendKeyEvent(
            KeycodeConverter.getKeyEventInterface(event),
            sendKeyToApplicationCallback
          )
        }
        return true
      }
    }

    // If the IME is turned off or the event should not be sent to the server,
    // delegate to the super class.
    // Note that delegation should be done only when needed.
    // For example hardware keyboard's enter key should not be delegated
    // because its DOWN event is sent to the sever.
    // If delegated, enter key event is sent to the application twice (DOWN and UP).
    return super.onKeyUp(keyCode, event)
  }

  /**
   * Sends mozcKeyEvent and/or Request to mozc server.
   *
   * This skips to send request if the given keyboard specification is same as before.
   */
  private fun sendKeyWithKeyboardSpecification(
    mozcKeyEvent: ProtoCommands.KeyEvent?,
    event: KeyEventInterface?,
    keyboardSpecification: KeyboardSpecification?,
    configuration: Configuration,
    touchEventList: List<ProtoCommands.Input.TouchEvent?>?
  ) {
    if (keyboardSpecification != null && currentKeyboardSpecification != keyboardSpecification) {
      // Submit composition on the transition from software KB to hardware KB by key event.
      // This is done only when mozcKeyEvent is non-null (== the key event is a printable
      // character) in order to avoid clearing pre-selected characters by meta keys.
      if (!currentKeyboardSpecification.isHardwareKeyboard &&
          keyboardSpecification.isHardwareKeyboard &&
          mozcKeyEvent != null
      ) {
        sessionExecutor.submit(renderResultCallback)
      }
      changeKeyboardSpecificationAndSendKey(
        mozcKeyEvent,
        event,
        keyboardSpecification,
        configuration,
        touchEventList
      )
      updateStatusIcon()
    } else if (mozcKeyEvent != null) {
      // Send mozcKeyEvent as usual.
      sessionExecutor.sendKey(mozcKeyEvent, event, touchEventList, renderResultCallback)
    } else if (event != null) {
      // Send event back to the application to handle key events which cannot be converted into Mozc
      // key event (e.g. Shift) correctly.
      sessionExecutor.sendKeyEvent(event, sendKeyToApplicationCallback)
    }
  }

  /** Sends Request for changing keyboard setting to mozc server and sends key. */
  private fun changeKeyboardSpecificationAndSendKey(
    mozcKeyEvent: ProtoCommands.KeyEvent?,
    event: KeyEventInterface?,
    keyboardSpecification: KeyboardSpecification,
    configuration: Configuration,
    touchEventList: List<ProtoCommands.Input.TouchEvent?>?
  ) {
    // Send Request to change composition table.
    sessionExecutor.updateRequest(
      MozcUtil.getRequestBuilder(resources, keyboardSpecification, configuration).build(),
      touchEventList
    )
    if (mozcKeyEvent == null) {
      // Change composition mode.
      sessionExecutor.switchInputMode(
        Optional.fromNullable(event),
        keyboardSpecification.compositionMode,
        renderResultCallback
      )
    } else {
      // Send key with composition mode change.
      sessionExecutor.sendKey(
        ProtoCommands.KeyEvent.newBuilder(mozcKeyEvent)
          .setMode(keyboardSpecification.compositionMode)
          .build(),
        event,
        touchEventList,
        renderResultCallback
      )
    }
    currentKeyboardSpecification = keyboardSpecification
  }

  /** Shows/Hides status icon according to the input view status. */
  private fun updateStatusIcon() {
    if (isInputViewShown) {
      showStatusIcon()
    } else {
      hideStatusIcon()
    }
  }

  /** Shows the status icon basing on the current keyboard spec. */
  private fun showStatusIcon() {
    if (Objects.requireNonNull(currentKeyboardSpecification.compositionMode) ==
        ProtoCommands.CompositionMode.HIRAGANA
    ) {
      showStatusIcon(R.drawable.status_icon_hiragana)
    } else {
      showStatusIcon(R.drawable.status_icon_alphabet)
    }
  }

  override fun onEvaluateFullscreenMode(): Boolean {
    return viewManager.isFullscreenMode
  }

  override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
    val result = super.onShowInputRequested(flags, configChange)
    val isHardwareKeyboardConnected =
      resources.configuration.keyboard != Configuration.KEYBOARD_NOKEYS
    // Original result becomes false when a hardware keyboard is connected.
    // This means that the window won't be shown in such situation.
    // We want to show it even with a hardware keyboard so override the result here.
    return result || isHardwareKeyboardConnected
  }

  override fun onWindowShown() {
    showStatusIcon()
    // Remove memory trimming message.
    memoryTrimmingHandler.removeMessages(WHAT)
    // Ensure keyboard's request.
    // The session might be deleted by trimMemory caused by onWindowHidden.
    // Note that this logic must be placed *after* removing the messages in memoryTrimmingHandler.
    // Otherwise the session might be unexpectedly deleted and newly re-created one will be used
    // without appropriate request which is sent below.
    changeKeyboardSpecificationAndSendKey(
      null,
      null,
      currentKeyboardSpecification,
      resources.configuration,
      emptyList<ProtoCommands.Input.TouchEvent>()
    )
  }

  override fun onWindowHidden() {
    // "Hiding IME's window" is very similar to "Turning off IME" for PC.
    // Thus
    // - Committing composing text.
    // - Removing all pending messages.
    // - Resetting Mozc server
    // are needed.
    sessionExecutor.removePendingEvaluations()
    resetContext()
    selectionTracker.onWindowHidden()
    viewManager.reset()
    hideStatusIcon()
    // MemoryTrimmingHandler.DURATION_MS from now, memory trimming will be done.
    // If the window is shown before MemoryTrimmingHandler.DURATION_MS,
    // the message posted here will be removed.
    memoryTrimmingHandler.removeMessages(WHAT)
    memoryTrimmingHandler.sendEmptyMessageDelayed(WHAT, DURATION_MS)
    super.onWindowHidden()
  }

  /**
   * Updates InputConnection.
   *
   * @param command Output message. Rendering is based on this parameter.
   * @param keyEvent Trigger event for this calling. When direct input is needed, this event is sent
   * to InputConnection.
   */
  private fun renderInputConnection(command: ProtoCommands.Command, keyEvent: KeyEventInterface?) {
    Preconditions.checkNotNull(command)
    val inputConnection = currentInputConnection ?: return
    val output = command.output
    if (!output.hasConsumed() || !output.consumed) {
      maybeCommitText(output, inputConnection)
      sendKeyEvent(keyEvent)
      return
    }

    // Meta key may invoke a command for Mozc server like SWITCH_INPUT_MODE session command. In this
    // case, the command is consumed by Mozc server and the application cannot get the key event.
    // To avoid such situation, we should send the key event back to application. b/13238551
    // The command itself is consumed by Mozc server, so we should NOT put a return statement here.
    if (keyEvent != null &&
        keyEvent.nativeEvent.isPresent &&
        KeycodeConverter.isMetaKey(keyEvent.nativeEvent.get())
    ) {
      sendKeyEvent(keyEvent)
    }

    // Here the key is consumed by the Mozc server.
    inputConnection.beginBatchEdit()
    try {
      maybeDeleteSurroundingText(output, inputConnection)
      maybeCommitText(output, inputConnection)
      setComposingText(command, inputConnection)
      maybeSetSelection(output, inputConnection)
      selectionTracker.onRender(
        if (output.hasDeletionRange()) output.deletionRange else null,
        if (output.hasResult()) output.result.value else null,
        if (output.hasPreedit()) output.preedit else null
      )
    } finally {
      inputConnection.endBatchEdit()
    }
  }

  /** Sends the `KeyEvent`, which is not consumed by the mozc server. */
  private fun sendKeyEvent(keyEvent: KeyEventInterface?) {
    if (keyEvent == null) {
      return
    }
    val keyCode = keyEvent.keyCode
    // Some keys have a potential to be consumed from mozc client.
    if (maybeProcessBackKey(keyCode) || maybeProcessActionKey(keyCode)) {
      // The key event is consumed.
      return
    }

    // Following code is to fallback to target activity.
    val nativeKeyEvent = keyEvent.nativeEvent
    val inputConnection = currentInputConnection
    if (nativeKeyEvent.isPresent && inputConnection != null) {
      // Meta keys are from this.onKeyDown/Up so fallback each time.
      if (KeycodeConverter.isMetaKey(nativeKeyEvent.get())) {
        inputConnection.sendKeyEvent(
          createKeyEvent(
            nativeKeyEvent.get(),
            MozcUtil.getUptimeMillis(),
            nativeKeyEvent.get().action,
            nativeKeyEvent.get().repeatCount
          )
        )
        return
      }

      // Other keys are from this.onKeyDown so create dummy Down/Up events.
      inputConnection.sendKeyEvent(
        createKeyEvent(nativeKeyEvent.get(), MozcUtil.getUptimeMillis(), KeyEvent.ACTION_DOWN, 0)
      )
      inputConnection.sendKeyEvent(
        createKeyEvent(nativeKeyEvent.get(), MozcUtil.getUptimeMillis(), KeyEvent.ACTION_UP, 0)
      )
      return
    }

    // Otherwise, just delegates the key event to the connected application.
    // However space key needs special treatment because it is expected to produce space character
    // instead of sending ACTION_DOWN/UP pair.
    if (keyCode == KeyEvent.KEYCODE_SPACE) {
      inputConnection.commitText(" ", 0)
    } else {
      sendDownUpKeyEvents(keyCode)
    }
  }

  /** @return true if the key event is consumed */
  private fun maybeProcessBackKey(keyCode: Int): Boolean {
    if (keyCode != KeyEvent.KEYCODE_BACK || !isInputViewShown) {
      return false
    }

    // Special handling for back key event, to close the software keyboard or its subview.
    // First, try to hide the subview, such as the symbol input view or the cursor view.
    // If neither is shown, hideSubInputView would fail, then hide the whole software keyboard.
    if (!viewManager.hideSubInputView()) {
      requestHideSelf(0)
    }
    return true
  }

  private fun maybeProcessActionKey(keyCode: Int): Boolean {
    // Handle the event iff the enter is pressed.
    return if (keyCode != KeyEvent.KEYCODE_ENTER || !isInputViewShown) {
      false
    } else sendEditorAction(true)
  }

  /**
   * Sends editor action to `InputConnection`.
   *
   * The difference from [InputMethodService.sendDefaultEditorAction] is that if custom action label
   * is specified `EditorInfo#actionId` is sent instead.
   */
  private fun sendEditorAction(fromEnterKey: Boolean): Boolean {
    // If custom action label is specified (=non-null), special action id is also specified.
    // If there is no IME_FLAG_NO_ENTER_ACTION option, we should send the id to the InputConnection.
    val editorInfo = currentInputEditorInfo
    if (editorInfo != null &&
        editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0 &&
        editorInfo.actionLabel != null
    ) {
      val inputConnection = currentInputConnection
      if (inputConnection != null) {
        inputConnection.performEditorAction(editorInfo.actionId)
        return true
      }
    }
    // No custom action label is specified. Fall back to default EditorAction.
    return sendDefaultEditorAction(fromEnterKey)
  }

  private fun setComposingText(command: ProtoCommands.Command, inputConnection: InputConnection) {
    Preconditions.checkNotNull(command)
    Preconditions.checkNotNull(inputConnection)
    val output = command.output
    if (!output.hasPreedit()) {
      // If preedit field is empty, we should clear composing text in the InputConnection
      // because Mozc server asks us to do so.
      // But there is special situation in Android.
      // On onWindowShown, SWITCH_INPUT_MODE command is sent as a step of initialization.
      // In this case we reach here with empty preedit.
      // As described above we should clear the composing text but if we do so
      // texts in selection range (e.g., URL in OmniBox) is always cleared.
      // To avoid from this issue, we don't clear the composing text if the input
      // is SWITCH_INPUT_MODE.
      val input = command.input
      if (input.type != ProtoCommands.Input.CommandType.SEND_COMMAND ||
          input.command.type != SessionCommand.CommandType.SWITCH_INPUT_MODE
      ) {
        if (!inputConnection.setComposingText("", 0)) {
          MozcLog.e("Failed to set composing text.")
        }
      }
      return
    }

    // Builds preedit expression.
    val preedit = output.preedit
    val builder = SpannableStringBuilder()
    for (segment in preedit.segmentList) {
      builder.append(segment.value)
    }

    // Set underline for all the preedit text.
    builder.setSpan(SPAN_UNDERLINE, 0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

    // Draw cursor if in composition mode.
    val cursor = preedit.cursor
    val spanFlags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING
    if (output.hasAllCandidateWords() &&
        output.allCandidateWords.hasCategory() &&
        output.allCandidateWords.category == ProtoCandidates.Category.CONVERSION
    ) {
      var offsetInString = 0
      for (segment in preedit.segmentList) {
        val length = segment.value.length
        builder.setSpan(
          if (segment.hasAnnotation() && segment.annotation == Preedit.Segment.Annotation.HIGHLIGHT)
            SPAN_CONVERT_HIGHLIGHT
          else BackgroundColorSpan(CONVERT_NORMAL_COLOR),
          offsetInString,
          offsetInString + length,
          spanFlags
        )
        offsetInString += length
      }
    } else {
      // We cannot show system cursor inside preedit here.
      // Instead we change text style before the preedit's cursor.
      val cursorOffsetInString = builder.toString().offsetByCodePoints(0, cursor)
      if (cursor != builder.length) {
        builder.setSpan(
          SPAN_PARTIAL_SUGGESTION_COLOR,
          cursorOffsetInString,
          builder.length,
          spanFlags
        )
      }
      if (cursor > 0) {
        builder.setSpan(SPAN_BEFORE_CURSOR, 0, cursorOffsetInString, spanFlags)
      }
    }

    // System cursor will be moved to the tail of preedit.
    // It triggers onUpdateSelection again.
    val cursorPosition = if (cursor > 0) MozcUtil.CURSOR_POSITION_TAIL else 0
    if (!inputConnection.setComposingText(builder, cursorPosition)) {
      MozcLog.e("Failed to set composing text.")
    }
  }

  private fun maybeSetSelection(output: ProtoCommands.Output, inputConnection: InputConnection) {
    if (!output.hasPreedit()) {
      return
    }
    val preedit = output.preedit
    val cursor = preedit.cursor
    if (cursor == 0 || cursor == getPreeditLength(preedit)) {
      // The cursor is at the beginning/ending of the preedit. So we don't anything about the
      // caret setting.
      return
    }
    var caretPosition = selectionTracker.preeditStartPosition
    if (output.hasDeletionRange()) {
      caretPosition += output.deletionRange.offset
    }
    if (output.hasResult()) {
      caretPosition += output.result.value.length
    }
    if (output.hasPreedit()) {
      caretPosition += output.preedit.cursor
    }
    if (!inputConnection.setSelection(caretPosition, caretPosition)) {
      MozcLog.e("Failed to set selection.")
    }
  }

  /**
   * Propagates the preferences which affect client-side.
   *
   * If the previous parameter (this.clientSidePreference) is null, all the fields in the latest
   * parameter are propagated. If not, only differences are propagated.
   *
   * After the execution, `this.propagatedClientSidePreference` is updated.
   *
   * @param newPreference the ClientSidePreference to be propagated
   */
  private fun propagateClientSidePreference(newPreference: ClientSidePreference) {
    val oldPreference = propagatedClientSidePreference
    if (oldPreference == null ||
        oldPreference.isHapticFeedbackEnabled != newPreference.isHapticFeedbackEnabled
    ) {
      feedbackManager.isHapticFeedbackEnabled = newPreference.isHapticFeedbackEnabled
    }
    if (oldPreference == null ||
        oldPreference.isSoundFeedbackEnabled != newPreference.isSoundFeedbackEnabled
    ) {
      feedbackManager.isSoundFeedbackEnabled = newPreference.isSoundFeedbackEnabled
    }
    if (oldPreference == null ||
        oldPreference.soundFeedbackVolume != newPreference.soundFeedbackVolume
    ) {
      // The default value is 0.4f. In order to set the 50 to the default value, divide the
      // preference value by 125f heuristically.
      feedbackManager.soundFeedbackVolume = newPreference.soundFeedbackVolume / 125f
    }
    if (oldPreference == null ||
        oldPreference.isPopupFeedbackEnabled != newPreference.isPopupFeedbackEnabled
    ) {
      viewManager.setPopupEnabled(newPreference.isPopupFeedbackEnabled)
    }
    if (oldPreference == null || oldPreference.keyboardLayout !== newPreference.keyboardLayout) {
      viewManager.setKeyboardLayout(newPreference.keyboardLayout)
    }
    if (oldPreference == null || oldPreference.inputStyle !== newPreference.inputStyle) {
      viewManager.setInputStyle(newPreference.inputStyle)
    }
    if (oldPreference == null ||
        oldPreference.isQwertyLayoutForAlphabet != newPreference.isQwertyLayoutForAlphabet
    ) {
      viewManager.setQwertyLayoutForAlphabet(newPreference.isQwertyLayoutForAlphabet)
    }
    if (oldPreference == null || oldPreference.isFullscreenMode != newPreference.isFullscreenMode) {
      viewManager.isFullscreenMode =
        applicationCompatibility.isFullScreenModeSupported && newPreference.isFullscreenMode
    }
    if (oldPreference == null || oldPreference.flickSensitivity != newPreference.flickSensitivity) {
      viewManager.setFlickSensitivity(newPreference.flickSensitivity)
    }
    if (oldPreference == null || oldPreference.hardwareKeyMap !== newPreference.hardwareKeyMap) {
      viewManager.setHardwareKeyMap(newPreference.hardwareKeyMap)
    }
    if (oldPreference == null || oldPreference.skinType != newPreference.skinType) {
      viewManager.setSkin(newPreference.skinType.getSkin(resources))
    }
    if (oldPreference == null ||
        oldPreference.isMicrophoneButtonEnabled != newPreference.isMicrophoneButtonEnabled
    ) {
      viewManager.setMicrophoneButtonEnabledByPreference(newPreference.isMicrophoneButtonEnabled)
    }
    if (oldPreference == null || oldPreference.layoutAdjustment != newPreference.layoutAdjustment) {
      viewManager.setLayoutAdjustment(newPreference.layoutAdjustment)
    }
    if (oldPreference == null ||
        oldPreference.keyboardHeightRatio != newPreference.keyboardHeightRatio
    ) {
      viewManager.setKeyboardHeightRatio(newPreference.keyboardHeightRatio)
    }
    propagatedClientSidePreference = newPreference
  }

  /**
   * Sends imposed config to the Mozc server.
   *
   * Some config items should be mobile ones. For example, "selection shortcut" should be disabled
   * on software keyboard regardless of stored config if there is no hardware keyboard.
   */
  private fun updateImposedConfig() {
    // TODO(hsumita): Respect Config.SelectionShortcut.
    val shortcutMode =
      if (viewManager.isFloatingCandidateMode) SelectionShortcut.SHORTCUT_123456789
      else SelectionShortcut.NO_SHORTCUT

    // TODO(matsuzakit): deviceConfig should be used to set following config items.
    sessionExecutor.setConfig(
      Config.newBuilder()
        .setSessionKeymap(SessionKeymap.MOBILE)
        .setSelectionShortcut(shortcutMode)
        .setUseEmojiConversion(true)
        .build()
    )
  }

  /** A call-back to catch all the change on any preferences. */
  private inner class SharedPreferenceChangeAdapter : OnSharedPreferenceChangeListener {
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
      if (key != null && key.startsWith(PREF_TWEAK_PREFIX)) {
        // If the key belongs to PREF_TWEAK group, re-create SessionHandler and view.
        prepareEveryTime(sharedPreferences, resources.configuration)
        setInputView(onCreateInputView())
        return
      }
      propagateClientSidePreference(
        createFromSharedPreferences(
          sharedPreferences,
          resources,
          resources.configuration.orientation
        )
      )
      sessionExecutor.config = ConfigUtil.toConfig(sharedPreferences)
      sessionExecutor.preferenceUsageStatsEvent(sharedPreferences, resources)
    }
  }

  private fun onConfigurationChangedInternal(newConfig: Configuration) {
    val inputConnection = currentInputConnection
    if (inputConnection != null) {
      if (inputBound) {
        inputConnection.finishComposingText()
      }
      val selectionStart = selectionTracker.lastSelectionStart
      val selectionEnd = selectionTracker.lastSelectionEnd
      if (selectionStart >= 0 && selectionEnd >= 0) {
        // We need to keep the last caret position, but it will be soon overwritten in
        // onStartInput. Theoretically, we should prohibit the overwriting, but unfortunately
        // there is no good way to figure out whether the invocation of onStartInput is caused by
        // configuration change, or not. Thus, instead, we'll make an event to invoke
        // onUpdateSelectionInternal with an expected position after the onStartInput invocation,
        // so that it will again overwrite the caret position.
        // Note that, if a user rotates the device with holding preedit text, it will be committed
        // by finishComposingText above, and onUpdateSelection will be invoked from the framework.
        // Invoke onUpdateSelectionInternal twice with same arguments should be safe in this
        // situation.
        configurationChangedHandler.sendMessage(
          configurationChangedHandler.obtainMessage(0, selectionStart, selectionEnd)
        )
      }
    }
    resetContext()
    selectionTracker.onConfigurationChanged()
    sessionExecutor.updateRequest(
      MozcUtil.getRequestBuilder(resources, currentKeyboardSpecification, newConfig).build(),
      emptyList()
    )

    // NOTE : This method is not called at the time when the service is started.
    // Based on newConfig, client side preferences should be sent
    // because they change based on device config.
    propagateClientSidePreference(
      createFromSharedPreferences(
        Preconditions.checkNotNull(PreferenceManager.getDefaultSharedPreferences(this)),
        resources,
        newConfig.orientation
      )
    )
    viewManager.onConfigurationChanged(newConfig)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    onConfigurationChangedInternal(newConfig)
    // super.onConfigurationChanged must be called after propagateClientSidePreference
    // to use updated MobileConfiguration.
    super.onConfigurationChanged(newConfig)
  }

  private fun onUpdateSelectionInternal(
    oldSelStart: Int,
    oldSelEnd: Int,
    newSelStart: Int,
    newSelEnd: Int,
    candidatesStart: Int,
    candidatesEnd: Int
  ) {
    MozcLog.d("start MozcService#onUpdateSelectionInternal " + System.nanoTime())
    val updateStatus =
      selectionTracker.onUpdateSelection(
        oldSelStart,
        oldSelEnd,
        newSelStart,
        newSelEnd,
        candidatesStart,
        candidatesEnd,
        applicationCompatibility.isIgnoringMoveToTail
      )
    when (updateStatus) {
      SelectionTracker.DO_NOTHING -> {}
      SelectionTracker.RESET_CONTEXT -> {
        sessionExecutor.resetContext()

        // Commit the current composing text (preedit text), in case we hit an unknown state.
        // Keeping the composing text sometimes makes it impossible for users to input characters,
        // because it can cause consecutive mis-understanding of caret positions.
        // We do this iff the keyboard is shown, because some other application may edit
        // composition string, such as Google Translate.
        if (isInputViewShown && inputBound) {
          val inputConnection = currentInputConnection
          inputConnection?.finishComposingText()
        }

        // Rendering default Command causes hiding candidate window,
        // and re-showing the keyboard view.
        viewManager.render(ProtoCommands.Command.getDefaultInstance())
      }
      else -> {
        // Otherwise, the updateStatus is the position of the cursor to be moved.
        if (updateStatus < 0) {
          throw AssertionError("Unknown update status: $updateStatus")
        }
        sessionExecutor.moveCursor(updateStatus, renderResultCallback)
      }
    }
    MozcLog.d("end MozcService#onUpdateSelectionInternal " + System.nanoTime())
  }

  override fun onUpdateSelection(
    oldSelStart: Int,
    oldSelEnd: Int,
    newSelStart: Int,
    newSelEnd: Int,
    candidatesStart: Int,
    candidatesEnd: Int
  ) {
    onUpdateSelectionInternal(
      oldSelStart,
      oldSelEnd,
      newSelStart,
      newSelEnd,
      candidatesStart,
      candidatesEnd
    )
    super.onUpdateSelection(
      oldSelStart,
      oldSelEnd,
      newSelStart,
      newSelEnd,
      candidatesStart,
      candidatesEnd
    )
  }

  private fun trimMemory() {
    // We must guarantee the contract of MemoryManageable#trimMemory.
    if (!isInputViewShown) {
      MozcLog.d("Trimming memory")
      sessionExecutor.deleteSession()
      viewManager.trimMemory()
    }
  }
}

/** "what" value of message. Always use this. */
private const val WHAT = 0

/** The current period of sending SYNC_DATA is 15 mins (as same as desktop version). */
private const val SYNC_DATA_COMMAND_PERIOD = 15L * 60L * 1000L

/** Duration after hiding window in milliseconds. */
private const val DURATION_MS = 10L * 1000L

// Keys for tweak preferences.
private const val PREF_TWEAK_PREFIX = "pref_tweak_"
private const val PREF_TWEAK_LOGGING_PROTOCOL_BUFFERS = "pref_tweak_logging_protocol_buffers"

// Focused segment's attribute.
private val SPAN_CONVERT_HIGHLIGHT = BackgroundColorSpan(0x66EF3566)

// Background color span for non-focused conversion segment.
// We don't create a static CharacterStyle instance since there are multiple segments at the
// same
// time. Otherwise, segments except for the last one cannot have style.
private const val CONVERT_NORMAL_COLOR = 0x19EF3566

// Cursor position.
// Note that InputConnection seems not to be able to show cursor. This is a workaround.
private val SPAN_BEFORE_CURSOR = BackgroundColorSpan(0x664DB6AC)

// Background color span for partial conversion.
private val SPAN_PARTIAL_SUGGESTION_COLOR = BackgroundColorSpan(0x194DB6AC)

// Underline.
private val SPAN_UNDERLINE = UnderlineSpan()

/**
 * Hook to support mushroom protocol. If there is pending Mushroom result for the connecting field,
 * commit it. Then, (regardless of whether there exists pending result,) clears all remaining
 * pending result.
 */
private fun maybeCommitMushroomResult(attribute: EditorInfo, connection: InputConnection?) {
  if (connection == null) {
    return
  }
  val resultProxy = MushroomResultProxy.getInstance()
  var result: String?
  synchronized(resultProxy) {
    // We need to obtain the result.
    result = resultProxy.getReplaceKey(attribute.fieldId)
  }
  if (result != null) {
    // Found the pending mushroom application result to the connecting field. Commit it.
    connection.commitText(result, MozcUtil.CURSOR_POSITION_TAIL)
    // And clear the proxy.
    // Previous implementation cleared the proxy even when the replace result is NOT found.
    // This caused incompatible mushroom issue because the activity transition gets sometimes
    // like following:
    //   Mushroom activity -> Intermediate activity -> Original application activity
    // In this case the intermediate activity unexpectedly consumed the result so nothing
    // was committed to the application activity.
    // To fix this issue the proxy is cleared when:
    // - The result is committed. OR
    // - Mushroom activity is launched.
    // NOTE: In the worst case, result data might remain in the proxy.
    synchronized(resultProxy) { resultProxy.clear() }
  }
}

private fun enableCursorAnchorInfo(connection: InputConnection): Boolean {
  Preconditions.checkNotNull(connection)
  return connection.requestCursorUpdates(
    InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
  )
}

private fun getInputFieldType(attribute: EditorInfo): InputFieldType {
  val inputType = attribute.inputType
  if (MozcUtil.isPasswordField(inputType)) {
    return InputFieldType.PASSWORD
  }
  val inputClass = inputType and InputType.TYPE_MASK_CLASS
  if (inputClass == InputType.TYPE_CLASS_PHONE) {
    return InputFieldType.TEL
  }
  return if (inputClass == InputType.TYPE_CLASS_NUMBER) {
    InputFieldType.NUMBER
  } else InputFieldType.NORMAL
}

private fun createKeyEvent(
  original: KeyEvent,
  eventTime: Long,
  action: Int,
  repeatCount: Int
): KeyEvent {
  return KeyEvent(
    original.downTime,
    eventTime,
    action,
    original.keyCode,
    repeatCount,
    original.metaState,
    original.deviceId,
    original.scanCode,
    original.flags
  )
}

private fun maybeDeleteSurroundingText(
  output: ProtoCommands.Output,
  inputConnection: InputConnection
) {
  if (!output.hasDeletionRange()) {
    return
  }
  val range = output.deletionRange
  val leftRange = -range.offset
  val rightRange = range.length - leftRange
  if (leftRange < 0 || rightRange < 0) {
    // If the range does not include the current position, do nothing
    // because Android's API does not expect such situation.
    MozcLog.w("Deletion range has unsupported parameters: $range")
    return
  }
  if (!inputConnection.deleteSurroundingText(leftRange, rightRange)) {
    MozcLog.e("Failed to delete surrounding text.")
  }
}

private fun maybeCommitText(output: ProtoCommands.Output, inputConnection: InputConnection) {
  if (!output.hasResult()) {
    return
  }
  val outputText = output.result.value
  if (outputText == "") {
    // Do nothing for an empty result string.
    return
  }
  var position = MozcUtil.CURSOR_POSITION_TAIL
  if (output.result.hasCursorOffset()) {
    if (output.result.cursorOffset == -outputText.codePointCount(0, outputText.length)) {
      position = MozcUtil.CURSOR_POSITION_HEAD
    } else {
      MozcLog.e("Unsupported position: " + output.result.toString())
    }
  }
  if (!inputConnection.commitText(outputText, position)) {
    MozcLog.e("Failed to commit text.")
  }
}

private fun getPreeditLength(preedit: Preedit): Int {
  var result = 0
  for (i in 0 until preedit.segmentCount) {
    result += preedit.getSegment(i).valueLength
  }
  return result
}
