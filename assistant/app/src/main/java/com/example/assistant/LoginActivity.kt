package com.example.assistant

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db = FirebaseFirestore.getInstance()

    // Используем Activity Result API для обработки результата входа
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                // Успешный вход
                val email = account.email
                val displayName = account.displayName
                val serverAuthCode = account.serverAuthCode

                Log.d("GoogleSignIn", "Email: $email, Name: $displayName")
                // Сохраняем пользователя в Firestore
                saveUserToFirestore(email, displayName)
            } catch (e: ApiException) {
                Log.e("GoogleSignIn", "Sign-in failed: ${e.statusCode}")
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        // Настройка Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail() // Запрашиваем email пользователя
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar")) // Доступ к Google Calendar
            .requestIdToken(getString(R.string.google_client_id))
            .requestServerAuthCode(getString(R.string.google_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Кнопка для входа
        val signInButton = findViewById<Button>(R.id.signInButton)
        signInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent) // Используем Activity Result API
        }
    }

    private fun saveUserToFirestore(email: String?, displayName: String?) {
        if (email == null || displayName == null) return
        val user = hashMapOf(
            "email" to email,
            "displayName" to displayName
        )
        db.collection("users")
            .document(email)
            .set(user)
            .addOnSuccessListener {
                Log.d("Firestore", "User saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving user", e)
            }
    }
}