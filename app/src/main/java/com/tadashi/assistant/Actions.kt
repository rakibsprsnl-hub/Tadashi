package com.tadashi.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import org.json.JSONObject

object Actions {
    fun run(ctx: Context, a: JSONObject): String {
        val svc = TadashiAccessibilityService.instance
        return try {
            when (a.optString("type")) {
                "call" -> {
                    val n = findNumber(ctx, a.optString("name")) ?: return "no contact named ${a.optString("name")}"
                    dial(ctx, n)
                }
                "call_number" -> dial(ctx, a.optString("number"))
                "sms" -> {
                    val n = findNumber(ctx, a.optString("name")) ?: return "no contact named ${a.optString("name")}"
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault().sendTextMessage(n, null, a.optString("message"), null, null)
                    "sms sent"
                }
                "whatsapp" -> whatsapp(ctx, a, svc)
                "open_app" -> openApp(ctx, a.optString("name"))
                "open_url" -> {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.optString("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "opened url"
                }
                "click" -> if (svc?.click(a.optString("text")) == true) "clicked ${a.optString("text")}" else "could not find ${a.optString("text")}"
                "type" -> if (svc?.typeText(a.optString("text")) == true) "typed" else "no input field to type in"
                "scroll" -> if (svc?.scroll(a.optString("dir")) == true) "scrolled" else "nothing to scroll"
                "back" -> { svc?.back(); "back" }
                "home" -> { svc?.home(); "home" }
                "notifications" -> { svc?.notifications(); "notifications opened" }
                "wait" -> { Thread.sleep(1500); "waited" }
                else -> "unknown action"
            }
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }

    private fun dial(ctx: Context, number: String): String {
        ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "calling $number"
    }

    private fun findNumber(ctx: Context, name: String): String? {
        val p = ContactsContract.CommonDataKinds.Phone.NUMBER
        val c = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(p),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null)
        c?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun openApp(ctx: Context, name: String): String {
        val pm = ctx.packageManager
        for (app in pm.getInstalledApplications(0)) {
            if (pm.getApplicationLabel(app).toString().contains(name, ignoreCase = true)) {
                val i = pm.getLaunchIntentForPackage(app.packageName) ?: continue
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "opened $name"
            }
        }
        return "app $name not found"
    }

    private fun whatsapp(ctx: Context, a: JSONObject, svc: TadashiAccessibilityService?): String {
        val raw = a.optString("number").ifBlank { findNumber(ctx, a.optString("name")) ?: "" }
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return "no number for ${a.optString("name")}"
        val uri = Uri.parse("https://wa.me/$digits?text=" + Uri.encode(a.optString("message")))
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(3500)
        return if (svc?.click("Send") == true) "whatsapp message sent" else "whatsapp opened, could not press send"
    }
}
