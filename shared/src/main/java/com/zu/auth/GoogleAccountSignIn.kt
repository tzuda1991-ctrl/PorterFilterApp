package com.zu.auth

import android.app.Activity
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

object GoogleAccountSignIn {
    fun signIn(
        activity: Activity,
        webClientId: String,
        cancellation: CancellationSignal,
        onComplete: (Result<FirebaseUser>) -> Unit
    ) {
        val googleOption = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        CredentialManager.create(activity).getCredentialAsync(
            activity,
            request,
            cancellation,
            ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val token = runCatching {
                        val credential = result.credential
                        check(credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            "Google sign-in returned an unexpected credential."
                        }
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    }.getOrElse {
                        onComplete(Result.failure(it))
                        return
                    }

                    FirebaseAuth.getInstance()
                        .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                        .addOnCompleteListener { task ->
                            val user = if (task.isSuccessful) task.result.user else null
                            if (user != null) onComplete(Result.success(user))
                            else onComplete(Result.failure(task.exception ?: IllegalStateException("Google sign-in failed.")))
                        }
                }

                override fun onError(e: GetCredentialException) {
                    onComplete(Result.failure(e))
                }
            }
        )
    }
}
