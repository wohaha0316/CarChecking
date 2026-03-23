package com.example.carchecking

import androidx.room.*

@Entity(
    tableName = "vehicle_master",
    indices = [
        Index(value = ["brand", "model"]),
        Index(value = ["normalizedKey"], unique = true)
    ]
)
data class VehicleMaster(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val model: String,
    val lengthMm: Int,
    val widthMm: Int,
    val normalizedKey: String,
    val createdTs: Long = System.currentTimeMillis(),
    val updatedTs: Long = System.currentTimeMillis()
)

@Dao
interface VehicleMasterDao {

    @Query("SELECT * FROM vehicle_master ORDER BY brand ASC, model ASC")
    suspend fun getAll(): List<VehicleMaster>

    @Query("SELECT COUNT(*) FROM vehicle_master")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VehicleMaster): Long

    @Update
    suspend fun update(item: VehicleMaster)

    @Delete
    suspend fun delete(item: VehicleMaster)

    @Query("""
        SELECT * FROM vehicle_master
        WHERE LOWER(brand) LIKE '%' || LOWER(:q) || '%'
           OR LOWER(model) LIKE '%' || LOWER(:q) || '%'
        ORDER BY brand ASC, model ASC
    """)
    suspend fun search(q: String): List<VehicleMaster>

    @Query("DELETE FROM vehicle_master")
    suspend fun deleteAll()
}