package com.example.carchecking

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NoteDao {
    /** 파일의 모든 메모 */
    @Query("SELECT * FROM notes WHERE fileKey = :fileKey")
    suspend fun listByFile(fileKey: String): List<Note>

    /** ✅ B/L 기준으로 1건 조회 */
    @Query("SELECT * FROM notes WHERE fileKey = :fileKey AND bl = :bl LIMIT 1")
    suspend fun getByBl(fileKey: String, bl: String): Note?

    /** ✅ B/L 기준으로 삭제 */
    @Query("DELETE FROM notes WHERE fileKey = :fileKey AND bl = :bl")
    suspend fun deleteByBl(fileKey: String, bl: String)

    /** upsert */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note)
}
