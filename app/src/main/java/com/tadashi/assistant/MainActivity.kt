package com.tadashi.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class MainActivity : Activity() {
    private lateinit var geminiKey: EditText
    private lateinit var status: TextView

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { f() }
    }

    private fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 28, 0, 4) }

    private fun modelReady() = File(filesDir, "vosk-model/am").exists()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 80) }
        col.addView(TextView(this).apply { text = "Tadashi"; textSize = 28f })

        col.addView(label("1. Free Gemini API key (aistudio.google.com/apikey)"))
        geminiKey = EditText(this).apply {
            hint = "AIza..."; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Prefs.get(this@MainActivity, "gemini_key"))
        }
        col.addView(geminiKey)
        col.addView(btn("Save key") {
            Prefs.set(this, "gemini_key", geminiKey.text.toString().trim())
            toast("Saved")
        })

        col.addView(label("2. Wake-word model (free, offline, about 40 MB, needs internet once)"))
        status = TextView(this).apply { text = if (modelReady()) "Model ready" else "Model not downloaded yet" }
        col.addView(status)
        col.addView(btn("Download wake-word model") { downloadModel() })

        col.addView(label("3. Permissions"))
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

        col.addView(label("4. Start"))
        col.addView(btn("Start / restart Tadashi") {
            startForegroundService(Intent(this, AssistantService::class.java).setAction("RESTART_WAKE"))
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

    private fun downloadModel() {
        status.text = "Downloading... keep this screen open"
        Thread {
            try {
                val dir = File(filesDir, "vosk-model")
                dir.deleteRecursively()
                dir.mkdirs()
                val c = URL("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 20000
                c.readTimeout = 60000
                ZipInputStream(c.inputStream).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val name = e.name.substringAfter('/')
                        if (name.isNotEmpty() && !name.contains("..")) {
                            val f = File(dir, name)
                            if (e.isDirectory) f.mkdirs() else {
                                f.parentFile?.mkdirs()
                                f.outputStream().use { o -> z.copyTo(o) }
                            }
                        }
                        e = z.nextEntry
                    }
                }
                runOnUiThread { status.text = "Model ready. Tap Start / restart Tadashi." }
            } catch (ex: Exception) {
                runOnUiThread { status.text = "Download failed: ${ex.message}" }
            }
        }.start()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
