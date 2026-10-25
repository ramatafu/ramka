package com.ramka.app.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Локальное хранилище фото контактов: filesDir/contact_photos/<contactId>-<время>.jpg.
 *
 * Выбранная в галерее картинка НЕ хранится по исходному content:// (доступ к нему временный и пропадёт),
 * а копируется сюда: обрезается по центру до квадрата, уменьшается до [MAX_SIDE_PX] и перекодируется в JPEG.
 * Перекодирование заодно стирает EXIF (GPS, модель камеры). Фото существует только на этом устройстве
 * и никуда не передаётся; при «кнопке паники» уходит вместе со всем содержимым filesDir.
 *
 * Имя файла содержит время — при замене фото получается новый URI, и список/чат гарантированно
 * перерисовывают аватар. Старый файл удаляет вызывающий через [delete].
 */
@Singleton
class ContactPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dir: File get() = File(context.filesDir, DIR_NAME)

    /** @return file:// URI сохранённого фото или null, если картинку не удалось прочитать/сохранить. */
    suspend fun import(contactId: String, source: Uri): String? = withContext(Dispatchers.IO) {
        var decoded: Bitmap? = null
        var cropped: Bitmap? = null
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return@withContext null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val bitmap = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null
            decoded = bitmap

            val result = cropToSquare(bitmap, readRotationDegrees(source))
            cropped = result

            if (!dir.isDirectory && !dir.mkdirs()) return@withContext null
            val file = File(dir, "${safeName(contactId)}-${System.currentTimeMillis()}.jpg")
            val written = FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!written) {
                file.delete()
                return@withContext null
            }
            Uri.fromFile(file).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            if (cropped !== decoded) cropped?.recycle()
            decoded?.recycle()
        }
    }

    /** Удаляет файл фото, только если он лежит в нашей папке (чужие URI и файлы не трогаем). */
    suspend fun delete(photoUri: String?) {
        if (photoUri == null || !photoUri.startsWith("file:")) return
        withContext(Dispatchers.IO) {
            try {
                val file = File(URI(photoUri)).canonicalFile
                if (file.parentFile == dir.canonicalFile) file.delete()
            } catch (_: Exception) {
                // файла уже нет или URI некорректен — удалять нечего
            }
        }
    }

    private fun readRotationDegrees(source: Uri): Int = try {
        context.contentResolver.openInputStream(source)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }

    private fun cropToSquare(src: Bitmap, rotationDegrees: Int): Bitmap {
        val side = min(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val matrix = Matrix()
        if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())
        if (side > MAX_SIDE_PX) {
            val scale = MAX_SIDE_PX.toFloat() / side
            matrix.postScale(scale, scale)
        }
        return Bitmap.createBitmap(src, x, y, side, side, matrix, true)
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (min(width, height) / (sample * 2) >= MAX_SIDE_PX) sample *= 2
        return sample
    }

    private fun safeName(contactId: String): String = contactId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "contact" }

    companion object {
        const val DIR_NAME = "contact_photos"
        const val MAX_SIDE_PX = 512
        private const val JPEG_QUALITY = 88
    }
}
