package ai.workis.jowi.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Biometric gate on the stored token (iOS Face ID gate twin).
 * Biometrics is a gate on the LOCAL token, not a server auth method.
 * Returns true when the user passes — or when the device has nothing
 * to authenticate with (no enrolled biometrics / no screen lock): a
 * device that can't ask shouldn't dead-end the app.
 */
suspend fun biometricGate(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
): Boolean {
    val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    val can = BiometricManager.from(activity).canAuthenticate(authenticators)
    if (can != BiometricManager.BIOMETRIC_SUCCESS) return true

    // The prompt can only be shown by a RESUMED activity: asked while the app starts behind the
    // lock screen (or in the background) it is dropped without any callback, and the launch
    // would wait on the splash for ever. So the gate waits here until the app is in front.
    activity.lifecycle.withResumed { }

    return suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false)
                }
                // onAuthenticationFailed = wrong finger, prompt stays up — no resume
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
