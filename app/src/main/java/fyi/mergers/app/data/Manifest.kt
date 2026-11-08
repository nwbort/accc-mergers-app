package fyi.mergers.app.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * mergers.fyi's data/manifest.json: a short hash of every merger's detail
 * file and industry division file, and the matters currently in Phase 2. Written at deploy time by
 * scripts/generate/data_manifest.py in nwbort/accc-mergers.
 *
 * It lets the background check ask one question instead of one per followed
 * merger: when the site hasn't changed, the manifest is an empty 304 and every
 * followed merger is read from the copy kept on the phone.
 */
@Serializable
data class Manifest(
    val format: Int = 0,
    /** Merger id to [ManifestHash] of its mergers/{id}.json. */
    val mergers: Map<String, String> = emptyMap(),
    val phase2_current: List<String> = emptyList(),
    /** Industry division file stem ("G", "_orphans") to [ManifestHash] of industries/{stem}.json. */
    val industries: Map<String, String> = emptyMap(),
) {
    companion object {
        /** The shape this app reads; anything else is ignored and the app fetches as before. */
        const val FORMAT = 1
        const val PATH = "manifest.json"
    }
}

/**
 * The manifest's hash of a file: the first 12 hex digits of the SHA-256 of
 * its bytes. Must match file_hash() in data_manifest.py, which pins the same
 * test value as ManifestTest.
 */
object ManifestHash {
    private const val LENGTH = 12

    fun of(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(LENGTH)
    }
}
