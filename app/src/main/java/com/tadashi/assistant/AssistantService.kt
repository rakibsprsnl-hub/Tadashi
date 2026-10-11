package com.tadashi.assistant

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.UUID

class AssistantService : Service(), TextToSpeech.OnInitListener {
    private var wake: PorcupineManager? = null
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            startFg()
        } catch (e: Exception) {
            Log.e("Tadashi", "foreground failed", e)
            stopSelf()
            return
        }
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
        main.post { startWake() }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        when (i?.action) {
            "LISTEN_NOW" -> main.post { onWake() }
            "TEXT" -> i.getStringExtra("text")?.let { runCommand(it) }
        }
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("tadashi", "Tadashi", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = Notification.Builder(this, "tadashi")
            .setContentTitle("Tadashi is listening")
            .setContentText("Say \"Tadashi\" to wake me up")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun startWake() {
        if (wake != null || busy) return
        val key = Prefs.get(this, "pv_key")
        val ppn = File(filesDir, "tadashi.ppn")
        if (key.isBlank() || !ppn.exists()) {
            Log.w("Tadashi", "Picovoice key or tadashi.ppn missing")
            return
        }
        try {
            wake = PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeywordPath(ppn.absolutePath)
                .setSensitivity(0.7f)
                .build(applicationContext, PorcupineManagerCallback { _ -> onWake() })
            wake?.start()
        } catch (e: Exception) {
            Log.e("Tadashi", "wake word failed", e)
            wake = null
        }
    }

    private fun stopWake() {
        try { wake?.stop(); wake?.delete() } catch (e: Exception) { }
        wake = null
    }

    private fun onWake() {
        main.post {
            if (busy) return@post
            busy = true
            stopWake()
            listen()
        }
    }

    private fun listen() {
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) { sr.destroy(); finish() }
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                sr.destroy()
                if (t.isNullOrBlank()) finish() else runCommand(t)
            }
        })
        sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
    }

    private fun runCommand(text: String) {
        busy = true
        main.post { stopWake() }
        Thread {
            Agent.run(this, text) { speak(it) }
            Thread.sleep(400)
            val end = System.currentTimeMillis() + 90000
            while (tts?.isSpeaking == true && System.currentTimeMillis() < end) Thread.sleep(200)
            main.post { finish() }
        }.start()
    }

    private fun finish() {
        busy = false
        startWake()
    }

    private fun speak(t: String) {
        tts?.speak(t, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    override fun onDestroy() {
        stopWake()
        tts?.shutdown()
        super.onDestroy()
    }
}
