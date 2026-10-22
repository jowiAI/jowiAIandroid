package ai.workis.jowi

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ai.workis.jowi.data.AuthRepository
import ai.workis.jowi.data.PartnerStore
import ai.workis.jowi.data.PasskeyService
import ai.workis.jowi.data.PublicHomeStore
import ai.workis.jowi.data.WorkspacesStore
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
    lateinit var publicHome: PublicHomeStore
    lateinit var workspaces: WorkspacesStore

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
        publicHome = PublicHomeStore(api, prefs)
        workspaces = WorkspacesStore(api)
    }

    /**
     * The visitor key the homepage logs its questions under (`vk`): one per install,
     * never tied to an account — the console's Sorular groups a visitor's presses by it.
     */
    val visitorKey: String
        get() {
            prefs.getString(VISITOR_KEY, null)?.takeIf { it.isNotEmpty() }?.let { return it }
            val v = java.util.UUID.randomUUID().toString().lowercase()
            prefs.edit().putString(VISITOR_KEY, v).apply()
            return v
        }

    private const val VISITOR_KEY = "workis_vk"

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

    /**
     * A tap on a language picker is a DELIBERATE choice and is remembered as
     * one. It matters at sign-in: the login screen has no session to write to,
     * and Django's rule is that a stored preference — which may have been set
     * on the web — is never overwritten just because a device disagrees.
     */
    fun setLanguage(value: String) {
        language.value = value
        prefs.edit().putString("workis_lang", value).putBoolean(LANG_PICKED, true).apply()
        applyLocale(value)
    }

    /**
     * Rule (c): the account already holds a language, so this device TAKES it
     * instead of writing its own over it — that is how a choice made on the web
     * or on another phone travels here. Not a pick: nothing is sent back.
     */
    fun adoptLanguage(value: String) {
        if (value.isEmpty() || value == language.value) return
        language.value = value
        prefs.edit().putString("workis_lang", value).apply()
        applyLocale(value)
    }

    /** The per-app locale follows the language, wherever it was decided. */
    private fun applyLocale(value: String) {
        val set = Runnable { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(value)) }
        if (Looper.myLooper() == Looper.getMainLooper()) set.run() else Handler(Looper.getMainLooper()).post(set)
    }

    fun languageWasPicked(): Boolean = prefs.getBoolean(LANG_PICKED, false)

    fun clearLanguagePicked() { prefs.edit().remove(LANG_PICKED).apply() }

    private const val LANG_PICKED = "workis_lang_picked"
}

class JowiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        ai.workis.jowi.data.PushService.start() // register/ on every session while Bildirimler is on
    }
}
