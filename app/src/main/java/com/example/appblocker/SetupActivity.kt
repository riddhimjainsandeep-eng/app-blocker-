package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * Shown on first launch only.
 * Asks for the user's email, sends a 6-digit verification code,
 * then confirms it. On success, saves the email and opens MainActivity.
 */
class SetupActivity : AppCompatActivity() {

    companion object {
        const val PREFS_SETUP    = "SetupPrefs"
        const val KEY_USER_NAME  = "user_name"
        const val KEY_USER_AGE   = "user_age"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_SETUP_DONE = "setup_done"
    }

    private var pendingName  = ""
    private var pendingAge   = ""
    private var pendingEmail = ""
    private var sentCode     = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already set up, skip straight to main
        val prefs = getSharedPreferences(PREFS_SETUP, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            launchMain(); return
        }

        showEmailEntry()
    }

    // ── Step 1: Ask for name, age, email ────────────────────────────────────
    private fun showEmailEntry() {
        val layout = buildLayout()

        val title = TextView(this).apply {
            text = "📚 Study Sanctum"
            textSize = 28f
            setTextColor(0xFFC8A2F8.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Create your focus profile to get started."
            textSize = 13f
            setTextColor(0xFF8888AA.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        val nameInput = setupInput("Enter your name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val ageInput = setupInput("Enter your age", InputType.TYPE_CLASS_NUMBER)
        val emailInput = setupInput("Enter your email address", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT)

        val sendBtn = Button(this).apply {
            text = "Verify Email & Start"
            textSize = 15f
            setTextColor(0xFF0D0D1A.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFC8A2F8.toInt())
            stateListAnimator = null
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val age = ageInput.text.toString().trim()
                val email = emailInput.text.toString().trim()

                if (name.isEmpty() || age.isEmpty() || email.isEmpty()) {
                    Toast.makeText(this@SetupActivity, "Please fill all fields.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(this@SetupActivity, "Invalid email address.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                pendingName = name
                pendingAge = age
                pendingEmail = email
                sendVerificationCode(email)
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(textViewLabel("NAME"))
        layout.addView(nameInput)
        layout.addView(makeSpace(12))
        layout.addView(textViewLabel("AGE"))
        layout.addView(ageInput)
        layout.addView(makeSpace(12))
        layout.addView(textViewLabel("EMAIL"))
        layout.addView(emailInput)
        layout.addView(makeSpace(24))
        layout.addView(sendBtn)
        setContentView(wrapInScroll(layout))
    }

    private fun setupInput(hintStr: String, inputTypeInt: Int) = EditText(this).apply {
        hint = hintStr
        inputType = inputTypeInt
        setHintTextColor(0xFF555577.toInt())
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF1A1A2E.toInt())
        setPadding(24, 20, 24, 20)
        textSize = 15f
    }

    private fun textViewLabel(txt: String) = TextView(this).apply {
        text = txt
        textSize = 10f
        setTextColor(0xFF6666AA.toInt())
        letterSpacing = 0.2f
        setPadding(4, 0, 0, 4)
    }

    // ── Step 2: Send code and show verification screen ───────────────────────
    private fun sendVerificationCode(email: String) {
        sentCode = (100000..999999).random().toString()
        val subject = "Your Study Sanctum Verification Code"
        val html = """
            <div style="background:#0D0D1A;padding:32px;font-family:Arial;color:#CCCCDD;">
              <div style="font-size:24px;font-weight:bold;color:#C8A2F8;margin-bottom:16px;">📚 Study Sanctum</div>
              <p>Your verification code is:</p>
              <div style="font-size:48px;font-weight:bold;color:#C8A2F8;letter-spacing:12px;margin:20px 0;">$sentCode</div>
              <p style="color:#6666AA;font-size:12px;">This code expires when you close the app. Do not share it.</p>
            </div>
        """.trimIndent()

        Toast.makeText(this, "Sending code to $email…", Toast.LENGTH_SHORT).show()

        EmailSender.sendReport(email, subject, html) { success, error ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Code sent! Check your inbox.", Toast.LENGTH_LONG).show()
                    showCodeEntry()
                } else {
                    Toast.makeText(this, "Failed to send: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Step 3: User enters the code ─────────────────────────────────────────
    private fun showCodeEntry() {
        val layout = buildLayout()

        val title = TextView(this).apply {
            text = "Check Your Inbox"
            textSize = 22f
            setTextColor(0xFFC8A2F8.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "We sent a 6-digit code to\n$pendingEmail"
            textSize = 13f
            setTextColor(0xFF8888AA.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        val codeInput = EditText(this).apply {
            hint = "Enter 6-digit code"
            inputType = InputType.TYPE_CLASS_NUMBER
            setHintTextColor(0xFF555577.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(24, 20, 24, 20)
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.3f
        }
        val verifyBtn = Button(this).apply {
            text = "Verify & Get Started"
            textSize = 15f
            setTextColor(0xFF0D0D1A.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFC8A2F8.toInt())
            stateListAnimator = null
            setOnClickListener {
                val entered = codeInput.text.toString().trim()
                if (entered == sentCode) {
                    saveEmailAndProceed()
                } else {
                    Toast.makeText(this@SetupActivity, "Incorrect code. Try again.", Toast.LENGTH_SHORT).show()
                    codeInput.setBackgroundColor(0xFF2A0A0A.toInt())
                }
            }
        }
        val resendBtn = Button(this).apply {
            text = "Resend Code"
            textSize = 13f
            setTextColor(0xFF8888AA.toInt())
            setBackgroundColor(0x00000000)
            stateListAnimator = null
            setOnClickListener { sendVerificationCode(pendingEmail) }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(codeInput)
        layout.addView(makeSpace(16))
        layout.addView(verifyBtn)
        layout.addView(resendBtn)
        setContentView(wrapInScroll(layout))
    }

    // ── Save verified profile → show Accessibility Guide ─────────────────────
    private fun saveEmailAndProceed() {
        getSharedPreferences(PREFS_SETUP, Context.MODE_PRIVATE).edit()
            .putString(KEY_USER_NAME, pendingName)
            .putString(KEY_USER_AGE, pendingAge)
            .putString(KEY_USER_EMAIL, pendingEmail)
            .apply() 
        showAccessibilityGuide()
    }

    private fun showAccessibilityGuide() {
        val layout = buildLayout()

        val title = TextView(this).apply {
            text = "🛡️ Final Step: Enable Focus Guard"
            textSize = 24f
            setTextColor(0xFFC8A2F8.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }

        val description = TextView(this).apply {
            text = "To block distracting apps and websites, Study Sanctum needs 'Accessibility' permission.\n\n" +
                   "1. Click the button below\n" +
                   "2. Find 'Study Sanctum' in the list\n" +
                   "3. Toggle 'Use Study Sanctum' to ON"
            textSize = 14f
            setTextColor(0xFFCCCCDD.toInt())
            setLineSpacing(8f, 1f)
            setPadding(0, 0, 0, 32)
        }

        val grantBtn = Button(this).apply {
            text = "Grant Permission"
            textSize = 15f
            setTextColor(0xFF0D0D1A.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFC8A2F8.toInt())
            stateListAnimator = null
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val continueBtn = Button(this).apply {
            text = "I've Enabled It, Let's Go!"
            textSize = 15f
            setTextColor(0xFFCCCCDD.toInt())
            background = null
            setPadding(0, 24, 0, 0)
            setOnClickListener {
                getSharedPreferences(PREFS_SETUP, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_SETUP_DONE, true)
                    .apply()
                launchMain()
            }
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(grantBtn)
        layout.addView(continueBtn)
        setContentView(wrapInScroll(layout))
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ── Layout helpers ────────────────────────────────────────────────────────
    private fun buildLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF0D0D1A.toInt())
        setPadding(48, 0, 48, 48)
        gravity = android.view.Gravity.CENTER_HORIZONTAL
    }

    private fun wrapInScroll(inner: LinearLayout): android.widget.ScrollView {
        val scroll = android.widget.ScrollView(this).apply {
            setBackgroundColor(0xFF0D0D1A.toInt())
            isFillViewport = true
        }
        
        val lp = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 120 }
        
        scroll.addView(inner, lp)
        return scroll
    }

    private fun makeSpace(dp: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, dp * resources.displayMetrics.density.toInt()
        )
    }
}
