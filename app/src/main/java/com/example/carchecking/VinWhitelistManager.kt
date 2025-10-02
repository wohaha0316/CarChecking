package com.example.carchecking

object VinWhitelistManager {
    /** 정규화된 17자리 VIN 전체 */
    private val all = hashSetOf<String>()
    /** 접두사(앞 8글자) → 빠른 후보 조회용 */
    private val byPrefix = hashMapOf<String, MutableSet<String>>()

    fun clear() {
        all.clear(); byPrefix.clear()
    }

    fun add(vin: String) {
        val n = VinUtils.normalize(vin) ?: return
        if (!VinUtils.isValidVin(n)) return
        if (all.add(n)) {
            val px = n.take(8)
            byPrefix.getOrPut(px) { mutableSetOf() }.add(n)
        }
    }

    /** 차량정보 텍스트에서 VIN들 긁어서 누적 */
    fun addFromRows(rows: List<CheckRow>) {
        for (r in rows) {
            if (r.isLabelRow) continue
            // 차량정보/기타 텍스트에서 전부 수집
            VinUtils.extractAll(r.carInfo).forEach { add(it) }
            // B/L, 화주에 섞여있을 가능성도 희박하지만 한 번 더
            VinUtils.extractAll(r.bl).forEach { add(it) }
            VinUtils.extractAll(r.haju).forEach { add(it) }
        }
    }

    fun contains(vin: String): Boolean {
        val n = VinUtils.normalize(vin) ?: return false
        return n in all
    }

    /** 접두사(앞 8자리) 기준 근접 후보 조회 */
    fun suggestByPrefix(vin: String): Set<String> {
        val n = VinUtils.normalizeLenient(vin) ?: return emptySet()
        return byPrefix[n.take(8)] ?: emptySet()
    }

    fun size(): Int = all.size
}
