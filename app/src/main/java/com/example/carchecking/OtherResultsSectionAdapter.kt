package com.example.carchecking

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 다른 리스트 결과 섹션 어댑터
 * - 한 스크롤(ConcatAdapter)에서 동작
 * - submit()마다 접힘/펼침 규칙을 초기화
 * - 파일이 삭제된 경우를 대비해 외부에서 dropGroupByKey()로 즉시 제거 가능
 */
class OtherResultsSectionAdapter(
    private val ctx: Context,
    private val onMove: (fileKey: String, filePath: String, keyword: String?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // ========= 외부 데이터 =========
    data class Data(
        val query: String,
        val currentHasAny: Boolean,
        val groups: List<Group>
    )
    data class Group(
        val fileKey: String,
        val filePath: String,
        val fileName: String,
        val rows: List<Row>
    )
    data class Row(
        val bl: String,
        val haju: String,
        val carInfo: String,
        val qty: String,
        val clearance: String
    )

    // ========= 내부 플랫 모델 =========
    private sealed class Item {
        data class SectionHeader(val totalCount: Int): Item()
        data class GroupHeader(
            val fileKey: String,
            val filePath: String,
            val displayName: String,
            val expanded: Boolean
        ): Item()
        object ColumnsHeader: Item()
        data class DataRow(val no: Int, val r: Row): Item()
    }

    private var data: Data? = null
    private val collapsed: MutableMap<String, Boolean> = mutableMapOf()
    private val items: MutableList<Item> = mutableListOf()

    /** 새 데이터 반영(접힘 상태는 규칙에 따라 초기화) */
    fun submit(newData: Data?) {
        data = newData
        collapsed.clear()
        newData?.let { d ->
            val shouldExpand = (d.groups.size == 1 && !d.currentHasAny)
            d.groups.forEach { g -> collapsed[g.fileKey] = shouldExpand }
        }
        rebuildItems()
        notifyDataSetChanged()
    }

    /** 삭제된(또는 무효) 파일의 그룹을 즉시 제거 */
    fun dropGroupByKey(fileKey: String) {
        val d = data ?: return
        val filtered = d.groups.filter { it.fileKey != fileKey }
        data = d.copy(groups = filtered)
        // 새 규칙 다시 적용(그룹 수가 1개가 됐을 수 있으니)
        val shouldExpand = (filtered.size == 1 && !(d.currentHasAny))
        collapsed.clear()
        filtered.forEach { g -> collapsed[g.fileKey] = shouldExpand }
        rebuildItems()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
    override fun getItemViewType(position: Int): Int = when(items[position]) {
        is Item.SectionHeader -> 0
        is Item.GroupHeader   -> 1
        is Item.ColumnsHeader -> 2
        is Item.DataRow       -> 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when(viewType) {
            0 -> SectionHeaderVH(makeSectionHeader())
            1 -> GroupHeaderVH(makeGroupHeader())
            2 -> ColumnsHeaderVH(makeColumnsHeader())
            else -> RowVH(makeRow())
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val it = items[position]) {
            is Item.SectionHeader -> (holder as SectionHeaderVH).bind(it)
            is Item.GroupHeader   -> (holder as GroupHeaderVH).bind(it)
            is Item.ColumnsHeader -> {}
            is Item.DataRow       -> (holder as RowVH).bind(it)
        }
    }

    // ========= ViewHolders =========

    private inner class SectionHeaderVH(v: View): RecyclerView.ViewHolder(v) {
        private val tv = v as TextView
        fun bind(m: Item.SectionHeader) {
            tv.text = "다른 리스트에서 발견됨 (${m.totalCount}개)"
        }
    }

    private inner class GroupHeaderVH(v: View): RecyclerView.ViewHolder(v) {
        private val root   = v as LinearLayout
        private val tvArrow= root.getChildAt(0) as TextView
        private val tvName = root.getChildAt(1) as TextView
        private val btn    = root.getChildAt(2) as Button

        fun bind(m: Item.GroupHeader) {
            tvArrow.text = if (m.expanded) "▼" else "▶"
            tvName.text  = m.displayName
            btn.setOnClickListener {
                val item = items[adapterPosition] as Item.GroupHeader
                onMove(item.fileKey, item.filePath, data?.query)
            }
            root.setOnClickListener {
                val item = items[adapterPosition] as Item.GroupHeader
                toggle(item.fileKey)
            }
        }
    }

    private inner class ColumnsHeaderVH(v: View): RecyclerView.ViewHolder(v)
    private inner class RowVH(v: View): RecyclerView.ViewHolder(v) {
        private val row = v as LinearLayout
        private val tvNo   = row.getChildAt(0) as TextView
        private val tvBL   = row.getChildAt(1) as TextView
        private val tvHaju = row.getChildAt(2) as TextView
        private val tvCar  = row.getChildAt(3) as TextView
        private val tvQty  = row.getChildAt(4) as TextView
        private val tvClr  = row.getChildAt(5) as TextView
        fun bind(m: Item.DataRow) {
            tvNo.text = m.no.toString()
            tvBL.text = m.r.bl
            tvHaju.text = m.r.haju
            tvCar.text = m.r.carInfo
            tvQty.text = m.r.qty
            tvClr.text = m.r.clearance
        }
    }

    // ========= 레이아웃 빌더 =========

    // 열 가중치(요청 반영: B/L, 차량정보 넓게 / No, 수, 면장 좁게)
    private val W_NO  = 0.5f
    private val W_BL  = 1.7f
    private val W_HAJ = 1.3f
    private val W_CAR = 3.0f
    private val W_QTY = 0.6f
    private val W_CLR = 0.5f

    private fun makeSectionHeader(): View =
        TextView(ctx).apply {
            setPadding(dp(10), dp(8), dp(10), dp(6))
            setBackgroundColor(0xFFEEEEEE.toInt())
            setTextColor(0xFF222222.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

    private fun makeGroupHeader(): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setBackgroundColor(0xFFF5F5F5.toInt())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // 삼각형(검은 아이콘 느낌: 문자로 처리)
            val tvArrow = TextView(ctx).apply {
                text = "▶"
                textSize = 14f
                setTextColor(0xFF111111.toInt())
                setPadding(0, 0, dp(6), 0)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            // 파일명 (n건) — 글자크기 +1pt
            val tvName = TextView(ctx).apply {
                textSize = 14f      // ← 기존 13f에서 +1pt
                setTextColor(0xFF111111.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            // 이동 버튼 — 최소폭 20% 키움
            val btn = Button(ctx).apply {
                text = "이동"
                textSize = 12f
                // 기존보다 20% 더 넓게: 최소폭 증가
                val min = dp(76)    // 대략 20% 늘린 값(환경별 폰트차 보정)
                minWidth = min
                minimumWidth = min
                setPadding(dp(10), dp(2), dp(10), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            addView(tvArrow) // idx 0
            addView(tvName)  // idx 1
            addView(btn)     // idx 2
        }

    private fun makeColumnsHeader(): View =
        rowCommon(isHeader = true).apply { setBackgroundColor(0xFFEFEFEF.toInt()) }

    private fun makeRow(): View = rowCommon(isHeader = false)

    private fun rowCommon(isHeader: Boolean): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(if (isHeader) 6 else 4), dp(6), dp(if (isHeader) 6 else 4))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            fun cell(weight: Float, bold: Boolean = false): TextView =
                TextView(ctx).apply {
                    textSize = if (isHeader) 12.5f else 12f
                    if (bold) typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFF222222.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
                }

            addView(cell(W_NO,  bold = isHeader).apply { text = if (isHeader) "No" else "" })
            addView(cell(W_BL,  bold = isHeader).apply { text = if (isHeader) "B/L" else "" })
            addView(cell(W_HAJ, bold = isHeader).apply { text = if (isHeader) "화주" else "" })
            addView(cell(W_CAR, bold = isHeader).apply { text = if (isHeader) "차량정보" else "" })
            addView(cell(W_QTY, bold = isHeader).apply { text = if (isHeader) "수" else "" })
            addView(cell(W_CLR, bold = isHeader).apply { text = if (isHeader) "면장" else "" })
        }

    // ========= 내부 로직 =========

    private fun rebuildItems() {
        items.clear()
        val d = data ?: return
        val total = d.groups.sumOf { it.rows.size }

        // 현재 리스트에만 있고 다른 리스트 결과가 0이면 섹션을 숨김
        if (total == 0 && d.currentHasAny) return

        items += Item.SectionHeader(total)

        d.groups.forEach { g ->
            val expanded = collapsed[g.fileKey] == true
            val name = buildDisplayName(g.fileName, g.rows.size)
            items += Item.GroupHeader(g.fileKey, g.filePath, name, expanded)
            if (expanded) {
                items += Item.ColumnsHeader
                var idx = 1
                g.rows.forEach { r -> items += Item.DataRow(idx++, r) }
            }
        }
    }

    private fun toggle(fileKey: String) {
        collapsed[fileKey] = !(collapsed[fileKey] ?: false)
        rebuildItems()
        notifyDataSetChanged()
    }

    private fun buildDisplayName(name: String, rowsCount: Int): String {
        val trimmed = if (name.length > 30) name.substring(0, 30) + "…" else name
        return "$trimmed (${rowsCount}건)"
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
