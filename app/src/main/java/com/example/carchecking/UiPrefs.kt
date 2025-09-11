package com.example.carchecking

import android.content.Context

object UiPrefs {
    private const val PREF = "ui_prefs"
    enum class Scope { APP, FILE }

    fun load(ctx: Context, fileKey: String?): UiConfig {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        fun gF(k: String, d: Float) = p.getFloat(k, d)
        fun gB(k: String, d: Boolean) = p.getBoolean(k, d)
        fun key(n: String) = if (fileKey != null) "$n:$fileKey" else n

        return UiConfig(
            wNo = gF(key("wNo"), 0.08f),
            wBL = gF(key("wBL"), 0.18f),
            wHaju = gF(key("wHaju"), 0.18f),
            wCar = gF(key("wCar"), 0.36f),
            wQty = gF(key("wQty"), 0.10f),
            wClear = gF(key("wClear"), 0.10f),
            wCheck = gF(key("wCheck"), 0.10f),

            fNo = gF(key("fNo"), 11f),
            fBL = gF(key("fBL"), 13f),
            fHaju = gF(key("fHaju"), 13f),
            fCar = gF(key("fCar"), 13f),
            fQty = gF(key("fQty"), 12f),
            fClear = gF(key("fClear"), 12f),
            fCheck = gF(key("fCheck"), 12f),

            wrapBL = gB(key("wrapBL"), false),
            wrapHaju = gB(key("wrapHaju"), false),
            vinBold = gB(key("vinBold"), true),

            rowSpacing = gF(key("rowSpacing"), 0.35f),
            showRowDividers = gB(key("showRowDividers"), false)
        ).clamped()
    }

    fun save(ctx: Context, scope: Scope, fileKey: String?, cfg: UiConfig) {
        val e = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        fun k(n: String) = if (scope == Scope.FILE && fileKey != null) "$n:$fileKey" else n
        val c = cfg.clamped()

        e.putFloat(k("wNo"), c.wNo)
        e.putFloat(k("wBL"), c.wBL)
        e.putFloat(k("wHaju"), c.wHaju)
        e.putFloat(k("wCar"), c.wCar)
        e.putFloat(k("wQty"), c.wQty)
        e.putFloat(k("wClear"), c.wClear)
        e.putFloat(k("wCheck"), c.wCheck)

        e.putFloat(k("fNo"), c.fNo)
        e.putFloat(k("fBL"), c.fBL)
        e.putFloat(k("fHaju"), c.fHaju)
        e.putFloat(k("fCar"), c.fCar)
        e.putFloat(k("fQty"), c.fQty)
        e.putFloat(k("fClear"), c.fClear)
        e.putFloat(k("fCheck"), c.fCheck)

        e.putBoolean(k("wrapBL"), c.wrapBL)
        e.putBoolean(k("wrapHaju"), c.wrapHaju)
        e.putBoolean(k("vinBold"), c.vinBold)

        e.putFloat(k("rowSpacing"), c.rowSpacing)
        e.putBoolean(k("showRowDividers"), c.showRowDividers)

        e.apply()
    }
}
