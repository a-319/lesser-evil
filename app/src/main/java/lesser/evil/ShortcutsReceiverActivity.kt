package lesser.evil

import android.app.Activity
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
                        if (state) {
                            Privilege.DPM.addUserRestriction(Privilege.DAR, id)
                        } else {
                            Privilege.DPM.clearUserRestriction(Privilege.DAR, id)
                        }
                        ShortcutUtils.updateUserRestrictionShortcut(this, id, !state, false)
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
                        if (id == -1) return
                        val repo = (applicationContext as MyApplication).myRepo
                        val toggle = repo.getPolicyToggle(id)
                        if (toggle == null) {
                            showOperationResultToast(false)
                            return
                        }
                        // Shortcuts bypass the app lock, so only switches available to the
                        // user profile may be flipped this way while a password is set
                        if (!toggle.userAllowed && !SP.lockPasswordHash.isNullOrEmpty()) {
                            showOperationResultToast(false)
                            return
                        }
                        val newState = !toggle.enabled
                        val result = PolicyToggleManager.apply(this, toggle.policies, newState)
                        repo.setPolicyToggleEnabled(id, newState)
                        ShortcutUtils.updatePolicyToggleShortcut(this, id, toggle.name, newState)
                        if (!result) {
                            showOperationResultToast(false)
                            return
                        }
                    }
                }
                Log.d(TAG, "Received intent: $action")
                showOperationResultToast(true)
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
