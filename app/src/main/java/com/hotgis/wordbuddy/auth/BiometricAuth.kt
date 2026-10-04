package com.hotgis.wordbuddy.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuth {
    private const val AUTHENTICATORS = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    private class PromptHandle {
        var prompt: BiometricPrompt? = null
    }

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun statusMessage(context: Context): String? {
        return when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "此设备不支持指纹 / 生物识别"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "生物识别暂时不可用，请稍后再试"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "请先在系统设置中录入指纹或面容"
            else -> "当前无法使用生物识别"
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "指纹登录",
        subtitle: String = "验证指纹以继续使用词搭子",
        negativeButton: String = "取消",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {},
        onPasswordFallback: (() -> Unit)? = null,
    ) {
        val blocked = statusMessage(activity)
        if (blocked != null) {
            onError(blocked)
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val handle = PromptHandle()
        var failures = 0
        var handedOff = false
        fun handOff() {
            if (handedOff) return
            handedOff = true
            onPasswordFallback?.invoke()
        }
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!handedOff) onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (handedOff) return
                    if (onPasswordFallback != null &&
                        (errorCode == BiometricPrompt.ERROR_LOCKOUT ||
                            errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT)
                    ) {
                        handOff()
                        return
                    }
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    if (onPasswordFallback == null || handedOff) return
                    failures += 1
                    if (failures >= 3) {
                        handOff()
                        handle.prompt?.cancelAuthentication()
                    }
                }
            },
        )
        handle.prompt = prompt
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButton)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build(),
        )
    }
}
