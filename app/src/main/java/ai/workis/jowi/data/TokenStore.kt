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

    /**
     * The encrypted file opened with the Keystore master key. On some devices (Samsung after
     * a Smart Switch / cloud restore, a reset Keystore) the file comes back but the key does
     * not, and `create` throws (`AEADBadTagException`, `KeyStoreException`) — on EVERY process
     * start, including the ones FCM makes in the background (2026-10-27, a tester's phone:
     * "workis sıklıkla çöküyor", the app gone before its first frame). So: one retry after
     * dropping the unreadable file (the session is lost, the person signs in again), and an
     * in-memory store as the last resort so the app opens at all. Never a plain-text file.
     */
    private val prefs: SharedPreferences = openEncrypted(context)
        ?: run { context.deleteSharedPreferences(FILE); context.deleteSharedPreferences(KEYSET); openEncrypted(context) }
        ?: MemoryPrefs()

    private fun openEncrypted(context: Context): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.onFailure { android.util.Log.w("TokenStore", "encrypted prefs unreadable, recovering", it) }.getOrNull()

    /** Process-lifetime fallback when the Keystore refuses twice: the app opens, the session does not survive. */
    private class MemoryPrefs : SharedPreferences {
        private val map = java.util.concurrent.ConcurrentHashMap<String, Any?>()
        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun contains(key: String) = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val puts = HashMap<String, Any?>(); private val removes = HashSet<String>(); private var clear = false
            override fun putString(key: String, value: String?) = apply { puts[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values }
            override fun putInt(key: String, value: Int) = apply { puts[key] = value }
            override fun putLong(key: String, value: Long) = apply { puts[key] = value }
            override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
            override fun remove(key: String) = apply { removes += key }
            override fun clear() = apply { clear = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clear) map.clear()
                removes.forEach { map.remove(it) }
                puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private companion object {
        const val FILE = "workis_secure"
        /** Where androidx.security keeps the (encrypted) keysets — restored without its Keystore key it is just as unreadable. */
        const val KEYSET = "__androidx_security_crypto_encrypted_prefs__"
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
