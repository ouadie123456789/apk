package com.example.likeebridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var statusText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        statusText = findViewById(R.id.statusText)
        val saveBtn = findViewById<Button>(R.id.saveBtn)
        val enableBtn = findViewById<Button>(R.id.enableBtn)

        val prefs = getSharedPreferences(LikeeAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        urlInput.setText(prefs.getString(LikeeAccessibilityService.KEY_ENDPOINT, ""))

        saveBtn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                Toast.makeText(this, "Enter a valid http(s) URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString(LikeeAccessibilityService.KEY_ENDPOINT, url).apply()
            Toast.makeText(this, "Endpoint saved", Toast.LENGTH_SHORT).show()
        }

        enableBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() { super.onResume(); handler.post(refresher) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(refresher) }

    private fun updateStatus() {
        val prefs = getSharedPreferences(LikeeAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        val last = prefs.getLong(LikeeAccessibilityService.KEY_LAST_CAPTURE, 0L)
        val ageMs = System.currentTimeMillis() - last
        statusText.text = when {
            !isServiceEnabled() ->
                "Status: DISABLED — tap \"Enable Service\" and turn on Likee Bridge."
            last > 0 && ageMs < 10_000 ->
                "Status: CAPTURING (last message ${ageMs / 1000}s ago)"
            else ->
                "Status: WAITING — service on, no Likee chat detected yet."
        }
    }

    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, LikeeAccessibilityService::class.java)
        val flat = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        for (name in splitter) {
            if (ComponentName.unflattenFromString(name) == expected) return true
        }
        return false
    }
}
