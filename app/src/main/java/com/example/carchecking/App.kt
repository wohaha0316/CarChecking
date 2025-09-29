package com.example.carchecking

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val isDebug = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (isDebug) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("App", "Uncaught in ${t.name}", e)
        }
    }
}
