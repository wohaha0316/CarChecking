package com.example.carchecking

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.io.File
data class LogEntry(val ts: Long, val user: String, val content: String) {
    fun timeText(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
}

object LogBus {
    // ===== state =====
    private lateinit var appCtx: Context
    private val io = Executors.newSingleThreadExecutor()
    private val _feed = MutableLiveData<List<LogEntry>>(emptyList())
    val feed: LiveData<List<LogEntry>> = _feed

    private var userId: String = "me"
    private var deviceId: String = "unknown"

    // ===== prefs keys =====
    private const val PREF = "log_prefs"
    private const val KEY_DAYS = "days"              // Set<String> of "yyyyMMdd"
    // 실제 데이터는 키 "day_yyyyMMdd" 에 JSONL로 저장

    // ===== public =====
    fun init(ctx: Context, currentUserId: String? = null) {
        appCtx = ctx.applicationContext
        userId = currentUserId ?: "me"
        deviceId = runCatching {
            Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: "unknown"

        // 최근 N일 로드 + 보존기간 정리
        io.execute {
            val recent = loadAllInternal(lastNDaysKeys(7))
            _feed.postValue(recent)
            pruneOldDays(keepDays = 30) // ✅ 30일 이전 데이터 자동 삭제
        }
    }

    /** 개발용: 모든 로그 삭제 (UI의 "로그 초기화" 버튼에서 호출) */
    fun clearAll() {
        io.execute {
            val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val keys = sp.all.keys.toList()
            sp.edit().clear().apply()
            _feed.postValue(emptyList())
        }
    }

    // 일반 로그 기록
    fun logRaw(content: String, ts: Long = System.currentTimeMillis(), user: String = userId) {
        val entry = LogEntry(ts, user, content)

        // 메모리 반영 (최대 4000건 유지)
        val cur = _feed.value?.toMutableList() ?: mutableListOf()
        cur.add(entry)
        if (cur.size > 4000) cur.subList(0, cur.size - 4000).clear()
        _feed.postValue(cur)

        // 디스크 저장(JSONL)
        io.execute {
            val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val dayKey = dayKey(ts)              // "yyyyMMdd"
            val storeKey = "day_$dayKey"
            val line = JSONObject().apply {
                put("ts", ts)
                put("user", user)
                put("content", content)
                put("deviceId", deviceId)
                put("model", Build.MODEL ?: "")
                put("ver", appVersion())
            }.toString()

            val old = sp.getString(storeKey, "") ?: ""
            sp.edit()
                .putString(storeKey, if (old.isEmpty()) line else "$old\n$line")
                .putStringSet(KEY_DAYS, (sp.getStringSet(KEY_DAYS, emptySet()) ?: emptySet()) + dayKey)
                .apply()
        }
    }

    // ===== shorthand APIs (요구 포맷 반영) =====
    fun appOpen(screen: String)  = logRaw("$screen open")
    fun appClose(screen: String) = logRaw("$screen close")

    fun excelUpload(fileName: String) = logRaw("엑셀업로드($fileName)")

    // ✅ 'B/L', '+', '번째' 제거 → "<BL> n번 확인"
    fun checkConfirm(bl: String, nthCheck: Int) =
        logRaw("${bl.safeBL()} ${nthCheck}번 확인")

    fun checkConfirmCancel(bl: String, nthCheck: Int) =
        logRaw("${bl.safeBL()} ${nthCheck}번 확인 취소")

    // ✅ "<BL> n번 확인 m번 선적"
    fun shipAction(bl: String, nthCheck: Int, nthShip: Int) =
        logRaw("${bl.safeBL()} ${nthCheck}번 확인 ${nthShip}번 선적")

    fun shipActionCancel(bl: String, nthCheck: Int, nthShip: Int) =
        logRaw("${bl.safeBL()} ${nthCheck}번 확인 ${nthShip}번 선적 취소")

    // ===== loaders (LogActivity에서 사용) =====
    /** 전체/범위 로드 (디스크에서 다시 읽음) */
    fun loadAll(fromTs: Long? = null, toTs: Long? = null): List<LogEntry> {
        val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val days = sp.getStringSet(KEY_DAYS, emptySet())?.toList()?.sorted() ?: emptyList()
        val all = loadAllInternal(days)
        return all.filter { e ->
            (fromTs == null || e.ts >= fromTs) && (toTs == null || e.ts <= toTs)
        }
    }

    /** 로그에서 엑셀 파일명 수집 (드롭다운용, 중복 제거, 입력 순서 유지) */
    fun allExcelNamesFromLogs(): List<String> {
        val regex = Regex("""^엑셀업로드\((.+)\)$""")
        val names = LinkedHashSet<String>()
        for (e in _feed.value.orEmpty()) {
            val m = regex.find(e.content) ?: continue
            names.add(m.groupValues[1])
        }
        return names.toList()
    }

    // ===== internal =====
    private fun loadAllInternal(days: List<String>): List<LogEntry> {
        val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val list = mutableListOf<LogEntry>()
        for (d in days) {
            val s = sp.getString("day_$d", "") ?: ""
            if (s.isEmpty()) continue
            s.lineSequence().forEach { line ->
                runCatching {
                    val jo = JSONObject(line)
                    list.add(
                        LogEntry(
                            ts = jo.optLong("ts"),
                            user = jo.optString("user"),
                            content = jo.optString("content")
                        )
                    )
                }
            }
        }
        list.sortBy { it.ts }
        return list
    }

    private fun pruneOldDays(keepDays: Int) {
        val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val days = sp.getStringSet(KEY_DAYS, emptySet())?.toMutableSet() ?: return
        if (days.isEmpty()) return

        val sorted = days.toList().sorted()          // "yyyyMMdd" 오름차순
        val keep = sorted.takeLast(keepDays).toSet() // 최근 keepDays 유지
        val remove = sorted.toSet() - keep
        if (remove.isEmpty()) return

        val e = sp.edit()
        for (d in remove) e.remove("day_$d")
        e.putStringSet(KEY_DAYS, keep)
        e.apply()
    }

    private fun lastNDaysKeys(n: Int): List<String> {
        val tz = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(tz)
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = tz }
        val keys = ArrayList<String>(n)
        repeat(n) {
            keys.add(fmt.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return keys.sorted()
    }

    private fun dayKey(ts: Long): String {
        val tz = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(tz).apply { timeInMillis = ts }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d%02d%02d", y, m, d)
    }

    private fun appVersion(): String = try {
        val pm = appCtx.packageManager
        val pi = pm.getPackageInfo(appCtx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        "${pi.versionName ?: "0"}($code)"
    } catch (_: Exception) { "0(0)" }

    private fun cut(s: String, max: Int): String {
        if (s.length <= max) return s
        if (max <= 3) return s.take(max)
        return s.take(max - 3) + "..."
    }

    private fun String.safeBL(): String = trim()

    fun noteAdd(fileKey: String, rowIndex: Int, bl: String, text: String) {
        // println(...) 말고 ↓ 이렇게 통일
        logRaw("${bl.safeBL()} 특이사항: ${cut(text, 100)}")
    }

    fun noteDelete(fileKey: String, rowIndex: Int, bl: String) {
        logRaw("${bl.safeBL()} 특이사항 삭제")
    }

    fun vinScanned(vin: String) = logRaw("VIN 스캔: $vin")
    fun vinMatched(vin: String, bl: String) = logRaw("VIN 매칭: $vin -> $bl")
    fun vinMatchedCrossFile(vin: String, toFile: String, bl: String) =
        logRaw("VIN 교차매칭: $vin -> ${File(toFile).name} / $bl")
    fun vinUnmatched(vin: String) = logRaw("VIN 미매칭: $vin")
    fun vinScan(vin: String) = logRaw("[차대번호 $vin] 스캔")
}
