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
 * v3 -> v4: vehicle_master 테이블 신설
 */
@Database(
    entities = [
        CheckEvent::class,
        Note::class,
        SpecOverride::class,
        VehicleMaster::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): CheckEventDao
    abstract fun notes(): NoteDao
    abstract fun specOverrides(): SpecOverrideDao
    abstract fun vehicleMasters(): VehicleMasterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileKey` TEXT NOT NULL,
                        `rowIndex` INTEGER NOT NULL,
                        `bl` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `updatedTs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_fileKey_rowIndex`
                    ON `notes`(`fileKey`,`rowIndex`)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `spec_override` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileKey` TEXT NOT NULL,
                        `bl` TEXT NOT NULL,
                        `vin` TEXT,
                        `lenMm` INTEGER,
                        `widthMm` INTEGER,
                        `updatedTs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_spec_override_fileKey` ON `spec_override`(`fileKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_spec_override_bl` ON `spec_override`(`bl`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicle_master` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `brand` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `lengthMm` INTEGER NOT NULL,
                        `widthMm` INTEGER NOT NULL,
                        `normalizedKey` TEXT NOT NULL,
                        `createdTs` INTEGER NOT NULL,
                        `updatedTs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_vehicle_master_brand_model`
                    ON `vehicle_master`(`brand`,`model`)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_vehicle_master_normalizedKey`
                    ON `vehicle_master`(`normalizedKey`)
                    """.trimIndent()
                )
            }
        }

        fun get(ctx: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDatabase::class.java,
                    "carchecking.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}