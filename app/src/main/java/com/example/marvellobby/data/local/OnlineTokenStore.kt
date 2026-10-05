package com.example.marvellobby.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.example.marvellobby.data.remote.SessionSecrets
import com.google.gson.Gson
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface OnlineTokens {
    fun read(owner: String): SessionSecrets?
    fun save(owner: String,secrets: SessionSecrets)
    fun remove(owner: String)
}
/** Refresh tokens never enter Room, DataStore or BuildConfig. Backup is disabled for the app. */
class OnlineTokenStore(context: Context): OnlineTokens {
    private val directory=File(context.filesDir,"online-sessions").apply { mkdirs() }
    private val gson=Gson()
    private fun file(owner: String): AtomicFile {
        val name=MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory,"$name.enc"))
    }
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("marvel-lobby-online-v1",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("marvel-lobby-online-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized override fun read(owner: String): SessionSecrets?=runCatching {
        val data=file(owner).readFully();require(data.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12)))
            updateAAD(owner.toByteArray(Charsets.UTF_8))
        }
        gson.fromJson(String(cipher.doFinal(data.copyOfRange(12,data.size)),Charsets.UTF_8),SessionSecrets::class.java)
    }.getOrNull()
    @Synchronized override fun save(owner: String,secrets: SessionSecrets) {
        require(owner=="remote:${secrets.userId}")
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key());updateAAD(owner.toByteArray(Charsets.UTF_8)) }
        val encoded=cipher.iv+cipher.doFinal(gson.toJson(secrets).toByteArray(Charsets.UTF_8))
        val target=file(owner);val stream=target.startWrite()
        try { stream.write(encoded);target.finishWrite(stream) } catch(error: Exception) { target.failWrite(stream);throw error }
    }
    @Synchronized override fun remove(owner: String) { file(owner).delete() }
}
