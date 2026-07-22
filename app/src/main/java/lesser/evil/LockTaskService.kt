package lesser.evil

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(28)
class LockTaskService: Service() {
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            coroutineScope.cancel()
            LockTaskUtils.exitLockTask(this@LockTaskService) { stop() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.getBooleanExtra(EXTRA_NAVIGATION_BUTTONS, false) == true) {
            val gestureNavigation = intent.getBooleanExtra(EXTRA_GESTURE_NAV, false)
            val accessibility = NavigationAccessibilityService.instance
            if (accessibility != null) {
                accessibility.showNavigationBar(gestureNavigation)
            } else if (gestureNavigation && Settings.canDrawOverlays(this)) {
                // No accessibility: draw our own exit bar over the app. Back still works through
                // the system back gesture, and Home / exit runs entirely in our own code.
                showOverlayBar()
            }
            // With neither permission, LockTaskUtils.start() fell back to the system bar.
        }
        val filter = IntentFilter(STOP_ACTION)
        ContextCompat.registerReceiver(
            this, stopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, Intent(STOP_ACTION).setPackage(this.packageName), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, MyNotificationChannel.LockTaskMode.id)
            .setContentTitle(getText(R.string.lock_task_mode))
            .setSmallIcon(R.drawable.lock_fill0)
            .addAction(NotificationCompat.Action.Builder(null, getString(R.string.stop), pendingIntent).build())
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        ServiceCompat.startForeground(
            this, NotificationType.LockTaskMode.id, notification,
            if (Build.VERSION.SDK_INT < 34) 0 else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
        )
        coroutineScope.launch {
            val am = getSystemService(ActivityManager::class.java)
            delay(3000)
            while (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED) {
                delay(1000)
            }
            stop()
        }
        return START_NOT_STICKY
    }

    private val stopped = AtomicBoolean(false)
    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        NavigationAccessibilityService.instance?.hideNavigationBar()
        hideOverlayBar()
        if (SP.lockTaskHomeInterception) LockTaskUtils.disableHomeInterception(this)
        LockTaskUtils.restoreTemporaryAppStates()
        unregisterReceiver(stopReceiver)
        stopSelf()
    }

    override fun onDestroy() {
        NavigationAccessibilityService.instance?.hideNavigationBar()
        hideOverlayBar()
        super.onDestroy()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayBar: View? = null

    /** Home / exit bar shown with the display-over-other-apps permission, no accessibility. */
    private fun showOverlayBar() {
        if (overlayBar != null) return
        val density = resources.displayMetrics.density
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xE6000000.toInt())
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val home = ImageView(this)
        home.setImageResource(R.drawable.nav_home)
        val padding = (12 * density).toInt()
        home.setPadding(padding, padding, padding, padding)
        val outValue = TypedValue()
        if (theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, outValue, true
        )) {
            home.setBackgroundResource(outValue.resourceId)
        }
        home.setOnClickListener { LockTaskUtils.exitToHome(this) }
        bar.addView(home, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1F))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (48 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }
        try {
            getSystemService(WindowManager::class.java).addView(bar, params)
            overlayBar = bar
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideOverlayBar() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hideOverlayBar() }
            return
        }
        overlayBar?.let {
            try {
                getSystemService(WindowManager::class.java).removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayBar = null
    }

    companion object {
        const val STOP_ACTION = "lesser.evil.action.STOP_LOCK_TASK_MODE"
        const val EXTRA_NAVIGATION_BUTTONS = "show_navigation_buttons"
        const val EXTRA_GESTURE_NAV = "gesture_navigation"
    }
}