package com.bintianqi.owndroid

import android.app.Activity
import android.os.Build
import android.os.Bundle

/**
 * Transparent, no-UI activity registered as the persistent HOME activity (via Device Owner)
 * while a lock task session with the navigation-buttons option is running. Pressing the real
 * Home button (or performing the home gesture) resolves to this activity instead of the
 * launcher; it exits lock task mode (waiting until the system has actually left it) and then
 * forwards to the real launcher.
 *
 * This lets the Home button leave lock task mode without needing the accessibility service, and
 * it is self-healing: even if a session ends abnormally, the first Home press runs this activity,
 * which clears the interception and hands control back to the real launcher.
 */
class LockTaskHomeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                LockTaskUtils.exitToHome(this) { finish() }
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        LockTaskUtils.launchHome(this)
        finish()
    }
}
