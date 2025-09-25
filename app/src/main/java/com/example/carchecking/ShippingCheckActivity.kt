package com.example.carchecking

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.Locale
import com.example.carchecking.LogBus

class ShippingCheckActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var keyId: String
    private lateinit var currentFile: File

    private lateinit var tvStatus: TextView
    private lateinit var rv: RecyclerView

    private var rows: List<CheckRow> = emptyList()

    private val PREF_NAME = "carchecking_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shipping_check)

        tvStatus = findViewById(R.id.tvStatus)
        rv = findViewById(R.id.rvShipping)
        rv.layoutManager = LinearLayoutManager(this)
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        val path = intent.getStringExtra("filePath")
        if (path.isNullOrBlank()) { toast("파일 경로 없음"); finish(); return }
        currentFile = File(path)
        if (!currentFile.exists()) { toast("파일이 존재하지 않음"); finish(); return }

        keyId = ParsedCache.keyFor(currentFile).id()

        rows = runCatching { CarCheckExcelParser.parse(currentFile) }
            .onFailure { it.printStackTrace() }
            .getOrElse { toast("엑셀 읽기 오류"); emptyList() }

        rv.adapter = ShippingRowAdapter(
            rows = rows,
            readChecked = { idx -> readCheckedState(idx) },
            readCheckedOrder = { idx -> readCheckedOrder(idx) },  // ✅ 확인 순번 전달
            readShip = { idx -> readShipState(idx) },
            readShipOrder = { idx -> readShipOrder(idx) },
            onToggleShip = { idx -> toggleShip(idx) }
        )

        updateStatus()
    }

    // ===== 현황판 =====
    private fun updateStatus() {
        val total = rows.count { !it.isLabelRow }
        val clearanceX = rows.indices.count { !rows[it].isLabelRow && isClearanceX(rows[it].clearance) }
        val checked = rows.indices.count { !rows[it].isLabelRow && readCheckedState(it) }
        val shipped = rows.indices.count { !rows[it].isLabelRow && readShipState(it) }

        val html = "전체 <font color='#000000'>${String.format("%02d", total)}대</font>  " +
                "<font color='#CC0000'>면장X <b>${String.format("%02d", clearanceX)}대</b></font>  " +
                "<font color='#1E90FF'>확인 <b>${String.format("%02d", checked)}대</b></font>  " +
                "<font color='#008000'>선적 <b>${String.format("%02d", shipped)}대</b></font>"

        // 화면 표시
        tvStatus.text = fromHtmlCompat(html)
        tvStatus.gravity = Gravity.CENTER_HORIZONTAL

        // ✅ 메인에서 읽을 수 있도록 저장 (차체크 화면과 동일 키)
        val e = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        val base = "status:$keyId"
        e.putInt("$base:total", total)
        e.putInt("$base:clearanceX", clearanceX)
        e.putInt("$base:checked", checked)
        e.putInt("$base:shipped", shipped)
        e.putString("$base:html", html)
        e.apply()
    }

    private fun fromHtmlCompat(s: String) =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
            android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_LEGACY)
        else android.text.Html.fromHtml(s)
    private fun pad2(v: Int) = String.format(Locale.US, "%02d", v)

    override fun onResume() {
        super.onResume()
        LogBus.appOpen("선적화면")
    }

    override fun onPause() {
        LogBus.appClose("선적화면")
        super.onPause()
        updateStatus()   // ✅ 나갈 때 최종 저장
    }
    private fun isClearanceX(c: String): Boolean {
        val t = c.trim().lowercase(Locale.ROOT)
        return t in setOf("x","미통관","n","0","x표시","면장x","미처리","미")
    }

    // ===== 선적 상태 저장/조회 (순번 카운팅 유지) =====
    private fun readShipState(idx: Int): Boolean =
        prefs.getBoolean("ship_orders:${keyId}:${idx}_shipped", false)

    private fun readShipOrder(idx: Int): Int =
        prefs.getInt("ship_orders:${keyId}:${idx}_order", 0)

    private fun saveShipState(idx: Int, shipped: Boolean, order: Int) {
        prefs.edit()
            .putBoolean("ship_orders:${keyId}:${idx}_shipped", shipped)
            .putInt("ship_orders:${keyId}:${idx}_order", order)
            .apply()
    }

    private fun clearShipState(idx: Int) {
        prefs.edit()
            .putBoolean("ship_orders:${keyId}:${idx}_shipped", false)
            .putInt("ship_orders:${keyId}:${idx}_order", 0)
            .apply()
    }

    private fun nextOrder(): Int {
        val key = "ship_orders:${keyId}:orderCounter"
        val next = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, next).apply()
        return next
    }

    private fun decrementCounter() {
        val key = "ship_orders:${keyId}:orderCounter"
        val cur = prefs.getInt(key, 0)
        if (cur > 0) prefs.edit().putInt(key, cur - 1).apply()
    }

    private fun renumberAfterRemoval(removedOrder: Int) {
        if (removedOrder <= 0) return
        val e = prefs.edit()
        rows.indices.forEach { i ->
            val ord = readShipOrder(i)
            if (ord > removedOrder) e.putInt("ship_orders:${keyId}:${i}_order", ord - 1)
        }
        e.apply()
    }

    // 클릭에서만 호출: 안전 갱신(post)로 레이아웃 중 notify 방지
    // ShippingCheckActivity.kt 내부
    /** 선적 토글: (idx)행의 선적 여부를 뒤집고, 순번/현황/로그를 일관되게 처리한다. */
    private fun toggleShip(idx: Int): Pair<Boolean, Int> {
        val row = rows.getOrNull(idx) ?: return false to 0
        if (row.isLabelRow == true) return false to 0

        val wasShipped = readShipState(idx)        // 현재 선적 여부
        val bl = row.bl                             // B/L 번호 (프로젝트 필드명 그대로)
        val nthCheck = readCheckedOrder(idx)        // 확인 순번 (없으면 0을 리턴하도록 구현돼있어야 함)

        return if (!wasShipped) {
            // ===== 선적으로 전환 =====
            val ord = nextOrder()                   // 새 선적 순번
            saveShipState(idx, true, ord)           // 상태 저장(선적/순번)
            LogBus.shipAction(bl, nthCheck, ord)    // ★ 로그: 선적

            safeNotifyItemChanged(idx)              // 해당 아이템만 갱신
            updateStatus()                          // 현황판 업데이트
            true to ord
        } else {
            // ===== 선적 취소 =====
            val removedOrder = readShipOrder(idx)   // 지워질 순번을 먼저 읽어둔다
            clearShipState(idx)                     // 선적 상태/순번 제거
            renumberAfterRemoval(removedOrder)      // 뒤 번호 당겨오기
            decrementCounter()                      // 총계 감소 등

            if (removedOrder > 0) {
                LogBus.shipActionCancel(bl, nthCheck, removedOrder) // ★ 로그: 선적 취소
            }

            safeNotifyDataChanged()                 // 전체 새로고침(당겨온 순번 반영)
            updateStatus()
            false to 0
        }
    }

    private fun safeNotifyDataChanged() {
        val doNotify = { (rv.adapter as? ShippingRowAdapter)?.notifyDataSetChanged() }
        if (rv.isComputingLayout || rv.isLayoutRequested) rv.post { doNotify() } else doNotify()
    }
    private fun safeNotifyItemChanged(idx: Int) {
        val doNotify = { (rv.adapter as? ShippingRowAdapter)?.notifyItemChanged(idx) }
        if (rv.isComputingLayout || rv.isLayoutRequested) rv.post { doNotify() } else doNotify()
    }

    // ===== 차체크(확인) 상태 =====
    private fun readCheckedState(idx: Int): Boolean =
        prefs.getBoolean("check_orders:${keyId}:${idx}_checked", false)

    // ✅ 확인 순번(차체크에서 쓰던 값): 없으면 0
    private fun readCheckedOrder(idx: Int): Int =
        prefs.getInt("check_orders:${keyId}:${idx}_order", 0)

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).apply { setGravity(Gravity.CENTER, 0, 0) }.show()
}
