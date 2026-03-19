package com.example.carchecking

import android.content.Context
import android.util.Log
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// ---- 데이터 모델 ----
data class VehicleSize(val lengthMm: Int, val widthMm: Int)
enum class WidthBand { W190_UP, W180_190, W180_DOWN } // idx 0,1,2

data class LengthBucket(
    val label: String,
    val lowerMm: Int?,
    val upperExclMm: Int?,
    val counts: IntArray = IntArray(3),
    val rowIdxByBand: Array<MutableList<Int>> = arrayOf(
        mutableListOf(), mutableListOf(), mutableListOf()
    )
)

data class SpecSummary(
    val stepMm: Int,
    val buckets: List<LengthBucket>,
    val unknownRows: List<Int>,
    val total: Int,
    val clearanceX: Int,
    val checked: Int,
    val shipped: Int
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
    out += LengthBucket(label = "5.0 이상", lowerMm = 5000, upperExclMm = null)

    var high = 5000
    while (true) {
        val low = high - stepMm
        if (low < 4000) break
        val lb = (low.toFloat() / 1000f)
        val hb = (high.toFloat() / 1000f)
        out += LengthBucket(
            label = String.format(Locale.US, "%.1f~%.1f", lb, hb),
            lowerMm = low,
            upperExclMm = high
        )
        high = low
    }

    out += LengthBucket(label = "4.0 미만", lowerMm = null, upperExclMm = 4000)
    return out
}

// ---- 사이즈 소스 ----
interface SizeResolver {
    fun resolve(row: CheckRow): VehicleSize?
}

/** carInfo 안에 전장/전폭 숫자가 직접 있을 때 파싱 */
object InlineSizeResolver : SizeResolver {
    private val lenRegex = Regex("""(?i)(전장|length|L)\s*[:：]?\s*([0-9]{3,4})\s*""")
    private val widRegex = Regex("""(?i)(전폭|width|W)\s*[:：]?\s*([0-9]{3,4})\s*""")

    override fun resolve(row: CheckRow): VehicleSize? {
        val t = row.carInfo
        val lm = lenRegex.find(t)?.groupValues?.getOrNull(2)?.toIntOrNull()
        val wm = widRegex.find(t)?.groupValues?.getOrNull(2)?.toIntOrNull()
        return if (lm != null && wm != null) VehicleSize(lm, wm) else null
    }
}

/** 마스터 제원표 조회 */
object MasterSpecResolver : SizeResolver {
    override fun resolve(row: CheckRow): VehicleSize? {
        return SpecMaster.findForRow(row)
    }
}

/** Inline → Master 순서 */
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
            if (size == null) {
                unknown += idx
                return@forEachIndexed
            }

            val bandIdx = when (bandOfWidth(size.widthMm)) {
                WidthBand.W190_UP -> 0
                WidthBand.W180_190 -> 1
                WidthBand.W180_DOWN -> 2
            }

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

// ---- 문자열 정규화 ----
private fun normalizeVehicleKey(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .uppercase(Locale.ROOT)
        .replace(Regex("""[\r\n\t]"""), " ")
        .replace(Regex("""[^0-9A-Z가-힣]+"""), "")
        .trim()
}

private fun removeKnownBrands(raw: String): String {
    if (raw.isBlank()) return raw
    var out = raw.uppercase(Locale.ROOT)
    val brands = listOf(
        "HYUNDAI", "KIA", "GENESIS", "BMW", "BENZ", "MERCEDES", "AUDI",
        "VOLKSWAGEN", "VW", "TOYOTA", "LEXUS", "HONDA", "NISSAN",
        "PORSCHE", "LANDROVER", "RANGE ROVER", "MINI", "VOLVO",
        "현대", "기아", "제네시스", "벤츠", "아우디", "폭스바겐", "토요타",
        "렉서스", "혼다", "닛산", "포르쉐", "랜드로버", "미니", "볼보"
    )
    brands.forEach { b ->
        out = out.replace(b.uppercase(Locale.ROOT), " ")
    }
    return normalizeVehicleKey(out)
}

// ---- 마스터 저장소 ----
object SpecMaster {
    private const val TAG = "SpecMaster"
    private const val ASSET_NAME = "vehicle_master.xlsx"
    private const val SHEET_NAME = "제원 정리"

    // key -> size
    private val map = linkedMapOf<String, VehicleSize>()
    @Volatile private var loaded = false

    fun put(key: String, size: VehicleSize) {
        val nk = normalizeVehicleKey(key)
        if (nk.isNotBlank()) map[nk] = size
    }

    fun get(key: String): VehicleSize? {
        val nk = normalizeVehicleKey(key)
        if (nk.isBlank()) return null
        return map[nk]
    }

    fun clear() {
        map.clear()
        loaded = false
    }

    fun size(): Int = map.size

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                loadFromAssets(context)
                loaded = true
                Log.d(TAG, "loaded master keys=${map.size}")
            }.onFailure { e ->
                Log.e(TAG, "failed to load $ASSET_NAME", e)
            }
        }
    }

    fun findForRow(row: CheckRow): VehicleSize? {
        val candidates = buildCandidates(row)
        if (candidates.isEmpty()) return null

        // 1차: exact match
        candidates.forEach { c ->
            get(c)?.let { return it }
        }

        val normalizedCandidates = candidates.map(::normalizeVehicleKey).filter { it.isNotBlank() }

        // 2차: normalized exact
        normalizedCandidates.forEach { c ->
            map[c]?.let { return it }
        }

        // 3차: brand removed exact
        val stripped = normalizedCandidates.map(::removeKnownBrands).filter { it.isNotBlank() }
        stripped.forEach { c ->
            map[c]?.let { return it }
        }

        // 4차: 짧은 모델명 전용 규칙
        // 예: QM6, SM6, K5, X5
        val modelTokens = extractModelTokens(row.carInfo)
        modelTokens.forEach { token ->
            val normalizedToken = normalizeVehicleKey(token)
            if (normalizedToken.length in 2..4) {
                map[normalizedToken]?.let { return it }
            }
        }

        // 5차: contains는 긴 후보만 제한적으로 허용
        normalizedCandidates
            .sortedByDescending { it.length }
            .forEach { c ->
                if (c.length < 5) return@forEach
                val hit = map.entries.firstOrNull { (k, _) ->
                    k.length >= 4 && (c.contains(k) || k.contains(c))
                }
                if (hit != null) return hit.value
            }

        // 6차: 브랜드 제거 후 contains
        stripped
            .sortedByDescending { it.length }
            .forEach { c ->
                if (c.length < 5) return@forEach
                val hit = map.entries.firstOrNull { (k, _) ->
                    k.length >= 4 && (c.contains(k) || k.contains(c))
                }
                if (hit != null) return hit.value
            }

        return null
    }

    private fun buildCandidates(row: CheckRow): List<String> {
        val raw = linkedSetOf<String>()
        val text = row.carInfo.trim()
        if (text.isBlank()) return emptyList()

        // 원문 자체를 최우선
        raw += text

        // 영어/숫자 토큰
        extractAsciiTokens(text).forEach { raw += it }

        // 한글/영문/숫자 토큰
        extractHangulTokens(text).forEach { raw += it }

        return raw.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun extractAsciiTokens(text: String): List<String> {
        val out = mutableListOf<String>()
        Regex("""[A-Za-z][A-Za-z0-9\s\-]{1,40}""").findAll(text).forEach { m ->
            out += m.value.trim()
        }
        return out
    }
    private fun extractModelTokens(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        return Regex("""[A-Za-z0-9가-힣]+""")
            .findAll(text.uppercase(Locale.ROOT))
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }
    private fun extractHangulTokens(text: String): List<String> {
        val out = mutableListOf<String>()
        Regex("""[가-힣A-Za-z0-9\s]{2,40}""").findAll(text).forEach { m ->
            out += m.value.trim()
        }
        return out
    }

    private fun loadFromAssets(context: Context) {
        map.clear()

        context.assets.open(ASSET_NAME).use { input ->
            WorkbookFactory.create(input).use { wb ->
                val sheet = wb.getSheet(SHEET_NAME) ?: wb.getSheetAt(0)
                if (sheet == null) return

                val headerRow = sheet.getRow(sheet.firstRowNum) ?: return
                val headerIndex = buildHeaderIndex(headerRow)

                val idxBrandModel = headerIndex["브랜드+모델명"] ?: 0
                val idxBrand = headerIndex["브랜드"]
                val idxModel = headerIndex["모델명(대표)"] ?: headerIndex["모델명"]
                val idxLen = headerIndex["전장"]
                val idxWid = headerIndex["전폭"]

                for (r in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue

                    val brandModel = cellString(row, idxBrandModel)
                    val brand = idxBrand?.let { cellString(row, it) }.orEmpty()
                    val model = idxModel?.let { cellString(row, it) }.orEmpty()
                    val len = idxLen?.let { cellInt(row, it) }
                    val wid = idxWid?.let { cellInt(row, it) }

                    if (brandModel.isBlank() && model.isBlank()) continue
                    if (len == null || wid == null) continue

                    val size = VehicleSize(len, wid)

                    registerAlias(brandModel, size)
                    registerAlias("$brand $model", size)
                    registerAlias(model, size)
                    registerAlias(removeKnownBrands("$brand $model"), size)
                    registerAlias(removeKnownBrands(brandModel), size)
                }
            }
        }
    }

    private fun registerAlias(raw: String?, size: VehicleSize) {
        val key = normalizeVehicleKey(raw)
        if (key.isNotBlank() && !map.containsKey(key)) {
            map[key] = size
        }
    }

    private fun buildHeaderIndex(row: Row): Map<String, Int> {
        val map = linkedMapOf<String, Int>()
        for (c in row.firstCellNum.toInt() until row.lastCellNum.toInt()) {
            val name = cellString(row, c).trim()
            if (name.isNotBlank()) map[name] = c
        }
        return map
    }

    private fun cellString(row: Row, col: Int): String {
        val cell = row.getCell(col) ?: return ""
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue?.trim().orEmpty()
                CellType.NUMERIC -> {
                    val n = cell.numericCellValue
                    if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    runCatching { cell.stringCellValue }.getOrElse {
                        runCatching { cell.numericCellValue.toString() }.getOrDefault("")
                    }.trim()
                }
                else -> ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun cellInt(row: Row, col: Int): Int? {
        val cell = row.getCell(col) ?: return null
        return try {
            when (cell.cellType) {
                CellType.NUMERIC -> cell.numericCellValue.toInt()
                CellType.STRING -> cell.stringCellValue
                    ?.replace(",", "")
                    ?.trim()
                    ?.toIntOrNull()
                CellType.FORMULA -> {
                    runCatching { cell.numericCellValue.toInt() }.getOrElse {
                        runCatching {
                            cell.stringCellValue?.replace(",", "")?.trim()?.toIntOrNull()
                        }.getOrNull()
                    }
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}