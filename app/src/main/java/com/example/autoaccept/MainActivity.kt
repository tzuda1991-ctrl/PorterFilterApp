package com.example.autoaccept

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FilterPrefs", Context.MODE_PRIVATE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        val minFareInput = EditText(this).apply {
            hint = "कम से कम किराया लिखें (जैसे: 300)"
            setText(prefs.getInt("MIN_FARE", 300).toString())
        }
        val saveBtn = Button(this).apply {
            text = "1. फ़िल्टर सेव करें (Save Filter)"
            setOnClickListener {
                val fare = minFareInput.text.toString().toIntOrNull() ?: 300
                prefs.edit().putInt("MIN_FARE", fare).apply()
                Toast.makeText(context, "फ़िल्टर सेव हो गया: ₹$fare", Toast.LENGTH_SHORT).show()
            }
        }
        val permBtn = Button(this).apply {
            text = "2. Accessibility सर्विस ऑन करें"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        layout.addView(minFareInput)
        layout.addView(saveBtn)
        layout.addView(permBtn)
        setContentView(layout)
    }
}
