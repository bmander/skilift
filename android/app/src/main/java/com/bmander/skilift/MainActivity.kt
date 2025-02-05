package com.bmander.skilift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bmander.skilift.ui.theme.SkiliftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkiliftTheme {
                TripPlannerApp()
            }
        }
    }
}

@Composable
fun TripPlannerApp() {
    var startLocation = remember { mutableStateOf("") }
    var endLocation = remember { mutableStateOf("") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            MapComponent(
                modifier = Modifier.weight(1f).fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LocationInputs()
        }
    }
}

@Composable
fun LocationInputs() {
    Text(text = "Location Inputs Placeholder")
}

@Composable
fun MapComponent(modifier: Modifier) {
    Text(text = "Map Component Placeholder")
}