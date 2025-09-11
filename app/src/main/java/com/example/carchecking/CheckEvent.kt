package com.example.carchecking

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "check_events",
    indices = [Index(value = ["fileKey", "rowIndex", "ts"])]
)
data class CheckEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileKey: String,     // ParsedCache.Key.id()
    val rowIndex: Int,       // rows 내 인덱스(라벨행 제외 인덱스 아님, 화면 인덱스 그대로)
    val action: String,      // "CHECK" | "UNCHECK"
    val ts: Long,            // System.currentTimeMillis()
    val user: String? = null // 확인자(추후 설정에서 받기)
)
