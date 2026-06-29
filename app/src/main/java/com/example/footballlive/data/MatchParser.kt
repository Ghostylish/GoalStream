package com.example.footballlive.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MatchParser {
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    private val requestTimeoutMs = 12_000
    private val retryDelayMs = 700L

    private data class NewsPreview(
        val title: String,
        val publishedAt: String,
        val url: String
    )

    private suspend fun fetchDocument(url: String, attempts: Int = 3): Document {
        var lastError: Exception? = null

        repeat(attempts) { attempt ->
            try {
                return Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer("https://livetv901.me/ua/")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .followRedirects(true)
                    .timeout(requestTimeoutMs)
                    .get()
            } catch (e: Exception) {
                lastError = e
                if (attempt < attempts - 1) {
                    delay(retryDelayMs)
                }
            }
        }

        throw lastError ?: IllegalStateException("Failed to load $url")
    }

    suspend fun parseFootballNews(): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        try {
            val doc: Document = fetchDocument("https://livetv901.me/ua/lenta/full/")

            val articles = parseNewsPreviews(doc)
                .distinctBy { it.url }
                .take(12)
                .map { preview ->
                    async {
                        parseNewsArticlePage(preview.url, preview.title, preview.publishedAt)
                    }
                }
                .awaitAll()

            Result.success(articles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseNewsPreviews(doc: Document): List<NewsPreview> {
        return doc.select(".date, .tnws")
            .filter { metaElement -> isFootballNewsMeta(metaElement) }
            .flatMap { metaElement ->
                val container = metaElement.parents().firstOrNull { parent ->
                    parent.select("a[href~=/lenta/\\d+_]").isNotEmpty()
                } ?: metaElement.parent()

                container?.select("a[href~=/lenta/\\d+_]")?.mapNotNull { anchor ->
                    parseNewsListItem(anchor, metaElement.text())
                }.orEmpty()
            }
    }

    private fun isFootballNewsMeta(element: Element): Boolean {
        return element.text().contains("Футбол", ignoreCase = true) ||
            element.select("a[href*=/lenta/sport/1/]").isNotEmpty()
    }

    private fun parseNewsListItem(anchor: Element, metaText: String? = null): NewsPreview? {
        val title = anchor.text()
            .replace(Regex("\\s+"), " ")
            .trim()
        val href = anchor.attr("href").trim()
        if (title.isBlank() || href.isBlank()) return null
        if (!Regex("/lenta/\\d+_").containsMatchIn(href)) return null

        val dateText = metaText?.replace(Regex("\\s+"), " ")?.trim() ?: run {
            val container = anchor.parents().firstOrNull { parent ->
            parent.select(".date, .tnws").isNotEmpty()
        } ?: return null

            container.select(".date, .tnws").firstOrNull()
            ?.text()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: return null
        }

        if (!dateText.contains("Футбол", ignoreCase = true) && metaText == null) return null

        val publishedAt = extractNewsPublishedAt(dateText)

        return NewsPreview(
            title = title,
            publishedAt = publishedAt,
            url = toAbsoluteUrl(href)
        )
    }

    private fun extractNewsPublishedAt(dateText: String): String {
        val normalized = dateText
            .replace("&ndash;", "–")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.contains("|")) {
            return normalized.substringBefore("|").trim()
        }

        val afterDash = normalized.substringAfter("–", normalized).trim()
        val withoutCategory = afterDash
            .replace(Regex("^Футбол\\s*,\\s*"), "")
            .trim()
            .trim(',')
            .trim()

        return withoutCategory.ifBlank { normalized }
    }

    private suspend fun parseNewsArticlePage(
        url: String,
        fallbackTitle: String,
        publishedAt: String
    ): NewsArticle {
        return try {
            val doc: Document = fetchDocument(url)

            val textElement = findNewsTextElement(doc)
            val bannerUrl = findNewsBannerUrl(doc, textElement)
            val body = extractNewsBody(textElement)

            NewsArticle(
                id = url,
                title = fallbackTitle,
                bannerUrl = bannerUrl,
                body = body.ifBlank { fallbackTitle },
                publishedAt = publishedAt,
                url = url
            )
        } catch (e: Exception) {
            NewsArticle(
                id = url,
                title = fallbackTitle,
                bannerUrl = "",
                body = fallbackTitle,
                publishedAt = publishedAt,
                url = url
            )
        }
    }

    private fun findNewsTextElement(doc: Document): Element? {
        val titleContainer = doc.select("h1.nwstitle").firstOrNull()?.parent()
        val articleText = titleContainer?.select("> div.text")?.firstOrNull()
        if (articleText != null) return articleText

        return doc.select("div.text")
            .filter { element ->
                element.select("p").isNotEmpty() || element.select("img[src]").any { image ->
                    image.attr("src").contains("img/images")
                }
            }
            .maxByOrNull { element -> element.text().length }
    }

    private fun findNewsBannerUrl(doc: Document, textElement: Element?): String {
        val articleImage = textElement?.select("img[src]")?.firstOrNull { image ->
            val src = image.attr("src")
            src.contains("img/images") || src.contains("img/nws")
        }
            ?.attr("src")
            ?.let { normalizeNewsBannerUrl(it) }
            .orEmpty()

        if (articleImage.isNotBlank()) return articleImage

        return doc.select("meta[property=og:image]").firstOrNull()
            ?.attr("content")
            ?.let { normalizeNewsBannerUrl(it) }
            .orEmpty()
    }

    private fun normalizeNewsBannerUrl(url: String): String {
        val absoluteUrl = toAbsoluteAssetUrl(url)
        return if (absoluteUrl.endsWith("/img/oglogo.png", ignoreCase = true)) {
            ""
        } else {
            absoluteUrl
        }
    }

    private fun extractNewsBody(textElement: Element?): String {
        if (textElement == null) return ""

        val paragraphs = textElement.select("p")
            .map { paragraph ->
                paragraph.text()
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }
            .filter { it.isNotBlank() }

        if (paragraphs.isNotEmpty()) {
            return paragraphs.joinToString("\n\n")
        }

        val cleanElement = textElement.clone()
        cleanElement.select("script, style, iframe, img").remove()
        return cleanElement.text()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    suspend fun parseMatches(): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            var mediaItems = emptyList<MediaItem>()

            for (attempt in 0 until 3) {
                try {
                    val doc = fetchDocument("https://livetv901.me/ua/allupcomingsports/1/", attempts = 1)
                    mediaItems = parseMatchesDocument(doc)
                } catch (e: Exception) {
                    mediaItems = emptyList()
                }

                if (mediaItems.isNotEmpty()) {
                    break
                }

                if (attempt < 2) {
                    delay(retryDelayMs)
                }
            }

            if (mediaItems.isEmpty()) {
                Result.failure(IllegalStateException("No matches parsed from LiveTV"))
            } else {
                Result.success(mediaItems)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMatchesDocument(doc: Document): List<MediaItem> {
        val specificTable = doc.select(
            "body > table > tbody > tr > td:nth-child(2) > table > tbody > tr:nth-child(4) > td > table > tbody > tr > td:nth-child(2) > table > tbody > tr > td > table > tbody > tr:nth-child(2) > td > table > tbody > tr > td > table > tbody > tr > td > table:nth-child(5) > tbody > tr > td:nth-child(2) > table:nth-child(2)"
        ).firstOrNull()

        val fastRows = specificTable
            ?.select("tbody tr td[colspan=\"2\"]:has(a.live[href])")
            .orEmpty()

        val fastItems = parseMatchRows(fastRows)
        if (fastItems.isNotEmpty()) return fastItems

        return parseMatchLinks(doc.select("a.live[href], a[href*=transmatch], a[href*=/eventinfo/]"))
    }

    private fun parseMatchRows(rows: List<Element>): List<MediaItem> {
        return rows
            .mapNotNull { element ->
                val titleElement = element.select("a.live[href]").firstOrNull() ?: return@mapNotNull null
                parseMatchElement(titleElement, element)
            }
            .distinctBy { it.matchUrl }
    }

    private fun parseMatchLinks(links: List<Element>): List<MediaItem> {
        return links
            .mapNotNull { titleElement ->
                val element = titleElement.parents().firstOrNull { parent ->
                    parent.`is`("td[colspan=2]") || parent.select("span.evdesc").isNotEmpty()
                } ?: titleElement.parent()
                if (element == null) return@mapNotNull null

                parseMatchElement(titleElement, element)
            }
            .distinctBy { it.matchUrl }
    }

    private fun parseMatchElement(titleElement: Element, element: Element): MediaItem? {
            val title = titleElement.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            val link = titleElement.attr("href").trim()
        if (title.isBlank() || link.isBlank()) return null

            // Parse full match schedule info. Some pages keep the tournament
            // outside span.evdesc, so fallback to the whole row text.
            val evdescText = element.select("span.evdesc").firstOrNull()
                ?.text()
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?: ""
            val rowText = element.text()
                .replace(Regex("\\s+"), " ")
                .trim()
            val rowInfo = rowText
                .removePrefix(title)
                .trim()
                .removePrefix("-")
                .trim()
            val time = when {
                rowInfo.contains(Regex("\\d{1,2}:\\d{2}")) && rowInfo.length > evdescText.length -> rowInfo
                else -> evdescText
            }

        return MediaItem(
            id = toAbsoluteUrl(link),
            title = title,
            imageUrl = "",
            aceStreamUrl = "",
            time = time,
            matchUrl = toAbsoluteUrl(link)
        )
    }
    
    suspend fun loadTeamImages(mediaItems: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val updatedItems = mediaItems.map { item ->
            async {
                if (item.matchUrl.isNotEmpty()) {
                    try {
                        val teamImages = parseMatchPageForTeamImages(item.matchUrl)
                        item.copy(
                            homeTeamImage = teamImages.first,
                            awayTeamImage = teamImages.second
                        )
                    } catch (e: Exception) {
                        item // Return original if parsing fails
                    }
                } else {
                    item
                }
            }
        }
        updatedItems.awaitAll()
    }
    
    suspend fun loadAcestreamLinks(mediaItems: List<MediaItem>): List<MediaItem> = withContext(Dispatchers.IO) {
        val updatedItems = mediaItems.map { item ->
            async {
                if (item.matchUrl.isNotEmpty()) {
                    try {
                        val acestreamLink = parseMatchPageForAcestreamLink(item.matchUrl)
                        if (acestreamLink.isNotEmpty()) {
                            item.copy(aceStreamUrl = acestreamLink)
                        } else {
                            item
                        }
                    } catch (e: Exception) {
                        item // Return original if parsing fails
                    }
                } else {
                    item
                }
            }
        }
        updatedItems.awaitAll()
    }
    
    private suspend fun parseMatchPageForTeamImages(matchUrl: String): Pair<String, String> = withContext(Dispatchers.IO) {
        repeat(3) { attempt ->
            try {
                val doc: Document = fetchDocument(matchUrl, attempts = 1)

                // Find all img tags with itemprop="image" in the match page
                val images = doc.select("img[itemprop=\"image\"]")

                if (images.size >= 2) {
                    return@withContext Pair(
                        toAbsoluteAssetUrl(images[0].attr("src")),
                        toAbsoluteAssetUrl(images[1].attr("src"))
                    )
                }
            } catch (e: Exception) {
                // Try again below. Missing images should not break match loading.
            }

            if (attempt < 2) {
                delay(retryDelayMs)
            }
        }

        Pair("", "")
    }
    
    suspend fun parseMatchPageForAcestreamLinks(matchUrl: String): List<AcestreamStream> = withContext(Dispatchers.IO) {
        var streams = emptyList<AcestreamStream>()

        repeat(3) { attempt ->
            try {
                val doc = fetchDocument(matchUrl, attempts = 1)
                streams = parseAcestreamStreamsDocument(doc)

                if (streams.isNotEmpty()) {
                    return@withContext streams
                }
            } catch (e: Exception) {
                // Try again below. Empty list is still a valid final result.
            }

            if (attempt < 2) {
                delay(retryDelayMs)
            }
        }

        streams
    }

    private fun parseAcestreamStreamsDocument(doc: Document): List<AcestreamStream> {
        val streams = mutableListOf<AcestreamStream>()

        // Parse lnktbj class elements for acestream links
        val lnktbjTables = doc.select("table.lnktbj")

        lnktbjTables.forEachIndexed { index, table ->
            // Extract bitrate
            val bitrate = table.select("td.bitrate").firstOrNull()?.text() ?: ""

            // Extract quality rating
            val quality = table.select("td.rate div").firstOrNull()
                ?.text()
                ?.replace("%", "")
                ?.trim()
                ?: ""

            val flagUrl = table.select("img[src*=linkflag]").firstOrNull()
                ?.attr("src")
                ?.let { toAbsoluteAssetUrl(it) }
                ?: ""

            // Extract acestream link
            val link = table.select("a[href^=acestream://]").firstOrNull()?.attr("href") ?: ""

            if (link.isNotEmpty()) {
                streams.add(
                    AcestreamStream(
                        id = link,
                        bitrate = bitrate,
                        quality = quality,
                        link = link,
                        flagUrl = flagUrl
                    )
                )
            }
        }

        return streams.distinctBy { it.link }
    }

    suspend fun parseMatchPageForAcestreamLink(matchUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val doc: Document = fetchDocument(matchUrl)
            
            // Look for acestream links in the page
            val acestreamLinks = doc.select("a[href^=acestream://]")
            acestreamLinks.firstOrNull()?.attr("href")?.let { link ->
                return@withContext link
            }
            
            // Also check for acestream links in text content
            val bodyText = doc.body()?.text().orEmpty()
            val acestreamMatches = Regex("acestream://[a-f0-9]{32,}").find(bodyText)
            if (acestreamMatches != null) {
                return@withContext acestreamMatches.value
            }
            
            // Check all links for acestream
            val allLinks = doc.select("a[href]")
            for (link in allLinks) {
                val href = link.attr("href")
                if (href.contains("acestream")) {
                    val match = Regex("acestream://[a-f0-9]{32,}").find(href)
                    if (match != null) {
                        return@withContext match.value
                    }
                }
            }
            
            ""
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun generateAceStreamId(index: Int): String {
        // Generate a placeholder acestream ID
        // In a real app, this would come from the parsed data or API
        val chars = "0123456789abcdef"
        return (1..32).map { chars.random() }.joinToString("")
    }

    private fun toAbsoluteUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://livetv901.me$url"
            else -> url
        }
    }

    private fun toAbsoluteAssetUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://livetv901.me$url"
            else -> url
        }
    }

}
