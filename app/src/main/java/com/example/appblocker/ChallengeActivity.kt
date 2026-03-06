package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Mental Friction Gate — matches study-sanctum-focus/src/pages/Challenge.tsx
 * Presents a math puzzle with a custom number keypad. Correct = 10-min exemption.
 */
class ChallengeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE   = "blocked_package"
        const val PREFS_EXEMPT    = "ChallengeExemptions"
        const val EXEMPT_DURATION = 10L * 60 * 1000

        fun exemptKey(pkg: String) = "exempt_${pkg.replace('.', '_')}_until"

        fun isExempt(context: Context, pkg: String): Boolean {
            val until = context.getSharedPreferences(PREFS_EXEMPT, Context.MODE_PRIVATE)
                .getLong(exemptKey(pkg), 0L)
            return System.currentTimeMillis() < until
        }
    }

    private var correctAnswer   = -1
    private var blockedPkg      = ""
    private var inputStr        = ""
    private lateinit var tvInput: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvQuestion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        window.statusBarColor = 0xFF0B0F1A.toInt()
        blockedPkg = intent.getStringExtra(EXTRA_PACKAGE) ?: ""

        setContentView(buildLayout("Summoning AI puzzle...\n(Gemini 3.1 Flash-Lite)"))
        
        lifecycleScope.launch {
            val puzzle = GeminiHelper.generateLogicPuzzle()
            correctAnswer = puzzle.correctAnswer
            tvQuestion.text = puzzle.question
        }
    }

    private fun buildLayout(question: String): ScrollView {
        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0F1A.toInt())
            setPadding((20*dp).toInt(), (56*dp).toInt(), (20*dp).toInt(), (32*dp).toInt())
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Brain icon circle
        val iconFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams((64*dp).toInt(), (64*dp).toInt()).also {
                it.bottomMargin = (20*dp).toInt()
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFF0D1A35.toInt())
            }
        }
        iconFrame.addView(textView("🧠", 26f, 0xFFFFFFFF.toInt()).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)
        })
        root.addView(iconFrame)

        root.addView(textView("Mental Friction Gate", 18f, 0xFFEEF2F7.toInt(), bold = true, bottomPad = 8))
        root.addView(textView("Prove your intent by solving this challenge", 12f, 0xFF5D7A99.toInt(), bottomPad = 40))

        // Question card
        root.addView(glassCard(this, dp).apply {
            addView(textView("CHALLENGE", 9f, 0xFF5D7A99.toInt(), letterSpacing = 0.3f, bottomPad = 8))
            tvQuestion = textView(question, 18f, 0xFFEEF2F7.toInt(), bold = true, mono = true, centered = true)
            addView(tvQuestion)
        })

        space(root, 20, dp)

        // Input display card
        val inputCard = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, (72*dp).toInt()).also { it.bottomMargin = (8*dp).toInt() }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF1A2233.toInt()); cornerRadius = 20*dp; setStroke(1, 0xFF1E2D3D.toInt())
            }
        }
        tvInput = TextView(this).apply {
            text = "—"
            textSize = 32f
            setTextColor(0x4DEEF2F7.toInt())
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)
        }
        inputCard.addView(tvInput)
        root.addView(inputCard)

        tvStatus = textView("", 13f, 0xFF5D7A99.toInt(), centered = true, bottomPad = 16)
        root.addView(tvStatus)

        space(root, 8, dp)

        // Number keypad — 4 rows × 3 cols
        val keys = listOf("1","2","3","4","5","6","7","8","9","DEL","0","✓")
        val grid = GridLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = (20*dp).toInt() }
            columnCount = 3
            rowCount = 4
        }
        keys.forEach { key ->
            val btn = TextView(this).apply {
                text = key
                textSize = if (key == "✓" || key == "DEL") 16f else 22f
                setTextColor(if (key == "✓") 0xFF0B0F1A.toInt() else 0xFFEEF2F7.toInt())
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                background = if (key == "✓") {
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF11C27A.toInt()); cornerRadius = 14*dp
                    }
                } else {
                    android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xFF1A2233.toInt()); cornerRadius = 14*dp; setStroke(1, 0xFF1E2D3D.toInt())
                    }
                }
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0; height = (56*dp).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec    = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins((4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt(), (4*dp).toInt())
                }
                setOnClickListener { handleKey(key) }
                isClickable = true
                isFocusable = true
            }
            grid.addView(btn)
        }
        root.addView(grid)

        // Back to Study button
        val backBtn = Button(this).apply {
            text = "← Back to Study"
            textSize = 13f
            setTextColor(0xFF5D7A99.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1A2233.toInt())
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(-1, (52*dp).toInt())
            setOnClickListener { finish() }
        }
        root.addView(backBtn)

        return ScrollView(this).apply {
            setBackgroundColor(0xFF0B0F1A.toInt())
            addView(root)
        }
    }

    private fun handleKey(key: String) {
        when (key) {
            "DEL" -> {
                if (inputStr.isNotEmpty()) inputStr = inputStr.dropLast(1)
                updateInputDisplay()
            }
            "✓"  -> verifyAnswer()
            else -> {
                if (inputStr.length < 6) { inputStr += key; updateInputDisplay() }
            }
        }
    }

    private fun updateInputDisplay() {
        tvInput.text = if (inputStr.isEmpty()) "—" else inputStr
        tvInput.setTextColor(if (inputStr.isEmpty()) 0x4DEEF2F7.toInt() else 0xFFEEF2F7.toInt())
    }

    private fun verifyAnswer() {
        if (correctAnswer == -1) {
            tvStatus.setTextColor(0xFFFC7171.toInt())
            tvStatus.text = "Please wait for AI..."
            return
        }
        val entered = inputStr.toIntOrNull()
        if (entered == null) { tvStatus.setTextColor(0xFFFC7171.toInt()); tvStatus.text = "Enter a number first."; return }
        if (entered == correctAnswer) {
            tvStatus.setTextColor(0xFF11C27A.toInt())
            tvStatus.text = "✓ Correct! Access granted for 10 minutes."
            getSharedPreferences(PREFS_EXEMPT, Context.MODE_PRIVATE).edit()
                .putLong(exemptKey(blockedPkg), System.currentTimeMillis() + EXEMPT_DURATION).apply()
            tvInput.postDelayed({
                if (blockedPkg.isNotEmpty()) {
                    packageManager.getLaunchIntentForPackage(blockedPkg)?.let {
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it)
                    }
                }
                finish()
            }, 800)
        } else {
            tvStatus.setTextColor(0xFFFC7171.toInt())
            tvStatus.text = "Incorrect. Try again."
            inputStr = ""
            updateInputDisplay()
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private fun textView(
        txt: String, size: Float, color: Int, bold: Boolean = false,
        mono: Boolean = false, centered: Boolean = true,
        letterSpacing: Float = 0f, bottomPad: Int = 0, wrap: Boolean = false
    ) = TextView(this).apply {
        text = txt; textSize = size; setTextColor(color)
        if (bold) typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
        else if (mono) typeface = Typeface.MONOSPACE
        if (centered) gravity = Gravity.CENTER
        if (letterSpacing > 0) this.letterSpacing = letterSpacing
        if (bottomPad > 0) setPadding(0, 0, 0, (bottomPad * resources.displayMetrics.density).toInt())
        if (wrap) layoutParams = LinearLayout.LayoutParams(-2, -2)
        else layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun glassCard(ctx: Context, dp: Float) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding((24*dp).toInt(), (20*dp).toInt(), (24*dp).toInt(), (20*dp).toInt())
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF1A2233.toInt()); cornerRadius = 20*dp; setStroke(1, 0xFF1E2D3D.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = (8*dp).toInt() }
    }

    private fun space(parent: LinearLayout, dp: Int, dpF: Float) = parent.addView(
        View(this).apply { layoutParams = LinearLayout.LayoutParams(1, (dp * dpF).toInt()) }
    )
}
