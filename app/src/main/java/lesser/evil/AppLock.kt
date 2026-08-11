package lesser.evil

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricPrompt.AuthenticationCallback
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.min

private const val BASE_LOCKOUT_MILLIS = 30_000L
private const val MAX_LOCKOUT_MILLIS = 10 * 60_000L

private fun calculateRemainingSeconds(lockoutUntil: Long, now: Long = System.currentTimeMillis()): Int {
    val remainingMillis = lockoutUntil - now
    return if (remainingMillis <= 0L) 0 else ((remainingMillis + 999L) / 1_000L).toInt()
}

@Composable
fun AppLockDialog(
    onSucceed: () -> Unit, onEnterRestricted: (() -> Unit)? = null, onDismiss: () -> Unit
) = Dialog(onDismiss, DialogProperties(true, false)) {
    val context = LocalContext.current
    val fm = LocalFocusManager.current
    val fr = remember { FocusRequester() }
    var input by rememberSaveable { mutableStateOf("") }
    var isError by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var failedAttempts by rememberSaveable { mutableIntStateOf(SP.lockPasswordFailedAttempts) }
    var lockoutUntil by rememberSaveable { mutableLongStateOf(SP.lockPasswordLockoutUntil) }
    var remainingSeconds by rememberSaveable { mutableIntStateOf(calculateRemainingSeconds(lockoutUntil)) }
    val isLocked = remainingSeconds > 0
    var showCode by rememberSaveable { mutableStateOf(false) }
    var codeInput by rememberSaveable { mutableStateOf("") }
    var codeError by rememberSaveable { mutableStateOf(false) }
    // A challenge belongs to the lock screen in front of you, never to an older one
    val challenge = remember { if (AdminUnlockChallenge.enabled) AdminUnlockChallenge.regenerate() else null }

    fun succeed() {
        fm.clearFocus()
        failedAttempts = 0
        lockoutUntil = 0L
        SP.lockPasswordFailedAttempts = 0
        SP.lockPasswordLockoutUntil = 0L
        AdminUnlockChallenge.invalidate()
        onSucceed()
    }

    fun registerFailure() {
        failedAttempts += 1
        SP.lockPasswordFailedAttempts = failedAttempts
        if (failedAttempts >= 3) {
            val extraFailures = min(failedAttempts - 3, 5)
            val delayMillis = min(BASE_LOCKOUT_MILLIS * (1L shl extraFailures), MAX_LOCKOUT_MILLIS)
            lockoutUntil = System.currentTimeMillis() + delayMillis
            remainingSeconds = calculateRemainingSeconds(lockoutUntil)
            SP.lockPasswordLockoutUntil = lockoutUntil
        }
    }

    fun unlock() {
        if (isLocked) return
        if (input.hash() == SP.lockPasswordHash) succeed()
        else {
            isError = true
            registerFailure()
        }
    }

    /** The admin's signature over the challenge on screen opens this session once */
    fun unlockWithCode() {
        if (isLocked) return
        // A mistyped code the check digits caught costs nothing, it never reached the key
        if (AdminUnlockCode.decodeResponse(codeInput) == null) {
            codeError = true
            return
        }
        if (AdminUnlockChallenge.verify(codeInput)) succeed()
        else {
            codeError = true
            registerFailure()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 28 && SP.biometricsUnlock) {
            startBiometricsUnlock(context, ::succeed)
        } else {
            fr.requestFocus()
        }
    }

    LaunchedEffect(lockoutUntil) {
        if (lockoutUntil == 0L) {
            remainingSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            remainingSeconds = calculateRemainingSeconds(lockoutUntil)
            if (remainingSeconds == 0) {
                lockoutUntil = 0L
                SP.lockPasswordLockoutUntil = 0L
                break
            }
            delay(1_000L)
        }
    }

    BackHandler(onBack = onDismiss)
    Card(Modifier.pointerInput(Unit) { detectTapGestures(onTap = { fm.clearFocus() }) }, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it; isError = false }, Modifier.width(200.dp).focusRequester(fr),
                    label = { Text(stringResource(R.string.password)) }, isError = isError,
                    enabled = !isLocked,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { if (input.length >= 4) unlock() else fm.clearFocus() }),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painter = painterResource(
                                    id = if (showPassword) R.drawable.visibility_fill0 else R.drawable.visibility_off_fill0
                                ),
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    }
                )
                if (Build.VERSION.SDK_INT >= 28 && SP.biometricsUnlock) {
                    FilledTonalIconButton({ startBiometricsUnlock(context, ::succeed) }, Modifier.padding(start = 4.dp)) {
                        Icon(painterResource(R.drawable.fingerprint_fill0), null)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onEnterRestricted != null) {
                    TextButton(onEnterRestricted) {
                        Text(stringResource(R.string.enter_as_user))
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Button(::unlock, enabled = !isLocked) {
                    Text(stringResource(R.string.unlock))
                }
            }
            if (remainingSeconds > 0) {
                Text(
                    stringResource(R.string.unlock_wait_seconds, remainingSeconds),
                    Modifier.padding(top = 8.dp)
                )
            }
            if (AdminUnlockChallenge.enabled) {
                TextButton({ showCode = !showCode }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.unlock_with_code))
                }
                if (showCode) UnlockCode(
                    challenge.orEmpty(), codeInput, codeError, !isLocked,
                    { codeInput = it; codeError = false }, ::unlockWithCode
                )
            }
        }
    }
}

/**
 * The challenge to read out to the admin, and the signature they read back.
 * Both are digits only, so nothing gets lost over the phone.
 */
@Composable
private fun UnlockCode(
    challenge: String, response: String, isError: Boolean, enabled: Boolean,
    onResponseChange: (String) -> Unit, onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val typed = AdminUnlockCode.digitsOnly(response).length
    val expected = AdminUnlockCode.ResponseDigits + AdminUnlockCode.CheckDigits
    val shown = if (challenge.length == AdminUnlockCode.ChallengeDigits)
        AdminUnlockCode.formatChallenge(challenge) else ""
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.unlock_code_notes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            shown, {}, Modifier.fillMaxWidth().padding(top = 8.dp), readOnly = true,
            label = { Text(stringResource(R.string.unlock_code_challenge)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            trailingIcon = {
                IconButton({ writeClipBoard(context, shown) }) {
                    Icon(painterResource(R.drawable.content_copy_fill0), stringResource(R.string.copy))
                }
            }
        )
        OutlinedTextField(
            response, onResponseChange, Modifier.fillMaxWidth().padding(top = 4.dp),
            label = { Text(stringResource(R.string.unlock_code_response)) },
            isError = isError, enabled = enabled, maxLines = 6,
            supportingText = {
                Text(
                    if (isError) stringResource(R.string.unlock_code_refused)
                    else stringResource(R.string.unlock_code_digits, typed, expected)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Button(
            onUnlock, Modifier.fillMaxWidth().padding(top = 4.dp),
            enabled = enabled && typed == expected
        ) {
            Text(stringResource(R.string.unlock))
        }
    }
}

@RequiresApi(28)
fun startBiometricsUnlock(context: Context, onSucceed: () -> Unit) {
    context.getSystemService(FingerprintManager::class.java) ?: return
    val callback = object : AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
            super.onAuthenticationSucceeded(result)
            onSucceed()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
            super.onAuthenticationError(errorCode, errString)
            if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) context.showOperationResultToast(false)
        }
    }
    val cancel = CancellationSignal()
    BiometricPrompt.Builder(context)
        .setTitle(context.getText(R.string.unlock))
        .setNegativeButton(context.getString(R.string.cancel), context.mainExecutor) { _, _ -> cancel.cancel() }
        .build()
        .authenticate(cancel, context.mainExecutor, callback)
}