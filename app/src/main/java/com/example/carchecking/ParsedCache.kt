package com.example.carchecking

import java.io.File

object ParsedCache {
    // v3: 잘못된 맵핑이 남은 캐시 무시
    private const val DIR = "parsed_cache_v3"

    private const val TAB_ESC = "␉"
    private const val NL_ESC = "␤"

    data class Key(val name: String, val len: Long, val lastMod: Long) {
        fun id(): String = "${name}_${len}_${lastMod}".hashCode().toString()
    }

    fun keyFor(file: File): Key = Key(file.name, file.length(), file.lastModified())

    private fun cacheFile(baseDir: File, key: Key): File {
        val dir = File(baseDir, DIR).apply { if (!exists()) mkdirs() }
        return File(dir, key.id() + ".tsv")
    }

    private fun esc(s: String): String =
        s.replace("\n", NL_ESC).replace("\t", TAB_ESC)

    private fun unesc(s: String): String =
        s.replace(NL_ESC, "\n").replace(TAB_ESC, "\t")

    fun write(baseDir: File, key: Key, rows: List<CheckRow>) {
        val f = cacheFile(baseDir, key)
        f.bufferedWriter().use { w ->
            rows.forEach { r ->
                w.appendLine(
                    listOf(
                        esc(r.bl),
                        esc(r.haju),
                        esc(r.carInfo),
                        esc(r.qty),
                        esc(r.clearance),
                        r.isLabelRow.toString(),
                        r.isChecked.toString(),
                        r.checkOrder.toString()
                    ).joinToString("\t")
                )
            }
        }
    }

    fun read(baseDir: File, key: Key): List<CheckRow>? {
        val f = cacheFile(baseDir, key)
        if (!f.exists()) return null
        val out = mutableListOf<CheckRow>()
        f.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split('\t', limit = 8)
                if (parts.size < 8) return@forEach
                val bl = unesc(parts[0])
                val haju = unesc(parts[1])
                val carInfo = unesc(parts[2])
                val qty = unesc(parts[3])
                val clearance = unesc(parts[4])
                val isLabel = parts[5].toBooleanStrictOrNull() ?: false
                val checked = parts[6].toBooleanStrictOrNull() ?: false
                val order = parts[7].toIntOrNull() ?: 0
                out += CheckRow(bl, haju, carInfo, qty, clearance, checked, order, isLabel)
            }
        }
        return out
    }
}
