package com.example.carchecking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream

object ExcelReaders {
    suspend fun readRows(
        file: File,
        startRow: Int,
        onRow: (org.apache.poi.ss.usermodel.Row, DataFormatter) -> Unit
    ) = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            val wb = if (file.name.endsWith(".xls", true)) HSSFWorkbook(fis) else XSSFWorkbook(fis)
            val sheet = wb.getSheetAt(0)
            val fmt = DataFormatter()
            for (r in startRow..sheet.lastRowNum) {
                val row = sheet.getRow(r) ?: continue
                onRow(row, fmt)
            }
            wb.close()
        }
    }
}
