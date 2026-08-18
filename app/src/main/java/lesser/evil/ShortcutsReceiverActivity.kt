package lesser.evil

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import lesser.evil.dpm.UserOperationType
import lesser.evil.dpm.doUserOperationWithContext

class ShortcutsReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val action = intent.action?.removePrefix("lesser.evil.action.")
            val key = SP.shortcutKey
            val requestKey = intent?.getStringExtra("key")
            if (action != null && key != null && requestKey == key) {
                var success = true
                when (action) {
                    "LOCK" -> Privilege.DPM.lockNow()
                    "DISABLE_CAMERA" -> {
                        val state = Privilege.DPM.getCameraDisabled(Privilege.DAR)
                        Privilege.DPM.setCameraDisabled(Privilege.DAR, !state)
                        ShortcutUtils.setShortcut(this, MyShortcut.DisableCamera, state)
                    }
                    "MUTE" -> {
                        val state = Privilege.DPM.isMasterVolumeMuted(Privilege.DAR)
                        Privilege.DPM.setMasterVolumeMuted(Privilege.DAR, !state)
                        ShortcutUtils.setShortcut(this, MyShortcut.Mute, state)
                    }
                    "USER_RESTRICTION" -> {
                        val state = intent?.getBooleanExtra("state", false)
                        val id = intent?.getStringExtra("restriction")
                        if (state == null || id == null) return
                        val toggles = (applicationContext as MyApplication).myRepo.getPolicyToggles()
                        if (PolicyToggleManager.switchControlled(
                                toggles, BlockKind.UserRestriction, id
                        )) {
                            // A mode switch owns this restriction's state. Changing it here would
                            // fight the switch, and would rewrite ownership on the way past
                            success = false
                        } else {
                            if (state) {
                                Privilege.DPM.addUserRestriction(Privilege.DAR, id)
                            } else {
                                Privilege.DPM.clearUserRestriction(Privilege.DAR, id)
                            }
                            // A shortcut runs outside any session and bypasses the app lock, so it
                            // never claims ownership for the user profile - it only keeps the
                            // record in step so the restriction does not look user-owned later
                            BlockOwnership.record(
                                BlockKind.UserRestriction, listOf(id), state, false
                            )
                            ShortcutUtils.updateUserRestrictionShortcut(this, id, !state, false)
                        }
                    }
                    "USER_OPERATION" -> {
                        val typeName = intent.getStringExtra("operation") ?: return
                        val type = UserOperationType.valueOf(typeName)
                        val serial = intent.getIntExtra("serial", -1)
                        if (serial == -1) return
                        doUserOperationWithContext(this, type, serial, false)
                    }
                    "POLICY_TOGGLE" -> {
                        val id = intent.getIntExtra("id", -1)
                        val repo = (applicationContext as MyApplication).myRepo
                        val toggle = if (id == -1) null else repo.getPolicyToggle(id)
                        // Shortcuts bypass the app lock, so only switches available to the user
                        // profile may be flipped this way while a password is set. A switch
                        // targeting an app lock task mode has lifted is refused too: the
                        // restoration at the end of the session would undo the flip anyway
                        success = if (toggle == null ||
                            (!toggle.userAllowed && !SP.lockPasswordHash.isNullOrEmpty()) ||
                            PolicyToggleManager.touchesLockTaskLift(toggle.policies)) {
                            false
                        } else {
                            var persisted = toggle.enabled
                            val applied = if (!toggle.enabled) {
                                // Store the snapshot before applying, so even a partly applied
                                // switch can be undone
                                repo.setPolicyToggleEnabled(
                                    id, true, PolicyToggleManager.captureBackup(toggle.policies)
                                )
                                persisted = true
                                PolicyToggleManager.apply(this, toggle.policies, true)
                            } else {
                                // Give up the snapshot only once everything was restored
                                val restored = PolicyToggleManager.apply(
                                    this, toggle.policies, false, toggle.backup
                                )
                                if (restored) {
                                    repo.setPolicyToggleEnabled(id, false, "")
                                    persisted = false
                                }
                                restored
                            }
                            ShortcutUtils.updatePolicyToggleShortcut(this, id, toggle.name, persisted)
                            applied
                        }
                    }
                    "LOCK_TASK_PROFILE" -> {
                        success = if (Build.VERSION.SDK_INT >= 28) {
                            val id = intent.getIntExtra("profile", -1)
                            val profile = LockTaskUtils.getProfiles().find { it.id == id }
                            profile != null && LockTaskUtils.startProfile(this, profile)
                        } else false
                    }
                }
                Log.d(TAG, "Received intent: $action")
                showOperationResultToast(success)
            } else {
                showOperationResultToast(false)
            }
        } catch(e: Exception) {
            e.printStackTrace()
        } finally {
            finish()
        }
    }
    companion object {
        private const val TAG = "ShortcutsReceiver"
    }
}
