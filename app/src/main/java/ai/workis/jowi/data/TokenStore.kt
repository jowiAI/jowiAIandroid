package ai.workis.jowi.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Sole sanctioned token store (Android twin of iOS KeychainHelper).
 * Also keeps the raw user JSON — like iOS, the raw Django payload is the
 * single source of truth for identity.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "workis_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var userJson: String?
        get() = prefs.getString("user_json", null)
        set(value) = prefs.edit().putString("user_json", value).apply()

    fun clear() {
        prefs.edit().remove("token").remove("user_json").apply()
    }
}
