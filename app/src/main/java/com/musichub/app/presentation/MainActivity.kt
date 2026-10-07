package com.musichub.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    LIBRARY("Library", Icons.Default.List),
    PLAYLIST("Playlist", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MusicHubApp()
        }
    }
}

@Composable
fun MusicHubApp() {
    val darkTheme = isSystemInDarkTheme()
    var currentScreen by remember { mutableStateOf(Screen.HOME) }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF6366F1),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF4F46E5),
            background = Color(0xFFF9FAFB),
            surface = Color(0xFFFFFFFF)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Screen.values().forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (currentScreen) {
                    Screen.HOME -> Text("Home Screen", modifier = Modifier.padding(paddingValues))
                    Screen.LIBRARY -> Text("Library Screen", modifier = Modifier.padding(paddingValues))
                    Screen.PLAYLIST -> Text("Playlist Screen", modifier = Modifier.padding(paddingValues))
                    Screen.SETTINGS -> Text("Settings Screen", modifier = Modifier.padding(paddingValues))
                }
            }
        }
    }
}
