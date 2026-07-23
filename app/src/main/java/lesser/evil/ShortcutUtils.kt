package lesser.evil

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import lesser.evil.dpm.UserOperationType

object ShortcutUtils {
    fun setAllShortcuts(context: Context, enabled: Boolean) {
        if (enabled) {
            setShortcutKey()
            val list = listOf(
                createShortcut(context, MyShortcut.Lock, true),
                createShortcut(context, MyShortcut.DisableCamera,
                    !Privilege.DPM.getCameraDisabled(Privilege.DAR)),
                createShortcut(context, MyShortcut.Mute,
                    !Privilege.DPM.isMasterVolumeMuted(Privilege.DAR))
            )
            ShortcutManagerCompat.setDynamicShortcuts(context, list)
        } else {
            ShortcutManagerCompat.removeDynamicShortcuts(context, MyShortcut.entries.map { it.id })
        }
    }
    fun setShortcut(context: Context, shortcut: MyShortcut, state: Boolean) {
        setShortcutKey()
        ShortcutManagerCompat.pushDynamicShortcut(
            context, createShortcut(context, shortcut, state)
        )
    }
    private fun createShortcut(
        context: Context, shortcut: MyShortcut, state: Boolean
    ): ShortcutInfoCompat {
        val icon = IconCompat.createWithResource(
            context,
            if (!state && shortcut.iconDisable != null) shortcut.iconDisable else shortcut.iconEnable
        )
        return ShortcutInfoCompat.Builder(context, shortcut.id)
            .setIcon(icon)
            .setShortLabel(context.getText(
                if (!state && shortcut.labelDisable != null) shortcut.labelDisable else shortcut.labelEnable
            ))
            .setIntent(
                Intent(context, ShortcutsReceiverActivity::class.java)
                    .setAction("lesser.evil.action.${shortcut.id}")
                    .putExtra("key", SP.shortcutKey)
            )
            .build()
    }
    /** @param state If true, set the user restriction */
    fun createUserRestrictionShortcut(context: Context, id: String, state: Boolean): ShortcutInfoCompat {
        val restriction = UserRestrictionsRepository.findRestrictionById(id)
        val label = context.getString(if (state) R.string.disable else R.string.enable) + " " +
                context.getString(restriction.name)
        setShortcutKey()
        return ShortcutInfoCompat.Builder(context, "USER_RESTRICTION-$id")
            .setIcon(IconCompat.createWithResource(context, restriction.icon))
            .setShortLabel(label)
            .setIntent(
                Intent(context, ShortcutsReceiverActivity::class.java)
                    .setAction("lesser.evil.action.USER_RESTRICTION")
                    .putExtra("restriction", id)
                    .putExtra("state", state)
                    .putExtra("key", SP.shortcutKey)
            )
            .build()
    }
    fun setUserRestrictionShortcut(context: Context, id: String, state: Boolean): Boolean {
        val shortcut = createUserRestrictionShortcut(context, id, state)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
    fun updateUserRestrictionShortcut(context: Context, id: String, state: Boolean, checkExist: Boolean) {
        if (checkExist) {
            val shortcuts = ShortcutManagerCompat.getShortcuts(
                context, ShortcutManagerCompat.FLAG_MATCH_PINNED
            )
            if (shortcuts.find { it.id == "USER_RESTRICTION-$id" } == null) return
        }
        val shortcut = createUserRestrictionShortcut(context, id, state)
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
    }
    fun buildUserOperationShortcut(
        context: Context, type: UserOperationType, serial: Int
    ): ShortcutInfoCompat {
        setShortcutKey()
        val icon = when (type) {
            UserOperationType.Start, UserOperationType.Switch -> R.drawable.person_fill0
            UserOperationType.Stop -> R.drawable.person_off
            else -> R.drawable.person_fill0
        }
        val text = when (type) {
            UserOperationType.Start -> R.string.start_user_n
            UserOperationType.Switch -> R.string.switch_to_user_n
            UserOperationType.Stop -> R.string.stop_user_n
            else -> R.string.place_holder
        }
        return ShortcutInfoCompat.Builder(context, "USER_OPERATION-${type.name}-$serial")
            .setIcon(IconCompat.createWithResource(context, icon))
            .setShortLabel(context.getString(text, serial))
            .setIntent(
                Intent(context, ShortcutsReceiverActivity::class.java)
                    .setAction("lesser.evil.action.USER_OPERATION")
                    .putExtra("operation", type.name)
                    .putExtra("serial", serial)
                    .putExtra("key", SP.shortcutKey)
            )
            .build()
    }
    fun setUserOperationShortcut(context: Context, type: UserOperationType, serial: Int): Boolean {
        val shortcut = buildUserOperationShortcut(context, type, serial)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
    fun disableUserOperationShortcut(context: Context, serial: Int) {
        val shortcuts = UserOperationType.entries.map {
            "USER_OPERATION-${it.name}-$serial"
        }
        ShortcutManagerCompat.disableShortcuts(
            context, shortcuts, context.getString(R.string.user_removed)
        )
    }
    fun createPolicyToggleShortcutInfo(
        context: Context, id: Int, name: String, enabled: Boolean
    ): ShortcutInfoCompat {
        setShortcutKey()
        val icon = if (enabled) R.drawable.toggle_on_fill0 else R.drawable.toggle_off_fill0
        return ShortcutInfoCompat.Builder(context, "POLICY_TOGGLE-$id")
            .setIcon(IconCompat.createWithResource(context, icon))
            .setShortLabel(name)
            .setIntent(
                Intent(context, ShortcutsReceiverActivity::class.java)
                    .setAction("lesser.evil.action.POLICY_TOGGLE")
                    .putExtra("id", id)
                    .putExtra("key", SP.shortcutKey)
            )
            .build()
    }
    fun buildLockTaskProfileShortcut(context: Context, profile: LockTaskProfile): ShortcutInfoCompat {
        setShortcutKey()
        // The shortcut takes the icon and label of the profile's main app
        var label = profile.name.ifBlank { context.getString(R.string.lock_task_mode) }
        var icon = IconCompat.createWithResource(context, R.drawable.lock_fill0)
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(profile.packageName, getInstalledAppsFlags)
            label = info.loadLabel(pm).toString()
            icon = IconCompat.createWithBitmap(info.loadIcon(pm).toBitmap())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ShortcutInfoCompat.Builder(context, "LOCK_TASK_PROFILE-${profile.id}")
            .setIcon(icon)
            .setShortLabel(label)
            .setIntent(
                Intent(context, ShortcutsReceiverActivity::class.java)
                    .setAction("lesser.evil.action.LOCK_TASK_PROFILE")
                    .putExtra("profile", profile.id)
                    .putExtra("key", SP.shortcutKey)
            )
            .build()
    }
    fun setPolicyToggleShortcut(context: Context, id: Int, name: String, enabled: Boolean): Boolean {
        val shortcut = createPolicyToggleShortcutInfo(context, id, name, enabled)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
    fun updatePolicyToggleShortcut(context: Context, id: Int, name: String, enabled: Boolean) {
        val shortcuts = ShortcutManagerCompat.getShortcuts(
            context, ShortcutManagerCompat.FLAG_MATCH_PINNED
        )
        if (shortcuts.find { it.id == "POLICY_TOGGLE-$id" } == null) return
        val shortcut = createPolicyToggleShortcutInfo(context, id, name, enabled)
        ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
    }
    fun disablePolicyToggleShortcut(context: Context, id: Int) {
        ShortcutManagerCompat.disableShortcuts(
            context, listOf("POLICY_TOGGLE-$id"), context.getString(R.string.switch_removed)
        )
    }
    fun setLockTaskProfileShortcut(context: Context, profile: LockTaskProfile): Boolean {
        val shortcut = buildLockTaskProfileShortcut(context, profile)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
    fun setShortcutKey() {
        if (SP.shortcutKey.isNullOrEmpty()) {
            SP.shortcutKey = generateBase64Key(10)
        }
    }
}

enum class MyShortcut(
    val id: String, val labelEnable: Int, val labelDisable: Int? = null, val iconEnable: Int,
    val iconDisable: Int? = null
) {
    Lock("LOCK", R.string.lock_screen, iconEnable = R.drawable.lock_fill0),
    DisableCamera("DISABLE_CAMERA", R.string.disable_cam, R.string.enable_camera,
        R.drawable.no_photography_fill0, R.drawable.photo_camera_fill0),
    Mute("MUTE", R.string.mute, R.string.unmute, R.drawable.volume_off_fill0, R.drawable.volume_up_fill0)
}