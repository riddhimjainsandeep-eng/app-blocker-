package com.example.appblocker

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class StatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stats = getSharedPreferences("BlockerStats", Context.MODE_PRIVATE)
        val prefs = getSharedPreferences("BlockerPrefs", Context.MODE_PRIVATE)

        val thisWeek   = stats.getInt("weekly_blocks", 0)
        val lastWeek   = stats.getInt("last_week_total", 0)
        val totalEver  = stats.getInt("total_blocks", 0)
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

        // Blocks in last 24 h
        val timestamps = (stats.getString("block_timestamps", "") ?: "")
            .split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
        val oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val todayBlocks = timestamps.count { it > oneDayAgo }

        // ── Root scroll layout ────────────────────────────────────────────
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF0D0D1A.toInt()) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 48)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(root)
        setContentView(scroll)

        // ── Title ─────────────────────────────────────────────────────────
        root.addView(textView("📊 Focus Stats", 26f, 0xFFC8A2F8.toInt(), bold = true, bottomPad = 4))
        root.addView(textView(
            SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()),
            12f, 0xFF6666AA.toInt(), bottomPad = 32
        ))

        // ── Ring chart: today's blocks ────────────────────────────────────
        val ringChart = RingChartView(this, todayBlocks, thisWeek, totalEver)
        val chartParams = LinearLayout.LayoutParams(560, 560).apply { bottomMargin = 32 }
        root.addView(ringChart, chartParams)

        // ── Stats cards ───────────────────────────────────────────────────
        root.addView(card(listOf(
            "Today's block attempts"    to "$todayBlocks",
            "This week"                 to "$thisWeek",
            "Last week"                 to "$lastWeek",
            "All-time total"            to "$totalEver",
            "Week-over-week trend"      to if (thisWeek < lastWeek) "📉 Improved" else if (thisWeek > lastWeek) "📈 More attempts" else "➡️ Stable"
        )))
        root.addView(spacer(16))

        // ── Per-app breakdown ─────────────────────────────────────────────
        root.addView(textView("APP BREAKDOWN", 10f, 0xFF6666AA.toInt(), bold = false, bottomPad = 8,
            letterSpacing = 0.2f))
        val pm = packageManager
        if (blockedApps.isEmpty()) {
            root.addView(textView("No apps in blocklist yet.", 13f, 0xFF555577.toInt(), bottomPad = 0))
        } else {
            blockedApps.forEach { pkg ->
                val key   = "app_count_${pkg.replace('.', '_')}"
                val count = stats.getInt(key, 0)
                val name  = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                val pct   = if (totalEver > 0) (count * 100 / totalEver) else 0
                root.addView(appRow(name, count, pct))
            }
        }

        // ── Back button ───────────────────────────────────────────────────
        root.addView(spacer(32))
        val back = Button(this).apply {
            text = "← Back"
            textSize = 14f
            setTextColor(0xFF0D0D1A.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFC8A2F8.toInt())
            stateListAnimator = null
            setOnClickListener { finish() }
        }
        root.addView(back, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 140))
    }

    // ── Per-app row ───────────────────────────────────────────────────────
    private fun appRow(name: String, count: Int, pct: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 14, 20, 14)
            setBackgroundColor(0xFF12122A.toInt())
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 6
            layoutParams = params
        }
        val nameV = textView(name, 13f, 0xFFCCCCDD.toInt(), bottomPad = 0)
        nameV.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val countV = textView("$count blocks  ($pct%)", 13f, 0xFFC8A2F8.toInt(), bold = true, bottomPad = 0)
        row.addView(nameV)
        row.addView(countV)
        return row
    }

    // ── Stats card ────────────────────────────────────────────────────────
    private fun card(pairs: List<Pair<String, String>>): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF12122A.toInt())
            setPadding(24, 20, 24, 20)
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = 16
            layoutParams = p
        }
        pairs.forEach { (label, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.bottomMargin = 10
                layoutParams = p
            }
            val lbl = textView(label, 13f, 0xFF8888AA.toInt(), bottomPad = 0)
            lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(lbl)
            row.addView(textView(value, 13f, 0xFFFFFFFF.toInt(), bold = true, bottomPad = 0))
            card.addView(row)
        }
        return card
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean = false,
                         bottomPad: Int = 0, letterSpacing: Float = 0f) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (letterSpacing > 0) this.letterSpacing = letterSpacing
        gravity = android.view.Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, bottomPad)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun spacer(dp: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, (dp * resources.displayMetrics.density).toInt())
    }
}

// ── Custom ring chart view ────────────────────────────────────────────────────
class RingChartView(
    context: Context,
    private val today: Int,
    private val week: Int,
    private val total: Int
) : android.view.View(context) {

    private val bgPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 38f; color = 0xFF1A1A2E.toInt() }
    private val todayPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 38f; strokeCap = Paint.Cap.ROUND; color = 0xFFC8A2F8.toInt() }
    private val weekPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 38f; strokeCap = Paint.Cap.ROUND; color = 0xFFA2E8F8.toInt() }
    private val textPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt() }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFF8888AA.toInt() }
    private val labelPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFF6666AA.toInt() }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val outerR = minOf(cx, cy) * 0.82f
        val innerR = outerR - 52f

        val outerRect = RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        val innerRect = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)

        // Background rings
        canvas.drawArc(outerRect, 0f, 360f, false, bgPaint)
        canvas.drawArc(innerRect, 0f, 360f, false, bgPaint)

        // Today arc (outer ring)
        val todaySweep = if (total > 0) (today.toFloat() / total * 360f).coerceAtLeast(if (today > 0) 8f else 0f) else 0f
        canvas.drawArc(outerRect, -90f, todaySweep, false, todayPaint)

        // Week arc (inner ring)
        val weekMax   = maxOf(week, 1)
        val weekSweep = if (total > 0) (week.toFloat() / total * 360f).coerceAtLeast(if (week > 0) 8f else 0f) else 0f
        canvas.drawArc(innerRect, -90f, weekSweep, false, weekPaint)

        // Centre text
        textPaint.textSize = 52f
        canvas.drawText("$total", cx, cy - 10f, textPaint)
        subTextPaint.textSize = 26f
        canvas.drawText("all-time blocks", cx, cy + 28f, subTextPaint)

        // Legend
        val ly = cy + outerR + 48f
        labelPaint.textSize = 24f
        labelPaint.color = 0xFFC8A2F8.toInt()
        canvas.drawText("● Today: $today", cx - 120f, ly, labelPaint)
        labelPaint.color = 0xFFA2E8F8.toInt()
        canvas.drawText("● This week: $week", cx + 120f, ly, labelPaint)
    }
}
