package com.ramka.app.files

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class FallbackFileCrypto(context: Context) : FileCipher {

    private val keyFile = File(context.applicationContext.filesDir, "fallback_master.key")
    private val keyLock = Any()
    private val secretKey: SecretKey by lazy { loadOrCreateKey() }

    private fun fingerprint(): String =
        CryptoLog.hex(MessageDigest.getInstance("SHA-256").digest(secretKey.encoded), 4)

    private fun loadOrCreateKey(): SecretKey = synchronized(keyLock) {
        val existing = if (keyFile.isFile) keyFile.readBytes() else null
        if (existing != null && existing.size == KEY_LEN) {
            Log.d(TAG, "KEY loaded from ${keyFile.name}")
            return@synchronized SecretKeySpec(existing, "AES")
        }
        if (existing != null) Log.e(TAG, "KEY file has wrong size ${existing.size}, regenerating")

        val bytes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().encoded
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        if (!tmp.renameTo(keyFile)) throw IOException("cannot save ${keyFile.name}")
        Log.d(TAG, "KEY generated and saved to ${keyFile.name}")
        SecretKeySpec(bytes, "AES")
    }

    private fun crypt(mode: Int, prefix: ByteArray, counter: Int, aadBase: ByteArray, last: Boolean, data: ByteArray, len: Int): ByteArray {
        val nonce = ByteBuffer.allocate(NONCE_LEN).put(prefix).putInt(counter).array()
        val aad = ByteBuffer.allocate(aadBase.size + 5).put(aadBase).putInt(counter).put(if (last) 1 else 0).array()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, secretKey, GCMParameterSpec(TAG_BITS, nonce))
        c.updateAAD(aad)
        return c.doFinal(data, 0, len)
    }

    private fun readUpTo(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n == -1) break
            total += n
        }
        return total
    }

    override fun encrypt(src: File, dst: File, transferId: String) {
        val aadBase = transferId.toByteArray(Charsets.UTF_8)
        try {
            Log.d(TAG, "FB-ENC id=$transferId key=${fingerprint()} aad=${CryptoLog.hex(aadBase)} src=${src.name}(${src.length()} B)")
            val prefix = ByteArray(PREFIX_LEN).also { SecureRandom().nextBytes(it) }
            val fos = FileOutputStream(dst)
            DataOutputStream(BufferedOutputStream(fos, BUFFER)).use { out ->
                out.write(MAGIC)
                out.write(prefix)
                FileInputStream(src).use { input ->
                    var cur = ByteArray(CHUNK)
                    var next = ByteArray(CHUNK)
                    var curLen = readUpTo(input, cur)
                    var counter = 0
                    while (true) {
                        val nextLen = if (curLen == CHUNK) readUpTo(input, next) else 0
                        val last = nextLen == 0
                        val ct = crypt(Cipher.ENCRYPT_MODE, prefix, counter, aadBase, last, cur, curLen)
                        out.writeInt(ct.size)
                        out.write(ct)
                        if (last) break
                        val t = cur; cur = next; next = t
                        curLen = nextLen
                        counter++
                    }
                }
                out.flush()
                fos.fd.sync()
            }
            Log.d(TAG, "FB-ENC done: ${dst.name} size=${dst.length()} sha256=${CryptoLog.sha256(dst)} header=${CryptoLog.headerHex(dst)}")
        } catch (t: Throwable) {
            Log.e(TAG, "FB-ENC FAILED id=$transferId: ${t.javaClass.name}: ${t.message}", t)
            throw t
        }
    }

    override fun decrypt(src: File, dst: File, transferId: String) {
        val aadBase = transferId.toByteArray(Charsets.UTF_8)
        try {
            Log.d(TAG, "FB-DEC id=$transferId key=${fingerprint()} aad=${CryptoLog.hex(aadBase)} src=${src.name} size=${src.length()} sha256=${CryptoLog.sha256(src)} header=${CryptoLog.headerHex(src)}")
            processDecrypt(src, dst, aadBase)
            Log.d(TAG, "FB-DEC done: ${dst.name} size=${dst.length()}")
        } catch (t: Throwable) {
            Log.e(TAG, "FB-DEC FAILED id=$transferId: ${t.javaClass.name}: ${t.message}", t)
            throw t
        }
    }

    override fun verify(src: File, transferId: String) {
        val aadBase = transferId.toByteArray(Charsets.UTF_8)
        processDecrypt(src, null, aadBase)
    }

    private fun processDecrypt(src: File, dst: File?, aadBase: ByteArray) {
        DataInputStream(BufferedInputStream(FileInputStream(src), BUFFER)).use { input ->
            val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
            if (!magic.contentEquals(MAGIC)) throw IOException("bad magic: ${CryptoLog.hex(magic)}")
            val prefix = ByteArray(PREFIX_LEN).also { input.readFully(it) }

            var curLen = readLen(input) ?: throw IOException("no chunks")
            val fos = dst?.let { FileOutputStream(it) }
            try {
                var counter = 0
                while (true) {
                    val ct = ByteArray(curLen).also { input.readFully(it) }
                    val nextLen = readLen(input)
                    val last = nextLen == null
                    val pt = crypt(Cipher.DECRYPT_MODE, prefix, counter, aadBase, last, ct, ct.size)
                    fos?.write(pt)
                    if (last) break
                    curLen = nextLen!!
                    counter++
                }
                fos?.fd?.sync()
            } finally {
                fos?.close()
            }
        }
    }

    private fun readLen(input: DataInputStream): Int? {
        val first = input.read()
        if (first < 0) return null
        val rest = ByteArray(3).also { input.readFully(it) }
        val len = (first shl 24) or ((rest[0].toInt() and 0xff) shl 16) or ((rest[1].toInt() and 0xff) shl 8) or (rest[2].toInt() and 0xff)
        if (len !in (TAG_BITS / 8)..(CHUNK + TAG_BITS / 8)) throw IOException("bad chunk length $len")
        return len
    }

    private companion object {
        const val TAG = CryptoLog.TAG
        val MAGIC = byteArrayOf('R'.code.toByte(), 'K'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())
        const val KEY_LEN = 32
        const val PREFIX_LEN = 8
        const val NONCE_LEN = 12
        const val TAG_BITS = 128
        const val CHUNK = 64 * 1024
        const val BUFFER = 64 * 1024
    }
}