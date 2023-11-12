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
package sh.eliza.japaneseinput.mushroom

import android.content.Context
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import sh.eliza.japaneseinput.R

/**
 * This is the activity to select a Mushroom application to be launched. Also, this class proxies
 * the `replace_key` between MozcService and the mushroom application.
 */
class MushroomSelectionActivity : AppCompatActivity() {

  /**
   * ListAdapter to use custom view (Application Icon followed by Application Name) for ListView
   * entry.
   */
  private class MushroomApplicationListAdapter(context: Context) :
    ArrayAdapter<ResolveInfo?>(
      context,
      0,
      0,
      MushroomUtil.getMushroomApplicationList(context.packageManager)
    ) {
    override fun getView(position: Int, contentView: View?, parent: ViewGroup): View {
      val realizedContentView =
        contentView
          ?: LayoutInflater.from(context)
            .inflate(R.layout.mushroom_selection_dialog_entry, parent, false)

      // Set appropriate application icon and its name.
      val resolveInfo = getItem(position)!!
      val icon = realizedContentView.findViewById<ImageView>(R.id.mushroom_application_icon)
      icon.setImageDrawable(resolveInfo.loadIcon(context.packageManager))
      val text = realizedContentView.findViewById<TextView>(R.id.mushroom_application_label)
      text.text = resolveInfo.loadLabel(context.packageManager)

      return realizedContentView
    }
  }

  /** ClickListener to launch the target activity. */
  private inner class MushroomApplicationListClickListener(
    private val activity: AppCompatActivity,
  ) : OnItemClickListener {

    private val activityResultLauncher =
      activity.registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
          MushroomUtil.sendReplaceKey(intent, result.data)
        }
        finish()
      }

    override fun onItemClick(adapter: AdapterView<*>, view: View, position: Int, id: Long) {
      MushroomUtil.clearProxy()
      val resolveInfo = adapter.getItemAtPosition(position) as ResolveInfo
      val activityInfo = resolveInfo.activityInfo
      activityResultLauncher.launch(
        MushroomUtil.createMushroomLaunchingIntent(
          activityInfo.packageName,
          activityInfo.name,
          MushroomUtil.getReplaceKey(activity.intent)
        )
      )
    }
  }

  override fun onCreate(savedInstance: Bundle?) {
    super.onCreate(savedInstance)
    setContentView(R.layout.mushroom_selection_dialog)
    val view = findViewById<ListView>(R.id.mushroom_selection_list_view)
    view.onItemClickListener = MushroomApplicationListClickListener(this)
  }

  override fun onResume() {
    super.onResume()

    // Reset application list for every onResume.
    // It is because this activity is launched in singleTask mode, so that the onCreate may be
    // skipped for second (or later) launching.
    val view = findViewById<ListView>(R.id.mushroom_selection_list_view)
    view.adapter = MushroomApplicationListAdapter(this)
  }
}
