package com.example.carchecking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VehicleMasterAdapter(
    private val items: MutableList<VehicleMaster>,
    private val onEdit: (VehicleMaster) -> Unit,
    private val onDelete: (VehicleMaster) -> Unit
) : RecyclerView.Adapter<VehicleMasterAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvBrand: TextView = v.findViewById(R.id.tvBrand)
        val tvModel: TextView = v.findViewById(R.id.tvModel)
        val tvLength: TextView = v.findViewById(R.id.tvLength)
        val tvWidth: TextView = v.findViewById(R.id.tvWidth)
        val btnEdit: Button = v.findViewById(R.id.btnEdit)
        val btnDelete: Button = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle_master, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvBrand.text = item.brand
        holder.tvModel.text = item.model
        holder.tvLength.text = item.lengthMm.toString()
        holder.tvWidth.text = item.widthMm.toString()

        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount(): Int = items.size
}