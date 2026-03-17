package com.example.carchecking

import org.apache.poi.hssf.usermodel.HSSFPalette
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.IdentityHashMap

/**
 * .xls를 .xlsx로 변환(색/폰트/테두리까지 최대 보존).
 * 이미 .xlsx면 원본 파일 그대로 반환.
 */
object ExcelConverter {

    /** .xls면 .xlsx로 변환하여 반환. 이미 .xlsx면 원본 반환 */
    fun convertXlsToXlsxIfNeeded(src: File): File {
        if (!src.name.endsWith(".xls", ignoreCase = true)) return src

        val parentDir: File = src.parentFile ?: src.absoluteFile.parentFile ?: File(".")
        val outFile = File(parentDir, src.nameWithoutExtension + ".xlsx")

        FileInputStream(src).use { fis ->
            val oldWb = HSSFWorkbook(fis)
            val newWb = XSSFWorkbook()

            try {
                for (s in 0 until oldWb.numberOfSheets) {
                    val oldSheet = oldWb.getSheetAt(s)
                    val newSheet = newWb.createSheet(safeSheetName(oldSheet.sheetName))

                    // 열 너비 복제
                    val maxCol = maxLastCellNum(oldSheet)
                    for (c in 0..maxCol) {
                        try {
                            newSheet.setColumnWidth(c, oldSheet.getColumnWidth(c))
                        } catch (_: Throwable) {
                        }
                    }

                    val styleCache = StyleCache(oldWb, newWb)

                    // 행/셀 복제
                    for (r in 0..oldSheet.lastRowNum) {
                        val oldRow = oldSheet.getRow(r) ?: continue
                        val newRow = newSheet.createRow(r)

                        try {
                            newRow.height = oldRow.height
                        } catch (_: Throwable) {
                        }

                        val lastCell = oldRow.lastCellNum.toInt().coerceAtLeast(0)
                        for (c in 0 until lastCell) {
                            val oldCell = oldRow.getCell(c) ?: continue
                            val newCell = newRow.createCell(c)

                            when (oldCell.cellType) {
                                CellType.STRING -> {
                                    try {
                                        newCell.setCellValue(oldCell.stringCellValue ?: "")
                                    } catch (_: Throwable) {
                                        newCell.setCellValue("")
                                    }
                                }

                                CellType.NUMERIC -> {
                                    try {
                                        newCell.setCellValue(oldCell.numericCellValue)
                                    } catch (_: Throwable) {
                                    }
                                }

                                CellType.BOOLEAN -> {
                                    try {
                                        newCell.setCellValue(oldCell.booleanCellValue)
                                    } catch (_: Throwable) {
                                    }
                                }

                                CellType.FORMULA -> {
                                    try {
                                        newCell.cellFormula = oldCell.cellFormula
                                    } catch (_: Throwable) {
                                        // 수식 복사 실패 시 표시값으로라도 최대한 보존
                                        try {
                                            when (oldCell.cachedFormulaResultType) {
                                                CellType.STRING -> newCell.setCellValue(oldCell.stringCellValue ?: "")
                                                CellType.NUMERIC -> newCell.setCellValue(oldCell.numericCellValue)
                                                CellType.BOOLEAN -> newCell.setCellValue(oldCell.booleanCellValue)
                                                else -> {}
                                            }
                                        } catch (_: Throwable) {
                                        }
                                    }
                                }

                                CellType.BLANK, CellType.ERROR, CellType._NONE -> {
                                    // 그대로 비워둠
                                }
                            }

                            try {
                                styleCache.mapCellStyle(oldCell.cellStyle)?.let { newCell.cellStyle = it }
                            } catch (_: Throwable) {
                            }
                        }
                    }

                    // 병합 복제
                    for (i in 0 until oldSheet.numMergedRegions) {
                        try {
                            val rg = oldSheet.getMergedRegion(i)
                            newSheet.addMergedRegion(
                                CellRangeAddress(
                                    rg.firstRow,
                                    rg.lastRow,
                                    rg.firstColumn,
                                    rg.lastColumn
                                )
                            )
                        } catch (_: Throwable) {
                        }
                    }
                }

                FileOutputStream(outFile).use { fos ->
                    newWb.write(fos)
                }
            } finally {
                try {
                    newWb.close()
                } catch (_: Throwable) {
                }
                try {
                    oldWb.close()
                } catch (_: Throwable) {
                }
            }
        }

        return outFile
    }

    private fun safeSheetName(name: String?): String {
        val base = (name ?: "Sheet").trim().ifEmpty { "Sheet" }
        return base.replace(Regex("[\\\\/*?:\\[\\]]"), "_")
    }

    private fun maxLastCellNum(sheet: Sheet): Int {
        var max = 0
        for (r in 0..sheet.lastRowNum) {
            val row = sheet.getRow(r) ?: continue
            max = maxOf(max, row.lastCellNum.toInt().coerceAtLeast(0))
        }
        return max
    }

    /**
     * HSSF -> XSSF 스타일/폰트/색상 매핑
     * POI 5.x 기준
     */
    private class StyleCache(
        private val oldWb: HSSFWorkbook,
        private val newWb: XSSFWorkbook
    ) {
        private val palette: HSSFPalette? = oldWb.customPalette
        private val styleMap = IdentityHashMap<CellStyle, XSSFCellStyle>()
        private val fontMap = IdentityHashMap<Font, XSSFFont>()
        private val dataFormat = newWb.createDataFormat()

        fun mapCellStyle(src: CellStyle?): XSSFCellStyle? {
            src ?: return null
            styleMap[src]?.let { return it }

            val dst = newWb.createCellStyle()
            styleMap[src] = dst

            // 정렬 / 줄바꿈
            try {
                dst.alignment = src.alignment
            } catch (_: Throwable) {
            }
            try {
                dst.verticalAlignment = src.verticalAlignment
            } catch (_: Throwable) {
            }
            try {
                dst.wrapText = src.wrapText
            } catch (_: Throwable) {
            }
            try {
                dst.indention = src.indention
            } catch (_: Throwable) {
            }
            try {
                dst.rotation = src.rotation
            } catch (_: Throwable) {
            }

            // 데이터 포맷
            val fmtStr = try {
                src.dataFormatString
            } catch (_: Throwable) {
                null
            }

            if (!fmtStr.isNullOrBlank()) {
                try {
                    dst.dataFormat = dataFormat.getFormat(fmtStr)
                } catch (_: Throwable) {
                }
            } else {
                try {
                    dst.dataFormat = src.dataFormat
                } catch (_: Throwable) {
                }
            }

            // 테두리
            try {
                dst.borderTop = src.borderTop
            } catch (_: Throwable) {
            }
            try {
                dst.borderBottom = src.borderBottom
            } catch (_: Throwable) {
            }
            try {
                dst.borderLeft = src.borderLeft
            } catch (_: Throwable) {
            }
            try {
                dst.borderRight = src.borderRight
            } catch (_: Throwable) {
            }

            // 테두리 색
            hssfIndexToXssfColor(src.topBorderColor)?.let { color ->
                try {
                    dst.setTopBorderColor(color)
                } catch (_: Throwable) {
                    try {
                        dst.topBorderColor = src.topBorderColor
                    } catch (_: Throwable) {
                    }
                }
            }
            hssfIndexToXssfColor(src.bottomBorderColor)?.let { color ->
                try {
                    dst.setBottomBorderColor(color)
                } catch (_: Throwable) {
                    try {
                        dst.bottomBorderColor = src.bottomBorderColor
                    } catch (_: Throwable) {
                    }
                }
            }
            hssfIndexToXssfColor(src.leftBorderColor)?.let { color ->
                try {
                    dst.setLeftBorderColor(color)
                } catch (_: Throwable) {
                    try {
                        dst.leftBorderColor = src.leftBorderColor
                    } catch (_: Throwable) {
                    }
                }
            }
            hssfIndexToXssfColor(src.rightBorderColor)?.let { color ->
                try {
                    dst.setRightBorderColor(color)
                } catch (_: Throwable) {
                    try {
                        dst.rightBorderColor = src.rightBorderColor
                    } catch (_: Throwable) {
                    }
                }
            }

            // 채우기
            try {
                dst.fillPattern = src.fillPattern
            } catch (_: Throwable) {
            }

            hssfIndexToXssfColor(src.fillForegroundColor)?.let { color ->
                try {
                    dst.setFillForegroundColor(color)
                } catch (_: Throwable) {
                    try {
                        dst.fillForegroundColor = src.fillForegroundColor
                    } catch (_: Throwable) {
                    }
                }
            }

            hssfIndexToXssfColor(src.fillBackgroundColor)?.let { color ->
                try {
                    dst.setFillBackgroundColor(color)
                } catch (_: Throwable) {
                    try {
                        dst.fillBackgroundColor = src.fillBackgroundColor
                    } catch (_: Throwable) {
                    }
                }
            }

            // 폰트
            try {
                val hssfFont = oldWb.getFontAt(src.fontIndex.toInt())
                dst.setFont(mapFont(hssfFont))
            } catch (_: Throwable) {
            }

            return dst
        }

        fun mapFont(src: Font?): XSSFFont {
            src ?: return newWb.createFont()
            fontMap[src]?.let { return it }

            val dst = newWb.createFont()
            fontMap[src] = dst

            try {
                dst.fontName = src.fontName
            } catch (_: Throwable) {
            }
            try {
                dst.fontHeightInPoints = src.fontHeightInPoints
            } catch (_: Throwable) {
            }

            // 굵게 / 기울임 / 밑줄 / 취소선
            try {
                dst.bold = src.bold
            } catch (_: Throwable) {
            }
            try {
                dst.italic = src.italic
            } catch (_: Throwable) {
            }
            try {
                dst.underline = src.underline
            } catch (_: Throwable) {
            }
            try {
                dst.strikeout = src.strikeout
            } catch (_: Throwable) {
            }

            // 폰트 색
            try {
                hssfIndexToXssfColor(src.color)?.let { dst.setColor(it) }
            } catch (_: Throwable) {
            }

            return dst
        }

        /** HSSF 팔레트 인덱스 -> XSSFColor(RGB) */
        private fun hssfIndexToXssfColor(idx: Short): XSSFColor? {
            val index = idx.toInt() and 0xFF
            if (index == 0) return null // AUTOMATIC

            val color = try {
                palette?.getColor(idx)
            } catch (_: Throwable) {
                null
            } ?: return null

            val triplet = try {
                color.triplet
            } catch (_: Throwable) {
                null
            } ?: return null

            val r = (triplet.getOrNull(0) ?: 0).toByte()
            val g = (triplet.getOrNull(1) ?: 0).toByte()
            val b = (triplet.getOrNull(2) ?: 0).toByte()

            return try {
                XSSFColor(byteArrayOf(r, g, b), null)
            } catch (_: Throwable) {
                null
            }
        }
    }
}