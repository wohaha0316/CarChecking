package com.example.carchecking

import androidx.room.*

@Entity(
    tableName = "spec_override",
    indices = [Index("fileKey"), Index("bl")]
)
data class SpecOverride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileKey: String,
    val bl: String,
    val vin: String? = null,
    val lenMm: Int? = null,
    val widthMm: Int? = null,
    val updatedTs: Long = System.currentTimeMillis()
)

@Dao
interface SpecOverrideDao {
    @Query("SELECT * FROM spec_override WHERE fileKey=:fileKey")
    suspend fun listByFile(fileKey: String): List<SpecOverride>

    @Query("SELECT * FROM spec_override WHERE fileKey=:fileKey AND bl=:bl LIMIT 1")
    suspend fun get(fileKey: String, bl: String): SpecOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ov: SpecOverride): Long

    @Update
    suspend fun update(ov: SpecOverride)

    @Query("DELETE FROM spec_override WHERE fileKey=:fileKey AND bl=:bl")
    suspend fun delete(fileKey: String, bl: String)
}
