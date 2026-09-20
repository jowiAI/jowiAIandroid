package ai.workis.jowi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import ai.workis.jowi.data.AuthRepository
import ai.workis.jowi.data.PartnerStore
import ai.workis.jowi.data.PasskeyService
import ai.workis.jowi.data.TokenStore
import ai.workis.jowi.data.WorkisApi
import ai.workis.jowi.data.buildApi
import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight service locator — mirrors the iOS singleton services. */
object Graph {
    lateinit var prefs: SharedPreferences
    lateinit var tokenStore: TokenStore
    lateinit var api: WorkisApi
    lateinit var auth: AuthRepository
    lateinit var partner: PartnerStore
    lateinit var passkeys: PasskeyService

    // "system" | "light" | "dark" (iOS @AppStorage("workis_appearance"))
    val appearance = MutableStateFlow("system")

    // "tr" | "en" (iOS @AppStorage("workis_lang"))
    val language = MutableStateFlow("tr")

    /**
     * What the sign-in history should call this device. Without it the server
     * falls back to the User-Agent, which reads like a library version string.
     */
    var client: String = "android"
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences("workis_prefs", Context.MODE_PRIVATE)
        appearance.value = prefs.getString("workis_appearance", "system") ?: "system"
        language.value = prefs.getString("workis_lang", "tr") ?: "tr"
        client = describeClient(context)
        tokenStore = TokenStore(context)
        api = buildApi(
            tokenProvider = { tokenStore.token },
            languageProvider = { language.value },
            clientProvider = { client },
        )
        auth = AuthRepository(api, tokenStore) { language.value }
        partner = PartnerStore(api)
        passkeys = PasskeyService(api) { language.value }
    }

    /** "android; Pixel 9; Android 16; app 1.0 (2)" */
    private fun describeClient(context: Context): String {
        val app = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
            "app ${info.versionName} ($code)"
        }.getOrDefault("app")
        return listOf("android", Build.MODEL ?: "", "Android ${Build.VERSION.RELEASE}", app)
            .filter { it.isNotBlank() }
            .joinToString("; ")
    }

    fun setAppearance(value: String) {
        appearance.value = value
        prefs.edit().putString("workis_appearance", value).apply()
    }

    fun setLanguage(value: String) {
        language.value = value
        prefs.edit().putString("workis_lang", value).apply()
    }
}

class JowiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
