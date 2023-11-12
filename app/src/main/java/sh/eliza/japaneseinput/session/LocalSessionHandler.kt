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
package sh.eliza.japaneseinput.session

import android.content.Context
import com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI
import com.google.common.base.Preconditions
import com.google.protobuf.InvalidProtocolBufferException
import java.io.File
import java.io.FileOutputStream
import org.mozc.android.inputmethod.japanese.protobuf.ProtoCommands.Command
import sh.eliza.japaneseinput.MozcLog

private const val USER_PROFILE_DIRECTORY_NAME = ".mozc"
private const val MOZC_DATA_NAME = "mozc.data"

/** Concrete SessionHandler. Calls JNI. */
internal class LocalSessionHandler : SessionHandler {
  override fun initialize(context: Context) {
    // Ensure the user profile directory exists.
    val userProfileDirectory = File(context.filesDir, USER_PROFILE_DIRECTORY_NAME)
    if (!userProfileDirectory.exists()) {
      // No profile directory is found. Create the one.
      if (!userProfileDirectory.mkdirs()) {
        // Failed to create a directory. The mozc conversion engine will be able to run even in this
        // case, but no persistent data (e.g. user history, user dictionary) will be stored, so some
        // fuctions using them won't work well.
        MozcLog.e("Failed to create user profile directory: " + userProfileDirectory.absolutePath)
      }
    }

    // Ensure a copy of the mozc data file exists.
    val dataFile = File(context.cacheDir, MOZC_DATA_NAME)
    if (!dataFile.exists()) {
      context.assets.open(MOZC_DATA_NAME).use { input ->
        FileOutputStream(dataFile).use { output -> input.copyTo(output) }
      }
    }

    // Load the shared object.
    MozcJNI.load(userProfileDirectory.absolutePath, dataFile.absolutePath)
  }

  override fun evalCommand(command: Command): Command {
    val inBytes = Preconditions.checkNotNull(command).toByteArray()
    val outBytes = MozcJNI.evalCommand(inBytes)
    return try {
      Command.parseFrom(outBytes)
    } catch (e: InvalidProtocolBufferException) {
      MozcLog.w(
        "InvalidProtocolBufferException is thrown." +
          "We can do nothing so just return default instance."
      )
      MozcLog.w(e.toString())
      Command.getDefaultInstance()
    }
  }
}
