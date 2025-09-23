package com.example.carchecking

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView

/**
 * 차체크 화면 어댑터.
 * - ‘확인’ 버튼 텍스트 자동맞춤(버튼 안에 딱 맞게)
 * - 액티비티 의존 없음(캐스팅 제거)
 * - 순번 부여/당겨오기를 어댑터 내부에서 수행
 */
class CheckRowAdapter(
    private var rows: List<CheckRow>,
    var ui: UiConfig,
    private val onToggle: ((position: Int, newChecked: Boolean) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_DATA = 0
    private val TYPE_LABEL = 1

    override fun getItemViewType(position: Int) =
        if (rows[position].isLabelRow) TYPE_LABEL else TYPE_DATA

    private fun dp(v: View, dp: Float) = (dp * v.context.resources.displayMetrics.density).toInt()
    private fun spToPx(v: View, sp: Float) = (sp * v.context.resources.displayMetrics.scaledDensity).toInt()

    @Suppress("WrongConstant")
    private fun tvTight(tv: TextView, sizeSp: Float) {
        tv.textSize = sizeSp
        tv.includeFontPadding = false
        tv.setLineSpacing(0f, 1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tv.breakStrategy =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
                else
                    android.text.Layout.BREAK_STRATEGY_SIMPLE
            tv.hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
        }
    }

    private fun applyRowSpacing(v: View) {
        val px = if (ui.rowSpacing <= 0f) 0 else spToPx(v, ui.fCar * ui.rowSpacing)
        v.setPadding(0, 0, 0, px)
    }

    private fun lpTop(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply {
        gravity = Gravity.TOP
    }
    private fun lpCenter(w: Float) = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply {
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun setChildVert(tv: TextView, center: Boolean) {
        (tv.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            val newG = if (center) Gravity.CENTER_VERTICAL else Gravity.TOP
            if (lp.gravity != newG) { lp.gravity = newG; tv.layoutParams = lp }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_LABEL) {
            val tv = TextView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(this, 12f), dp(this, 8f), dp(this, 12f), dp(this, 8f))
                textSize = 15f
                setBackgroundColor(Color.parseColor("#FFF2CC"))
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
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 0, 0, 0)
                isBaselineAligned = false
                gravity = Gravity.TOP
            }

            val seq = TextView(parent.context).apply {
                layoutParams = lpCenter(ui.wNo)
                tvTight(this, ui.fNo)
                isSingleLine = true; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, 0, 0, 0)                           // ◀ 좌측 패딩 0
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START   // ◀ 왼쪽 정렬
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setTextColor(Color.DKGRAY)
            }
            val bl = TextView(parent.context).apply {
                layoutParams = if (ui.wrapBL) lpTop(ui.wBL) else lpCenter(ui.wBL)
                tvTight(this, ui.fBL)
                isSingleLine = !ui.wrapBL
                maxLines = if (ui.wrapBL) Int.MAX_VALUE else 1
                ellipsize = if (ui.wrapBL) null else TextUtils.TruncateAt.END
                setPadding(dp(this, 4f), dp(this, 2f), dp(this, 4f), dp(this, 2f))
            }
            val haju = TextView(parent.context).apply {
                layoutParams = if (ui.wrapHaju) lpTop(ui.wHaju) else lpCenter(ui.wHaju)
                tvTight(this, ui.fHaju)
                isSingleLine = !ui.wrapHaju
                maxLines = if (ui.wrapHaju) Int.MAX_VALUE else 1
                ellipsize = if (ui.wrapHaju) null else TextUtils.TruncateAt.END
                setPadding(dp(this, 4f), dp(this, 2f), dp(this, 4f), dp(this, 2f))
            }
            val car = TextView(parent.context).apply {
                layoutParams = lpTop(ui.wCar)
                tvTight(this, ui.fCar)
                isSingleLine = false; maxLines = Int.MAX_VALUE; ellipsize = null
                setPadding(0, 0, dp(this, 2f), 0)   // ◀ 오른쪽 패딩만 2dp
            }
            val qty = TextView(parent.context).apply {
                layoutParams = lpCenter(ui.wQty)
                tvTight(this, ui.fQty)
                isSingleLine = true; maxLines = 1; gravity = Gravity.CENTER
                ellipsize = TextUtils.TruncateAt.END
            }
            val clearance = TextView(parent.context).apply {
                layoutParams = lpCenter(ui.wClear)
                tvTight(this, ui.fClear)
                isSingleLine = true; maxLines = 1; gravity = Gravity.CENTER
                ellipsize = TextUtils.TruncateAt.END
            }
            val checkBtn = Button(parent.context).apply {
                layoutParams = lpCenter(ui.wCheck)
                text = "확인"
                isAllCaps = false
                isSingleLine = true
                includeFontPadding = false
                minHeight = 0; minimumHeight = 0
                setPadding(dp(this, 8f), dp(this, 2f), dp(this, 8f), dp(this, 2f))
                setBackgroundColor(Color.parseColor("#CCCCCC"))
                setTextColor(Color.parseColor("#000000"))
                // ✅ 텍스트 자동맞춤: 10sp ~ 14sp 범위
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this, 10, 14, 1, TypedValue.COMPLEX_UNIT_SP
                )
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

        applyRowSpacing(h.itemView)

        val seqNum = rows.take(pos + 1).count { !it.isLabelRow }
        h.seq.text = seqNum.toString()

        // B/L 끝 3자리 굵게
        val blRawAll = item.bl.replace("\r\n", "\n").replace('\r', '\n')
        val blText = if (ui.wrapBL) blRawAll else blRawAll.replace("\n", " ")
        h.bl.apply {
            text = if (blText.length >= 3) {
                SpannableStringBuilder(blText).apply {
                    setSpan(StyleSpan(Typeface.BOLD), blText.length - 3, blText.length, 0)
                }
            } else blText
            isSingleLine = !ui.wrapBL
            maxLines = if (ui.wrapBL) Int.MAX_VALUE else 1
            ellipsize = if (ui.wrapBL) null else TextUtils.TruncateAt.END
            setChildVert(this, center = !ui.wrapBL || !blRawAll.contains('\n'))
        }

        // 화주
        val hajuRawAll = item.haju.replace("\r\n", "\n").replace('\r', '\n')
        val hajuText = if (ui.wrapHaju) hajuRawAll else hajuRawAll.replace("\n", " ")
        h.haju.apply {
            text = hajuText
            isSingleLine = !ui.wrapHaju
            maxLines = if (ui.wrapHaju) Int.MAX_VALUE else 1
            ellipsize = if (ui.wrapHaju) null else TextUtils.TruncateAt.END
            setChildVert(this, center = !ui.wrapHaju || !hajuRawAll.contains('\n'))
        }

        // 차량정보(VIN 볼드 옵션)
        val carRaw = item.carInfo.replace("\r\n", "\n").replace('\r', '\n')
        if (ui.vinBold) {
            h.car.text = SpannableStringBuilder(carRaw).also { ssb ->
                val vinRegex = Regex("(?i)(?=([A-HJ-NPR-Z0-9]{17}))\\1")
                vinRegex.findAll(carRaw).forEach { m ->
                    val st = m.range.first; val ed = m.range.last + 1
                    ssb.setSpan(StyleSpan(Typeface.BOLD), st, ed, 0)
                }
            }
        } else h.car.text = carRaw

        h.qty.text = item.qty
        h.clearance.text = item.clearance

        val BLUE = Color.parseColor("#1E90FF")
        val GREY = Color.parseColor("#CCCCCC")
        val ON_PRIMARY = Color.parseColor("#FFFFFF")
        val ON_GREY = Color.parseColor("#000000")

        if (item.isChecked) {
            h.checkBtn.text = item.checkOrder.takeIf { it > 0 }?.toString() ?: ""
            h.checkBtn.setBackgroundColor(Color.parseColor("#1E90FF"))
            h.checkBtn.setTextColor(Color.parseColor("#FFFFFF"))
            h.checkBtn.typeface = Typeface.DEFAULT_BOLD
        } else {
            h.checkBtn.text = ""                 // ◀ 미확인 시 텍스트 제거
            h.checkBtn.setBackgroundColor(Color.parseColor("#CCCCCC"))
            h.checkBtn.setTextColor(Color.parseColor("#000000"))
            h.checkBtn.typeface = Typeface.DEFAULT
        }

        // 클릭: 순번 관리(앞 번호 해제 시 뒤 번호 1씩 당김)
        h.checkBtn.setOnClickListener {
            val nowChecked = !item.isChecked
            val removedOrder = item.checkOrder

            if (nowChecked) {
                val nextOrder = (rows.filter { it.checkOrder > 0 }.maxOfOrNull { it.checkOrder } ?: 0) + 1
                item.isChecked = true
                item.checkOrder = nextOrder
            } else {
                item.isChecked = false
                item.checkOrder = 0
                if (removedOrder > 0) {
                    rows.forEach { r -> if (r.checkOrder > removedOrder) r.checkOrder -= 1 }
                }
            }

            onToggle?.invoke(h.bindingAdapterPosition, nowChecked)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = rows.size

    fun updateData(newRows: List<CheckRow>) { rows = newRows; notifyDataSetChanged() }
    fun updateUi(newUi: UiConfig) { ui = newUi; notifyDataSetChanged() }

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
