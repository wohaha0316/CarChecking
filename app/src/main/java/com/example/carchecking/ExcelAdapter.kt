package com.example.carchecking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExcelAdapter(private val data: List<List<String>>) :
    RecyclerView.Adapter<ExcelAdapter.RowViewHolder>() {

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rowText: TextView = view.findViewById(R.id.textExcelRow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_excel_row, parent, false)
        return RowViewHolder(v)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        holder.rowText.text = data[position].joinToString(" | ")
    }

    override fun getItemCount(): Int = data.size
}
