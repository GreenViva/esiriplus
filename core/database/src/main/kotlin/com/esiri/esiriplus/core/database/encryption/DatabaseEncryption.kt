package com.esiri.esiriplus.core.database.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object DatabaseEncryption {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DB_KEY_ALIAS = "esiriplus_db_key"
    private const val PREFS_FILE = "esiriplus_db_passphrase"
    private const val KEY_PASSPHRASE = "db_passphrase"

    fun createOpenHelperFactory(context: Context): SupportOpenHelperFactory {
        System.loadLibrary("sqlcipher")
        val passphrase = getOrCreatePassphrase(context)
        return SupportOpenHelperFactory(passphrase.toByteArray())
    }

    private fun getOrCreatePassphrase(context: Context): String {
        ensureKeystoreKey()

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        return prefs.getString(KEY_PASSPHRASE, null)
            ?: generatePassphrase().also { passphrase ->
                prefs.edit().putString(KEY_PASSPHRASE, passphrase).apply()
            }
    }

    private fun generatePassphrase(): String =
        UUID.randomUUID().toString() + UUID.randomUUID().toString()

    private fun ensureKeystoreKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(DB_KEY_ALIAS)) return

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )

        val keySpec = KeyGenParameterSpec.Builder(
            DB_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keySpec)
        keyGenerator.generateKey()
    }

    /**
     * Returns the existing passphrase without creating one if it doesn't exist.
     * Used by [DatabaseVersionChecker] to open the encrypted DB file before Room.
     */
    fun getPassphrase(context: Context): String? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(DB_KEY_ALIAS)) return null

            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

            prefs.getString(KEY_PASSPHRASE, null)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            null
        }
    }

    fun verifyEncryption(context: Context): Boolean =
        try {
            val passphrase = getOrCreatePassphrase(context)
            passphrase.isNotEmpty()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            false
        }

    private const val KEY_SIZE_BITS = 256
}
