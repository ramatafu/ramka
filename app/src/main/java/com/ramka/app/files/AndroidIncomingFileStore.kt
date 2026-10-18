@file:Suppress("DEPRECATION")

package com.ramka.app.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.ramka.domain.repository.IncomingFileStore
import com.ramka.domain.util.FileNames
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidIncomingFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) : IncomingFileStore {

    private val tempDir get() = File(context.filesDir, TEMP_DIR)
    private val encryptedDir get() = File(context.filesDir, ENCRYPTED_DIR)
    private val openDir get() = File(context.cacheDir, OPEN_DIR)

    private val crypto: FileCipher by lazy {
        if (USE_FALLBACK_CRYPTO) {
            Log.w("AndroidIncomingFileStore", "⚠️ ИСПОЛЬЗУЕТСЯ РЕЗЕРВНЫЙ FallbackFileCrypto (без Keystore)")
            FallbackFileCrypto(context)
        } else {
            Log.d("AndroidIncomingFileStore", "✅ ИСПОЛЬЗУЕТСЯ FileCrypto с диагностикой Tink")
            FileCrypto(context).also { it.selfTest() }
        }
    }

    private fun partFile(transferId: String): File = File(tempDir, "${validId(transferId)}.part")
    private fun encFile(transferId: String): File = File(encryptedDir, "${validId(transferId)}.enc")
    private fun encTmpFile(transferId: String): File = File(encryptedDir, "${validId(transferId)}.enc.tmp")
    
    private fun getDecryptedFileName(transferId: String, fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        return if (ext.isNotEmpty()) "${validId(transferId)}.$ext" else validId(transferId)
    }
    
    private fun openFile(transferId: String, fileName: String): File = File(openDir, getDecryptedFileName(transferId, fileName))
    private fun openTmpFile(transferId: String, fileName: String): File = File(openDir, "${getDecryptedFileName(transferId, fileName)}.tmp")

    private fun validId(transferId: String): String {
        require(transferId.matches(ID_REGEX)) { "Недопустимый id передачи" }
        return transferId
    }

    override suspend fun begin(transferId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!tempDir.isDirectory && !tempDir.mkdirs()) return@withContext false
            val part = partFile(transferId)
            shredAndDelete(part)
            encTmpFile(transferId).delete()
            part.createNewFile()
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun write(transferId: String, offset: Long, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                RandomAccessFile(partFile(transferId), "rw").use { raf ->
                    raf.seek(offset)
                    raf.write(data)
                }
                true
            } catch (e: IOException) {
                false
            }
        }

    override suspend fun publish(transferId: String, displayName: String, mimeType: String): String? =
        withContext(Dispatchers.IO) {
            Log.d("AndroidIncomingFileStore", "📥 publish вызван для: $transferId")
            val part = partFile(transferId)
            Log.d("AndroidIncomingFileStore", "📥 part файл существует: ${part.isFile}, размер: ${part.length()} байт")
            
            val enc = encFile(transferId)
            val tmp = encTmpFile(transferId)
            fun cleanupAfterFailure() {
                tmp.delete()
                enc.delete()
                shredAndDelete(part)
            }
            try {
                if (!part.isFile) {
                    Log.e("AndroidIncomingFileStore", "❌ publish: part файл не найден!")
                    return@withContext null
                }
                if (!encryptedDir.isDirectory && !encryptedDir.mkdirs()) throw IOException("encrypted_media")
                tmp.delete()
                enc.delete()
                
                Log.d("AndroidIncomingFileStore", "📥 Начинаю шифрование через Tink (AAD=transferId)...")
                crypto.encrypt(part, tmp, transferId)
                crypto.verify(tmp, transferId)
                Log.d("AndroidIncomingFileStore", "📥 Шифрование и проверка успешны, размер tmp: ${tmp.length()} байт")
                
                if (!tmp.renameTo(enc)) {
                    Log.e("AndroidIncomingFileStore", "❌ publish: ошибка переименования .enc")
                    throw IOException("rename .enc")
                }
                Log.d("AndroidIncomingFileStore", "📥 Переименование успешно, размер enc: ${enc.length()} байт")
                
                shredAndDelete(part)
                val result = "$ENCRYPTED_SCHEME://$transferId"
                Log.d("AndroidIncomingFileStore", "✅ publish успешен, возвращаем: $result")
                result
            } catch (e: CancellationException) {
                cleanupAfterFailure()
                throw e
            } catch (e: Exception) {
                Log.e("AndroidIncomingFileStore", "❌ publish упал с ошибкой", e)
                cleanupAfterFailure()
                null
            }
        }

    override suspend fun discard(transferId: String) {
        withContext(Dispatchers.IO) { runCatching { shredAndDelete(partFile(transferId)) } }
    }

    override suspend fun discardAll() {
        withContext(Dispatchers.IO) {
            tempDir.listFiles()?.forEach { runCatching { shredAndDelete(it) } }
            encryptedDir.listFiles()?.filter { it.name.endsWith(".enc.tmp") }?.forEach { runCatching { it.delete() } }
            clearOpenDir()
        }
    }

    // ИСПРАВЛЕНО: динамический authority вместо захардкоженного
    override suspend fun getDecryptedFileUri(transferId: String, fileName: String): String? = withContext(Dispatchers.IO) {
        Log.d("AndroidIncomingFileStore", "🔓 getDecryptedFileUri вызван для: $transferId, fileName: $fileName")
        val decryptedFile = decryptToCache(transferId, fileName)
        
        if (decryptedFile != null) {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider", // <-- ИСПРАВЛЕНО
                decryptedFile
            )
            Log.d("AndroidIncomingFileStore", "🔓 getDecryptedFileUri результат (content://): $contentUri")
            contentUri.toString()
        } else {
            Log.d("AndroidIncomingFileStore", "🔓 getDecryptedFileUri результат: null")
            null
        }
    }

    suspend fun clearDecryptedCache() {
        withContext(Dispatchers.IO) { clearOpenDir() }
    }

    suspend fun exportToPublicStorage(transferId: String, displayName: String, mimeType: String): String? =
        withContext(Dispatchers.IO) {
            val source = decryptToCache(transferId, displayName) ?: return@withContext null
            val name = FileNames.sanitizeFileName(displayName)
            val mime = FileNames.sanitizeMimeType(mimeType)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    publishToMediaStore(source, name, mime)
                } else {
                    publishLegacy(source, name)
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun deleteStoredFile(transferId: String) {
        withContext(Dispatchers.IO) {
            openDir.listFiles()?.filter { it.name.startsWith(validId(transferId)) }?.forEach {
                runCatching { shredAndDelete(it) }
            }
            runCatching { encFile(transferId).delete() }
            runCatching { encTmpFile(transferId).delete() }
        }
    }

    private fun decryptToCache(transferId: String, fileName: String): File? {
        val target = openFile(transferId, fileName)
        
        // НОВОЕ: Удаляем старый файл, если он есть, чтобы избежать отдачи битого кэша
        if (target.isFile) {
            Log.d("AndroidIncomingFileStore", "🔓 Удаляю старый файл из кэша перед новой расшифровкой")
            target.delete()
        }
        
        val source = encFile(transferId)
        Log.d("AndroidIncomingFileStore", "🔓 decryptToCache: source=$source, существует=${source.isFile}, размер: ${source.length()} байт")
        if (!source.isFile) {
            Log.e("AndroidIncomingFileStore", "❌ decryptToCache: зашифрованный файл НЕ СУЩЕСТВУЕТ!")
            return null
        }
        
        val tmp = openTmpFile(transferId, fileName)
        return try {
            if (!openDir.isDirectory && !openDir.mkdirs()) {
                Log.e("AndroidIncomingFileStore", "❌ decryptToCache: не удалось создать openDir")
                return null
            }
            tmp.delete()
            Log.d("AndroidIncomingFileStore", "🔓 Начинаю расшифровку через Tink...")
            
            crypto.decrypt(source, tmp, transferId)
            
            if (!tmp.renameTo(target)) {
                Log.e("AndroidIncomingFileStore", "❌ decryptToCache: ошибка переименования open")
                throw IOException("rename open")
            }
            
            // НОВОЕ: Проверка целостности расшифрованного файла
            logFileSanity(target)
            
            Log.d("AndroidIncomingFileStore", "✅ decryptToCache успешен, размер target: ${target.length()} байт")
            target
        } catch (e: Exception) {
            Log.e("AndroidIncomingFileStore", "❌ decryptToCache упал с ошибкой: ${e.message}", e)
            shredAndDelete(tmp)
            null
        }
    }

    // НОВОЕ: Метод для проверки целостности файла
    private fun logFileSanity(file: File) {
        try {
            val head = ByteArray(8)
            val n = FileInputStream(file).use { it.read(head) }
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
            val headHex = head.take(maxOf(n ?: 0, 0)).joinToString("") { "%02x".format(it) }
            Log.d("AndroidIncomingFileStore", "sanity: ${file.name} size=${file.length()} head=$headHex decoded=${opts.outWidth}x${opts.outHeight} mime=${opts.outMimeType}")
        } catch (e: Exception) {
            Log.e("AndroidIncomingFileStore", "sanity check failed", e)
        }
    }

    private fun clearOpenDir() {
        openDir.listFiles()?.forEach { runCatching { shredAndDelete(it) } }
    }

    private fun shredAndDelete(file: File) {
        if (!file.exists()) return
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val zeros = ByteArray(64 * 1024)
                var left = raf.length()
                raf.seek(0)
                while (left > 0) {
                    val n = minOf(left, zeros.size.toLong()).toInt()
                    raf.write(zeros, 0, n)
                    left -= n
                }
                raf.fd.sync()
            }
        } catch (e: IOException) {
        } finally {
            file.delete()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(source: File, displayName: String, mimeType: String): String? {
        val (collection, directory) = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$PUBLIC_SUBDIR")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values) ?: return null
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("No output stream for $uri")
            out.use { output -> FileInputStream(source).use { it.copyTo(output, 64 * 1024) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    private fun publishLegacy(source: File, displayName: String): String? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val dir = File(base, PUBLIC_SUBDIR)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val target = uniqueFile(dir, displayName)
        FileInputStream(source).use { input -> target.outputStream().use { input.copyTo(it, 64 * 1024) } }
        return Uri.fromFile(target).toString()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($n)$ext")
            n++
        }
        return candidate
    }

    companion object {
        const val ENCRYPTED_SCHEME = "ramka-enc"
        private const val TEMP_DIR = "incoming_files"
        private const val ENCRYPTED_DIR = "encrypted_media"
        private const val OPEN_DIR = "open"
        private const val PUBLIC_SUBDIR = "ramka"
        private val ID_REGEX = Regex("^[0-9a-fA-F-]{36}$")

        private const val USE_FALLBACK_CRYPTO = false

        fun isEncryptedUri(raw: String?): Boolean = transferIdFrom(raw) != null

        fun transferIdFrom(raw: String?): String? {
            val prefix = "$ENCRYPTED_SCHEME://"
            if (raw == null || !raw.startsWith(prefix)) return null
            return raw.removePrefix(prefix).takeIf { it.matches(ID_REGEX) }
        }
    }
}