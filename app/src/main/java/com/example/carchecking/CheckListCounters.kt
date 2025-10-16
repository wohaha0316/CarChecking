package com.example.carchecking

import android.content.Context
import android.content.Context.MODE_PRIVATE

/**
 * 현황 카운터(전체/면장X/확인/선적) HTML 문자열을 SharedPreferences에서 읽어 만들어 준다.
 * CheckListActivity / MainActivity에서 쓰는 키 체계와 동일하게 맞춤.
 */
object CheckListCounters {
    private const val PREF_NAME = "carchecking_prefs"

    fun buildStatusHtml(ctx: Context, keyId: String): String {
        val p = ctx.getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        val total      = p.getInt("status:$keyId:total", 0)
        val clearanceX = p.getInt("status:$keyId:clearanceX", 0)
        val checked    = p.getInt("status:$keyId:checked", 0)
        val shipped    = p.getInt("status:$keyId:shipped", 0) // 없으면 0

        // 색상/포맷은 기존 화면과 동일하게 유지
        var s = "전체 <font color='#000000'>${total} 대</font>  " +
                "면장X <font color='#CC0000'>${clearanceX} 대</font>  " +
                "확인 <font color='#1E90FF'>${checked} 대</font>  " +
                "선적 <font color='#008000'>${shipped} 대</font>"

        // 혹시 이전 포맷이 섞여 있어도 색상 통일
        s = s.replace("#FF0000", "#CC0000")
            .replace("#ff0000", "#CC0000")
            .replace("#1e90ff", "#1E90FF")

        return s
    }
}
