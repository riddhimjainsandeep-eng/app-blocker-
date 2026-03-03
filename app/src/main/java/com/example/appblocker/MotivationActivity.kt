package com.example.appblocker

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MotivationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motivation)

        val btnBackToStudy = findViewById<Button>(R.id.btnBackToStudy)
        btnBackToStudy.setOnClickListener {
            // Close the app/activity
            finish()
        }
    }
}
