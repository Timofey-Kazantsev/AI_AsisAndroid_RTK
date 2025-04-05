package com.example.assistant

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("GoogleSignIn", "📌 Result received, resultCode: ${result.resultCode}")

        if (result.resultCode == RESULT_OK) {
            Log.d("GoogleSignIn", "✅ Result OK, processing task...")
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)

            try {
                val account = task.getResult(ApiException::class.java)
                val email = account.email
                val displayName = account.displayName

                Log.d("GoogleSignIn", "✅ Success! Email: $email, Name: $displayName")

                // Сохраняем пользователя в Realtime Database
                saveUserToRealtimeDatabase(email, displayName)
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "❌ Sign-in failed! Status code: ${e.statusCode}, Message: ${e.message}")

                when (e.statusCode) {
                    7 -> Log.e("GoogleSignIn", "🌐 ERROR: Network issue. Check internet connection.")
                    10 -> Log.e("GoogleSignIn", "🔑 ERROR: Developer error. Verify SHA-1 and Web Client ID.")
                    12501 -> Log.e("GoogleSignIn", "🚫 ERROR: User canceled the sign-in.")
                    12502 -> Log.e("GoogleSignIn", "⚠️ ERROR: Sign-in intent problem, try restarting the app.")
                    16 -> Log.e("GoogleSignIn", "⚡ ERROR: API not connected, ensure Google Play Services are installed.")
                    else -> Log.e("GoogleSignIn", "❓ Unknown error occurred.")
                }
            }
        } else {
            Log.e("GoogleSignIn", "❌ Result NOT OK, resultCode: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        Log.d("LoginActivity", "🚀 onCreate called, setting up Google Sign-In...")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        Log.d("GoogleSignIn", "📌 GoogleSignInOptions created: $gso")
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        Log.d("GoogleSignIn", "✅ GoogleSignInClient created: $googleSignInClient")

        val signInButton = findViewById<Button>(R.id.signInButton)
        signInButton.setOnClickListener {
            Log.d("LoginActivity", "👆 Sign-in button clicked, launching sign-in intent...")
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }
    }

    private fun saveUserToRealtimeDatabase(email: String?, displayName: String?) {
        if (email == null || displayName == null) {
            Log.e("RealtimeDB", "❌ Email или DisplayName пустые, не можем сохранить данные")
            return
        }

        val database = FirebaseDatabase.getInstance().getReference("users")

        val user = mapOf(
            "email" to email,
            "displayName" to displayName
        )

        database.child(email.replace(".", "_")).setValue(user)
            .addOnSuccessListener {
                Log.d("RealtimeDB", "✅ Данные сохранены в Realtime Database: $email")
            }
            .addOnFailureListener { e ->
                Log.e("RealtimeDB", "❌ Ошибка сохранения: ${e.message}")
            }
    }
}
