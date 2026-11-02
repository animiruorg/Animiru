package animiru.feature.cast

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import androidx.core.net.toUri
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import tachiyomi.core.common.util.system.logcat
import java.io.FileInputStream
import java.io.InputStream
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CastProxyServer(
    baseClient: OkHttpClient,
    private val contentResolver: ContentResolver,
    private val ipAddress: String,
    private val port: Int,
    private val json: Json,
) : NanoHTTPD(port) {
    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager =
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(naiveTrustManager), SecureRandom())
        }

        sslSocketFactory(sslContext.socketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    private val client = baseClient.newBuilder()
        .ignoreAllSSLErrors()
        .build()

    override fun serve(session: IHTTPSession?): Response? {
        val uri = session?.uri ?: return super.serve(session)
        return when {
            uri.startsWith("/local") -> localServe(session)
            uri.startsWith("/proxy") -> proxyServe(session)
            else -> super.serve(session)
        }
    }

    // Close the afd when nanohttpd closes the inputstream
    private class AfdStream(
        private val afd: AssetFileDescriptor,
        private val stream: FileInputStream,
    ) : InputStream() {
        override fun read(): Int = stream.read()
        override fun read(b: ByteArray): Int = stream.read(b)
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream.read(b, off, len)
        override fun skip(n: Long): Long = stream.skip(n)
        override fun available(): Int = stream.available()

        override fun close() {
            try {
                stream.close()
            } finally {
                afd.close()
            }
        }
    }

    private fun localServe(session: IHTTPSession): Response {
        val url = session.parameters["url"]?.firstOrNull() ?: return badRequest("No url param")
        val uri = try {
            url.toUri()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse uri" }
            return badRequest("Invalid uri")
        }
        val afd = try {
            contentResolver.openAssetFileDescriptor(url.toUri(), "r")
                ?: return notFound("Unable to open file")
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to open content uri: $url" }
            return notFound("File not found")
        }

        val mime = contentResolver.getType(uri) ?: guessMimeFromUri(uri)
        val fileLength = afd.length
        val rangeHeader = session.headers["range"]

        val stream = afd.createInputStream()
        val afdStream = AfdStream(afd, stream)

        if (rangeHeader != null && fileLength > 0) {
            try {
                val range = rangeHeader.replace("bytes=", "").split("-")
                val start = range.getOrNull(0)?.toLongOrNull() ?: 0L
                val end = range.getOrNull(1)?.toLongOrNull() ?: (fileLength - 1)
                val length = end - start + 1

                afdStream.skip(start)

                val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, afdStream, length)
                response.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                response.addHeader("Accept-Ranges", "bytes")
                return response.apply { addCorsHeaders(this) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Error processing Range header" }
            }
        }

        val response = if (fileLength >= 0) {
            newFixedLengthResponse(Response.Status.OK, mime, stream, fileLength)
        } else {
            newChunkedResponse(Response.Status.OK, mime, stream)
        }
        response.addHeader("Accept-Ranges", "bytes")
        return response.apply { addCorsHeaders(this) }
    }

    private fun guessMimeFromUri(uri: Uri): String {
        val ext = uri.lastPathSegment?.substringAfterLast(".", "") ?: ""
        return when (ext) {
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "wmv" -> "video/x-ms-wmv"
            else -> "application/octet-stream"
        }
    }

    private fun proxyServe(session: IHTTPSession): Response {
        val url = session.parameters["url"]?.firstOrNull() ?: return badRequest("No url param")
        val headers = session.parameters["header"]?.firstOrNull()
            ?.let { json.decodeFromString<Map<String, String>>(it) }
            ?: return badRequest("No header param")

        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.addHeader(name, value) }
        session.headers["range"]?.let { requestBuilder.addHeader("Range", it) }

        val resp = client.newCall(requestBuilder.build()).execute()
        val contentType = resp.header("Content-Type") ?: ""
        val status = Response.Status.lookup(resp.code) ?: Response.Status.OK

        val response = if (resp.isM3U8()) {
            val body = resp.body.string()
            val proxied = proxyM3U8(body, url, headers)
            newFixedLengthResponse(status, "application/vnd.apple.mpegurl", proxied)
        } else if (resp.isDash()) {
            val body = resp.body.string()
            val proxied = proxyDash(body, url, headers)
            newFixedLengthResponse(status, "application/dash+xml", proxied)
        } else {
            val body = resp.body
            val length = body.contentLength()
            val stream = body.byteStream()
            val mime = contentType.ifEmpty { "application/octet-stream" }
            val returnResp = if (length >= 0) {
                newFixedLengthResponse(status, mime, stream, length)
            } else {
                newChunkedResponse(status, mime, stream)
            }
            resp.header("Content-Range")?.let { returnResp.addHeader("Content-Range", it) }
            resp.header("Accept-Ranges")?.let { returnResp.addHeader("Accept-Ranges", it) }
            returnResp
        }

        return response.apply { addCorsHeaders(this) }
    }

    private fun okhttp3.Response.isM3U8(): Boolean {
        val contentType = header("Content-Type") ?: ""
        if (contentType.endsWith("vnd.apple.mpegurl") || contentType.endsWith("x-mpegURL")) {
            return true
        }

        val path = request.url.pathSegments.lastOrNull()
        return path?.endsWith(".m3u8") == true || path?.endsWith(".m3u") == true
    }

    private fun proxyM3U8(playlist: String, baseUrl: String, headers: Map<String, String>): String {
        return buildString {
            playlist.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> appendLine(line)
                    HLS_TAGS.any { trimmed.startsWith(it) } -> appendLine(
                        proxyAttrUris(line, baseUrl, headers),
                    )
                    trimmed.startsWith('#') -> appendLine(line)
                    else -> {
                        val full = resolveUrl(baseUrl, trimmed)
                        appendLine(getProxyUrl(full, headers))
                    }
                }
            }
        }
    }

    private fun proxyAttrUris(line: String, baseUrl: String, headers: Map<String, String>): String {
        return ATTRIBUTE_REGEX.replace(line) { m ->
            val uri = m.groupValues[1]
            val full = resolveUrl(baseUrl, uri)
            val proxied = getProxyUrl(full, headers)
            "URI=\"$proxied\""
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        if (relative.startsWith("//")) return "https:$relative"
        return try {
            URI(base).resolve(relative).toString()
        } catch (_: Exception) {
            relative
        }
    }

    private fun okhttp3.Response.isDash(): Boolean {
        val contentType = header("Content-Type") ?: ""
        if (contentType.endsWith("dash+xml")) {
            return true
        }

        val path = request.url.pathSegments.lastOrNull()
        return path?.endsWith(".mpd") == true
    }

    private fun proxyDash(manifest: String, baseUrl: String, headers: Map<String, String>): String {
        val document = Jsoup.parse(manifest, baseUrl, Parser.xmlParser()).apply {
            outputSettings().prettyPrint(false).syntax(Document.OutputSettings.Syntax.xml)
        }

        DASH_ATTRS.forEach { attr ->
            document.select("[$attr]").forEach { element ->
                val value = element.attr(attr)
                val full = resolveUrl(baseUrl, value)
                element.attr(attr, getDashProxyUrl(full, headers))
            }
        }

        return document.outerHtml()
    }

    // Since dash uses strings inside $...$ for something, we can't encode it in the proxy url
    private fun getDashProxyUrl(targetUrl: String, headers: Map<String, String>): String {
        val tokens = mutableListOf<String>()
        val prefix = "DASH_TOKEN_PREFIX"

        val placeholderUrl = DASH_TOKEN_REGEX.replace(targetUrl) { m ->
            val placeholder = "$prefix${tokens.size}END"
            tokens.add(m.value)
            placeholder
        }

        println(placeholderUrl)
        var proxied = getProxyUrl(placeholderUrl, headers)
        tokens.forEachIndexed { index, token ->
            proxied = proxied.replace("$prefix${index}END", token)
        }
        return proxied
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS, HEAD")
        response.addHeader("Access-Control-Allow-Headers", "origin, accept, content-type, authorization, range")
        response.addHeader("Access-Control-Expose-Headers", "content-range, content-length, accept-ranges")
    }

    private fun getProxyUrl(targetUrl: String, headers: Map<String, String>): String {
        return "http://$ipAddress:$port".toHttpUrl().newBuilder().apply {
            addPathSegment("proxy")
            addQueryParameter("url", targetUrl)
            addQueryParameter("header", json.encodeToString(headers))
        }.build().toString()
    }

    private fun notFound(message: String): Response {
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", message)
    }

    private fun badRequest(message: String): Response {
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", message)
    }

    companion object {
        private val ATTRIBUTE_REGEX = Regex("""URI="([^"]+)"""")
        private val HLS_TAGS = listOf("#EXT-X-KEY", "#EXT-X-MEDIA", "#EXT-X-I-FRAME-STREAM-INF")
        private val DASH_ATTRS = listOf("initialization", "media", "index")
        private val DASH_TOKEN_REGEX = Regex("""\$[A-Za-z0-9]*(?:%0\d+d)?\$""")
    }
}
