package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "BlockerPrefs"
        const val KEY_APPS = "blocked_apps"
        const val KEY_WEBSITES = "blocked_websites"
        const val KEY_KEYWORDS = "blocked_keywords"
        const val KEY_INITIALIZED = "is_initialized"

        // Default presets — installed on first launch
        val DEFAULT_APPS = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.twitter.android",
            "com.snapchat.android"
        )
        val DEFAULT_WEBSITES = setOf(
            "instagram.com",
            "facebook.com",
            "youtube.com/shorts",
            "tiktok.com",
            "twitter.com",
            "snapchat.com"
        )
        val DEFAULT_KEYWORDS = setOf(
            "reels", "shorts", "foryou", "explore", "trending",
            "porn", "xxx", "onlyfans", "nsfw"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Seed defaults on very first launch
        seedDefaultsIfNeeded()

        // Wire up buttons
        findViewById<Button>(R.id.btnEnableService).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // App button → opens the installed App Picker
        findViewById<Button>(R.id.btnAddApp).setOnClickListener {
            showAppPicker()
        }

        // Website button → text input dialog
        findViewById<Button>(R.id.btnAddWebsite).setOnClickListener {
            showAddDialog(
                title = "Block a Website",
                hint = "e.g. instagram.com",
                key = KEY_WEBSITES,
                listView = findViewById(R.id.tvWebsitesList)
            )
        }

        // Keyword button → text input dialog
        findViewById<Button>(R.id.btnAddKeyword).setOnClickListener {
            showAddDialog(
                title = "Block a Keyword",
                hint = "e.g. reels",
                key = KEY_KEYWORDS,
                listView = findViewById(R.id.tvKeywordsList)
            )
        }

        refreshAllLists()
    }

    // -----------------------------------------------------------------------
    // Seeds default blocked lists on the very first install
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
    // App Picker — shows all installed user apps with their icons and names
    // -----------------------------------------------------------------------
    private fun showAppPicker() {
        val pm = packageManager

        // Load all installed apps except system apps
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        // Build a simple list of (AppName, PackageName)
        val appNames = allApps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val appIcons = allApps.map { pm.getApplicationIcon(it) }

        // Custom adapter to show icon + name
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
            val pkg = allApps[position].packageName
            val name = appNames[position]
            addToPrefs(KEY_APPS, pkg)
            refreshList(KEY_APPS, findViewById(R.id.tvAppsList))
            Toast.makeText(this, "\"$name\" blocked ✓", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    // -----------------------------------------------------------------------
    // Text-input dialog for websites and keywords
    // -----------------------------------------------------------------------
    private fun showAddDialog(title: String, hint: String, key: String, listView: TextView) {
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
                    refreshList(key, listView)
                    Toast.makeText(this, "\"$value\" blocked ✓", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -----------------------------------------------------------------------
    // SharedPreferences helpers
    // -----------------------------------------------------------------------
    private fun addToPrefs(key: String, value: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(key, mutableSetOf())!!.toMutableSet()
        current.add(value)
        prefs.edit().putStringSet(key, current).apply()
    }

    private fun refreshList(key: String, textView: TextView) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val items = prefs.getStringSet(key, emptySet()) ?: emptySet()
        textView.text = if (items.isEmpty()) {
            "None blocked yet."
        } else {
            items.sorted().joinToString("\n") { "• $it" }
        }
        textView.setTextColor(
            if (items.isEmpty()) 0xFF555577.toInt() else 0xFFCCCCDD.toInt()
        )
    }

    private fun refreshAllLists() {
        refreshList(KEY_APPS, findViewById(R.id.tvAppsList))
        refreshList(KEY_WEBSITES, findViewById(R.id.tvWebsitesList))
        refreshList(KEY_KEYWORDS, findViewById(R.id.tvKeywordsList))
    }
}
