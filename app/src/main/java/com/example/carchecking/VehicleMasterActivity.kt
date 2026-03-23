package com.example.carchecking

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VehicleMasterActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnAdd: Button
    private lateinit var recyclerView: RecyclerView

    private val items = mutableListOf<VehicleMaster>()
    private lateinit var adapter: VehicleMasterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_master)

        etSearch = findViewById(R.id.etSearch)
        btnAdd = findViewById(R.id.btnAdd)
        recyclerView = findViewById(R.id.recyclerView)

        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = VehicleMasterAdapter(
            items = items,
            onEdit = { showEditDialog(it) },
            onDelete = { showDeleteDialog(it) }
        )
        recyclerView.adapter = adapter

        btnAdd.setOnClickListener {
            showEditDialog(null)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                loadItems(s?.toString().orEmpty())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadItems("")
    }

    private fun loadItems(query: String) {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                VehicleMasterRepository.search(this@VehicleMasterActivity, query)
            }

            items.clear()
            items.addAll(list)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showEditDialog(item: VehicleMaster?) {
        val isEdit = item != null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 8)
        }

        val etBrand = EditText(this).apply { hint = "브랜드" }
        val etModel = EditText(this).apply { hint = "모델" }
        val etLength = EditText(this).apply { hint = "전장(mm)" }
        val etWidth = EditText(this).apply { hint = "전폭(mm)" }

        if (item != null) {
            etBrand.setText(item.brand)
            etModel.setText(item.model)
            etLength.setText(item.lengthMm.toString())
            etWidth.setText(item.widthMm.toString())
        }

        root.addView(etBrand)
        root.addView(etModel)
        root.addView(etLength)
        root.addView(etWidth)

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "차량 제원 수정" else "차량 제원 추가")
            .setView(root)
            .setPositiveButton("저장") { _, _ ->
                val brand = etBrand.text.toString().trim()
                val model = etModel.text.toString().trim()
                val length = etLength.text.toString().trim().toIntOrNull()
                val width = etWidth.text.toString().trim().toIntOrNull()

                if (brand.isBlank() || model.isBlank() || length == null || width == null) {
                    Toast.makeText(this, "브랜드, 모델, 전장, 전폭을 제대로 입력해라", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    VehicleMasterRepository.upsert(
                        context = this@VehicleMasterActivity,
                        old = item,
                        brand = brand,
                        model = model,
                        lengthMm = length,
                        widthMm = width
                    )

                    withContext(Dispatchers.Main) {
                        loadItems(etSearch.text.toString())
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteDialog(item: VehicleMaster) {
        AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("${item.brand} ${item.model} 을(를) 삭제할까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    VehicleMasterRepository.delete(this@VehicleMasterActivity, item)

                    withContext(Dispatchers.Main) {
                        loadItems(etSearch.text.toString())
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}