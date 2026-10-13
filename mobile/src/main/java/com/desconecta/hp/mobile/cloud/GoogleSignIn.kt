package com.desconecta.hp.mobile.cloud

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.desconecta.hp.mobile.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

/**
 * "Continuar con Google" con Credential Manager. Se pide a Google un ID token con un nonce
 * cifrado (SHA-256) y a Supabase se le da el nonce original, que lo comprueba: un token
 * robado no sirve para otra petición.
 *
 * Solo está disponible si hay `google.webClientId` en local.properties.
 */
object GoogleSignIn {
    val available: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    sealed interface Result {
        data class Token(val idToken: String, val rawNonce: String) : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    /** [activityContext] debe ser la Activity: Credential Manager muestra su propia hoja. */
    suspend fun requestIdToken(activityContext: Context): Result {
        val rawNonce = UUID.randomUUID().toString()
        val hashed = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashed).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken, rawNonce)
            } else {
                Result.Failed("Google no devolvió una cuenta válida.")
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: NoCredentialException) {
            Result.Failed("No hay cuentas de Google en este teléfono. Añade una en Ajustes o usa tu correo.")
        } catch (e: Exception) {
            Result.Failed("No se pudo conectar con Google. Inténtalo de nuevo o usa tu correo.")
        }
    }
}
