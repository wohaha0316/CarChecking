package com.example.carchecking

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val title = TextView(this).apply {
            text = "로그"; textSize = 18f; setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8))
        }
        val rv = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@LogActivity); adapter = LogAdapter() }
        root.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(rv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        val ad = rv.adapter as LogAdapter
        LogBus.feed.observe(this, Observer { list -> ad.submit(list); rv.scrollToPosition(ad.itemCount - 1) })
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

private class LogAdapter : RecyclerView.Adapter<LogVH>() {
    private val data = mutableListOf<LogEntry>()
    fun submit(list: List<LogEntry>) { data.clear(); data.addAll(list); notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogVH {
        val row = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(parent,8), dp(parent,6), dp(parent,8), dp(parent,6)) }
        val t1 = TextView(parent.context).apply { textSize = 12f }
        val t2 = TextView(parent.context).apply { textSize = 14f; setTypeface(typeface, Typeface.BOLD) }
        row.addView(t1); row.addView(t2); return LogVH(row, t1, t2)
    }
    override fun onBindViewHolder(h: LogVH, p: Int) { val e = data[p]; h.t1.text = "${e.timeText()}  •  ${e.user}"; h.t2.text = e.content }
    override fun getItemCount() = data.size
    private fun dp(parent: ViewGroup, v: Int) = (v * parent.resources.displayMetrics.density).toInt()
}
private class LogVH(root: LinearLayout, val t1: TextView, val t2: TextView) : RecyclerView.ViewHolder(root)
