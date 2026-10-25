package com.ramka.app.ui.contacts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramka.domain.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import kotlin.math.min

/**
 * Имя для показа: локальный псевдоним, а если он пуст — короткий отпечаток публичного ключа
 * (чтобы строка контакта никогда не оставалась без подписи).
 */
fun Contact.displayName(): String {
    val name = alias.trim()
    if (name.isNotEmpty()) return name
    val fingerprint = publicKey.take(4).joinToString("") { "%02x".format(it) }
    return if (fingerprint.isEmpty()) "Контакт" else "ключ $fingerprint"
}

/**
 * Круглый аватар контакта: локальное фото, если оно есть и читается, иначе первая буква имени
 * на цветном фоне (для пустого имени — значок человека). «Избранное» ([isSelf]) — всегда звезда на нейтральном фоне. Фото декодируется вне главного потока, уменьшенным
 * под размер аватара, и кэшируется в памяти.
 */
@Composable
fun ContactAvatar(
    alias: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isSelf: Boolean = false
) {
    val targetPx = with(LocalDensity.current) { size.roundToPx() }
    val photo by produceState<ImageBitmap?>(
        initialValue = AvatarBitmapLoader.cached(photoUri, targetPx),
        photoUri,
        targetPx
    ) {
        value = if (photoUri == null) null else withContext(Dispatchers.IO) { AvatarBitmapLoader.load(photoUri, targetPx) }
    }

    val round = modifier.size(size).clip(CircleShape)
    val bitmap = photo
    if (isSelf) {
        Box(modifier = round.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Избранное",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    } else if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Фото контакта",
            contentScale = ContentScale.Crop,
            modifier = round
        )
    } else {
        val initial = alias.trim().firstOrNull()?.uppercaseChar()?.toString()
        Box(modifier = round.background(colorForAlias(alias)), contentAlignment = Alignment.Center) {
            if (initial != null) {
                Text(initial, color = Color.White, fontSize = (size.value * 0.42f).sp)
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}

private fun colorForAlias(alias: String): Color {
    val hue = (alias.hashCode().and(0xFFFFFF) % 360).toFloat()
    return Color.hsv(hue, 0.45f, 0.55f)
}

/** Декодирование аватаров с даунсэмплингом и маленьким LRU-кэшем (по байтам, а не по количеству). */
internal object AvatarBitmapLoader {
    private const val CACHE_BYTES = 6 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun key(uri: String, px: Int) = "$uri@$px"

    fun cached(uri: String?, px: Int): ImageBitmap? =
        uri?.let { cache.get(key(it, px))?.asImageBitmap() }

    /** Поддерживаются только file:// — именно их создаёт ContactPhotoStore. */
    fun load(uri: String, px: Int): ImageBitmap? {
        cached(uri, px)?.let { return it }
        val file = runCatching { File(URI(uri)) }.getOrNull()?.takeIf { it.isFile } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        cache.put(key(uri, px), bitmap)
        return bitmap.asImageBitmap()
    }
}
