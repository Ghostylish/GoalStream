package com.example.footballlive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.footballlive.data.MediaItem

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaCard(
    mediaItem: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Карточка матча для Android TV с фокусом и анимацией
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        scale = CardDefaults.scale(
            focusedScale = 1.1f // Увеличение при фокусе
        ),
        border = CardDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Верхняя зона: логотипы команд и символ ⚔️
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (mediaItem.homeTeamImage.isNotEmpty() && mediaItem.awayTeamImage.isNotEmpty()) {
                    // Отображение логотипов команд если они есть
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = mediaItem.homeTeamImage,
                            contentDescription = "Home team",
                            modifier = Modifier.size(60.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = "⚔️",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AsyncImage(
                            model = mediaItem.awayTeamImage,
                            contentDescription = "Away team",
                            modifier = Modifier.size(60.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    // Только символ ⚔️ если логотипов нет
                    Text(
                        text = "⚔️",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Нижняя зона: время матча
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (mediaItem.time.isNotEmpty()) {
                    Text(
                        text = mediaItem.time,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            // Название матча
            Text(
                text = mediaItem.title,
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
