package com.example.carchecking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE fileKey = :fileKey")
    suspend fun listByFile(fileKey: String): List<Note>

    @Query("SELECT * FROM notes WHERE fileKey = :fileKey AND rowIndex = :rowIndex LIMIT 1")
    suspend fun get(fileKey: String, rowIndex: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)

    @Query("DELETE FROM notes WHERE fileKey = :fileKey AND rowIndex = :rowIndex")
    suspend fun delete(fileKey: String, rowIndex: Int)
}
