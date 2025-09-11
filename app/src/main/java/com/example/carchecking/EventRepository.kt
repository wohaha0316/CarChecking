package com.example.carchecking

import android.content.Context

class EventRepository(ctx: Context) {
    private val dao = AppDatabase.get(ctx).events()

    suspend fun logCheck(fileKey: String, rowIndex: Int, checked: Boolean, user: String?) {
        val e = CheckEvent(
            fileKey = fileKey,
            rowIndex = rowIndex,
            action = if (checked) "CHECK" else "UNCHECK",
            ts = System.currentTimeMillis(),
            user = user
        )
        dao.insert(e)
    }

    suspend fun eventsForFile(fileKey: String) = dao.findByFile(fileKey)
    suspend fun clearForFile(fileKey: String) = dao.deleteByFile(fileKey)
}
