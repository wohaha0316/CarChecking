package com.example.carchecking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class VinAdapter(
    private val onConfirmClick: (VinData) -> Unit
) : ListAdapter<VinData, VinAdapter.VinViewHolder>(DiffCallback()) {

    class VinViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val vinText: TextView = view.findViewById(R.id.vinText)
        val modelText: TextView = view.findViewById(R.id.modelText)
        val colorText: TextView = view.findViewById(R.id.colorText)
        val confirmButton: Button = view.findViewById(R.id.confirmButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VinViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vin_row, parent, false)
        return VinViewHolder(view)
    }

    override fun onBindViewHolder(holder: VinViewHolder, position: Int) {
        val item = getItem(position)
        holder.vinText.text = item.vin
        holder.modelText.text = item.model
        holder.colorText.text = item.color

        holder.confirmButton.text = if (item.isConfirmed) "완료" else "확인"
        holder.confirmButton.isEnabled = !item.isConfirmed

        holder.confirmButton.setOnClickListener {
            onConfirmClick(item)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<VinData>() {
        override fun areItemsTheSame(oldItem: VinData, newItem: VinData) = oldItem.vin == newItem.vin
        override fun areContentsTheSame(oldItem: VinData, newItem: VinData) = oldItem == newItem
    }
}
