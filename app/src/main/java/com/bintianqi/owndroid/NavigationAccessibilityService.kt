package com.bintianqi.owndroid

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi

/**
 * Renders navigation controls while lock task mode is running, without root or Shizuku.
 *
 * Two display modes:
 * - 3-button navigation: two fully transparent touch zones are placed over the left/right thirds
 *   of the system navigation bar (over the real Back and Overview buttons). The middle third is
 *   left uncovered so the real Home button keeps working and is intercepted by the persistent
 *   home activity (which exits lock task mode). Tapping a zone runs the matching action.
 * - Gesture navigation (no on-screen buttons): a visible bar with Back / Home / Overview buttons
 *   is drawn at the bottom of the screen.
 *
 * The Home button always leaves lock task mode via the Device-Owner home interception, so it works
 * even when this accessibility service is not enabled. Back and Overview require this service.
 */
class NavigationAccessibilityService : AccessibilityService() {
    private val addedViews = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hideNavigationBar()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideNavigationBar()
        instance = null
        super.onDestroy()
    }

    private fun navigationBarHeight(): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId)
        else (48 * resources.displayMetrics.density).toInt()
    }

    private val isRtl: Boolean
        get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

    @RequiresApi(28)
    fun showNavigationBar(gestureNavigation: Boolean) {
        if (addedViews.isNotEmpty()) return
        if (gestureNavigation) showVisibleBar() else showTransparentZones()
    }

    /** Fully transparent touch zones over the real Back and Overview buttons. */
    @RequiresApi(28)
    private fun showTransparentZones() {
        val screenWidth = resources.displayMetrics.widthPixels
        val zoneWidth = screenWidth / 3
        val height = navigationBarHeight()
        val backAction = { performGlobalAction(GLOBAL_ACTION_BACK); Unit }
        val recentsAction = { exitLockTaskThen { performGlobalAction(GLOBAL_ACTION_RECENTS) } }
        // In RTL the Back / Overview buttons are usually mirrored, so swap the side each zone maps to.
        val leftAction = if (isRtl) recentsAction else backAction
        val rightAction = if (isRtl) backAction else recentsAction
        addZone(zoneWidth, height, Gravity.BOTTOM or Gravity.LEFT, leftAction)
        addZone(zoneWidth, height, Gravity.BOTTOM or Gravity.RIGHT, rightAction)
    }

    private fun addZone(width: Int, height: Int, gravity: Int, onClick: () -> Unit) {
        val view = View(this)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.setOnClickListener { onClick() }
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }
        addView(view, params)
    }

    /** Visible navigation bar for gesture-navigation devices (which have no system buttons). */
    @RequiresApi(28)
    private fun showVisibleBar() {
        val density = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xE6000000.toInt())
        }
        fun addButton(icon: Int, onClick: () -> Unit) {
            val view = ImageView(this)
            view.setImageResource(icon)
            val padding = (12 * density).toInt()
            view.setPadding(padding, padding, padding, padding)
            val outValue = TypedValue()
            if (theme.resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, outValue, true
            )) {
                view.setBackgroundResource(outValue.resourceId)
            }
            view.setOnClickListener { onClick() }
            bar.addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1F))
        }
        val back = { addButton(R.drawable.nav_back) { performGlobalAction(GLOBAL_ACTION_BACK) } }
        val home = { addButton(R.drawable.nav_home) { exitLockTaskThen { performGlobalAction(GLOBAL_ACTION_HOME) } } }
        val recents = { addButton(R.drawable.nav_recents) { exitLockTaskThen { performGlobalAction(GLOBAL_ACTION_RECENTS) } } }
        // Home stays in the middle; Back and Overview mirror in RTL.
        if (isRtl) { recents(); home(); back() } else { back(); home(); recents() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (48 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }
        addView(bar, params)
    }

    private fun addView(view: View, params: WindowManager.LayoutParams) {
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            addedViews += view
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideNavigationBar() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hideNavigationBar() }
            return
        }
        val wm = getSystemService(WindowManager::class.java)
        addedViews.forEach {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        addedViews.clear()
    }

    /** Exit lock task mode, then run the action once the system has actually left it. */
    @RequiresApi(28)
    private fun exitLockTaskThen(action: () -> Unit) {
        try {
            LockTaskUtils.forceStopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val am = getSystemService(ActivityManager::class.java)
        fun attempt(count: Int) {
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE || count >= 20) {
                action()
            } else {
                handler.postDelayed({ attempt(count + 1) }, 50)
            }
        }
        attempt(0)
    }

    companion object {
        var instance: NavigationAccessibilityService? = null
            private set
    }
}
