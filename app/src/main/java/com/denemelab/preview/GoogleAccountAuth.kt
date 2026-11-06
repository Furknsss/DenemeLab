package com.denemelab.preview

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.IOException

/**
 * Explicit Google account picker -> verified Firebase Authentication session.
 * This DOES NOT request access to the student's private Google Drive data.
 */
object GoogleAccountAuth {
    fun webClientConfigured(context: Context): Boolean = webClientId(context) != null

    private fun webClientId(context: Context): String? {
        // Generated only after Firebase config contains the OAuth WEB client
        // (client_type=3). Avoid a generated-R reference so a bad config fails
        // gracefully rather than preventing the project from compiling.
        val id = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName)
        return if (id == 0) null else
            context.getString(id).takeIf {
                it.isNotBlank() && it.endsWith(".apps.googleusercontent.com")
            }
    }

    suspend fun signIn(activity: Activity): String {
        if (!FirebaseCloudBackup.configured(activity))
            throw IOException("Firebase yapılandırması bulunamadı.")
        val clientId = webClientId(activity)
            ?: throw IOException(
                "Google giriş yapılandırması eksik. Firebase Console'a Android SHA-1 ekleyip " +
                    "google-services.json dosyasını yeniden indir ve GitHub secret'ını güncelle.")
        val credential = try {
            val googleOption = GetSignInWithGoogleOption.Builder(clientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption).build()
            CredentialManager.create(activity)
                .getCredential(context = activity, request = request).credential
        } catch (_: GetCredentialCancellationException) {
            throw IOException("Google hesap seçimi iptal edildi.")
        } catch (e: GetCredentialException) {
            throw IOException(
                "Google hesabı seçilemedi. Google Play Hizmetlerini ve Firebase SHA-1 kaydını kontrol et.",
                e
            )
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            throw IOException("Google'dan beklenen oturum bilgisi alınamadı.")
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: Exception) {
            throw IOException("Google hesabının kimlik bilgisi okunamadı.", e)
        }
        return FirebaseCloudBackup.signInWithGoogleToken(activity, idToken)
    }

    suspend fun signOut(activity: Activity) {
        FirebaseCloudBackup.signOut(activity)
        // Best effort: Firebase auth is already signed out if provider cache
        // cannot be cleared (for example while Play Services is offline).
        runCatching {
            CredentialManager.create(activity)
                .clearCredentialState(ClearCredentialStateRequest())
        }
    }
}
