package com.example.carchecking

/** 앱이 켜져있는 동안 ‘열었던 파일들’의 VIN ↔ (fileKey, rowIndex) 맵을 유지 */
object VinIndexManager {
    data class Hit(val fileKey: String, val filePath: String, val rowIndex: Int, val bl: String)

    // fileKey -> 메타
    private data class FileMeta(val path: String, val rows: List<CheckRow>)
    private val files = LinkedHashMap<String, FileMeta>() // 삽입 순서 유지(최근 열림 마지막)

    // vin -> List<hit> (여러 파일에 있을 수 있음)
    private val vinMap = HashMap<String, MutableList<Hit>>()

    /** 파일 로드 직후 호출: 현재 rows에서 VIN 추출하여 인덱싱 */
    fun indexFile(fileKey: String, filePath: String, rows: List<CheckRow>) {
        files[fileKey] = FileMeta(filePath, rows)
        // rows에서 VIN 뽑아 인덱스 생성
        rows.forEachIndexed { idx, r ->
            if (r.isLabelRow) return@forEachIndexed
            val txt = r.carInfo.replace("\r\n", "\n")
            VinUtils.extractCandidates(txt).forEach { vin ->
                val hit = Hit(fileKey, filePath, idx, r.bl)
                val list = vinMap.getOrPut(vin) { mutableListOf() }
                // 동일 (fileKey, rowIndex) 중복 방지
                if (list.none { it.fileKey == fileKey && it.rowIndex == idx }) {
                    list.add(hit)
                }
            }
        }
    }

    /** 현재 파일에서만 매칭 (빠름) */
    fun findInCurrent(currentKey: String, vin: String): Hit? {
        val list = vinMap[vin] ?: return null
        return list.firstOrNull { it.fileKey == currentKey }
    }

    /** 현재 외의 최근 파일에서 매칭 (가장 최근 등록 순으로) */
    fun findInOthers(currentKey: String, vin: String): List<Hit> {
        val list = vinMap[vin] ?: return emptyList()
        return list.filter { it.fileKey != currentKey }.sortedBy { hit ->
            // ‘최근’ 판단: files 삽입 순서 기준(후입 우선)
            files.keys.indexOf(hit.fileKey)
        }.reversed()
    }
}
