package com.example.carchecking

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * 차체크 스타일 유지 + 요구 반영:
 * - [NO(고정, 글자크기 +2sp)] [B/L(끝3자리 굵게, +2sp)] [차대번호(VIN만 굵게, +2sp)]
 *   [면장(X=붉은색)] [확인: 체크된 경우에만 파란 글자 버튼(텍스트=확인순번), 미확인=버튼 없음]
 *   [선적버튼(초록, 텍스트=선적순번; 해제시 "", 당김은 Activity 로직 그대로)]
 * - 마지막 두 열(확인, 선적)은 **헤더와 동일한 너비**(각 0.10f)로 정렬
 */
class ShippingRowAdapter(
    private val rows: List<CheckRow>,
    private val readChecked: (Int) -> Boolean,
    private val readCheckedOrder: (Int) -> Int,  // ✅ 확인 순번 콜백 추가
    private val readShip: (Int) -> Boolean,
    private val readShipOrder: (Int) -> Int,
    private val onToggleShip: (Int) -> Pair<Boolean, Int>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_DATA = 0
    private val TYPE_LABEL = 1

    private val COLOR_GREEN = Color.parseColor("#008000") // 선적(초록)
    private val COLOR_BLUE  = Color.parseColor("#1E90FF") // 확인(파랑)
    private val COLOR_RED   = Color.parseColor("#CC0000") // 면장X(붉은색)
    private val CHIP_GREY   = Color.parseColor("#E0E0E0")
    private val ON_PRIMARY  = Color.parseColor("#FFFFFF")
    private val ON_GREY     = Color.parseColor("#000000")

    // NO(고정) 번호
    private val seqNo: IntArray = computeSerials(rows)

    private fun computeSerials(list: List<CheckRow>): IntArray {
        var n = 0
        val a = IntArray(list.size)
        for (i in list.indices) {
            if (list[i].isLabelRow) a[i] = 0 else { n += 1; a[i] = n }
        }
        return a
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position].isLabelRow) TYPE_LABEL else TYPE_DATA

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val d = parent.context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        return if (viewType == TYPE_LABEL) {
            val tv = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(8), dp(6), dp(8), dp(6))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(Color.parseColor("#EEEEEE"))
            }
            LabelVH(tv)
        } else {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(dp(6), dp(8), dp(6), dp(8))
            }

            // NO (+2sp: 14)
            val tvNo = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.06f)
                textSize = 14f   // ✅ 12→14
                gravity = Gravity.CENTER_VERTICAL
            }

            // B/L (+2sp: 14)
            val tvBL = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.26f)
                textSize = 14f   // ✅ 12→14
                maxLines = 1
            }

            // 차대번호(VIN만 굵게, +2sp: 14)
            val tvVin = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.32f)
                textSize = 14f   // ✅ 12→14
                maxLines = 1
            }

            // 면장 (X=붉은색)
            val tvClear = TextView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.14f)
                gravity = Gravity.END
                textSize = 12f
                maxLines = 1
            }

            // 확인 영역(헤더와 동일 너비 0.10) — 버튼 컨테이너
            val boxCheck = LinearLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.10f)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            val btnCheck = Button(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(32)
                )
                minWidth = dp(56)
                textSize = 12f
                isAllCaps = false
                // 색은 파란 "텍스트" 요구 — 배경은 연회색 유지
                setBackgroundColor(CHIP_GREY)
                setTextColor(COLOR_BLUE)
            }
            boxCheck.addView(btnCheck)

            // 선적 영역(헤더와 동일 너비 0.10) — 버튼 컨테이너
            val boxShip = LinearLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.10f)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            val btnShip = Button(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,  // 컨테이너 내에서 꽉 차게
                    dp(32)
                )
                minWidth = dp(56)
                textSize = 12f
                isAllCaps = false
            }
            boxShip.addView(btnShip)

            row.addView(tvNo)
            row.addView(tvBL)
            row.addView(tvVin)
            row.addView(tvClear)
            row.addView(boxCheck)
            row.addView(boxShip)

            DataVH(
                row = row,
                tvNo = tvNo,
                tvBL = tvBL,
                tvVin = tvVin,
                tvClear = tvClear,
                btnCheck = btnCheck,
                btnShip = btnShip
            )
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        if (holder is LabelVH) {
            holder.label.text = row.carInfo.ifBlank { row.bl } // 구분행
            return
        }
        holder as DataVH

        val checked = readChecked(position)
        val checkedOrder = readCheckedOrder(position)  // ✅ 확인 순번
        val shipped = readShip(position)
        val shipOrder = readShipOrder(position)

        // NO(고정)
        holder.tvNo.text = seqNo[position].toString()

        // B/L: 끝 3자리 굵게
        holder.tvBL.text = boldLast3(row.bl)

        // VIN만 굵게 (없으면 전체 굵게)
        holder.tvVin.text = boldVinOnly(row.carInfo)

        // 면장: X면 붉은색
        holder.tvClear.text = row.clearance
        holder.tvClear.setTextColor(if (isX(row.clearance)) COLOR_RED else Color.DKGRAY)

        // 확인: 체크된 경우에만 "파란 글자 버튼(텍스트=확인순번)" 표시, 아니면 버튼 숨김
        if (checked && checkedOrder > 0) {
            holder.btnCheck.visibility = View.VISIBLE
            holder.btnCheck.text = checkedOrder.toString()
        } else {
            holder.btnCheck.visibility = View.GONE
            holder.btnCheck.text = ""
        }
        // (미래 확장) 클릭 시 위치/사진/확인자 표시 예정 — 지금은 표시용만

        // 선적 버튼: 텍스트=선적순번 / 색상=초록(선적), 회색(미선적)
        if (shipped && shipOrder > 0) {
            holder.btnShip.text = shipOrder.toString()
            holder.btnShip.setBackgroundColor(COLOR_GREEN)
            holder.btnShip.setTextColor(ON_PRIMARY)
        } else {
            holder.btnShip.text = ""
            holder.btnShip.setBackgroundColor(CHIP_GREY)
            holder.btnShip.setTextColor(ON_GREY)
        }
        holder.btnShip.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            onToggleShip(pos) // 상태 변경은 여기서만 (Activity가 안전 notify/post 처리)
        }
    }

    class DataVH(
        val row: View,
        val tvNo: TextView,
        val tvBL: TextView,
        val tvVin: TextView,
        val tvClear: TextView,
        val btnCheck: Button,
        val btnShip: Button
    ) : RecyclerView.ViewHolder(row)

    class LabelVH(val label: TextView) : RecyclerView.ViewHolder(label)

    // ===== Helpers =====
    private fun boldLast3(text: String): CharSequence {
        if (text.isEmpty()) return text
        val s = SpannableStringBuilder(text)
        val from = (text.length - 3).coerceAtLeast(0)
        s.setSpan(StyleSpan(Typeface.BOLD), from, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun boldVinOnly(carInfo: String): CharSequence {
        val vinRegex = Regex("""\b[A-HJ-NPR-Z0-9]{17}\b""") // I,O,Q 제외
        val m = vinRegex.find(carInfo)
        val target = m?.value ?: carInfo
        val s = SpannableStringBuilder(target)
        s.setSpan(StyleSpan(Typeface.BOLD), 0, target.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun isX(clear: String): Boolean {
        val t = clear.trim().lowercase()
        return t in setOf("x","면장x","n","0","미통관","미","x표시","미처리")
    }
}
