package com.example.likeebridge

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LikeeAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "LikeeBridge"
        const val PREFS_NAME = "likee_bridge_prefs"
        const val KEY_ENDPOINT = "endpoint_url"
        const val KEY_LAST_CAPTURE = "last_capture_ts"

        // Verified from Likee APK v5.64.1 (versionCode 7577)
        val LIKEE_PACKAGES = setOf("video.like")

        // Barrage (live public chat) row IDs, v5.64.1. Re-derive via DEBUG_DUMP if Likee updates.
        const val ID_NICK = "video.like:id/tv_barrage_sender_nickname"
        const val ID_MSG  = "video.like:id/tv_barrage_sender_msg"
        const val ID_BARRAGE = "video.like:id/tv_barrage"   // fallback: combined "user: message"

        // Flip to true once to print the whole node tree to Logcat, then flip back.
        const val DEBUG_DUMP = false

        private const val DEDUP_WINDOW_MS = 15_000L
        private const val MIN_EVENT_INTERVAL_MS = 120L
        private const val MAX_CACHE = 200
        private const val MAX_DEPTH = 40

        @Volatile
        var isRunning = false
            private set
    }

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var endpoint: String = ""
    private var lastEventTime = 0L

    // Access-ordered LRU: dedupes recent identical messages within the time window.
    private val recent = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>) = size > MAX_CACHE
    }

    private lateinit var prefs: SharedPreferences
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
        if (key == KEY_ENDPOINT) endpoint = sp.getString(KEY_ENDPOINT, "") ?: ""
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        endpoint = prefs.getString(KEY_ENDPOINT, "") ?: ""
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        isRunning = true
        Log.i(TAG, "Service connected. endpoint=$endpoint")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (LIKEE_PACKAGES.isNotEmpty() && pkg !in LIKEE_PACKAGES) return

        val now = System.currentTimeMillis()
        if (now - lastEventTime < MIN_EVENT_INTERVAL_MS) return   // throttle event storms
        lastEventTime = now

        val root = rootInActiveWindow ?: return

        if (DEBUG_DUMP) {
            dumpTree(root, 0)
            return
        }

        val messages = extractMessages(root)
        if (messages.isEmpty()) return

        var captured = false
        for ((user, msg) in messages) {
            val key = "$user|$msg"
            synchronized(recent) {
                val prev = recent[key]
                if (prev != null && now - prev < DEDUP_WINDOW_MS) return@synchronized
                recent[key] = now
            }
            send(user, msg, now)
            captured = true
        }
        if (captured) prefs.edit().putLong(KEY_LAST_CAPTURE, now).apply()
    }

    private fun extractMessages(root: AccessibilityNodeInfo): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()

        val nicks = root.findAccessibilityNodeInfosByViewId(ID_NICK) ?: emptyList()
        val msgs  = root.findAccessibilityNodeInfosByViewId(ID_MSG)  ?: emptyList()

        // Pair nickname[i] with msg[i] — same row index in the barrage RecyclerView
        if (nicks.isNotEmpty() && msgs.isNotEmpty()) {
            val n = minOf(nicks.size, msgs.size)
            for (i in 0 until n) {
                val user = nicks[i].text?.toString()?.trim().orEmpty()
                val msg  = msgs[i].text?.toString()?.trim().orEmpty()
                if (msg.isNotEmpty()) out.add(user to msg)
            }
            return out
        }

        // Fallback: combined barrage line "user: message"
        val barrage = root.findAccessibilityNodeInfosByViewId(ID_BARRAGE) ?: emptyList()
        for (b in barrage) {
            val line = b.text?.toString()?.trim().orEmpty()
            if (line.isNotEmpty()) out.add(parseLine(line))
        }
        return out
    }

    private fun parseLine(line: String): Pair<String, String> {
        val idx = line.indexOfFirst { it == ':' || it == '：' }
        if (idx in 1..40) {
            val user = line.substring(0, idx).trim()
            val msg = line.substring(idx + 1).trim()
            if (user.isNotEmpty() && msg.isNotEmpty()) return user to msg
        }
        return "" to line   // couldn't split — send whole line as the message
    }

    private fun send(username: String, message: String, ts: Long) {
        val url = endpoint
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) return
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val body = JSONObject()
                    .put("username", username)
                    .put("message", message)
                    .put("source", "likee")
                    .put("captured_at", ts)
                    .toString()
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                Log.d(TAG, "POST ${conn.responseCode} $body")
            } catch (e: Exception) {
                Log.w(TAG, "POST failed: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun dumpTree(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null || depth > MAX_DEPTH) return
        val id = node.viewIdResourceName ?: "-"
        val txt = node.text?.toString() ?: ""
        Log.d(TAG, "  ".repeat(depth) + "[$id] ${node.className} text='$txt'")
        for (i in 0 until node.childCount) dumpTree(node.getChild(i), depth + 1)
    }

    override fun onInterrupt() { Log.i(TAG, "onInterrupt") }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (this::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        executor.shutdown()
    }
}
