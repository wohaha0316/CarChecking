package com.example.carchecking

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Sheet

/**
 * 엑셀 색상 관련 유틸
 * 현재는 실행 방해 요소 제거를 위해 임시 비활성화.
 */
object ExcelColorUtils {

    fun getCellBackgroundHex(cell: Cell?): String? {
        return null
    }

    fun getMergedTopLeftCellBackgroundHex(sheet: Sheet, r: Int, c: Int): String? {
        return null
    }

    fun getStyleHex(style: CellStyle?): String? {
        return null
    }
}

/*package com.example.carchecking

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import kotlin.math.roundToInt

object ExcelColorUtils {

    /** 셀 객체에서 배경색을 HEX(#AARRGGBB)로 반환. 없으면 null */
    fun bgColorHex(cell: Cell?): String? {
        val style = try { cell?.cellStyle } catch (_: Throwable) { null } ?: return null
        val wb = style.sheet.workbook
        return styleToHex(style, wb)
    }

    /** (r,c)에 대한 배경색 – 병합영역을 고려해서 좌상단 스타일로 판단 */
    fun getCellBgArgb(sheet: Sheet, r: Int, c: Int, wb: Workbook): String? {
        val cell = ExcelParseShared.getTopLeftCellOfMerged(sheet, r, c)
        val style = try { cell?.cellStyle } catch (_: Throwable) { null } ?: return null
        return styleToHex(style, wb)
    }

    // ----- 내부 도우미 -----

    private fun styleToHex(style: CellStyle, wb: Workbook): String? {
        // 패턴이 없으면 배경색 없는 것으로 처리
        val fill = safeFillPattern(style)
        if (fill == null || fill == FillPatternType.NO_FILL) return null

        return when (wb) {
            is XSSFWorkbook -> xssfStyleToHex(style as? XSSFCellStyle)
            is HSSFWorkbook -> hssfStyleToHex(style, wb)
            else -> null
        }
    }

    private fun safeFillPattern(style: CellStyle): FillPatternType? = try {
        style.fillPattern
    } catch (_: Throwable) { null }

    // XSSF (xlsx)
    private fun xssfStyleToHex(cs: XSSFCellStyle?): String? {
        if (cs == null) return null

        // 우선순위: Foreground → Background
        val xc: XSSFColor? = try { cs.fillForegroundXSSFColor } catch (_: Throwable) { null }
            ?: try { cs.fillBackgroundXSSFColor } catch (_: Throwable) { null }

        return xssfColorToHex(xc)
    }

    private fun xssfColorToHex(c: XSSFColor?): String? {
        if (c == null) return null

        // 1) ARGB Hex (가장 간단)
        runCatching {
            val hex = try { c.argbHex } catch (_: Throwable) { c.getARGBHex() }
            if (!hex.isNullOrBlank()) return normalizeArgbHex(hex)
        }

        // 2) RGB + tint → ARGB
        val rgb = runCatching { c.rgb }.getOrNull()
        if (rgb != null && rgb.size >= 3) {
            var r = rgb[0].toInt() and 0xFF
            var g = rgb[1].toInt() and 0xFF
            var b = rgb[2].toInt() and 0xFF

            // tint 적용 (있을 경우)
            val tint = runCatching { c.tint }.getOrNull() ?: 0.0
            if (tint != 0.0) {
                val dr = applyTint(r, tint)
                val dg = applyTint(g, tint)
                val db = applyTint(b, tint)
                r = dr; g = dg; b = db
            }
            return String.format("#%02X%02X%02X%02X", 0xFF, r, g, b)
        }
        return null
    }

    private fun applyTint(channel: Int, tint: Double): Int {
        // 엑셀 tint 근사 적용
        val c = channel / 255.0
        val v = if (tint > 0) c * (1.0 - tint) + (1.0 - (1.0 - c) * (1.0 - tint)) else c * (1.0 + tint)
        return (v.coerceIn(0.0, 1.0) * 255.0).roundToInt().coerceIn(0, 255)
    }

    private fun normalizeArgbHex(hex: String): String {
        var h = hex.trim().removePrefix("#")
        // "RRGGBB" 형태면 FF 알파 붙여주기
        if (h.length == 6) return "#FF$h".uppercase()
        if (h.length == 8) return "#${h.uppercase()}"
        // 그 외 길이는 안전하게 null 처리
        return "#${h.uppercase()}"
    }

    // HSSF (xls)
    private fun hssfStyleToHex(style: CellStyle, wb: HSSFWorkbook): String? {
        val fgIdx = runCatching { style.fillForegroundColor }.getOrNull() ?: 0
        val bgIdx = runCatching { style.fillBackgroundColor }.getOrNull() ?: 0

        // 보통 xls는 Foreground가 실제 배경색
        val hexFg = hssfIndexToHex(wb, fgIdx)
        if (hexFg != null) return hexFg

        val hexBg = hssfIndexToHex(wb, bgIdx)
        return hexBg
    }

    private fun hssfIndexToHex(wb: HSSFWorkbook, idx: Short): String? {
        if ((idx.toInt() and 0xFF) == 0) return null // 자동색
        val c = wb.customPalette?.getColor(idx) ?: return null
        val triplet = c.triplet ?: return null
        val r = (triplet.getOrNull(0) ?: 0) and 0xFF
        val g = (triplet.getOrNull(1) ?: 0) and 0xFF
        val b = (triplet.getOrNull(2) ?: 0) and 0xFF
        return String.format("#%02X%02X%02X%02X", 0xFF, r, g, b)
    }
}
*/