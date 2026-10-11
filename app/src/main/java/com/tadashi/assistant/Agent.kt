package com.tadashi.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Agent {
    private val memory = ArrayDeque<String>()

    private fun screen(): String =
        TadashiAccessibilityService.instance?.dump() ?: "(accessibility service is not enabled)"

    fun run(ctx: Context, command: String, say: (String) -> Unit) {
        val key = Prefs.get(ctx, "gemini_key")
        if (key.isBlank()) {
            say("Please add your API key in the Tadashi app first.")
            return
        }
        val msgs = JSONArray()
        val first = "Recent chat:\n" + memory.joinToString("\n") +
            "\n\nUser command: $command\n\nCurrent screen:\n" + screen()
        msgs.put(JSONObject().put("role", "user").put("content", first))
        var lastSpeech = ""
        for (step in 0 until 6) {
            val raw = Brain.chat(key, msgs)
            msgs.put(JSONObject().put("role", "assistant").put("content", raw.ifBlank { "{}" }))
            val j = parse(raw)
            val speech = j.optString("speech")
            if (speech.isNotBlank()) {
                say(speech)
                lastSpeech = speech
            }
            val acts = j.optJSONArray("actions") ?: JSONArray()
            val results = ArrayList<String>()
            for (i in 0 until acts.length()) results.add(Actions.run(ctx, acts.getJSONObject(i)))
            if (j.optBoolean("done", true) || acts.length() == 0) break
            Thread.sleep(1500)
            msgs.put(JSONObject().put("role", "user").put("content",
                "Action results:\n" + results.joinToString("\n") + "\n\nCurrent screen:\n" + screen()))
        }
        memory.addLast("You: $command / Tadashi: $lastSpeech")
        while (memory.size > 6) memory.removeFirst()
    }

    private fun parse(raw: String): JSONObject {
        return try {
            JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
        } catch (e: Exception) {
            JSONObject().put("speech", raw.take(400)).put("done", true)
        }
    }
}
