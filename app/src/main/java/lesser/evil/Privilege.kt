package lesser.evil

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Binder
import android.os.Build
import lesser.evil.dpm.binderWrapperDevicePolicyManager
import lesser.evil.dpm.dhizukuErrorStatus
import com.rosan.dhizuku.api.Dhizuku
import kotlinx.coroutines.flow.MutableStateFlow

object Privilege {
    fun initialize(context: Context) {
        if (SP.dhizuku) {
            if (Dhizuku.init(context)) try {
                if (Dhizuku.isPermissionGranted()) {
                    val dhizukuDpm = binderWrapperDevicePolicyManager(context)
                    if (dhizukuDpm != null) {
                        DPM = dhizukuDpm
                        DAR = Dhizuku.getOwnerComponent()
                        updateStatus()
                        return
                    }
                }
            } catch(e: Exception) {
                e.printStackTrace()
            }
            dhizukuErrorStatus.value = 2
        }
        DPM = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        DAR = MyAdminComponent
        updateStatus()
    }
    lateinit var DPM: DevicePolicyManager
        private set
    lateinit var DAR: ComponentName
        private set

    data class Status(
        val device: Boolean = false,
        val profile: Boolean = false,
        val dhizuku: Boolean = false,
        val work: Boolean = false,
        val org: Boolean = false,
        val affiliated: Boolean = false,
        /** Holds the android.app.role.DEVICE_POLICY_MANAGEMENT role (temporary DPC, cleared on reboot) */
        val roleHolder: Boolean = false
    ) {
        val activated = device || profile
        /** Has some form of device policy access: an owner, or the role holder (permission based) */
        val managed = device || profile || roleHolder
        val primary = Binder.getCallingUid() / 100000 == 0 // Primary user
    }
    val status = MutableStateFlow(Status())
    fun updateStatus() {
        val profile = DPM.isProfileOwnerApp(DAR.packageName)
        val work = profile && Build.VERSION.SDK_INT >= 24 && DPM.isManagedProfile(DAR)
        status.value = Status(
            device = DPM.isDeviceOwnerApp(DAR.packageName),
            profile = profile,
            dhizuku = SP.dhizuku,
            work = work,
            org = work && Build.VERSION.SDK_INT >= 30 && DPM.isOrganizationOwnedDeviceWithManagedProfile,
            affiliated = Build.VERSION.SDK_INT >= 28 && DPM.isAffiliatedUser,
            roleHolder = isRoleHolder()
        )
    }

    private fun isRoleHolder(): Boolean {
        if (SP.dhizuku || Build.VERSION.SDK_INT < 33) return false
        return try {
            DPM.devicePolicyManagementRoleHolderPackage == DAR.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Admin component for DevicePolicyManager policy calls. Returns null when the app operates
     * purely through the device policy management role, selecting the permission based path.
     * Equals [DAR] for every owner, so routing policy calls through this is behavior preserving.
     * Called from [PolicyAdmin] (Java) so the null flows to @NonNull parameters at runtime.
     */
    fun policyAdmin(): ComponentName? {
        val s = status.value
        return if (s.roleHolder && !s.device && !s.profile) null else DAR
    }
}
