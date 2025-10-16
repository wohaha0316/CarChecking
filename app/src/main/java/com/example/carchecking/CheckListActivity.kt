package com.example.carchecking

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlin.math.max

class CheckListActivity : AppCompatActivity() {

    companion object { const val ACTION_UPDATE_HOME_STATUS = "com.example.carchecking.UPDATE_HOME_STATUS" }

    // 엑셀 컬럼 추정
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
    private var allRows: MutableList<CheckRow> = mutableListOf() // 전역(전체)
    internal var rows: MutableList<CheckRow> = mutableListOf()   // 화면(필터)

    private val PREF_NAME = "carchecking_prefs"
    private lateinit var currentFile: File
    private lateinit var keyId: String
    private lateinit var uiConfig: UiConfig
    private lateinit var eventRepo: EventRepository

    // 전역 저장소
    private lateinit var prefs: SharedPreferences
    private lateinit var orderStore: CheckOrderStore

    // 메모
    private lateinit var db: AppDatabase
    private lateinit var notesDao: NoteDao
    private val notedBLs = mutableSetOf<String>()

    // 정렬
    private var sortKey: SortKey = SortKey.NONE
    private var sortAsc: Boolean = true
    private enum class SortKey { NONE, NO, BL, HAJU, CAR, QTY, CLEAR, CHECK }

    private var searchBar: View? = null
    private var searchEdit: EditText? = null
    private var rowDivider: RecyclerView.ItemDecoration? = null

    // 스캐너
    private val vinScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            val vin = res.data?.getStringExtra("vin")?.let(VinUtils::normalize) ?: return@registerForActivityResult
            showVinConfirmDialog(vin)
        }
    }

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

        uiConfig = UiPrefs.load(this, keyId).also { it.rowSpacing = 0f }
        eventRepo = EventRepository(this)

        db = AppDatabase.get(this)
        notesDao = db.notes()

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        orderStore = CheckOrderStore(prefs, keyId)

        showLoading(true)
        lifecycleScope.launch {
            val cacheKey = ParsedCache.keyFor(currentFile)
            val cached = withContext(Dispatchers.IO) { ParsedCache.read(filesDir, cacheKey) }
            val parsed = if (!cached.isNullOrEmpty()) cached.toMutableList() else {
                val p = withContext(Dispatchers.IO) { readExcel(currentFile) }.toMutableList()
                withContext(Dispatchers.IO) { ParsedCache.write(filesDir, cacheKey, p) }
                p
            }

            allRows = parsed
            rows = parsed.toMutableList()

            adapter = CheckRowAdapter(
                rows, uiConfig,
                onToggle = { position, nowChecked -> onRowToggled(position, nowChecked) }
            ).also { ad -> ad.onRowLongPress = { pos, bl -> showNoteDialog(pos, bl) } }
            recycler.adapter = adapter

            VinIndexManager.indexFile(keyId, currentFile.absolutePath, allRows)

            loadNotesAndMark()
            applyUiToHeader(uiConfig)
            applyDivider(uiConfig.showRowDividers)
            restoreCheckState()                  // 저장된 체크/순번 반영
            updateStatus()                       // 전역 기준 현황

            attachHeaderSort()
            showLoading(false)
        }

        findViewById<Button?>(R.id.btnSearch)?.apply { text = "찾기"; setOnClickListener { toggleSearchBar() } }
        findViewById<Button?>(R.id.btnSort)?.apply {
            text = "설정"
            setOnClickListener {
                SettingsBottomSheet(
                    context = this@CheckListActivity,
                    initial = uiConfig.copy(),
                    onApply = { cfg, scope ->
                        UiPrefs.save(this@CheckListActivity, scope, if (scope == UiPrefs.Scope.FILE) keyId else null, cfg)
                        uiConfig = UiPrefs.load(this@CheckListActivity, keyId)
                        applyUiToHeader(uiConfig); adapter.updateUi(uiConfig); applyDivider(cfg.showRowDividers)
                    },
                    onLiveChange = { cfg -> applyUiToHeader(cfg); adapter.updateUi(cfg); applyDivider(cfg.showRowDividers) },
                    onResetToDefault = { def -> applyUiToHeader(def); adapter.updateUi(def); applyDivider(def.showRowDividers) }
                ).show()
            }
        }
        findViewById<Button?>(R.id.btnCamera)?.apply { text = "카메라"; setOnClickListener { openVinScanner() } }
    }
    /** Prefs → allRows 최신화 (필터/화면 전환 뒤 상태 어긋남 방지) */

    /** 전역 기준 다음 순번 계산: 메모리 vs Prefs 중 더 큰 값 + 1 */
    private fun nextGlobalOrder(): Int {
        val maxInMem   = allRows.maxOfOrNull { it.checkOrder } ?: 0
        val maxInPrefs = orderStore.maxOrderInPrefs()
        return kotlin.math.max(maxInMem, maxInPrefs) + 1
    }

    /**
     * ✅ Prefs → 메모리(allRows) 동기화
     *   - 토글 직전에 호출해서, '전역 최대 순번' 계산이 항상 최신이 되도록 보장
     */
    private fun syncOrdersFromPrefs() {
        val id = keyId
        val p = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        allRows.forEachIndexed { idx, r ->
            if (!r.isLabelRow) {
                val base = "check_orders:$id:$idx"
                r.isChecked  = p.getBoolean("${base}_checked", false)
                r.checkOrder = p.getInt("${base}_order", 0)
            }
        }
    }
    /** 전역에서 현재 체크된 개수 (라벨 제외) */
    private fun countCheckedGlobal(): Int =
        allRows.count { !it.isLabelRow && it.checkOrder > 0 }

    /** 체크 해제 시: 제거된 순번보다 큰 모든 항목을 1씩 당겨서 1..K 연속성 유지 */
    private fun compactAfterUncheck(removedOrder: Int) {
        if (removedOrder <= 0) return
        allRows.forEachIndexed { idx, r ->
            if (!r.isLabelRow && r.checkOrder > removedOrder) {
                r.checkOrder -= 1
                // 체크 상태는 유지, Prefs도 동기화
                orderStore.write(idx, r.isChecked, r.checkOrder)
            }
        }
    }

    /** 확인 해제 재확인 다이얼로그 */
    private fun confirmUncheck(row: CheckRow, position: Int, globalIdx: Int) {
        AlertDialog.Builder(this)
            .setTitle("확인 취소")
            .setMessage("${row.bl}\n확인 취소하시겠습니까?")
            .setNegativeButton("아니오") { _, _ ->
                // 취소: 원래 상태로 되돌림 (체크 유지)
                row.isChecked = true
                adapter.notifyItemChanged(position)
            }
            .setPositiveButton("예") { _, _ ->
                // 실제 해제 처리
                val removed = row.checkOrder
                row.isChecked  = false
                row.checkOrder = 0
                orderStore.write(globalIdx, false, 0)

                // 뒤 번호들 1씩 당겨서 1..K 유지
                compactAfterUncheck(removed)

                adapter.notifyDataSetChanged()
                Toast.makeText(this, "확인 해제", Toast.LENGTH_SHORT).show()

                // 로그 & 현황 갱신
                val user = getSharedPreferences("user_profile", MODE_PRIVATE)
                    .getString("checker_name", null)
                lifecycleScope.launch {
                    eventRepo.logCheck(fileKey = keyId, rowIndex = globalIdx, checked = false, user = user)
                    updateStatus()
                    pushStatusToHomeAndBroadcast()
                }
            }
            .show()
    }


    // ===== 확인 토글(전역, 필터 무관) =====
    // ===== 확인 토글(전역, 필터 무관) =====
    // ===== 확인 토글(전역, 필터 무관) =====
    fun onRowToggled(position: Int, nowChecked: Boolean) {
        val row = rows.getOrNull(position) ?: return
        if (row.isLabelRow) return

        // 화면 인덱스 → 전역 인덱스
        val globalIdx = allRows.indexOf(row)
        if (globalIdx < 0) return

        // 전역 상태 최신화 (필수)
        syncOrdersFromPrefs()

        if (nowChecked) {
            // 체크: 번호 없으면 전역 개수+1 부여
            if (row.checkOrder == 0) {
                val next = countCheckedGlobal() + 1
                row.isChecked  = true
                row.checkOrder = next
                orderStore.write(globalIdx, true, next)
            } else {
                row.isChecked = true
                orderStore.write(globalIdx, true, row.checkOrder)
            }
            adapter.notifyItemChanged(position)
            Toast.makeText(this, "확인 #${row.checkOrder}", Toast.LENGTH_SHORT).show()

            // 로그 & 현황 갱신
            val user = getSharedPreferences("user_profile", MODE_PRIVATE)
                .getString("checker_name", null)
            lifecycleScope.launch {
                eventRepo.logCheck(fileKey = keyId, rowIndex = globalIdx, checked = true, user = user)
                updateStatus()
                pushStatusToHomeAndBroadcast()
            }
        } else {
            // ✅ 체크 해제 시: 즉시 해제하지 말고 '확인 취소하시겠습니까?' 팝업
            // row.isChecked는 UI 토글 반영으로 false일 수 있으니, 팝업에서 '아니오'면 true로 되돌려줌
            confirmUncheck(row, position, globalIdx)
        }
    }




    // ===== 현황표(전역 기준) =====
    private fun readShipStateGlobal(idx: Int): Boolean =
        getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getBoolean("ship_orders:${keyId}:${idx}_shipped", false)

    fun updateStatus() {
        val totalCars  = allRows.count { !it.isLabelRow && it.bl.isNotBlank() }
        val clearanceX = allRows.filter { !it.isLabelRow }.count { it.clearance.equals("X", true) }
        val checked    = allRows.count { it.isChecked }
        val shipped    = allRows.indices.count { !allRows[it].isLabelRow && readShipStateGlobal(it) }

        val statusHtml = "전체 <font color='#000000'>${totalCars} 대</font>  " +
                "면장X <font color='#CC0000'>${clearanceX} 대</font>  " +
                "확인 <font color='#1E90FF'>${checked} 대</font>  " +
                "선적 <font color='#008000'>${shipped} 대</font>"
        tvStatus.text = fromHtmlCompat(statusHtml)

        val p = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        val base = "status:${keyId}"
        p.putInt("$base:total", totalCars)
        p.putInt("$base:clearanceX", clearanceX)
        p.putInt("$base:checked", checked)
        p.putInt("$base:shipped", shipped)
        p.putString("$base:html", statusHtml)
        p.apply()
    }

    private fun pushStatusToHomeAndBroadcast() {
        val intent = Intent(ACTION_UPDATE_HOME_STATUS).putExtra("fileKey", keyId)
        sendBroadcast(intent)
    }

    // ===== 스캐너/다이얼로그 =====
    private fun openVinScanner() { vinScanLauncher.launch(Intent(this, ScanVinActivity::class.java)) }

    private fun showVinConfirmDialog(vin: String) {
        LogBus.logRaw("[차대번호 $vin] 스캔")
        AlertDialog.Builder(this)
            .setTitle("스캔된 VIN 확인")
            .setMessage(vin)
            .setNegativeButton("취소", null)
            .setNeutralButton("복사") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("VIN", vin))
                Toast.makeText(this, "복사됨: $vin", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("확인") { _, _ -> handleScannedVin(vin) }
            .show()
    }

    private fun handleScannedVin(vin: String) {
        val hitNow = VinIndexManager.findInCurrent(keyId, vin)
        if (hitNow != null) {
            smoothScrollAndBlink(hitNow.rowIndex)
            Toast.makeText(this, "현재 리스트에서 매칭: ${hitNow.bl}", Toast.LENGTH_SHORT).show()
            LogBus.logRaw("VIN 매칭(현재): $vin -> ${hitNow.bl}")
            return
        }
        val hits = VinIndexManager.findInOthers(keyId, vin)
        if (hits.isNotEmpty()) {
            val top = hits.first()
            AlertDialog.Builder(this)
                .setTitle("다른 리스트에서 발견")
                .setMessage("${File(top.filePath).name}\nB/L: ${top.bl}\n해당 파일을 열고 이동할까요?")
                .setNegativeButton("취소", null)
                .setPositiveButton("열기") {
                        _, _ ->
                    LogBus.logRaw("VIN 교차매칭: $vin -> ${File(top.filePath).name} / ${top.bl}")
                    val i = Intent(this, CheckListActivity::class.java)
                    i.putExtra("filePath", top.filePath)
                    startActivity(i)
                }
                .show()
            return
        }
        Toast.makeText(this, "어느 리스트에도 없음 (선입고는 다음 단계에서)", Toast.LENGTH_SHORT).show()
        LogBus.logRaw("VIN 미매칭: $vin")
    }

    private fun smoothScrollAndBlink(rowIndex: Int) {
        recycler.smoothScrollToPosition(rowIndex)
        recycler.postDelayed({
            val vh = recycler.findViewHolderForAdapterPosition(rowIndex) ?: return@postDelayed
            val v = vh.itemView
            v.animate().alpha(0.4f).setDuration(120).withEndAction {
                v.animate().alpha(1f).setDuration(120).start()
            }.start()
        }, 300)
    }

    // ===== 메모 =====
    private fun showNoteDialog(pos: Int, bl: String) {
        val ctx = this
        val et = EditText(ctx).apply { setLines(4); gravity = Gravity.TOP; hint = "특이사항 메모 (비우면 삭제)" }
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { notesDao.getByBl(keyId, bl) }
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
                        if (text.isEmpty()) { notesDao.deleteByBl(keyId, bl); LogBus.noteDelete(keyId, pos, bl) }
                        else {
                            notesDao.upsert(Note(fileKey = keyId, rowIndex = pos, bl = bl, text = text, updatedTs = System.currentTimeMillis()))
                            LogBus.noteAdd(keyId, pos, bl, text)
                        }
                    }
                    if (text.isEmpty()) notedBLs.remove(bl) else notedBLs.add(bl)
                    adapter.setNotedBLs(notedBLs)
                }
            }
            .show()
    }

    private fun loadNotesAndMark() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { notesDao.listByFile(keyId) }
            notedBLs.clear()
            notedBLs.addAll(list.map { it.bl.trim() }.filter { it.isNotEmpty() })
            adapter.setNotedBLs(notedBLs)
        }
    }

    // ===== 상태 저장/복원/정렬 =====
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
        if (this::adapter.isInitialized) adapter.updateData(rows)
    }

    private fun attachHeaderSort() {
        val hdrNo    = findViewById<TextView?>(R.id.tvNoHeader)
        val hdrBL    = findViewById<TextView>(R.id.tvBLHeader)
        val hdrHaju  = findViewById<TextView>(R.id.tvHajuHeader)
        val hdrCar   = findViewById<TextView>(R.id.tvCarInfoHeader)
        val hdrQty   = findViewById<TextView>(R.id.tvQtyHeader)
        val hdrClear = findViewById<TextView>(R.id.tvClearHeader)
        val hdrCheck = findViewById<TextView>(R.id.tvCheckHeader)

        fun toggle(key: SortKey) {
            if (sortKey == key) sortAsc = !sortAsc else { sortKey = key; sortAsc = true }
            sortInBlocks()
            setHeaderIndicators(hdrNo, hdrBL, hdrHaju, hdrCar, hdrQty, hdrClear, hdrCheck)
            saveLastViewState()
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
            tv.text = if (sortKey == key) { if (sortAsc) "$base ▲" else "$base ▼" } else base
        }
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

    // ===== 검색바/필터 =====
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
        if (resetFilter) {
            applyFilter(null)
            saveLastViewState()
        }
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
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString())
                saveLastViewState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBar = container; searchEdit = et
    }
    private fun applyFilter(q: String?) {
        val query = q?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (query.isEmpty()) {
            rows = allRows.toMutableList()
            adapter.updateData(rows)
            adapter.setNotedBLs(notedBLs)
            sortInBlocks()
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
        adapter.setNotedBLs(notedBLs)
        sortInBlocks()
        updateStatus()
    }

    // ===== 마지막 화면 상태 저장/복원 =====
    private fun saveLastViewState() {
        val lm = recycler.layoutManager as? LinearLayoutManager
        val first = lm?.findFirstVisibleItemPosition() ?: 0
        val topOffset = if (recycler.childCount > 0) recycler.getChildAt(0)?.top ?: 0 else 0
        val q = searchEdit?.text?.toString().orEmpty()

        val base = "viewstate:${keyId}"
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
            .putString("$base:sortKey", sortKey.name)
            .putBoolean("$base:sortAsc", sortAsc)
            .putString("$base:query", q)
            .putInt("$base:firstPos", first)
            .putInt("$base:offset", topOffset)
            .apply()
    }

    private fun restoreLastViewState() {
        val p = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val base = "viewstate:${keyId}"

        val savedKey = p.getString("$base:sortKey", SortKey.NONE.name) ?: SortKey.NONE.name
        sortKey = runCatching { SortKey.valueOf(savedKey) }.getOrElse { SortKey.NONE }
        sortAsc = p.getBoolean("$base:sortAsc", true)

        val q = p.getString("$base:query", "") ?: ""
        if (q.isNotEmpty()) {
            if (searchBar == null) createSearchBar()
            searchBar?.visibility = View.VISIBLE
            searchEdit?.setText(q)
            searchEdit?.setSelection(q.length)
        } else {
            rows = allRows.toMutableList()
            adapter.updateData(rows)
            sortInBlocks()
        }

        val hdrNo = findViewById<TextView?>(R.id.tvNoHeader)
        val hdrBL = findViewById<TextView>(R.id.tvBLHeader)
        val hdrHaju = findViewById<TextView>(R.id.tvHajuHeader)
        val hdrCar = findViewById<TextView>(R.id.tvCarInfoHeader)
        val hdrQty = findViewById<TextView>(R.id.tvQtyHeader)
        val hdrClear = findViewById<TextView>(R.id.tvClearHeader)
        val hdrCheck = findViewById<TextView>(R.id.tvCheckHeader)
        setHeaderIndicators(hdrNo, hdrBL, hdrHaju, hdrCar, hdrQty, hdrClear, hdrCheck)

        val first = p.getInt("$base:firstPos", 0)
        val offset = p.getInt("$base:offset", 0)
        recycler.post { (recycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(first, offset) }
    }

    // ===== 유틸 =====
    private fun applyUiToHeader(cfgRaw: UiConfig) {
        val cfg = cfgRaw.normalized()
        fun setW(id: Int, w: Float) {
            val tv = findViewById<TextView?>(id) ?: return
            (tv.layoutParams as? LinearLayout.LayoutParams)?.let { it.weight = w; tv.layoutParams = it }
        }
        setW(R.id.tvNoHeader, cfg.wNo); setW(R.id.tvBLHeader, cfg.wBL); setW(R.id.tvHajuHeader, cfg.wHaju)
        setW(R.id.tvCarInfoHeader, cfg.wCar); setW(R.id.tvQtyHeader, cfg.wQty); setW(R.id.tvClearHeader, cfg.wClear); setW(R.id.tvCheckHeader, cfg.wCheck)
    }

    private fun applyDivider(show: Boolean) {
        rowDivider?.let { recycler.removeItemDecoration(it); rowDivider = null }
        if (show) { rowDivider = DividerItemDecoration(this, LinearLayoutManager.VERTICAL); recycler.addItemDecoration(rowDivider!!) }
    }

    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
        recycler.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private fun dp(v: View, dp: Float) = (dp * v.context.resources.displayMetrics.density).toInt()

    // ===== 엑셀 파싱 =====
    private fun readExcel(file: File): List<CheckRow> {
        val out = mutableListOf<CheckRow>()
        try {
            FileInputStream(file).use { fis ->
                val wb = if (file.name.endsWith(".xls", true)) HSSFWorkbook(fis) else XSSFWorkbook(fis)
                val sheet = wb.getSheetAt(0)
                val fmt = DataFormatter()
                for (r in DEF_DATA_START..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val bl = getRaw(fmt, row, DEF_BL).trim()
                    val haju = getRaw(fmt, row, DEF_HAJU).trim()
                    val descRaw = getRaw(fmt, row, DEF_CAR)
                    val qty = getRaw(fmt, row, DEF_QTY).trim()
                    val clearance = getRaw(fmt, row, DEF_CLEAR).trim()
                    if (bl.isEmpty() && haju.isEmpty() && descRaw.isEmpty() && qty.isEmpty()) continue
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

    // ===== 라이프사이클 =====
    override fun onResume() {
        super.onResume()
        if (this::adapter.isInitialized) updateStatus()
        LogBus.appOpen("차체크화면")
    }

    override fun onPause() {
        super.onPause()
        saveCheckState()
        saveLastViewState()
        updateStatus()
        pushStatusToHomeAndBroadcast()
        LogBus.appClose("차체크화면")
    }

    private fun saveCheckState() {
        val e = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit()
        allRows.forEachIndexed { idx, r ->
            if (!r.isLabelRow) {
                val base = "check_orders:${ParsedCache.keyFor(currentFile).id()}:$idx"
                e.putBoolean("${base}_checked", r.isChecked)
                e.putInt("${base}_order", r.checkOrder)
            }
        }
        e.apply()
    }
}
