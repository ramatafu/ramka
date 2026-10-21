package com.ramka.app.panic

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files
import java.security.KeyStore

/**
 * Стирание данных приложения. Зависит ТОЛЬКО от Context (без Hilt/БД/корутин), поэтому его можно
 * вызвать и из аварийного пути в [RamkaApp.onCreate] до инициализации графа зависимостей.
 * Каждый шаг изолирован: сбой одного не останавливает остальные. Ничего не логируется.
 */
object PanicWipe {

    /** Нейтральное имя маркера «стирание начато»; лежит в dataDir и при стирании пропускается. */
    private const val MARKER = ".sync"
    private val KEEP_IN_DATA_DIR = setOf("lib", MARKER)

    fun markPending(context: Context) {
        step { File(context.dataDir, MARKER).createNewFile() }
    }

    fun isPending(context: Context): Boolean =
        try { File(context.dataDir, MARKER).exists() } catch (_: Throwable) { false }

    fun clearMarker(context: Context) {
        step { File(context.dataDir, MARKER).delete() }
    }

    /** Полное стирание. Идемпотентно: можно вызывать повторно. */
    fun wipeAll(context: Context) {
        wipeKeystore()                 // 1) крипто-шреддинг: без ключей БД и .enc нечитаемы
        wipePreferences(context)       // 2) сбросить SharedPreferences (в т.ч. отложенные apply())
        wipeFiles(context)             // 3) всё содержимое dataDir + внешние каталоги приложения
        wipeMediaStoreExports(context) // 4) файлы, сохранённые пользователем в публичные папки
    }

    /** Удаляет ВСЕ записи Android Keystore этого приложения (MasterKey, ключ Tink и т.д.). */
    fun wipeKeystore() {
        step {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            ks.aliases().toList().forEach { alias -> step { ks.deleteEntry(alias) } }
        }
    }

    fun wipePreferences(context: Context) {
        step {
            File(context.dataDir, "shared_prefs").listFiles()
                ?.filter { it.name.endsWith(".xml") }
                ?.forEach { f ->
                    step {
                        context.getSharedPreferences(f.name.removeSuffix(".xml"), Context.MODE_PRIVATE)
                            .edit().clear().commit()
                    }
                }
        }
    }

    fun wipeFiles(context: Context) {
        // Содержимое dataDir: files (в т.ч. datastore/), cache, code_cache, databases, shared_prefs, no_backup.
        step { deleteChildren(context.dataDir, KEEP_IN_DATA_DIR) }
        step { context.externalCacheDirs?.forEach { d -> d?.let { step { deleteChildren(it, emptySet()) } } } }
        step { context.getExternalFilesDirs(null)?.forEach { d -> d?.let { step { deleteChildren(it, emptySet()) } } } }
    }

    /** Копии, которые пользователь сам экспортировал кнопкой «Сохранить» (…/ramka/). Android 10+. */
    fun wipeMediaStoreExports(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        )
        val where = "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND " +
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        collections.forEach { uri ->
            step { context.contentResolver.delete(uri, where, arrayOf(context.packageName, "%/ramka/%")) }
        }
    }

    /** Удаляет содержимое [dir] (сам [dir] остаётся), кроме имён из [keep]. Симлинки не разыменовываются. */
    internal fun deleteChildren(dir: File, keep: Set<String>) {
        dir.listFiles()?.forEach { child ->
            if (child.name in keep) return@forEach
            step { deleteTree(child) }
        }
    }

    private fun deleteTree(file: File) {
        val isLink = try { Files.isSymbolicLink(file.toPath()) } catch (_: Throwable) { false }
        if (!isLink && file.isDirectory) {
            file.listFiles()?.forEach { step { deleteTree(it) } }
        }
        file.delete()
    }

    private inline fun step(block: () -> Unit) {
        try { block() } catch (_: Throwable) { }
    }
}
