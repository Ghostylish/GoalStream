package com.example.footballlive.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MatchParser {
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    private data class NewsPreview(
        val title: String,
        val publishedAt: String,
        val url: String
    )

    suspend fun parseFootballNews(): Result<List<NewsArticle>> = withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect("https://livetv901.me/ua/lenta/full/")
                .userAgent(userAgent)
                .timeout(10000)
                .get()

            val articles = doc.select("a[href]")
                .mapNotNull { anchor -> parseNewsListItem(anchor) }
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

    private fun parseNewsListItem(anchor: Element): NewsPreview? {
        val title = anchor.text()
            .replace(Regex("\\s+"), " ")
            .trim()
        val href = anchor.attr("href").trim()
        if (title.isBlank() || href.isBlank()) return null
        if (!Regex("/lenta/\\d+_").containsMatchIn(href)) return null

        val container = anchor.parents().firstOrNull { parent ->
            parent.select(".date, .tnws").isNotEmpty()
        } ?: return null

        val dateText = container.select(".date, .tnws").firstOrNull()
            ?.text()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: return null

        if (!dateText.contains("Футбол", ignoreCase = true)) return null

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

    private fun parseNewsArticlePage(
        url: String,
        fallbackTitle: String,
        publishedAt: String
    ): NewsArticle {
        return try {
            val doc: Document = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(10000)
                .get()

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
            ?.let { toAbsoluteAssetUrl(it) }
            .orEmpty()

        if (articleImage.isNotBlank()) return articleImage

        return doc.select("meta[property=og:image]").firstOrNull()
            ?.attr("content")
            ?.let { toAbsoluteAssetUrl(it) }
            .orEmpty()
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
            val doc: Document = Jsoup.connect("https://livetv901.me/ua/allupcomingsports/1/")
                .userAgent(userAgent)
                .timeout(10000)
                .get()
            
            val mediaItems = mutableListOf<MediaItem>()
            
            // Try to select the specific table using the provided JS path selector
            // JS path: body > table > tbody > tr > td:nth-child(2) > table > tbody > tr:nth-child(4) > td > table > tbody > tr > td:nth-child(2) > table > tbody > tr > td > table > tbody > tr:nth-child(2) > td > table > tbody > tr > td > table > tbody > tr > td > table:nth-child(5) > tbody > tr > td:nth-child(2) > table:nth-child(2)
            val specificTable = doc.select("body > table > tbody > tr > td:nth-child(2) > table > tbody > tr:nth-child(4) > td > table > tbody > tr > td:nth-child(2) > table > tbody > tr > td > table > tbody > tr:nth-child(2) > td > table > tbody > tr > td > table > tbody > tr > td > table:nth-child(5) > tbody > tr > td:nth-child(2) > table:nth-child(2)").first()
            
            val matchRows = if (specificTable != null) {
                specificTable.select("tbody tr td[colspan=\"2\"]")
            } else {
                // Fallback to general selector if specific table not found
                doc.select("table tbody tr td[colspan=\"2\"]")
            }
            
            matchRows.forEachIndexed { index, element ->
                val titleElement = element.select("a.live").first()
                val title = titleElement?.text() ?: "Unknown Match"
                val link = titleElement?.attr("href") ?: ""
                
                // Parse full match schedule info. Some pages keep the tournament
                // outside span.evdesc, so fallback to the whole row text.
                val evdesc = element.select("span.evdesc").first()
                val evdescText = evdesc?.text()
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
                
                // Convert relative match link to absolute
                val matchUrl = toAbsoluteUrl(link)
                
                mediaItems.add(
                    MediaItem(
                        id = index.toString(),
                        title = title,
                        imageUrl = "",
                        aceStreamUrl = "",
                        time = time,
                        matchUrl = matchUrl
                    )
                )
            }
            
            Result.success(mediaItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        try {
            val doc: Document = Jsoup.connect(matchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            
            // Find all img tags with itemprop="image" in the match page
            val images = doc.select("img[itemprop=\"image\"]")
            
            if (images.size >= 2) {
                val homeTeamImage = if (images[0].attr("src").startsWith("//")) {
                    "https:${images[0].attr("src")}"
                } else {
                    images[0].attr("src")
                }
                
                val awayTeamImage = if (images[1].attr("src").startsWith("//")) {
                    "https:${images[1].attr("src")}"
                } else {
                    images[1].attr("src")
                }
                
                Pair(homeTeamImage, awayTeamImage)
            } else {
                Pair("", "")
            }
        } catch (e: Exception) {
            Pair("", "")
        }
    }
    
    suspend fun parseMatchPageForAcestreamLinks(matchUrl: String): List<AcestreamStream> = withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(matchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            
            val streams = mutableListOf<AcestreamStream>()
            
            // Parse lnktbj class elements for acestream links
            val lnktbjTables = doc.select("table.lnktbj")
            
            lnktbjTables.forEachIndexed { index, table ->
                // Extract bitrate
                val bitrateElement = table.select("td.bitrate").first()
                val bitrate = bitrateElement?.text() ?: ""
                
                // Extract quality rating
                val qualityElement = table.select("td.rate div").first()
                val quality = qualityElement?.text()?.replace("%", "")?.trim() ?: ""

                val flagUrl = table.select("img[src*=linkflag]").first()
                    ?.attr("src")
                    ?.let { toAbsoluteAssetUrl(it) }
                    ?: ""
                
                // Extract acestream link
                val acestreamLinkElement = table.select("a[href^=acestream://]").first()
                val link = acestreamLinkElement?.attr("href") ?: ""
                
                if (link.isNotEmpty()) {
                    streams.add(
                        AcestreamStream(
                            id = index.toString(),
                            bitrate = bitrate,
                            quality = quality,
                            link = link,
                            flagUrl = flagUrl
                        )
                    )
                }
            }
            
            streams
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun parseMatchPageForAcestreamLink(matchUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(matchUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get()
            
            // Look for acestream links in the page
            val acestreamLinks = doc.select("a[href^=acestream://]")
            if (acestreamLinks.isNotEmpty()) {
                return@withContext acestreamLinks.first().attr("href")
            }
            
            // Also check for acestream links in text content
            val bodyText = doc.body().text()
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
