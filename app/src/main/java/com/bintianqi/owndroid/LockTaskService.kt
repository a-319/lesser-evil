package com.bintianqi.owndroid

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
            // Home always works via the Device-Owner home interception. Back / Overview need the
            // accessibility service; if it is off we silently run in "Home only" mode.
            val gestureNavigation = intent.getBooleanExtra(EXTRA_GESTURE_NAV, false)
            NavigationAccessibilityService.instance?.showNavigationBar(gestureNavigation)
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
        if (SP.lockTaskHomeInterception) LockTaskUtils.disableHomeInterception(this)
        LockTaskUtils.restoreTemporaryAppStates()
        unregisterReceiver(stopReceiver)
        stopSelf()
    }

    override fun onDestroy() {
        NavigationAccessibilityService.instance?.hideNavigationBar()
        super.onDestroy()
    }

    companion object {
        const val STOP_ACTION = "com.bintianqi.owndroid.action.STOP_LOCK_TASK_MODE"
        const val EXTRA_NAVIGATION_BUTTONS = "show_navigation_buttons"
        const val EXTRA_GESTURE_NAV = "gesture_navigation"
    }
}