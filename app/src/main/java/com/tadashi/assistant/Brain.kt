package com.tadashi.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {
    // Free Gemini model. If Google retires it, change this name (see aistudio.google.com).
    const val MODEL = "gemini-2.5-flash"

    val SYSTEM = """
You are Tadashi, a friendly voice assistant living inside the user's Android phone. You can answer any question (use Google search for current facts) and control the phone.
Reply with ONE JSON object and nothing else, no markdown fences:
{"speech":"what to say out loud, short and natural","actions":[...],"done":true}
Set "done" to false only when you need to see the screen after your actions before continuing (e.g. after opening an app, to find the next button). You will then receive the new screen text.
Action types:
{"type":"call","name":"contact name"}
{"type":"call_number","number":"+123..."}
{"type":"sms","name":"contact name","message":"text"}
{"type":"whatsapp","name":"contact name","message":"text"}  (opens chat, types and presses send)
{"type":"open_app","name":"app name, e.g. Instagram"}
{"type":"open_url","url":"https://..."}
{"type":"click","text":"visible text or description of the button"}
{"type":"type","text":"text to type in the focused input"}
{"type":"scroll","dir":"down or up"}
{"type":"back"} {"type":"home"} {"type":"notifications"} {"type":"wait"}
Rules: keep speech brief because it is spoken aloud. No markdown. For questions just answer in speech with an empty actions list. Before anything irreversible (payments, deleting things, posting publicly, messaging many people) ask the user to confirm by voice and do not act until they say yes. Never invent screen contents; use the screen text given.
""".trimIndent()

    fun chat(key: String, messages: JSONArray): String {
        return try {
            call(key, messages, true)
        } catch (e: Exception) {
            try {
                call(key, messages, false)
            } catch (e2: Exception) {
                JSONObject().put("speech", "Sorry, I could not reach my brain. " + (e2.message ?: "").take(100))
                    .put("actions", JSONArray()).put("done", true).toString()
            }
        }
    }

    private fun call(key: String, messages: JSONArray, search: Boolean): String {
        val contents = JSONArray()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = if (m.getString("role") == "assistant") "model" else "user"
            contents.put(JSONObject().put("role", role)
                .put("parts", JSONArray().put(JSONObject().put("text", m.getString("content")))))
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", contents)
        if (search) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 90000
        c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("HTTP $code $text")
        val parts = JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString()
    }
}
