package com.example.carchecking

object SpecHeuristics {

    // "전장 ####" or 4자리/5자리 숫자 등에서 mm 추정. 없으면 0
    fun guessLengthMm(text: String): Int {
        val t = text.replace("\r\n", "\n")
        // mm로 기재된 4~5자리 숫자 우선 매칭
        val m1 = Regex("""(?<!\d)([45]\d{2,3})(?!\d)""").find(t)
        val raw = m1?.groupValues?.get(1)?.toIntOrNull() ?: return 0
        // 4000~6000 사이만 길이로 인정
        return if (raw in 3600..6500) raw else 0
    }

    fun guessWidthMm(text: String): Int {
        val t = text.replace("\r\n", "\n")
        val m1 = Regex("""(?<!\d)(1\d{3}|2\d{3})(?!\d)""").find(t) // 1000~2999
        val raw = m1?.groupValues?.get(1)?.toIntOrNull() ?: return 0
        return if (raw in 1500..2600) raw else 0
    }

    fun guessModel(text: String): String {
        // 첫 줄 기준 간단히 모델로 표기
        val first = text.replace("\r\n", "\n").lineSequence().firstOrNull()?.trim().orEmpty()
        return if (first.isBlank()) "-" else first
    }

    fun isElectric(text: String): Boolean {
        val s = text.lowercase()
        return listOf("ev", "electric", "전기", "ioniq", "ev6", "테슬라").any { s.contains(it) }
    }
}
