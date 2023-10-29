package sh.eliza.japaneseinput.preference

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import sh.eliza.japaneseinput.R

class MozcMainPreferenceActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.preferences_activity)

    if (savedInstanceState == null) {
      supportFragmentManager
        .beginTransaction()
        .replace(R.id.preferences, MozcPreferenceFragment(R.xml.pref_main))
        .commit()
    }
  }
}
