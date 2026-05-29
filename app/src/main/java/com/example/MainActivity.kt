package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.ExpenseViewModel
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle initial notification intent redirection
        handleNotificationLaunchIntent(intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationLaunchIntent(intent)
    }

    private fun handleNotificationLaunchIntent(currIntent: Intent?) {
        if (currIntent == null) return
        val action = currIntent.getStringExtra("LAUNCH_ACTION")
        Log.d("MainActivity", "Launch action extra: $action")
        
        if (action == "QUICK_LOG") {
            val amount = currIntent.getDoubleExtra("DETECTED_AMOUNT", 0.0)
            val appName = currIntent.getStringExtra("DETECTED_APP") ?: "UPI App"
            val merchant = currIntent.getStringExtra("DETECTED_MERCHANT") ?: "Merchant transaction"
            
            Log.d("MainActivity", "Successfully captured notification redirection: Amount $amount via $appName")
            
            // Set data inside ViewModel so it triggers the quick action logging modal dialog instantly upon launch!
            viewModel.setQuickLogIntentData(amount, appName, merchant)
        }
    }
}
