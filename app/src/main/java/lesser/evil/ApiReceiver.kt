package lesser.evil

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresApi

class ApiReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val requestKey = intent.getStringExtra("key")
        var log = "OwnDroid API request received. action: ${intent.action}"
        val key = SP.apiKeyHash
        if(!key.isNullOrEmpty() && key == requestKey?.hash()) {
            val app = intent.getStringExtra("package")
            val permission = intent.getStringExtra("permission")
            val restriction = intent.getStringExtra("restriction")
            if (!app.isNullOrEmpty()) log += "\npackage: $app"
            if (!permission.isNullOrEmpty()) log += "\npermission: $permission"
            try {
                @SuppressWarnings("NewApi")
                when(intent.action?.removePrefix("lesser.evil.action.")) {
                    // These change the same blocks the user profile can own, so each one records
                    // the change: it belongs to the admin API caller, never to the user profile
                    "HIDE" -> setHidden(app, true)
                    "UNHIDE" -> setHidden(app, false)
                    "SUSPEND" -> setSuspended(app, true)
                    "UNSUSPEND" -> setSuspended(app, false)
                    "ADD_USER_RESTRICTION" -> setRestriction(restriction, true)
                    "CLEAR_USER_RESTRICTION" -> setRestriction(restriction, false)
                    "SET_PERMISSION_DEFAULT" -> {
                        Privilege.DPM.setPermissionGrantState(
                            Privilege.DAR, app!!, permission!!,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                        )
                    }
                    "SET_PERMISSION_GRANTED" -> {
                        Privilege.DPM.setPermissionGrantState(
                            Privilege.DAR, app!!, permission!!,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                        )
                    }
                    "SET_PERMISSION_DENIED" -> {
                        Privilege.DPM.setPermissionGrantState(
                            Privilege.DAR, app!!, permission!!,
                            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                        )
                    }
                    "LOCK" -> { Privilege.DPM.lockNow() }
                    "REBOOT" -> { Privilege.DPM.reboot(Privilege.DAR) }
                    "SET_CAMERA_DISABLED" -> {
                        Privilege.DPM.setCameraDisabled(Privilege.DAR, true)
                    }
                    "SET_CAMERA_ENABLED" -> {
                        Privilege.DPM.setCameraDisabled(Privilege.DAR, false)
                    }
                    "SET_USB_DISABLED" -> {
                        Privilege.DPM.isUsbDataSignalingEnabled = false
                    }
                    "SET_USB_ENABLED" -> {
                        Privilege.DPM.isUsbDataSignalingEnabled = true
                    }
                    "SET_SCREEN_CAPTURE_DISABLED" -> {
                        Privilege.DPM.setScreenCaptureDisabled(Privilege.DAR, true)
                    }
                    "SET_SCREEN_CAPTURE_ENABLED" -> {
                        Privilege.DPM.setScreenCaptureDisabled(Privilege.DAR, false)
                    }
                    else -> {
                        log += "\nInvalid action"
                    }
                }
            } catch(e: Exception) {
                e.printStackTrace()
                val message = (e::class.qualifiedName ?: "Exception") + ": " + (e.message ?: "")
                log += "\n$message"
            }
        } else {
            log += "\nUnauthorized"
        }
        Log.d(TAG, log)
    }
    private fun setHidden(app: String?, hidden: Boolean) {
        if (app.isNullOrEmpty()) return
        Privilege.DPM.setApplicationHidden(Privilege.DAR, app, hidden)
        BlockOwnership.recordExternalChange(BlockKind.Hidden, app, hidden)
    }
    @RequiresApi(24)
    private fun setSuspended(app: String?, suspended: Boolean) {
        if (app.isNullOrEmpty()) return
        Privilege.DPM.setPackagesSuspended(Privilege.DAR, arrayOf(app), suspended)
        BlockOwnership.recordExternalChange(BlockKind.Suspended, app, suspended)
    }
    private fun setRestriction(restriction: String?, set: Boolean) {
        if (restriction.isNullOrEmpty()) return
        if (set) {
            Privilege.DPM.addUserRestriction(Privilege.DAR, restriction)
        } else {
            Privilege.DPM.clearUserRestriction(Privilege.DAR, restriction)
        }
        BlockOwnership.recordExternalChange(BlockKind.UserRestriction, restriction, set)
    }
    companion object {
        private const val TAG = "API"
    }
}
