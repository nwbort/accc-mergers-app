package fyi.mergers.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** What came of asking for the weekly digest by email. */
sealed interface SignupResult {
    /** A link to confirm is on its way (or was, within the hour). */
    data class Sent(val message: String) : SignupResult

    /** It didn't go: the worker's reason, or that it couldn't be reached. */
    data class Failed(val message: String) : SignupResult
}

/** Signs an address up for the weekly digest email. */
fun interface DigestSignup {
    suspend fun subscribe(email: String): SignupResult
}

/**
 * The site's signup worker (accc-mergers: workers/mergers-digest-signup),
 * on the app's own route. The website's route wants a Turnstile token, which
 * only a browser can get; this one emails the address a link to confirm
 * instead (double opt-in), and adds it to the list only once that's pressed.
 */
class HttpDigestSignup(private val url: String = "https://signup.mergers.fyi/app-signup") : DigestSignup {

    @Serializable
    private data class Reply(val success: Boolean = false, val message: String? = null, val error: String? = null)

    override suspend fun subscribe(email: String): SignupResult = withContext(Dispatchers.IO) {
        try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "mergers.fyi-android")
                val body = MergersJson.encodeToString(mapOf("email" to email))
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val reply = runCatching { MergersJson.decodeFromString(Reply.serializer(), text) }.getOrNull()
                if (code in 200..299 && reply?.success == true) {
                    SignupResult.Sent(reply.message ?: "Check your inbox: we’ve sent a link to confirm.")
                } else {
                    SignupResult.Failed(reply?.error ?: "Couldn’t sign you up just now. Please try again.")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            SignupResult.Failed("Couldn’t reach mergers.fyi. Check your connection and try again.")
        }
    }
}
