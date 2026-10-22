package ai.workis.jowi

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import ai.workis.jowi.ui.theme.WorkisTheme
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ai.workis.jowi.data.PushService
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
        // a tapped push (ledger 45): the launcher intent carries the message's data; only
        // on a fresh launch here — a rotation re-creates the activity with the same intent
        if (savedInstanceState == null) PushService.tapped(intent?.extras)
        enableEdgeToEdge()
        setContent {
            val appearance by Graph.appearance.collectAsState()
            val darkTheme = when (appearance) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            JowiTheme(darkTheme = darkTheme) {
                // Edge-to-edge: the canvas paints under the system bars, the content stays
                // above the navigation bar (a 3-button bar hid the login tabs and the
                // consent row, 2026-09-14). Screens add statusBarsPadding/imePadding themselves.
                Box(Modifier.fillMaxSize().background(WorkisTheme.colors.canvas).navigationBarsPadding()) {
                    JowiRoot()
                }
            }
        }
    }

    /** singleTop: a tap while the app is alive lands here instead of re-creating the activity. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PushService.tapped(intent.extras)
    }
}
