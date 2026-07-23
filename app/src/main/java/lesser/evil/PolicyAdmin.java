package lesser.evil;

import android.content.ComponentName;

/**
 * Supplies the admin component that is passed as the first argument to
 * {@link android.app.admin.DevicePolicyManager} policy methods.
 *
 * <p>For a device owner or profile owner this is the app's {@code DeviceAdminReceiver}
 * component. When the app only holds the {@code android.app.role.DEVICE_POLICY_MANAGEMENT}
 * role (temporary DPC, like Test DPC) it returns {@code null}, which selects the Android 14+
 * permission based (coexistence) path where access is granted by the role's
 * {@code MANAGE_DEVICE_POLICY_*} permissions instead of device/profile ownership.
 *
 * <p>This lives in Java on purpose: the method returns a <em>platform type</em> so Kotlin
 * call sites can pass it to {@code @NonNull}-annotated {@code DevicePolicyManager} parameters
 * without a compile error, while still carrying {@code null} at runtime.
 */
public final class PolicyAdmin {
    private PolicyAdmin() {}

    public static ComponentName get() {
        return Privilege.INSTANCE.policyAdmin();
    }
}
