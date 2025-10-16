package com.example.carchecking

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * 엑셀 1개에 대한 액션 허브 화면.
 * 1줄: [엑셀명] [로그] [닫기]
 * 2줄: [현황 카운트]  → SpecSummaryActivity 로 이동
 * 3줄: [선적체크] [차체크]
 */
class FileActionsActivity : AppCompatActivity() {

    private lateinit var filePath: String
    private lateinit var fileKey: String

    private val PREF_NAME = "carchecking_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_actions)

        filePath = intent.getStringExtra("filePath") ?: run {
            Toast.makeText(this, "filePath가 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        fileKey = ParsedCache.keyFor(File(filePath)).id()

        // --- 1줄: 파일명 / 로그 / 닫기
        findViewById<Button>(R.id.btnFileName).apply {
            text = File(filePath).name
            setOnClickListener {
                Toast.makeText(this@FileActionsActivity, text, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }
        findViewById<Button>(R.id.btnClose).setOnClickListener { finish() }

        // --- 2줄: 현황 카운트 버튼
        val btnStatus = findViewById<Button>(R.id.btnStatus)
        btnStatus.text = buildStatusLine()
        btnStatus.setOnClickListener {
            // ✅ 여기서 현황(요약) 화면으로 연결
            startActivity(
                Intent(this, SpecSummaryActivity::class.java)
                    .putExtra("filePath", filePath)
            )
        }

        // --- 3줄: 선적체크 / 차체크
        findViewById<Button>(R.id.btnShipping).setOnClickListener {
            startActivity(Intent(this, ShippingCheckActivity::class.java).putExtra("filePath", filePath))
        }
        findViewById<Button>(R.id.btnChecking).setOnClickListener {
            startActivity(Intent(this, CheckListActivity::class.java).putExtra("filePath", filePath))
        }

        // 하단 파일 경로 참고용(선택)
        findViewById<TextView>(R.id.tvPath).text = filePath
    }

    /** CheckListActivity/ShippingCheckActivity 가 저장한 상태를 불러와 한 줄로 표시 */
    private fun buildStatusLine(): String {
        val sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val base = "status:$fileKey"
        val total = sp.getInt("$base:total", -1)
        val clearX = sp.getInt("$base:clearanceX", -1)
        val checked = sp.getInt("$base:checked", -1)
        val shipped = sp.getInt("$base:shipped", -1)
        return if (total >= 0 && clearX >= 0 && checked >= 0 && shipped >= 0) {
            "전체 $total  면장X $clearX  확인 $checked  선적 $shipped"
        } else {
            "현황(미계산) — 먼저 차체크/선적체크 화면을 한번 열어주세요"
        }
    }
}
