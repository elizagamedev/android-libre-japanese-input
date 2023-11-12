package sh.eliza.japaneseinput.preference

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import sh.eliza.japaneseinput.R

/** Advanced keyboard settings. */
class MozcSoftwareKeyboardAdvancedPreferenceActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.keyboard_preferences_activity)

    findViewById<MaterialToolbar>(R.id.topAppBar).setNavigationOnClickListener { finish() }

    if (savedInstanceState == null) {
      supportFragmentManager
        .beginTransaction()
        .replace(R.id.preferences, MozcSoftwareKeyboardAdvancedPreferenceFragment())
        .commit()
    }
  }
}

class MozcSoftwareKeyboardAdvancedPreferenceFragment : PreferenceFragmentCompat() {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.pref_software_keyboard_advanced, rootKey)
  }

  override fun onPause() {
    super.onPause()
    // Probably, it'll be slightly confusing if the software keyboard advanced settings preference
    // is shown when a user restart the task. So, finish the activity here.
    activity?.finish()
  }
}
