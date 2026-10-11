package com.tadashi.assistant

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("tadashi", Context.MODE_PRIVATE)
    fun get(c: Context, k: String): String = p(c).getString(k, "") ?: ""
    fun set(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()
}
