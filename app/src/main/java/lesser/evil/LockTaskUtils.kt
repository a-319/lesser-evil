package lesser.evil

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LockTaskProfile(
    val id: Int = 0,
    val name: String,
    val packageName: String,
    val activity: String = "",
    val clearTask: Boolean = true,
    val showNotification: Boolean = true,
    val packages: List<String> = emptyList(),
    val features: Int = 0,
    val showNavigationButtons: Boolean = false
)

object LockTaskUtils {
    private val json = Json { ignoreUnknownKeys = true }

    fun getProfiles(): List<LockTaskProfile> = SP.lockTaskProfiles?.let {
        try {
            json.decodeFromString<List<LockTaskProfile>>(it)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    } ?: emptyList()

    private fun saveProfiles(profiles: List<LockTaskProfile>) {
        SP.lockTaskProfiles = json.encodeToString(profiles)
    }

    fun addProfile(profile: LockTaskProfile): LockTaskProfile {
        val profiles = getProfiles()
        val added = profile.copy(id = (profiles.maxOfOrNull { it.id } ?: 0) + 1)
        saveProfiles(profiles + added)
        return added
    }

    fun deleteProfile(id: Int) {
        saveProfiles(getProfiles().filter { it.id != id })
    }

    /** Apply the packages and features saved in the profile, then start lock task mode. */
    @RequiresApi(28)
    fun startProfile(context: Context, profile: LockTaskProfile): Boolean {
        val packages = (profile.packages + profile.packageName).distinct()
        Privilege.DPM.setLockTaskPackages(Privilege.DAR, packages.toTypedArray())
        try {
            Privilege.DPM.setLockTaskFeatures(Privilege.DAR, profile.features)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
        return start(
            context, profile.packageName, profile.activity, profile.clearTask,
            profile.showNotification, profile.showNavigationButtons
        )
    }

    @RequiresApi(28)
    fun start(
        context: Context, packageName: String, activity: String,
        clearTask: Boolean, showNotification: Boolean, showNavigationButtons: Boolean
    ): Boolean {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        if (!dpm.isLockTaskPermitted(packageName)) {
            dpm.setLockTaskPackages(dar, dpm.getLockTaskPackages(dar) + packageName)
        }
        var features = dpm.getLockTaskFeatures(dar)
        if (showNotification) {
            features = features or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_HOME
        }
        if (showNavigationButtons) {
            val accessibility = NavigationAccessibilityService.instance != null
            if (!isGestureNavigation(context)) {
                // 3-button navigation: surface the real Home button so it can be intercepted
                // (exits lock task mode), and the real Overview button when the accessibility
                // service can put a transparent touch zone over it.
                features = features or DevicePolicyManager.LOCK_TASK_FEATURE_HOME
                if (accessibility) features = features or DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
            } else if (!accessibility && !Settings.canDrawOverlays(context)) {
                // Gesture navigation with no way to draw our own bar: fall back to the system
                // bar (the system temporarily switches to buttons) so Home can still exit.
                features = features or DevicePolicyManager.LOCK_TASK_FEATURE_HOME
            }
            // In gesture mode with our own bar, the home feature is deliberately not granted, so
            // the system never switches the device to button navigation (which on some devices
            // sticks after the session ends). Our bar is removed the moment the session exits.
        }
        if (features != dpm.getLockTaskFeatures(dar)) dpm.setLockTaskFeatures(dar, features)
        if (showNavigationButtons) {
            enableHomeInterception(context)
            // OwnDroid itself must be lock-task-permitted, otherwise the system will refuse to
            // launch the transparent home activity when the Home button is pressed.
            if (!dpm.isLockTaskPermitted(context.packageName)) {
                dpm.setLockTaskPackages(dar, dpm.getLockTaskPackages(dar) + context.packageName)
            }
        }
        liftTemporaryAppStates(dpm.getLockTaskPackages(dar).toList())
        val intent = if (activity.isNotEmpty()) {
            Intent().setComponent(ComponentName(packageName, activity))
        } else context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            restoreTemporaryAppStates()
            if (showNavigationButtons) disableHomeInterception(context)
            return false
        }
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or (if (clearTask) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)
        )
        val options = ActivityOptions.makeBasic().setLockTaskEnabled(true)
        context.startActivity(intent, options.toBundle())
        // The service monitors lock task mode, restores the lifted app states when it exits and
        // manages the navigation buttons, so it must also run when either of those is needed.
        if (showNotification || showNavigationButtons || hasTemporaryAppStates()) {
            context.applicationContext.startForegroundService(
                Intent(context.applicationContext, LockTaskService::class.java)
                    .putExtra(LockTaskService.EXTRA_NAVIGATION_BUTTONS, showNavigationButtons)
                    .putExtra(LockTaskService.EXTRA_GESTURE_NAV, isGestureNavigation(context))
            )
        }
        return true
    }

    /**
     * Force the device out of lock task mode by clearing the lock task whitelist, and run
     * [onExited] once the system has actually left lock task mode. The whitelist and features are
     * only restored after the state change is observed — restoring them immediately can cancel
     * the exit on some devices, leaving the launcher visible while lock task mode is still active.
     */
    @RequiresApi(28)
    fun exitLockTask(context: Context, onExited: () -> Unit = {}) {
        val am = context.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            onExited()
            return
        }
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        val features = dpm.getLockTaskFeatures(dar)
        val packages = dpm.getLockTaskPackages(dar)
        try {
            dpm.setLockTaskPackages(dar, arrayOf())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val handler = Handler(Looper.getMainLooper())
        fun restoreAndFinish() {
            try {
                dpm.setLockTaskPackages(dar, packages)
                dpm.setLockTaskFeatures(dar, features)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            onExited()
        }
        fun attempt(count: Int) {
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE || count >= 40) {
                restoreAndFinish()
            } else {
                handler.postDelayed({ attempt(count + 1) }, 50)
            }
        }
        attempt(0)
    }

    /** @return true when the device uses gesture navigation (no on-screen navigation buttons). */
    fun isGestureNavigation(context: Context): Boolean = try {
        Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0) == 2
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    private fun homeComponent(context: Context) =
        ComponentName(context, LockTaskHomeActivity::class.java)

    private val homeFilter: IntentFilter
        get() = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }

    /**
     * Make OwnDroid's transparent home activity the persistent preferred HOME target, so the real
     * Home button / gesture is routed to it (and therefore exits lock task mode). Device Owner
     * capability, no extra OS permission required.
     */
    fun enableHomeInterception(context: Context) {
        try {
            context.packageManager.setComponentEnabledSetting(
                homeComponent(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
            Privilege.DPM.addPersistentPreferredActivity(Privilege.DAR, homeFilter, homeComponent(context))
            SP.lockTaskHomeInterception = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Restore the normal launcher as the HOME target and disable the interception activity. */
    fun disableHomeInterception(context: Context) {
        try {
            Privilege.DPM.clearPackagePersistentPreferredActivities(Privilege.DAR, context.packageName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            context.packageManager.setComponentEnabledSetting(
                homeComponent(context),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        SP.lockTaskHomeInterception = false
    }

    /** Open the real launcher (clearing the interception first so it resolves to the real home). */
    fun launchHome(context: Context) {
        disableHomeInterception(context)
        try {
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Exit lock task mode and go to the real launcher. Used by the Home button. */
    @RequiresApi(28)
    fun exitToHome(context: Context, onDone: () -> Unit = {}) {
        exitLockTask(context) {
            launchHome(context)
            onDone()
        }
    }

    /**
     * Unsuspend and unhide the lock task packages so that they can run in lock task mode,
     * remembering which ones were changed so their state can be restored on exit.
     */
    private fun liftTemporaryAppStates(packages: List<String>) {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        val hidden = packages.filter {
            try { dpm.isApplicationHidden(dar, it) } catch (_: Exception) { false }
        }
        hidden.forEach {
            try { dpm.setApplicationHidden(dar, it, false) } catch (e: Exception) { e.printStackTrace() }
        }
        val suspended = if (Build.VERSION.SDK_INT >= 24) packages.filter {
            try { dpm.isPackageSuspended(dar, it) } catch (_: Exception) { false }
        }.also {
            if (it.isNotEmpty()) try {
                dpm.setPackagesSuspended(dar, it.toTypedArray(), false)
            } catch (e: Exception) { e.printStackTrace() }
        } else emptyList()
        SP.lockTaskUnhiddenApps = mergePackages(SP.lockTaskUnhiddenApps, hidden)
        SP.lockTaskUnsuspendedApps = mergePackages(SP.lockTaskUnsuspendedApps, suspended)
    }

    private fun mergePackages(old: String?, new: List<String>): String? {
        val merged = ((old?.let { parsePackageNames(it) } ?: emptyList()) + new).distinct()
        return if (merged.isEmpty()) null else merged.joinToString("\n")
    }

    fun hasTemporaryAppStates() =
        !SP.lockTaskUnhiddenApps.isNullOrEmpty() || !SP.lockTaskUnsuspendedApps.isNullOrEmpty()

    /** Re-suspend and re-hide the apps whose state was lifted for lock task mode. */
    fun restoreTemporaryAppStates() {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        SP.lockTaskUnhiddenApps?.let { parsePackageNames(it) }?.forEach {
            try { dpm.setApplicationHidden(dar, it, true) } catch (e: Exception) { e.printStackTrace() }
        }
        if (Build.VERSION.SDK_INT >= 24) SP.lockTaskUnsuspendedApps?.let { parsePackageNames(it) }?.let {
            if (it.isNotEmpty()) try {
                dpm.setPackagesSuspended(dar, it.toTypedArray(), true)
            } catch (e: Exception) { e.printStackTrace() }
        }
        SP.lockTaskUnhiddenApps = null
        SP.lockTaskUnsuspendedApps = null
    }

    /** Restore state left over from a lock task session that ended without cleanup. */
    fun restoreStaleTemporaryAppStates(context: Context) {
        if (Build.VERSION.SDK_INT < 28) return
        if (!hasTemporaryAppStates() && !SP.lockTaskHomeInterception) return
        val am = context.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return
        if (hasTemporaryAppStates()) restoreTemporaryAppStates()
        if (SP.lockTaskHomeInterception) disableHomeInterception(context)
    }
}
