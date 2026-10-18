package com.example.upaos.data.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Almacén seguro respaldado por Android Keystore y AES-256-GCM.
 * Reemplaza EncryptedSharedPreferences (androidx.security:security-crypto alpha)
 * con una implementación nativa, robusta y sin dependencias obsoletas.
 */
class SecureStore(
    context: Context,
    prefsName: String = PREFS_NAME,
    customKey: SecretKey? = null,
    customPrefs: SharedPreferences? = null
) {

    companion object {
        private const val TAG = "SecureStore"
        private const val PREFS_NAME = "upao_secure_store"
        private const val KEY_ALIAS = "upao_secure_store_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private val keyLock = Any()

        object Base64Compat {
            fun encode(bytes: ByteArray): String {
                return try {
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } catch (_: Throwable) {
                    java.util.Base64.getEncoder().encodeToString(bytes)
                }
            }

            fun decode(str: String): ByteArray {
                return try {
                    android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
                } catch (_: Throwable) {
                    java.util.Base64.getDecoder().decode(str)
                }
            }
        }
    }

    private val prefs: SharedPreferences = customPrefs
        ?: context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    // A failed Keystore access must never create a key that disappears with the process.
    private val secretKey: SecretKey by lazy { customKey ?: getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            } else {
                keyStore.getKey(KEY_ALIAS, null) as SecretKey
            }
        } catch (e: Exception) {
            throw GeneralSecurityException("No se pudo acceder al almacenamiento seguro", e)
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        check(iv.size == GCM_IV_LENGTH_BYTES) { "IV GCM incompatible" }
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Empaquetar IV (12 bytes) + CipherText (incluye tag de 16 bytes)
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64Compat.encode(combined)
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = Base64Compat.decode(encryptedBase64)
            if (combined.size <= GCM_IV_LENGTH_BYTES) return null

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

            val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo descifrar valor almacenado (clave invalidada o datos corruptos)")
            null
        }
    }

    fun put(key: String, value: String) {
        putAll(mapOf(key to value))
    }

    /** Encrypt everything before editing; commit reports persistence failures to the caller. */
    fun putAll(values: Map<String, String>, removals: Set<String> = emptySet()) {
        try {
            val encrypted = values.mapValues { encrypt(it.value) }
            val editor = prefs.edit()
            removals.forEach { editor.remove(it) }
            encrypted.forEach { (key, value) -> editor.putString(key, value) }
            check(editor.commit()) { "No se pudo persistir el almacenamiento seguro" }
        } catch (e: Exception) {
            throw IllegalStateException("No se pudo guardar la sesión de forma segura. Inténtalo de nuevo.", e)
        }
    }

    fun get(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        // Preserve ciphertext on transient Keystore failures; a later read can recover it.
        return decrypt(raw)
    }

    fun putBoolean(key: String, value: Boolean) {
        put(key, value.toString())
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        val str = get(key) ?: return defValue
        return str.toBooleanStrictOrNull() ?: defValue
    }

    fun contains(key: String): Boolean {
        return prefs.contains(key)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Retorna todas las claves descifradas actualmente almacenadas.
     */
    fun getAll(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        prefs.all.keys.forEach { key ->
            get(key)?.let { result[key] = it }
        }
        return result
    }
}
