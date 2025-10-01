package com.example.carchecking

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index(value = ["fileKey", "rowIndex"], unique = true)]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileKey: String,
    val rowIndex: Int,
    val bl: String,
    val text: String,
    val updatedTs: Long
)
