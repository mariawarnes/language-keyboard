package io.github.mariawarnes.languagelens

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Language Lens. Only WhatsApp and Messenger send it events (see res/xml/lens_service.xml), and
 * it only reads the messages on screen there, to translate them on the phone. Nothing it reads is
 * stored or sent anywhere.
 */
class LensService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: LensOverlay
    private lateinit var translator: LensTranslator

    private val refresh = Runnable { refreshNow() }

    /** While labels are up, check now and then that the chat app is still in front. */
    private val watch = object : Runnable {
        override fun run() {
            if (!overlay.isShowing) return
            if (!chatAppInFront()) overlay.clear() else handler.postDelayed(this, WATCH_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = LensOverlay(this)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (LensPrefs.paused(this)) {
            overlay.clear()
            return
        }
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in LensPrefs.packages(this)) {
            overlay.clear()
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            overlay.setVisible(false) // back once scrolling stops
            schedule(SCROLL_SETTLE_MS)
        } else {
            schedule(CHANGE_SETTLE_MS)
        }
    }

    private fun schedule(delay: Long) {
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, delay)
    }

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
        val text = node.text?.toString()
            ?: node.contentDescription?.toString()?.takeIf { node.childCount == 0 }
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
        const val WATCH_MS = 700L
        const val MAX_LABELS = 30
    }
}
