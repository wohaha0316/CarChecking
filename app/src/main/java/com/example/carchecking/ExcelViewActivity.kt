package com.example.carchecking

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File

class ExcelViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excel_view)

        val rv = findViewById<RecyclerView>(R.id.recyclerViewExcelRows)
        rv.layoutManager = LinearLayoutManager(this)

        val path = intent.getStringExtra("filePath")
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "파일 경로 없음", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(path)
        val data = readExcel(file)
        rv.adapter = ExcelAdapter(data)
    }

    private fun readExcel(file: File): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            file.inputStream().use { input ->
                val workbook = if (file.name.endsWith(".xls", true)) {
                    HSSFWorkbook(input) // xls
                } else {
                    XSSFWorkbook(input) // xlsx
                }
                val sheet = workbook.getSheetAt(0)
                for (row in sheet) {
                    val cells = mutableListOf<String>()
                    for (cell in row) cells.add(cell.toString())
                    rows.add(cells)
                }
                workbook.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "엑셀을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
        return rows
    }
}
