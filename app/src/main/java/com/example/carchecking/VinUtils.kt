package com.example.carchecking

object VinUtils {
    // 17자리 VIN 후보(금지문자 I/O/Q 제외는 나중 단계에서 검사)
    private val VIN_CORE = Regex("[A-HJ-NPR-Z0-9]{17}", RegexOption.IGNORE_CASE)

    // 현장 라벨들 확장 가능
    private val LABELS = listOf(
        "차대번호", "차대 번호", "차대", "VIN", "Vehicle Identification Number", "車台番号", "车架号"
    )
    private val LABEL_REGEX = Regex(
        "(" + LABELS.joinToString("|") { Regex.escape(it) } + ")\\s*[:：]\\s*([A-Za-z0-9\\-\\s]{10,40})",
        RegexOption.IGNORE_CASE
    )

    /** 엄격 정규화: 공백/대시 제거, 대문자, 길이 17, I/O/Q 포함 시 실패 */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.uppercase().replace("[\\s-]".toRegex(), "")
        if (s.length != 17) return null
        if (s.any { it == 'I' || it == 'O' || it == 'Q' }) return null
        return s
    }

    /** 관대한 정규화: OCR 혼동(I→1, O→0, Q→0) 보정 시도 + 체크디지트 통과한 것만 반환 */
    fun normalizeLenient(raw: String?): String? {
        val base = raw?.uppercase()?.replace("[\\s-]".toRegex(), "") ?: return null
        val tries = sequenceOf(
            base,
            base.replace('O', '0'),
            base.replace('I', '1'),
            base.replace('Q', '0'),
            base.replace('O', '0').replace('I', '1'),
            base.replace('O', '0').replace('Q', '0'),
            base.replace('I', '1').replace('Q', '0'),
            base.replace('O', '0').replace('I', '1').replace('Q', '0')
        ).distinct()

        for (t in tries) {
            val n = normalize(t) ?: continue
            if (isValidVin(n)) return n
        }
        return null
    }

    /** 라벨 오른쪽 우선 추출: "차대번호: XXXXX" 영역 먼저 검사 */
    fun extractPreferLabeledArea(text: String): List<String> {
        val out = mutableListOf<String>()
        LABEL_REGEX.findAll(text).forEach { m ->
            val right = m.groupValues[2]
            VIN_CORE.findAll(right).forEach { k ->
                val norm = normalizeLenient(k.value) ?: return@forEach
                out += norm
            }
        }
        return out.distinct()
    }

    /** 라벨 무관, 전체 텍스트에서 VIN 후보 스캔 */
    fun extractAll(text: String): List<String> {
        val out = mutableListOf<String>()
        VIN_CORE.findAll(text).forEach { m ->
            normalizeLenient(m.value)?.let { out += it }
        }
        return out.distinct()
    }

    /** 최종 후보 목록: 라벨우선 → 없으면 전체 스캔 */
    fun extractCandidates(text: String): List<String> {
        val prior = extractPreferLabeledArea(text)
        if (prior.isNotEmpty()) return prior
        return extractAll(text)
    }

    /** 체크디지트 포함 VIN 검증 (SAE J272, ISO 3779) */
    fun isValidVin(vin: String): Boolean {
        if (vin.length != 17) return false
        if (vin.any { it == 'I' || it == 'O' || it == 'Q' }) return false

        val translit = mapOf(
            'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
            'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5, 'P' to 7, 'R' to 9,
            'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9
        )
        fun valOf(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'Z' -> translit[c] ?: 0
            else -> 0
        }
        val weights = intArrayOf(8,7,6,5,4,3,2,10,0,9,8,7,6,5,4,3,2)

        var sum = 0
        for (i in 0 until 17) sum += valOf(vin[i]) * weights[i]
        val check = sum % 11
        val expected = if (check == 10) 'X' else ('0' + check)
        return vin[8] == expected
    }
}
