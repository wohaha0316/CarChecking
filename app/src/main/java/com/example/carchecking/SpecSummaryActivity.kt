package com.example.carchecking

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class SpecSummaryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "filePath"
    }

    private lateinit var keyId: String
    private lateinit var currentFile: File
    private lateinit var rows: List<CheckRow>

    private lateinit var db: AppDatabase
    private lateinit var ovDao: SpecOverrideDao

    // UI
    private lateinit var btnCrud: Button
    private lateinit var btnSettings: Button
    private lateinit var tvCounters: TextView
    private lateinit var summaryContainer: LinearLayout
    private lateinit var rvDetails: RecyclerView
    private lateinit var detailsAdapter: DetailAdapter

    // 상태
    private var stepCm: Int = 20
    private var buckets: List<SpecBucketer.LenBucket> = emptyList()
    private var details: List<SpecBucketer.Detail> = emptyList()
    private var grid: Array<IntArray> = emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ===== 레이아웃 (코드로 구성) =====
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(8))
        }
        setContentView(root)

        // 1줄: 버튼 2개
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        btnCrud = Button(this).apply {
            text = "제원정리 표"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnSettings = Button(this).apply {
            text = "설정"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showSettings() }
        }
        top.addView(btnCrud); top.addView(btnSettings)
        root.addView(top)

        // 2줄: 현황 카운터 텍스트 (Main과 동일 포맷)
        tvCounters = TextView(this).apply {
            textSize = 16f
            setPadding(dp(4))
        }
        root.addView(tvCounters)

        // 3줄: 요약 그리드
        summaryContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(summaryContainer)

        // 4줄: 상세 Recycler
        rvDetails = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SpecSummaryActivity)
            addItemDecoration(DividerItemDecoration(this@SpecSummaryActivity, DividerItemDecoration.VERTICAL))
        }
        detailsAdapter = DetailAdapter()
        rvDetails.adapter = detailsAdapter
        root.addView(rvDetails, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // ===== 데이터 로딩 =====
        val path = intent.getStringExtra(EXTRA_FILE_PATH) ?: run { finish(); return }
        currentFile = File(path)
        keyId = ParsedCache.keyFor(currentFile).id()
        db = AppDatabase.get(this)
        ovDao = db.specOverrides()

        lifecycleScope.launch {
            rows = withContext(Dispatchers.IO) {
                val cacheKey = ParsedCache.keyFor(currentFile)
                val cached = ParsedCache.read(filesDir, cacheKey)
                (cached ?: emptyList())
            } ?: emptyList()

            // counters
            tvCounters.text = fromHtmlCompat(
                CheckListCounters.buildStatusHtml(this@SpecSummaryActivity, keyId)
            )

            // 요약 생성
            rebuildSummary()
        }
    }

    private fun showSettings() {
        val items = arrayOf("전장 간격 20cm", "전장 간격 15cm", "전장 간격 10cm")
        val initial = when (stepCm) {
            15 -> 1
            10 -> 2
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("설정")
            .setSingleChoiceItems(items, initial) { d, which ->
                stepCm = when (which) { 1 -> 15; 2 -> 10; else -> 20 }
            }
            .setPositiveButton("적용") { _, _ -> rebuildSummary() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun rebuildSummary() {
        lifecycleScope.launch {
            val overrides = withContext(Dispatchers.IO) {
                ovDao.listByFile(keyId).associateBy { it.bl }
            }
            buckets = SpecBucketer.buildLenBuckets(stepCm)
            details = SpecBucketer.buildDetails(keyId, rows, overrides)
            grid = SpecBucketer.summarize(buckets, details)

            drawSummaryGrid() // 표시
            detailsAdapter.submit(emptyList()) // 상세 초기화
        }
    }

    private fun drawSummaryGrid() {
        summaryContainer.removeAllViews()

        // 헤더
        summaryContainer.addView(makeRow(
            listOf("구간(전장 m)", "1.9 이상", "1.8~1.9", "1.8 이하", "합계"),
            isHeader = true
        ))

        // 바디
        for (i in buckets.indices) {
            val g = grid.getOrNull(i) ?: intArrayOf(0,0,0)
            val total = g.sum()
            val cells = listOf(
                buckets[i].label,
                g[0].toString(),
                g[1].toString(),
                g[2].toString(),
                total.toString()
            )
            val rowView = makeRow(cells, isHeader = false)
            rowView.setOnClickListener { onBucketClick(i) }
            summaryContainer.addView(rowView)
        }

        // 합계 행
        val sumCols = IntArray(3)
        grid.forEach { g -> for (c in 0 until 3) sumCols[c] += g.getOrElse(c) {0} }
        val grand = sumCols.sum()
        summaryContainer.addView(makeRow(
            listOf("합계", sumCols[0].toString(), sumCols[1].toString(), sumCols[2].toString(), grand.toString()),
            isHeader = true
        ))
    }

    private fun onBucketClick(idx: Int) {
        val b = buckets[idx]
        val picked = details.filter { d ->
            val l = d.lenMm ?: return@filter false
            when {
                b.minMm != null && b.maxMm == null -> l >= b.minMm
                b.minMm == null && b.maxMm != null -> l < b.maxMm
                else -> l in (b.minMm!! until b.maxMm!!)
            }
        }
        detailsAdapter.submit(picked)
        Toast.makeText(this, "${b.label} 상세 ${picked.size}건", Toast.LENGTH_SHORT).show()
    }

    // ===== Utils =====

    private fun makeRow(cells: List<String>, isHeader: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(4)) }
        }
        val weights = floatArrayOf(1.7f, 1f, 1f, 1f, 1f) // 가로스크롤 없이 맞춤
        cells.forEachIndexed { i, s ->
            val tv = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weights[i])
                text = s
                gravity = Gravity.CENTER
                setPadding(dp(6))
                setBackgroundColor(Color.parseColor(if (isHeader) "#EEEEEE" else "#FFFFFF"))
                setTextColor(Color.parseColor("#000000"))
            }
            tv.background = android.graphics.drawable.ShapeDrawable().apply {
                paint.color = Color.TRANSPARENT
            }
            // 테두리(얇은 회색)
            tv.setBackgroundResource(android.R.drawable.editbox_background_normal)
            row.addView(tv)
        }
        return row
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun readShipState(idx: Int): Boolean {
        return getSharedPreferences("carchecking_prefs", MODE_PRIVATE)
            .getBoolean("ship_orders:${keyId}:${idx}_shipped", false)
    }

    // ===== 상세 어댑터 =====

    inner class DetailAdapter : RecyclerView.Adapter<DetailVH>() {
        private var data: List<SpecBucketer.Detail> = emptyList()

        fun submit(list: List<SpecBucketer.Detail>) {
            data = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailVH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(6))
            }
            fun cell(w: Float) = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4))
            }
            val tvNo = cell(0.6f)
            val tvBL = cell(1.4f)
            val tvModel = cell(1.8f)
            val tvLen = cell(0.8f)
            val tvWid = cell(0.8f)

            row.addView(tvNo); row.addView(tvBL); row.addView(tvModel); row.addView(tvLen); row.addView(tvWid)
            return DetailVH(row, tvNo, tvBL, tvModel, tvLen, tvWid).also { vh ->
                row.setOnLongClickListener {
                    val d = data.getOrNull(vh.bindingAdapterPosition) ?: return@setOnLongClickListener false
                    showOverrideDialog(d)
                    true
                }
            }
        }

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: DetailVH, position: Int) {
            val d = data[position]
            val seq = position + 1
            holder.no.text = seq.toString()
            holder.bl.text = d.row.bl
            holder.model.text = d.row.haju + " " + firstLine(d.row.carInfo)

            holder.len.text = d.lenMm?.let { String.format("%.2f", it / 1000.0) } ?: "-"
            holder.wid.text = d.widthMm?.let { String.format("%.2f", it / 1000.0) } ?: "-"

            // 색칠 규칙
            val shipped = readShipState(d.rowIndex)
            val clr = when {
                shipped -> Color.parseColor("#008000") // 초록
                d.row.clearance.equals("X", true) -> Color.parseColor("#CC0000") // 빨강
                d.row.isChecked -> Color.parseColor("#1E90FF") // 파랑
                else -> Color.TRANSPARENT
            }
            holder.itemView.setBackgroundColor(if (clr == Color.TRANSPARENT) Color.WHITE else clr)
            val textColor = if (clr == Color.TRANSPARENT) Color.BLACK else Color.WHITE
            listOf(holder.no, holder.bl, holder.model, holder.len, holder.wid).forEach { it.setTextColor(textColor) }

            // 출처 뱃지
            val badge = when (d.source) { 'O' -> "O"; 'T' -> "T"; else -> "-" }
            holder.no.text = "$seq($badge)"
        }

        private fun firstLine(s: String) = s.replace("\r\n", "\n").replace('\r','\n').lineSequence().firstOrNull().orEmpty()
    }

    class DetailVH(
        v: View,
        val no: TextView,
        val bl: TextView,
        val model: TextView,
        val len: TextView,
        val wid: TextView
    ) : RecyclerView.ViewHolder(v)

    // ===== 입력 다이얼로그 =====

    private fun showOverrideDialog(d: SpecBucketer.Detail) {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12))
        }
        fun input(hint: String, init: String?): EditText {
            return EditText(this).apply {
                this.hint = hint
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(4))
                setText(init ?: "")
            }.also { wrap.addView(it) }
        }
        val etLen = input("전장(mm, 예: 5179)", d.lenMm?.toString())
        val etWid = input("전폭(mm, 예: 1956)", d.widthMm?.toString())

        AlertDialog.Builder(this)
            .setTitle("제원 입력/수정")
            .setView(wrap)
            .setNegativeButton("삭제") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.specOverrides().delete(keyId, d.row.bl)
                }
                LogBus.logRaw("제원 삭제: ${d.row.bl}")
                rebuildSummary()
            }
            .setPositiveButton("저장") { _, _ ->
                val len = etLen.text.toString().toIntOrNull()
                val wid = etWid.text.toString().toIntOrNull()
                lifecycleScope.launch(Dispatchers.IO) {
                    val old = db.specOverrides().get(keyId, d.row.bl)
                    val ov = (old ?: SpecOverride(fileKey = keyId, bl = d.row.bl)).copy(
                        lenMm = len, widthMm = wid, updatedTs = System.currentTimeMillis()
                    )
                    if (old == null) db.specOverrides().insert(ov) else db.specOverrides().update(ov)
                }
                LogBus.logRaw("제원 입력: ${d.row.bl} ${len ?: "-"} / ${wid ?: "-"}")
                rebuildSummary()
            }
            .setNeutralButton("닫기", null)
            .show()
    }
}
