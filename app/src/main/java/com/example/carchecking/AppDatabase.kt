package com.example.carchecking

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CheckEvent::class, Note::class], // ★ Note 추가
    version = 2,                                 // ★ v2로 올림
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): CheckEventDao
    abstract fun notes(): NoteDao                // ★ NoteDao 노출

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 -> v2: notes 테이블 신설 + 유니크 인덱스
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

        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "carchecking.db"
                )
                    .addMigrations(MIGRATION_1_2) // ★ 마이그레이션 적용
                    //.fallbackToDestructiveMigration() // ← 테스트용 초기화가 필요할 때만 임시로 사용
                    //.enableMultiInstanceInvalidation() // (옵션) 다중 프로세스/인스턴스 갱신
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
