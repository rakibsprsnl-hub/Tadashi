package com.tadashi.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var geminiKey: EditText
    private lateinit var pvKey: EditText

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { f() }
    }

    private fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 28, 0, 4) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 80) }
        col.addView(TextView(this).apply { text = "Tadashi"; textSize = 28f })

        col.addView(label("1. Free Gemini API key (aistudio.google.com)"))
        geminiKey = EditText(this).apply {
            hint = "AIza..."; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.get(this@MainActivity, "gemini_key"))
        }
        col.addView(geminiKey)

        col.addView(label("2. Picovoice AccessKey (for the wake word)"))
        pvKey = EditText(this).apply {
            hint = "AccessKey"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.get(this@MainActivity, "pv_key"))
        }
        col.addView(pvKey)
        col.addView(btn("Save keys") {
            Prefs.set(this, "gemini_key", geminiKey.text.toString().trim())
            Prefs.set(this, "pv_key", pvKey.text.toString().trim())
            toast("Saved")
        })

        col.addView(label("3. Wake word file (Tadashi_android.ppn from console.picovoice.ai)"))
        col.addView(btn("Choose Tadashi .ppn file") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, 11)
        })

        col.addView(label("4. Permissions"))
        col.addView(btn("Grant mic / calls / contacts / SMS") {
            requestPermissions(arrayOf(
                Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS,
                Manifest.permission.POST_NOTIFICATIONS), 1)
        })
        col.addView(btn("Enable Tadashi in Accessibility (phone control)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        col.addView(btn("Set Tadashi as default assistant") {
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        })
        col.addView(btn("Turn off battery optimization") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        col.addView(label("5. Start"))
        col.addView(btn("Start Tadashi") {
            startForegroundService(Intent(this, AssistantService::class.java))
            toast("Tadashi started. Say her name.")
        })

        col.addView(label("Test without voice"))
        val test = EditText(this).apply { hint = "Type a command" }
        col.addView(test)
        col.addView(btn("Send command") {
            startForegroundService(Intent(this, AssistantService::class.java)
                .setAction("TEXT").putExtra("text", test.text.toString()))
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onActivityResult(r: Int, res: Int, d: Intent?) {
        super.onActivityResult(r, res, d)
        if (r == 11 && res == RESULT_OK) {
            d?.data?.let { uri: Uri ->
                contentResolver.openInputStream(uri)?.use { i ->
                    File(filesDir, "tadashi.ppn").outputStream().use { o -> i.copyTo(o) }
                }
                toast("Wake word file saved")
            }
        }
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
