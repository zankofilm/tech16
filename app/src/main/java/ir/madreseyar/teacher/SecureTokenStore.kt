package ir.madreseyar.teacher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("madreseyar_teacher_direct_api", Context.MODE_PRIVATE)
    private val alias = "madreseyar_teacher_direct_api_token_v1"

    fun hasToken(): Boolean = !prefs.getString("cipher", null).isNullOrBlank() && !prefs.getString("iv", null).isNullOrBlank()

    fun save(token: String) {
        if (token.isBlank()) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val enc = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("cipher", Base64.encodeToString(enc, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun load(): String? = try {
        val enc = Base64.decode(prefs.getString("cipher", ""), Base64.NO_WRAP)
        val iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)
        if (enc.isEmpty() || iv.isEmpty()) null else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(enc), Charsets.UTF_8)
        }
    } catch (_: Exception) { null }

    fun clear() { prefs.edit().clear().apply() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }
}
