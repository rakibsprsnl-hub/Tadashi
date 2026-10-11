package com.tadashi.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService

// These let Android list Tadashi as a "digital assistant" (power-button / home long-press).
class TadashiVoiceService : VoiceInteractionService()

class TadashiSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = TadashiSession(this)
}

class TadashiSession(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            context.startForegroundService(Intent(context, AssistantService::class.java).setAction("LISTEN_NOW"))
        } catch (e: Exception) { }
        hide()
    }
}

class TadashiRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStop(listener: Callback?) {}
}
