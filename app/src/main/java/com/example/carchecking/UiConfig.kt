package com.example.carchecking

data class UiConfig(
    // 열 너비(weight)
    var wNo: Float = 0.08f,
    var wBL: Float = 0.18f,
    var wHaju: Float = 0.18f,
    var wCar: Float = 0.36f,
    var wQty: Float = 0.10f,
    var wClear: Float = 0.10f,
    var wCheck: Float = 0.10f,
    // 글자 크기(sp)
    var fNo: Float = 11f,
    var fBL: Float = 13f,
    var fHaju: Float = 13f,
    var fCar: Float = 13f,
    var fQty: Float = 12f,
    var fClear: Float = 12f,
    var fCheck: Float = 12f,
    // 줄바꿈 옵션
    var wrapBL: Boolean = false,
    var wrapHaju: Boolean = false,
    // VIN 굵게
    var vinBold: Boolean = true,
    // 행간(행 하단 여백 계수: fCar * rowSpacing)
    var rowSpacing: Float = 0.35f,
    // 기본(RV) 구분선 표시
    var showRowDividers: Boolean = false
) {
    private fun clampWeights(): UiConfig = copy(
        wNo = wNo.coerceIn(0.04f, 0.40f),
        wBL = wBL.coerceIn(0.06f, 0.50f),
        wHaju = wHaju.coerceIn(0.06f, 0.50f),
        wCar = wCar.coerceIn(0.20f, 0.70f),
        wQty = wQty.coerceIn(0.06f, 0.25f),
        wClear = wClear.coerceIn(0.06f, 0.25f),
        wCheck = wCheck.coerceIn(0.06f, 0.30f)
    )

    /** weight 합이 1.0이 되도록 정규화 — 가로 스크롤 원천 차단 */
    private fun renormWeights(): UiConfig {
        val sum = (wNo + wBL + wHaju + wCar + wQty + wClear + wCheck).coerceAtLeast(0.0001f)
        return copy(
            wNo = wNo / sum,
            wBL = wBL / sum,
            wHaju = wHaju / sum,
            wCar = wCar / sum,
            wQty = wQty / sum,
            wClear = wClear / sum,
            wCheck = wCheck / sum
        )
    }

    private fun clampFonts(): UiConfig = copy(
        fNo = fNo.coerceIn(8f, 20f),
        fBL = fBL.coerceIn(8f, 26f),
        fHaju = fHaju.coerceIn(8f, 26f),
        fCar = fCar.coerceIn(8f, 26f),
        fQty = fQty.coerceIn(8f, 22f),
        fClear = fClear.coerceIn(8f, 22f),
        fCheck = fCheck.coerceIn(8f, 22f),
        rowSpacing = rowSpacing.coerceIn(0.10f, 0.80f)
    )

    fun normalized(): UiConfig = clampWeights().renormWeights().clampFonts()
    fun clamped(): UiConfig = normalized()

    companion object { fun defaults() = UiConfig().normalized() }
}
