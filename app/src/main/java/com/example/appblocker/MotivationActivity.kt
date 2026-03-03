package com.example.appblocker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MotivationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motivation)

        val btnBackToStudy = findViewById<Button>(R.id.btnBackToStudy)
        btnBackToStudy.setOnClickListener {
            // 1. Go to Home Screen (Safe Exit)
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            
            // 2. Close the wall
            finish()
        }
    }
}
