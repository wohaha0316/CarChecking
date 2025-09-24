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

data class LogEntry(val ts: Long, val user: String, val content: String) {
    fun timeText(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
}

object LogBus {
    private lateinit var appCtx: Context
    private val io = Executors.newSingleThreadExecutor()
    private val _feed = MutableLiveData<List<LogEntry>>(emptyList())
    val feed: LiveData<List<LogEntry>> = _feed

    private var userId: String = "me"
    private var deviceId: String = "unknown"
    private const val PREF = "log_prefs"
    private const val KEY_DAYS = "days"

    fun init(ctx: Context, currentUserId: String? = null) {
        appCtx = ctx.applicationContext
        userId = currentUserId ?: "me"
        deviceId = try {
            Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (_: Exception) { "unknown" }
        // 시작 시 최근 7일 로드
        io.execute {
            val all = loadAllInternal(lastNDaysKeys(7))
            _feed.postValue(all)
        }
        pruneOldDays(keepDays = 30)   // ✅ 30일 이전 로그 자동 삭제
    }

    // ===== append & persist =====
    fun logRaw(content: String, ts: Long = System.currentTimeMillis(), user: String = userId) {
        val entry = LogEntry(ts, user, content)

        // 메모리 최신화
        val cur = _feed.value?.toMutableList() ?: mutableListOf()
        cur.add(entry)
        if (cur.size > 4000) cur.subList(0, cur.size - 4000).clear()
        _feed.postValue(cur)

        // SharedPreferences 저장(JSONL)
        io.execute {
            val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val dayKey = dayKey(ts)
            val key = "day_$dayKey"
            val line = JSONObject().apply {
                put("ts", ts); put("user", user); put("content", content)
                put("deviceId", deviceId); put("model", Build.MODEL ?: ""); put("ver", appVersion())
            }.toString()

            val old = sp.getString(key, "") ?: ""
            sp.edit()
                .putString(key, if (old.isEmpty()) line else "$old\n$line")
                .putStringSet(KEY_DAYS, (sp.getStringSet(KEY_DAYS, emptySet()) ?: emptySet()) + dayKey)
                .apply()
        }
    }

    // ===== helpers you call =====
    fun appOpen(screen: String) = logRaw("$screen open")
    fun appClose(screen: String) = logRaw("$screen close")
    fun excelUpload(fileName: String) = logRaw("엑셀업로드(${cut(fileName, 20)})")
    fun checkConfirm(bl: String, nthCheck: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인")
    fun checkConfirmCancel(bl: String, nthCheck: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인 취소")
    fun shipAction(bl: String, nthCheck: Int, nthShip: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인 + ${nthShip}번째 선적")
    fun shipActionCancel(bl: String, nthCheck: Int, nthShip: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인 + ${nthShip}번째 선적 취소")

    // ===== loader for LogActivity filters =====
    fun loadAll(fromTs: Long? = null, toTs: Long? = null): List<LogEntry> {
        val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val days = sp.getStringSet(KEY_DAYS, emptySet())?.toList()?.sorted() ?: emptyList()
        val all = loadAllInternal(days)
        return all.filter { e ->
            (fromTs == null || e.ts >= fromTs) && (toTs == null || e.ts <= toTs)
        }
    }

    fun allExcelNamesFromLogs(): List<String> {
        // "엑셀업로드(name)" 패턴에서 name 추출
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
                    list.add(LogEntry(jo.optLong("ts"), jo.optString("user"), jo.optString("content")))
                }
            }
        }
        list.sortBy { it.ts }
        return list
    }

    private fun lastNDaysKeys(n: Int): List<String> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US); fmt.timeZone = TimeZone.getTimeZone("UTC")
        val keys = ArrayList<String>(n)
        repeat(n) {
            keys.add(fmt.format(cal.time)); cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return keys.sorted()
    }

    private fun dayKey(ts: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)+1; val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d%02d%02d", y,m,d)
    }

    private fun appVersion(): String = try {
        val pi = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        "${pi.versionName ?: "0"}($code)"
    } catch (_: Exception) { "0(0)" }

    private fun cut(s: String, max: Int): String {
        if (s.length <= max) return s
        if (max <= 3) return s.take(max)
        return s.take(max - 3) + "..."
    }
    private fun pruneOldDays(keepDays: Int) {
        val sp = appCtx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val days = sp.getStringSet(KEY_DAYS, emptySet())?.toMutableSet() ?: return
        if (days.isEmpty()) return

        // 최근 keepDays만 남기기
        val sorted = days.toList().sorted()                   // "yyyyMMdd" 정렬 = 시간순
        val keep = sorted.takeLast(keepDays).toSet()
        val remove = sorted.toSet() - keep

        if (remove.isEmpty()) return

        val e = sp.edit()
        for (d in remove) e.remove("day_$d")
        e.putStringSet(KEY_DAYS, keep)
        e.apply()
    }
}
