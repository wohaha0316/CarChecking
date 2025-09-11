package com.example.carchecking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.cardview.widget.CardView
import java.io.File

class ExcelUploadFragment : Fragment() {

    private lateinit var btnSelectExcel: Button
    private lateinit var fileListLayout: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_excel_upload, container, false)

        btnSelectExcel = view.findViewById(R.id.btnSelectExcel)
        fileListLayout = view.findViewById(R.id.fileListLayout)

        btnSelectExcel.setOnClickListener {
            // 파일 선택 로직 (ActivityResultContracts.OpenDocument) 기존 코드 그대로
        }

        renderSavedFiles()
        return view
    }

    private fun renderSavedFiles() {
        fileListLayout.removeAllViews()
        val files = (requireContext().filesDir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".xls", true) || it.name.endsWith(".xlsx", true)) }
            .sortedBy { it.name.lowercase() }

        UploadedExcelStore.files.clear()
        UploadedExcelStore.files.addAll(files)
        files.forEach { addFileRow(it) }
    }

    private fun addFileRow(file: File) {
        // ✅ 그룹 박스 (CardView)
        val card = CardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(12, 12, 12, 12)
            }
            radius = 16f
            cardElevation = 6f
            useCompatPadding = true
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 첫 줄: 파일명(70%) + 로그(20%) + 삭제(10%)
        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            )
        }

        val fileBtn = Button(requireContext()).apply {
            text = file.name
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f)
            setOnClickListener {
                openInExternalExcel(file) // 외부 엑셀 앱 열기
            }
        }

        val logBtn = Button(requireContext()).apply {
            text = "로그"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.2f)
            setOnClickListener {
                Toast.makeText(requireContext(), "로그 기능은 추후 구현 예정", Toast.LENGTH_SHORT).show()
            }
        }

        val delBtn = Button(requireContext()).apply {
            text = "삭제"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.1f)
            setOnClickListener {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("삭제 확인")
                    .setMessage("정말로 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        if (file.delete()) {
                            fileListLayout.removeView(card)
                            UploadedExcelStore.files.remove(file)
                            Toast.makeText(requireContext(), "${file.name} 삭제됨", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "삭제 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }

        topRow.addView(fileBtn)
        topRow.addView(logBtn)
        topRow.addView(delBtn)

        // 두 번째 줄: 현상황(70%) + 차체크(30%)
        val bottomRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                120
            )
        }

        // ✅ 현상황 버튼 (SharedPreferences 연동)
        val prefs = requireContext().getSharedPreferences("checklist_status", Context.MODE_PRIVATE)
        val statusHtml = prefs.getString(file.absolutePath, "전체 0 대  면장X 0 대  확인 0 대")!!

        val statusBtn = Button(requireContext()).apply {
            text = fromHtmlCompat(statusHtml).toString()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.7f)
            gravity = android.view.Gravity.CENTER
            textSize = 12f
            isAllCaps = false
            isEnabled = false // 클릭 불가
        }

        val checkBtn = Button(requireContext()).apply {
            text = "차체크"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.3f)
            setOnClickListener {
                val intent = Intent(requireContext(), CheckListActivity::class.java)
                intent.putExtra("filePath", file.absolutePath)
                startActivity(intent)
            }
        }

        bottomRow.addView(statusBtn)
        bottomRow.addView(checkBtn)

        // 그룹에 추가
        container.addView(topRow)
        container.addView(bottomRow)
        card.addView(container)
        fileListLayout.addView(card)
    }

    private fun openInExternalExcel(file: File) {
        // 기존 외부 엑셀 앱 열기 로직 그대로
    }
}
