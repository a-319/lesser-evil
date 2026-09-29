package lesser.evil

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import lesser.evil.ui.theme.OwnDroidTheme
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.aidl.IDhizukuRequestPermissionListener
import com.rosan.dhizuku.server_api.DhizukuProvider
import com.rosan.dhizuku.server_api.DhizukuService
import com.rosan.dhizuku.shared.DhizukuVariables
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

private const val TAG = "DhizukuServer"

class MyDhizukuProvider(): DhizukuProvider() {
    override fun onCreateService(client: IDhizukuClient): DhizukuService? {
        Log.d(TAG, "Creating MyDhizukuService")
        return if (SP.dhizukuServer) MyDhizukuService(context!!, MyAdminComponent, client) else null
    }
}

class MyDhizukuService(context: Context, admin: ComponentName, client: IDhizukuClient) :
    DhizukuService(context, admin, client) {
    private val repo get() = (mContext.applicationContext as MyApplication).myRepo

    /** The client that made the call being handled, found by the uid it came from. */
    private fun client(callingUid: Int): DhizukuClientInfo? {
        val pm = mContext.packageManager
        val packageInfo = try {
            pm.getPackageInfo(
                pm.getNameForUid(callingUid) ?: return null,
                if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                else PackageManager.GET_SIGNATURES
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return repo.getDhizukuClient(callingUid, getPackageSignature(packageInfo))
    }

    override fun checkCallingPermission(func: String?, callingUid: Int, callingPid: Int): Boolean {
        if (!SP.dhizukuServer) return false
        val requiredPermission = when (func) {
            "remote_transact", "remote_process" -> func
            "bind_user_service", "unbind_user_service" -> "user_service"
            "get_delegated_scopes", "set_delegated_scopes" -> "delegated_scopes"
            else -> "other"
        }
        val client = client(callingUid)
        // A grant made by a profile cannot carry the ones that hand over the identity itself:
        // running a process or a service as this app, or handing out delegated scopes, are not
        // requests that can be checked - they are ways to stop being asked
        val hasPermission = client != null && requiredPermission in client.permissions &&
                (client.actor is Actor.Admin || requiredPermission in DhizukuUserPermissions)
        Log.d(TAG, "UID $callingUid, PID $callingPid, actor: ${client?.actor}, " +
                "required permission: $requiredPermission, has permission: $hasPermission")
        return hasPermission
    }

    /**
     * Every call a client makes on a system service passes through here. An admin's grant goes
     * straight on, exactly as it always did. A profile's grant is instead read: a call this app
     * knows becomes the same gateway operation the button in the app performs, in that profile's
     * name, and anything else is refused.
     */
    override fun onRemoteTransact(
        target: IBinder?, code: Int, data: Parcel?, reply: Parcel?, flags: Int
    ): Boolean {
        val actor = client(Binder.getCallingUid())?.actor
        if (actor == null || actor is Actor.Admin || target == null || data == null) {
            return super.onRemoteTransact(target, code, data, reply, flags)
        }
        return try {
            handleForProfile(actor, target, code, data, reply, flags)
        } catch (e: Exception) {
            e.printStackTrace()
            refuse(reply)
        }
    }

    private fun handleForProfile(
        actor: Actor, target: IBinder, code: Int, data: Parcel, reply: Parcel?, flags: Int
    ): Boolean {
        val descriptor = try {
            target.interfaceDescriptor
        } catch (e: Exception) {
            null
        }
        // Only the device policy service, and only the calls below: with this identity a client
        // could otherwise reach the package manager or the activity manager just as easily
        if (descriptor != DhizukuBridge.DPM_DESCRIPTOR) return refuse(reply)
        val name = DpmTransactions.nameOf(code) ?: return refuse(reply)
        data.setDataPosition(0)
        data.enforceInterface(descriptor)
        val call = DhizukuBridge.decode(name, data)
        Log.d(TAG, "Profile call $name -> $call")
        return when (call) {
            is DhizukuBridge.Call.PassThrough -> {
                data.setDataPosition(0)
                super.onRemoteTransact(target, code, data, reply, flags)
            }
            is DhizukuBridge.Call.Block -> {
                val refused = PolicyGateway.setBlocks(actor, call.kind, call.keys, call.blocked)
                if (reply != null) {
                    reply.writeNoException()
                    when (call.reply) {
                        DhizukuBridge.Reply.Void -> Unit
                        DhizukuBridge.Reply.Bool -> reply.writeInt(if (refused.isEmpty()) 1 else 0)
                        // The real call answers with what it could not carry out, which is exactly
                        // what a refusal is
                        DhizukuBridge.Reply.StringArray ->
                            reply.writeStringArray(refused.keys.toTypedArray())
                    }
                }
                true
            }
            is DhizukuBridge.Call.Refused -> refuse(reply)
        }
    }

    /** Turns the call down the way the system turns down a caller that may not make it. */
    private fun refuse(reply: Parcel?): Boolean {
        reply?.writeException(SecurityException("lesser.evil: not allowed for this profile"))
        return true
    }

    override fun getVersionName() = "1.0"
}

class DhizukuActivity : ComponentActivity() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SP.dhizukuServer) {
            finish()
            return
        }
        val bundle = intent.extras ?: return
        val uid = bundle.getInt(DhizukuVariables.PARAM_CLIENT_UID, -1)
        if (uid == -1) return
        val binder = bundle.getBinder(DhizukuVariables.PARAM_CLIENT_REQUEST_PERMISSION_BINDER) ?: return
        val listener = IDhizukuRequestPermissionListener.Stub.asInterface(binder)
        val packageName = packageManager.getPackagesForUid(uid)?.first() ?: return
        val packageInfo = packageManager.getPackageInfo(
            packageName,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        )
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        val icon = appInfo.loadIcon(packageManager)
        val label = appInfo.loadLabel(packageManager).toString()
        /**
         * @param actor whose name the client will act in, or null to turn it down. An admin grant
         * is the whole device and so asks for the password; a grant by the user profile is only
         * what that profile may do in the app, and needs no more than the phone in hand.
         */
        fun close(actor: Actor?) {
            val clientInfo = DhizukuClientInfo(
                uid, getPackageSignature(packageInfo),
                when {
                    actor == null -> emptyList()
                    actor is Actor.Admin -> DhizukuPermissions
                    else -> DhizukuUserPermissions
                },
                actor ?: Actor.Admin
            )
            (application as MyApplication).myRepo.setDhizukuClient(clientInfo)
            finish()
            listener.onRequestPermission(
                if (actor != null) PackageManager.PERMISSION_GRANTED
                else PackageManager.PERMISSION_DENIED
            )
        }
        enableEdgeToEdge()
        val theme = ThemeSettings(SP.materialYou, SP.darkTheme, SP.blackTheme)
        setContent {
            var appLockDialog by rememberSaveable { mutableStateOf(false) }
            // With no password there is no user profile to tell apart, so there is one answer
            val hasProfiles = !SP.lockPasswordHash.isNullOrEmpty()
            OwnDroidTheme(theme) {
                if (!appLockDialog) AlertDialog(
                    icon = {
                        Image(rememberDrawablePainter(icon), null, Modifier.size(35.dp))
                    },
                    title = {
                        Text(stringResource(R.string.request_permission))
                    },
                    text = {
                        Column {
                            Text("$label\n($packageName)")
                            if (hasProfiles) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.dhizuku_grant_note),
                                    style = typography.bodyMedium
                                )
                                if (!DpmTransactions.available()) Text(
                                    stringResource(R.string.dhizuku_user_grant_unavailable),
                                    Modifier.padding(top = 8.dp),
                                    color = colorScheme.error, style = typography.bodyMedium
                                )
                            }
                        }
                    },
                    confirmButton = {
                        var time by remember { mutableIntStateOf(3) }
                        LaunchedEffect(Unit) {
                            for (i in 2 downTo 0) {
                                delay(1000)
                                time = i
                            }
                        }
                        val append = if (time > 0) " (${time}s)" else ""
                        Column {
                            TextButton({
                                if (hasProfiles) appLockDialog = true else close(Actor.Admin)
                            }, enabled = time == 0) {
                                Text(
                                    stringResource(
                                        if (hasProfiles) R.string.allow_as_admin else R.string.allow
                                    ) + append
                                )
                            }
                            if (hasProfiles) TextButton(
                                { close(Actor.Child()) }, enabled = time == 0
                            ) {
                                Text(stringResource(R.string.allow_as_user_profile) + append)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton({
                            close(null)
                        }) {
                            Text(stringResource(R.string.reject))
                        }
                    },
                    onDismissRequest = { close(null) }
                )
                else AppLockDialog({ close(Actor.Admin) }, onDismiss = { close(null) })
            }
        }
    }
}

val DhizukuPermissions = listOf("remote_transact", "remote_process", "user_service", "delegated_scopes", "other")

/**
 * What a grant made by the user profile may carry: asking this app to change something, and
 * nothing that runs code as this app. Of the five, the three left out do not ask for a function -
 * they hand over the identity that performs them, which no rule of ours could then be applied to.
 */
val DhizukuUserPermissions = listOf("remote_transact", "other")

@Serializable
data class DhizukuClientInfo(
    val uid: Int,
    val signature: String?,
    val permissions: List<String> = emptyList(),
    /**
     * Who granted this, and therefore in whose name the client's calls are made. An admin's grant
     * is the whole device, as it always was; a profile's grant reaches only what that profile may
     * reach in the app. Grants made before this existed all required the password, so they are the
     * admin's.
     */
    val actor: Actor = Actor.Admin
)