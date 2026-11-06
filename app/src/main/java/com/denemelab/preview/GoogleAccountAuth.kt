package com.denemelab.preview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.IOException

/**
 * Explicit consent-based Google login. On Android 16, Google Credential
 * Manager can close before showing the account picker (platform issue);
 * provide a legacy Play Services intent-based compatibility path.
 *
 * Never auto-retry a cancelled authorization or send a Google token to logs.
 */
object GoogleAccountAuth {
    private const val TAG = "DenemeLabGoogleAuth"

    class GoogleSelectionNotCompleted(
        message: String, cause: Throwable? = null
    ) : IOException(message, cause)

    enum class Flow { CREDENTIAL_MANAGER, COMPATIBILITY }

    // Keep this pure to regression-test Android version routing.
    fun preferredFlow(sdk: Int): Flow =
        if (sdk >= 36) Flow.COMPATIBILITY else Flow.CREDENTIAL_MANAGER

    fun accountEmail(context: Context): String? {
        if (!FirebaseCloudBackup.configured(context)) return null
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return null
        return user.email?.takeIf { email ->
            email.isNotBlank() && user.providerData.any { it.providerId == "google.com" }
        }
    }

    fun webClientConfigured(context: Context): Boolean = webClientId(context) != null

    private fun webClientId(context: Context): String? {
        val generatedId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName)
        val publicFallbackId = context.resources.getIdentifier(
            "denemelab_google_web_client_id", "string", context.packageName)
        val id = if (generatedId != 0) generatedId else publicFallbackId
        return if (id == 0) null else context.getString(id).takeIf {
            it.isNotBlank() && it.endsWith(".apps.googleusercontent.com")
        }
    }

    private fun requireClientId(context: Context): String {
        if (!FirebaseCloudBackup.configured(context))
            throw IOException("Bu APK'da Firebase projesi yapılandırılmamış.")
        return webClientId(context)
            ?: throw IOException(
                "Google giriş yapılandırması eksik. Firebase Android proje ayarlarını kontrol et."
            )
    }

    /** Launch Google-owned Activity, not a popup layered over Compose Dialog. */
    @Suppress("DEPRECATION")
    fun compatibilityIntent(activity: Activity): Intent {
        val clientId = requireClientId(activity)
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(clientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, options).signInIntent
    }

    /** Only called after the Google-owned activity delivers an explicit result. */
    @Suppress("DEPRECATION")
    suspend fun signInWithCompatibilityResult(
        context: Context, resultCode: Int, data: Intent?
    ): String {
        if (resultCode != Activity.RESULT_OK) {
            throw GoogleSelectionNotCompleted(
                "Google hesabı seçimi tamamlanmadı. Pencereyi sen kapatmadıysan " +
                    "'Diğer giriş yöntemini dene' seçeneğini kullan."
            )
        }
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
        } catch (e: ApiException) {
            // Numeric status is safe for diagnostics; email/id token never logged.
            Log.w(TAG, "Play Services auth failed status=" + e.statusCode)
            throw when (e.statusCode) {
                GoogleSignInStatusCodes.SIGN_IN_CANCELLED, CommonStatusCodes.CANCELED ->
                    GoogleSelectionNotCompleted(
                        "Google hesap seçme penceresi tamamlanmadan kapandı. " +
                            "Diğer giriş yöntemini deneyebilirsin.", e
                    )
                CommonStatusCodes.DEVELOPER_ERROR ->
                    IOException("Google OAuth uygulama kimliği veya APK imzası uyuşmuyor " +
                        "(10). Firebase Android SHA-1 ayarını kontrol et.", e)
                CommonStatusCodes.NETWORK_ERROR ->
                    IOException("Google hesabına bağlanırken ağ hatası oluştu. " +
                        "Bağlantını kontrol edip tekrar dene.", e)
                else ->
                    IOException("Google Play Hizmetleri giriş hatası (kod " +
                        e.statusCode + "). Diğer giriş yöntemini deneyebilirsin.", e)
            }
        }
        val idToken = account?.idToken
            ?: throw IOException(
                "Google hesabı seçildi ancak ID token alınamadı. " +
                    "Web OAuth istemci kimliği ve Android SHA-1 eşleşmesini kontrol et."
            )
        return FirebaseCloudBackup.signInWithGoogleToken(context, idToken)
    }

    /** Android Credential Manager alternative on supported devices. */
    suspend fun signInWithCredentialManager(activity: Activity): String {
        val clientId = requireClientId(activity)
        val credential = try {
            val googleOption = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption).build()
            CredentialManager.create(activity)
                .getCredential(context = activity, request = request).credential
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Credential Manager dismissed before selection, sdk=" +
                Build.VERSION.SDK_INT)
            throw GoogleSelectionNotCompleted(
                "Google hesap seçimi tamamlanmadı. Bu, Android'in giriş " +
                    "penceresinden de kaynaklanabilir. 'Diğer giriş yöntemini dene'ye bas.", e
            )
        } catch (e: GetCredentialInterruptedException) {
            throw IOException("Google hesap penceresi kesintiye uğradı. Yeniden deneyebilirsin.", e)
        } catch (e: GetCredentialProviderConfigurationException) {
            throw IOException("Cihazda Google giriş sağlayıcısı yapılandırılamadı. " +
                "Diğer giriş yöntemini dene.", e)
        } catch (e: GetCredentialUnsupportedException) {
            throw IOException("Bu cihaz Credential Manager ile Google girişini desteklemiyor. " +
                "Diğer giriş yöntemini dene.", e)
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager error type=" + e.type)
            throw IOException("Google hesap sağlayıcısı yanıt vermedi (" +
                e.javaClass.simpleName + "). Diğer giriş yöntemini dene.", e)
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            throw IOException("Google hesabı seçildi ancak beklenen oturum yanıtı alınamadı.")
        val token = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: Exception) {
            throw IOException("Google oturum bilgisi doğrulanamadı.", e)
        }
        return FirebaseCloudBackup.signInWithGoogleToken(activity, token)
    }

    suspend fun signOut(activity: Activity) {
        FirebaseCloudBackup.signOut(activity)
        runCatching {
            CredentialManager.create(activity)
                .clearCredentialState(ClearCredentialStateRequest())
        }
        // Invalidate compatibility-client cache, without clearing local exams.
        @Suppress("DEPRECATION")
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        runCatching { GoogleSignIn.getClient(activity, options).signOut() }
    }
}
