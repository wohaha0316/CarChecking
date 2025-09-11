package com.example.carchecking

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Job

open class BaseActivity : AppCompatActivity() {
    protected val pendingJobs = mutableListOf<Job>()
    protected val pendingDialogs = mutableListOf<Dialog>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(localClassName, "onCreate")
    }

    override fun onStop() {
        super.onStop()
        pendingDialogs.forEach { if (it.isShowing) it.dismiss() }
        pendingDialogs.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        pendingJobs.forEach { it.cancel() }
        pendingJobs.clear()
    }
}
