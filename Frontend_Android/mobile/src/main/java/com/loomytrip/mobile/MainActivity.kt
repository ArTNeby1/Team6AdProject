package com.loomytrip.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.loomytrip.mobile.ui.LoomyTripApp
import com.loomytrip.mobile.ui.theme.LoomyTripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LoomyTripTheme {
                LoomyTripApp()
            }
        }
    }
}
