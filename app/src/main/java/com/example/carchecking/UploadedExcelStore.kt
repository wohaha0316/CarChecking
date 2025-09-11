package com.example.carchecking

import java.io.File

object UploadedExcelStore {
    val files = mutableListOf<File>()                    // 업로드된 파일들
    val customNames = mutableMapOf<String, String>()     // key: file.absolutePath, value: 수정된 표시 이름
}
