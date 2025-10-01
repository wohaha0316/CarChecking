package com.example.carchecking

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.util.Locale

class CheckListActivity : AppCompatActivity() {

    // 기본 엑셀 추정 위치
    private val DEF_BL = 1
    private val DEF_HAJU = 2
    private val DEF_CAR = 3
    private val DEF_QTY = 4
    private val DEF_CLEAR = 7
    private val DEF_DATA_START = 10

    private lateinit var recycler: RecyclerView
    private lateinit var tvStatus: TextView
    private lateinit var progress: ProgressBar

    private lateinit var adapter: CheckRowAdapter
    private var allRows: MutableList<CheckRow> = mutableListOf()
    internal var rows: MutableList<CheckRow> = mutableListOf()
    internal var orderCounter = 0

    private val PREF_NAME = "carchecking_prefs"

    private lateinit var currentFile: File
    private lateinit var keyId: String

    private lateinit var uiConfig: UiConfig
    private lateinit var eventRepo: EventRepository

    // ======== 특이사항 메모용 ========
    private lateinit var db: AppDatabase
    private lateinit var notesDao: NoteDao
    /** 메모가 있는 '행 인덱스(현재 리스트 기준)' — 간단 구현 */
    private val noted = mutableSetOf<Int>()
    // ==============================

    private var sortKey: SortKey = SortKey.NONE
    private var sortAsc: Boolean = true
    private enum class SortKey { NONE, NO, BL, HAJU, CAR, QTY, CLEAR, CHECK }

    private var searchBar: View? = null
    private var searchEdit: EditText? = null
    private var rowDivider: RecyclerView.ItemDecoration? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_list)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        recycler = findViewById(R.id.recyclerViewCheck)
        tvStatus = findViewById(R.id.tvStatus)
        progress = findViewById(R.id.progress)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.setHasFixedSize(true)

        val path = intent.getStringExtra("filePath")
        if (path.isNullOrBlank()) { Toast.makeText(this, "파일 경로 없음", Toast.LENGTH_SHORT).show(); finish(); return }

        currentFile = File(path)
        keyId = ParsedCache.keyFor(currentFile).id()
        uiConfig = UiPrefs.load(this, keyId)
        uiConfig.rowSpacing = 0f
        eventRepo = EventRepository(this)

        // DB 준비
        db = AppDatabase.get(this)
        notesDao = db.notes()

        showLoading(true)
        lifecycleScope.launch {
            val cacheKey = ParsedCache.keyFor(currentFile)
            val cached = withContext(Dispatchers.IO) { ParsedCache.read(filesDir, cacheKey) }

            val parsed = if (cached != null && cached.isNotEmpty()) cached.toMutableList()
            else {
                val p = withContext(Dispatchers.IO) { readExcel(currentFile) }.toMutableList()
                withContext(Dispatchers.IO) { ParsedCache.write(filesDir, cacheKey, p) }
                p
            }

            allRows = parsed; rows = parsed.toMutableList()

            adapter = CheckRowAdapter(
                rows, uiConfig,
                onToggle = { position, nowChecked -> onRowToggled(position, nowChecked) }
            ).also { ad ->
                // 롱프레스 → 특이사항 다이얼로그
                ad.onRowLongPress = { pos, bl -> showNoteDialog(pos, bl) }
            }
            recycler.adapter = adapter

            // 파일에 저장된 메모 로드 → 표시
            loadNotesAndMark()

            applyUiToHeader(uiConfig)
            applyDivider(uiConfig.showRowDividers)

            findViewById<Button?>(R.id.btnSort)?.apply {
                text = "설정"
                setOnClickListener {
                    SettingsBottomSheet(
                        context = this@CheckListActivity,
                        initial = uiConfig.copy(),
                        onApply = { cfg, scope ->
                            UiPrefs.save(this@CheckListActivity, scope, if (scope==UiPrefs.Scope.FILE) keyId else null, cfg)
                            uiConfig = UiPrefs.load(this@CheckListActivity, keyId)
                            applyUiToHeader(uiConfig); adapter.updateUi(uiConfig); applyDivider(uiConfig.showRowDividers)
                        },
                        onLiveChange = { cfg ->
                            applyUiToHeader(cfg); adapter.updateUi(cfg); applyDivider(cfg.showRowDividers)
                        },
                        onResetToDefault = { def ->
                            applyUiToHeader(def); adapter.updateUi(def); applyDivider(def.showRowDividers)
                        }
                    ).show()
                }
            }
            findViewById<Button?>(R.id.btnSearch)?.apply { text = "찾기"; setOnClickListener { toggleSearchBar() } }
            findViewById<Button?>(R.id.btnCamera)?.apply { text = "카메라" }

            restoreCheckState()
            updateStatus()
            attachHeaderSort()
            showLoading(false)
        }
    }

    // 특이사항: DB에서 불러와 어댑터에 표시
    private fun loadNotesAndMark() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { notesDao.listByFile(keyId) }
            noted.clear()
            // 간단 구현: 현재 rows 인덱스 기준으로 저장했음을 가정
            // (필터/정렬 시 위치가 바뀔 수 있음. 고도화는 추후)
            list.forEach { n ->
                if (n.rowIndex in rows.indices) noted.add(n.rowIndex)
            }
            adapter.setNotedPositions(noted)
        }
    }

    // 체크/해제 시 DB 이벤트 기록
    fun onRowToggled(position: Int, nowChecked: Boolean) {
        val user = getSharedPreferences("user_profile", MODE_PRIVATE).getString("checker_name", null)
        lifecycleScope.launch {
            eventRepo.logCheck(
                fileKey = keyId,
                rowIndex = position, // 라벨 포함 화면 인덱스 기준
                checked = nowChecked,
                user = user
            )
            updateStatus()
        }
    }

    override fun onResume() { super.onResume(); if (this::adapter.isInitialized) updateStatus()
        LogBus.appOpen("차체크화면") }
    override fun onPause() { super.onPause(); saveCheckState()
        LogBus.appClose("차체크화면") }

    // ===== 특이사항 메모 다이얼로그 =====
    private fun showNoteDialog(pos: Int, bl: String) {
        val ctx = this
        val et = EditText(ctx).apply {
            setLines(4)
            gravity = Gravity.TOP
            hint = "특이사항 메모를 입력하세요 (비워두면 삭제)"
        }

        // 기존 메모 로딩
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { notesDao.get(keyId, pos) }
            et.setText(existing?.text ?: "")
        }

        AlertDialog.Builder(ctx)
            .setTitle("특이사항 - $bl")
            .setView(et)
            .setNegativeButton("취소", null)
            .setPositiveButton("완료") { _, _ ->
                val text = et.text?.toString()?.trim().orEmpty()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        if (text.isEmpty()) {
                            notesDao.delete(keyId, pos)
                            LogBus.noteDelete(keyId, pos, bl)   // ★ 삭제 로그
                        } else {
                            notesDao.upsert(
                                Note(
                                    fileKey = keyId,
                                    rowIndex = pos,
                                    bl = bl,
                                    text = text,
                                    updatedTs = System.currentTimeMillis()
                                )
                            )
                            LogBus.noteAdd(keyId, pos, bl, text)   // ★ 추가/수정 로그
                        }
                    }
                    if (text.isEmpty()) noted.remove(pos) else noted.add(pos)
                    adapter.setNotedPositions(noted)
                }
            }

            .show()
    }
    // =================================

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        recycler.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private fun applyDivider(show: Boolean) {
        rowDivider?.let { recycler.removeItemDecoration(it); rowDivider = null }
        if (show) { rowDivider = DividerItemDecoration(this, LinearLayoutManager.VERTICAL); recycler.addItemDecoration(rowDivider!!) }
    }

    // ---- 엑셀 파싱(기존 로직 유지) ----
    private fun readExcel(file: File): List<CheckRow> {
        val out = mutableListOf<CheckRow>()
        try {
            FileInputStream(file).use { fis ->
                val wb = if (file.name.endsWith(".xls", true)) HSSFWorkbook(fis) else XSSFWorkbook(fis)
                val sheet = wb.getSheetAt(0); val fmt = DataFormatter()
                for (r in DEF_DATA_START..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val bl = getRaw(fmt, row, DEF_BL).trim()
                    val haju = getRaw(fmt, row, DEF_HAJU).trim()
                    val descRaw = getRaw(fmt, row, DEF_CAR)
                    val qty = getRaw(fmt, row, DEF_QTY).trim()
                    val clearance = getRaw(fmt, row, DEF_CLEAR).trim()
                    if (bl.isEmpty() && haju.isEmpty() && descRaw.isEmpty() && qty.isEmpty() && clearance.isEmpty()) continue
                    if (bl.startsWith("TERMINAL", true)) { out.add(CheckRow(bl, "", "", "", "", isLabelRow = true)); continue }
                    val carInfo = cleanMultiline(descRaw)
                    out.add(CheckRow(bl, haju, carInfo, qty, clearance))
                }
                wb.close()
            }
        } catch (e: Exception) { e.printStackTrace() }
        return out
    }

    private fun getRaw(fmt: DataFormatter, row: org.apache.poi.ss.usermodel.Row, idx: Int): String {
        val c = row.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: return ""
        return fmt.formatCellValue(c)
    }

    private fun cleanMultiline(src: String): String {
        val normalized = src.replace("\r\n", "\n").replace('\r', '\n')
        val specialSpaces = Regex("[\\u00A0\\u2007\\u202F\\u200B\\t]")
        return normalized.trim('\n', ' ', '\t', '\r')
            .split('\n').map { it.replace(specialSpaces, " ").trim() }
            .filter { it.isNotEmpty() && !it.equals("USED CAR", true) }
            .joinToString("\n")
    }

    private fun formatStatusHtml(total: Int, clearanceX: Int, checked: Int, shipped: Int) =
        "전체 <font color='#000000'>${total} 대</font>  " +
                "면장X <font color='#CC0000'>${clearanceX} 대</font>  " +
                "확인 <font color='#1E90FF'>${checked} 대</font>  " +
                "선적 <font color='#008000'>${shipped} 대</font>"

    fun updateStatus() {
        val totalCars = rows.count { !it.isLabelRow && it.bl.isNotBlank() }
        val clearanceX = rows.filter { !it.isLabelRow }.count { it.clearance.equals("X", true) }
        val checked = rows.count { it.isChecked }
        val shipped = rows.indices.count { !rows[it].isLabelRow && readShipState(it) }

        val statusHtml = formatStatusHtml(totalCars, clearanceX, checked, shipped)
        tvStatus.text = fromHtmlCompat(statusHtml)

        val p = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        val baseNew = "status:${ParsedCache.keyFor(currentFile).id()}"
        p.putInt("$baseNew:total", totalCars)
        p.putInt("$baseNew:clearanceX", clearanceX)
        p.putInt("$baseNew:checked", checked)
        p.putInt("$baseNew:shipped", shipped)
        p.putString("$baseNew:html", statusHtml)
        p.apply()
    }

    private fun readShipState(idx: Int): Boolean =
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getBoolean("ship_orders:${keyId}:${idx}_shipped", false)

    private fun saveCheckState() {
        val e = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        allRows.forEachIndexed { idx, r ->
            if (!r.isLabelRow) {
                val base = "check_orders:${ParsedCache.keyFor(currentFile).id()}:$idx"
                e.putBoolean("${base}_checked", r.isChecked)
                e.putInt("${base}_order", r.checkOrder)
            }
        }
        e.putInt("check_orders:${ParsedCache.keyFor(currentFile).id()}:orderCounter", orderCounter)
        e.apply()
    }

    private fun restoreCheckState() {
        val id = ParsedCache.keyFor(currentFile).id()
        val p = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        allRows.forEachIndexed { idx, r ->
            if (!r.isLabelRow) {
                val base = "check_orders:$id:$idx"
                r.isChecked = p.getBoolean("${base}_checked", false)
                r.checkOrder = p.getInt("${base}_order", 0)
            }
        }
        orderCounter = p.getInt("check_orders:$id:orderCounter", 0)
        if (this::adapter.isInitialized) adapter.updateData(rows)
    }

    fun reindexOrders() {
        var next = 1
        allRows.filter { it.isChecked }.sortedBy { it.checkOrder }.forEach { it.checkOrder = next++ }
        orderCounter = allRows.count { it.isChecked }
        adapter.updateData(rows); updateStatus(); saveCheckState()
    }

    private fun applyUiToHeader(cfgRaw: UiConfig) {
        val cfg = cfgRaw.normalized()
        fun setW(id: Int, w: Float) {
            val tv = findViewById<TextView?>(id) ?: return
            (tv.layoutParams as? LinearLayout.LayoutParams)?.let { it.weight = w; tv.layoutParams = it }
        }
        setW(R.id.tvNoHeader, cfg.wNo)
        setW(R.id.tvBLHeader, cfg.wBL)
        setW(R.id.tvHajuHeader, cfg.wHaju)
        setW(R.id.tvCarInfoHeader, cfg.wCar)
        setW(R.id.tvQtyHeader, cfg.wQty)
        setW(R.id.tvClearHeader, cfg.wClear)
        setW(R.id.tvCheckHeader, cfg.wCheck)
    }

    private fun attachHeaderSort() {
        val hdrNo = findViewById<TextView?>(R.id.tvNoHeader)
        val hdrBL = findViewById<TextView>(R.id.tvBLHeader)
        val hdrHaju = findViewById<TextView>(R.id.tvHajuHeader)
        val hdrCar = findViewById<TextView>(R.id.tvCarInfoHeader)
        val hdrQty = findViewById<TextView>(R.id.tvQtyHeader)
        val hdrClear = findViewById<TextView>(R.id.tvClearHeader)
        val hdrCheck = findViewById<TextView>(R.id.tvCheckHeader)

        fun toggle(key: SortKey) {
            if (sortKey == key) sortAsc = !sortAsc else { sortKey = key; sortAsc = true }
            sortInBlocks()
            setHeaderIndicators(hdrNo, hdrBL, hdrHaju, hdrCar, hdrQty, hdrClear, hdrCheck)
        }
        hdrNo?.setOnClickListener   { toggle(SortKey.NO) }
        hdrBL.setOnClickListener    { toggle(SortKey.BL) }
        hdrHaju.setOnClickListener  { toggle(SortKey.HAJU) }
        hdrCar.setOnClickListener   { toggle(SortKey.CAR) }
        hdrQty.setOnClickListener   { toggle(SortKey.QTY) }
        hdrClear.setOnClickListener { toggle(SortKey.CLEAR) }
        hdrCheck.setOnClickListener { toggle(SortKey.CHECK) }

        setHeaderIndicators(hdrNo, hdrBL, hdrHaju, hdrCar, hdrQty, hdrClear, hdrCheck)
    }

    private fun setHeaderIndicators(hdrNo: TextView?, hdrBL: TextView, hdrHaju: TextView, hdrCar: TextView, hdrQty: TextView, hdrClear: TextView, hdrCheck: TextView) {
        fun baseText(k: SortKey) = when (k) {
            SortKey.NO -> "No"; SortKey.BL -> "B/L"; SortKey.HAJU -> "화주"; SortKey.CAR -> "차량정보"
            SortKey.QTY -> "수"; SortKey.CLEAR -> "면장"; SortKey.CHECK -> "확인"; else -> ""
        }
        val items = listOfNotNull(hdrNo?.let { it to SortKey.NO }, hdrBL to SortKey.BL, hdrHaju to SortKey.HAJU, hdrCar to SortKey.CAR, hdrQty to SortKey.QTY, hdrClear to SortKey.CLEAR, hdrCheck to SortKey.CHECK)
        items.forEach { (tv, key) ->
            val base = baseText(key)
            tv.text = if (sortKey == key) { if (sortAsc) "$base ▲" else "$base ▼" } else base
        }
    }

    private fun sortInBlocks() {
        if (!this::adapter.isInitialized) return
        val labelIdx = rows.mapIndexedNotNull { idx, r -> if (r.isLabelRow) idx else null }
        val cuts = mutableListOf(-1); cuts.addAll(labelIdx); cuts.add(rows.size)
        for (i in 0 until cuts.size - 1) {
            val start = cuts[i] + 1; val endEx = cuts[i + 1]; if (start >= endEx) continue
            val block = rows.subList(start, endEx)
            block.sortWith(compareFor(sortKey, sortAsc))
        }
        adapter.updateData(rows); adapter.notifyDataSetChanged()
    }

    private fun compareFor(key: SortKey, asc: Boolean): Comparator<CheckRow> {
        val sign = if (asc) 1 else -1
        return Comparator { a, b ->
            if (a.isLabelRow && b.isLabelRow) 0
            else if (a.isLabelRow) -1
            else if (b.isLabelRow) 1
            else {
                val res = when (key) {
                    SortKey.NO -> 0
                    SortKey.BL -> a.bl.compareTo(b.bl, true)
                    SortKey.HAJU -> a.haju.compareTo(b.haju, true)
                    SortKey.CAR -> a.carInfo.compareTo(b.carInfo, true)
                    SortKey.QTY -> (a.qty.toIntOrNull() ?: -1).compareTo(b.qty.toIntOrNull() ?: -1)
                    SortKey.CLEAR -> a.clearance.compareTo(b.clearance, true)
                    SortKey.CHECK -> a.checkOrder.compareTo(b.checkOrder)
                    SortKey.NONE -> 0
                }
                sign * res
            }
        }
    }

    // ---- 찾기바 (기존) ----
    private fun toggleSearchBar() {
        if (searchBar == null) createSearchBar()
        if (searchBar!!.visibility == View.VISIBLE) {
            hideSearchBar(resetFilter = true, alsoClearInput = true, alsoHideIme = true)
        } else showSearchBar()
    }
    private fun showSearchBar() {
        searchBar?.visibility = View.VISIBLE
        searchEdit?.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        searchEdit?.let { imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
    }
    private fun hideSearchBar(resetFilter: Boolean, alsoClearInput: Boolean = false, alsoHideIme: Boolean = false) {
        if (alsoHideIme) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            searchEdit?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
        }
        if (alsoClearInput) searchEdit?.setText("")
        searchBar?.visibility = View.GONE
        if (resetFilter) applyFilter(null)
    }
    private fun createSearchBar() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFFAFAFA.toInt())
            elevation = 6f
            setPadding(dp(this, 8f), dp(this, 6f), dp(this, 8f), dp(this, 6f))
            visibility = View.GONE
        }
        val et = EditText(this).apply {
            hint = "검색어 입력 (B/L, 화주, 차량정보)"
            setSingleLine()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClear = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_revert)
            contentDescription = "전체 삭제"
            background = null
            setOnClickListener {
                et.setText(""); et.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = "닫기"
            background = null
            setOnClickListener { hideSearchBar(resetFilter = true, alsoClearInput = true, alsoHideIme = true) }
        }
        container.addView(et); container.addView(btnClear); container.addView(btnClose)

        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        val bottomBar = (findViewById<Button?>(R.id.btnSearch)?.parent as? View)
        lp.bottomMargin = bottomBar?.height ?: dp(container, 48f)
        root.addView(container, lp)

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val barH = (findViewById<Button?>(R.id.btnSearch)?.parent as? View)?.height ?: dp(v, 48f)
            val lp2 = v.layoutParams as FrameLayout.LayoutParams
            lp2.bottomMargin = maxOf(barH, ime, sys); v.layoutParams = lp2; insets
        }

        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter(s?.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBar = container; searchEdit = et
    }
    private fun applyFilter(q: String?) {
        val query = q?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (query.isEmpty()) {
            rows = allRows.toMutableList()
            adapter.updateData(rows)
            adapter.setNotedPositions(noted) // 표시 유지
            updateStatus()
            return
        }
        val result = mutableListOf<CheckRow>()
        var currentLabel: CheckRow? = null
        var buffer = mutableListOf<CheckRow>()
        fun flush() { if (buffer.isNotEmpty()) { currentLabel?.let { result += it }; result.addAll(buffer) }; currentLabel = null; buffer = mutableListOf() }
        for (r in allRows) {
            if (r.isLabelRow) { flush(); currentLabel = r; continue }
            val hit = r.bl.lowercase(Locale.ROOT).contains(query) || r.haju.lowercase(Locale.ROOT).contains(query) ||
                    r.carInfo.lowercase(Locale.ROOT).contains(query) || r.qty.lowercase(Locale.ROOT).contains(query) ||
                    r.clearance.lowercase(Locale.ROOT).contains(query)
            if (hit) buffer += r
        }
        flush(); rows = result; adapter.updateData(rows)
        // 간단 표시: 현재 rows 인덱스 기준으로도 noted와 교집합만 표시 (필요 시 고도화)
        val filteredNoted = noted.filter { it in rows.indices }.toSet()
        adapter.setNotedPositions(filteredNoted)
        updateStatus()
    }

    private fun dp(v: View, dp: Float) = (dp * v.context.resources.displayMetrics.density).toInt()
}
