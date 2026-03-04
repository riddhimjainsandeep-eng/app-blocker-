package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "BlockerPrefs"
        const val KEY_APPS = "blocked_apps"
        const val KEY_WEBSITES = "blocked_websites"
        const val KEY_KEYWORDS = "blocked_keywords"
        const val KEY_INITIALIZED = "is_initialized"

        private const val STATS_PREFS = "BlockerStats"
        private const val REPORT_EMAIL = "riddhimjainsandeep@gmail.com"

        val DEFAULT_APPS = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.twitter.android",
            "com.snapchat.android"
        )
        val DEFAULT_WEBSITES = setOf(
            "instagram.com", "facebook.com", "youtube.com/shorts",
            "tiktok.com", "twitter.com", "snapchat.com"
        )
        val DEFAULT_KEYWORDS = setOf(
            "reels", "shorts", "foryou", "explore", "trending",
            "porn", "xxx", "onlyfans", "nsfw"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        seedDefaultsIfNeeded()
        WeeklyReportWorker.scheduleAll(this)   // daily + Sunday + last day of month

        findViewById<Button>(R.id.btnEnableService).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnAddApp).setOnClickListener { showAppPicker() }

        findViewById<Button>(R.id.btnAddWebsite).setOnClickListener {
            showAddDialog("Block a Website", "e.g. instagram.com", KEY_WEBSITES)
        }

        findViewById<Button>(R.id.btnAddKeyword).setOnClickListener {
            showAddDialog("Block a Keyword", "e.g. reels", KEY_KEYWORDS)
        }

        // Manual "Send now" button — reuses the same worker logic
        findViewById<Button>(R.id.btnSendReport).setOnClickListener {
            sendReportNow()
        }
    }

    // Schedule weekly report every 7 days via WorkManager (survives reboots)
    private fun scheduleWeeklyReport() {
        val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WeeklyReportWorker.WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeeklyReportWorker.WORK_TAG,
            ExistingPeriodicWorkPolicy.KEEP,   // Don't reset timer if already scheduled
            request
        )
    }

    // Manual send — builds the HTML report and fires it immediately
    private fun sendReportNow() {
        Toast.makeText(this, "Sending report…", Toast.LENGTH_SHORT).show()
        val (subject, body) = WeeklyReportWorker.buildReport(this)
        EmailSender.sendReport(subject, body) { success, error ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "✅ Report sent!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "❌ Failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Default preset seeding on first launch
    // -----------------------------------------------------------------------
    private fun seedDefaultsIfNeeded() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        prefs.edit()
            .putStringSet(KEY_APPS, DEFAULT_APPS.toMutableSet())
            .putStringSet(KEY_WEBSITES, DEFAULT_WEBSITES.toMutableSet())
            .putStringSet(KEY_KEYWORDS, DEFAULT_KEYWORDS.toMutableSet())
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    // -----------------------------------------------------------------------
    // App Picker using packageManager
    // -----------------------------------------------------------------------
    private fun showAppPicker() {
        val pm = packageManager

        // Use getLaunchIntentForPackage so we get ALL apps with a launcher icon
        // (including pre-installed ones like Instagram, YouTube, etc.)
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != "com.example.appblocker" } // hide ourselves
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val appNames = allApps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val appIcons = allApps.map { pm.getApplicationIcon(it) }
        val inflater = LayoutInflater.from(this)

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, appNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = inflater.inflate(R.layout.item_app_picker, parent, false)
                row.findViewById<ImageView>(R.id.ivAppIcon).setImageDrawable(appIcons[position])
                row.findViewById<TextView>(R.id.tvAppName).text = appNames[position]
                row.findViewById<TextView>(R.id.tvPackageName).text = allApps[position].packageName
                return row
            }
        }

        val listView = ListView(this).apply { this.adapter = adapter }
        val dialog = AlertDialog.Builder(this, R.style.BlockerDialog)
            .setTitle("Pick an App to Block")
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val name = appNames[position]
            addToPrefs(KEY_APPS, allApps[position].packageName)
            Toast.makeText(this, "✓ \"$name\" is now being blocked.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    // -----------------------------------------------------------------------
    // Text-input dialog (websites + keywords)
    // -----------------------------------------------------------------------
    private fun showAddDialog(title: String, hint: String, key: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            this.hint = hint
            setHintTextColor(0xFF555577.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF12122A.toInt())
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this, R.style.BlockerDialog)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Block It") { _, _ ->
                val value = input.text.toString().trim().lowercase()
                if (value.isNotEmpty()) {
                    addToPrefs(key, value)
                    Toast.makeText(this, "✓ \"$value\" is now being blocked.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -----------------------------------------------------------------------
    // Weekly Report — compiles stats and opens Gmail with pre-filled email
    // -----------------------------------------------------------------------
    private fun sendWeeklyReport() {
        val stats = getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
        val prefs  = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val thisWeek   = stats.getInt("weekly_blocks", 0)
        val lastWeek   = stats.getInt("last_week_total", 0)
        val totalEver  = stats.getInt("total_blocks", 0)
        val blockedApps = prefs.getStringSet(KEY_APPS, emptySet()) ?: emptySet()
        val blockedSites = prefs.getStringSet(KEY_WEBSITES, emptySet()) ?: emptySet()

        // Per-app breakdown
        val appBreakdown = blockedApps.joinToString("\n") { pkg ->
            val key = "app_count_${pkg.replace('.', '_')}"
            val count = stats.getInt(key, 0)
            val name = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) { pkg }
            "  • $name: $count block(s)"
        }

        // Hourly heatmap (last 7 days)
        val timestamps = (stats.getString("block_timestamps", "") ?: "")
            .split(",").filter { it.isNotBlank() }
            .mapNotNull { it.toLongOrNull() }
        val hourCounts = IntArray(24)
        val cal = Calendar.getInstance()
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        timestamps.filter { it > sevenDaysAgo }.forEach {
            cal.timeInMillis = it
            hourCounts[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        val peakHour = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 0
        val peakLabel = SimpleDateFormat("ha", Locale.getDefault())
            .format(cal.apply { set(Calendar.HOUR_OF_DAY, peakHour) }.time)

        // Trend emoji
        val trend = when {
            thisWeek < lastWeek -> "📉 Improved! Down ${lastWeek - thisWeek} vs last week"
            thisWeek > lastWeek -> "📈 More attempts this week (+${thisWeek - lastWeek})"
            else                -> "➡️ Same as last week"
        }

        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        val subject = "📊 Study Sanctum Weekly Report — $dateStr"
        val body = """
Hello Riddhim,

Here is your Focus Guard Weekly Report for the week ending $dateStr.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🛡️  WEEKLY OVERVIEW
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  This week's blocked attempts  : $thisWeek
  Last week's blocked attempts  : $lastWeek
  Trend                         : $trend
  All-time total blocked        : $totalEver

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📱  BLOCKED APP BREAKDOWN (this week)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

${if (appBreakdown.isBlank()) "  No attempts recorded." else appBreakdown}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⏰  PEAK DISTRACTION TIME
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  You most often try to open blocked apps around $peakLabel.
  Consider putting your phone on Do Not Disturb during this window.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🌐  CURRENTLY BLOCKED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Apps     : ${blockedApps.size} blocked
  Websites : ${blockedSites.size} blocked

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💡  COACH'S NOTE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

${buildCoachNote(thisWeek, lastWeek)}

Keep going. Your CA/ISC exams are worth more than a 15-second reel.
— Study Sanctum Focus Guard
        """.trimIndent()

        // Send directly via SMTP — no app picker, goes straight to inbox
        Toast.makeText(this, "Sending report…", Toast.LENGTH_SHORT).show()

        EmailSender.sendReport(subject, body) { success, error ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "✅ Report sent to $REPORT_EMAIL", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "❌ Failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildCoachNote(thisWeek: Int, lastWeek: Int): String = when {
        thisWeek == 0             -> "  🏆 Perfect week! Zero attempts on any blocked app. You are in the zone."
        thisWeek < lastWeek       -> "  ✅ You improved this week. Every time you didn't open Instagram,\n  you chose your future over a dopamine hit. Keep that streak alive."
        thisWeek in 1..5          -> "  🙂 Only $thisWeek attempt(s) this week — that's great control.\n  The Motivation Wall is doing its job."
        thisWeek in 6..15         -> "  ⚠️  $thisWeek attempts this week. Try placing your phone face-down\n  during study sessions to reduce temptation."
        else                      -> "  🚨 $thisWeek attempts! Consider enabling Grayscale mode on your phone\n  and using a physical timer (Pomodoro: 25 min on, 5 min off)."
    }

    // -----------------------------------------------------------------------
    // SharedPreferences helper
    // -----------------------------------------------------------------------
    private fun addToPrefs(key: String, value: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(key, mutableSetOf())!!.toMutableSet()
        current.add(value)
        prefs.edit().putStringSet(key, current).apply()
    }
}
