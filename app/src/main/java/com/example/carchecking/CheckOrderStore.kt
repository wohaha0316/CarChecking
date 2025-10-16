package com.example.carchecking

import android.content.SharedPreferences

/**
 * 파일별(keyId) 전역 체크/순번 저장 헬퍼
 * - 저장/조회/정리만 담당
 */
class CheckOrderStore(
    private val prefs: SharedPreferences,
    private val keyId: String
) {
    private fun base(idx: Int) = "check_orders:$keyId:$idx"
    private fun isOrderKey(key: String)   = key.startsWith("check_orders:$keyId:") && key.endsWith("_order")
    private fun isCheckedKey(key: String) = key.startsWith("check_orders:$keyId:") && key.endsWith("_checked")

    fun readChecked(idx: Int): Boolean =
        prefs.getBoolean("${base(idx)}_checked", false)

    fun readOrder(idx: Int): Int =
        prefs.getInt("${base(idx)}_order", 0)

    /** 체크/순번 저장 (전역 인덱스 기준) */
    fun write(idx: Int, checked: Boolean, order: Int) {
        prefs.edit()
            .putBoolean("${base(idx)}_checked", checked)
            .putInt("${base(idx)}_order", order)
            .apply()
    }

    /** 현재 파일에서 _checked=true 가 하나라도 있는가 */
    fun anyCheckedInPrefs(): Boolean {
        for ((k, v) in prefs.all) {
            if (isCheckedKey(k) && v as? Boolean == true) return true
        }
        return false
    }

    /** 현재 파일의 저장된 최대 순번(_order 키의 최댓값) */
    fun maxOrderInPrefs(): Int {
        var maxOrder = 0
        for ((k, v) in prefs.all) {
            if (isOrderKey(k)) {
                val n = (v as? Int) ?: 0
                if (n > maxOrder) maxOrder = n
            }
        }
        return maxOrder
    }

    /**
     * 진입 시 정리:
     *  - 현재 파일에 _checked=true 가 하나도 없으면, 남아있는 _order 잔여키 전부 삭제.
     *    (예전 기록 때문에 첫 번호가 2부터 시작하는 문제 방지)
     */
    fun purgeOrdersIfNoChecked() {
        if (anyCheckedInPrefs()) return
        val e = prefs.edit()
        for (key in prefs.all.keys) if (key.startsWith("check_orders:$keyId:") && key.endsWith("_order")) e.remove(key)
        e.apply()
    }

    /** 전체 초기화(테스트/리셋용) */
    fun resetAll() {
        val e = prefs.edit()
        val prefix = "check_orders:$keyId:"
        for (key in prefs.all.keys) if (key.startsWith(prefix)) e.remove(key)
        e.apply()
    }
}
