package com.bintianqi.owndroid

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
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
        if (showNotification) {
            dpm.setLockTaskFeatures(
                dar,
                dpm.getLockTaskFeatures(dar) or
                        DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_HOME
            )
        }
        liftTemporaryAppStates(dpm.getLockTaskPackages(dar).toList())
        val intent = if (activity.isNotEmpty()) {
            Intent().setComponent(ComponentName(packageName, activity))
        } else context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            restoreTemporaryAppStates()
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
            )
        }
        return true
    }

    /** Force the device out of lock task mode by resetting the lock task packages. */
    @RequiresApi(28)
    fun forceStopLockTask() {
        val features = Privilege.DPM.getLockTaskFeatures(Privilege.DAR)
        val packages = Privilege.DPM.getLockTaskPackages(Privilege.DAR)
        Privilege.DPM.setLockTaskPackages(Privilege.DAR, arrayOf())
        Privilege.DPM.setLockTaskPackages(Privilege.DAR, packages)
        Privilege.DPM.setLockTaskFeatures(Privilege.DAR, features)
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

    /** Restore app states left over from a lock task session that ended without cleanup. */
    fun restoreStaleTemporaryAppStates(context: Context) {
        if (Build.VERSION.SDK_INT < 28 || !hasTemporaryAppStates()) return
        val am = context.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return
        restoreTemporaryAppStates()
    }
}
