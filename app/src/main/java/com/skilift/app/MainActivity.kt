package com.skilift.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.skilift.app.ui.theme.SkiliftTheme
import com.skilift.app.ui.navigation.SkiliftNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkiliftTheme {
                SkiliftNavGraph()
            }
        }
    }
}
