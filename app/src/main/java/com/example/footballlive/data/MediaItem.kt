package com.example.footballlive.data

data class MediaItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val aceStreamUrl: String,
    val time: String = "",
    val homeTeamImage: String = "",
    val awayTeamImage: String = "",
    val matchUrl: String = ""
)

data class AcestreamStream(
    val id: String,
    val bitrate: String,
    val quality: String,
    val link: String
)
