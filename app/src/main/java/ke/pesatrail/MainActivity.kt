package ke.pesatrail

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import ke.pesatrail.ui.*

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    var lastInteraction = SystemClock.elapsedRealtime()
    override fun onUserInteraction() { super.onUserInteraction(); lastInteraction=SystemClock.elapsedRealtime() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val vm=ViewModelProvider(this)[LedgerViewModel::class.java]
        setContent { PesaTrail(vm, this) }
    }
    fun authenticate(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val authenticators=BiometricManager.Authenticators.BIOMETRIC_STRONG
        if(BiometricManager.from(this).canAuthenticate(authenticators)!=BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Strong biometrics unavailable. Enroll a fingerprint or use your app PIN."); return
        }
        BiometricPrompt(this,ContextCompat.getMainExecutor(this),object: BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(code: Int, message: CharSequence) { onError(message.toString()) }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Unlock PesaTrail").setSubtitle("Your money stays on this device").setAllowedAuthenticators(authenticators).setNegativeButtonText("Cancel").build())
    }
}
