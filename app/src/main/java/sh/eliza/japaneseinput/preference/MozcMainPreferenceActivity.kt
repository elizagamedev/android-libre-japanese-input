package sh.eliza.japaneseinput.preference

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnCancelListener
import android.content.DialogInterface.OnClickListener
import android.content.DialogInterface.OnDismissListener
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.annotations.VisibleForTesting
import sh.eliza.japaneseinput.ApplicationInitializerFactory
import sh.eliza.japaneseinput.ApplicationInitializerFactory.ApplicationInitializer
import sh.eliza.japaneseinput.MozcUtil
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboardSpecification
import sh.eliza.japaneseinput.preference.KeyboardPreviewDrawable.BitmapCache
import sh.eliza.japaneseinput.preference.KeyboardPreviewDrawable.CacheReferenceKey
import sh.eliza.japaneseinput.util.LauncherIconManagerFactory

private fun createAlertDialog(
  context: Context,
  titleResourceId: Int,
  message: String,
  clickListener: DialogInterface.OnClickListener
) =
  AlertDialog.Builder(context).run {
    setTitle(titleResourceId)
    setMessage(message)
    setPositiveButton(R.string.pref_ime_alert_next, clickListener)
    setNegativeButton(R.string.pref_ime_alert_cancel, clickListener)
    create()
  }

class MozcMainPreferenceActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_preferences_activity)

    findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { finish() }

    if (savedInstanceState == null) {
      supportFragmentManager
        .beginTransaction()
        .replace(R.id.preferences, MozcMainPreferenceFragment())
        .commit()
    }
  }
}

class MozcMainPreferenceFragment() : PreferenceFragmentCompat() {

  private class ImeEnableDialogClickListener
  internal constructor(
    private val context: Context,
  ) : DialogInterface.OnClickListener {
    override fun onClick(dialog: DialogInterface, which: Int) {
      if (which == DialogInterface.BUTTON_POSITIVE) {
        // Open the keyboard & language setting page.
        val intent = Intent()
        intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        context.startActivity(intent)
      }
    }
  }

  private class ImeSwitchDialogListener
  internal constructor(
    private val context: Context,
  ) :
    DialogInterface.OnClickListener,
    DialogInterface.OnDismissListener,
    DialogInterface.OnCancelListener {

    // This variable should be reset every dialog shown timing, but it is not supported
    // on API level 7. So, instead, we check all possible finishing pass.
    // TODO(hidehiko): use OnShownListener after we upgrade the API level.
    private var showInputMethodPicker = false

    override fun onClick(dialog: DialogInterface, which: Int) {
      showInputMethodPicker = which == DialogInterface.BUTTON_POSITIVE
    }

    override fun onCancel(arg0: DialogInterface) {
      showInputMethodPicker = false
    }

    override fun onDismiss(dialog: DialogInterface) {
      if (showInputMethodPicker) {
        // Send an input method picker shown event after the dismissing of this dialog is
        // completed. Otherwise, the new dialog will be dismissed, too.
        showInputMethodPicker = false
        MozcUtil.requestShowInputMethodPicker(context)
      }
    }
  }

  @VisibleForTesting lateinit var imeEnableDialog: AlertDialog
  @VisibleForTesting lateinit var imeSwitchDialog: AlertDialog

  private val sharedPreferenceChangeListener =
    object : OnSharedPreferenceChangeListener {
      override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (PreferenceUtil.PREF_LAUNCHER_ICON_VISIBILITY_KEY == key) {
          LauncherIconManagerFactory.getDefaultInstance().updateLauncherIconVisibility(context)
        }
      }
    }

  // Cache the SharedPreferences instance, otherwise PreferenceManager.getDefaultSharedPreferences
  // would try to read it from the storage every time.
  private lateinit var sharedPreferences: SharedPreferences
  private val cacheKey = CacheReferenceKey()

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.pref_main, rootKey)
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    initializeAlertDialog(context)
  }

  override fun onResume() {
    super.onResume()
    sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

    HardwareKeyboardSpecification.maybeSetDetectedHardwareKeyMap(
      sharedPreferences,
      getResources().getConfiguration(),
      false
    )

    onPostResumeInternal(ApplicationInitializerFactory.createInstance(context!!))
  }

  @VisibleForTesting
  fun onPostResumeInternal(initializer: ApplicationInitializer) {
    val context = context!!
    initializer.initialize(
      MozcUtil.getAbiIndependentVersionCode(context),
      LauncherIconManagerFactory.getDefaultInstance(),
      PreferenceUtil.defaultPreferenceManagerStatic
    )
  }

  override fun onPause() {
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    super.onPause()
  }

  // Note: when an activity A starts another activity B, the order of their onStart/onStop
  // invocation sequence is:
  //   B's onStart -> A's onStop.
  // (of course, some other methods, such as onPause or onResume etc, are also invoked accordingly).
  // Thus, we can keep and pass the cached keyboard preview bitmaps by reference counting
  // in onStart/onStop methods from the single-pane preference top page to
  // software keyboard advanced settings, and vice versa.
  // Note that if a user tries to move an activity other than Mozc's preference even from
  // software keyboard advanced settings preference page, the counter will simply get 0, and all the
  // cached bitmap resources will be released.
  override fun onStart() {
    super.onStart()
    // Tell bitmap cache that this Activity uses it.
    BitmapCache.instance.addReference(cacheKey)
  }

  override fun onStop() {
    // Release all the bitmap cache (if necessary) to utilize the used memory.
    BitmapCache.instance.removeReference(cacheKey)
    super.onStop()
  }

  private fun initializeAlertDialog(context: Context) {
    val resource = getResources()
    imeEnableDialog =
      createAlertDialog(
        context,
        R.string.pref_ime_enable_alert_title,
        resource.getString(
          R.string.pref_ime_enable_alert_message,
          resource.getString(R.string.app_name)
        ),
        ImeEnableDialogClickListener(context)
      )

    val listener = ImeSwitchDialogListener(context)
    imeSwitchDialog =
      createAlertDialog(
          context,
          R.string.pref_ime_switch_alert_title,
          resource.getString(
            R.string.pref_ime_switch_alert_message,
            resource.getString(R.string.app_name)
          ),
          listener
        )
        .apply {
          setOnCancelListener(listener)
          setOnDismissListener(listener)
        }
  }
}
