package com.example.diaryapp

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val APP_LOCK_PREFS = "haru_piece_app_lock"
private const val APP_LOCK_ENABLED = "device_auth_enabled"
private const val LEGACY_APP_LOCK_HASH = "pin_hash"
private const val LEGACY_APP_LOCK_SALT = "pin_salt"
private const val DEVICE_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun isAppLockEnabled(context: Context): Boolean {
    val preferences = context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE)
    if (!preferences.contains(APP_LOCK_ENABLED) && preferences.contains(LEGACY_APP_LOCK_HASH)) {
        val canMigrate = canUseDeviceAuthentication(context)
        preferences.edit()
            .remove(LEGACY_APP_LOCK_HASH)
            .remove(LEGACY_APP_LOCK_SALT)
            .putBoolean(APP_LOCK_ENABLED, canMigrate)
            .apply()
        return canMigrate
    }
    return preferences.getBoolean(APP_LOCK_ENABLED, false)
}

fun setAppLockEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(APP_LOCK_ENABLED, enabled)
        .apply()
}

fun clearAppLock(context: Context) {
    context.getSharedPreferences(APP_LOCK_PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply()
}

fun canUseDeviceAuthentication(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(DEVICE_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun authenticateWithDevice(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("하루조각 잠금 해제")
        .setSubtitle("휴대폰에 등록된 잠금으로 확인해주세요.")
        .setAllowedAuthenticators(DEVICE_AUTHENTICATORS)
        .build()
    prompt.authenticate(promptInfo)
}

@Composable
fun AppLockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }

    fun authenticate() {
        if (!canUseDeviceAuthentication(activity)) {
            message = "휴대폰 설정에서 화면 잠금을 먼저 등록해주세요."
            return
        }
        authenticateWithDevice(
            activity = activity,
            onSuccess = onUnlocked,
            onError = { message = "인증이 취소됐어요. 다시 시도해주세요." }
        )
    }

    LaunchedEffect(Unit) { authenticate() }

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
                "휴대폰 잠금으로 기록을 열어주세요.",
                modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            message?.let {
                Text(
                    it,
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            PrimaryButton("휴대폰 잠금으로 열기", onClick = ::authenticate)
        }
    }
}

@Composable
fun AppLockSettingsScreen(onBack: () -> Unit, onLockChanged: (Boolean) -> Unit) {
    val activity = LocalActivity.current as FragmentActivity
    var enabled by remember { mutableStateOf(isAppLockEnabled(activity)) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun changeLock(nextEnabled: Boolean) {
        if (!canUseDeviceAuthentication(activity)) {
            message = "휴대폰 설정에서 지문, 얼굴 또는 화면 잠금을 먼저 등록해주세요."
            isError = true
            return
        }
        authenticateWithDevice(
            activity = activity,
            onSuccess = {
                setAppLockEnabled(activity, nextEnabled)
                enabled = nextEnabled
                onLockChanged(nextEnabled)
                message = if (nextEnabled) {
                    "이제 휴대폰 잠금으로 하루조각을 보호해요."
                } else {
                    "앱 잠금을 껐어요."
                }
                isError = false
            },
            onError = {
                message = "인증이 취소되어 설정을 바꾸지 않았어요."
                isError = true
            }
        )
    }

    AppScreen(
        "앱 잠금",
        if (enabled) "휴대폰 잠금으로 기록을 보호하고 있어요." else "휴대폰의 지문, 얼굴 또는 화면 잠금을 사용해요."
    ) {
        WhitePanel {
            TextButton(onClick = onBack) { Text("설정으로") }
            Text(
                if (enabled) {
                    "앱을 다시 열 때 휴대폰에 등록된 인증을 요청해요."
                } else {
                    "휴대폰 잠금으로 하루조각을 보호해요."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
            PrimaryButton(if (enabled) "앱 잠금 끄기" else "앱 잠금 켜기") {
                changeLock(!enabled)
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
