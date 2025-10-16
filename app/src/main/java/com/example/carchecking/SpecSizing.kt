package com.example.carchecking

import android.content.Context
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// ---- 데이터 모델 ----
data class VehicleSize(val lengthMm: Int, val widthMm: Int)
enum class WidthBand { W190_UP, W180_190, W180_DOWN } // idx 0,1,2

data class LengthBucket(
    val label: String,          // 예: "5.0 이상", "4.8~5.0"
    val lowerMm: Int?,          // 포함(>=), 최상단은 null
    val upperExclMm: Int?,      // 미포함(<), 최하단은 null
    val counts: IntArray = IntArray(3), // 전폭 밴드별 카운트
    val rowIdxByBand: Array<MutableList<Int>> = arrayOf(
        mutableListOf(), mutableListOf(), mutableListOf()
    )
)

data class SpecSummary(
    val stepMm: Int,
    val buckets: List<LengthBucket>,
    val unknownRows: List<Int>,     // 사이즈 모름
    val total: Int, val clearanceX: Int, val checked: Int, val shipped: Int
)

// ---- 전폭 밴드 계산 ----
fun bandOfWidth(widthMm: Int): WidthBand = when {
    widthMm >= 1900 -> WidthBand.W190_UP
    widthMm >= 1800 -> WidthBand.W180_190
    else -> WidthBand.W180_DOWN
}

// ---- 구간 생성 (5.0m 이상 ~ 4.0m 미만) ----
fun buildLengthBuckets(stepMm: Int): List<LengthBucket> {
    require(stepMm in listOf(200, 150, 100)) { "step must be 200/150/100mm" }
    val out = mutableListOf<LengthBucket>()
    // Top: >= 5000
    out += LengthBucket(label = "5.0 이상", lowerMm = 5000, upperExclMm = null)

    var high = 5000
    while (true) {
        val low = high - stepMm
        if (low < 4000) break
        val lb = (low.toFloat() / 1000f)
        val hb = (high.toFloat() / 1000f)
        out += LengthBucket(
            label = String.format(Locale.US, "%.1f~%.1f", lb, hb),
            lowerMm = low, upperExclMm = high
        )
        high = low
    }
    // Bottom: < 4000
    out += LengthBucket(label = "4.0 미만", lowerMm = null, upperExclMm = 4000)
    return out
}

// ---- 사이즈 소스(차후 마스터 붙일 때 확장 가능) ----
interface SizeResolver { fun resolve(row: CheckRow): VehicleSize? }

/** carInfo 내에 '전장/전폭' 숫자가 직접 있을 때 파싱 */
object InlineSizeResolver : SizeResolver {
    // 예: "전장 4,950 전폭 1,920", "L:4950 W:1920", "전장: 5012mm" 등
    private val lenRegex = Regex("""(?i)(전장|length|L)\s*[:：]?\s*([0-9]{3,4})\s*""")
    private val widRegex = Regex("""(?i)(전폭|width|W)\s*[:：]?\s*([0-9]{3,4})\s*""")
    override fun resolve(row: CheckRow): VehicleSize? {
        val t = row.carInfo
        val lm = lenRegex.find(t)?.groupValues?.getOrNull(2)?.toIntOrNull()
        val wm = widRegex.find(t)?.groupValues?.getOrNull(2)?.toIntOrNull()
        return if (lm != null && wm != null) VehicleSize(lm, wm) else null
    }
}

/** 마스터 제원표(브랜드+모델→전장/전폭)를 조회 (지금은 비어있고, 나중에 CRUD/엑셀로 채움) */
object MasterSpecResolver : SizeResolver {
    override fun resolve(row: CheckRow): VehicleSize? {
        val key = extractModelKey(row)
        return SpecMaster.get(key) // null 허용
    }
    private fun extractModelKey(row: CheckRow): String {
        // 간단 키: (화주+차량정보) 중 알파벳/숫자/공백만 남기고 압축 → 대문자
        val raw = (row.haju + " " + row.carInfo)
        val key = raw.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        // 실제로는 브랜드/모델 분리 로직을 점차 고도화 예정
        return key
    }
}

/** 합성 리졸버: Inline → Master 순서로 조회 */
class CompositeResolver(
    private val a: SizeResolver = InlineSizeResolver,
    private val b: SizeResolver = MasterSpecResolver
) : SizeResolver {
    override fun resolve(row: CheckRow): VehicleSize? = a.resolve(row) ?: b.resolve(row)
}

// ---- 요약 집계 ----
object SpecAggregator {
    fun summarize(
        context: Context,
        fileKey: String,
        rows: List<CheckRow>,
        stepMm: Int,
        resolver: SizeResolver = CompositeResolver()
    ): SpecSummary {
        val buckets = buildLengthBuckets(stepMm).toMutableList()
        val unknown = mutableListOf<Int>()

        var total = 0
        var clearanceX = 0
        var checked = 0
        var shipped = 0

        rows.forEachIndexed { idx, r ->
            if (r.isLabelRow || r.bl.isBlank()) return@forEachIndexed
            total++
            if (r.clearance.equals("X", true)) clearanceX++
            if (r.isChecked) checked++
            if (ShipStateReader.read(context, fileKey, idx)) shipped++

            val size = resolver.resolve(r)
            if (size == null) { unknown += idx; return@forEachIndexed }

            val bandIdx = when (bandOfWidth(size.widthMm)) {
                WidthBand.W190_UP -> 0
                WidthBand.W180_190 -> 1
                WidthBand.W180_DOWN -> 2
            }
            // 길이 구간에 투입
            val len = size.lengthMm
            val b = buckets.firstOrNull { bk ->
                when {
                    bk.lowerMm == null && bk.upperExclMm != null -> len < bk.upperExclMm
                    bk.lowerMm != null && bk.upperExclMm == null -> len >= bk.lowerMm
                    else -> len >= (bk.lowerMm ?: Int.MIN_VALUE) && len < (bk.upperExclMm ?: Int.MAX_VALUE)
                }
            } ?: buckets.last()

            b.counts[bandIdx]++
            b.rowIdxByBand[bandIdx].add(idx)
        }

        return SpecSummary(stepMm, buckets, unknown, total, clearanceX, checked, shipped)
    }
}

// ---- 상태/설정 보조 ----
object ShipStateReader {
    fun read(ctx: Context, fileKey: String, rowIdx: Int): Boolean =
        ctx.getSharedPreferences("carchecking_prefs", Context.MODE_PRIVATE)
            .getBoolean("ship_orders:${fileKey}:${rowIdx}_shipped", false)
}

object SpecSummaryPrefs {
    private const val PREF = "spec_summary_prefs"
    private const val KEY_STEP = "length_step_mm"
    fun loadStep(ctx: Context): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_STEP, 200)
    fun saveStep(ctx: Context, stepMm: Int) {
        require(stepMm in listOf(200, 150, 100))
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_STEP, stepMm).apply()
    }
}

// ---- 마스터 저장소 (차후 CRUD/엑셀 연동) ----
object SpecMaster {
    // key: 간단 키(대문자, 영숫자/공백만)  -> VehicleSize
    private val map = linkedMapOf<String, VehicleSize>()

    fun put(key: String, size: VehicleSize) { map[key] = size }
    fun get(key: String): VehicleSize? = map[key]
    fun clear() = map.clear()

    // 샘플: 필요하면 임시로 몇 개 등록해서 테스트 가능
    fun seedForDemo() {
        put("BMW 730D", VehicleSize(5260, 1902))
        put("BMW 740LI", VehicleSize(5260, 1902))
        put("GENESIS G80", VehicleSize(5005, 1925))
        // ...
    }
}
