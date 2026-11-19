package app.hearth.integrations

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps the hub's access token sealed on this device: encrypted with AES-GCM under a key that lives in the phone's
 * keystore and never leaves it, so what is written to the app's preferences is of no use anywhere else. The app's
 * data is kept out of backups and device transfers, and the key could not travel with it anyway.
 *
 * Nothing here logs the token, and it is never synced.
 */
class HubTokenVault(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** The token; `null` when none is kept. A token that can no longer be opened, as after the key was lost, is thrown. */
    fun read(): String? {
        val sealed = preferences.getString(TOKEN_KEY, null) ?: return null
        val (iv, cipherText) = sealed.split(SEPARATOR).takeIf { it.size == 2 }?.map { Base64.decode(it, Base64.NO_WRAP) }
            ?: throw IOException("The kept token is not in its sealed form")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    /** Keeps the token, replacing any kept before, or forgets it and its key with `null`. */
    // Written with `commit()` itself, not the `edit { }` shorthand, whose answer on whether the write was made is lost.
    @SuppressLint("UseKtx")
    fun write(token: String?) {
        if (token == null) {
            if (!preferences.edit().remove(TOKEN_KEY).commit()) throw IOException("The token could not be forgotten")
            keyStore().deleteEntry(KEY_ALIAS)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val text = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR + Base64.encodeToString(sealed, Base64.NO_WRAP)
        if (!preferences.edit().putString(TOKEN_KEY, text).commit()) throw IOException("The token could not be kept")
    }

    /** The key in the keystore, made the first time it is wanted. */
    private fun key(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hearth.hubToken"
        const val PREFERENCES_NAME = "sync"
        const val TOKEN_KEY = "sealedHubToken"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val KEY_BITS = 256
        const val SEPARATOR = ":"
    }
}
