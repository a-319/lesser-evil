package lesser.evil

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single policy controlled by a mode switch. A policy only describes the state its target is
 * put in while the switch is on: [blockedWhenOn] true blocks the target, false releases it.
 * Turning the switch off does not dictate a state - it restores whatever was configured in the
 * app before the switch was turned on, from the snapshot [PolicyToggleManager.captureBackup] took.
 */
@Serializable
sealed class TogglePolicy {
    // Serialized under the old name so switches configured before the rename keep working:
    // the meaning of the on state is unchanged, only the off state became a restore.
    @SerialName("enforceWhenOn")
    abstract val blockedWhenOn: Boolean

    @Serializable
    @SerialName("user_restriction")
    data class UserRestriction(
        val restriction: String,
        @SerialName("enforceWhenOn") override val blockedWhenOn: Boolean
    ) : TogglePolicy()

    @Serializable
    @SerialName("hide_app")
    data class HideApp(
        val packageName: String,
        @SerialName("enforceWhenOn") override val blockedWhenOn: Boolean
    ) : TogglePolicy()

    @Serializable
    @SerialName("suspend_app")
    data class SuspendApp(
        val packageName: String,
        @SerialName("enforceWhenOn") override val blockedWhenOn: Boolean
    ) : TogglePolicy()

    @Serializable
    @SerialName("always_on_vpn")
    data class AlwaysOnVpn(
        val packageName: String, val lockdown: Boolean,
        @SerialName("enforceWhenOn") override val blockedWhenOn: Boolean
    ) : TogglePolicy()

    /** While blocked, metered data is disabled for every app except [excludedPackages]. */
    @Serializable
    @SerialName("block_metered_data")
    data class BlockMeteredData(
        val excludedPackages: List<String>,
        @SerialName("enforceWhenOn") override val blockedWhenOn: Boolean
    ) : TogglePolicy()

    val requiresApi get() = when (this) {
        is UserRestriction -> 21
        is HideApp -> 21
        is SuspendApp -> 24
        is AlwaysOnVpn -> 24
        is BlockMeteredData -> 28
    }
}

class PolicyToggle(
    val id: Int, val name: String, val enabled: Boolean, val userAllowed: Boolean,
    val policies: List<TogglePolicy>, val backup: String = ""
)

object PolicyToggleManager {
    val json = Json { ignoreUnknownKeys = true }

    fun encodePolicies(policies: List<TogglePolicy>): String = json.encodeToString(policies)

    fun decodePolicies(data: String): List<TogglePolicy> = try {
        json.decodeFromString(data)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }

    /** Identifies a policy's target, so a snapshot survives the policies being reordered */
    private fun backupKey(policy: TogglePolicy): String = when (policy) {
        is TogglePolicy.UserRestriction -> "restriction:${policy.restriction}"
        is TogglePolicy.HideApp -> "hide:${policy.packageName}"
        is TogglePolicy.SuspendApp -> "suspend:${policy.packageName}"
        is TogglePolicy.AlwaysOnVpn -> "vpn"
        is TogglePolicy.BlockMeteredData -> "mdd"
    }

    /**
     * Records the state every policy target is in right now, to be restored when the switch is
     * turned off. Call this before turning a switch on.
     */
    fun captureBackup(policies: List<TogglePolicy>): String {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        val snapshot = mutableMapOf<String, String>()
        policies.forEach { policy ->
            try {
                val value = when (policy) {
                    is TogglePolicy.UserRestriction ->
                        if (VERSION.SDK_INT >= 24 &&
                            dpm.getUserRestrictions(dar).getBoolean(policy.restriction)) "1" else "0"
                    is TogglePolicy.HideApp ->
                        if (dpm.isApplicationHidden(dar, policy.packageName)) "1" else "0"
                    is TogglePolicy.SuspendApp ->
                        if (VERSION.SDK_INT >= 24 &&
                            dpm.isPackageSuspended(dar, policy.packageName)) "1" else "0"
                    is TogglePolicy.AlwaysOnVpn -> if (VERSION.SDK_INT < 24) "" else {
                        val current = dpm.getAlwaysOnVpnPackage(dar)
                        val lockdown = VERSION.SDK_INT >= 29 && dpm.isAlwaysOnVpnLockdownEnabled(dar)
                        if (current == null) "" else "$current|${if (lockdown) 1 else 0}"
                    }
                    is TogglePolicy.BlockMeteredData ->
                        if (VERSION.SDK_INT >= 28)
                            dpm.getMeteredDataDisabledPackages(dar).joinToString("\n") else ""
                }
                snapshot[backupKey(policy)] = value
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return json.encodeToString(snapshot)
    }

    private fun decodeBackup(data: String): Map<String, String> = try {
        if (data.isEmpty()) emptyMap() else json.decodeFromString(data)
    } catch (e: Exception) {
        e.printStackTrace()
        emptyMap()
    }

    /**
     * Turns a switch on ([state] true), putting every policy target into the state its policy
     * describes, or off, restoring the targets from [backup].
     * @return true if every policy was applied successfully
     */
    fun apply(
        context: Context, policies: List<TogglePolicy>, state: Boolean, backup: String = ""
    ): Boolean {
        val snapshot = if (state) emptyMap() else decodeBackup(backup)
        var success = true
        policies.forEach { policy ->
            try {
                val applied = if (state) {
                    applyPolicy(context, policy, policy.blockedWhenOn)
                } else {
                    // Without a snapshot the switch was never turned on through this path,
                    // so there is nothing to restore and the current state is left alone
                    val saved = snapshot[backupKey(policy)]
                    if (saved == null) true else restorePolicy(context, policy, saved)
                }
                if (!applied) success = false
            } catch (e: Exception) {
                e.printStackTrace()
                success = false
            }
        }
        return success
    }

    private fun applyPolicy(context: Context, policy: TogglePolicy, blocked: Boolean): Boolean {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        when (policy) {
            is TogglePolicy.UserRestriction ->
                if (blocked) dpm.addUserRestriction(dar, policy.restriction)
                else dpm.clearUserRestriction(dar, policy.restriction)
            is TogglePolicy.HideApp ->
                return dpm.setApplicationHidden(dar, policy.packageName, blocked) ||
                        dpm.isApplicationHidden(dar, policy.packageName) == blocked
            is TogglePolicy.SuspendApp -> {
                if (VERSION.SDK_INT < 24) return false
                return dpm.setPackagesSuspended(
                    dar, arrayOf(policy.packageName), blocked
                ).isEmpty()
            }
            is TogglePolicy.AlwaysOnVpn -> {
                if (VERSION.SDK_INT < 24) return false
                if (blocked) dpm.setAlwaysOnVpnPackage(dar, policy.packageName, policy.lockdown)
                else dpm.setAlwaysOnVpnPackage(dar, null, false)
            }
            is TogglePolicy.BlockMeteredData -> {
                if (VERSION.SDK_INT < 28) return false
                dpm.setMeteredDataDisabledPackages(
                    dar,
                    if (blocked) {
                        context.packageManager.getInstalledApplications(0).map { it.packageName }
                            .filter { it !in policy.excludedPackages && it != context.packageName }
                    } else emptyList()
                )
            }
        }
        return true
    }

    /** Puts [policy]'s target back into the state [saved] recorded before the switch was turned on */
    private fun restorePolicy(context: Context, policy: TogglePolicy, saved: String): Boolean {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        when (policy) {
            is TogglePolicy.UserRestriction, is TogglePolicy.HideApp, is TogglePolicy.SuspendApp ->
                return applyPolicy(context, policy, saved == "1")
            is TogglePolicy.AlwaysOnVpn -> {
                if (VERSION.SDK_INT < 24) return false
                try {
                    if (saved.isEmpty()) {
                        dpm.setAlwaysOnVpnPackage(dar, null, false)
                    } else {
                        val parts = saved.split('|')
                        dpm.setAlwaysOnVpnPackage(dar, parts[0], parts.getOrNull(1) == "1")
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                    // The app that was configured before is gone, so there is nothing to go back to
                    dpm.setAlwaysOnVpnPackage(dar, null, false)
                }
            }
            is TogglePolicy.BlockMeteredData -> {
                if (VERSION.SDK_INT < 28) return false
                dpm.setMeteredDataDisabledPackages(
                    dar, saved.split('\n').filter { it.isNotEmpty() }
                )
            }
        }
        return true
    }
}
