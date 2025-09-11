package com.example.carchecking

import android.os.Build
import android.text.Html
import android.text.Spanned

/** HTML 문자열을 Spanned 로 안전 변환 (색상 강조 유지) */
fun fromHtmlCompat(src: String): Spanned =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        Html.fromHtml(src, Html.FROM_HTML_MODE_LEGACY)
    else
        Html.fromHtml(src)
