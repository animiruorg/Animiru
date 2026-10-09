package eu.kanade.tachiyomi.data.download.downloader

import java.net.URI

internal fun resolveUrl(base: String, relative: String): String =
    URI(base).resolve(relative).toString()
