package com.ramka.app.files

import android.content.Context
import android.util.Log
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

internal object CryptoLog {
    const val TAG = "FileCrypto"
    fun hex(b: ByteArray, max: Int = b.size): String =
        b.take(max).joinToString("") { "%02x".format(it) }

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest(), 8)
    }

    fun headerHex(file: File): String {
        val buf = ByteArray(32)
        val n = FileInputStream(file).use { it.read(buf) }
        return hex(buf, maxOf(n, 0))
    }
}

class FileCrypto(context: Context) : FileCipher {

    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(KEYSET_PREFS, Context.MODE_PRIVATE) }

    private class Built(val aead: StreamingAead, val primaryKeyId: Int)

    private val built: Built by lazy { build("main") }

    private fun prefsHash(): String {
        val raw = prefs.getString(KEYSET_NAME, null) ?: return "<none>"
        return CryptoLog.hex(MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()), 6)
    }

    private fun build(label: String): Built {
        try {
            Log.d(TAG, "[$label] init: thread=${Thread.currentThread().name}, " +
                "keyset in prefs BEFORE=${prefs.contains(KEYSET_NAME)}, prefsHash=${prefsHash()}")
            StreamingAeadConfig.register()
            val handle: KeysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
                .withMasterKeyUri("android-keystore://$MASTER_KEY_ALIAS")
                .build()
                .keysetHandle
            val info = handle.keysetInfo
            val ids = info.keyInfoList.joinToString { "${it.keyId}/${it.status}" }
            Log.d(TAG, "[$label] init OK: primaryKeyId=${info.primaryKeyId}, keys=[$ids], " +
                "keyset in prefs AFTER=${prefs.contains(KEYSET_NAME)}, prefsHash=${prefsHash()}")
            return Built(handle.getPrimitive(StreamingAead::class.java), info.primaryKeyId)
        } catch (t: Throwable) {
            Log.e(TAG, "[$label] init FAILED: ${t.javaClass.name}: ${t.message}", t)
            t.cause?.let { Log.e(TAG, "[$label] cause: ${it.javaClass.name}: ${it.message}", it) }
            throw t
        }
    }

    override fun encrypt(src: File, dst: File, transferId: String) {
        val aad = transferId.toByteArray(Charsets.UTF_8)
        try {
            val b = built
            Log.d(TAG, "ENC id=$transferId keyId=${b.primaryKeyId} prefsHash=${prefsHash()} " +
                "aad=${CryptoLog.hex(aad)} src=${src.name}(${src.length()} B) dst=${dst.name}")
            FileInputStream(src).use { input ->
                b.aead.newEncryptingStream(FileOutputStream(dst), aad).use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                    }
                }
            }
            Log.d(TAG, "ENC done: ${dst.name} size=${dst.length()} sha256=${CryptoLog.sha256(dst)} " +
                "header=${CryptoLog.headerHex(dst)}")
        } catch (t: Throwable) {
            Log.e(TAG, "ENC FAILED id=$transferId: ${t.javaClass.name}: ${t.message}", t)
            throw t
        }
    }

    override fun decrypt(src: File, dst: File, transferId: String) {
        val aad = transferId.toByteArray(Charsets.UTF_8)
        try {
            val b = built
            Log.d(TAG, "DEC id=$transferId keyId=${b.primaryKeyId} prefsHash=${prefsHash()} " +
                "aad=${CryptoLog.hex(aad)} src=${src.name} size=${src.length()} " +
                "sha256=${CryptoLog.sha256(src)} header=${CryptoLog.headerHex(src)}")
            b.aead.newDecryptingStream(FileInputStream(src), aad).use { input ->
                FileOutputStream(dst).use { out ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                    }
                    out.fd.sync()
                }
            }
            Log.d(TAG, "DEC done: ${dst.name} size=${dst.length()}")
        } catch (t: Throwable) {
            Log.e(TAG, "DEC FAILED id=$transferId: ${t.javaClass.name}: ${t.message}", t)
            throw t
        }
    }

    override fun verify(src: File, transferId: String) {
        val aad = transferId.toByteArray(Charsets.UTF_8)
        built.aead.newDecryptingStream(FileInputStream(src), aad).use { input ->
            val buf = ByteArray(BUFFER)
            while (input.read(buf) != -1) { /* drain */ }
        }
    }

    fun selfTest(): Boolean {
        return try {
            val plain = ByteArray(1024) { (it % 251).toByte() }
            val aad = "selftest".toByteArray()

            val first = built
            val ct = ByteArrayOutputStream().also { bos ->
                first.aead.newEncryptingStream(bos, aad).use { it.write(plain) }
            }.toByteArray()
            val back1 = first.aead.newDecryptingStream(ByteArrayInputStream(ct), aad).use { it.readBytes() }
            val t1 = back1.contentEquals(plain)
            Log.d(TAG, "SELFTEST 1 (same instance, in-memory): $t1")

            val second = build("reload")
            val back2 = second.aead.newDecryptingStream(ByteArrayInputStream(ct), aad).use { it.readBytes() }
            val t2 = back2.contentEquals(plain)
            Log.d(TAG, "SELFTEST 2 (keyset reloaded from prefs): $t2, " +
                "keyIds equal=${first.primaryKeyId == second.primaryKeyId} (${first.primaryKeyId} vs ${second.primaryKeyId})")
            t1 && t2
        } catch (t: Throwable) {
            Log.e(TAG, "SELFTEST FAILED: ${t.javaClass.name}: ${t.message}", t)
            false
        }
    }

    private companion object {
        const val TAG = CryptoLog.TAG
        const val KEYSET_NAME = "ramka_file_keyset"
        const val KEYSET_PREFS = "ramka_file_keyset_prefs"
        const val MASTER_KEY_ALIAS = "ramka_file_master_key"
        const val BUFFER = 64 * 1024
    }
}