package com.example.footballlive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.example.footballlive.data.MediaItem
import com.example.footballlive.ui.screens.MainScreen
import com.example.footballlive.ui.theme.FootballLiveTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FootballLiveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    MainScreen(
                        onMediaItemClick = { mediaItem ->
                            // Handle media item click
                            // For now, just log the acestream URL
                            println("Clicked: ${mediaItem.title} - ${mediaItem.aceStreamUrl}")
                        }
                    )
                }
            }
        }
    }
}