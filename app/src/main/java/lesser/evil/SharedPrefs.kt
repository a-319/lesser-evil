package lesser.evil

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class SharedPrefs(context: Context) {
    val sharedPrefs: SharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
    var managedProfileActivated by BooleanSharedPref("managed_profile_activated")
    var dhizuku by BooleanSharedPref("dhizuku_mode")
    var isDefaultAffiliationIdSet by BooleanSharedPref("default_affiliation_id_set")
    var displayDangerousFeatures by BooleanSharedPref("display_dangerous_features")
    var apiKeyHash by StringSharedPref("api_key_hash")
    var materialYou by BooleanSharedPref("theme.material_you", Build.VERSION.SDK_INT >= 31)
    /** -1: follow system, 0: off, 1: on */
    var darkTheme by IntSharedPref("theme.dark", -1)
    var blackTheme by BooleanSharedPref("theme.black")
    var lockPasswordHash by StringSharedPref("lock.password.sha256")
    var biometricsUnlock by BooleanSharedPref("lock.biometrics")
    var lockWhenLeaving by BooleanSharedPref("lock.onleave")
    var lockPasswordFailedAttempts by IntSharedPref("lock.password.failed_attempts")
    var lockPasswordLockoutUntil by LongSharedPref("lock.password.lockout_until")
    var applicationsListView by BooleanSharedPref("applications.list_view", true)
    var shortcuts by BooleanSharedPref("shortcuts")
    var dhizukuServer by BooleanSharedPref("dhizuku_server")
    var notifications by StringSharedPref("notifications")
    var shortcutKey by StringSharedPref("shortcut_key")
    /**
     * Where a release before per-switch snapshots kept the state to restore when a metered data
     * or always-on VPN policy was released. Only read, to migrate a switch that was left on
     * across the upgrade; cleared once used.
     */
    var legacyPolicyToggleMddBackup by StringSharedPref("policy_toggles.mdd_backup")
    var legacyPolicyToggleVpnBackup by StringSharedPref("policy_toggles.vpn_backup")
    /**
     * Blocks created by the user profile, per function - newline separated.
     * Only these may be lifted by the user profile; everything else belongs to the admin.
     */
    var userOwnedHidden by StringSharedPref("user_owned.hidden")
    var userOwnedSuspended by StringSharedPref("user_owned.suspended")
    var userOwnedUninstallBlocked by StringSharedPref("user_owned.uninstall_blocked")
    var userOwnedUcd by StringSharedPref("user_owned.user_control_disabled")
    var userOwnedMdd by StringSharedPref("user_owned.metered_data_disabled")
    var userOwnedRestrictions by StringSharedPref("user_owned.user_restrictions")
    var lockTaskProfiles by StringSharedPref("lock_task.profiles")
    var lockTaskUnsuspendedApps by StringSharedPref("lock_task.unsuspended_apps")
    var lockTaskUnhiddenApps by StringSharedPref("lock_task.unhidden_apps")
    var lockTaskHomeInterception by BooleanSharedPref("lock_task.home_interception")
    var lockTaskNavButtonsSwapped by BooleanSharedPref("lock_task.nav_buttons_swapped")
}

private class BooleanSharedPref(val key: String, val defValue: Boolean = false): ReadWriteProperty<SharedPrefs, Boolean> {
    override fun getValue(thisRef: SharedPrefs, property: KProperty<*>): Boolean =
        thisRef.sharedPrefs.getBoolean(key, defValue)
    override fun setValue(thisRef: SharedPrefs, property: KProperty<*>, value: Boolean) =
        thisRef.sharedPrefs.edit(true) { putBoolean(key, value) }
}

private class StringSharedPref(val key: String): ReadWriteProperty<SharedPrefs, String?> {
    override fun getValue(thisRef: SharedPrefs, property: KProperty<*>): String? =
        thisRef.sharedPrefs.getString(key, null)
    override fun setValue(thisRef: SharedPrefs, property: KProperty<*>, value: String?) =
        thisRef.sharedPrefs.edit(true) { putString(key, value) }
}

private class IntSharedPref(val key: String, val defValue: Int = 0): ReadWriteProperty<SharedPrefs, Int> {
    override fun getValue(thisRef: SharedPrefs, property: KProperty<*>): Int =
        thisRef.sharedPrefs.getInt(key, defValue)
    override fun setValue(thisRef: SharedPrefs, property: KProperty<*>, value: Int) =
        thisRef.sharedPrefs.edit(true) { putInt(key, value) }
}

private class LongSharedPref(val key: String, val defValue: Long = 0L): ReadWriteProperty<SharedPrefs, Long> {
    override fun getValue(thisRef: SharedPrefs, property: KProperty<*>): Long =
        thisRef.sharedPrefs.getLong(key, defValue)
    override fun setValue(thisRef: SharedPrefs, property: KProperty<*>, value: Long) =
        thisRef.sharedPrefs.edit(true) { putLong(key, value) }
}
