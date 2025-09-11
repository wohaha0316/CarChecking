package com.example.carchecking

import android.content.Context

object UiPrefs {
    enum class Scope { FILE, APP }

    private const val PREF = "ui_prefs"

    private fun base(scope: Scope, fileKey: String?): String =
        if (scope == Scope.FILE && !fileKey.isNullOrBlank()) "ui:file:$fileKey:" else "ui:app:"

    fun load(ctx: Context, fileKey: String?): UiConfig {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // 파일 스코프 우선 → 없으면 앱 스코프 → 없으면 기본값
        fun get(key: String, defF: Float, defB: Boolean? = null): Pair<Float, Boolean?> {
            val fileV = p.all[key]               // 파일스코프 값
            val appV  = p.all["ui:app:${key.substringAfterLast(':')}"] // 앱스코프 값
            return when (fileV) {
                is Float -> fileV to null
                is Boolean -> defF to fileV
                else -> when (appV) {
                    is Float -> appV to null
                    is Boolean -> defF to appV
                    else -> defF to defB
                }
            }
        }

        val cfg = UiConfig.defaults()

        // prefix들
        val filePrefix = if (!fileKey.isNullOrBlank()) "ui:file:$fileKey:" else null
        val appPrefix  = "ui:app:"

        fun f(key: String, def: Float): Float {
            return when {
                filePrefix != null && p.contains(filePrefix + key) -> p.getFloat(filePrefix + key, def)
                p.contains(appPrefix + key) -> p.getFloat(appPrefix + key, def)
                else -> def
            }
        }
        fun b(key: String, def: Boolean): Boolean {
            return when {
                filePrefix != null && p.contains(filePrefix + key) -> p.getBoolean(filePrefix + key, def)
                p.contains(appPrefix + key) -> p.getBoolean(appPrefix + key, def)
                else -> def
            }
        }

        return UiConfig(
            wNo = f("wNo", cfg.wNo),
            wBL = f("wBL", cfg.wBL),
            wHaju = f("wHaju", cfg.wHaju),
            wCar = f("wCar", cfg.wCar),
            wQty = f("wQty", cfg.wQty),
            wClear = f("wClear", cfg.wClear),
            wCheck = f("wCheck", cfg.wCheck),

            fNo = f("fNo", cfg.fNo),
            fBL = f("fBL", cfg.fBL),
            fHaju = f("fHaju", cfg.fHaju),
            fCar = f("fCar", cfg.fCar),
            fQty = f("fQty", cfg.fQty),
            fClear = f("fClear", cfg.fClear),
            fCheck = f("fCheck", cfg.fCheck),

            wrapBL = b("wrapBL", cfg.wrapBL),
            wrapHaju = b("wrapHaju", cfg.wrapHaju),

            rowSpacing = f("rowSpacing", cfg.rowSpacing),
            showRowDividers = b("showRowDividers", cfg.showRowDividers),

            vinBold = b("vinBold", cfg.vinBold)
        ).normalized()
    }

    fun save(ctx: Context, scope: Scope, fileKey: String?, cfg: UiConfig) {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val prefix = base(scope, fileKey)
        p.edit().apply {
            putFloat(prefix + "wNo", cfg.wNo)
            putFloat(prefix + "wBL", cfg.wBL)
            putFloat(prefix + "wHaju", cfg.wHaju)
            putFloat(prefix + "wCar", cfg.wCar)
            putFloat(prefix + "wQty", cfg.wQty)
            putFloat(prefix + "wClear", cfg.wClear)
            putFloat(prefix + "wCheck", cfg.wCheck)

            putFloat(prefix + "fNo", cfg.fNo)
            putFloat(prefix + "fBL", cfg.fBL)
            putFloat(prefix + "fHaju", cfg.fHaju)
            putFloat(prefix + "fCar", cfg.fCar)
            putFloat(prefix + "fQty", cfg.fQty)
            putFloat(prefix + "fClear", cfg.fClear)
            putFloat(prefix + "fCheck", cfg.fCheck)

            putBoolean(prefix + "wrapBL", cfg.wrapBL)
            putBoolean(prefix + "wrapHaju", cfg.wrapHaju)

            putFloat(prefix + "rowSpacing", cfg.rowSpacing)
            putBoolean(prefix + "showRowDividers", cfg.showRowDividers)

            putBoolean(prefix + "vinBold", cfg.vinBold) // ★ 추가
        }.apply()
    }
}
