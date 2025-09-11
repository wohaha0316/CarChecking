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

    private lateinit var fileListLayout: LinearLayout
    private lateinit var btnSelectExcel: Button

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

        fileListLayout = findViewById(R.id.fileListLayout)
        btnSelectExcel = findViewById(R.id.btnSelectExcel)

        btnSelectExcel.setOnClickListener {
            // ✅ 엑셀 MIME 타입만 선택
            pickExcelFile.launch(arrayOf(
                "application/vnd.ms-excel", // .xls
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
            ))
        }

        renderSavedFiles()
    }

    override fun onResume() {
        super.onResume()
        // ✅ 차량관리 화면 다녀오면 현황 최신화
        renderSavedFiles()
    }

    /** 내부 저장소의 엑셀 목록을 표시 */
    private fun renderSavedFiles() {
        fileListLayout.removeAllViews()

        val files = (filesDir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".xls", true) || it.name.endsWith(".xlsx", true)) }
            .sortedBy { it.name.lowercase() }

        UploadedExcelStore.files.clear()
        UploadedExcelStore.files.addAll(files)

        files.forEach { addFileRow(it) }
    }

    /** 한 파일에 대한 5개 버튼(2줄) 렌더링 */
    private fun addFileRow(file: File) {
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
                // 원본 엑셀 외부앱으로 열기 (필요 시 내부 뷰어로 변경)
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
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("삭제 확인")
                    .setMessage("정말로 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        if (file.delete()) {
                            fileListLayout.removeView(group)
                            UploadedExcelStore.files.remove(file)
                            Toast.makeText(this@MainActivity, "${file.name} 삭제됨", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "삭제 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }

        topRow.addView(fileBtn)
        topRow.addView(logBtn)
        topRow.addView(delBtn)

        // 2줄: 현상황(70) + 차체크(30)
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

        val statusHtml = prefsNew.getString("status:$keyId:html", null)
            ?: prefsOld.getString("${file.absolutePath}|html", null)
            ?: run {
                val total = prefsNew.getInt("status:$keyId:total",
                    prefsOld.getInt("${file.absolutePath}|total", 0))
                val clearanceX = prefsNew.getInt("status:$keyId:clearanceX",
                    prefsOld.getInt("${file.absolutePath}|clearanceX", 0))
                val checked = prefsNew.getInt("status:$keyId:checked",
                    prefsOld.getInt("${file.absolutePath}|checked", 0))
                "전체 <font color='#000000'>${total} 대</font>  " +
                        "면장X <font color='#FF0000'>${clearanceX} 대</font>  " +
                        "확인 <font color='#0000FF'>${checked} 대</font>"
            }

        val statusBtn = Button(this).apply {
            text = fromHtmlCompat(statusHtml)  // fromHtmlCompat == htmlSpanned 동일기능
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f)
            gravity = Gravity.CENTER
            textSize = 13f
            isAllCaps = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener { /* 클릭 없음 */ }
        }

        val checkBtn = Button(this).apply {
            text = "차체크"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.3f)
            setOnClickListener {
                val intent = Intent(this@MainActivity, CheckListActivity::class.java)
                intent.putExtra("filePath", file.absolutePath)
                startActivity(intent)
            }
        }

        bottomRow.addView(statusBtn)
        bottomRow.addView(checkBtn)

        group.addView(topRow)
        group.addView(bottomRow)
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
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )
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
}
