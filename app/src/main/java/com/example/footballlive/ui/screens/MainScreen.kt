package com.example.footballlive.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.CircularProgressIndicator
import com.example.footballlive.R
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items as lazyItems
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface as TvSurface
import androidx.tv.material3.Text as TvText
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Card
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.example.footballlive.data.MatchParser
import com.example.footballlive.data.MediaItem
import com.example.footballlive.data.MockData
import com.example.footballlive.data.AcestreamStream
import com.example.footballlive.ui.components.MediaCard
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onMediaItemClick: (MediaItem) -> Unit = {}
) {
    // Контекст приложения для работы с интентами
    val context = LocalContext.current
    // Список матчей для отображения
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // Флаг загрузки данных
    var isLoading by remember { mutableStateOf(true) }
    // Выбранный матч для показа диалога
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    // Флаг парсинга acestream ссылок
    var isParsingAcestream by remember { mutableStateOf(false) }
    // Список acestream трансляций
    var acestreamStreams by remember { mutableStateOf<List<AcestreamStream>>(emptyList()) }
    // Флаг показа диалога установки Ace Stream
    var showInstallDialog by remember { mutableStateOf(false) }
    // Выбранная ссылка для трансляции
    var selectedStreamLink by remember { mutableStateOf("") }
    
    // Загрузка данных при запуске экрана
    LaunchedEffect(Unit) {
        isLoading = true
        val parser = MatchParser()
        val result = parser.parseMatches()
        result.onSuccess { items ->
            // Загрузка изображений команд для каждого матча
            val itemsWithImages = parser.loadTeamImages(items)
            mediaItems = itemsWithImages
        }.onFailure {
            // Резервные данные если парсинг не удался
            mediaItems = MockData.mediaItems
        }
        isLoading = false
    }
    
    // Основной контейнер с фоном и контентом
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Фоновое изображение
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.background_main),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
        
        // Контейнер с контентом поверх фона
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading) {
                // Индикатор загрузки
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp)
                    )
                }
            } else {
                // Сетка карточек матчей для Android TV
                TvLazyVerticalGrid(
                    columns = TvGridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 32.dp,
                        end = 32.dp,
                        top = 48.dp, // Даем чуть больше места сверху для scale анимации
                        bottom = 32.dp
                    ),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp)
                ) {
                    items(mediaItems) { mediaItem ->
                        MediaCard(
                            mediaItem = mediaItem,
                            onClick = { 
                                // При клике на карточку запускаем парсинг acestream ссылок
                                selectedMediaItem = mediaItem
                                isParsingAcestream = true
                                onMediaItemClick(mediaItem)
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Парсинг acestream ссылок при выборе матча
    LaunchedEffect(selectedMediaItem) {
        if (selectedMediaItem != null && isParsingAcestream) {
            val parser = MatchParser()
            val streams = parser.parseMatchPageForAcestreamLinks(selectedMediaItem!!.matchUrl)
            acestreamStreams = streams
            isParsingAcestream = false
        }
    }
    
    // Показ диалога с acestream трансляциями после парсинга
    selectedMediaItem?.let { item ->
        if (!isParsingAcestream) {
            AcestreamDialog(
                mediaItem = item,
                acestreamStreams = acestreamStreams,
                onStreamClick = { stream ->
                    selectedStreamLink = stream.link
                    try {
                        // Пытаемся открыть acestream ссылку
                        openAcestreamLink(context, stream.link)
                    } catch (e: Exception) {
                        android.util.Log.d("AceStreamCheck", "Failed to open acestream link: ${e.message}")
                        // Если не удалось - показываем диалог установки
                        showInstallDialog = true
                    }
                },
                onDismiss = { 
                    selectedMediaItem = null
                    acestreamStreams = emptyList()
                }
            )
        }
    }
    
    // Показ диалога установки Ace Stream если приложение не установлено
    if (showInstallDialog) {
        InstallAceStreamDialog(
            onDismiss = { showInstallDialog = false },
            onInstall = {
                // Открытие Play Store для установки Ace Stream
                openPlayStore(context)
                showInstallDialog = false
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AcestreamDialog(
    mediaItem: MediaItem,
    acestreamStreams: List<AcestreamStream>,
    onStreamClick: (AcestreamStream) -> Unit,
    onDismiss: () -> Unit
) {
    // Диалоговое окно с acestream трансляциями
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Заголовок с названием матча
                Text(
                    text = mediaItem.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (acestreamStreams.isNotEmpty()) {
                    Text(
                        text = "Трансляции:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Список трансляций для Android TV
                    TvLazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        lazyItems(acestreamStreams) { stream ->
                            // Карточка трансляции
                            Card(
                                onClick = {
                                    onStreamClick(stream)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Битрейт: ${stream.bitrate}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Качество: ${stream.quality}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "▶",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Сообщение если трансляций нет
                    Text(
                        text = "Трансляций еще нет",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопка закрытия диалога
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
fun InstallAceStreamDialog(
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    // Диалоговое окно для установки Ace Stream
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ace Stream не установлен",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Хотите установить Ace Stream?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопки выбора
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Закрыть")
                    }
                    
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

// Проверка установлен ли Ace Stream
fun isAceStreamInstalled(context: android.content.Context): Boolean {
    // Проверка есть ли приложение которое может обрабатывать acestream:// интенты
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("acestream://test"))
    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
    
    if (resolveInfo != null) {
        android.util.Log.d("AceStreamCheck", "Found app that can handle acestream: ${resolveInfo.activityInfo.packageName}")
        return true
    }
    
    android.util.Log.d("AceStreamCheck", "No app found that can handle acestream intents")
    return false
}

// Открытие acestream ссылки через интент
fun openAcestreamLink(context: android.content.Context, link: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
    context.startActivity(intent)
}

// Открытие Play Store для установки Ace Stream
fun openPlayStore(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.acestream.node"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
