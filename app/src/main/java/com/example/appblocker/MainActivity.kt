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
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME    = "BlockerPrefs"
        const val KEY_APPS      = "blocked_apps"
        const val KEY_WEBSITES  = "blocked_websites"
        const val KEY_KEYWORDS  = "blocked_keywords"
        const val KEY_INITIALIZED = "is_initialized"

        val DEFAULT_APPS = setOf(
            "com.instagram.android", "com.facebook.katana",
            "com.google.android.youtube", "com.zhiliaoapp.musically",
            "com.twitter.android", "com.snapchat.android"
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
        WeeklyReportWorker.scheduleAll(this)  // daily + Sunday + last day of month

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
        // Manual send button — builds & sends the HTML report immediately
        findViewById<Button>(R.id.btnSendReport).setOnClickListener {
            sendReportNow()
        }
        // Stats screen
        findViewById<Button>(R.id.btnViewStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        // Anti-Uninstall Protection
        findViewById<Button>(R.id.btnProtectUninstall).setOnClickListener {
            activateDeviceAdmin()
        }
        // Update check
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            checkForUpdates()
        }
    }

    private fun activateDeviceAdmin() {
        val componentName = ComponentName(this, AppBlockerAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enabling this adds friction to uninstallation, helping you stay focused.")
        }
        startActivity(intent)
    }

    // ── Seed defaults on very first install ──────────────────────────────────
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

    // ── Manual "Send now" — reuses the worker's report builder ───────────────
    private fun sendReportNow() {
        // Get the verified email the user registered with
        val userEmail = getSharedPreferences(SetupActivity.PREFS_SETUP, Context.MODE_PRIVATE)
            .getString(SetupActivity.KEY_USER_EMAIL, "riddhimjainsandeep@gmail.com") ?: "riddhimjainsandeep@gmail.com"

        Toast.makeText(this, "Sending report to $userEmail…", Toast.LENGTH_SHORT).show()
        val (subject, body) = WeeklyReportWorker.buildReport(this, WeeklyReportWorker.TYPE_WEEKLY)
        EmailSender.sendReport(userEmail, subject, body) { success, error ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "✅ Report sent!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "❌ Failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── App Picker ───────────────────────────────────────────────────────────
    private fun showAppPicker() {
        val pm = packageManager
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != "com.example.appblocker" }
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

    // ── Text input dialog (websites + keywords) ──────────────────────────────
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

    // ── SharedPreferences helper ─────────────────────────────────────────────
    private fun addToPrefs(key: String, value: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(key, mutableSetOf())!!.toMutableSet()
        current.add(value)
        prefs.edit().putStringSet(key, current).apply()
    }

    // ── Check for updates ────────────────────────────────────────────────────
    private fun checkForUpdates() {
        // Link to the version.json on your GitHub main branch
        val updateUrl = "https://raw.githubusercontent.com/riddhimjainsandeep-eng/app-blocker-/main/version.json"
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val url = java.net.URL(updateUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                
                if (connection.responseCode == 404) {
                    runOnUiThread {
                        AlertDialog.Builder(this, R.style.BlockerDialog)
                            .setTitle("Update Check Failed (404)")
                            .setMessage("The version file was not found on GitHub.\n\nNote: If your repository is PRIVATE, this check will fail. Make it PUBLIC on GitHub to enable auto-updates.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    return@Thread
                }

                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(text)

                val latestCode = json.getInt("versionCode")
                val latestName = json.getString("versionName")
                val apkUrl     = json.getString("apkUrl")
                val notes      = json.optString("notes", "New version available!")

                val currentCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0).versionCode
                }

                runOnUiThread {
                    if (latestCode > currentCode) {
                        AlertDialog.Builder(this, R.style.BlockerDialog)
                            .setTitle("Update Available: $latestName")
                            .setMessage("$notes\n\nWould you like to download the new version?")
                            .setPositiveButton("Download APK") { _, _ ->
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(apkUrl))
                                startActivity(intent)
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        Toast.makeText(this, "You're up to date! (v$latestName)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Update check failed. Note: JSON must be on GitHub.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
