package com.example.carchecking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExcelRowAdapter(private val data: List<List<String>>) :
    RecyclerView.Adapter<ExcelRowAdapter.RowViewHolder>() {

    class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textRow: TextView = itemView.findViewById(R.id.textExcelRow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_excel_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.textRow.text = data[position].joinToString(" | ")
    }

    override fun getItemCount(): Int = data.size
}
