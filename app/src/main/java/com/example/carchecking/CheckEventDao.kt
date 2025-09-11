package com.example.carchecking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CheckEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: CheckEvent)

    @Query("SELECT * FROM check_events WHERE fileKey = :fileKey ORDER BY ts ASC")
    suspend fun findByFile(fileKey: String): List<CheckEvent>

    @Query("DELETE FROM check_events WHERE fileKey = :fileKey")
    suspend fun deleteByFile(fileKey: String)
}
