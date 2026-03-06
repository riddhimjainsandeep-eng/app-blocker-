package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        val isWhitelist = prefs.getBoolean("is_whitelist_mode", false)

        val timestamps = (stats.getString("block_timestamps", "") ?: "")
            .split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }
        val oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        val todayBlocks = timestamps.count { it > oneDayAgo }

        // Focus Score calculation (inverse of blocks vs target)
        val targetBlocks = 5 // subjective "at most" per day
        val focusScore = if (todayBlocks <= targetBlocks) 100 - (todayBlocks * 10) else maxOf(0, 50 - (todayBlocks * 2))

        // ── Root Scroll ───────────────────────────────────────────────────
        val scroll = ScrollView(this).apply { 
            setBackgroundColor(0xFF0D0D1A.toInt()) 
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 64, 40, 64)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(root)
        setContentView(scroll)

        // ── Header & Profile Button ──────────────────────────────────────
        val header = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, 48)
        }
        
        val welcomeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            layoutParams = lp
        }

        val userName = getSharedPreferences(SetupActivity.PREFS_SETUP, Context.MODE_PRIVATE)
            .getString(SetupActivity.KEY_USER_NAME, "Scholar") ?: "Scholar"
        
        welcomeLayout.addView(textView("YOUR FOCUS CENTER", 10f, 0xFFA2E8F8.toInt(), letterSpacing = 0.4f, bottomPad = 4))
        welcomeLayout.addView(textView("Hi, $userName!", 28f, 0xFFFFFFFF.toInt(), bold = true))
        
        val profileBtn = FrameLayout(this).apply {
            val lp = RelativeLayout.LayoutParams(120, 120)
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            lp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = lp
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFF1A1A2E.toInt())
                setStroke(2, 0xFFC8A2F8.toInt())
            }
            setOnClickListener { showProfileDialog() }
            
            val initial = if (userName.isNotEmpty()) userName.take(1).uppercase() else "S"
            addView(TextView(context).apply {
                text = initial
                setTextColor(0xFFC8A2F8.toInt())
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
        }
        
        header.addView(welcomeLayout)
        header.addView(profileBtn)
        root.addView(header)

        // ── Glowing Focus Dial ────────────────────────────────────────────
        val ringChart = PremiumRingView(this, focusScore, todayBlocks)
        root.addView(ringChart, LinearLayout.LayoutParams(650, 650).apply { bottomMargin = 48 })

        // ── Check for Updates (Phase 8) ───────────────────────────────────
        val updateBtn = Button(this).apply {
            text = "🚀 Check for Updates"
            textSize = 12f
            setTextColor(0xFFA2E8F8.toInt())
            background = createCardDrawable(0x15A2E8F8.toInt()).apply { setStroke(1, 0x33A2E8F8.toInt()) }
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                val intent = Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/riddhimjainsandeep-eng/app-blocker-"))
                startActivity(intent)
            }
        }
        root.addView(updateBtn, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = 48 })

        // ── Whitelist Toggle (Special Card) ───────────────────────────────
        root.addView(whitelistCard(isWhitelist) { enabled ->
            prefs.edit().putBoolean("is_whitelist_mode", enabled).apply()
            Toast.makeText(this, if (enabled) "Whitelist ON: Only study apps allowed" else "Whitelist OFF: Back to standard blocking", Toast.LENGTH_SHORT).show()
        })
        root.addView(spacer(24))

        // ── Productivity Ratio (Feature 4) ────────────────────────────────
        val productiveMs    = stats.getLong("time_cat_productive", 0L)
        val distractionMs   = stats.getLong("time_cat_distraction", 0L)
        val neutralMs       = stats.getLong("time_cat_neutral", 0L)
        val totalMs         = productiveMs + distractionMs + neutralMs

        val productivityRatio = if (totalMs > 0) ((productiveMs.toFloat() / totalMs) * 100).toInt() else 0
        val distractionRatio  = if (totalMs > 0) ((distractionMs.toFloat() / totalMs) * 100).toInt() else 0

        fun msToLabel(ms: Long): String {
            val mins = ms / 60000
            return if (mins < 60) "${mins}m" else "${mins / 60}h ${mins % 60}m"
        }

        root.addView(textView("PRODUCTIVITY BREAKDOWN", 10f, 0xFF6666AA.toInt(), letterSpacing = 0.2f, bottomPad = 12))

        val productivityCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = createCardDrawable(0xFF0F1F0F.toInt()).apply { setStroke(1, 0xFF2A4A2A.toInt()) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 16 }
        }

        // Productivity ratio headline
        productivityCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(textView("🎯 Productivity Ratio", 14f, 0xFFAADDAA.toInt(), bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                gravity = android.view.Gravity.START
            })
            addView(textView("$productivityRatio%", 20f, 0xFF88FF88.toInt(), bold = true))
        })
        productivityCard.addView(spacer(12))

        // Color-coded progress bar
        val barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, 20)
        }
        val productivePct  = if (totalMs > 0) productiveMs.toFloat() / totalMs else 0f
        val distractPct    = if (totalMs > 0) distractionMs.toFloat() / totalMs else 0f
        val neutralPct     = 1f - productivePct - distractPct
        barRow.addView(android.view.View(this).apply {
            setBackgroundColor(0xFF44AA44.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -1, productivePct)
        })
        barRow.addView(android.view.View(this).apply {
            setBackgroundColor(0xFFAA4444.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -1, distractPct)
        })
        barRow.addView(android.view.View(this).apply {
            setBackgroundColor(0xFF444466.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -1, neutralPct)
        })
        productivityCard.addView(barRow)
        productivityCard.addView(spacer(12))

        // Category sub-row
        val catRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun catLabel(emoji: String, label: String, value: String, color: Int): TextView =
            textView("$emoji $label\n$value", 10f, color, wrap = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        catRow.addView(catLabel("✅", "Productive", msToLabel(productiveMs), 0xFF88CC88.toInt()))
        catRow.addView(catLabel("📵", "Distraction", msToLabel(distractionMs), 0xFFCC8888.toInt()))
        catRow.addView(catLabel("⬜", "Neutral", msToLabel(neutralMs), 0xFF6666AA.toInt()))
        productivityCard.addView(catRow)

        if (totalMs == 0L) {
            productivityCard.addView(textView("No usage data yet — keep studying! 📚", 12f, 0xFF444466.toInt(), bottomPad = 4))
        }
        root.addView(productivityCard)

        // ── Stats Cards Grid Row ──────────────────────────────────────────
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(smallCard("THIS WEEK", "$thisWeek", 0.5f))
        row.addView(spacer(16, horizontal = true))
        row.addView(smallCard("TREND", if (thisWeek < lastWeek) "📉 UP" else "📈 DOWN", 0.5f))
        root.addView(row)
        root.addView(spacer(16))

        // ── Top Distractions ──────────────────────────────────────────────
        root.addView(textView("TOP DISTRACTIONS", 10f, 0xFF6666AA.toInt(), letterSpacing = 0.2f, bottomPad = 12))
        val pm = packageManager
        val sortedApps = blockedApps.map { pkg ->
            val key = "app_count_${pkg.replace('.', '_')}"
            pkg to stats.getInt(key, 0)
        }.sortedByDescending { it.second }.take(4)

        if (sortedApps.isEmpty()) {
            root.addView(textView("No data recorded yet.", 13f, 0xFF555577.toInt()))
        } else {
            sortedApps.forEach { (pkg, count) ->
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                root.addView(distractionRow(name, count, totalEver))
            }
        }

        // ── Footer back button ────────────────────────────────────────────
        root.addView(spacer(48))
        val back = textView("DISMISS", 12f, 0xFF6666AA.toInt(), letterSpacing = 0.2f).apply {
            setOnClickListener { finish() }
            setPadding(40, 40, 40, 40)
        }
        root.addView(back)
        root.addView(textView("Version 1.4-Focus", 9f, 0xFF333355.toInt()))
    }

    private fun showProfileDialog() {
        val prefs = getSharedPreferences(SetupActivity.PREFS_SETUP, Context.MODE_PRIVATE)
        val name = prefs.getString(SetupActivity.KEY_USER_NAME, "") ?: ""
        val age = prefs.getString(SetupActivity.KEY_USER_AGE, "") ?: ""
        val email = prefs.getString(SetupActivity.KEY_USER_EMAIL, "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF0D0D1A.toInt())
        }

        val nameIn = EditText(this).apply { setText(name); hint = "Name"; setTextColor(Color.WHITE) }
        val ageIn = EditText(this).apply { setText(age); hint = "Age"; setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_NUMBER }
        val emailIn = EditText(this).apply { setText(email); hint = "Email"; setTextColor(Color.WHITE) }

        layout.addView(textView("EDIT PROFILE", 10f, 0xFFC8A2F8.toInt(), letterSpacing = 0.2f, bottomPad = 16))
        layout.addView(nameIn)
        layout.addView(ageIn)
        layout.addView(emailIn)

        AlertDialog.Builder(this, R.style.BlockerDialog)
            .setTitle("Focus Profile")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putString(SetupActivity.KEY_USER_NAME, nameIn.text.toString())
                    .putString(SetupActivity.KEY_USER_AGE, ageIn.text.toString())
                    .putString(SetupActivity.KEY_USER_EMAIL, emailIn.text.toString())
                    .apply()
                recreate() // Refresh greeting
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // ── UI HELPERS ────────────────────────────────────────────────────────

    private fun whitelistCard(active: Boolean, onToggle: (Boolean) -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 32, 32, 32)
            background = createCardDrawable(0xFF15152A.toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textLayout.addView(textView("Strict Whitelist Mode", 15f, 0xFFFFFFFF.toInt(), bold = true, bottomPad = 4, wrap = true).apply { gravity = Gravity.START })
        textLayout.addView(textView("Block everything except study essentials", 11f, 0xFFA2E8F8.toInt(), wrap = true).apply { gravity = Gravity.START })
        
        val sw = Switch(this).apply {
            isChecked = active
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        
        card.addView(textLayout)
        card.addView(sw)
        return card
    }

    private fun smallCard(title: String, value: String, weight: Float): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            background = createCardDrawable(0xFF15152A.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            addView(textView(title, 10f, 0xFF6666AA.toInt(), letterSpacing = 0.1f, bottomPad = 8))
            addView(textView(value, 20f, 0xFFFFFFFF.toInt(), bold = true))
        }
    }

    private fun distractionRow(name: String, count: Int, total: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val textRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        textRow.addView(textView(name, 13f, 0xFFCCCCDD.toInt()).apply { 
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f); gravity = Gravity.START 
        })
        textRow.addView(textView("$count attempts", 13f, 0xFFC8A2F8.toInt(), bold = true).apply { 
            layoutParams = LinearLayout.LayoutParams(-2, -2); gravity = Gravity.END 
        })
        
        val progressBg = View(this).apply {
            setBackgroundColor(0xFF12122A.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8)
        }
        val pct = if (total > 0) (count.toFloat() / total).coerceAtMost(1f) else 0f
        val progressFg = View(this).apply {
            setBackgroundColor(0xFFC8A2F8.toInt())
            layoutParams = LinearLayout.LayoutParams((100 * pct).toInt(), 8).apply { topMargin = -8 }
        }

        row.addView(textRow)
        row.addView(spacer(8))
        row.addView(progressBg)
        val fgBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(-1, 8).apply { topMargin = -8 } }
        fgBar.addView(View(this).apply { 
            background = createGradientDrawable(intArrayOf(0xFFC8A2F8.toInt(), 0xFFA2E8F8.toInt()), 4f)
            layoutParams = LinearLayout.LayoutParams(0, 8, pct) 
        })
        fgBar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 8, 1-pct) })
        row.addView(fgBar)

        return row
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean = false, 
                         bottomPad: Int = 0, letterSpacing: Float = 0f, wrap: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (letterSpacing > 0) this.letterSpacing = letterSpacing
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, 0, 0, bottomPad)
        if (!wrap) isSingleLine = true
    }

    private fun spacer(dp: Int, horizontal: Boolean = false) = View(this).apply {
        val size = (dp * resources.displayMetrics.density).toInt()
        layoutParams = if (horizontal) LinearLayout.LayoutParams(size, 1) else LinearLayout.LayoutParams(1, size)
    }

    private fun createCardDrawable(bgColor: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(bgColor)
        cornerRadius = 32f
        setStroke(1, 0xFF2A2A4A.toInt())
    }

    private fun createGradientDrawable(colors: IntArray, radiusDp: Float) = android.graphics.drawable.GradientDrawable().apply {
        this.colors = colors
        orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
        cornerRadius = radiusDp * resources.displayMetrics.density
    }
}

// ── Custom View for the Glowing Ring ─────────────────────────────────────
class PremiumRingView(context: Context, private val score: Int, private val blocks: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val radius = minOf(cx, cy) * 0.7f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // Background Ring
        paint.color = 0xFF15152A.toInt(); paint.strokeWidth = 45f
        canvas.drawArc(rect, 0f, 360f, false, paint)

        // Glow
        paint.setShadowLayer(30f, 0f, 0f, 0xFFC8A2F8.toInt())
        setLayerType(LAYER_TYPE_SOFTWARE, paint)

        // Progress Ring
        val sweep = (score.toFloat() / 100f * 360f)
        paint.shader = SweepGradient(cx, cy, intArrayOf(0xFFC8A2F8.toInt(), 0xFFA2E8F8.toInt(), 0xFFC8A2F8.toInt()), null).apply {
            val matrix = Matrix(); matrix.setRotate(-90f, cx, cy); setLocalMatrix(matrix)
        }
        canvas.drawArc(rect, -90f, sweep, false, paint)
        paint.shader = null; paint.clearShadowLayer()

        // Score Text
        textPaint.color = Color.WHITE; textPaint.textSize = 120f; textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("$score%", cx, cy + 20f, textPaint)
        
        textPaint.color = 0xFFA2E8F8.toInt(); textPaint.textSize = 28f; textPaint.typeface = Typeface.DEFAULT; textPaint.letterSpacing = 0.2f
        canvas.drawText("FOCUS SCORE", cx, cy - 80f, textPaint)

        textPaint.color = 0xFF6666AA.toInt(); textPaint.textSize = 28f; textPaint.letterSpacing = 0f
        canvas.drawText("$blocks attempts today", cx, cy + 80f, textPaint)
    }
}
