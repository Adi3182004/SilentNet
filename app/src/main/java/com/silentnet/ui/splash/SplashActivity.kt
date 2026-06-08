package com.silentnet.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.SilentNetApplication
import com.silentnet.ui.auth.AuthActivity
import com.silentnet.ui.main.MainActivity
import com.silentnet.ui.theme.SilentNetTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SilentNetApplication
        val session = app.graph.sessionManager

        setContent {
            SilentNetTheme {
                SplashView()
            }
        }

        lifecycleScope.launch {
            delay(1200)
            startActivity(
                Intent(
                    this@SplashActivity,
                    if (session.hasSession()) MainActivity::class.java else AuthActivity::class.java
                )
            )
            finish()
        }
    }
}

@Composable
private fun SplashView() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF192040), Color(0xFF090D18), Color.Black),
                    radius = 1200f
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(180.dp)
                .alpha(0.8f * glow)
                .background(Brush.radialGradient(listOf(Color(0xFF7C4DFF), Color.Transparent)))
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SilentNet",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Offline messenger built for privacy",
                color = Color(0xFFC8D0F0),
                fontSize = 14.sp
            )
        }
    }
}
