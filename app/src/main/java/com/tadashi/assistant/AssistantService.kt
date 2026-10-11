package com.tadashi.assistant

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
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener as VoskListener
import java.io.File
import java.util.Locale
import java.util.UUID

class AssistantService : Service(), TextToSpeech.OnInitListener {
    private var model: Model? = null
    private var rec: Recognizer? = null
    private var speech: SpeechService? = null
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false
    @Volatile private var loading = false

    // Ways the offline recognizer may "hear" the name Tadashi (spaces removed). Add more if needed.
    private val wakeWords = listOf(
        "tadashi", "tadasi", "tadasha", "tadashe", "tadashy", "tadashee",
        "tardashi", "tardyshe", "tardyshi", "todayshe", "todayshi", "todashi",
        "tadash", "dashi", "tidashi", "tedashi"
    )

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
            "RESTART_WAKE" -> main.post { stopWake(); startWake() }
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

    private val wakeListener = object : VoskListener {
        override fun onPartialResult(h: String?) { check(h) }
        override fun onResult(h: String?) { check(h) }
        override fun onFinalResult(h: String?) {}
        override fun onError(e: Exception?) { Log.e("Tadashi", "vosk error", e) }
        override fun onTimeout() {}
    }

    private fun check(json: String?) {
        if (json == null || busy) return
        val t = try {
            val o = JSONObject(json)
            o.optString("partial", o.optString("text", ""))
        } catch (e: Exception) { "" }
        val s = t.replace(" ", "").lowercase()
        if (s.isNotEmpty() && wakeWords.any { s.contains(it) }) onWake()
    }

    private fun startWake() {
        if (speech != null || busy || loading) return
        val dir = File(filesDir, "vosk-model")
        if (!File(dir, "am").exists()) {
            Log.w("Tadashi", "wake-word model not downloaded yet")
            return
        }
        loading = true
        Thread {
            try {
                if (model == null) model = Model(dir.absolutePath)
                main.post {
                    try {
                        if (busy || speech != null) return@post
                        val r = Recognizer(model, 16000.0f)
                        val s = SpeechService(r, 16000.0f)
                        s.startListening(wakeListener)
                        rec = r
                        speech = s
                    } catch (e: Exception) {
                        Log.e("Tadashi", "wake start failed", e)
                    } finally {
                        loading = false
                    }
                }
            } catch (e: Exception) {
                Log.e("Tadashi", "model load failed", e)
                loading = false
            }
        }.start()
    }

    private fun stopWake() {
        try { speech?.stop() } catch (e: Exception) { }
        try { speech?.shutdown() } catch (e: Exception) { }
        try { rec?.close() } catch (e: Exception) { }
        speech = null
        rec = null
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
