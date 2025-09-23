package com.example.carchecking

import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream

/** CheckListActivity.readExcel() 과 동일 로직 */
object CarCheckExcelParser {
    // CheckListActivity와 동일 인덱스
    private const val DEF_BL = 1
    private const val DEF_HAJU = 2
    private const val DEF_CAR = 3
    private const val DEF_QTY = 4
    private const val DEF_CLEAR = 7
    private const val DEF_DATA_START = 10

    fun parse(file: File): List<CheckRow> {
        val out = mutableListOf<CheckRow>()
        FileInputStream(file).use { fis ->
            val wb = if (file.name.endsWith(".xls", true)) HSSFWorkbook(fis) else XSSFWorkbook(fis)
            val sheet = wb.getSheetAt(0); val fmt = DataFormatter()
            for (r in DEF_DATA_START..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                val bl = getRaw(fmt, row, DEF_BL).trim()
                val haju = getRaw(fmt, row, DEF_HAJU).trim()
                val descRaw = getRaw(fmt, row, DEF_CAR)
                val qty = getRaw(fmt, row, DEF_QTY).trim()
                val clearance = getRaw(fmt, row, DEF_CLEAR).trim()

                // 완전 빈 줄 스킵
                if (bl.isEmpty() && haju.isEmpty() && descRaw.isEmpty() && qty.isEmpty() && clearance.isEmpty()) continue

                // 섹션 라벨(TERMINAL~)
                if (bl.startsWith("TERMINAL", true)) {
                    out.add(CheckRow(bl, "", "", "", "", isChecked = false, checkOrder = 0, isLabelRow = true))
                    continue
                }

                val carInfo = cleanMultiline(descRaw)
                out.add(CheckRow(bl, haju, carInfo, qty, clearance))
            }
            wb.close()
        }
        return out
    }

    private fun getRaw(fmt: DataFormatter, row: org.apache.poi.ss.usermodel.Row, idx: Int): String {
        val c = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
        return fmt.formatCellValue(c)
    }

    private fun cleanMultiline(src: String): String {
        val normalized = src.replace("\r\n", "\n").replace('\r', '\n')
        val specialSpaces = Regex("[\\u00A0\\u2007\\u202F\\u200B\\t]")
        return normalized.trim('\n', ' ', '\t', '\r')
            .split('\n')
            .map { it.replace(specialSpaces, " ").trim() }
            .filter { it.isNotEmpty() && !it.equals("USED CAR", true) }
            .joinToString("\n")
    }
}
