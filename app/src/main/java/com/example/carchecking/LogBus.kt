package com.example.carchecking

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONObject
import java.io.File
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

    fun init(ctx: Context, currentUserId: String? = null) {
        appCtx = ctx.applicationContext
        userId = currentUserId ?: "me"
        deviceId = try { Settings.Secure.getString(appCtx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown" } catch (_: Exception) { "unknown" }
        File(appCtx.filesDir, "logs").mkdirs()
        // 기존 파일(오늘/어제) 일부만 읽어 메모리 피드 구성 — 생략 가능
    }

    private fun dayFile(ts: Long = System.currentTimeMillis()) =
        File(appCtx.filesDir, "logs/events_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))}.jsonl")

    private fun append(entry: LogEntry) {
        // 메모리
        val cur = _feed.value?.toMutableList() ?: mutableListOf()
        cur.add(entry); if (cur.size > 2000) cur.subList(0, cur.size - 2000).clear()
        _feed.postValue(cur)
        // 파일
        io.execute {
            runCatching {
                dayFile(entry.ts).appendText(JSONObject().apply {
                    put("ts", entry.ts); put("user", entry.user); put("content", entry.content)
                    put("deviceId", deviceId); put("model", Build.MODEL ?: ""); put("ver", appVersion())
                }.toString() + "\n")
            }
        }
    }

    private fun appVersion(): String = try {
        val pi = appCtx.packageManager.getPackageInfo(appCtx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        "${pi.versionName ?: "0"}($code)"
    } catch (_: Exception) { "0(0)" }

    private fun cut20(s: String) = if (s.length <= 20) s else s.take(17) + "..."

    fun logRaw(content: String, ts: Long = System.currentTimeMillis(), user: String = userId) =
        append(LogEntry(ts, user, content))

    // ===== 요구 포맷 맞춤 헬퍼 =====
    fun appOpen(screen: String) = logRaw("$screen open")
    fun appClose(screen: String) = logRaw("$screen close")
    fun excelUpload(fileName: String) = logRaw("엑셀업로드(${cut20(fileName)})")
    fun checkConfirm(bl: String, nthCheck: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인")
    fun checkConfirmCancel(bl: String, nthCheck: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인 취소")
    fun shipAction(bl: String, nthCheck: Int, nthShip: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인 + ${nthShip}번째 선적")
    fun shipActionCancel(bl: String, nthCheck: Int, nthShip: Int) = logRaw("B/L $bl + ${nthCheck}번째 확인 + ${nthShip}번째 선적 취소")
}
