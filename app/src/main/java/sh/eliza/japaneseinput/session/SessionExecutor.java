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

package sh.eliza.japaneseinput.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Capability;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Capability.TextDeletionCapabilityType;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.CompositionMode;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Context.InputFieldType;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input.CommandType;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Input.TouchEvent;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.InputOrBuilder;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.KeyEvent.SpecialKey;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Output;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Request;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.SessionCommand.UsageStatsEvent;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoConfig.Config;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryCommand;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryCommandStatus;
import sh.eliza.japaneseinput.KeycodeConverter.KeyEventInterface;
import sh.eliza.japaneseinput.MozcLog;
import sh.eliza.japaneseinput.MozcUtil;
import sh.eliza.japaneseinput.ViewManagerInterface.LayoutAdjustment;
import sh.eliza.japaneseinput.preference.ClientSidePreference;

/**
 * This class handles asynchronous and synchronous execution of command evaluation based on {@link
 * SessionHandler}.
 *
 * <p>All execution is done on the single worker thread (not the UI thread). For asynchronous
 * execution, an evaluation task is sent to the worker thread, and the method returns immediately.
 * For synchronous execution, a task is sent to the worker thread, too, but it waits the execution
 * of the task (and pending tasks which have been sent before the task), and then returns.
 */
public class SessionExecutor {

  // At the moment we call mozc server via JNI interface directly,
  // while other platforms, e.g. Win/Mac/Linux etc, use IPC.
  // In order to keep the call order correctly, we call it from the single worker thread.
  // Note that we use well-known double check lazy initialization,
  // so that we can inject instances via reflections for testing purposes.
  private static volatile Optional<SessionExecutor> instance = Optional.absent();

  private static SessionExecutor getInstanceInternal(
      Optional<SessionHandlerFactory> factory, Context applicationContext) {
    Optional<SessionExecutor> result = instance;
    if (!result.isPresent()) {
      synchronized (SessionExecutor.class) {
        result = instance;
        if (!result.isPresent()) {
          result = instance = Optional.of(new SessionExecutor());
          if (factory.isPresent()) {
            result.get().reset(factory.get(), applicationContext);
          }
        }
      }
    }
    return result.get();
  }

  /**
   * Returns an instance of {@link SessionExecutor}. This method may return an instance without
   * initialization, assuming it will be initialized by client in appropriate timing.
   */
  public static SessionExecutor getInstance(Context applicationContext) {
    return getInstanceInternal(Optional.absent(), Preconditions.checkNotNull(applicationContext));
  }

  /**
   * Returns an instance of {@link SessionExecutor}. At first invocation, the instance will be
   * initialized by using given {@code factory}. Otherwise, the {@code factory} is simply ignored.
   */
  public static SessionExecutor getInstanceInitializedIfNecessary(
      SessionHandlerFactory factory, Context applicationContext) {
    return getInstanceInternal(
        Optional.of(factory), Preconditions.checkNotNull(applicationContext));
  }

  private static volatile Optional<HandlerThread> sessionHandlerThread = Optional.absent();

  private static HandlerThread getHandlerThread() {
    Optional<HandlerThread> result = sessionHandlerThread;
    if (!result.isPresent()) {
      synchronized (SessionExecutor.class) {
        result = sessionHandlerThread;
        if (!result.isPresent()) {
          result = Optional.of(new HandlerThread("Session worker thread"));
          result.get().setDaemon(true);
          result.get().start();
          sessionHandlerThread = result;
        }
      }
    }
    return result.get();
  }

  /** An interface to accept the result of asynchronous evaluation. */
  public interface EvaluationCallback {
    void onCompleted(Optional<Command> command, Optional<KeyEventInterface> triggeringKeyEvent);
  }

  private static class AsynchronousEvaluationContext {

    // For asynchronous evaluation, we set the sessionId in the callback running on the worker
    // thread. So, this class has Input.Builer as an argument for an evaluation, while
    // SynchronousEvaluationContext has Input because it is not necessary to be set sessionId.
    final long timeStamp;
    final Input.Builder inputBuilder;
    volatile Optional<Command> outCommand = Optional.absent();
    final Optional<KeyEventInterface> triggeringKeyEvent;
    final Optional<EvaluationCallback> callback;
    final Optional<Handler> callbackHandler;

    AsynchronousEvaluationContext(
        long timeStamp,
        Input.Builder inputBuilder,
        Optional<KeyEventInterface> triggeringKeyEvent,
        Optional<EvaluationCallback> callback,
        Optional<Handler> callbackHandler) {
      this.timeStamp = timeStamp;
      this.inputBuilder = Preconditions.checkNotNull(inputBuilder);
      this.triggeringKeyEvent = Preconditions.checkNotNull(triggeringKeyEvent);
      this.callback = Preconditions.checkNotNull(callback);
      this.callbackHandler = Preconditions.checkNotNull(callbackHandler);
    }
  }

  private static class SynchronousEvaluationContext {

    final Input input;
    volatile Optional<Command> outCommand = Optional.absent();
    final CountDownLatch evaluationSynchronizer;

    SynchronousEvaluationContext(Input input, CountDownLatch evaluationSynchronizer) {
      this.input = Preconditions.checkNotNull(input);
      this.evaluationSynchronizer = Preconditions.checkNotNull(evaluationSynchronizer);
    }
  }

  /** Context class just lines handler queue. */
  private static class KeyEventCallbackContext {

    final KeyEventInterface triggeringKeyEvent;
    final EvaluationCallback callback;
    final Handler callbackHandler;

    KeyEventCallbackContext(
        KeyEventInterface triggeringKeyEvent,
        EvaluationCallback callback,
        Handler callbackHandler) {
      this.triggeringKeyEvent = Preconditions.checkNotNull(triggeringKeyEvent);
      this.callback = Preconditions.checkNotNull(callback);
      this.callbackHandler = Preconditions.checkNotNull(callbackHandler);
    }
  }

  /**
   * A core implementation of evaluation executing process.
   *
   * <p>This class takes messages from the UI thread. By using {@link SessionHandler}, it evaluates
   * the {@link Input} in a message, and then returns the result with notifying the UI thread if
   * necessary. All evaluations should be done with this class in order to keep evaluation in the
   * incoming order.
   */
  private static class ExecutorMainCallback implements Handler.Callback {

    /**
     * Initializes the currently connected sesion handler. We need to initialize the current session
     * handler in the executor thread due to performance reason. This message should be run before
     * any other messages.
     */
    static final int INITIALIZE_SESSION_HANDLER = 0;

    /** Deletes the session. */
    static final int DELETE_SESSION = 1;

    /** Evaluates the command asynchronously. */
    static final int EVALUATE_ASYNCHRONOUSLY = 2;

    /**
     * Evaluates the command asynchronously as similar to {@code EVALUATE_ASYNCHRONOUSLY}. The
     * difference from it is this should be used for the actual "key" event, such as "hit 'A' key,"
     * "hit 'BACKSPACE'", "hit 'SPACEKEY'" or something.
     *
     * <p>Note: this is used to figure out whether it is ok to skip some heavier instruction in the
     * server.
     */
    static final int EVALUATE_KEYEVENT_ASYNCHRONOUSLY = 3;

    /** Evaluates the command synchronously. */
    static final int EVALUATE_SYNCHRONOUSLY = 4;

    /** Updates the current request, and notify it to the server. */
    static final int UPDATE_REQUEST = 5;

    /** Just pass a message to callback. */
    static final int PASS_TO_CALLBACK = 6;

    private static final long INVALID_SESSION_ID = 0;

    // TODO(exv): ensure list is exhaustive
    /** A set of CommandType which don't need session id. */
    private static final Set<CommandType> SESSION_INDEPENDENT_COMMAND_TYPE_SET =
        Collections.unmodifiableSet(
            EnumSet.of(
                CommandType.NO_OPERATION,
                CommandType.SET_CONFIG,
                CommandType.GET_CONFIG,
                CommandType.CLEAR_USER_HISTORY,
                CommandType.CLEAR_USER_PREDICTION,
                CommandType.CLEAR_UNUSED_USER_PREDICTION,
                CommandType.RELOAD,
                CommandType.SEND_USER_DICTIONARY_COMMAND));

    private final SessionHandler sessionHandler;

    // Mozc session's ID.
    // Set on CREATE_SESSION and will not be updated.
    private long sessionId = INVALID_SESSION_ID;
    private Optional<Request.Builder> request = Optional.absent();

    // The logging for debugging is disabled by default.
    boolean isLogging = false;

    private ExecutorMainCallback(SessionHandler sessionHandler) {
      this.sessionHandler = Preconditions.checkNotNull(sessionHandler);
    }

    @Override
    public boolean handleMessage(Message message) {
      Preconditions.checkNotNull(message);

      // Dispatch the message.
      switch (message.what) {
        case INITIALIZE_SESSION_HANDLER:
          sessionHandler.initialize((Context) message.obj);
          break;
        case DELETE_SESSION:
          deleteSession();
          break;
        case EVALUATE_ASYNCHRONOUSLY:
        case EVALUATE_KEYEVENT_ASYNCHRONOUSLY:
          evaluateAsynchronously((AsynchronousEvaluationContext) message.obj, message.getTarget());
          break;
        case EVALUATE_SYNCHRONOUSLY:
          evaluateSynchronously((SynchronousEvaluationContext) message.obj);
          break;
        case UPDATE_REQUEST:
          updateRequest((Input.Builder) message.obj);
          break;
        case PASS_TO_CALLBACK:
          passToCallBack((KeyEventCallbackContext) message.obj);
          break;
        default:
          // We don't process unknown messages.
          return false;
      }

      return true;
    }

    private Command evaluate(Input input) {
      Command inCommand =
          Command.newBuilder().setInput(input).setOutput(Output.getDefaultInstance()).build();
      // Note that toString() of ProtocolBuffers' message is very heavy.
      if (isLogging) {
        MozcCommandDebugger.inLog(inCommand);
      }
      Command outCommand = sessionHandler.evalCommand(inCommand);
      if (isLogging) {
        MozcCommandDebugger.outLog(outCommand);
      }
      return outCommand;
    }

    private void ensureSession() {
      if (sessionId != INVALID_SESSION_ID) {
        return;
      }

      // Send CREATE_SESSION command and keep the returned sessionId.
      Input input =
          Input.newBuilder()
              .setType(CommandType.CREATE_SESSION)
              .setCapability(
                  Capability.newBuilder()
                      .setTextDeletion(TextDeletionCapabilityType.DELETE_PRECEDING_TEXT))
              .build();
      sessionId = evaluate(input).getOutput().getId();

      // Just after session creation, we send the default "request" to the server,
      // with ignoring its result.
      // Set mobile dedicated fields, which will not be changed.
      // Other fields may be set when the input view is changed.
      Request.Builder builder = Request.newBuilder();
      MozcUtil.setSoftwareKeyboardRequest(builder);
      request = Optional.of(builder);
      evaluate(
          Input.newBuilder()
              .setId(sessionId)
              .setType(CommandType.SET_REQUEST)
              .setRequest(request.get())
              .build());
    }

    void deleteSession() {
      if (sessionId == INVALID_SESSION_ID) {
        return;
      }

      Input input = Input.newBuilder().setType(CommandType.DELETE_SESSION).setId(sessionId).build();
      evaluate(input);
      sessionId = INVALID_SESSION_ID;
      request = Optional.absent();
    }

    /**
     * Returns {@code true} iff the given {@code output} is squashable by following output.
     *
     * <p>Here, a squashable output A may be dropped if the next output B is sent before the UI
     * thread processes A (for performance reason).
     *
     * <p>{@link Output} consists from both fields which can be overwritten by following outputs
     * (e.g. composing text, a candidate list), and/or ones which cannot be (e.g. conversion
     * result). Squashing will happen when an output consists from only overwritable fields and then
     * another output, which will overwrite the UI change caused by the older output, comes.
     */
    private static boolean isSquashableOutput(Output output) {
      // - If the output contains a result, it is not squashable because it is necessary
      //   to commit the string to the client application.
      // - If it has deletion_range, it is not squashable either because it is necessary
      //   to notify the editing to the client application.
      // - If the key is not consumed, it is not squashable because it is necessary to pass through
      //   the event to the client application.
      // - If the event doesn't have candidates, it is not squashable because we need to ensure
      //   the keyboard is visible.
      // - Otherwise squashable. An example is toggling a character. It contains neither
      //   result string nor deletion_range, has candidates (by suggestion) and the keyevent
      //   is consumed.
      return (output.getResult().getValue().length() == 0)
          && !output.hasDeletionRange()
          && output.getConsumed()
          && (output.getAllCandidateWords().getCandidatesCount() > 0);
    }

    /**
     * @return {@code true} if the given {@code inputBuilder} needs to be set session id. Otherwise
     *     {@code false}.
     */
    private static boolean isSessionIdRequired(InputOrBuilder input) {
      return !SESSION_INDEPENDENT_COMMAND_TYPE_SET.contains(input.getType());
    }

    private void evaluateAsynchronously(
        AsynchronousEvaluationContext context, Handler sessionExecutorHandler) {
      // Before the evaluation, we remove all pending squashable result callbacks for performance
      // reason of less powerful devices.
      Input.Builder inputBuilder = context.inputBuilder;
      Optional<Handler> callbackHandler = context.callbackHandler;
      // TODO(exv): removed check to not squash EXPAND_SUGGESTION here. is that okay?
      if (callbackHandler.isPresent()) {
        // Do not squash by EXPAND_SUGGESTION request, because the result of EXPAND_SUGGESTION
        // won't affect the inputConnection in MozcService, as the result should update
        // only candidates conceptually.
        callbackHandler.get().removeMessages(CallbackHandler.SQUASHABLE_OUTPUT);
      }

      if (inputBuilder.hasKey()
          && (!inputBuilder.getKey().hasSpecialKey()
              || inputBuilder.getKey().getSpecialKey() == SpecialKey.BACKSPACE)
          && sessionExecutorHandler.hasMessages(EVALUATE_KEYEVENT_ASYNCHRONOUSLY)) {
        // Do not request suggestion result, due to performance reason, when:
        // - the key is normal key or backspace, and
        // - there is (at least one) following event.
        inputBuilder.setRequestSuggestion(false);
      }

      // We set the session id to the input in asynchronous evaluation before the evaluation,
      // if necessary.
      if (isSessionIdRequired(inputBuilder)) {
        ensureSession();
        inputBuilder.setId(sessionId);
      }
      context.outCommand = Optional.of(evaluate(inputBuilder.build()));

      // Invoke callback handler if necessary.
      if (callbackHandler.isPresent()) {
        // For performance reason of, especially, less powerful devices, we want to skip
        // rendering whose effect will be overwritten by following (pending) rendering.
        // We annotate if the output can be overwritten or not here, so that we can remove
        // only those messages in later evaluation.
        Output output = context.outCommand.get().getOutput();
        Message message =
            callbackHandler
                .get()
                .obtainMessage(
                    isSquashableOutput(output)
                        ? CallbackHandler.SQUASHABLE_OUTPUT
                        : CallbackHandler.UNSQUASHABLE_OUTPUT,
                    context);
        callbackHandler.get().sendMessage(message);
      }
    }

    private void evaluateSynchronously(SynchronousEvaluationContext context) {
      Input input = context.input;
      Preconditions.checkArgument(
          !isSessionIdRequired(input),
          "We expect only non-session-id-related input for synchronous evaluation: " + input);

      context.outCommand = Optional.of(evaluate(input));

      // The client thread is waiting for the evaluation by evaluationSynchronizer,
      // so notify the thread via the lock.
      context.evaluationSynchronizer.countDown();
    }

    private void updateRequest(Input.Builder inputBuilder) {
      ensureSession();
      Preconditions.checkState(request.isPresent());
      request.get().mergeFrom(inputBuilder.getRequest());
      Input input =
          inputBuilder
              .setId(sessionId)
              .setType(CommandType.SET_REQUEST)
              .setRequest(request.get())
              .build();
      // Do not render the result because the result does not have preedit.
      evaluate(input);
    }

    private void passToCallBack(KeyEventCallbackContext context) {
      Handler callbackHandler = context.callbackHandler;
      Message message = callbackHandler.obtainMessage(CallbackHandler.UNSQUASHABLE_OUTPUT, context);
      callbackHandler.sendMessage(message);
    }
  }

  /** A handler to process callback for asynchronous evaluation on the UI thread. */
  private static class CallbackHandler extends Handler {
    /** The message with this {@code what} cannot be overwritten by following evaluation. */
    static final int UNSQUASHABLE_OUTPUT = 0;

    /**
     * The message with this {@code what} may be cancelled by following evaluation because the
     * result of the message would be overwritten soon.
     */
    static final int SQUASHABLE_OUTPUT = 1;

    long cancelTimeStamp = System.nanoTime();

    CallbackHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message message) {
      if (Preconditions.checkNotNull(message).obj.getClass() == KeyEventCallbackContext.class) {
        KeyEventCallbackContext keyEventContext = (KeyEventCallbackContext) message.obj;
        keyEventContext.callback.onCompleted(
            Optional.absent(), Optional.of(keyEventContext.triggeringKeyEvent));
        return;
      }
      AsynchronousEvaluationContext context = (AsynchronousEvaluationContext) message.obj;
      // Note that this method should be run on the UI thread, where removePendingEvaluations runs,
      // so we don't need to take a lock here.
      if (context.timeStamp - cancelTimeStamp > 0) {
        Preconditions.checkState(context.callback.isPresent());
        context.callback.get().onCompleted(context.outCommand, context.triggeringKeyEvent);
      }
    }
  }

  private Optional<Handler> handler = Optional.absent();
  private Optional<ExecutorMainCallback> mainCallback = Optional.absent();
  private final CallbackHandler callbackHandler;

  private SessionExecutor() {
    callbackHandler = new CallbackHandler(Looper.getMainLooper());
  }

  private static void waitForQueueForEmpty(Handler handler) {
    final CountDownLatch synchronizer = new CountDownLatch(1);
    handler.post(
        new Runnable() {
          @Override
          public void run() {
            synchronizer.countDown();
          }
        });
    try {
      synchronizer.await();
    } catch (InterruptedException exception) {
      MozcLog.w("waitForQueueForEmpty is interrupted.");
    }
  }

  /** Resets the instance by setting {@code SessionHandler} created by the given {@code factory}. */
  public void reset(SessionHandlerFactory factory, Context applicationContext) {
    Preconditions.checkNotNull(factory);
    Preconditions.checkNotNull(applicationContext);
    HandlerThread thread = getHandlerThread();
    mainCallback = Optional.of(new ExecutorMainCallback(factory.create()));
    handler = Optional.of(new Handler(thread.getLooper(), mainCallback.get()));
    handler
        .get()
        .sendMessage(
            handler
                .get()
                .obtainMessage(
                    ExecutorMainCallback.INITIALIZE_SESSION_HANDLER, applicationContext));
  }

  /**
   * @param isLogging Set {@code true} if logging of evaluations is needed.
   */
  public void setLogging(boolean isLogging) {
    if (mainCallback.isPresent()) {
      mainCallback.get().isLogging = isLogging;
    }
  }

  /** Remove pending evaluations from the pending queue. */
  public void removePendingEvaluations() {
    callbackHandler.cancelTimeStamp = System.nanoTime();
    if (handler.isPresent()) {
      handler.get().removeMessages(ExecutorMainCallback.EVALUATE_ASYNCHRONOUSLY);
      handler.get().removeMessages(ExecutorMainCallback.EVALUATE_KEYEVENT_ASYNCHRONOUSLY);
      handler.get().removeMessages(ExecutorMainCallback.EVALUATE_SYNCHRONOUSLY);
      handler.get().removeMessages(ExecutorMainCallback.UPDATE_REQUEST);
    }
    callbackHandler.removeMessages(CallbackHandler.UNSQUASHABLE_OUTPUT);
    callbackHandler.removeMessages(CallbackHandler.SQUASHABLE_OUTPUT);
  }

  public void deleteSession() {
    Preconditions.checkState(handler.isPresent());
    handler.get().sendMessage(handler.get().obtainMessage(ExecutorMainCallback.DELETE_SESSION));
  }

  /**
   * Evaluates the input caused by triggeringKeyEvent on the JNI worker thread. When the evaluation
   * is done, callbackHandler will receive the message with the evaluation context.
   *
   * <p>This method returns immediately, i.e., even after this method's invocation, it shouldn't be
   * assumed that the evaluation is done.
   *
   * @param inputBuilder the input data
   * @param triggeringKeyEvent a key event which triggers this evaluation
   * @param callback a callback handler if needed
   */
  private void evaluateAsynchronously(
      Input.Builder inputBuilder,
      Optional<KeyEventInterface> triggeringKeyEvent,
      Optional<EvaluationCallback> callback) {
    Preconditions.checkState(handler.isPresent());
    AsynchronousEvaluationContext context =
        new AsynchronousEvaluationContext(
            System.nanoTime(),
            Preconditions.checkNotNull(inputBuilder),
            Preconditions.checkNotNull(triggeringKeyEvent),
            Preconditions.checkNotNull(callback),
            callback.isPresent() ? Optional.of(callbackHandler) : Optional.absent());
    int type =
        (triggeringKeyEvent.isPresent())
            ? ExecutorMainCallback.EVALUATE_KEYEVENT_ASYNCHRONOUSLY
            : ExecutorMainCallback.EVALUATE_ASYNCHRONOUSLY;
    handler.get().sendMessage(handler.get().obtainMessage(type, context));
  }

  /** Sends {@code SEND_KEY} command to the server asynchronously. */
  public void sendKey(
      ProtoCommands.KeyEvent mozcKeyEvent,
      KeyEventInterface triggeringKeyEvent,
      List<TouchEvent> touchEventList,
      EvaluationCallback callback) {
    Preconditions.checkNotNull(mozcKeyEvent);
    Preconditions.checkNotNull(triggeringKeyEvent);
    Preconditions.checkNotNull(touchEventList);
    Preconditions.checkNotNull(callback);

    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_KEY)
            .setKey(mozcKeyEvent)
            .addAllTouchEvents(touchEventList);
    evaluateAsynchronously(inputBuilder, Optional.of(triggeringKeyEvent), Optional.of(callback));
  }

  /** Sends {@code SUBMIT} command to the server asynchronously. */
  public void submit(EvaluationCallback callback) {
    Preconditions.checkNotNull(callback);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(SessionCommand.newBuilder().setType(SessionCommand.CommandType.SUBMIT));
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.of(callback));
  }

  /** Sends {@code SWITCH_INPUT_MODE} command to the server asynchronously. */
  public void switchInputMode(
      Optional<KeyEventInterface> triggeringKeyEvent,
      CompositionMode mode,
      EvaluationCallback callback) {
    Preconditions.checkNotNull(triggeringKeyEvent);
    Preconditions.checkNotNull(mode);
    Preconditions.checkNotNull(callback);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder()
                    .setType(SessionCommand.CommandType.SWITCH_INPUT_MODE)
                    .setCompositionMode(mode));
    evaluateAsynchronously(inputBuilder, triggeringKeyEvent, Optional.of(callback));
  }

  /** Sends {@code SUBMIT_CANDIDATE} command to the server asynchronously. */
  public void submitCandidate(
      int candidateId, Optional<Integer> rowIndex, EvaluationCallback callback) {
    Preconditions.checkNotNull(rowIndex);
    Preconditions.checkNotNull(callback);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder()
                    .setType(SessionCommand.CommandType.SUBMIT_CANDIDATE)
                    .setId(candidateId));
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.of(callback));

    if (rowIndex.isPresent()) {
      candidateSubmissionStatsEvent(rowIndex.get());
    }
  }

  private void candidateSubmissionStatsEvent(int rowIndex) {
    Preconditions.checkArgument(rowIndex >= 0);

    UsageStatsEvent event;
    switch (rowIndex) {
      case 0:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_0;
        break;
      case 1:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_1;
        break;
      case 2:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_2;
        break;
      case 3:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_3;
        break;
      case 4:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_4;
        break;
      case 5:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_5;
        break;
      case 6:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_6;
        break;
      case 7:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_7;
        break;
      case 8:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_8;
        break;
      case 9:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_9;
        break;
      default:
        event = UsageStatsEvent.SUBMITTED_CANDIDATE_ROW_GE10;
    }
    sendUsageStatsEvent(event);
  }

  /** Sends {@code RESET_CONTEXT} command to the server asynchronously. */
  public void resetContext() {
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder().setType(SessionCommand.CommandType.RESET_CONTEXT));
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.absent());
  }

  /** Sends {@code MOVE_CURSOR} command to the server asynchronously. */
  public void moveCursor(int cursorPosition, EvaluationCallback callback) {
    Preconditions.checkNotNull(callback);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder()
                    .setType(ProtoCommands.SessionCommand.CommandType.MOVE_CURSOR)
                    .setCursorPosition(cursorPosition));
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.of(callback));
  }

  /** Sends {@code CONVERT_NEXT_PAGE} command to the server asynchronously. */
  public void pageDown(EvaluationCallback callback) {
    Preconditions.checkNotNull(callback);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder().setType(SessionCommand.CommandType.CONVERT_NEXT_PAGE));
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.of(callback));
  }

  /** Sends {@code CONVERT_PREV_PAGE} command to the server asynchronously. */
  public void pageUp(EvaluationCallback callback) {
    Preconditions.checkNotNull(callback);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder().setType(SessionCommand.CommandType.CONVERT_PREV_PAGE));
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.of(callback));
  }

  /** Sends {@code SWITCH_INPUT_FIELD_TYPE} command to the server asynchronously. */
  public void switchInputFieldType(InputFieldType inputFieldType) {
    Preconditions.checkNotNull(inputFieldType);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder()
                    .setType(ProtoCommands.SessionCommand.CommandType.SWITCH_INPUT_FIELD_TYPE))
            .setContext(ProtoCommands.Context.newBuilder().setInputFieldType(inputFieldType));
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.absent());
  }

  /** Sends {@code UNDO_OR_REWIND} command to the server asynchronously. */
  public void undoOrRewind(List<TouchEvent> touchEventList, EvaluationCallback callback) {
    Preconditions.checkNotNull(touchEventList);
    Preconditions.checkNotNull(callback);
    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder().setType(SessionCommand.CommandType.UNDO_OR_REWIND))
            .addAllTouchEvents(touchEventList);
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.of(callback));
  }

  // TODO(exv): replace this function
  // /**
  //  * Sends {@code INSERT_TO_STORAGE} with given {@code type}, {@code key} and {@code values}
  //  * to the server asynchronously.
  //  */
  // public void insertToStorage(StorageType type, String key, List<ByteString> values) {
  //   Preconditions.checkNotNull(type);
  //   Preconditions.checkNotNull(key);
  //   Preconditions.checkNotNull(values);
  //   Input.Builder inputBuilder = Input.newBuilder()
  //       .setType(CommandType.INSERT_TO_STORAGE)
  //       .setStorageEntry(GenericStorageEntry.newBuilder()
  //           .setType(type)
  //           .setKey(key)
  //           .addAllValue(values));
  //   evaluateAsynchronously(
  //       inputBuilder, Optional.<KeyEventInterface>absent(),
  // Optional.<EvaluationCallback>absent());
  // }

  // TODO(exv): replace this function
  // public void expandSuggestion(EvaluationCallback callback) {
  //   Preconditions.checkNotNull(callback);
  //   Input.Builder inputBuilder = Input.newBuilder()
  //       .setType(CommandType.SEND_COMMAND)
  //       .setCommand(
  //           SessionCommand.newBuilder().setType(SessionCommand.CommandType.EXPAND_SUGGESTION));
  //   evaluateAsynchronously(
  //       inputBuilder, Optional.<KeyEventInterface>absent(), Optional.of(callback));
  // }

  public void preferenceUsageStatsEvent(SharedPreferences sharedPreferences, Resources resources) {
    Preconditions.checkNotNull(sharedPreferences);
    Preconditions.checkNotNull(resources);

    // TODO(exv): replace this
    // sendIntegerUsageStatsUsageStatsEvent(
    //     UsageStatsEvent.SOFTWARE_KEYBOARD_HEIGHT_DIP_LANDSCAPE,
    //     (int) Math.ceil(MozcUtil.getDimensionForOrientation(
    //         resources, R.dimen.ime_window_height, Configuration.ORIENTATION_LANDSCAPE)));
    // sendIntegerUsageStatsUsageStatsEvent(
    //     UsageStatsEvent.SOFTWARE_KEYBOARD_HEIGHT_DIP_PORTRAIT,
    //     (int) Math.ceil(MozcUtil.getDimensionForOrientation(
    //         resources, R.dimen.ime_window_height, Configuration.ORIENTATION_PORTRAIT)));

    ClientSidePreference landscapePreference =
        ClientSidePreference.createFromSharedPreferences(
            sharedPreferences, resources, Configuration.ORIENTATION_LANDSCAPE);
    ClientSidePreference portraitPreference =
        ClientSidePreference.createFromSharedPreferences(
            sharedPreferences, resources, Configuration.ORIENTATION_PORTRAIT);

    sendIntegerUsageStatsUsageStatsEvent(
        UsageStatsEvent.SOFTWARE_KEYBOARD_LAYOUT_LANDSCAPE, landscapePreference.keyboardLayout.id);
    sendIntegerUsageStatsUsageStatsEvent(
        UsageStatsEvent.SOFTWARE_KEYBOARD_LAYOUT_PORTRAIT, portraitPreference.keyboardLayout.id);

    boolean layoutAdjustmentEnabledInLandscape =
        landscapePreference.layoutAdjustment != LayoutAdjustment.FILL;
    boolean layoutAdjustmentEnabledInPortrait =
        portraitPreference.layoutAdjustment != LayoutAdjustment.FILL;

    sendIntegerUsageStatsUsageStatsEvent(
        UsageStatsEvent.SOFTWARE_KEYBOARD_LAYOUT_ADJUSTMENT_ENABLED_LANDSCAPE,
        layoutAdjustmentEnabledInLandscape ? 1 : 0);
    sendIntegerUsageStatsUsageStatsEvent(
        UsageStatsEvent.SOFTWARE_KEYBOARD_LAYOUT_ADJUSTMENT_ENABLED_PORTRAIT,
        layoutAdjustmentEnabledInPortrait ? 1 : 0);
  }

  private void sendIntegerUsageStatsUsageStatsEvent(UsageStatsEvent event, int value) {
    evaluateAsynchronously(
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder()
                    .setType(SessionCommand.CommandType.USAGE_STATS_EVENT)
                    .setUsageStatsEvent(event)
                    .setUsageStatsEventIntValue(value)),
        Optional.absent(),
        Optional.absent());
  }

  public void touchEventUsageStatsEvent(List<TouchEvent> touchEventList) {
    if (Preconditions.checkNotNull(touchEventList).isEmpty()) {
      return;
    }

    Input.Builder inputBuilder =
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder().setType(SessionCommand.CommandType.USAGE_STATS_EVENT))
            .addAllTouchEvents(touchEventList);
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.absent());
  }

  public void syncData() {
    Input.Builder inputBuilder = Input.newBuilder().setType(CommandType.SYNC_DATA);
    evaluateAsynchronously(inputBuilder, Optional.absent(), Optional.absent());
  }

  /**
   * Evaluates the input on the JNI worker thread, and wait that the evaluation is done. This method
   * blocks (typically <30ms).
   */
  private Output evaluateSynchronously(Input input) {
    Preconditions.checkState(handler.isPresent());
    CountDownLatch evaluationSynchronizer = new CountDownLatch(1);
    SynchronousEvaluationContext context =
        new SynchronousEvaluationContext(input, evaluationSynchronizer);
    handler
        .get()
        .sendMessage(
            handler.get().obtainMessage(ExecutorMainCallback.EVALUATE_SYNCHRONOUSLY, context));

    try {
      evaluationSynchronizer.await();
    } catch (InterruptedException exception) {
      MozcLog.w("Session thread is interrupted during evaluation.");
    }

    return context.outCommand.get().getOutput();
  }

  /** Gets a config from the server. */
  public Config getConfig() {
    Input input = Input.newBuilder().setType(Input.CommandType.GET_CONFIG).build();
    return evaluateSynchronously(input).getConfig();
  }

  /** Sets the given {@code config} to the server. */
  public void setConfig(Config config) {
    Preconditions.checkNotNull(config);
    // Ignore output.
    evaluateAsynchronously(
        Input.newBuilder().setType(Input.CommandType.SET_CONFIG).setConfig(config),
        Optional.absent(),
        Optional.absent());
  }

  /** Clears the unused user prediction from the server synchronously. */
  public void clearUnusedUserPrediction() {
    evaluateSynchronously(
        Input.newBuilder().setType(CommandType.CLEAR_UNUSED_USER_PREDICTION).build());
  }

  /** Clears the user's history from the server synchronously. */
  public void clearUserHistory() {
    evaluateSynchronously(Input.newBuilder().setType(CommandType.CLEAR_USER_HISTORY).build());
  }

  /** Clears user's prediction from the server synchronously. */
  public void clearUserPrediction() {
    evaluateSynchronously(Input.newBuilder().setType(CommandType.CLEAR_USER_PREDICTION).build());
  }

  // TODO(exv): replace this function
  // /**
  //  * Clears a generic storage, which is used typically by symbol history.
  //  * @param storageType the storage to be cleared
  //  */
  // public void clearStorage(StorageType storageType) {
  //   Preconditions.checkNotNull(storageType);
  //   evaluateSynchronously(
  //       Input.newBuilder()
  //           .setType(CommandType.CLEAR_STORAGE)
  //           .setStorageEntry(GenericStorageEntry.newBuilder().setType(storageType))
  //           .build());
  // }

  // TODO(exv): replace this function
  // /**
  //  * Reads stored values of the given {@code type} from the server, and returns it.
  //  */
  // public List<ByteString> readAllFromStorage(StorageType type) {
  //   Preconditions.checkNotNull(type);
  //   Input input = Input.newBuilder()
  //       .setType(CommandType.READ_ALL_FROM_STORAGE)
  //       .setStorageEntry(GenericStorageEntry.newBuilder()
  //           .setType(type))
  //       .build();
  //   Output output = evaluateSynchronously(input);
  //   return output.getStorageEntry().getValueList();
  // }

  /** Sends 'Reload' request to the server, typically for reloading user dictionaries. */
  public void reload() {
    // Ignore output.
    evaluateAsynchronously(
        Input.newBuilder().setType(Input.CommandType.RELOAD), Optional.absent(), Optional.absent());
  }

  /** Sends SEND_USER_DICTIONARY_COMMAND to edit user dictionaries. */
  public UserDictionaryCommandStatus sendUserDictionaryCommand(UserDictionaryCommand command) {
    Preconditions.checkNotNull(command);
    Output output =
        evaluateSynchronously(
            Input.newBuilder()
                .setType(CommandType.SEND_USER_DICTIONARY_COMMAND)
                .setUserDictionaryCommand(command)
                .build());
    return output.getUserDictionaryCommandStatus();
  }

  /** Sends an UPDATE_REQUEST command to the evaluation thread. */
  public void updateRequest(Request update, List<TouchEvent> touchEventList) {
    Preconditions.checkNotNull(update);
    Preconditions.checkNotNull(touchEventList);
    Preconditions.checkState(handler.isPresent());
    Input.Builder inputBuilder =
        Input.newBuilder().setRequest(update).addAllTouchEvents(touchEventList);
    handler
        .get()
        .sendMessage(
            handler.get().obtainMessage(ExecutorMainCallback.UPDATE_REQUEST, inputBuilder));
  }

  public void sendKeyEvent(KeyEventInterface triggeringKeyEvent, EvaluationCallback callback) {
    Preconditions.checkNotNull(triggeringKeyEvent);
    Preconditions.checkNotNull(callback);
    Preconditions.checkState(handler.isPresent());
    KeyEventCallbackContext context =
        new KeyEventCallbackContext(triggeringKeyEvent, callback, callbackHandler);
    handler
        .get()
        .sendMessage(handler.get().obtainMessage(ExecutorMainCallback.PASS_TO_CALLBACK, context));
  }

  public void sendUsageStatsEvent(UsageStatsEvent event) {
    evaluateAsynchronously(
        Input.newBuilder()
            .setType(CommandType.SEND_COMMAND)
            .setCommand(
                SessionCommand.newBuilder()
                    .setType(SessionCommand.CommandType.USAGE_STATS_EVENT)
                    .setUsageStatsEvent(event)),
        Optional.absent(),
        Optional.absent());
  }
}
