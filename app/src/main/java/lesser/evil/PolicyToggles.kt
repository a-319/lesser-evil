package lesser.evil

import android.content.Context
import android.os.Build.VERSION
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single policy controlled by a mode switch.
 *
 * [enforceWhenOn] - if true, the policy is enforced while the switch is on and released
 * while it is off. If false, the behavior is reversed.
 */
@Serializable
sealed class TogglePolicy {
    abstract val enforceWhenOn: Boolean

    @Serializable
    @SerialName("user_restriction")
    data class UserRestriction(
        val restriction: String, override val enforceWhenOn: Boolean
    ) : TogglePolicy()

    @Serializable
    @SerialName("hide_app")
    data class HideApp(
        val packageName: String, override val enforceWhenOn: Boolean
    ) : TogglePolicy()

    @Serializable
    @SerialName("suspend_app")
    data class SuspendApp(
        val packageName: String, override val enforceWhenOn: Boolean
    ) : TogglePolicy()

    @Serializable
    @SerialName("always_on_vpn")
    data class AlwaysOnVpn(
        val packageName: String, val lockdown: Boolean, override val enforceWhenOn: Boolean
    ) : TogglePolicy()

    /** While enforced, metered data is disabled for every app except [excludedPackages]. */
    @Serializable
    @SerialName("block_metered_data")
    data class BlockMeteredData(
        val excludedPackages: List<String>, override val enforceWhenOn: Boolean
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
    val id: Int, val name: String, val enabled: Boolean, val policies: List<TogglePolicy>
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

    /**
     * Apply all [policies] for the given switch [state].
     * @return true if every policy was applied successfully
     */
    fun apply(context: Context, policies: List<TogglePolicy>, state: Boolean): Boolean {
        var success = true
        policies.forEach { policy ->
            try {
                if (!applyPolicy(context, policy, policy.enforceWhenOn == state)) success = false
            } catch (e: Exception) {
                e.printStackTrace()
                success = false
            }
        }
        return success
    }

    private fun applyPolicy(context: Context, policy: TogglePolicy, enforce: Boolean): Boolean {
        val dpm = Privilege.DPM
        val dar = Privilege.DAR
        when (policy) {
            is TogglePolicy.UserRestriction ->
                if (enforce) dpm.addUserRestriction(dar, policy.restriction)
                else dpm.clearUserRestriction(dar, policy.restriction)
            is TogglePolicy.HideApp ->
                return dpm.setApplicationHidden(dar, policy.packageName, enforce) ||
                        dpm.isApplicationHidden(dar, policy.packageName) == enforce
            is TogglePolicy.SuspendApp -> {
                if (VERSION.SDK_INT < 24) return false
                return dpm.setPackagesSuspended(
                    dar, arrayOf(policy.packageName), enforce
                ).isEmpty()
            }
            is TogglePolicy.AlwaysOnVpn -> {
                if (VERSION.SDK_INT < 24) return false
                if (enforce) dpm.setAlwaysOnVpnPackage(dar, policy.packageName, policy.lockdown)
                else dpm.setAlwaysOnVpnPackage(dar, null, false)
            }
            is TogglePolicy.BlockMeteredData -> {
                if (VERSION.SDK_INT < 28) return false
                dpm.setMeteredDataDisabledPackages(
                    dar,
                    if (enforce) {
                        context.packageManager.getInstalledApplications(0).map { it.packageName }
                            .filter { it !in policy.excludedPackages && it != context.packageName }
                    } else emptyList()
                )
            }
        }
        return true
    }
}
