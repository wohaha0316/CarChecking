package com.example.carchecking

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileInputStream
import java.util.Locale

class CheckListActivity : AppCompatActivity() {

    // 기본 인덱스(폴백)
    private val DEF_BL = 1         // B
    private val DEF_HAJU = 2       // C
    private val DEF_CAR = 3        // D
    private val DEF_QTY = 4        // E
    private val DEF_CLEAR = 7      // H
    private val DEF_DATA_START = 10 // 0-based (11행부터)

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

    // UI 설정
    private lateinit var uiConfig: UiConfig

    // 정렬 상태
    private var sortKey: SortKey = SortKey.NONE
    private var sortAsc: Boolean = true
    private enum class SortKey { NONE, NO, BL, HAJU, CAR, QTY, CLEAR, CHECK }

    // 찾기바
    private var searchBar: View? = null
    private var searchEdit: EditText? = null

    // Divider
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
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "파일 경로 없음", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        currentFile = File(path)
        keyId = ParsedCache.keyFor(currentFile).id()

        uiConfig = UiPrefs.load(this, keyId)

        showLoading(true)

        lifecycleScope.launchWhenStarted {
            val cacheKey = ParsedCache.keyFor(currentFile)
            val cached = withContext(Dispatchers.IO) { ParsedCache.read(filesDir, cacheKey) }

            val parsed = if (cached != null && cached.isNotEmpty()) cached.toMutableList()
            else {
                val p = withContext(Dispatchers.IO) { readExcel(currentFile) }.toMutableList()
                withContext(Dispatchers.IO) { ParsedCache.write(filesDir, cacheKey, p) }
                p
            }

            allRows = parsed
            rows = parsed.toMutableList()

            adapter = CheckRowAdapter(rows, uiConfig)
            recycler.adapter = adapter

            // 헤더/Divider 초기반영
            applyUiToHeader(uiConfig)     // 내부에서 normalized 적용
            applyDivider(uiConfig.showRowDividers)

            // 하단 버튼: 설정
            findViewById<Button?>(R.id.btnSort)?.apply {
                text = "설정"
                setOnClickListener {
                    SettingsBottomSheet(
                        context = this@CheckListActivity,
                        initial = uiConfig.copy(),
                        onApply = { cfg, scope ->
                            UiPrefs.save(
                                this@CheckListActivity,
                                scope,
                                if (scope == UiPrefs.Scope.FILE) keyId else null,
                                cfg
                            )
                            uiConfig = UiPrefs.load(this@CheckListActivity, keyId)
                            applyUiToHeader(uiConfig)
                            adapter.updateUi(uiConfig)
                            applyDivider(uiConfig.showRowDividers)
                        },
                        onLiveChange = { cfg ->
                            // 미리보기: 항상 정규화된 값으로 헤더/리스트 동시 반영
                            applyUiToHeader(cfg)
                            adapter.updateUi(cfg)
                            applyDivider(cfg.showRowDividers)
                        },
                        onResetToDefault = { def ->
                            applyUiToHeader(def)
                            adapter.updateUi(def)
                            applyDivider(def.showRowDividers)
                        }
                    ).show()
                }
            }
            // 하단 버튼: 찾기
            findViewById<Button?>(R.id.btnSearch)?.apply {
                text = "찾기"
                setOnClickListener { toggleSearchBar() }
            }

            restoreCheckState()
            updateStatus()
            attachHeaderSort()

            showLoading(false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (this::adapter.isInitialized) updateStatus()
    }

    override fun onPause() {
        super.onPause()
        saveCheckState()
    }

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        recycler.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    // === Divider 토글 ===
    private fun applyDivider(show: Boolean) {
        rowDivider?.let { recycler.removeItemDecoration(it); rowDivider = null }
        if (show) {
            rowDivider = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
            recycler.addItemDecoration(rowDivider!!)
        }
    }

    // ===== 컬럼 감지 + car/qty 자동 교정 =====

    private data class ColMap(
        val bl: Int, val haju: Int, val car: Int, val qty: Int, val clear: Int, val dataStart: Int
    )

    private fun normalizeHeader(s: String): String {
        val t = s.trim().lowercase(Locale.ROOT)
        return t.replace("\\s".toRegex(), "")
            .replace("/", "")
            .replace(".", "")
            .replace("'", "")
            .replace("번호", "no")
    }

    private fun detectColumns(file: File): ColMap {
        var map = ColMap(DEF_BL, DEF_HAJU, DEF_CAR, DEF_QTY, DEF_CLEAR, DEF_DATA_START)
        try {
            FileInputStream(file).use { fis ->
                val wb = if (file.name.endsWith(".xls", true)) HSSFWorkbook(fis) else XSSFWorkbook(fis)
                val sheet = wb.getSheetAt(0)
                val fmt = DataFormatter()

                val last = minOf(sheet.lastRowNum, 40)
                var headerRowIdx = -1
                var blI = -1; var hajuI = -1; var carI = -1; var qtyI = -1; var clrI = -1

                for (r in 0..last) {
                    val row = sheet.getRow(r) ?: continue
                    val maxCell = row.lastCellNum.toInt().coerceAtLeast(12)
                    var found = 0
                    for (c in 0..maxCell) {
                        val cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: continue
                        val s = fmt.formatCellValue(cell)
                        if (s.isBlank()) continue
                        val n = normalizeHeader(s)
                        when {
                            blI == -1 && (n == "bl" || n == "blno" || n == "b/l" || n == "b/lno" || n.contains("bl")) -> { blI = c; found++ }
                            hajuI == -1 && (n.contains("화주") || n.contains("consignee") || n.contains("customer") || n.contains("shipper")) -> { hajuI = c; found++ }
                            carI  == -1 && (n.contains("차량정보") || n.contains("descriptionofgoods") || n.contains("description") || n.contains("desc") || n.contains("spec") || n.contains("car")) -> { carI = c; found++ }
                            qtyI  == -1 && (n == "qty" || n.contains("quantity") || n == "수" || n.contains("수량") || n.contains("qnty") || n.contains("totalqty")) -> { qtyI = c; found++ }
                            clrI  == -1 && (n.contains("면장") || n.contains("clearance") || n.contains("customs")) -> { clrI = c; found++ }
                        }
                    }
                    if (found >= 3) { headerRowIdx = r; break }
                }

                if (headerRowIdx != -1) {
                    map = ColMap(
                        bl   = if (blI  != -1) blI  else DEF_BL,
                        haju = if (hajuI!= -1) hajuI else DEF_HAJU,
                        car  = if (carI != -1) carI else DEF_CAR,
                        qty  = if (qtyI != -1) qtyI else DEF_QTY,
                        clear= if (clrI != -1) clrI else DEF_CLEAR,
                        dataStart = (headerRowIdx + 1).coerceAtLeast(DEF_DATA_START)
                    )
                }
                wb.close()
            }
        } catch (_: Exception) { }
        return map
    }

    private fun looksLikeQty(s: String): Boolean {
        if (s.isBlank()) return false
        val digits = Regex("\\d+").findAll(s).sumOf { it.value.length }
        val letters = s.count { it.isLetter() }
        return digits > 0 && digits >= letters
    }

    private fun looksLikeCarInfo(s: String): Boolean {
        if (s.isBlank()) return false
        if (s.length >= 12) return true
        val vinRegex = Regex("(?i)(?=([A-HJ-NPR-Z0-9]{17}))\\1")
        return vinRegex.containsMatchIn(s)
    }

    private fun validateAndFixColumns(file: File, cm: ColMap): ColMap {
        try {
            FileInputStream(file).use { fis ->
                val wb = if (file.name.endsWith(".xls", true)) HSSFWorkbook(fis) else XSSFWorkbook(fis)
                val sheet = wb.getSheetAt(0)
                val fmt = DataFormatter()

                val end = minOf(sheet.lastRowNum, cm.dataStart + 30)
                var carOk = 0; var qtyOk = 0; var carBad = 0; var qtyBad = 0

                for (r in cm.dataStart..end) {
                    val row = sheet.getRow(r) ?: continue
                    val bl = fmt.formatCellValue(row.getCell(cm.bl, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: continue)
                    if (bl.startsWith("TERMINAL", true)) continue

                    val carS = fmt.formatCellValue(row.getCell(cm.car, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: continue)
                    val qtyS = fmt.formatCellValue(row.getCell(cm.qty, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) ?: continue)

                    if (looksLikeCarInfo(carS)) carOk++ else carBad++
                    if (looksLikeQty(qtyS))     qtyOk++ else qtyBad++
                }
                wb.close()

                return if (carOk < carBad && qtyOk >= qtyBad) cm.copy(car = cm.qty, qty = cm.car) else cm
            }
        } catch (_: Exception) { }
        return cm
    }

    /** 엑셀 읽기 */
    private fun readExcel(file: File): List<CheckRow> {
        val out = mutableListOf<CheckRow>()
        try {
            val detected = detectColumns(file)
            val cm = validateAndFixColumns(file, detected)

            FileInputStream(file).use { fis ->
                val wb = if (file.name.endsWith(".xls", true)) HSSFWorkbook(fis) else XSSFWorkbook(fis)
                val sheet = wb.getSheetAt(0)
                val fmt = DataFormatter()

                for (r in cm.dataStart..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue

                    val bl = getRaw(fmt, row, cm.bl).trim()
                    val haju = getRaw(fmt, row, cm.haju).trim()
                    val descRaw = getRaw(fmt, row, cm.car)
                    val qty = getRaw(fmt, row, cm.qty).trim()
                    val clearance = getRaw(fmt, row, cm.clear).trim()

                    if (bl.isEmpty() && haju.isEmpty() && descRaw.isEmpty() && qty.isEmpty() && clearance.isEmpty()) continue

                    if (bl.startsWith("TERMINAL", ignoreCase = true)) {
                        out.add(CheckRow(bl, "", "", "", "", isLabelRow = true))
                        continue
                    }

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

    /** 차량정보 정리 */
    private fun cleanMultiline(src: String): String {
        val normalized = src.replace("\r\n", "\n").replace('\r', '\n')
        val specialSpaces = Regex("[\\u00A0\\u2007\\u202F\\u200B\\t]")
        return normalized
            .trim('\n', ' ', '\t', '\r')
            .split('\n')
            .map { it.replace(specialSpaces, " ").trim() }
            .filter { it.isNotEmpty() && !it.equals("USED CAR", ignoreCase = true) }
            .joinToString("\n")
    }

    /** 상태 문자열(색상 강조) */
    private fun formatStatusHtml(total: Int, clearanceX: Int, checked: Int): String =
        "전체 <font color='#000000'>${total} 대</font>  " +
                "면장X <font color='#FF0000'>${clearanceX} 대</font>  " +
                "확인 <font color='#0000FF'>${checked} 대</font>"

    /** 상태 갱신: 총 대수 = B/L 개수(표시 목록 기준) */
    fun updateStatus() {
        val totalCars = rows.count { !it.isLabelRow && it.bl.isNotBlank() }
        val clearanceX = rows.filter { !it.isLabelRow }.count { it.clearance.equals("X", true) }
        val checked = rows.count { it.isChecked }

        val statusHtml = formatStatusHtml(totalCars, clearanceX, checked)
        tvStatus.text = fromHtmlCompat(statusHtml)

        // 메인과 동기화(기존 키도 함께 저장)
        val p = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        val baseNew = "status:$keyId"
        p.putInt("$baseNew:total", totalCars)
        p.putInt("$baseNew:clearanceX", clearanceX)
        p.putInt("$baseNew:checked", checked)
        p.putString("$baseNew:html", statusHtml)
        p.apply()

        val legacy = getSharedPreferences("checklist_status", MODE_PRIVATE).edit()
        val legacyBase = "${currentFile.absolutePath}"
        legacy.putInt("$legacyBase|total", totalCars)
        legacy.putInt("$legacyBase|clearanceX", clearanceX)
        legacy.putInt("$legacyBase|checked", checked)
        legacy.putString("$legacyBase|html", statusHtml)
        legacy.apply()
    }

    // 체크 상태 저장/복원
    private fun saveCheckState() {
        val e = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        allRows.forEachIndexed { idx, r ->
            if (!r.isLabelRow) {
                val base = "check_orders:$keyId:$idx"
                e.putBoolean("${base}_checked", r.isChecked)
                e.putInt("${base}_order", r.checkOrder)
            }
        }
        e.putInt("check_orders:$keyId:orderCounter", orderCounter)
        e.apply()
    }

    private fun restoreCheckState() {
        val p = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        allRows.forEachIndexed { idx, r ->
            if (!r.isLabelRow) {
                val base = "check_orders:$keyId:$idx"
                r.isChecked = p.getBoolean("${base}_checked", false)
                r.checkOrder = p.getInt("${base}_order", 0)
            }
        }
        orderCounter = p.getInt("check_orders:$keyId:orderCounter", 0)
        if (this::adapter.isInitialized) adapter.updateData(rows)
    }

    fun reindexOrders() {
        var next = 1
        allRows.filter { it.isChecked }
            .sortedBy { it.checkOrder }
            .forEach { it.checkOrder = next++ }
        orderCounter = allRows.count { it.isChecked }
        adapter.updateData(rows)
        updateStatus()
        saveCheckState()
    }

    // 헤더 가중치 반영 (항상 normalized 적용)
    private fun applyUiToHeader(cfgRaw: UiConfig) {
        val cfg = cfgRaw.normalized()
        fun setW(id: Int, w: Float) {
            val tv = findViewById<TextView?>(id) ?: return
            (tv.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.weight = w; tv.layoutParams = it
            }
        }
        setW(R.id.tvNoHeader, cfg.wNo)
        setW(R.id.tvBLHeader, cfg.wBL)
        setW(R.id.tvHajuHeader, cfg.wHaju)
        setW(R.id.tvCarInfoHeader, cfg.wCar)
        setW(R.id.tvQtyHeader, cfg.wQty)
        setW(R.id.tvClearanceHeader, cfg.wClear)
        setW(R.id.tvCheckHeader, cfg.wCheck)
    }

    // 헤더 클릭 정렬 (기존 그대로)
    private fun attachHeaderSort() {
        val hdrNo = findViewById<TextView?>(R.id.tvNoHeader)
        val hdrBL = findViewById<TextView>(R.id.tvBLHeader)
        val hdrHaju = findViewById<TextView>(R.id.tvHajuHeader)
        val hdrCar = findViewById<TextView>(R.id.tvCarInfoHeader)
        val hdrQty = findViewById<TextView>(R.id.tvQtyHeader)
        val hdrClear = findViewById<TextView>(R.id.tvClearanceHeader)
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

    private fun setHeaderIndicators(
        hdrNo: TextView?, hdrBL: TextView, hdrHaju: TextView, hdrCar: TextView,
        hdrQty: TextView, hdrClear: TextView, hdrCheck: TextView
    ) {
        fun baseText(k: SortKey) = when (k) {
            SortKey.NO -> "No"
            SortKey.BL -> "B/L"
            SortKey.HAJU -> "화주"
            SortKey.CAR -> "차량정보"
            SortKey.QTY -> "수"
            SortKey.CLEAR -> "면장"
            SortKey.CHECK -> "확인"
            else -> ""
        }
        val items = listOfNotNull(
            hdrNo?.let { it to SortKey.NO },
            hdrBL to SortKey.BL,
            hdrHaju to SortKey.HAJU,
            hdrCar to SortKey.CAR,
            hdrQty to SortKey.QTY,
            hdrClear to SortKey.CLEAR,
            hdrCheck to SortKey.CHECK
        )
        items.forEach { (tv, key) ->
            val base = baseText(key)
            tv.text = if (sortKey == key) {
                if (sortAsc) "$base ▲" else "$base ▼"
            } else base
        }
    }

    /** TERMINAL 라벨 기준 블록 단위 정렬 */
    private fun sortInBlocks() {
        if (!this::adapter.isInitialized) return
        val labelIdx = rows.mapIndexedNotNull { idx, r -> if (r.isLabelRow) idx else null }
        val cuts = mutableListOf(-1); cuts.addAll(labelIdx); cuts.add(rows.size)
        for (i in 0 until cuts.size - 1) {
            val start = cuts[i] + 1
            val endEx = cuts[i + 1]
            if (start >= endEx) continue
            val block = rows.subList(start, endEx)
            block.sortWith(compareFor(sortKey, sortAsc))
        }
        adapter.updateData(rows)
        adapter.notifyDataSetChanged()
    }

    private fun compareFor(key: SortKey, asc: Boolean): Comparator<CheckRow> {
        val sign = if (asc) 1 else -1
        return Comparator { a, b ->
            if (a.isLabelRow && b.isLabelRow) return@Comparator 0
            if (a.isLabelRow) return@Comparator -1
            if (b.isLabelRow) return@Comparator 1
            val res = when (key) {
                SortKey.NO    -> 0
                SortKey.BL    -> a.bl.compareTo(b.bl, true)
                SortKey.HAJU  -> a.haju.compareTo(b.haju, true)
                SortKey.CAR   -> a.carInfo.compareTo(b.carInfo, true)
                SortKey.QTY   -> (a.qty.toIntOrNull() ?: -1).compareTo(b.qty.toIntOrNull() ?: -1)
                SortKey.CLEAR -> a.clearance.compareTo(b.clearance, true)
                SortKey.CHECK -> a.checkOrder.compareTo(b.checkOrder)
                SortKey.NONE  -> 0
            }
            sign * res
        }
    }

    // ====== 하단 고정 찾기바 (그대로) ======
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

    private fun hideSearchBar(
        resetFilter: Boolean,
        alsoClearInput: Boolean = false,
        alsoHideIme: Boolean = false
    ) {
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
            setImageResource(android.R.drawable.ic_menu_revert) // 전체 삭제
            contentDescription = "전체 삭제"
            background = null
            setOnClickListener {
                et.setText("")
                et.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        val btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = "닫기"
            background = null
            setOnClickListener {
                hideSearchBar(resetFilter = true, alsoClearInput = true, alsoHideIme = true)
            }
        }

        container.addView(et); container.addView(btnClear); container.addView(btnClose)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        val bottomBar = (findViewById<Button?>(R.id.btnSearch)?.parent as? View)
        lp.bottomMargin = bottomBar?.height ?: dp(container, 48f)
        root.addView(container, lp)

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val barH = (findViewById<Button?>(R.id.btnSearch)?.parent as? View)?.height ?: dp(v, 48f)
            val lp2 = v.layoutParams as FrameLayout.LayoutParams
            lp2.bottomMargin = maxOf(barH, ime, sys)
            v.layoutParams = lp2
            insets
        }

        et.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter(s?.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBar = container
        searchEdit = et
    }

    private fun applyFilter(q: String?) {
        val query = q?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (query.isEmpty()) {
            rows = allRows.toMutableList()
            adapter.updateData(rows)
            updateStatus()
            return
        }
        val result = mutableListOf<CheckRow>()
        var currentLabel: CheckRow? = null
        var buffer = mutableListOf<CheckRow>()
        fun flush() {
            if (buffer.isNotEmpty()) {
                currentLabel?.let { result += it }
                result.addAll(buffer)
            }
            currentLabel = null
            buffer = mutableListOf()
        }
        for (r in allRows) {
            if (r.isLabelRow) { flush(); currentLabel = r; continue }
            val hit = r.bl.lowercase(Locale.ROOT).contains(query)
                    || r.haju.lowercase(Locale.ROOT).contains(query)
                    || r.carInfo.lowercase(Locale.ROOT).contains(query)
                    || r.qty.lowercase(Locale.ROOT).contains(query)
                    || r.clearance.lowercase(Locale.ROOT).contains(query)
            if (hit) buffer += r
        }
        flush()

        rows = result
        adapter.updateData(rows)
        updateStatus()
    }

    private fun dp(view: View, dp: Float): Int =
        (dp * view.context.resources.displayMetrics.density).toInt()
}
