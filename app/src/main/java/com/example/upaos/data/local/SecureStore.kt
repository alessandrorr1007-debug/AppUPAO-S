package com.example.upaos.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
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

    private val secretKey: SecretKey = customKey ?: getOrCreateKey()

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        return try {
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
            Log.w(TAG, "Error accediendo a AndroidKeyStore, generando clave de respaldo local", e)
            // Fallback para entornos de testing o dispositivos con Keystore corrupto
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            keyGen.generateKey()
        }
    }

    private fun encrypt(plainText: String): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
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
        try {
            val encryptedValue = encrypt(value)
            prefs.edit().putString(key, encryptedValue).apply()
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Fallo al cifrar valor para la clave $key", e)
        }
    }

    fun get(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        val decrypted = decrypt(raw)
        if (decrypted == null) {
            // El valor no se pudo descifrar con la clave actual
            remove(key)
        }
        return decrypted
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
