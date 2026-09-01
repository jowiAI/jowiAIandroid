package ai.workis.jowi

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ai.workis.jowi.ui.JowiRoot
import ai.workis.jowi.ui.theme.JowiTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // apply the stored language on launch (TR-first product; device locale may differ)
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(Graph.language.value)
            )
        }
        enableEdgeToEdge()
        setContent {
            val appearance by Graph.appearance.collectAsState()
            val darkTheme = when (appearance) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            JowiTheme(darkTheme = darkTheme) {
                JowiRoot()
            }
        }
    }
}
