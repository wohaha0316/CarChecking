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
    var ui: UiConfig = UiConfig.defaults()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_DATA = 0
    private val TYPE_LABEL = 1

    override fun getItemViewType(position: Int): Int =
        if (rows[position].isLabelRow) TYPE_LABEL else TYPE_DATA

    private fun dp(view: View, dp: Float): Int =
        (dp * view.context.resources.displayMetrics.density).toInt()
    private fun spToPx(view: View, sp: Float): Int =
        (sp * view.context.resources.displayMetrics.scaledDensity).toInt()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_LABEL) {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(this, 12f), dp(this, 8f), dp(this, 12f), dp(this, 8f))
                textSize = 15f
                setBackgroundColor(Color.parseColor("#FFF2CC")) // TERMINAL 라벨
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isFocusable = false
                isFocusableInTouchMode = false
                isSelected = true
                setHorizontallyScrolling(true)
            }
            LabelVH(tv)
        } else {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
                // 행간: 글자크기 기반 계수
                setPadding(0, 0, 0, spToPx(this, ui.fCar * ui.rowSpacing))
                // weightSum 강제하지 않음(자식 weight 합 기준 자동 분배 → 가로 스크롤 방지)
                isBaselineAligned = false
                gravity = Gravity.TOP
            }
            fun lpTop(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply { gravity = Gravity.TOP }
            fun lpCenter(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply { gravity = Gravity.CENTER_VERTICAL }

            val seq = TextView(parent.context).apply {
                layoutParams = lpCenter(ui.wNo)
                textSize = ui.fNo
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                maxLines = 1
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                setHorizontallyScrolling(false)
            }
            val bl = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wBL)
                textSize = ui.fBL
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                isSingleLine = !ui.wrapBL
                maxLines = if (ui.wrapBL) Int.MAX_VALUE else 1
                ellipsize = if (ui.wrapBL) null else TextUtils.TruncateAt.END
                setHorizontallyScrolling(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                }
            }
            val haju = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wHaju)
                textSize = ui.fHaju
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                isSingleLine = !ui.wrapHaju
                maxLines = if (ui.wrapHaju) Int.MAX_VALUE else 1
                ellipsize = if (ui.wrapHaju) null else TextUtils.TruncateAt.END
                setHorizontallyScrolling(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                }
            }
            val car = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wCar)
                textSize = ui.fCar
                includeFontPadding = false
                setPadding(0, 0, 0, dp(this, 1f))
                isSingleLine = false
                maxLines = Int.MAX_VALUE
                ellipsize = null
                setHorizontallyScrolling(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
                    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
                }
            }
            val qty = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wQty)
                gravity = Gravity.CENTER
                textSize = ui.fQty
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                setHorizontallyScrolling(false)
            }
            val clearance = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wClear)
                gravity = Gravity.CENTER
                textSize = ui.fClear
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                setHorizontallyScrolling(false)
            }
            val checkBtn = Button(parent.context).apply {
                layoutParams = lpTop(ui.wCheck)
                text = "확인"
                textSize = ui.fCheck
                isAllCaps = false
                minWidth = 0; minimumWidth = 0 // 자기 칸 넘지 않게
                setPadding(dp(this, 8f), dp(this, 4f), dp(this, 8f), dp(this, 4f))
                minHeight = dp(this, 36f)
                setBackgroundColor(Color.LTGRAY)
            }

            row.addView(seq); row.addView(bl); row.addView(haju); row.addView(car)
            row.addView(qty); row.addView(clearance); row.addView(checkBtn)
            DataVH(row, seq, bl, haju, car, qty, clearance, checkBtn)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = rows[position]
        if (holder is LabelVH) {
            holder.label.text = item.bl
            holder.label.setBackgroundColor(Color.parseColor("#FFF2CC"))
            return
        }
        holder as DataVH

        // 행간(하단 패딩) 동적 반영
        holder.itemView.setPadding(
            0, 0, 0,
            spToPx(holder.itemView, ui.fCar * ui.rowSpacing)
        )

        // 순번(라벨 제외 카운트)
        val seqNum = rows.take(position + 1).count { !it.isLabelRow }
        holder.seq.text = seqNum.toString()

        // BL 끝 3자리 굵게
        val blStr = item.bl
        holder.bl.text = if (blStr.length >= 3) {
            SpannableStringBuilder(blStr).apply {
                setSpan(StyleSpan(Typeface.BOLD), blStr.length - 3, blStr.length, 0)
            }
        } else blStr

        // 화주
        holder.haju.isSingleLine = !ui.wrapHaju
        holder.haju.maxLines = if (ui.wrapHaju) Int.MAX_VALUE else 1
        holder.haju.ellipsize = if (ui.wrapHaju) null else TextUtils.TruncateAt.END
        holder.haju.text = item.haju

        // 차량정보: VIN(17자) 굵게
        val raw = item.carInfo.replace("\r\n", "\n").replace('\r', '\n')
        holder.car.text = if (!ui.vinBold) {
            raw
        } else {
            val ssb = SpannableStringBuilder(raw)
            val vinRegex = Regex("(?i)(?=([A-HJ-NPR-Z0-9]{17}))\\1")
            vinRegex.findAll(raw).forEach { m ->
                val st = m.range.first; val ed = m.range.last + 1
                ssb.setSpan(StyleSpan(Typeface.BOLD), st, ed, 0)
            }
            ssb
        }

        holder.qty.text = item.qty
        holder.clearance.text = item.clearance

        // 확인 버튼 상태
        if (item.isChecked) {
            holder.checkBtn.text = item.checkOrder.toString()
            holder.checkBtn.setBackgroundColor(Color.parseColor("#CCFFCC"))
            holder.checkBtn.setTextColor(Color.parseColor("#1E4620"))
            holder.checkBtn.typeface = Typeface.DEFAULT_BOLD
        } else {
            holder.checkBtn.text = "확인"
            holder.checkBtn.setBackgroundColor(Color.LTGRAY)
            holder.checkBtn.setTextColor(Color.BLACK)
            holder.checkBtn.typeface = Typeface.DEFAULT
        }

        holder.checkBtn.setOnClickListener {
            val act = holder.itemView.context as CheckListActivity
            if (item.isChecked) { item.isChecked = false; item.checkOrder = 0 }
            else { act.orderCounter++; item.isChecked = true; item.checkOrder = act.orderCounter }
            act.reindexOrders()
        }
    }

    override fun getItemCount(): Int = rows.size
    fun updateData(newRows: List<CheckRow>) { rows = newRows; notifyDataSetChanged() }
    fun updateUi(newUi: UiConfig) { this.ui = newUi.normalized().clamped(); notifyDataSetChanged() }

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
