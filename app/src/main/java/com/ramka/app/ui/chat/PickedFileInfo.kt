package com.ramka.app.ui.chat

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** Метаданные файла, выбранного через системный пикер. null — провайдер не сообщил. */
internal data class PickedFileInfo(val displayName: String?, val sizeBytes: Long?)

/**
 * Читает DISPLAY_NAME и SIZE через ContentResolver. Для Uri из пикера
 * (ACTION_GET_CONTENT) разрешения READ_EXTERNAL_STORAGE не нужны: пикер выдаёт
 * приложению временный доступ к конкретному Uri.
 *
 * Вызывать вне главного потока — провайдер (например, облачный) может отвечать долго.
 * Любой сбой провайдера не пробрасывается: возвращаем то, что удалось узнать.
 */
internal fun ContentResolver.readPickedFileInfo(uri: Uri): PickedFileInfo {
    var name: String? = null
    var size: Long? = null
    try {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
    } catch (_: Exception) {
        // SecurityException / IllegalArgumentException / сбой провайдера — не критично
    }
    return PickedFileInfo(
        displayName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.takeIf { it.isNotBlank() },
        sizeBytes = size?.takeIf { it >= 0 }
    )
}
