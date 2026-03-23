package com.example.carchecking

import android.content.Context
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory

object VehicleMasterImporter {

    fun loadFromAssets(context: Context, assetName: String = "vehicle_master.xlsx"): MutableList<VehicleMasterItem> {
        val result = mutableListOf<VehicleMasterItem>()

        context.assets.open(assetName).use { input ->
            WorkbookFactory.create(input).use { wb ->
                val sheet = wb.getSheet("제원 정리") ?: wb.getSheetAt(0) ?: return result
                val headerRow = sheet.getRow(sheet.firstRowNum) ?: return result
                val headerIndex = buildHeaderIndex(headerRow)

                val idxBrand = headerIndex["브랜드"] ?: return result
                val idxModel = headerIndex["모델명(대표)"] ?: headerIndex["모델명"] ?: return result
                val idxLen = headerIndex["전장"] ?: return result
                val idxWid = headerIndex["전폭"] ?: return result

                for (r in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue

                    val brand = cellString(row, idxBrand)
                    val model = cellString(row, idxModel)
                    val len = cellInt(row, idxLen)
                    val wid = cellInt(row, idxWid)

                    if (brand.isBlank() && model.isBlank()) continue
                    if (len == null || wid == null) continue

                    result += VehicleMasterItem(
                        id = "${brand.trim()}_${model.trim()}",
                        brand = brand.trim(),
                        model = model.trim(),
                        lengthMm = len,
                        widthMm = wid
                    )
                }
            }
        }

        return result
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
                CellType.FORMULA -> runCatching { cell.stringCellValue }
                    .getOrElse { runCatching { cell.numericCellValue.toString() }.getOrDefault("") }
                    .trim()
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
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
                CellType.STRING -> cell.stringCellValue?.replace(",", "")?.trim()?.toIntOrNull()
                CellType.FORMULA -> runCatching { cell.numericCellValue.toInt() }
                    .getOrElse {
                        runCatching {
                            cell.stringCellValue?.replace(",", "")?.trim()?.toIntOrNull()
                        }.getOrNull()
                    }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}