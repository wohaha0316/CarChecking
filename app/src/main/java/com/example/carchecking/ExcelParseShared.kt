package com.example.carchecking

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import java.io.File
import java.io.FileInputStream
import java.util.Locale

object ExcelParseShared {

    data class CarRow(
        val no: String,
        val bl: String,
        val carInfo: String,
        val clearance: String,
        val qty: String,
        val shipper: String,
        val terminal: String,
        val rowIndex: Int,
        val isSection: Boolean = false,
        val sectionTitle: String = ""
    )

    fun parse(file: File): List<CarRow> {
        FileInputStream(file).use { fis ->
            val wb = WorkbookFactory.create(fis)
            val sh = wb.getSheetAt(0)

            // 1) 헤더 찾기 (상단 30행 내)
            var headerRow = -1
            var idxNo = -1; var idxBL = -1; var idxCar = -1; var idxClr = -1
            var idxQty = -1; var idxShipper = -1
            val idxTerminal = mutableListOf<Int>(); val idxPier = mutableListOf<Int>()

            for (r in 0..minOf(30, sh.lastRowNum)) {
                val row = sh.getRow(r) ?: continue
                for (c in 0..row.lastCellNum) {
                    val name = cellTextMerged(sh, r, c).trim().uppercase(Locale.ROOT)
                    if (name in listOf("NO", "순번")) idxNo = c
                    if (name.contains("B/L") || name == "BL") idxBL = c
                    if (isCarInfoHeader(name)) idxCar = c
                    if (name.contains("면장") || name.contains("CLEAR")) idxClr = c
                    if (name == "수" || name.contains("QTY")) idxQty = c
                    if (name.contains("화주") || name.contains("SHIPPER")) idxShipper = c
                    if (isTerminalHeader(name)) idxTerminal += c
                    if (isPierHeader(name)) idxPier += c
                }
                if (idxBL >= 0 && idxCar >= 0) { headerRow = r; break }
            }
            if (headerRow < 0) headerRow = 0

            // 2) 시작 행 = 헤더 "바로 다음 줄" (차체크와 동일)
            val start = headerRow + 1

            val out = mutableListOf<CarRow>()
            var noCounter = 1

            for (r in start..sh.lastRowNum) {
                // 섹션(TERMINAL- …)은 무조건 보존
                detectSectionTitle(sh, r)?.let { title ->
                    out += CarRow("", "", "", "", "", "", title, r, isSection = true, sectionTitle = title)
                    return@let
                } ?: run {
                    val bl  = safe(sh, r, idxBL).trim()
                    val car = safe(sh, r, idxCar).trim()
                    val clr = if (idxClr >= 0) safe(sh, r, idxClr).trim() else ""
                    val qty = if (idxQty >= 0) safe(sh, r, idxQty).trim() else ""
                    val shipper = if (idxShipper >= 0) safe(sh, r, idxShipper).trim() else ""
                    val terminal = mergeTerminal(
                        idxTerminal.map { safe(sh, r, it).trim() } + idxPier.map { safe(sh, r, it).trim() }
                    )

                    // 완전 빈줄만 스킵
                    if (bl.isBlank() && car.isBlank() && clr.isBlank() && qty.isBlank() && shipper.isBlank() && terminal.isBlank()) return@run

                    val no = if (idxNo >= 0) safe(sh, r, idxNo).trim().ifBlank { (noCounter++).toString() }
                    else (noCounter++).toString()

                    out += CarRow(no, bl, car, clr, qty, shipper, terminal, r)
                }
            }
            return out
        }
    }

    // ===== 섹션(TERMINAL- …) 탐지: 병합행/단일행 모두 허용 =====
    private fun detectSectionTitle(sh: Sheet, r: Int): String? {
        // 병합된 행에서 가로로 2칸 이상이면 우선 섹션 후보
        for (i in 0 until sh.numMergedRegions) {
            val rg = sh.getMergedRegion(i)
            if (r in rg.firstRow..rg.lastRow && rg.numberOfCells >= 2) {
                val t = cellText(sh.getRow(rg.firstRow)?.getCell(rg.firstColumn)).trim()
                if (isSectionText(t)) return t
            }
        }
        // 병합이 없어도 행 내 텍스트가 섹션 규칙에 맞으면 인정
        val row = sh.getRow(r) ?: return null
        for (c in 0..row.lastCellNum) {
            val t = cellTextMerged(sh, r, c).trim()
            if (isSectionText(t)) return t
        }
        return null
    }
    private fun isSectionText(t: String): Boolean {
        if (t.isBlank()) return false
        return Regex("^(TERMINAL|PIER|터미널|부두|선석)\\b[\\s\\-:·]*.*", RegexOption.IGNORE_CASE).matches(t)
    }

    // ===== 병합셀 안전 접근(차체크와 동일: top-left 값) =====
    private fun safe(sh: Sheet, r: Int, c: Int): String {
        if (r < 0 || c < 0) return ""
        return cellTextMerged(sh, r, c)
    }
    private fun cellTextMerged(sh: Sheet, r: Int, c: Int): String {
        val region = findMergedRegion(sh, r, c)
        val cell = if (region != null) sh.getRow(region.firstRow)?.getCell(region.firstColumn)
        else sh.getRow(r)?.getCell(c)
        return cellText(cell)
    }
    private fun findMergedRegion(sh: Sheet, r: Int, c: Int): CellRangeAddress? {
        for (i in 0 until sh.numMergedRegions) {
            val rg = sh.getMergedRegion(i)
            if (r in rg.firstRow..rg.lastRow && c in rg.firstColumn..rg.lastColumn) return rg
        }
        return null
    }
    private fun cellText(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue ?: ""
            CellType.NUMERIC -> {
                val d = cell.numericCellValue
                if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
            }
            CellType.BOOLEAN -> if (cell.booleanCellValue) "TRUE" else "FALSE"
            else -> cell.toString()
        }
    }

    // 헤더 인식
    private fun isCarInfoHeader(u: String): Boolean {
        val blocked = listOf("고가", "HIGH", "EXPENSIVE"); if (blocked.any { u.contains(it) }) return false
        val strong = listOf("차량정보","차량 정보","차종명","차종","차명","차량명","모델","MODEL","CAR INFO","CAR","VEHICLE","VEHICLE MODEL")
            .map { it.uppercase(Locale.ROOT) }
        if (strong.any { u == it || u.contains(it) }) return true
        if (u.contains("차량")) return true
        return false
    }
    private fun isTerminalHeader(u: String) = listOf("TERMINAL","터미널","TML").any { u.contains(it) }
    private fun isPierHeader(u: String) = listOf("PIER","부두","선석").any { u.contains(it) }
    private fun mergeTerminal(values: List<String>) =
        values.asSequence().map { it.trim() }
            .filter { it.isNotEmpty() && it != "-" && it.uppercase(Locale.ROOT) != "NULL" }
            .distinct().joinToString(" / ")
}
