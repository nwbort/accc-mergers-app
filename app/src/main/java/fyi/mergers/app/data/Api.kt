package fyi.mergers.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Where the site lives. Documents and pages are linked relative to it. */
const val SITE = "https://mergers.fyi"

/** The site's own page for a merger, for sharing and opening in a browser. */
fun mergerPageUrl(id: String) = "$SITE/mergers/$id"

/**
 * A document link: the site's mirror (url_gh, a path on mergers.fyi, which
 * outlives the ACCC moving its files) when there is one, else the source.
 */
fun documentUrl(urlGh: String?, url: String?): String? {
    if (!urlGh.isNullOrBlank()) {
        // The mirror's paths carry raw spaces and the like; encode each segment.
        val path = urlGh.split('/').joinToString("/") { seg ->
            URLEncoder.encode(seg, Charsets.UTF_8).replace("+", "%20")
        }
        return SITE + path
    }
    return url?.takeIf { it.isNotBlank() }
}

/** Reads one of the site's JSON files by its path under /data/. */
interface DataSource {
    /** The freshest copy: the network, falling back to the last copy kept. */
    suspend fun fetch(path: String): String

    /** The last copy kept on the phone, without touching the network. */
    suspend fun cached(path: String): String?
}

/**
 * mergers.fyi over HTTPS, keeping each file on disk with its ETag. Cloudflare
 * answers a matching If-None-Match with an empty 304, so checking a followed
 * merger that hasn't moved costs a few hundred bytes, which is what makes
 * checking every couple of hours in the background cheap.
 */
class HttpDataSource(private val cacheDir: File, private val base: String = "$SITE/data/") : DataSource {

    private fun bodyFile(path: String) = File(cacheDir, path)
    private fun etagFile(path: String) = File(cacheDir, "$path.etag")

    override suspend fun cached(path: String): String? = withContext(Dispatchers.IO) {
        bodyFile(path).takeIf { it.exists() }?.readText()
    }

    override suspend fun fetch(path: String): String = withContext(Dispatchers.IO) {
        val body = bodyFile(path)
        val etag = etagFile(path)
        try {
            val conn = URI(base + path).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "mergers.fyi-android")
            if (body.exists() && etag.exists()) conn.setRequestProperty("If-None-Match", etag.readText())
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> body.readText()
                    HttpURLConnection.HTTP_OK -> {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        body.parentFile?.mkdirs()
                        // Write then rename, so a killed process never leaves half a file.
                        val tmp = File(body.parentFile, body.name + ".tmp")
                        tmp.writeText(text)
                        tmp.renameTo(body)
                        conn.getHeaderField("ETag")?.let { etag.writeText(it) } ?: etag.delete()
                        text
                    }
                    else -> throw IOException("HTTP $code for $path")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            // Offline, or the site is down: the last copy is better than nothing.
            if (body.exists()) body.readText() else throw e
        }
    }
}
