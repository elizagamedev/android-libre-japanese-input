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
import androidx.preference.PreferenceManager;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Factory class for SessionHandlerInterface.
 *
 * <p>Basically SessionHandler is used as a session handler, which uses JNI. But if SharedPreference
 * needs to use RPC Mozc, SessionHandlerSocketClient may be returned.
 */
public class SessionHandlerFactory {

  private static final String PREF_TWEAK_USE_SOCKET_SESSION_HANDLER_KEY =
      "pref_tweak_use_socket_session_handler";

  private static final String PREF_TWEAK_SOCKET_SESSION_HANDLER_ADDRESS_KEY =
      "pref_tweak_socket_session_handler_address";

  private static final String PREF_TWEAK_SOCKET_SESSION_HANDLER_PORT_KEY =
      "pref_tweak_socket_session_handler_port";

  private final Optional<SharedPreferences> sharedPreferences;

  public SessionHandlerFactory(Context context) {
    this(
        Optional.of(
            PreferenceManager.getDefaultSharedPreferences(Preconditions.checkNotNull(context))));
  }

  /**
   * @param sharedPreferences the preferences. The type to be created is based on the preference.
   */
  public SessionHandlerFactory(Optional<SharedPreferences> sharedPreferences) {
    this.sharedPreferences = Preconditions.checkNotNull(sharedPreferences);
  }

  /** Creates a session handler. */
  public SessionHandler create() {
    return new LocalSessionHandler();
  }
}
