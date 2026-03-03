package com.example.appblocker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val enableButton = Button(this).apply {
            text = "Enable App Blocker\n(Opens Settings)"
            textSize = 18f
            setPadding(32, 32, 32, 32)
            
            setOnClickListener {
                openAccessibilitySettings()
            }
        }

        val layout = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(enableButton)
        }
        
        setContentView(layout)
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK 
        startActivity(intent)
    }
}
