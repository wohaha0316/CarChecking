package com.example.carchecking

data class UiConfig(
    // 가중치(가로 폭). 합계는 normalized()에서 1로 정규화
    var wNo: Float = 0.08f,
    var wBL: Float = 0.18f,
    var wHaju: Float = 0.18f,
    var wCar: Float = 0.36f,
    var wQty: Float = 0.08f,
    var wClear: Float = 0.06f,
    var wCheck: Float = 0.06f,

    // 글자 크기(sp)
    var fNo: Float = 11f,
    var fBL: Float = 13f,
    var fHaju: Float = 13f,
    var fCar: Float = 13f,
    var fQty: Float = 13f,
    var fClear: Float = 13f,
    var fCheck: Float = 13f,

    // 줄바꿈 옵션
    var wrapBL: Boolean = false,
    var wrapHaju: Boolean = false,

    // 행간(상대). 0 = 여백 없음 (기본 0)
    var rowSpacing: Float = 0f,

    // 구분선
    var showRowDividers: Boolean = true,

    // ★ VIN 강조 여부 (SettingsBottomSheet에서 쓰는 필드)
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
