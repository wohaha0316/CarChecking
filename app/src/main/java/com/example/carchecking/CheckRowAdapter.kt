package com.example.carchecking

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CheckRowAdapter(
    private var rows: List<CheckRow>,
    var ui: UiConfig = UiConfig.defaults(),
    private val onToggle: ((position: Int, newChecked: Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_DATA = 0
    private val TYPE_LABEL = 1

    override fun getItemViewType(position: Int) =
        if (rows[position].isLabelRow) TYPE_LABEL else TYPE_DATA

    private fun dp(v: View, dp: Float) = (dp * v.context.resources.displayMetrics.density).toInt()
    private fun spToPx(v: View, sp: Float) = (sp * v.context.resources.displayMetrics.scaledDensity).toInt()

    private fun tvTight(tv: TextView, sizeSp: Float, singleLine: Boolean, maxLines: Int) {
        tv.textSize = sizeSp
        tv.includeFontPadding = false
        tv.setLineSpacing(0f, 1f)
        tv.isSingleLine = singleLine
        tv.maxLines = maxLines
        tv.ellipsize = if (singleLine) TextUtils.TruncateAt.END else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tv.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            tv.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    private fun applyRowSpacing(v: View) {
        // 행간 슬라이더(ui.rowSpacing) 값이 0이면 패딩 0
        val px = if (ui.rowSpacing <= 0f) 0 else spToPx(v, ui.fCar * ui.rowSpacing)
        v.setPadding(0, 0, 0, px)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_LABEL) {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                setPadding(dp(this, 12f), dp(this, 8f), dp(this, 12f), dp(this, 8f))
                textSize = 15f
                setBackgroundColor(Color.parseColor("#FFF2CC")) // TERMINAL 라벨
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSelected = true
                setHorizontallyScrolling(true)
            }
            LabelVH(tv)
        } else {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
                // 기본 패딩 제거 → 행 높이 최소화
                setPadding(0, 0, 0, 0)
                isBaselineAligned = false
                gravity = Gravity.TOP
            }
            fun lpTop(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply { gravity = Gravity.TOP }
            fun lpCenter(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply { gravity = Gravity.CENTER_VERTICAL }

            val seq = TextView(parent.context).apply {
                layoutParams = lpCenter(ui.wNo)
                tvTight(this, ui.fNo, singleLine = true, maxLines = 1)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0) // 패딩 완전 제거
                setTextColor(Color.DKGRAY)
            }
            val bl = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wBL)
                tvTight(this, ui.fBL, singleLine = !ui.wrapBL, maxLines = if (ui.wrapBL) Int.MAX_VALUE else 1)
            }
            val haju = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wHaju)
                tvTight(this, ui.fHaju, singleLine = !ui.wrapHaju, maxLines = if (ui.wrapHaju) Int.MAX_VALUE else 1)
            }
            val car = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wCar)
                // 차량정보는 항상 여러 줄 허용 + 타이트
                tvTight(this, ui.fCar, singleLine = false, maxLines = Int.MAX_VALUE)
            }
            val qty = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wQty)
                tvTight(this, ui.fQty, singleLine = true, maxLines = 1)
                gravity = Gravity.CENTER
            }
            val clearance = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wClear)
                tvTight(this, ui.fClear, singleLine = true, maxLines = 1)
                gravity = Gravity.CENTER
            }
            val checkBtn = Button(parent.context).apply {
                layoutParams = lpTop(ui.wCheck)
                text = "확인"
                // 버튼도 타이트하게
                isAllCaps = false
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                textSize = ui.fCheck
                includeFontPadding = false
                // 행 높이 강제 방지: 최소 높이 0
                minHeight = 0
                minimumHeight = 0
                // 패딩 최소화
                setPadding(dp(this, 8f), dp(this, 2f), dp(this, 8f), dp(this, 2f))
                // 기본 배경/색(변경은 기존 로직 유지)
                setBackgroundColor(Color.LTGRAY)
            }

            row.addView(seq); row.addView(bl); row.addView(haju); row.addView(car)
            row.addView(qty); row.addView(clearance); row.addView(checkBtn)
            DataVH(row, seq, bl, haju, car, qty, clearance, checkBtn)
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        val item = rows[pos]
        if (h is LabelVH) {
            h.label.text = item.bl
            h.label.setBackgroundColor(Color.parseColor("#FFF2CC"))
            return
        }
        h as DataVH

        // 행간(하단 패딩) 적용 — 기본은 0
        applyRowSpacing(h.itemView)

        // No
        val seqNum = rows.take(pos + 1).count { !it.isLabelRow }
        h.seq.text = seqNum.toString()

        // B/L (마지막 3자리 굵게)
        val blStr = item.bl
        h.bl.text = if (blStr.length >= 3) {
            SpannableStringBuilder(blStr).apply {
                setSpan(StyleSpan(Typeface.BOLD), blStr.length - 3, blStr.length, 0)
            }
        } else blStr

        // 화주
        h.haju.isSingleLine = !ui.wrapHaju
        h.haju.maxLines = if (ui.wrapHaju) Int.MAX_VALUE else 1
        h.haju.ellipsize = if (ui.wrapHaju) null else TextUtils.TruncateAt.END
        h.haju.text = item.haju

        // 차량정보: VIN 전체 굵게
        val raw = item.carInfo.replace("\r\n", "\n").replace('\r', '\n')
        h.car.text = SpannableStringBuilder(raw).also { ssb ->
            val vinRegex = Regex("(?i)(?=([A-HJ-NPR-Z0-9]{17}))\\1")
            vinRegex.findAll(raw).forEach { m ->
                val st = m.range.first; val ed = m.range.last + 1
                ssb.setSpan(StyleSpan(Typeface.BOLD), st, ed, 0)
            }
        }

        // 수 / 면장
        h.qty.text = item.qty
        h.clearance.text = item.clearance

        // 확인 버튼 상태
        if (item.isChecked) {
            h.checkBtn.text = item.checkOrder.toString()
            h.checkBtn.setBackgroundColor(Color.parseColor("#CCFFCC"))
            h.checkBtn.setTextColor(Color.parseColor("#1E4620"))
            h.checkBtn.typeface = Typeface.DEFAULT_BOLD
        } else {
            h.checkBtn.text = "확인"
            h.checkBtn.setBackgroundColor(Color.LTGRAY)
            h.checkBtn.setTextColor(Color.BLACK)
            h.checkBtn.typeface = Typeface.DEFAULT
        }

        h.checkBtn.setOnClickListener {
            val act = h.itemView.context as CheckListActivity
            val nowChecked = !item.isChecked
            if (nowChecked) {
                act.orderCounter++
                item.isChecked = true
                item.checkOrder = act.orderCounter
            } else {
                item.isChecked = false
                item.checkOrder = 0
            }
            onToggle?.invoke(h.adapterPosition, nowChecked)
            act.reindexOrders()
        }
    }

    override fun getItemCount() = rows.size
    fun updateData(newRows: List<CheckRow>) { rows = newRows; notifyDataSetChanged() }
    fun updateUi(newUi: UiConfig) { ui = newUi.normalized(); notifyDataSetChanged() }

    class DataVH(
        row: View,
        val seq: TextView,
        val bl: TextView,
        val haju: TextView,
        val car: TextView,
        val qty: TextView,
        val clearance: TextView,
        val checkBtn: Button
    ) : RecyclerView.ViewHolder(row)

    class LabelVH(val label: TextView) : RecyclerView.ViewHolder(label)
}
