package com.example.carchecking

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room DB
 * v1 -> v2: notes 테이블 신설 (+ unique index fileKey,rowIndex)
 * v2 -> v3: spec_override 테이블 신설 (+ index(fileKey), index(bl))
 */
@Database(
    entities = [
        CheckEvent::class,
        Note::class,
        SpecOverride::class // ★ v3 신규 엔티티
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): CheckEventDao
    abstract fun notes(): NoteDao
    abstract fun specOverrides(): SpecOverrideDao // ★ v3 신규 DAO

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 -> v2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileKey` TEXT NOT NULL,
                        `rowIndex` INTEGER NOT NULL,
                        `bl` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `updatedTs` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_fileKey_rowIndex`
                    ON `notes`(`fileKey`,`rowIndex`)
                """.trimIndent())
            }
        }

        // ★ v2 -> v3: 제원 수동입력 저장용 테이블 생성
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `spec_override` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileKey` TEXT NOT NULL,
                        `bl` TEXT NOT NULL,
                        `vin` TEXT,
                        `lenMm` INTEGER,
                        `widthMm` INTEGER,
                        `updatedTs` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""CREATE INDEX IF NOT EXISTS `index_spec_override_fileKey` ON `spec_override`(`fileKey`)""")
                db.execSQL("""CREATE INDEX IF NOT EXISTS `index_spec_override_bl` ON `spec_override`(`bl`)""")
            }
        }

        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "carchecking.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // ★ 마이그레이션 모두 적용
                    // .fallbackToDestructiveMigration() // 필요 시 테스트용으로만 잠깐 사용
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
