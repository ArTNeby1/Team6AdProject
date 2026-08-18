package com.loomytrip.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.loomytrip.mobile.data.network.TokenStore
import com.loomytrip.mobile.ui.LoomyTripApp
import com.loomytrip.mobile.ui.theme.LoomyTripTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            TokenStore.initialize(applicationContext)
            setContent {
                LoomyTripTheme {
                    LoomyTripApp()
                }
            }
        }
    }
}
