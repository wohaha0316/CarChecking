package com.example.carchecking

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

class ShippingCheckActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var file: File
    private lateinit var keyId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shipping_check)

        val path = intent.getStringExtra("filePath")
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "파일 경로 누락", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        file = File(path)
        keyId = try {
            // 프로젝트에 ParsedCache가 있으면 동일 키를 사용
            ParsedCache.keyFor(file).id()
        } catch (_: Throwable) {
            // 폴백: 파일 경로 MD5
            md5(file.absolutePath)
        }

        rv = findViewById(R.id.rvShipping)
        rv.layoutManager = LinearLayoutManager(this)
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        val rows = runCatching { parseExcelForShipping(file) }
            .getOrElse {
                it.printStackTrace()
                Toast.makeText(this, "엑셀 파싱 실패", Toast.LENGTH_SHORT).show()
                emptyList()
            }

        rv.adapter = ShippingRowAdapter(
            context = this,
            keyId = keyId,
            rows = rows,
            onToggleShip = { rowIndex, newValue ->
                saveShipState(keyId, rowIndex, newValue)
            },
            readChecked = { rowIndex ->
                // 차체크(기존 확인) 상태를 여러 가능 키로 조회 → 가능하면 O, 아니면 X
                readCheckedState(keyId, rowIndex)
            },
            readShip = { rowIndex ->
                readShipState(keyId, rowIndex)
            }
        )
    }

    // ====== 선적 상태 저장/조회 (SharedPreferences) ======
    private fun shipPrefs(): SharedPreferencesEx =
        SharedPreferencesEx(getSharedPreferences("carchecking_prefs", Context.MODE_PRIVATE))

    private fun saveShipState(keyId: String, rowIndex: Int, v: Boolean) {
        val p = shipPrefs()
        p.putBoolean("ship:$keyId:$rowIndex", v)
        // 필요하면 현황 집계 업데이트도 여기서 가능(요청 시 추가 구현)
    }
    private fun readShipState(keyId: String, rowIndex: Int): Boolean {
        val p = shipPrefs()
        // 우선 신규 키
        var v = p.getBoolean("ship:$keyId:$rowIndex", false)
        if (!v) {
            // 혹시 과거 키를 쓴 적이 있다면 추가 탐색
            v = p.getBoolean("shipping:$keyId:$rowIndex", false)
        }
        return v
    }

    // ====== 차체크(기존 확인) 상태 읽기 (읽기전용) ======
    private fun readCheckedState(keyId: String, rowIndex: Int): Boolean {
        // 신규/구키 모두 탐색
        val pNew = getSharedPreferences("carchecking_prefs", Context.MODE_PRIVATE)
        val pOld = getSharedPreferences("checklist_status", Context.MODE_PRIVATE)

        val candidates = listOf(
            "checked:$keyId:$rowIndex",
            "check:$keyId:$rowIndex",
            "${file.absolutePath}|checked:$rowIndex",
            "${file.absolutePath}|row:$rowIndex|checked"
        )
        for (k in candidates) {
            if (pNew.contains(k)) return pNew.getBoolean(k, false)
            if (pOld.contains(k)) return pOld.getBoolean(k, false)
        }
        return false
    }

    // ====== 엑셀 파싱: 헤더 자동감지 후 필요한 컬럼만 ======
    private fun parseExcelForShipping(file: File): List<ShipRow> {
        FileInputStream(file).use { fis ->
            val wb = WorkbookFactory.create(fis)
            val sh = wb.getSheetAt(0)

            // 헤더 찾기(상위 10행 안에서)
            var headerRowIdx = -1
            var idxNo = -1
            var idxBL = -1
            var idxCar = -1
            var idxClr = -1
            var idxQty = -1
            var idxShipper = -1
            for (r in 0..minOf(9, sh.lastRowNum)) {
                val row = sh.getRow(r) ?: continue
                val map = mutableMapOf<String, Int>()
                for (c in 0..row.lastCellNum) {
                    val name = cellText(row.getCell(c)).uppercase(Locale.ROOT).trim()
                    when {
                        name in listOf("NO", "순번") -> { idxNo = c; map["NO"] = c }
                        name.contains("B/L") || name == "BL" -> { idxBL = c; map["BL"] = c }
                        name.contains("차량") || name.contains("CAR") -> { idxCar = c; map["CAR"] = c }
                        name.contains("면장") || name.contains("CLEAR") -> { idxClr = c; map["CLR"] = c }
                        name.contains("수") || name.contains("QTY") -> { idxQty = c; map["QTY"] = c }
                        name.contains("화주") || name.contains("SHIPPER") -> { idxShipper = c; map["SHIPPER"] = c }
                    }
                }
                if (idxBL >= 0 && idxCar >= 0) {
                    headerRowIdx = r
                    break
                }
            }
            if (headerRowIdx < 0) headerRowIdx = 0 // 폴백

            val rows = mutableListOf<ShipRow>()
            var noCounter = 1
            for (r in headerRowIdx + 1..sh.lastRowNum) {
                val row = sh.getRow(r) ?: continue
                val bl = cellText(row.getCell(idxBL)).trim()
                val car = cellText(row.getCell(idxCar)).trim()
                val clr = if (idxClr >= 0) cellText(row.getCell(idxClr)).trim() else ""
                val qty = if (idxQty >= 0) cellText(row.getCell(idxQty)).trim() else ""
                val shipper = if (idxShipper >= 0) cellText(row.getCell(idxShipper)).trim() else ""

                // 데이터 종료 판단(주요 필드가 비면 스킵)
                if (bl.isBlank() && car.isBlank()) continue

                rows.add(
                    ShipRow(
                        no = if (idxNo >= 0) cellText(row.getCell(idxNo)).trim().ifBlank { (noCounter++).toString() } else (noCounter++).toString(),
                        bl = bl,
                        carInfo = car,
                        clearance = clr,
                        qty = qty,
                        shipper = shipper,
                        rowIndex = r // 파일 내 원행 인덱스 사용
                    )
                )
            }
            return rows
        }
    }

    private fun cellText(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue ?: ""
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                val d = cell.numericCellValue
                if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
            }
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> if (cell.booleanCellValue) "TRUE" else "FALSE"
            else -> cell.toString()
        }
    }

    private fun md5(s: String): String {
        val md = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }
}

/** 표시용 행 데이터 (화주/수(QTY)는 보유하지만 화면엔 안씀) */
data class ShipRow(
    val no: String,
    val bl: String,
    val carInfo: String,
    val clearance: String,
    val qty: String,
    val shipper: String,
    val rowIndex: Int
)
