package com.example.carchecking

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.TextUtils
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnSelectExcel: Button
    private lateinit var fileListLayout: LinearLayout

    // ✅ 엑셀 파일 선택 런처 (Activity 버전)
    private val pickExcelFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val name = getFileNameFromUri(uri)
            val saved = saveFileToInternal(name, uri)
            // 스토어/목록 갱신
            if (UploadedExcelStore.files.none { it.absolutePath == saved.absolutePath }) {
                UploadedExcelStore.files.add(saved)
            }
            renderSavedFiles()
            Toast.makeText(this, "${saved.name} 업로드 완료", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "업로드 실패", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSelectExcel = findViewById(R.id.btnSelectExcel)
        fileListLayout = findViewById(R.id.fileListLayout)

        btnSelectExcel.setOnClickListener {
            pickExcelFile.launch(arrayOf(
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
        }

        renderSavedFiles()
    }

    private fun renderSavedFiles() {
        fileListLayout.removeAllViews()

        val files = (filesDir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".xls", true) || it.name.endsWith(".xlsx", true)) }
            .sortedBy { it.name.lowercase() }

        UploadedExcelStore.files.clear()
        UploadedExcelStore.files.addAll(files)

        files.forEach { addFileRow(it) }
    }

    fun addFileRow(file: File) {
        // 그룹 컨테이너(테두리)
        val group = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 12, 8, 12) }
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }

        // 1줄: 파일명(70) + 로그(20) + X(10)
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            )
        }

        val fileBtn = Button(this).apply {
            text = file.name
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f)
            setOnClickListener {
                // 원본 엑셀 외부앱으로 열기
                openInExternalExcel(file)
            }
        }

        val logBtn = Button(this).apply {
            text = "로그"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.2f)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "로그 기능은 추후 구현 예정", Toast.LENGTH_SHORT).show()
            }
        }

        val delBtn = Button(this).apply {
            text = "X"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.1f)
            setOnClickListener {
                try {
                    if (file.exists()) file.delete()
                    UploadedExcelStore.files.removeAll { it.absolutePath == file.absolutePath }

                    // ✅ 여기 추가: 이 파일의 keyId/레거시 경로에 해당하는 모든 상태/카운터 삭제
                    val keyIdDel = ParsedCache.keyFor(file).id()
                    val pNew = getSharedPreferences("carchecking_prefs", Context.MODE_PRIVATE)
                    val pOld = getSharedPreferences("checklist_status", Context.MODE_PRIVATE)

                    // 새 포맷 키들 제거 (status:/check_orders:/ship_orders:)
                    run {
                        val e = pNew.edit()
                        for (k in pNew.all.keys) {
                            if (k.startsWith("status:$keyIdDel:") ||
                                k.startsWith("check_orders:$keyIdDel:") ||
                                k.startsWith("ship_orders:$keyIdDel:")) {
                                e.remove(k)
                            }
                        }
                        // 카운터 제거
                        e.remove("check_orders:$keyIdDel:orderCounter")
                        e.remove("ship_orders:$keyIdDel:orderCounter")
                        e.apply()
                    }

                    // 구버전 키 제거 ("<absolutePath>|" 프리픽스)
                    run {
                        val e = pOld.edit()
                        val prefix = file.absolutePath + "|"
                        for (k in pOld.all.keys) {
                            if (k.startsWith(prefix)) e.remove(k)
                        }
                        e.apply()
                    }

                    renderSavedFiles()
                    Toast.makeText(this@MainActivity, "삭제 완료", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "삭제 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }


        topRow.addView(fileBtn)
        topRow.addView(logBtn)
        topRow.addView(delBtn)

        // 2줄: 현황 버튼만(풀폭)
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            )
        }

        // ✅ 현황 버튼: SharedPreferences에서 파일별 상태 불러옴(색상강조 유지)
        val prefs = getSharedPreferences("checklist_status", Context.MODE_PRIVATE)
        val prefsNew = getSharedPreferences("carchecking_prefs", Context.MODE_PRIVATE)
        val prefsOld = getSharedPreferences("checklist_status", Context.MODE_PRIVATE)
        val keyId = ParsedCache.keyFor(file).id()

// statusHtml 로드 (기존)
        var statusHtml = prefsNew.getString("status:$keyId:html", null)
            ?: prefsOld.getString("${file.absolutePath}|html", null)
            ?: run {
                val total = prefsNew.getInt("status:$keyId:total",
                    prefsOld.getInt("${file.absolutePath}|total", 0))
                val clearanceX = prefsNew.getInt("status:$keyId:clearanceX",
                    prefsOld.getInt("${file.absolutePath}|clearanceX", 0))
                val checked = prefsNew.getInt("status:$keyId:checked",
                    prefsOld.getInt("${file.absolutePath}|checked", 0))
                // ✅ 업로드 직후에도 '선적 0 대'를 기본으로 포함
                "전체 <font color='#000000'>${total} 대</font>  " +
                        "면장X <font color='#CC0000'>${clearanceX} 대</font>  " +
                        "확인 <font color='#1E90FF'>${checked} 대</font>  " +
                        "선적 <font color='#008000'>0 대</font>"
            }

// ✅ normalize: 누락된 '선적' 세그먼트 보강 + 색상 통일
        if (statusHtml != null) {
            if (!statusHtml!!.contains("선적")) {
                statusHtml = statusHtml + "  선적 <font color='#008000'>0 대</font>"
            }
            statusHtml = statusHtml!!
                .replace("#FF0000", "#CC0000") // 빨강 통일
                .replace("#ff0000", "#CC0000")
                .replace("#1e90ff", "#1E90FF") // 파랑 케이스 통일
        }



        val statusBtn = Button(this).apply {
            text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                android.text.Html.fromHtml(statusHtml, android.text.Html.FROM_HTML_MODE_LEGACY)
            else
                android.text.Html.fromHtml(statusHtml)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isAllCaps = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            textSize = 16f                                    // ✅ +2pt
            typeface = android.graphics.Typeface.DEFAULT_BOLD // ✅ 굵게
            setOnClickListener { /* 클릭 없음 */ }
        }
        bottomRow.addView(statusBtn)

        // 3줄: 선적 체크(왼쪽) + 차체크(오른쪽)
        val thirdRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            )
        }

        val shippingBtn = Button(this).apply {
            text = "선적 체크"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f)
            setOnClickListener {
                val intent = Intent(this@MainActivity, ShippingCheckActivity::class.java)
                intent.putExtra("filePath", file.absolutePath)
                startActivity(intent)
            }
        }

        val checkBtn = Button(this).apply {
            text = "차체크"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.5f)
            setOnClickListener {
                val intent = Intent(this@MainActivity, CheckListActivity::class.java)
                intent.putExtra("filePath", file.absolutePath)
                startActivity(intent)
            }
        }

        group.addView(topRow)
        group.addView(bottomRow)
        thirdRow.addView(shippingBtn)
        thirdRow.addView(checkBtn)
        group.addView(thirdRow)
        fileListLayout.addView(group)
    }

    // ---------- 업로드 유틸 ----------

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "unknown.xlsx"
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
        }
        return name
    }

    private fun saveFileToInternal(fileName: String, uri: Uri): File {
        val safe = if (fileName.isBlank()) "unknown.xlsx" else fileName
        val out = File(filesDir, safe)
        contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun openInExternalExcel(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val mime = if (file.name.endsWith(".xls", true))
            "application/vnd.ms-excel"
        else
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "열 수 있는 엑셀 앱이 없습니다", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onResume() {
        super.onResume()
        renderSavedFiles()  // ✅ 돌아올 때 현황 버튼/리스트 다시 그리기
    }
}
