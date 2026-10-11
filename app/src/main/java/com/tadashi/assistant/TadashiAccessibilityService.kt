package com.tadashi.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TadashiAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: TadashiAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        try {
            startForegroundService(Intent(this, AssistantService::class.java))
        } catch (e: Exception) { }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun dump(): String {
        val root = rootInActiveWindow ?: return "(no screen)"
        val sb = StringBuilder("app: ${root.packageName}\n")
        walk(root, sb)
        return sb.toString().take(3500)
    }

    private fun walk(n: AccessibilityNodeInfo, sb: StringBuilder) {
        if (sb.length > 3500) return
        val t = n.text?.toString()?.takeIf { it.isNotBlank() } ?: n.contentDescription?.toString()
        if (!t.isNullOrBlank()) {
            sb.append(if (n.isEditable) "[input] " else if (n.isClickable) "[btn] " else "").append(t.take(80)).append('\n')
        } else if (n.isEditable) sb.append("[input]\n")
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, sb) }
    }

    fun click(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = find(root, text.lowercase()) ?: return false
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        return (n ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun find(n: AccessibilityNodeInfo, t: String): AccessibilityNodeInfo? {
        val s = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
        if (s.lowercase().contains(t)) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            find(c, t)?.let { return it }
        }
        return null
    }

    fun typeText(s: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: firstEditable(root) ?: return false
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s)
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun firstEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            firstEditable(c)?.let { return it }
        }
        return null
    }

    fun scroll(dir: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = firstScrollable(root) ?: return false
        val action = if (dir.equals("up", true)) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        return node.performAction(action)
    }

    private fun firstScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            firstScrollable(c)?.let { return it }
        }
        return null
    }
}
