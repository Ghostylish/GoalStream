@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.example.footballlive.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.footballlive.R
import com.example.footballlive.data.AcestreamStream
import com.example.footballlive.data.BrowserStream
import com.example.footballlive.data.MatchParser
import com.example.footballlive.data.MediaItem
import com.example.footballlive.data.MockData

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onMediaItemClick: (MediaItem) -> Unit = {}
) {
    val context = LocalContext.current
    val parser = remember { MatchParser() }

    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var isLoadingMatches by remember { mutableStateOf(true) }
    var isParsingStreams by remember { mutableStateOf(false) }
    var hasSearchedStreams by remember { mutableStateOf(false) }
    var acestreamStreams by remember { mutableStateOf<List<AcestreamStream>>(emptyList()) }
    var browserStreams by remember { mutableStateOf<List<BrowserStream>>(emptyList()) }
    var selectedBrowserUrl by remember { mutableStateOf<String?>(null) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var reloadRequest by remember { mutableStateOf(0) }

    LaunchedEffect(reloadRequest) {
        isLoadingMatches = true
        selectedMediaItem = null
        acestreamStreams = emptyList()
        browserStreams = emptyList()
        hasSearchedStreams = false
        val result = parser.parseMatches()
        val loadedItems = result.getOrElse { MockData.mediaItems }
        mediaItems = parser.loadTeamImages(loadedItems)
        selectedMediaItem = mediaItems.firstOrNull()
        isLoadingMatches = false
    }

    LaunchedEffect(selectedMediaItem, isParsingStreams) {
        val selected = selectedMediaItem
        if (selected != null && isParsingStreams) {
            acestreamStreams = parser
                .parseMatchPageForAcestreamLinks(selected.matchUrl)
                .sortedWith(compareByDescending<AcestreamStream> { it.quality.toIntOrNull() ?: 0 }
                    .thenByDescending { extractBitrate(it.bitrate) })
            browserStreams = parser
                .parseMatchPageForBrowserLinks(selected.matchUrl)
                .sortedWith(compareByDescending<BrowserStream> { it.quality.toIntOrNull() ?: 0 }
                    .thenByDescending { extractBitrate(it.bitrate) })
            hasSearchedStreams = true
            isParsingStreams = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_main),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xF209100D),
                            Color(0xCC09100D),
                            Color(0xF209100D)
                        )
                    )
                )
        )

        if (isLoadingMatches) {
            LoadingState()
        } else {
            // Главный контейнер экрана: здесь настраиваются общие отступы от краёв ТВ-экрана.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 40.dp, top = 32.dp, end = 40.dp, bottom = 32.dp)
            ) {
                Header(
                    matchCount = mediaItems.size,
                    onRefresh = {
                        reloadRequest++
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Левая панель со списком матчей. Width влияет на ширину списка и карточек матчей.
                    MatchListPanel(
                        mediaItems = mediaItems,
                        selectedMediaItem = selectedMediaItem,
                        modifier = Modifier
                            .width(430.dp)
                            .fillMaxHeight(),
                        onMatchClick = { mediaItem ->
                            selectedMediaItem = mediaItem
                            acestreamStreams = emptyList()
                            browserStreams = emptyList()
                            hasSearchedStreams = false
                            isParsingStreams = true
                            onMediaItemClick(mediaItem)
                        }
                    )

                    // Правая панель: выбранный матч, логотипы команд и блок трансляций.
                    MatchDetailPanel(
                        mediaItem = selectedMediaItem,
                        streams = acestreamStreams,
                        browserStreams = browserStreams,
                        isParsingStreams = isParsingStreams,
                        hasSearchedStreams = hasSearchedStreams,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onSearchStreams = {
                            acestreamStreams = emptyList()
                            hasSearchedStreams = false
                            isParsingStreams = true
                        },
                        onStreamClick = { stream ->
                            try {
                                openAcestreamLink(context, stream.link)
                            } catch (e: Exception) {
                                android.util.Log.d("AceStreamCheck", "Failed to open acestream link: ${e.message}")
                                showInstallDialog = true
                            }
                        },
                        onBrowserStreamClick = { stream ->
                            selectedBrowserUrl = stream.link
                        }
                    )
                }
            }
        }
    }

    if (showInstallDialog) {
        InstallAceStreamDialog(
            onDismiss = { showInstallDialog = false },
            onInstall = {
                openPlayStore(context)
                showInstallDialog = false
            }
        )
    }

    selectedBrowserUrl?.let { url ->
        BrowserWebViewDialog(
            url = url,
            onDismiss = { selectedBrowserUrl = null }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Header(
    matchCount: Int,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = "Goal Stream",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "GoalStream",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Все матчи • $matchCount событий",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(text = "Все", selected = true)
            FilterChip(text = "Live", selected = false)
            FilterChip(text = "Скоро", selected = false)
            OutlinedButton(onClick = onRefresh) {
                Text("Обновить")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FilterChip(
    text: String,
    selected: Boolean
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF32D583) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0f else 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (selected) Color(0xFF03120C) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MatchListPanel(
    mediaItems: List<MediaItem>,
    selectedMediaItem: MediaItem?,
    modifier: Modifier = Modifier,
    onMatchClick: (MediaItem) -> Unit
) {
    Panel(modifier = modifier) {
        // Список матчей слева. contentPadding даёт место для focus-scale первой/последней карточки.
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mediaItems, key = { it.id }) { mediaItem ->
                MatchRow(
                    mediaItem = mediaItem,
                    isSelected = mediaItem.id == selectedMediaItem?.id,
                    onClick = { onMatchClick(mediaItem) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MatchRow(
    mediaItem: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 1f else 0f),
        interactionSource = interactionSource,
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (isSelected) Color(0x332ED47A) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color(0x4432D583)
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color(0xFF32D583))),
            border = if (isSelected) Border(BorderStroke(1.dp, Color(0xFF32D583))) else Border.None
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isLive(mediaItem.time)) Color(0xCCF04438) else Color.White.copy(alpha = 0.08f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayMatchTime(mediaItem.time),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mediaItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSelected) "Выбранный матч" else "OK - найти трансляции",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MatchDetailPanel(
    mediaItem: MediaItem?,
    streams: List<AcestreamStream>,
    browserStreams: List<BrowserStream>,
    isParsingStreams: Boolean,
    hasSearchedStreams: Boolean,
    modifier: Modifier = Modifier,
    onSearchStreams: () -> Unit,
    onStreamClick: (AcestreamStream) -> Unit,
    onBrowserStreamClick: (BrowserStream) -> Unit
) {
    Panel(modifier = modifier) {
        if (mediaItem == null) {
            EmptySelection()
            return@Panel
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя часть правой панели. weight(1f) отдаёт ей всё место над блоком трансляций.
            MatchHero(
                mediaItem = mediaItem,
                streamCount = streams.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Нижний блок правой панели с потоками. Height влияет на количество видимых рядов трансляций.
            StreamsSection(
                mediaItem = mediaItem,
                streams = streams,
                browserStreams = browserStreams,
                isParsingStreams = isParsingStreams,
                hasSearchedStreams = hasSearchedStreams,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(279.dp),
                onSearchStreams = onSearchStreams,
                onStreamClick = onStreamClick,
                onBrowserStreamClick = onBrowserStreamClick
            )
        }
    }
}

@Composable
private fun MatchHero(
    mediaItem: MediaItem,
    streamCount: Int,
    modifier: Modifier = Modifier
) {
    // Hero выбранного матча: слева название/meta, справа логотипы и полная дата/турнир.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(34.dp),
        horizontalArrangement = Arrangement.spacedBy(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Выбранный матч",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF7CD4FD),
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = mediaItem.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaPill(text = displayMatchTime(mediaItem.time))
                MetaPill(text = "AceStream")
                MetaPill(text = if (streamCount > 0) "$streamCount потоков" else "Проверка по выбору")
            }
        }

        TeamSummaryBlock(
            mediaItem = mediaItem,
            modifier = Modifier.width(380.dp)
        )
    }
}

@Composable
private fun TeamSummaryBlock(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier
) {
    // Правый визуальный блок hero: логотипы команд сверху, полная информация о матче снизу.
    Column(
        // Отступ сверху для блока логотипов справа. Увеличивай/уменьшай top, если лого слишком высоко/низко.
        modifier = modifier.padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TeamVisual(
            homeTeamImage = mediaItem.homeTeamImage,
            awayTeamImage = mediaItem.awayTeamImage,
            modifier = Modifier.fillMaxWidth()
        )

        MatchFullInfo(text = fullMatchInfo(mediaItem))
    }
}

@Composable
private fun MatchFullInfo(text: String) {
    // Плашка полной даты/турнира под логотипами. Padding и maxLines можно менять под длинные строки.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TeamVisual(
    homeTeamImage: String,
    awayTeamImage: String,
    modifier: Modifier = Modifier
) {
    // Строка логотипов команд. Размер VS и расстояния между лого регулируются здесь.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crest(imageUrl = homeTeamImage, fallback = "H", modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x2232D583)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VS",
                color = Color(0xFF32D583),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
        Crest(imageUrl = awayTeamImage, fallback = "A", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Crest(
    imageUrl: String,
    fallback: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = fallback,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamsSection(
    mediaItem: MediaItem,
    streams: List<AcestreamStream>,
    browserStreams: List<BrowserStream>,
    isParsingStreams: Boolean,
    hasSearchedStreams: Boolean,
    modifier: Modifier = Modifier,
    onSearchStreams: () -> Unit,
    onStreamClick: (AcestreamStream) -> Unit,
    onBrowserStreamClick: (BrowserStream) -> Unit
) {
    val streamCards = remember(streams, browserStreams) {
        buildStreamCards(
            acestreamStreams = streams,
            browserStreams = browserStreams
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 0.dp)
    ) {
        // Заголовок блока трансляций: справа полная дата/турнир выбранного матча.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fullMatchInfo(mediaItem),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        when {
            isParsingStreams -> StreamsLoading()
            streamCards.isNotEmpty() -> {
                // Сетка карточек трансляций. contentPadding и spacedBy нужны, чтобы focus-scale не обрезался.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        top = 18.dp,
                        end = 4.dp,
                        bottom = 18.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(streamCards, key = { it.id }) { stream ->
                        StreamCard(
                            stream = stream,
                            modifier = Modifier.padding(vertical = 6.dp),
                            onClick = {
                                when (stream.source) {
                                    StreamSource.AceStream -> stream.acestreamStream?.let(onStreamClick)
                                    StreamSource.Browser -> stream.browserStream?.let(onBrowserStreamClick)
                                }
                            }
                        )
                    }
                }
            }
            hasSearchedStreams -> NoStreamsState(onRefresh = onSearchStreams)
            else -> SearchPrompt(onSearchStreams = onSearchStreams)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StreamCard(
    stream: StreamCardUi,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Карточка одного потока. focusedScale, padding и тексты внутри влияют на обрезание при фокусе.
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isFocused) 1f else 0f),
        interactionSource = interactionSource,
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            focusedContainerColor = Color(0x4432D583)
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color(0xFF32D583)))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stream.primaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stream.secondaryText,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF32D583),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stream.sourceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stream.actionText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun SearchPrompt(onSearchStreams: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Трансляции ещё не проверялись",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Запусти поиск для выбранного матча. Потоки появятся здесь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onSearchStreams) {
            Text("Найти трансляции")
        }
    }
}

@Composable
private fun NoStreamsState(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Трансляций пока нет",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Они часто появляются ближе к началу матча. Можно попробовать обновить.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onRefresh) {
            Text("Повторить")
        }
    }
}

@Composable
private fun StreamsLoading() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(42.dp))
        Column {
            Text(
                text = "Ищем трансляции...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Парсим страницу матча и проверяем AceStream ссылки.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySelection() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Выберите матч слева",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Загружаем матчи GoalStream",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.08f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xDD0D1411),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        content()
    }
}

@Composable
fun InstallAceStreamDialog(
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
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
            shape = RoundedCornerShape(8.dp),
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

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Хотите установить Ace Stream?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

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

@Composable
fun BrowserWebViewDialog(
    url: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Browser трансляция",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        if (webView.url != url) {
                            webView.loadUrl(url)
                        }
                    }
                )
            }
        }
    }
}

fun isAceStreamInstalled(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("acestream://test"))
    val resolveInfo = context.packageManager.resolveActivity(intent, 0)

    if (resolveInfo != null) {
        android.util.Log.d("AceStreamCheck", "Found app that can handle acestream: ${resolveInfo.activityInfo.packageName}")
        return true
    }

    android.util.Log.d("AceStreamCheck", "No app found that can handle acestream intents")
    return false
}

fun openAcestreamLink(context: Context, link: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
    context.startActivity(intent)
}

fun openBrowserLink(context: Context, link: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
    context.startActivity(intent)
}

fun openPlayStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.acestream.node"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun extractBitrate(bitrate: String): Int {
    return Regex("\\d+").find(bitrate)?.value?.toIntOrNull() ?: 0
}

private fun isLive(time: String): Boolean {
    return time.contains("live", ignoreCase = true) || time.contains("сейчас", ignoreCase = true)
}

private fun displayMatchTime(time: String): String {
    if (isLive(time)) return "LIVE"
    return Regex("\\b\\d{1,2}:\\d{2}\\b").find(time)?.value ?: time.ifBlank { "--:--" }
}

private fun fullMatchInfo(mediaItem: MediaItem): String {
    return mediaItem.time.ifBlank { "Дата и турнир уточняются" }
}

private fun displayStreamQuality(quality: String): String {
    val cleanQuality = quality.trim().removeSuffix("%")
    return if (cleanQuality.isBlank()) "?%" else "$cleanQuality%"
}

private enum class StreamSource(
    val sortOrder: Int,
    val label: String
) {
    AceStream(sortOrder = 0, label = "Ace Stream источник"),
    Browser(sortOrder = 1, label = "Browser источник")
}

private data class StreamCardUi(
    val id: String,
    val source: StreamSource,
    val primaryText: String,
    val secondaryText: String,
    val sourceLabel: String,
    val actionText: String,
    val acestreamStream: AcestreamStream? = null,
    val browserStream: BrowserStream? = null
)

private fun buildStreamCards(
    acestreamStreams: List<AcestreamStream>,
    browserStreams: List<BrowserStream>
): List<StreamCardUi> {
    val aceCards = acestreamStreams.map { stream ->
        StreamCardUi(
            id = "ace-${stream.id}",
            source = StreamSource.AceStream,
            primaryText = stream.bitrate.ifBlank { "Поток" },
            secondaryText = displayStreamQuality(stream.quality),
            sourceLabel = StreamSource.AceStream.label,
            actionText = "▶ Смотреть",
            acestreamStream = stream
        )
    }

    val browserCards = browserStreams.map { stream ->
        StreamCardUi(
            id = stream.id,
            source = StreamSource.Browser,
            primaryText = stream.title,
            secondaryText = listOf(
                stream.bitrate,
                displayStreamQuality(stream.quality)
            ).filter { it.isNotBlank() && it != "?%" }.joinToString(" • ").ifBlank { "WebPlayer" },
            sourceLabel = StreamSource.Browser.label,
            actionText = "▶ Открыть",
            browserStream = stream
        )
    }

    return (aceCards + browserCards).sortedBy { it.source.sortOrder }
}
