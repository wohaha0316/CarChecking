// PrefsCleaner.kt (새 파일 혹은 유틸 객체)
package com.example.carchecking

import android.content.Context

object PrefsCleaner {
    private const val PREF_NAME = "carchecking_prefs"

    fun clearForFile(ctx: Context, fileKey: String) {
        val p = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val allKeys = p.all.keys
        val e = p.edit()
        // 접두어들 전부 포함 제거
        allKeys.forEach { k ->
            if (
                k.startsWith("check_orders:$fileKey:") ||
                k.startsWith("viewstate:$fileKey")     ||
                k.startsWith("status:$fileKey")        ||
                k.startsWith("ship_orders:$fileKey:")
            ) {
                e.remove(k)
            }
        }
        e.apply()
    }
}
