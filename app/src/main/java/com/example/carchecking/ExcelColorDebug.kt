/*package com.example.carchecking

import android.util.Log
import java.io.File

/**
 * 색상 디버깅용 유틸 (원하면 호출해서 Logcat 확인)
 */
object ExcelColorDebug {

    fun dumpFirstSheetColors(file: File, tag: String = "ExcelColorDebug") {
        runCatching {
            val data = CarCheckExcelParser.parse(file)
            data.forEachIndexed { r, row ->
                val colors = row.joinToString(", ") { it.bgHex ?: "-" }
                Log.d(tag, "row $r colors: $colors")
            }
        }.onFailure { e ->
            Log.e(tag, "dump error: ${e.message}", e)
        }
    }
}
*/

package com.example.carchecking

import android.util.Log
import java.io.File

/**
 * 색상 디버깅용 유틸
 * 현재는 컴파일 방해만 막기 위해 임시 비활성화.
 */
object ExcelColorDebug {

    fun dumpFirstSheetColors(file: File, tag: String = "ExcelColorDebug") {
        Log.d(tag, "dumpFirstSheetColors disabled temporarily: ${file.name}")
    }
}