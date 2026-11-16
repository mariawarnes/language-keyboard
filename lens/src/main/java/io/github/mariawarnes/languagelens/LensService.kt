package io.github.mariawarnes.languagelens

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Language Lens. Reads text only in WhatsApp and Messenger (the apps switched on in settings), to
 * translate it on the phone. For any other app it only checks which app is in front, so it can
 * hide its translations the moment you switch away. Nothing it reads is stored or sent anywhere.
 */
class LensService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: LensOverlay
    private lateinit var translator: LensTranslator

    private val refresh = Runnable { refreshNow() }

    /** Backup check while labels are up, in case an app switch sends no event. */
    private val watch = object : Runnable {
        override fun run() {
            if (!overlay.isShowing) return
            if (!chatAppInFront()) overlay.clear() else handler.postDelayed(this, WATCH_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = LensOverlay(this, DragReplay())
        translator = LensTranslator { schedule(0) }
        LensPrefs.get(this).registerOnSharedPreferenceChangeListener(this)
        configure()
    }

    private fun configure() {
        translator.configure(LensPrefs.native(this), LensPrefs.learning(this))
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == LensPrefs.KEY_LEARNING || key == LensPrefs.KEY_NATIVE) configure()
        overlay.clear()
        schedule(0)
    }

    /** Snapshot of the real windows (not our labels), to tell real changes from our own. */
    private var lastWindows = ""

    private fun windowSnapshot(): String {
        val r = Rect()
        return windows.filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            .joinToString("|") { w ->
                w.getBoundsInScreen(r)
                "${w.id}:${w.type}:${w.isActive}:${r.flattenToString()}"
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::overlay.isInitialized) return
        if (event.packageName?.toString() == packageName) return // our own labels
        if (LensPrefs.paused(this)) {
            overlay.clear()
            return
        }
        when (event.eventType) {
            // Something moved in front or behind (app switch, recents, home, notification shade,
            // keyboard…): hide straight away, then put labels back if the chat is still in front.
            AccessibilityEvent.TYPE_WINDOWS_CHANGED, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (!chatAppInFront()) {
                    overlay.clear()
                    lastWindows = ""
                    return
                }
                val snapshot = windowSnapshot()
                if (snapshot == lastWindows) return // only our labels changed
                lastWindows = snapshot
                overlay.setVisible(false)
                schedule(WINDOW_SETTLE_MS)
            }
            else -> {
                // Content changes are only looked at for the chat apps; everything else is ignored.
                val pkg = event.packageName?.toString() ?: return
                if (pkg !in LensPrefs.packages(this)) return
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                    overlay.setVisible(false) // back once scrolling stops
                    schedule(SCROLL_SETTLE_MS)
                } else {
                    schedule(CHANGE_SETTLE_MS)
                }
            }
        }
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, delay)
    }

    /** True when the window you're using belongs to WhatsApp / Messenger. */
    private fun chatAppInFront(): Boolean {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return false
        return pkg in LensPrefs.packages(this)
    }

    private fun refreshNow() {
        if (LensPrefs.paused(this)) {
            overlay.clear()
            return
        }
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        if (root == null || pkg == null || pkg !in LensPrefs.packages(this)) {
            overlay.clear()
            return
        }
        val found = ArrayList<Pair<String, Rect>>()
        collect(root, found, 0)
        val items = found.mapNotNull { (text, bounds) ->
            translator.lookup(text)?.let { LensOverlay.Item(text, it, bounds) }
        }.take(MAX_LABELS)
        overlay.show(items)
        handler.removeCallbacks(watch)
        if (items.isNotEmpty()) handler.postDelayed(watch, WATCH_MS)
    }

    /** Visible, non-editable text on screen (the message bubbles), with where it is. */
    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Pair<String, Rect>>, depth: Int) {
        if (depth > 40 || !node.isVisibleToUser || node.isEditable) return
        // Message text. Descriptions are only used for plain views with no text (some apps describe
        // messages that way); buttons and icons ("Emojis, GIFs and stickers") are never translated.
        val className = node.className?.toString().orEmpty()
        val isControl = "Button" in className || "Image" in className || node.isCheckable
        val text = node.text?.toString()
            ?: node.contentDescription?.toString()?.takeIf { node.childCount == 0 && !isControl }
        if (text != null && TextFilter.worthTranslating(text)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                out += text to bounds
                return
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, depth + 1)
        }
    }

    /**
     * Replays a drag that started on a translation as a touch gesture on the chat, piece by piece
     * as the finger moves, so the chat scrolls along with it (and flings at the end).
     */
    private inner class DragReplay : LensOverlay.DragListener {
        private var stroke: GestureDescription.StrokeDescription? = null
        private val pending = ArrayList<PointF>()
        private var last = PointF()
        private var lastTime = 0L
        private var busy = false
        private var ending = false

        override fun dragStart(fromX: Float, fromY: Float, x: Float, y: Float) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return // continued strokes need Android 8
            stroke = null
            pending.clear()
            last = PointF(fromX, fromY)
            lastTime = SystemClock.uptimeMillis()
            busy = false
            ending = false
            add(x, y)
            pump()
        }

        override fun dragMove(x: Float, y: Float) {
            add(x, y)
            pump()
        }

        override fun dragEnd(x: Float, y: Float) {
            add(x, y)
            ending = true
            pump()
            schedule(SCROLL_SETTLE_MS * 2) // labels come back after the fling
        }

        private fun add(x: Float, y: Float) {
            val prev = pending.lastOrNull() ?: last
            if (prev.x != x || prev.y != y) pending += PointF(x, y)
        }

        private fun pump() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || busy) return
            if (pending.isEmpty() && !ending) return
            val path = Path().apply {
                moveTo(last.x, last.y)
                if (pending.isEmpty()) lineTo(last.x, last.y + 0.1f) // a stroke needs some length
                pending.forEach { lineTo(it.x, it.y) }
            }
            val end = pending.lastOrNull() ?: last
            pending.clear()
            val now = SystemClock.uptimeMillis()
            val duration = (now - lastTime).coerceIn(8L, 120L)
            lastTime = now
            val final = ending
            val next = stroke?.continueStroke(path, 0, duration, !final)
                ?: GestureDescription.StrokeDescription(path, 0, duration, !final)
            stroke = if (final) null else next
            last = end
            busy = true
            dispatchGesture(
                GestureDescription.Builder().addStroke(next).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        busy = false
                        if (!final) pump()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        busy = false
                        stroke = null
                        pending.clear()
                        ending = false
                    }
                },
                null,
            )
            if (final) ending = false
        }
    }

    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.clear()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::overlay.isInitialized) overlay.clear()
        if (::translator.isInitialized) translator.close()
        LensPrefs.get(this).unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    private companion object {
        const val CHANGE_SETTLE_MS = 150L
        const val SCROLL_SETTLE_MS = 250L
        const val WINDOW_SETTLE_MS = 300L
        const val WATCH_MS = 500L
        const val MAX_LABELS = 30
    }
}
