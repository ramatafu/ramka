package com.ramka.storage.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [ContactEntity::class, MessageEntity::class, OutboxEntity::class],
    version = 3,
    exportSchema = true
)
abstract class RamkaDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao

    companion object {

        /**
         * v1 -> v2: добавлена таблица `outbox` (§4.5, ЭТАП B.1) и две новые колонки
         * в `messages` — `remoteMessageId`/`readAckSent` (ЭТАП B.5, DELIVERED/READ-ACK).
         * Таблица contacts и существующие колонки messages не меняются — переписка
         * и контакты НЕ теряются.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `outbox` (
                        `messageLocalId` TEXT NOT NULL PRIMARY KEY,
                        `contactId` TEXT NOT NULL,
                        `attemptCount` INTEGER NOT NULL,
                        `nextAttemptAtEpochMillis` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`messageLocalId`) REFERENCES `messages`(`localId`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_outbox_messageLocalId` ON `outbox` (`messageLocalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_contactId` ON `outbox` (`contactId`)")

                db.execSQL("ALTER TABLE `messages` ADD COLUMN `remoteMessageId` TEXT")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `readAckSent` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 -> v3: добавлена колонка `contacts.unreadCount` (UX-правка — счётчик
         * непрочитанных в списке контактов). DEFAULT 0 — у существующих строк
         * счётчик просто обнуляется, ничего не теряется.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `contacts` ADD COLUMN `unreadCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Пароль БД — случайный ключ, сгенерированный один раз и хранящийся
         * в [com.ramka.crypto.securestorage.SecureKeyStore] (вызывающая сторона
         * передаёт его сюда, чтобы storage-модуль не зависел от crypto напрямую).
         */
        fun build(context: Context, passphrase: ByteArray): RamkaDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, RamkaDatabase::class.java, "ramka.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
