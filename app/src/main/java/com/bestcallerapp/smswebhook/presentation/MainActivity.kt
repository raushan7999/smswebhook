package com.bestcallerapp.smswebhook.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bestcallerapp.smswebhook.presentation.navigation.SmsWebhookApp
import com.bestcallerapp.smswebhook.presentation.theme.SmsWebhookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsWebhookTheme {
                SmsWebhookApp()
            }
        }
    }
}
