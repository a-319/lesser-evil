package com.bintianqi.owndroid

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Intent
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
 * Draws system-like navigation buttons at the bottom of the screen while lock task mode is
 * running. The back button works inside the locked app; the home and recents buttons first
 * exit lock task mode and then perform their normal function. No extra privileges are needed
 * beyond the user enabling this accessibility service.
 */
class NavigationAccessibilityService : AccessibilityService() {
    private var navigationBar: View? = null
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

    @RequiresApi(28)
    fun showNavigationBar() {
        if (navigationBar != null) return
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
        addButton(R.drawable.nav_back) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        addButton(R.drawable.nav_home) {
            exitLockTaskThen { performGlobalAction(GLOBAL_ACTION_HOME) }
        }
        addButton(R.drawable.nav_recents) {
            exitLockTaskThen { performGlobalAction(GLOBAL_ACTION_RECENTS) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (48 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }
        try {
            getSystemService(WindowManager::class.java).addView(bar, params)
            navigationBar = bar
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideNavigationBar() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hideNavigationBar() }
            return
        }
        navigationBar?.let {
            try {
                getSystemService(WindowManager::class.java).removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        navigationBar = null
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
