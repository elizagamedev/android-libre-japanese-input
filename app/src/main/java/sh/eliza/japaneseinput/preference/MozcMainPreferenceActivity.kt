package sh.eliza.japaneseinput.preference

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import sh.eliza.japaneseinput.ApplicationInitializerFactory
import sh.eliza.japaneseinput.MozcUtil
import sh.eliza.japaneseinput.R
import sh.eliza.japaneseinput.hardwarekeyboard.HardwareKeyboardSpecification
import sh.eliza.japaneseinput.preference.KeyboardPreviewDrawable.BitmapCache
import sh.eliza.japaneseinput.preference.KeyboardPreviewDrawable.CacheReferenceKey
import sh.eliza.japaneseinput.util.LauncherIconManagerFactory

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
  private val sharedPreferenceChangeListener = OnSharedPreferenceChangeListener { _, key ->
    if (PreferenceUtil.PREF_LAUNCHER_ICON_VISIBILITY_KEY == key) {
      LauncherIconManagerFactory.getDefaultInstance().updateLauncherIconVisibility(context)
    }
  }

  // Cache the SharedPreferences instance, otherwise PreferenceManager.getDefaultSharedPreferences
  // would try to read it from the storage every time.
  private lateinit var sharedPreferences: SharedPreferences
  private lateinit var enableKeyboardPreference: SwitchPreferenceCompat
  private lateinit var inputMethodManager: InputMethodManager
  private val cacheKey = CacheReferenceKey()

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.pref_main, rootKey)

    enableKeyboardPreference =
      findPreference<SwitchPreferenceCompat>("pref_enable_keyboard_key")!!.apply {
        onPreferenceClickListener = OnPreferenceClickListener {
          context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
          true
        }
        onPreferenceChangeListener = OnPreferenceChangeListener { _, _ -> false }
      }

    findPreference<Preference>("pref_about_version")!!.setSummary(MozcUtil.getVersionName(context))
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    inputMethodManager =
      context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  }

  override fun onResume() {
    super.onResume()
    sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

    HardwareKeyboardSpecification.maybeSetDetectedHardwareKeyMap(
      sharedPreferences,
      getResources().getConfiguration(),
      false
    )

    val context = requireContext()
    ApplicationInitializerFactory.createInstance(requireContext())
      .initialize(
        MozcUtil.getAbiIndependentVersionCode(context),
        LauncherIconManagerFactory.getDefaultInstance(),
        PreferenceUtil.defaultPreferenceManagerStatic
      )

    enableKeyboardPreference.setChecked(isInputMethodEnabled())
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

  private fun isInputMethodEnabled(): Boolean {
    val packageName = context?.packageName ?: return false
    return inputMethodManager.enabledInputMethodList.any { it.serviceName.startsWith(packageName) }
  }
}
