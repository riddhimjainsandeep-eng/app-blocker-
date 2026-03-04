package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "BlockerPrefs"
        const val KEY_APPS = "blocked_apps"
        const val KEY_WEBSITES = "blocked_websites"
        const val KEY_KEYWORDS = "blocked_keywords"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Wire up buttons ---
        findViewById<Button>(R.id.btnEnableService).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnAddApp).setOnClickListener {
            showAddDialog(
                title = "Block an App",
                hint = "e.g. com.instagram.android",
                key = KEY_APPS,
                listView = findViewById(R.id.tvAppsList)
            )
        }

        findViewById<Button>(R.id.btnAddWebsite).setOnClickListener {
            showAddDialog(
                title = "Block a Website",
                hint = "e.g. instagram.com",
                key = KEY_WEBSITES,
                listView = findViewById(R.id.tvWebsitesList)
            )
        }

        findViewById<Button>(R.id.btnAddKeyword).setOnClickListener {
            showAddDialog(
                title = "Block a Keyword",
                hint = "e.g. reels",
                key = KEY_KEYWORDS,
                listView = findViewById(R.id.tvKeywordsList)
            )
        }

        findViewById<Button>(R.id.btnClearAll).setOnClickListener {
            confirmClearAll()
        }

        // --- Display current lists on launch ---
        refreshAllLists()
    }

    // Shows an AlertDialog with an EditText, saves input to SharedPreferences
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

    private fun confirmClearAll() {
        AlertDialog.Builder(this, R.style.BlockerDialog)
            .setTitle("Clear Everything?")
            .setMessage("This will remove ALL blocked apps, websites, and keywords. Only do this in an emergency.")
            .setPositiveButton("Clear All") { _, _ ->
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_APPS)
                    .remove(KEY_WEBSITES)
                    .remove(KEY_KEYWORDS)
                    .apply()
                refreshAllLists()
                Toast.makeText(this, "All blocks cleared.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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
        textView.setTextColor(if (items.isEmpty()) 0xFF555577.toInt() else 0xFFCCCCDD.toInt())
    }

    private fun refreshAllLists() {
        refreshList(KEY_APPS, findViewById(R.id.tvAppsList))
        refreshList(KEY_WEBSITES, findViewById(R.id.tvWebsitesList))
        refreshList(KEY_KEYWORDS, findViewById(R.id.tvKeywordsList))
    }
}
