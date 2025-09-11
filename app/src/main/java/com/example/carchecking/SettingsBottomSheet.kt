package com.example.carchecking

import android.content.Context
import android.view.LayoutInflater
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialog

class SettingsBottomSheet(
    context: Context,
    initial: UiConfig,
    private val onApply: (UiConfig, UiPrefs.Scope) -> Unit,
    private val onLiveChange: (UiConfig) -> Unit,
    private val onResetToDefault: (UiConfig) -> Unit // 기본값 복귀 시 미리보기/적용용
) : BottomSheetDialog(context) {

    private var cfg = initial.copy()
    private var scope: UiPrefs.Scope = UiPrefs.Scope.FILE

    private fun setupSeek(v: android.view.View, idSeek: Int, idText: Int, label: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit) {
        val sb = v.findViewById<SeekBar>(idSeek)
        val tv = v.findViewById<TextView>(idText)
        sb.max = max - min
        sb.progress = (get() - min).coerceIn(0, max - min)
        tv.text = "$label ${get()}"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                val value = (min + p).coerceIn(min, max)
                set(value)
                tv.text = "$label $value"
                onLiveChange(cfg)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupWeight(v: android.view.View, idSeek: Int, idText: Int, label: String, get: () -> Float, set: (Float) -> Unit) {
        val sb = v.findViewById<SeekBar>(idSeek)
        val tv = v.findViewById<TextView>(idText)
        val min = 4
        val max = 70
        val cur = (get() * 100).toInt().coerceIn(min, max)
        sb.max = max - min
        sb.progress = cur - min
        tv.text = "$label ${cur}%"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                val value = ((min + p) / 100f).coerceIn(0.04f, 0.70f)
                set(value)
                tv.text = "$label ${(value * 100).toInt()}%"
                onLiveChange(cfg)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupRowSpacing(v: android.view.View) {
        val sb = v.findViewById<SeekBar>(R.id.sbRowSpacing)
        val tv = v.findViewById<TextView>(R.id.tvRowSpacing)
        // 0.10 ~ 0.80 (계수) → 10% ~ 80% 표시
        val toProgress = { value: Float -> ((value.coerceIn(0.10f, 0.80f) - 0.10f) * 100).toInt() }
        val toValue = { progress: Int -> (0.10f + progress / 100f).coerceIn(0.10f, 0.80f) }

        sb.max = 70
        sb.progress = toProgress(cfg.rowSpacing)
        tv.text = "행간 ${(cfg.rowSpacing * 100).toInt()}%"

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                cfg.rowSpacing = toValue(p)
                tv.text = "행간 ${(cfg.rowSpacing * 100).toInt()}%"
                onLiveChange(cfg)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun refreshAllControls(v: android.view.View) {
        // weight
        v.findViewById<SeekBar>(R.id.sbWNo)?.progress = ((cfg.wNo * 100).toInt() - 4).coerceIn(0, 66)
        v.findViewById<TextView>(R.id.tvWNo)?.text = "No 폭 ${(cfg.wNo * 100).toInt()}%"
        v.findViewById<SeekBar>(R.id.sbWBL)?.progress = ((cfg.wBL * 100).toInt() - 4).coerceIn(0, 66)
        v.findViewById<TextView>(R.id.tvWBL)?.text = "B/L 폭 ${(cfg.wBL * 100).toInt()}%"
        v.findViewById<SeekBar>(R.id.sbWHaju)?.progress = ((cfg.wHaju * 100).toInt() - 4).coerceIn(0, 66)
        v.findViewById<TextView>(R.id.tvWHaju)?.text = "화주 폭 ${(cfg.wHaju * 100).toInt()}%"
        v.findViewById<SeekBar>(R.id.sbWCar)?.progress = ((cfg.wCar * 100).toInt() - 4).coerceIn(0, 66)
        v.findViewById<TextView>(R.id.tvWCar)?.text = "차량정보 폭 ${(cfg.wCar * 100).toInt()}%"
        v.findViewById<SeekBar>(R.id.sbWQty)?.progress = ((cfg.wQty * 100).toInt() - 4).coerceIn(0, 66)
        v.findViewById<TextView>(R.id.tvWQty)?.text = "수(Qty) 폭 ${(cfg.wQty * 100).toInt()}%"
        v.findViewById<SeekBar>(R.id.sbWClear)?.progress = ((cfg.wClear * 100).toInt() - 4).coerceIn(0, 66)
        v.findViewById<TextView>(R.id.tvWClear)?.text = "면장 폭 ${(cfg.wClear * 100).toInt()}%"
        v.findViewById<SeekBar>(R.id.sbWCheck)?.progress = ((cfg.wCheck * 100).toInt() - 4).coerceIn(0, 66)
        v.findViewById<TextView>(R.id.tvWCheck)?.text = "확인 폭 ${(cfg.wCheck * 100).toInt()}%"

        // font
        v.findViewById<SeekBar>(R.id.sbFNo)?.progress = (cfg.fNo.toInt() - 8).coerceIn(0, 12)
        v.findViewById<TextView>(R.id.tvFNo)?.text = "No 글자 ${cfg.fNo.toInt()}"
        v.findViewById<SeekBar>(R.id.sbFBL)?.progress = (cfg.fBL.toInt() - 9).coerceIn(0, 13)
        v.findViewById<TextView>(R.id.tvFBL)?.text = "B/L 글자 ${cfg.fBL.toInt()}"
        v.findViewById<SeekBar>(R.id.sbFHaju)?.progress = (cfg.fHaju.toInt() - 9).coerceIn(0, 13)
        v.findViewById<TextView>(R.id.tvFHaju)?.text = "화주 글자 ${cfg.fHaju.toInt()}"
        v.findViewById<SeekBar>(R.id.sbFCar)?.progress = (cfg.fCar.toInt() - 9).coerceIn(0, 13)
        v.findViewById<TextView>(R.id.tvFCar)?.text = "차량정보 글자 ${cfg.fCar.toInt()}"
        v.findViewById<SeekBar>(R.id.sbFQty)?.progress = (cfg.fQty.toInt() - 8).coerceIn(0, 12)
        v.findViewById<TextView>(R.id.tvFQty)?.text = "수(Qty) 글자 ${cfg.fQty.toInt()}"
        v.findViewById<SeekBar>(R.id.sbFClear)?.progress = (cfg.fClear.toInt() - 8).coerceIn(0, 12)
        v.findViewById<TextView>(R.id.tvFClear)?.text = "면장 글자 ${cfg.fClear.toInt()}"
        v.findViewById<SeekBar>(R.id.sbFCheck)?.progress = (cfg.fCheck.toInt() - 8).coerceIn(0, 12)
        v.findViewById<TextView>(R.id.tvFCheck)?.text = "확인 글자 ${cfg.fCheck.toInt()}"

        // wrap / vin
        v.findViewById<Switch>(R.id.swWrapBL)?.isChecked = cfg.wrapBL
        v.findViewById<Switch>(R.id.swWrapHaju)?.isChecked = cfg.wrapHaju
        v.findViewById<Switch>(R.id.swVinBold)?.isChecked = cfg.vinBold

        // row spacing
        v.findViewById<SeekBar>(R.id.sbRowSpacing)?.progress = (((cfg.rowSpacing - 0.10f) * 100).toInt()).coerceIn(0, 70)
        v.findViewById<TextView>(R.id.tvRowSpacing)?.text = "행간 ${(cfg.rowSpacing * 100).toInt()}%"

        // divider
        v.findViewById<Switch>(R.id.swRowDivider)?.isChecked = cfg.showRowDividers
    }

    init {
        val v = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_settings, null)
        setContentView(v)

        // 저장 범위
        val swScope = v.findViewById<Switch>(R.id.swScope)
        swScope?.isChecked = (scope == UiPrefs.Scope.FILE)
        swScope?.setOnCheckedChangeListener { _, b ->
            scope = if (b) UiPrefs.Scope.FILE else UiPrefs.Scope.APP
        }

        // ---- 열 너비 ----
        setupWeight(v, R.id.sbWNo, R.id.tvWNo, "No 폭", { cfg.wNo }, { cfg.wNo = it })
        setupWeight(v, R.id.sbWBL, R.id.tvWBL, "B/L 폭", { cfg.wBL }, { cfg.wBL = it })
        setupWeight(v, R.id.sbWHaju, R.id.tvWHaju, "화주 폭", { cfg.wHaju }, { cfg.wHaju = it })
        setupWeight(v, R.id.sbWCar, R.id.tvWCar, "차량정보 폭", { cfg.wCar }, { cfg.wCar = it })
        setupWeight(v, R.id.sbWQty, R.id.tvWQty, "수(Qty) 폭", { cfg.wQty }, { cfg.wQty = it })
        setupWeight(v, R.id.sbWClear, R.id.tvWClear, "면장 폭", { cfg.wClear }, { cfg.wClear = it })
        setupWeight(v, R.id.sbWCheck, R.id.tvWCheck, "확인 폭", { cfg.wCheck }, { cfg.wCheck = it })

        // ---- 글자 크기 (sp) ----
        fun f(vf: Float) = vf.toInt()
        setupSeek(v, R.id.sbFNo, R.id.tvFNo, "No 글자", 8, 20, { f(cfg.fNo) }, { cfg.fNo = it.toFloat() })
        setupSeek(v, R.id.sbFBL, R.id.tvFBL, "B/L 글자", 8, 26, { f(cfg.fBL) }, { cfg.fBL = it.toFloat() })
        setupSeek(v, R.id.sbFHaju, R.id.tvFHaju, "화주 글자", 8, 26, { f(cfg.fHaju) }, { cfg.fHaju = it.toFloat() })
        setupSeek(v, R.id.sbFCar, R.id.tvFCar, "차량정보 글자", 8, 26, { f(cfg.fCar) }, { cfg.fCar = it.toFloat() })
        setupSeek(v, R.id.sbFQty, R.id.tvFQty, "수(Qty) 글자", 8, 22, { f(cfg.fQty) }, { cfg.fQty = it.toFloat() })
        setupSeek(v, R.id.sbFClear, R.id.tvFClear, "면장 글자", 8, 22, { f(cfg.fClear) }, { cfg.fClear = it.toFloat() })
        setupSeek(v, R.id.sbFCheck, R.id.tvFCheck, "확인 글자", 8, 22, { f(cfg.fCheck) }, { cfg.fCheck = it.toFloat() })

        // ---- 줄바꿈/스타일 ----
        val swWrapBL = v.findViewById<Switch>(R.id.swWrapBL)
        val swWrapHaju = v.findViewById<Switch>(R.id.swWrapHaju)
        val swVinBold = v.findViewById<Switch>(R.id.swVinBold)

        swWrapBL?.isChecked = cfg.wrapBL
        swWrapHaju?.isChecked = cfg.wrapHaju
        swVinBold?.isChecked = cfg.vinBold

        swWrapBL?.setOnCheckedChangeListener { _, b -> cfg.wrapBL = b; onLiveChange(cfg) }
        swWrapHaju?.setOnCheckedChangeListener { _, b -> cfg.wrapHaju = b; onLiveChange(cfg) }
        swVinBold?.setOnCheckedChangeListener { _, b -> cfg.vinBold = b; onLiveChange(cfg) }

        // ---- 행간 ----
        setupRowSpacing(v)

        // ---- 기본 Divider 스위치 ----
        val swRowDivider = v.findViewById<Switch>(R.id.swRowDivider)
        swRowDivider?.isChecked = cfg.showRowDividers
        swRowDivider?.setOnCheckedChangeListener { _, b -> cfg.showRowDividers = b; onLiveChange(cfg) }

        // ---- 하단 버튼 ----
        v.findViewById<Button>(R.id.btnReset)?.setOnClickListener {
            cfg = UiConfig.defaults()
            refreshAllControls(v)
            onResetToDefault(cfg)   // 미리보기 즉시 반영
        }
        v.findViewById<Button>(R.id.btnApply)?.setOnClickListener {
            onApply(cfg, scope); dismiss()
        }
        v.findViewById<Button>(R.id.btnClose)?.setOnClickListener { dismiss() }
    }
}
