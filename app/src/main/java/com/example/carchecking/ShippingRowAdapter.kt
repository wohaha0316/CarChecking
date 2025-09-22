package com.example.carchecking

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ShippingRowAdapter(
    private val context: Context,
    private val keyId: String,
    private val rows: List<ShipRow>,
    private val onToggleShip: (rowIndex: Int, newValue: Boolean) -> Unit,
    private val readChecked: (rowIndex: Int) -> Boolean,
    private val readShip: (rowIndex: Int) -> Boolean
) : RecyclerView.Adapter<ShippingRowAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_shipping_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val row = rows[position]
        h.tvNo.text = row.no
        h.tvBL.text = row.bl
        h.tvCarInfo.text = row.carInfo
        h.tvClearance.text = row.clearance.ifBlank { "-" }

        // 확인(O/X) = 차체크 화면에서 확인된 행인가?
        val checked = readChecked(row.rowIndex)
        h.tvChecked.text = if (checked) "O" else "X"
        h.tvChecked.typeface = if (checked) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        // 선적 토글 버튼
        val shipped = readShip(row.rowIndex)
        h.btnShip.text = if (shipped) "해제" else "선적"
        h.btnShip.setOnClickListener {
            val newValue = !readShip(row.rowIndex)
            h.btnShip.text = if (newValue) "해제" else "선적"
            onToggleShip(row.rowIndex, newValue)
        }
    }

    override fun getItemCount(): Int = rows.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvNo: TextView = v.findViewById(R.id.tvNo)
        val tvBL: TextView = v.findViewById(R.id.tvBL)
        val tvCarInfo: TextView = v.findViewById(R.id.tvCarInfo)
        val tvClearance: TextView = v.findViewById(R.id.tvClearance)
        val tvChecked: TextView = v.findViewById(R.id.tvChecked)
        val btnShip: Button = v.findViewById(R.id.btnShip)
    }
}

/** 간단 래퍼(가독용) */
class SharedPreferencesEx(private val sp: SharedPreferences) {
    fun getBoolean(key: String, def: Boolean = false): Boolean = sp.getBoolean(key, def)
    fun putBoolean(key: String, value: Boolean) { sp.edit().putBoolean(key, value).apply() }
    fun contains(key: String): Boolean = sp.contains(key)
}
