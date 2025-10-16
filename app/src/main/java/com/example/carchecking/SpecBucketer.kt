package com.example.carchecking

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object SpecBucketer {

    data class LenBucket(
        val label: String, // 예: "4.85 - 5.00" or "5.0 이상" or "4.0 미만"
        val minMm: Int?,   // mm 단위. 상단 "이상"은 min만 사용, 하단 "미만"은 max만 사용
        val maxMm: Int?
    )

    data class Detail(
        val rowIndex: Int,
        val row: CheckRow,
        val lenMm: Int?,
        val widthMm: Int?,
        val source: Char // 'O' override, 'T' text, '-' none
    )

    /** carInfo에서 전장/전폭 후보 추출 (mm). 근처 키워드에 가산점. */
    fun parseLenWidthFromText(carInfo: String): Pair<Int?, Int?> {
        if (carInfo.isBlank()) return null to null
        val txt = carInfo.replace("\r\n", "\n").replace('\r', '\n')
        val numRegex = Regex("""\b([1-6]\d{3})\b""") // 1000~6999
        val tokens = txt.lowercase()

        var bestLen: Int? = null
        var bestLenScore = Int.MIN_VALUE
        var bestW: Int? = null
        var bestWScore = Int.MIN_VALUE

        numRegex.findAll(txt).forEach { m ->
            val n = m.groupValues[1].toInt()
            val spanStart = m.range.first
            val windowStart = max(0, spanStart - 12)
            val ctx = tokens.substring(windowStart, min(tokens.length, spanStart + 12))

            // 전장 후보
            if (n in 4200..6200) {
                var s = 0
                if (ctx.contains("전장") || ctx.contains("length") || ctx.contains("l/")) s += 2
                if (ctx.contains("차대") || ctx.contains("vin")) s -= 1
                if (s >= bestLenScore) { bestLenScore = s; bestLen = n }
            }
            // 전폭 후보
            if (n in 1600..2300) {
                var s = 0
                if (ctx.contains("전폭") || ctx.contains("width") || ctx.contains("w/")) s += 2
                if (s >= bestWScore) { bestWScore = s; bestW = n }
            }
        }
        return bestLen to bestW
    }

    /** 20/15/10 cm 간격으로 5.00 이상 ~ 4.00 미만 범위를 생성 */
    fun buildLenBuckets(stepCm: Int): List<LenBucket> {
        val stepMm = stepCm * 10
        val top = LenBucket("5.0 이상", 5000, null)
        val bottom = LenBucket("4.0 미만", null, 4000)

        val list = mutableListOf<LenBucket>()
        list += top

        var hi = 5000
        while (hi > 4000) {
            val lo = max(4000, hi - stepMm)
            if (hi == 5000) {
                // 바로 아래 구간부터 표시
            }
            val label = String.format("%.2f - %.2f", lo / 1000.0, hi / 1000.0)
            list += LenBucket(label, lo, hi)
            hi = lo
        }

        list += bottom
        return list
    }

    /** 전폭 3분류 인덱스: 0=1.9이상, 1=1.8~1.9, 2=1.8이하 */
    fun widthBand(widthMm: Int?): Int {
        if (widthMm == null) return -1
        return when {
            widthMm >= 1900 -> 0
            widthMm >= 1800 -> 1
            else -> 2
        }
    }

    /** 상세행 구성: Override > Text 추정 > 없음 */
    fun buildDetails(
        keyId: String,
        rows: List<CheckRow>,
        overrides: Map<String, SpecOverride>
    ): List<Detail> {
        val out = ArrayList<Detail>(rows.size)
        rows.forEachIndexed { idx, r ->
            if (r.isLabelRow) return@forEachIndexed
            val ov = overrides[r.bl]
            val text = parseLenWidthFromText(r.carInfo)

            val len = ov?.lenMm ?: text.first
            val wid = ov?.widthMm ?: text.second
            val src = when {
                ov?.lenMm != null || ov?.widthMm != null -> 'O'
                text.first != null || text.second != null -> 'T'
                else -> '-'
            }
            out += Detail(idx, r, len, wid, src)
        }
        return out
    }

    /** 요약 카운트 계산: [행버킷][폭밴드] */
    fun summarize(
        buckets: List<LenBucket>,
        details: List<Detail>
    ): Array<IntArray> {
        val h = buckets.size
        val w = 3 // width bands
        val grid = Array(h) { IntArray(w) }

        details.forEach { d ->
            val len = d.lenMm
            val wb = widthBand(d.widthMm)
            if (len == null || wb < 0) return@forEach
            val rowIdx = bucketIndex(buckets, len)
            if (rowIdx >= 0) grid[rowIdx][wb]++
        }
        return grid
    }

    fun bucketIndex(buckets: List<LenBucket>, lenMm: Int): Int {
        buckets.forEachIndexed { i, b ->
            if (b.minMm != null && b.maxMm == null) { // 이상
                if (lenMm >= b.minMm) return i
            } else if (b.minMm == null && b.maxMm != null) { // 미만
                if (lenMm < b.maxMm) return i
            } else if (b.minMm != null && b.maxMm != null) {
                if (lenMm in b.minMm until b.maxMm) return i
            }
        }
        return -1
    }
}
