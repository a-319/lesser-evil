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
@@ -46,6 +48,9 @@ import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

@Composable
fun AppLockDialog(onSucceed: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismiss, DialogProperties(true, false)) {
@@ -55,12 +60,29 @@ fun AppLockDialog(onSucceed: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismi
    var input by rememberSaveable { mutableStateOf("") }
    var isError by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var failedAttempts by rememberSaveable { mutableIntStateOf(SP.lockPasswordFailedAttempts) }
    var lockoutUntil by rememberSaveable { mutableLongStateOf(SP.lockPasswordLockoutUntil) }
    var remainingSeconds by rememberSaveable { mutableIntStateOf(0) }
    val isLocked = lockoutUntil > System.currentTimeMillis()
    fun unlock() {
        if (isLocked) return
        if(input.hash() == SP.lockPasswordHash) {
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
                val extraFailures = failedAttempts - 3
                val delayMillis = min(30_000L * (1L shl extraFailures), 10 * 60_000L)
The lockout duration calculation can overflow before the `min` cap is applied: once `failedAttempts` gets high enough (about 52+, i.e. `extraFailures >= 49`), `30_000L * (1L shl extraFailures)` wraps to a negative/zero `Long`, so `lockoutUntil` may be set to the past and the password input re-enables immediately. This breaks the intended brute-force throttling for repeated failures; clamp `extraFailures` (or the shifted value) before multiplying to keep the arithmetic in range.
                lockoutUntil = System.currentTimeMillis() + delayMillis
                SP.lockPasswordLockoutUntil = lockoutUntil
            }
        }
    }
    LaunchedEffect(Unit) {
@@ -70,6 +92,22 @@ fun AppLockDialog(onSucceed: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismi
            fr.requestFocus()
        }
    }
    LaunchedEffect(lockoutUntil) {
        if (lockoutUntil == 0L) {
            remainingSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            val remaining = max(0L, lockoutUntil - System.currentTimeMillis())
            remainingSeconds = (remaining / 1000L).toInt()
            if (remaining == 0L) {
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
@@ -77,6 +115,7 @@ fun AppLockDialog(onSucceed: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismi
                OutlinedTextField(
                    input, { input = it; isError = false }, Modifier.width(200.dp).focusRequester(fr),
                    label = { Text(stringResource(R.string.password)) }, isError = isError,
                    enabled = !isLocked,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password, imeAction = if(input.length >= 4) ImeAction.Go else ImeAction.Done
                    ),
@@ -99,9 +138,15 @@ fun AppLockDialog(onSucceed: () -> Unit, onDismiss: () -> Unit) = Dialog(onDismi
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