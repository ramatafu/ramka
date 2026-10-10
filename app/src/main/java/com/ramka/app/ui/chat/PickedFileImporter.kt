package com.ramka.app.ui.chat

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ramka.domain.util.FileTransferConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** Файл больше [FileTransferConfig.MAX_FILE_SIZE_BYTES]. */
internal class FileTooLargeException(val maxBytes: Long) : IOException("File exceeds $maxBytes bytes")

/** Пустой файл: передавать нечего. */
internal class EmptyFileException : IOException("File is empty")

/** Результат успешного копирования: [localUri] — file:// Uri копии во внутреннем хранилище. */
data class PreparedFile(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val localUri: String
)

/**
 * Копирует файл, выбранный через системный пикер, во внутреннее хранилище приложения
 * (filesDir/outgoing_files). Временный Uri из пикера для отложенной отправки не годится:
 * доступ к нему может исчезнуть. filesDir, а не cacheDir — система вправе чистить кэш,
 * пока файл ещё ждёт отправки.
 *
 * Выполняется на Dispatchers.IO; при ошибке или отмене частично записанный файл удаляется.
 * Сеть, Outbox и БД не затрагиваются.
 */
class PickedFileImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** @throws IOException / SecurityException при сбое чтения или записи. */
    suspend fun import(uri: Uri): PreparedFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val info = resolver.readPickedFileInfo(uri)
        val mimeType = resolver.getType(uri) ?: DEFAULT_MIME_TYPE
        val displayName = info.displayName ?: DEFAULT_FILE_NAME
        // Размер известен заранее — отказываем до копирования, не тратя место и время.
        info.sizeBytes?.let { if (it > FileTransferConfig.MAX_FILE_SIZE_BYTES) throw FileTooLargeException(FileTransferConfig.MAX_FILE_SIZE_BYTES) }
        val extension = pickExtension(
            displayName,
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        )

        val dir = File(context.filesDir, OUTGOING_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create $dir")
        val target = File(dir, generateStoredName(extension))

        val input = resolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
        val copiedBytes = input.use { copyToFile(it, target, FileTransferConfig.MAX_FILE_SIZE_BYTES) }
        if (copiedBytes == 0L) {
            target.delete()
            throw EmptyFileException()
        }

        // Размер берём по факту скопированных байт: провайдер может не сообщать SIZE.
        PreparedFile(displayName, copiedBytes, mimeType, Uri.fromFile(target).toString())
    }
}

internal const val OUTGOING_DIR = "outgoing_files"
private const val DEFAULT_MIME_TYPE = "application/octet-stream"
private const val DEFAULT_FILE_NAME = "file"
private const val COPY_BUFFER_SIZE = 64 * 1024

/**
 * Расширение для имени копии: сперва из исходного имени, затем из MIME-типа. Допускаются
 * только [a-z0-9] длиной 1..10 — имя на диске не должно зависеть от недоверенного ввода.
 */
internal fun pickExtension(displayName: String?, mimeExtension: String?): String? {
    val fromName = displayName?.substringAfterLast('.', "")
    return sanitizeExtension(fromName) ?: sanitizeExtension(mimeExtension)
}

private fun sanitizeExtension(raw: String?): String? =
    raw?.lowercase(Locale.ROOT)?.takeIf { ext ->
        ext.length in 1..10 && ext.all { it in 'a'..'z' || it in '0'..'9' }
    }

/** UUID (+ расширение): оригинальное имя на диске не используется, коллизии исключены. */
internal fun generateStoredName(extension: String?, uuid: UUID = UUID.randomUUID()): String =
    if (extension != null) "$uuid.$extension" else uuid.toString()

/**
 * Копирует [input] в [target] через временный `<target>.part` и переименовывает по
 * завершении, так что готовый файл никогда не бывает обрезанным. Проверяет отмену
 * корутины на каждом блоке. При превышении [maxBytes] бросает [FileTooLargeException].
 * Возвращает число скопированных байт.
 */
internal suspend fun copyToFile(input: InputStream, target: File, maxBytes: Long = Long.MAX_VALUE): Long {
    val part = File(target.parentFile, target.name + ".part")
    var total = 0L
    try {
        FileOutputStream(part).use { out ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
                if (total > maxBytes) throw FileTooLargeException(maxBytes)
            }
        }
        if (!part.renameTo(target)) throw IOException("Cannot rename $part to $target")
        return total
    } catch (t: Throwable) {
        part.delete()
        throw t
    }
}
