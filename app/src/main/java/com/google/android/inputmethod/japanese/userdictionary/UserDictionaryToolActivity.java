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

package org.mozc.android.inputmethod.japanese.userdictionary;

import org.mozc.android.inputmethod.japanese.MozcLog;
import org.mozc.android.inputmethod.japanese.MozcUtil;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.Entry;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionary.PosType;
import org.mozc.android.inputmethod.japanese.protobuf.ProtoUserDictionaryStorage.UserDictionaryCommandStatus.Status;
import org.mozc.android.inputmethod.japanese.R;
import org.mozc.android.inputmethod.japanese.session.SessionExecutor;
import org.mozc.android.inputmethod.japanese.session.SessionHandlerFactory;
import org.mozc.android.inputmethod.japanese.userdictionary.UserDictionaryUtil.DictionaryNameDialog;
import org.mozc.android.inputmethod.japanese.userdictionary.UserDictionaryUtil.DictionaryNameDialogListener;
import org.mozc.android.inputmethod.japanese.userdictionary.UserDictionaryUtil.WordRegisterDialog;
import org.mozc.android.inputmethod.japanese.userdictionary.UserDictionaryUtil.WordRegisterDialogListener;
import org.mozc.android.inputmethod.japanese.util.ZipFileUtil;
import com.google.common.base.Optional;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

/**
 * Activity implementation for user dictionary tool.
 *
 */
public class UserDictionaryToolActivity extends Activity {

  /**
   * A ListAdapter for the main entry list.
   */
  private class EntryListAdapter extends ArrayAdapter<Entry> {
    public EntryListAdapter() {
      super(UserDictionaryToolActivity.this, 0, model.getEntryList());
    }

    @Override
    public View getView(final int position, @Nullable View convertView, ViewGroup parent) {
      // We'll use customized view for each entry.
      // The view should have
      // - Check box (to indicate if the entry is selected or not for deletion)
      // - Word
      // - Reading
      // - POS name
      if (convertView == null) {
        convertView = LayoutInflater.from(getContext()).inflate(
            R.layout.user_dictionary_tool_entry_list_view, null);
      }

      final ListView entryListView = ListView.class.cast(parent);
      Entry entry = getItem(position);
      TextView.class.cast(convertView.findViewById(R.id.user_dictionary_tool_entry_list_reading))
          .setText(entry.getKey());
      TextView.class.cast(convertView.findViewById(R.id.user_dictionary_tool_entry_list_word))
          .setText(entry.getValue());
      TextView.class.cast(convertView.findViewById(R.id.user_dictionary_tool_entry_list_pos))
          .setText(UserDictionaryUtil.getPosStringResourceId(entry.getPos()));
      CheckBox checkBox = CheckBox.class.cast(
          convertView.findViewById(R.id.user_dictionary_tool_entry_list_check));

      // Before set the "checked" state, we need to remove OnCheckedChangeListener,
      // because convertView *may* be an instance, which is previously used, i.e. which is
      // connected to another entry. Thus, without the removing, old entry's state would be
      // updated unexpectedly.
      checkBox.setOnCheckedChangeListener(null);
      checkBox.setChecked(entryListView.isItemChecked(position));
      checkBox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          entryListView.setItemChecked(position, isChecked);
        }
      });

      // Once the entry is tapped, it is a trigger of editing the entry.
      convertView.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          model.setEditTargetIndex(position);
          showDialogInternal(EDIT_ENTRY_DIALOG_ID);
        }
      });

      return convertView;
    }
  }

  // IDs of dialogs which are related to this Activity.
  private static final int ADD_ENTRY_DIALOG_ID = 0;
  private static final int EDIT_ENTRY_DIALOG_ID = 1;
  private static final int CREATE_DICTIONARY_DIALOG_ID = 2;
  private static final int RENAME_DICTIONARY_DIALOG_ID = 3;
  private static final int ZIP_FILE_SELECTION_DIALOG_ID = 4;
  private static final int IMPORT_DICTIONARY_SELECTION_DIALOG_ID = 5;

  private UserDictionaryToolModel model;
  private ToastManager toastManager;

  @Override
  protected void onCreate(Bundle savedInstance) {
    super.onCreate(savedInstance);
    toastManager = new ToastManager(this);

    // Initialize model.
    Context applicationContext = getApplicationContext();
    model = new UserDictionaryToolModel(
        SessionExecutor.getInstanceInitializedIfNecessary(
            new SessionHandlerFactory(applicationContext), applicationContext));
    model.createSession();

    // Initialize views.
    setContentView(R.layout.user_dictionary_tool_view);
    initializeDictionaryNameSpinner();
    initializeEntryListView();

    // Set import source uri for data importing.
    model.setImportUri(UserDictionaryUtil.getImportUri(getIntent()));
  }

  private void initializeDictionaryNameSpinner() {
    Spinner dictionaryNameSpinner = getDictionaryNameSpinner();
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
        this, android.R.layout.simple_spinner_item, model.getDictionaryNameList());
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    dictionaryNameSpinner.setAdapter(adapter);
    dictionaryNameSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // When the selected dictionary is changed, we should change the main entry list view
        // as well.
        model.setSelectedDictionaryByIndex(position);
        updateEntryList();
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
        // Do nothing.
      }
    });
  }

  private void initializeEntryListView() {
    ListView entryListView = getEntryList();
    // We use the ListView's "choicable" function as the backend of selected entries for deletion.
    entryListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    EntryListAdapter adapter = new EntryListAdapter();
    entryListView.setAdapter(adapter);
  }

  @Override
  protected void onDestroy() {
    // To release pending resources.
    model.resetImportState();
    model.deleteSession();
    model = null;
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    String defaultDictionaryName =
        getResources().getText(R.string.user_dictionary_tool_default_dictionary_name).toString();
    toastManager.maybeShowMessageShortly(model.resumeSession(defaultDictionaryName));
    updateDictionaryNameSpinner();
    updateEntryList();
  }

  @Override
  protected void onPostResume() {
    super.onPostResume();

    // Import handling.
    handleImportData();
    maybeShowImportDictionarySelectionDialog();
  }

  private void handleImportData() {
    if (model.getImportData() != null) {
      // The data has been read from the stream.
      return;
    }

    Uri importUri = model.getImportUri();
    if (importUri == null) {
      // This is not an activity for importing operation,
      // or the importing operation has already been done.
      return;
    }

    if (!"file".equals(importUri.getScheme())) {
      // Not a file.
      toastManager.showMessageShortly(
          R.string.user_dictionary_tool_error_import_source_invalid_scheme);
      return;
    }

    // Need to read data from the file.
    if ("application/zip".equals(getIntent().getType())) {
      handleZipImportData(importUri.getPath());
    } else {
      handleTextImportData(importUri.getPath());
    }
  }

  private void handleTextImportData(String path) {
    try {
      model.setImportData(UserDictionaryUtil.readFromFile(path));
    } catch (IOException e) {
      // Failed to read the file, or failed to detect the encoding.
      MozcLog.e("Failed to read data.", e);
      toastManager.showMessageShortly(
          R.string.user_dictionary_tool_error_import_cannot_read_import_source);
      model.resetImportState();
    }
  }

  private void handleZipImportData(String path) {
    ZipFile zipFile = null;
    try {
      zipFile = new ZipFile(path);
      int size = zipFile.size();
      if (size == 0) {
        // Empty zip file.
        toastManager.showMessageShortly(R.string.user_dictionary_tool_error_import_no_zip_entry);
        model.resetImportState();
        return;
      }

      if (size == 1) {
        // The zip file has only one entry, so we should read the file without asking user to
        // select an entry in the zip file.
        model.setImportData(
            UserDictionaryUtil.toStringWithEncodingDetection(
                ZipFileUtil.getBuffer(zipFile, zipFile.entries().nextElement().getName())));
        return;
      }

      // Delegate the ownership of zipFile to model.
      model.setZipFile(zipFile);
      zipFile = null;

      // The zip file has more than one entry. So ask the user to select an appropriate entry
      // based on dialog.
      showDialogInternal(ZIP_FILE_SELECTION_DIALOG_ID);
    } catch (IOException e) {
      // Failed to open or manipulate the zip file.
      MozcLog.e("Failed to read zip", e);
      toastManager.showMessageShortly(
          R.string.user_dictionary_tool_error_import_cannot_read_import_source);
      model.resetImportState();
    } catch (OutOfMemoryError e) {
      // The zip file being imported is too large. Recovering (if possible).
      toastManager.showMessageShortly(
              R.string.user_dictionary_tool_error_import_too_large_zip_entry);
      model.resetImportState();
    } finally {
      if (zipFile != null) {
        MozcUtil.closeIgnoringIOException(zipFile);
      }
    }
  }

  private void maybeShowImportDictionarySelectionDialog() {
    if (model.getImportData() != null) {
      // Show a dialog to specify import dictionary.
      showDialogInternal(IMPORT_DICTIONARY_SELECTION_DIALOG_ID);
    }
  }

  // Just redirect to the showDialog in order to suppress warnings.
  @SuppressWarnings("deprecation")
  private void showDialogInternal(int id) {
    super.showDialog(id);
  }

  @Override
  protected void onPause() {
    toastManager.maybeShowMessageShortly(model.pauseSession());
    super.onPause();
  }

  // Menu implementation.
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.user_dictionary_tool_menu, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.user_dictionary_tool_menu_undo).setEnabled(
        model.checkUndoability() == Status.USER_DICTIONARY_COMMAND_SUCCESS);
    return super.onPrepareOptionsMenu(menu);
  }

  // TODO(hidehiko): Due to backward compatibility, we need to use deprecated APIs.
  //   Replace them when we get rid of support for the older devices.
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.user_dictionary_tool_menu_add_entry) {
        maybeShowAddEntryDialog();
        return true;
    }
    if (id == R.id.user_dictionary_tool_menu_delete_entry) {
      maybeDeleteEntry();
      return true;
    }
    if (id == R.id.user_dictionary_tool_menu_undo) {
      runUndo();
      return true;
    }
    if (id == R.id.user_dictionary_tool_menu_create_dictionary) {
      maybeShowCreateDictionaryDialog();
      return true;
    }
    if (id == R.id.user_dictionary_tool_menu_rename_dictionary) {
      showDialogInternal(RENAME_DICTIONARY_DIALOG_ID);
      return true;
    }
    if (id == R.id.user_dictionary_tool_menu_delete_dictionary) {
      deleteDictionary();
      return true;
    }
    if (id == R.id.user_dictionary_tool_menu_export_dictionary) {
      startActivityForDictionaryExport();
      return true;
    }
    return false;
  }

  private void maybeShowAddEntryDialog() {
    Status status = model.checkNewEntryAvailability();
    toastManager.maybeShowMessageShortly(status);
    if (status != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      return;
    }

    showDialogInternal(ADD_ENTRY_DIALOG_ID);
  }

  private void maybeDeleteEntry() {
    SparseBooleanArray selectedItemList = getEntryList().getCheckedItemPositions();
    List<Integer> indexList = new ArrayList<Integer>();
    for (int i = 0; i < selectedItemList.size(); ++i) {
      if (selectedItemList.valueAt(i)) {
        indexList.add(selectedItemList.keyAt(i));
      }
    }

    if (indexList.isEmpty()) {
      // Show the delete confirmation dialog iff at least one entry is selected.
      toastManager.showMessageShortly(
          R.string.user_dictionary_tool_error_delete_entries_without_check);
      selectedItemList.clear();
      return;
    }

    Status status = model.deleteEntry(indexList);
    toastManager.maybeShowMessageShortly(status);
    if (status == Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      toastManager.showMessageShortly(R.string.user_dictionary_tool_delete_done_message);
      selectedItemList.clear();
      updateEntryList();
    }
  }

  private void runUndo() {
    Status status = model.undo();
    toastManager.maybeShowMessageShortly(status);
    if (status == Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      toastManager.showMessageShortly(R.string.user_dictionary_tool_undo_done_message);
      // The update by undo may change the list of entries in the current dictionary.
      // So invalidate checked items.
      getEntryList().getCheckedItemPositions().clear();
    }
    updateDictionaryNameSpinner();
    updateEntryList();
  }

  private void maybeShowCreateDictionaryDialog() {
    Status status = model.checkNewDictionaryAvailability();
    toastManager.maybeShowMessageShortly(status);
    if (status != Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      // If we cannot add a new dictionary now, we shouldn't show the dialog.
      return;
    }

    showDialogInternal(CREATE_DICTIONARY_DIALOG_ID);
  }

  private void deleteDictionary() {
    Status status = model.deleteSelectedDictionary();
    toastManager.maybeShowMessageShortly(status);
    if (status == Status.USER_DICTIONARY_COMMAND_SUCCESS) {
      toastManager.showMessageShortly(R.string.user_dictionary_tool_delete_done_message);
    }
    updateDictionaryNameSpinner();
    updateEntryList();
  }

  private void startActivityForDictionaryExport() {
    int index = model.getSelectedDictionaryIndex();
    String dictionaryName = model.getDictionaryNameList().get(index);
    Context context = getApplicationContext();

    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.setType("application/zip");
    intent.putExtra(Intent.EXTRA_SUBJECT, dictionaryName + ".zip");
    if (getPackageManager().queryIntentActivities(
            intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
      toastManager.showMessageShortly(
          R.string.user_dictionary_tool_error_export_no_exportable_applications);
      return;
    }

    // "export" is used as filename in the .zip file.
    // Ideally we want to pass dictionaryName instead but java.util.zip.ZipOutputStream cannot
    // handle non-ASCII filename correctly.
    // If we become able to switch to org.apache.tools.zip.ZipOutputStream, which accepts non-ASCII,
    // get rid of this workaround.
    Optional<File> exportFile = model.createExportFile(
        context.getResources(), "export",
        MozcUtil.getUserDictionaryExportTempDirectory(context));
    if (!exportFile.isPresent()) {
      toastManager.showMessageShortly(R.string.user_dictionary_tool_error_export_failed_to_export);
      return;
    }

    intent.putExtra(Intent.EXTRA_STREAM, Uri.parse(
        "content://"
            + context.getResources().getString(R.string.user_dictionary_tool_export_provider_name)
            + "/"
            + exportFile.get().getAbsolutePath()));
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivity(intent);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case ADD_ENTRY_DIALOG_ID:
        return UserDictionaryUtil.createWordRegisterDialog(
            this,
            R.string.user_dictionary_tool_add_entry_dialog_title,
            new WordRegisterDialogListener() {
              @Override
              public Status onPositiveButtonClicked(String word, String reading, PosType pos) {
                Status status = model.addEntry(word, reading, pos);
                if (status == Status.USER_DICTIONARY_COMMAND_SUCCESS) {
                  updateEntryList();
                }
                return status;
              }
            }, toastManager);

      case EDIT_ENTRY_DIALOG_ID:
        return UserDictionaryUtil.createWordRegisterDialog(
            this,
            R.string.user_dictionary_tool_add_entry_dialog_title,
            new WordRegisterDialogListener() {
              @Override
              public Status onPositiveButtonClicked(String word, String reading, PosType pos) {
                Status status = model.editEntry(word, reading, pos);
                if (status == Status.USER_DICTIONARY_COMMAND_SUCCESS) {
                  updateEntryList();
                }
                return status;
              }
            }, toastManager);

      case CREATE_DICTIONARY_DIALOG_ID:
        return UserDictionaryUtil.createDictionaryNameDialog(
            this,
            R.string.user_dictionary_tool_create_dictionary_dialog_title,
            new DictionaryNameDialogListener() {
              @Override
              public Status onPositiveButtonClicked(String dictionaryName) {
                Status status = model.createDictionary(dictionaryName);
                if (status == Status.USER_DICTIONARY_COMMAND_SUCCESS) {
                  updateDictionaryNameSpinner();
                  updateEntryList();
                }
                return status;
              }
            }, toastManager);

      case RENAME_DICTIONARY_DIALOG_ID:
        return UserDictionaryUtil.createDictionaryNameDialog(
            this,
            R.string.user_dictionary_tool_rename_dictionary_dialog_title,
            new DictionaryNameDialogListener() {
              @Override
              public Status onPositiveButtonClicked(String dictionaryName) {
                Status status = model.renameSelectedDictionary(dictionaryName);
                if (status == Status.USER_DICTIONARY_COMMAND_SUCCESS) {
                  updateDictionaryNameSpinner();
                }
                return status;
              }
            }, toastManager);

      case ZIP_FILE_SELECTION_DIALOG_ID:
        return UserDictionaryUtil.createZipFileSelectionDialog(
            this,
            R.string.user_dictionary_tool_zip_file_selection_dialog_title,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                Spinner spinner = Spinner.class.cast(
                    Dialog.class.cast(dialog).findViewById(
                        R.id.user_dictionary_tool_simple_spinner_dialog_spinner));

                ZipFile zipFile = model.releaseZipFile();
                try {
                  model.setImportData(
                      UserDictionaryUtil.toStringWithEncodingDetection(
                          ZipFileUtil.getBuffer(zipFile, spinner.getSelectedItem().toString())));
                } catch (IOException e) {
                  toastManager.showMessageShortly(
                      R.string.user_dictionary_tool_error_import_cannot_read_import_source);
                  model.resetImportState();
                  return;
                } catch (OutOfMemoryError e) {
                  toastManager.showMessageShortly(
                      R.string.user_dictionary_tool_error_import_too_large_zip_entry);
                  model.resetImportState();
                  return;
                } finally {
                  MozcUtil.closeIgnoringIOException(zipFile);
                }
                maybeShowImportDictionarySelectionDialog();
              }
            },
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                model.resetImportState();
              }
            },
            new DialogInterface.OnCancelListener() {
              @Override
              public void onCancel(DialogInterface dialog) {
                model.resetImportState();
              }
            });

      case IMPORT_DICTIONARY_SELECTION_DIALOG_ID:
        return UserDictionaryUtil.createImportDictionarySelectionDialog(
            this,
            R.string.user_dictionary_tool_import_dictionary_selection_dialog_title,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                Spinner spinner = Spinner.class.cast(
                    Dialog.class.cast(dialog).findViewById(
                        R.id.user_dictionary_tool_simple_spinner_dialog_spinner));
                // This is the trick to specify dictionary index.
                // The entry list of the spinner has "new dictionary" followed by
                // a list of dictionary names.
                // So, the actual dictionary index is the position - 1.
                // Note that the way to tell importData to create new dictionary is setting
                // -1 to the argument.
                toastManager.maybeShowMessageShortly(
                    model.importData(spinner.getSelectedItemPosition() - 1));
                updateDictionaryNameSpinner();
                updateEntryList();
              }
            },
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                model.resetImportState();
              }
            },
            new DialogInterface.OnCancelListener() {
              @Override
              public void onCancel(DialogInterface dialog) {
                model.resetImportState();
              }
            });
    }

    MozcLog.e("Unknown Dialog ID: " + id);
    return null;
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    super.onPrepareDialog(id, dialog);
    switch (id) {
      case ADD_ENTRY_DIALOG_ID:
        WordRegisterDialog.class.cast(dialog).setEntry(Entry.newBuilder()
            .setPos(PosType.NOUN)
            .buildPartial());
        UserDictionaryUtil.showInputMethod(dialog);
        break;
      case EDIT_ENTRY_DIALOG_ID:
        WordRegisterDialog.class.cast(dialog).setEntry(model.getEditTargetEntry());
        UserDictionaryUtil.showInputMethod(dialog);
        break;

      case CREATE_DICTIONARY_DIALOG_ID:
        DictionaryNameDialog.class.cast(dialog).setDictionaryName("");
        UserDictionaryUtil.showInputMethod(dialog);
        break;

      case RENAME_DICTIONARY_DIALOG_ID:
        DictionaryNameDialog.class.cast(dialog).setDictionaryName(
            model.getSelectedDictionaryName());
        UserDictionaryUtil.showInputMethod(dialog);
        break;

      case ZIP_FILE_SELECTION_DIALOG_ID: {
        ZipFile zipFile = model.getZipFile();
        if (zipFile == null) {
          throw new AssertionError();
        }
        List<String> pathList = new ArrayList<String>(zipFile.size());
        for (Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
             enumeration.hasMoreElements(); ) {
          pathList.add(enumeration.nextElement().getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, pathList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner.class.cast(
            dialog.findViewById(R.id.user_dictionary_tool_simple_spinner_dialog_spinner))
            .setAdapter(adapter);
        break;
      }

      case IMPORT_DICTIONARY_SELECTION_DIALOG_ID: {
        List<String> dictionaryNameList = model.getDictionaryNameList();
        // One more element for "new dictionary".
        List<String> dictionarySelectionItemList =
            new ArrayList<String>(dictionaryNameList.size() + 1);
        int newDictionaryResourceId =
            R.string.user_dictionary_tool_import_dictionary_selection_dialog_new_dictionary;
        dictionarySelectionItemList.add(getText(newDictionaryResourceId).toString());
        dictionarySelectionItemList.addAll(dictionaryNameList);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_item, dictionarySelectionItemList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner.class.cast(
            dialog.findViewById(R.id.user_dictionary_tool_simple_spinner_dialog_spinner))
            .setAdapter(adapter);
        break;
      }

      default:
        MozcLog.e("Unknown Dialog ID: " + id);
    }
  }

  /**
   * Updates the contents and the selected item of dictionary name spinner.
   */
  private void updateDictionaryNameSpinner() {
    Spinner dictionaryNameSpinner = getDictionaryNameSpinner();
    dictionaryNameSpinner.setSelection(model.getSelectedDictionaryIndex());
    ArrayAdapter.class.cast(dictionaryNameSpinner.getAdapter()).notifyDataSetChanged();
  }

  /**
   * Updates the contents in the entry list view.
   */
  private void updateEntryList() {
    ListView entryList = getEntryList();
    ArrayAdapter.class.cast(entryList.getAdapter()).notifyDataSetChanged();
  }

  private Spinner getDictionaryNameSpinner() {
    return Spinner.class.cast(findViewById(R.id.user_dictionary_tool_dictionary_name_spinner));
  }

  private ListView getEntryList() {
    return ListView.class.cast(findViewById(R.id.user_dictionary_tool_entry_list));
  }
}
