package com.example.carchecking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class LogActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EXCEL_NAME = "extra_excel_name"
        private const val COLOR_BLUE = "#1E90FF"
        private const val COLOR_GREEN = "#008000"
        private const val COLOR_RED = "#CC0000"
    }

    private val all = mutableListOf<LogEntry>()   // 원본
    private val shown = mutableListOf<LogEntry>() // 필터링

    // 필터 상태
    private var fromTs: Long? = null
    private var toTs: Long? = null
    private var selExcel: String? = null
    private var selUser: String? = null
    private var selType: String = "전체"

    // UI
    private lateinit var tvFrom: TextView
    private lateinit var tvTo: TextView
    private lateinit var spExcel: Spinner
    private lateinit var spUser: Spinner
    private lateinit var spType: Spinner
    private lateinit var rv: RecyclerView
    private lateinit var adapter: LogAdapter

    private val sdfDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // 제목
        root.addView(TextView(this).apply {
            text = "로그"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        // 필터 바
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        // 1행: 시간 From/To
        val rowTime = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tvFrom = TextView(this).apply {
            text = "From: 전체"; setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { pickDateTime(true) }
        }
        tvTo = TextView(this).apply {
            text = "To: 전체"; setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { pickDateTime(false) }
        }
        val btnResetTime = Button(this).apply {
            text = "시간 초기화"
            setOnClickListener {
                fromTs = null; toTs = null
                tvFrom.text = "From: 전체"; tvTo.text = "To: 전체"
                applyFilter()
            }
        }
        rowTime.addView(tvFrom, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(tvTo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTime.addView(btnResetTime)
        bar.addView(rowTime)

        // 2행: 모선명 / 사용자
        val rowExcelUser = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
        }
        spExcel = Spinner(this)
        spUser = Spinner(this)
        rowExcelUser.addView(spExcel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowExcelUser.addView(spUser, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(rowExcelUser)

        // 3행: 내용필터 + 버튼
        val rowTypeAndBtns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(4), 0, 0)
        }
        spType = Spinner(this)

        val btnLogReset = Button(this).apply {
            text = "로그초기화"
            setOnClickListener {
                LogBus.clearAll()
                all.clear(); shown.clear(); adapter.submit(emptyList())
                Toast.makeText(this@LogActivity, "로그 전체 삭제됨", Toast.LENGTH_SHORT).show()
            }
        }
        val btnReset = Button(this).apply {
            text = "초기화"
            setOnClickListener { resetFilters() }
        }
        val btnApply = Button(this).apply {
            text = "적용"
            setOnClickListener { applyFilter() }
        }

        rowTypeAndBtns.addView(spType, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowTypeAndBtns.addView(btnLogReset)
        rowTypeAndBtns.addView(btnReset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6) })
        rowTypeAndBtns.addView(btnApply, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6) })

        bar.addView(rowTypeAndBtns)
        root.addView(bar)

        // 리스트
        rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@LogActivity) }
        adapter = LogAdapter()
        rv.adapter = adapter
        root.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        // 데이터 로드
        val defaultExcel = intent.getStringExtra(EXTRA_EXCEL_NAME)
        LogBus.feed.observe(this, Observer {
            all.clear(); all.addAll(it)
            setupSpinners(defaultExcel)
        })
    }

    // 날짜+시간 선택
    private fun pickDateTime(isFrom: Boolean) {
        val now = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            val cal = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
            TimePickerDialog(this, { _, hh, mm ->
                cal.set(Calendar.HOUR_OF_DAY, hh)
                cal.set(Calendar.MINUTE, mm)
                val txt = sdfDateTime.format(cal.time)
                if (isFrom) { fromTs = cal.timeInMillis; tvFrom.text = "From: $txt" }
                else { toTs = cal.timeInMillis; tvTo.text = "To: $txt" }
                applyFilter()
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setupSpinners(defaultExcel: String?) {
        // 모선명
        val excelNames = LogBus.allExcelNamesFromLogs()
        val excelLabels = mutableListOf("모선명: 전체")
        val excelMap = mutableMapOf<Int, String?>()
        excelMap[0] = null
        excelNames.forEachIndexed { idx, name ->
            excelLabels.add(cut(name, 20))
            excelMap[idx + 1] = name
        }
        spExcel.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, excelLabels)
        val defaultPos = findExcelDefaultPosition(excelMap, defaultExcel)
        spExcel.post {
            spExcel.setSelection(defaultPos, false)
            selExcel = excelMap[defaultPos]
            applyFilter()  // ✅ 자동 검색 실행
        }
        spExcel.onItemSelectedListener = simpleSel { pos -> selExcel = excelMap[pos] }

        // 사용자
        spUser.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("사용자: 전체", "me"))
        spUser.setSelection(0)
        spUser.onItemSelectedListener = simpleSel { pos -> selUser = if (pos == 1) "me" else null }

        // 내용
        val typeItems = listOf("내용: 전체", "내용: 확인", "내용: 선적", "내용: 기타")
        spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeItems)
        spType.setSelection(0, false)
        selType = "전체"
        spType.onItemSelectedListener = simpleSel { pos ->
            selType = when (pos) { 1 -> "확인"; 2 -> "선적"; 3 -> "기타"; else -> "전체" }
        }
    }

    private fun findExcelDefaultPosition(map: Map<Int, String?>, defaultExcel: String?): Int {
        if (defaultExcel.isNullOrBlank()) return 0
        return map.entries.firstOrNull { it.value == defaultExcel }?.key ?: 0
    }

    private fun resetFilters() {
        fromTs = null; toTs = null
        tvFrom.text = "From: 전체"; tvTo.text = "To: 전체"
        spExcel.setSelection(0); selExcel = null
        spUser.setSelection(0); selUser = null
        spType.setSelection(0); selType = "전체"
        applyFilter()
    }

    private fun applyFilter() {
        var list = if (fromTs == null && toTs == null) all.toList() else LogBus.loadAll(fromTs, toTs)
        list = list.filter { selUser == null || it.user == selUser }
        selExcel?.let { target ->
            val uploads = all.filter { it.content.startsWith("엑셀업로드(") }
                .map { it.ts to it.content.removePrefix("엑셀업로드(").removeSuffix(")") }
            val segments = mutableListOf<Pair<Long, Long>>()
            for (i in uploads.indices) {
                val (ts, name) = uploads[i]
                if (name != target) continue
                val end = if (i + 1 < uploads.size) uploads[i + 1].first - 1 else Long.MAX_VALUE
                segments.add(ts to end)
            }
            list = if (segments.isNotEmpty()) list.filter { e -> segments.any { e.ts in it.first..it.second } } else emptyList()
        }
        list = when (selType) {
            "확인" -> list.filter { it.content.contains("확인") && !it.content.contains("선적") }
            "선적" -> list.filter { it.content.contains("선적") }
            "기타" -> list.filter { !it.content.contains("확인") && !it.content.contains("선적") }
            else -> list
        }
        list = list.sortedByDescending { it.ts } // 최신 위
        shown.clear(); shown.addAll(list)
        adapter.submit(shown)
        rv.scrollToPosition(0)
    }

    private fun simpleSel(onSel: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSel(position)
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun cut(s: String, max: Int): String = if (s.length <= max) s else s.take(max) + ".."

    private inner class LogAdapter : RecyclerView.Adapter<LogVH>() {
        private val data = mutableListOf<LogEntry>()
        fun submit(list: List<LogEntry>) { data.clear(); data.addAll(list); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogVH {
            val row = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)) }
            val t1 = TextView(parent.context).apply { textSize = 12f }
            val t2 = TextView(parent.context).apply { textSize = 14f; setTypeface(typeface, Typeface.BOLD) }
            row.addView(t1); row.addView(t2)
            return LogVH(row, t1, t2)
        }
        override fun onBindViewHolder(h: LogVH, p: Int) {
            val e = data[p]
            h.t1.text = "${e.timeText()}  •  ${e.user}"
            val excel = resolveExcelForTs(e.ts)
            val prefix = if (excel != null) "[${cut(excel, 20)}] " else ""
            val full = prefix + e.content
            val sp = SpannableString(full)
            val patConfirm = Pattern.compile("(\\d+번\\s*확인)")
            val patShip = Pattern.compile("(\\d+번\\s*선적)")
            val patCancel = Pattern.compile("취소")
            var m = patConfirm.matcher(full)
            while (m.find()) sp.setSpan(ForegroundColorSpan(android.graphics.Color.parseColor(COLOR_BLUE)), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            m = patShip.matcher(full)
            while (m.find()) sp.setSpan(ForegroundColorSpan(android.graphics.Color.parseColor(COLOR_GREEN)), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            m = patCancel.matcher(full)
            while (m.find()) sp.setSpan(ForegroundColorSpan(android.graphics.Color.parseColor(COLOR_RED)), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            h.t2.text = sp
        }
        override fun getItemCount() = data.size
    }

    private class LogVH(root: LinearLayout, val t1: TextView, val t2: TextView) : RecyclerView.ViewHolder(root)

    private fun resolveExcelForTs(ts: Long): String? {
        val uploads = all.filter { it.content.startsWith("엑셀업로드(") }
            .map { it.ts to it.content.removePrefix("엑셀업로드(").removeSuffix(")") }
        if (uploads.isEmpty()) return null
        var chosen: String? = null
        for ((t, name) in uploads.sortedBy { it.first }) {
            if (t <= ts) chosen = name else break
        }
        return chosen
    }
}
