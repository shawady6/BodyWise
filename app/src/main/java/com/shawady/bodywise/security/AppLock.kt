package com.shawady.bodywise.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint / device-credential gate. Needs a FragmentActivity for BiometricPrompt. */
class AppLock(private val activity: FragmentActivity) {
    private val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }

    fun isAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(onSuccess: () -> Unit, onMessage: (String) -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح BodyWise")
            .setSubtitle("أكد هويتك بالبصمة أو قفل الشاشة")
            .setAllowedAuthenticators(authenticators)
            .apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText("إلغاء") }
            .build()
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onMessage(errString.toString())
            override fun onAuthenticationFailed() = onMessage("لم يتم التعرف على البصمة. حاول مرة أخرى.")
        })
        prompt.authenticate(info)
    }
}
