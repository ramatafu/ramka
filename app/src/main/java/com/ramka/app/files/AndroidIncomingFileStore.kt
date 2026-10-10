package com.ramka.app.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.ramka.domain.repository.IncomingFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Принимаемые файлы на Android (этап 6.4).
 *
 * Сборка — во временном `filesDir/incoming_files/<id>.part` (внутреннее хранилище, чанки пишутся по
 * смещению). Готовый файл публикуется так:
 *  - Android 10+ (API 29+): MediaStore — Pictures/Movies/Music для image/video/audio, Download для
 *    остального, подпапка `ramka`; разрешения не нужны (приложение само создаёт запись);
 *    IS_PENDING скрывает файл от других приложений, пока он не дописан;
 *  - Android 8–9: запись в общий каталог без WRITE_EXTERNAL_STORAGE невозможна, поэтому файл
 *    кладётся в `getExternalFilesDir(DIRECTORY_DOWNLOADS)/ramka` (без разрешений; другим приложениям
 *    виден только через FileProvider/файловый менеджер). Возвращается file:// Uri.
 * Имена приходят уже очищенными (FileNames), id — локальный UUID.
 */
@Singleton
class AndroidIncomingFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) : IncomingFileStore {

    private val tempDir get() = File(context.filesDir, TEMP_DIR)

    private fun tempFile(transferId: String): File {
        require(transferId.matches(ID_REGEX)) { "Недопустимый id передачи" }
        return File(tempDir, "$transferId.part")
    }

    override suspend fun begin(transferId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!tempDir.isDirectory && !tempDir.mkdirs()) return@withContext false
            val file = tempFile(transferId)
            file.delete()
            file.createNewFile()
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun write(transferId: String, offset: Long, data: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            try {
                RandomAccessFile(tempFile(transferId), "rw").use { raf ->
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
            val temp = tempFile(transferId)
            try {
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    publishToMediaStore(temp, displayName, mimeType)
                } else {
                    publishLegacy(temp, displayName)
                }
                if (result != null) temp.delete()
                result
            } catch (e: Exception) {
                null
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(temp: File, displayName: String, mimeType: String): String? {
        val (collection, directory) = when {
            mimeType.startsWith("image/") ->
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            mimeType.startsWith("video/") ->
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            mimeType.startsWith("audio/") ->
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
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
            out.use { output -> FileInputStream(temp).use { it.copyTo(output) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri.toString()
        } catch (e: Exception) {
            resolver.delete(uri, null, null) // не оставляем недописанную запись
            return null
        }
    }

    private fun publishLegacy(temp: File, displayName: String): String? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        val dir = File(base, PUBLIC_SUBDIR)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val target = uniqueFile(dir, displayName)
        FileInputStream(temp).use { input -> target.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(target).toString()
    }

    /** «name.pdf» -> «name (1).pdf», если файл с таким именем уже есть. */
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

    override suspend fun discard(transferId: String) {
        withContext(Dispatchers.IO) { runCatching { tempFile(transferId).delete() } }
    }

    override suspend fun discardAll() {
        withContext(Dispatchers.IO) { tempDir.listFiles()?.forEach { runCatching { it.delete() } } }
    }

    private companion object {
        const val TEMP_DIR = "incoming_files"
        const val PUBLIC_SUBDIR = "ramka"
        val ID_REGEX = Regex("^[0-9a-fA-F-]{36}$")
    }
}
