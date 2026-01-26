package com.example.beautyappfrontend.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.beautyappfrontend.data.remote.RetrofitInstance
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    // Додаємо callback: "що робити, коли натиснули кнопку"
    onNavigateToSearch: () -> Unit
) {
    var serverMessage by remember { mutableStateOf("Click to test!") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = serverMessage, modifier = Modifier.padding(16.dp))

        // Кнопка тесту сервера
        Button(onClick = {
            coroutineScope.launch {
                try {
                    serverMessage = "Connecting.."
                    val response = RetrofitInstance.api.checkConnection()
                    serverMessage = response.message
                } catch (e: Exception) {
                    serverMessage = "Error: ${e.message}"
                }
            }
        }) {
            Text(text = "Test server connection")
        }

        // Відступ між кнопками
        Spacer(modifier = Modifier.height(16.dp))

        // НОВА КНОПКА: Перехід на пошук
        Button(onClick = { onNavigateToSearch() }) {
            Text(text = "Go to Search")
        }
    }
}