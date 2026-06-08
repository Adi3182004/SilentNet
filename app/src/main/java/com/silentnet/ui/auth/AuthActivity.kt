package com.silentnet.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.silentnet.SilentNetApplication
import com.silentnet.ui.main.MainActivity
import com.silentnet.ui.theme.SilentNetTheme

class AuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SilentNetApplication

        if (app.graph.sessionManager.hasSession()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            SilentNetTheme {
                AuthScreen(
                    graph = app.graph,
                    onLoggedIn = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}
