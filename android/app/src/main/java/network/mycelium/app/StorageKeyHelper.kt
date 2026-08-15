// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

private const val PREFS = "mycelium_secure"
private const val KEY_STORAGE_HEX = "storage_key_hex"

/**
 * 32-byte master key for Rust at-rest encryption, stored in EncryptedSharedPreferences.
 */
object StorageKeyHelper {
    fun getOrCreateStorageKeyHex(context: Context): String {
        val prefs = encryptedPrefs(context)
        prefs.getString(KEY_STORAGE_HEX, null)?.let { return it }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { b -> "%02x".format(b) }
        prefs.edit().putString(KEY_STORAGE_HEX, hex).apply()
        Log.i("Mycelium", "generated new at-rest storage key")
        return hex
    }

    fun clear(context: Context) {
        encryptedPrefs(context).edit().remove(KEY_STORAGE_HEX).apply()
    }

    private fun encryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun masterKey(context: Context): MasterKey =
        try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        } catch (e: Exception) {
            Log.w("Mycelium", "StrongBox unavailable, using software Keystore: ${e.message}")
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
}
