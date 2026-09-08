package com.example.diaryapp

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private const val APP_LOCK_PREFS = "haru_piece_app_lock"
private const val APP_LOCK_HASH = "pin_hash"
private const val APP_LOCK_SALT = "pin_salt"

fun isAppLockEnabled(context: Context): Boolean =
    context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE)
        .contains(APP_LOCK_HASH)

fun setAppLockPin(context: Context, pin: String) {
    require(pin.length == 4 && pin.all(Char::isDigit))
    val salt = ByteArray(16).also(SecureRandom()::nextBytes)
    context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(APP_LOCK_SALT, Base64.getEncoder().encodeToString(salt))
        .putString(APP_LOCK_HASH, hashPin(pin, salt))
        .apply()
}

fun verifyAppLockPin(context: Context, pin: String): Boolean {
    if (pin.length != 4 || !pin.all(Char::isDigit)) return false
    val prefs = context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE)
    val saltText = prefs.getString(APP_LOCK_SALT, null) ?: return false
    val expected = prefs.getString(APP_LOCK_HASH, null) ?: return false
    return runCatching {
        val actual = hashPin(pin, Base64.getDecoder().decode(saltText))
        MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8)
        )
    }.getOrDefault(false)
}

fun clearAppLock(context: Context) {
    context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}

internal fun hashPin(pin: String, salt: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    digest.update(pin.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(digest.digest())
}

@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun unlock() {
        if (verifyAppLockPin(context, pin)) {
            error = false
            focusManager.clearFocus()
            onUnlocked()
        } else {
            pin = ""
            error = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MiniPieceCluster()
            Text(
                "하루조각",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "잠금 번호를 입력해주세요.",
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            PinField(
                value = pin,
                onValueChange = {
                    pin = it
                    error = false
                },
                label = "4자리 잠금 번호",
                isError = error,
                imeAction = ImeAction.Done,
                onDone = ::unlock
            )
            if (error) {
                Text(
                    "잠금 번호가 맞지 않아요.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }
            PrimaryButton(
                text = "열기",
                enabled = pin.length == 4,
                onClick = ::unlock
            )
        }
    }
}

@Composable
fun AppLockSettingsScreen(onBack: () -> Unit, onLockChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isAppLockEnabled(context)) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun resetInputs() {
        currentPin = ""
        newPin = ""
        confirmPin = ""
    }

    AppScreen("앱 잠금", if (enabled) "잠금 번호로 기록을 보호하고 있어요." else "나만 기록을 볼 수 있게 보호해요.") {
        WhitePanel {
            TextButton(onClick = onBack) { Text("설정으로") }
            if (enabled) {
                PinField(currentPin, {
                    currentPin = it
                    message = null
                }, "현재 잠금 번호")
                PinField(newPin, {
                    newPin = it
                    message = null
                }, "새 잠금 번호")
                PinField(confirmPin, {
                    confirmPin = it
                    message = null
                }, "새 잠금 번호 확인")
                PrimaryButton(
                    "잠금 번호 변경",
                    enabled = currentPin.length == 4 && newPin.length == 4 && confirmPin.length == 4
                ) {
                    when {
                        !verifyAppLockPin(context, currentPin) -> {
                            message = "현재 잠금 번호가 맞지 않아요."
                            isError = true
                        }
                        newPin != confirmPin -> {
                            message = "새 잠금 번호가 서로 달라요."
                            isError = true
                        }
                        else -> {
                            setAppLockPin(context, newPin)
                            message = "잠금 번호를 변경했어요."
                            isError = false
                            resetInputs()
                            onLockChanged(true)
                        }
                    }
                }
                OutlinedSoftButton(
                    "앱 잠금 끄기",
                    enabled = currentPin.length == 4
                ) {
                    if (verifyAppLockPin(context, currentPin)) {
                        clearAppLock(context)
                        enabled = false
                        message = "앱 잠금을 껐어요."
                        isError = false
                        resetInputs()
                        onLockChanged(false)
                    } else {
                        message = "현재 잠금 번호가 맞지 않아요."
                        isError = true
                    }
                }
            } else {
                PinField(newPin, {
                    newPin = it
                    message = null
                }, "새 잠금 번호")
                PinField(confirmPin, {
                    confirmPin = it
                    message = null
                }, "잠금 번호 확인")
                PrimaryButton(
                    "앱 잠금 켜기",
                    enabled = newPin.length == 4 && confirmPin.length == 4
                ) {
                    if (newPin != confirmPin) {
                        message = "잠금 번호가 서로 달라요."
                        isError = true
                    } else {
                        setAppLockPin(context, newPin)
                        enabled = true
                        message = "다음부터 앱을 열 때 잠금 번호를 물어볼게요."
                        isError = false
                        resetInputs()
                        onLockChanged(true)
                    }
                }
            }
            message?.let {
                Text(
                    it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}
