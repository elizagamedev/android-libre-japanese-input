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
package sh.eliza.japaneseinput.ui

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.content.DialogInterface.OnDismissListener
import android.content.DialogInterface.OnShowListener
import android.os.IBinder
import android.view.InflateException
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.common.base.Optional
import java.util.Collections
import sh.eliza.japaneseinput.MozcLog
import sh.eliza.japaneseinput.MozcUtil
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.mushroom.MushroomUtil

/** UI component implementation for the Mozc's menu dialog. */
class MenuDialog(context: Context, listener: Optional<MenuDialogListener>) {
  /** Listener interface for the menu dialog. */
  interface MenuDialogListener {
    /** Invoked when the dialog is shown. */
    fun onShow(context: Context?)

    /** Invoked when the dialog is dismissed. */
    fun onDismiss(context: Context?)

    /** Invoked when "Show Input Method Picker" item is selected. */
    fun onShowInputMethodPickerSelected(context: Context?)

    /** Invoked when "Launch Preference Activity" item is selected. */
    fun onLaunchPreferenceActivitySelected(context: Context?)

    /** Invoked when "Launch Mushroom" item is selected. */
    fun onShowMushroomSelectionDialogSelected(context: Context?)
  }

  /** Internal implementation of callback invocation dispatching. */
  private class MenuDialogListenerHandler(
    private val context: Context,
    /** Table to convert from a menu item index to a string resource id. */
    private val indexToIdTable: IntArray,
    private val listener: Optional<MenuDialogListener>
  ) : DialogInterface.OnClickListener, DialogInterface.OnDismissListener, OnShowListener {
    override fun onShow(dialog: DialogInterface) {
      if (!listener.isPresent) {
        return
      }
      listener.get().onShow(context)
    }

    override fun onDismiss(dialog: DialogInterface) {
      if (!listener.isPresent) {
        return
      }
      listener.get().onDismiss(context)
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
      if (!listener.isPresent || indexToIdTable.size <= which) {
        return
      }
      when (indexToIdTable[which]) {
        R.string.menu_item_input_method -> listener.get().onShowInputMethodPickerSelected(context)
        R.string.menu_item_preferences -> listener.get().onLaunchPreferenceActivitySelected(context)
        R.string.menu_item_mushroom -> listener.get().onShowMushroomSelectionDialogSelected(context)
        else -> MozcLog.e("Unknown menu index: $which")
      }
    }
  }

  private val dialog: AlertDialog?
  private val listenerHandler: MenuDialogListenerHandler

  init {
    val resources = context.resources
    val appName = resources.getString(R.string.app_name)

    // R.string.menu_item_* resources needs to be formatted.
    val menuItemIds = getEnabledMenuIds(context)
    val menuNum = menuItemIds.size
    val menuTextList = arrayOfNulls<String>(menuNum)
    val indexToIdTable = IntArray(menuNum)
    for (i in 0 until menuNum) {
      val id = menuItemIds[i]
      menuTextList[i] = resources.getString(id, appName)
      indexToIdTable[i] = id
    }
    listenerHandler = MenuDialogListenerHandler(context, indexToIdTable, listener)
    dialog =
      try {
        AlertDialog.Builder(context)
          .setTitle(R.string.menu_dialog_title)
          .setItems(menuTextList, listenerHandler)
          .create()
          .apply {
            window?.run {
              addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
              attributes.dimAmount = 0.60f
            }
            setOnDismissListener(listenerHandler)
            setOnShowListener(listenerHandler)
          }
      } catch (e: InflateException) {
        // Ignore the exception.
        null
      }
  }

  fun show() {
    dialog?.show()
  }

  fun dismiss() {
    dialog?.dismiss()
  }

  fun setWindowToken(windowToken: IBinder) {
    dialog?.let { MozcUtil.setWindowToken(windowToken, it) }
  }

  companion object {
    @JvmStatic
    private fun getEnabledMenuIds(context: Context): List<Int> {
      // "Mushroom" item is enabled only when Mushroom-aware applications are available.
      val packageManager = context.packageManager
      val isMushroomEnabled = !MushroomUtil.getMushroomApplicationList(packageManager).isEmpty()
      val menuItemIds =
        mutableListOf(
          R.string.menu_item_input_method,
          R.string.menu_item_preferences,
        )
      if (isMushroomEnabled) {
        menuItemIds.add(R.string.menu_item_mushroom)
      }
      return Collections.unmodifiableList(menuItemIds)
    }
  }
}
