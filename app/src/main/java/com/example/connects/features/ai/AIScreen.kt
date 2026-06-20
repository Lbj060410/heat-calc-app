package com.example.connects.features.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AIScreen(
    viewModel: AIVieModel = hiltViewModel()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Build, contentDescription = null)
                Text(
                    text = "\u0041\u0049 \u529f\u80fd\u6682\u672a\u5f00\u653e",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "\u5f53\u524d\u7248\u672c\u5148\u4fdd\u7559\u5165\u53e3\uff0c\u540e\u7eed\u518d\u63a5\u5165\u667a\u80fd\u5efa\u8bae\u3002",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
