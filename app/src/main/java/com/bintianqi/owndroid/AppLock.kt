package com.bintianqi.owndroid

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.hardware.biometrics.BiometricPrompt.AuthenticationCallback
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
fun AppLockDialog(onSucceed: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismiss, DialogProperties(true, false)) {
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

    fun unlock() {
        if (isLocked) return
        if (input.hash() == SP.lockPasswordHash) {
            fm.clearFocus()
            failedAttempts = 0
            lockoutUntil = 0L
            SP.lockPasswordFailedAttempts = 0
            SP.lockPasswordLockoutUntil = 0L
            onSucceed()
        } else {
            isError = true
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
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 28 && SP.biometricsUnlock) {
            startBiometricsUnlock(context, onSucceed)
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
                        imeAction = if (input.length >= 4) ImeAction.Go else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions({ fm.clearFocus() }, { unlock() }),
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
                    FilledTonalIconButton({ startBiometricsUnlock(context, onSucceed) }, Modifier.padding(start = 4.dp)) {
                        Icon(painterResource(R.drawable.fingerprint_fill0), null)
                    }
                }
            }
            Button(::unlock, Modifier.align(Alignment.End).padding(top = 8.dp), enabled = !isLocked) {
                Text(stringResource(R.string.unlock))
            }
            if (remainingSeconds > 0) {
                Text(
                    stringResource(R.string.unlock_wait_seconds, remainingSeconds),
                    Modifier.padding(top = 8.dp)
                )
            }
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