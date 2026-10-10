package com.ramka.app.files

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.ramka.domain.model.MessageBody
import java.io.File

/** Результат попытки открыть файл; [userMessage] — текст для Snackbar или null, если всё хорошо. */
internal enum class OpenResult(val userMessage: String?) {
    OPENED(null),
    NO_FILE("Файл недоступен"),
    NO_APP("Нет приложения для открытия этого файла"),
    FAILED("Не удалось открыть файл")
}

/**
 * Открывает файл из чата через ACTION_VIEW с его MIME-типом. Наши файлы (file://: исходящая копия
 * во внутреннем хранилище, приём на Android 8–9) отдаются через FileProvider — прямой file:// Uri
 * другое приложение получить не может; content:// MediaStore (приём на Android 10+) отдаётся как
 * есть с временным правом чтения. Доступ выдаётся только на этот Uri и только на чтение.
 */
internal object FileOpener {
    fun open(context: Context, body: MessageBody.File): OpenResult {
        val raw = body.uri ?: return OpenResult.NO_FILE
        val uri = try {
            val parsed = Uri.parse(raw)
            if (parsed.scheme == "file") {
                val file = File(parsed.path ?: return OpenResult.NO_FILE)
                if (!file.isFile) return OpenResult.NO_FILE
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                parsed
            }
        } catch (e: IllegalArgumentException) {
            return OpenResult.FAILED
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, body.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            OpenResult.OPENED
        } catch (e: ActivityNotFoundException) {
            OpenResult.NO_APP
        } catch (e: SecurityException) {
            OpenResult.FAILED
        }
    }
}
