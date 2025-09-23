package com.example.carchecking

data class UiConfig(
    // 가중치(가로 폭) — 합계는 normalized()에서 1로 정규화
    var wNo: Float = 0.05f,
    var wBL: Float = 0.18f,
    var wHaju: Float = 0.18f,
    var wCar: Float = 0.30f,
    var wQty: Float = 0.06f,
    var wClear: Float = 0.06f,
    var wCheck: Float = 0.12f,

    // 글자 크기(sp)
    var fNo: Float = 11f,
    var fBL: Float = 13f,
    var fHaju: Float = 13f,
    var fCar: Float = 13f,
    var fQty: Float = 12f,
    var fClear: Float = 12f,
    var fCheck: Float = 11f,

    // 줄 간격 배수(0 = 없음)
    var rowSpacing: Float = 0f,

    // 말줄임/줄바꿈 옵션
    var wrapBL: Boolean = false,
    var wrapHaju: Boolean = false,

    // 시각 옵션
    var showRowDividers: Boolean = true,

    // VIN 굵게(차체크에서 사용할 수 있는 플래그)
    var vinBold: Boolean = true
) {
    fun normalized(): UiConfig {
        val sum = (wNo + wBL + wHaju + wCar + wQty + wClear + wCheck).let { if (it <= 0f) 1f else it }
        wNo /= sum; wBL /= sum; wHaju /= sum; wCar /= sum; wQty /= sum; wClear /= sum; wCheck /= sum

        fun clampW(x: Float) = if (x.isFinite() && x > 0f) x else 0.1f
        wNo = clampW(wNo); wBL = clampW(wBL); wHaju = clampW(wHaju); wCar = clampW(wCar)
        wQty = clampW(wQty); wClear = clampW(wClear); wCheck = clampW(wCheck)

        if (rowSpacing < 0f) rowSpacing = 0f
        return this
    }

    companion object { fun defaults() = UiConfig() }
}
