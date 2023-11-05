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
package sh.eliza.japaneseinput.preference

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.google.common.base.Optional
import com.google.common.base.Preconditions
import java.util.regex.Pattern
import sh.eliza.japaneseinput.R

private fun PackageManager.queryIntentActivitiesCompat(intent: Intent) =
  if (Build.VERSION.SDK_INT >= 33) {
    queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
  } else {
    @Suppress("deprecation") queryIntentActivities(intent, 0)
  }

/**
 * Mini browser to show licenses.
 *
 * We must show some web sites (e.g. EULA) from preference screen. However in some special
 * environments default browser is not installed so even if an Intent is issued nothing will happen.
 * This mini browser accepts an Intent and shows its content (of which URL is included as Intent's
 * data) like as a browser.
 */
class MiniBrowserActivity : AppCompatActivity() {
  // TODO(matsuzakit): "print" link is meaningless. Should be invisible.
  // TODO(matsuzakit): CSS needs to be improved.
  private class MiniBrowserClient(
    private val restrictionPattern: String,
    private val packageManager: PackageManager,
    private val context: Context
  ) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, url: String) =
      try {
        // Use temporary matcher intentionally.
        // Regex engine is rather heavy to instantiate so use it as less as possible.
        if (!Pattern.matches(restrictionPattern, url)) {
          // If the URL's doesn't match restriction pattern,
          // delegate the operation to the default browser.
          val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
          if (!packageManager.queryIntentActivitiesCompat(browserIntent).isEmpty()) {
            context.startActivity(browserIntent)
          }
          // If no default browser is available, do nothing.
          true
        } else {
          // Prevent from invoking default browser.
          // In some special environment default browser is not installed.
          false
        }
      } catch (e: Throwable) {
        // This method might be called from native layer.
        // Therefore throwing something from here causes native crash.
        // To prevent from native crash, catches all here.
        // At least SecurityException must be caught here for Android-TV.
        true
      }
  }

  private var webView = Optional.absent<WebView>()
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val webView = WebView(this)
    this.webView = Optional.of(webView)
    webView.webViewClient =
      MiniBrowserClient(
        resources.getString(R.string.pref_url_restriction_regex),
        packageManager,
        this
      )
    webView.loadUrl(intent.data.toString())
    setContentView(webView)
  }

  override fun onPause() {
    // Clear cache in order to show appropriate website even if system locale is changed.
    if (webView.isPresent) {
      webView.get().clearCache(true)
    }
    super.onPause()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    Preconditions.checkNotNull(event)

    // Enable back-key inside the webview.
    // If no history exists, default behavior is executed (finish the activity).
    if (keyCode == KeyEvent.KEYCODE_BACK && webView.isPresent && webView.get().canGoBack()) {
      webView.get().goBack()
      return true
    }
    return super.onKeyDown(keyCode, event)
  }
}
