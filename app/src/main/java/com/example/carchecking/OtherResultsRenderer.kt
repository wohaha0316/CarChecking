package com.example.carchecking

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.min

/**
 * ‘다른 리스트에서 발견됨’ 회색 박스 렌더러
 * - 행별 ‘이동’ 버튼 제거
 * - 각 파일 Group 헤더 우측에 ‘이동’ 버튼 하나만 배치
 * - 파일명 30자 제한 + (n건) 표시
 * - 1개 그룹 + 현재리스트에 없음이면 자동 펼침, 그 외에는 기본 접힘
 * - 행/헤더 패딩 축소(타이트)
 * - 부모 컨테이너 가시성도 함께 관리(안전장치)
 */
class OtherResultsRenderer(
    private val ctx: Context,
    private val container: LinearLayout,
    private val onGoToFile: (fileKey: String, filePath: String) -> Unit
) {

    data class Row(
        val bl: String,
        val haju: String,
        val carInfo: String,
        val qty: String,
        val clearance: String
    )

    data class Group(
        val fileKey: String,
        val filePath: String,
        val fileName: String,
        val rows: List<Row>
    )

    data class RenderData(
        val currentFileKey: String,
        val query: String,
        val groups: List<Group>,
        val currentHasAny: Boolean
    )

    fun clear() {
        container.removeAllViews()
        (container.parent as? View)?.visibility = View.GONE
    }

    fun render(data: RenderData) {
        container.removeAllViews()
        (container.parent as? View)?.visibility = View.VISIBLE

        if (!data.currentHasAny) {
            container.addView(badge("현재 리스트에서 일치 없음"))
        }

        if (data.groups.isEmpty()) {
            container.addView(badge("다른 리스트에서 일치 없음"))
            return
        }

        val total = data.groups.sumOf { it.rows.size }
        container.addView(header("다른 리스트에서 발견됨 (${total}개)"))

        // 규칙: “현재 없음 AND 그룹 1개” -> 자동 펼침 / 그 외 전부 접힘
        val shouldAutoExpand = !data.currentHasAny && data.groups.size == 1

        data.groups.forEachIndexed { index, g ->
            val collapsedByRule = !shouldAutoExpand  // 자동 펼침 조건이 아니면 전부 접힘
            container.addView(groupView(g, collapsed = collapsedByRule))
        }
    }

    // ===== 뷰 빌더 =====

    private fun badge(text: String): View =
        TextView(ctx).apply {
            this.text = text
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFFEDEDED.toInt())
            setTextColor(0xFF333333.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }

    private fun header(title: String): View =
        TextView(ctx).apply {
            text = title
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(0xFFDDDDDD.toInt())
            setTextColor(0xFF222222.toInt())
            textSize = 13f
        }

    private fun groupView(g: Group, collapsed: Boolean): View {
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF7F7F7.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        // ── 헤더(파일명 + (n건) + 이동 버튼 + 토글 아이콘)
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        val nameText = limitFileName(g.fileName, 30) + " (${g.rows.size}건)"
        val tvName = TextView(ctx).apply {
            text = nameText
            setTextColor(0xFF111111.toInt())
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnMove = Button(ctx).apply {
            text = "이동"
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { onGoToFile(g.fileKey, g.filePath) }
        }

        val ivToggle = ImageView(ctx).apply {
            setImageResource(android.R.drawable.arrow_down_float)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(6) }
        }

        head.addView(tvName)
        head.addView(btnMove)
        head.addView(ivToggle)
        wrap.addView(head)

        // ── 컬럼 헤더 (No / B/L / 화주 / 차량정보 / 수 / 면장) – 확인/이동 열 없음, 타이트
        val colHead = rowView(
            isHeader = true,
            no = "No", bl = "B/L", haju = "화주", car = "차량정보", qty = "수", clr = "면장"
        )
        wrap.addView(colHead)

        // ── 내용
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (collapsed) View.GONE else View.VISIBLE
        }
        g.rows.forEachIndexed { i, r ->
            body.addView(
                rowView(
                    isHeader = false,
                    no = (i + 1).toString(),
                    bl = r.bl, haju = r.haju, car = r.carInfo, qty = r.qty, clr = r.clearance
                )
            )
        }
        wrap.addView(body)

        // ── 접기/펼치기 (아이콘도 함께 바뀌게)
        fun refreshToggleIcon() {
            ivToggle.setImageResource(
                if (body.visibility == View.VISIBLE)
                    android.R.drawable.arrow_up_float
                else
                    android.R.drawable.arrow_down_float
            )
        }
        head.setOnClickListener {
            body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshToggleIcon()
        }
        refreshToggleIcon()

        return wrap
    }

    private fun rowView(
        isHeader: Boolean,
        no: String, bl: String, haju: String, car: String, qty: String, clr: String
    ): View {
        fun cell(text: String, weight: Float): TextView =
            TextView(ctx).apply {
                this.text = text
                textSize = if (isHeader) 12f else 11f   // 1pt 더 줄임 + 타이트
                setTextColor(0xFF222222.toInt())
                setPadding(dp(4), dp(4), dp(4), dp(4))  // 타이트 패딩
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            }

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (isHeader) 0xFFEFEFEF.toInt() else 0xFFFFFFFF.toInt())
            addView(cell(no, 0.7f))
            addView(cell(bl, 1.4f))
            addView(cell(haju, 1.2f))
            addView(cell(car, 2.6f))
            addView(cell(qty, 0.8f))
            addView(cell(clr, 0.9f))
        }
    }

    private fun limitFileName(name: String, max: Int): String {
        val safe = name.ifBlank { "(이름없음)" }
        return if (safe.length <= max) safe else safe.substring(0, min(max, safe.length)).trimEnd() + "…"
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}
