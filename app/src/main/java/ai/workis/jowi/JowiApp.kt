package ai.workis.jowi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import ai.workis.jowi.data.AuthRepository
import ai.workis.jowi.data.PartnerStore
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

    // "system" | "light" | "dark" (iOS @AppStorage("workis_appearance"))
    val appearance = MutableStateFlow("system")

    // "tr" | "en" (iOS @AppStorage("workis_lang"))
    val language = MutableStateFlow("tr")

    fun init(context: Context) {
        prefs = context.getSharedPreferences("workis_prefs", Context.MODE_PRIVATE)
        appearance.value = prefs.getString("workis_appearance", "system") ?: "system"
        language.value = prefs.getString("workis_lang", "tr") ?: "tr"
        tokenStore = TokenStore(context)
        api = buildApi(
            tokenProvider = { tokenStore.token },
            languageProvider = { language.value },
        )
        auth = AuthRepository(api, tokenStore) { language.value }
        partner = PartnerStore(api)
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
