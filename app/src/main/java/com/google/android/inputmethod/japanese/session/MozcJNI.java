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

package org.mozc.android.inputmethod.japanese.session;

import org.mozc.android.inputmethod.japanese.MozcLog;
import com.google.common.base.Preconditions;

/**
 * The wrapper for JNI Mozc server.
 *
 */
class MozcJNI {
  /**
   * Load the mozc native library. This method must be invoked before {@code evalCommand}.
   */
  private static volatile boolean isLoaded = false;

  /**
   * Loads and initializes the JNI library.
   *
   * @param userProfileDirectoryPath path to user profile directory
   * @param dataFilePath optional path to data file (e.g., mozc.data), or {@code null}
   * @param expectedVersion expected version name of .so
   */
  static void load(
      String userProfileDirectoryPath, String dataFilePath, String expectedVersion) {
    Preconditions.checkNotNull(userProfileDirectoryPath);
    Preconditions.checkNotNull(expectedVersion);

    if (isLoaded) {
      return;
    }
    synchronized (MozcJNI.class) {
      if (isLoaded) {
        return;
      }
      MozcLog.d("start MozcJNI#load " + System.nanoTime());
      try {
        System.loadLibrary("mozc");
        MozcLog.v("loadLibrary succeeded");
      } catch (Throwable e) {
        MozcLog.e("loadLibrary failed", e);
        throw new RuntimeException(e);
      }
      String nativeVersion = getVersion();
      if (!nativeVersion.equals(expectedVersion)) {
        StringBuilder message = new StringBuilder("Version conflicts;");
        message.append(" Client:").append(expectedVersion);
        message.append(" Server:").append(nativeVersion);
        throw new UnsatisfiedLinkError(message.toString());
      }
      if (!onPostLoad(userProfileDirectoryPath, dataFilePath)) {
          MozcLog.e("onPostLoad fails");
          return;
      }
      isLoaded = true;
      MozcLog.d("end MozcJNI#load " + System.nanoTime());
    }
  }

  /**
   * Sends Command message to Mozc server and get a result.
   *
   * A caller has to interpret Command instance to and from blob.
   *
   * @param command blob of Command message.
   * @return blob of Command message.
   */
  static synchronized native byte[] evalCommand(byte[] command);

  /**
   * This method initializes the internal state of mozc server, especially dictionary data
   * and session related stuff. We cannot do this in JNI_OnLoad, which is the callback API
   * for JNI called when the shared object is loaded, because we need to pass the file path
   * of data file from Java as only Java knows where the data is in our context.
   */
  private static synchronized native boolean onPostLoad(
      String userProfileDirectoryPath, String dataFilePath);

  /**
   * @return Version string of shared object
   */
  private static native String getVersion();

  /**
   * @return Data version string currently loaded in native layer. Empty if initialization has
   *     not been done yet.
   */
  public static native String getDataVersion();

  /**
   * Sets suppression policy against sending usage stats.
   *
   * <p>Never call this method before JNI is loaded.
   *
   * @param suppress suppresses sending if true
   */
  private static native void suppressSendingStats(boolean suppress);
}
