package com.example.carchecking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ExcelFileAdapter(private val onItemClick: (File) -> Unit) :
    ListAdapter<File, ExcelFileAdapter.ExcelFileViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: File, newItem: File) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExcelFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_excel_file, parent, false)
        return ExcelFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExcelFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExcelFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val fileNameText: TextView = itemView.findViewById(R.id.textFileName)
        private val fileSizeText: TextView = itemView.findViewById(R.id.textFileSize)

        fun bind(file: File) {
            fileNameText.text = file.name
            fileSizeText.text = "${file.length() / 1024} KB"
            itemView.setOnClickListener { onItemClick(file) }
        }
    }
}
