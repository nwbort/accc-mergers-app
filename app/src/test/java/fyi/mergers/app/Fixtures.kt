package fyi.mergers.app

import fyi.mergers.app.data.DataSource
import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.DigestSignup
import fyi.mergers.app.data.SignupResult
import fyi.mergers.app.data.Manifest
import fyi.mergers.app.data.ManifestHash
import fyi.mergers.app.data.MergersJson
import fyi.mergers.app.data.Phase2Feed
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A snapshot of mergers.fyi's data (src/test/resources/data/, copied from the
 * accc-mergers repo on 6 Nov 2026), served in place of the network. A test can
 * replace a file to play out the site changing.
 */
object Fixtures : DataSource {
    val overrides = ConcurrentHashMap<String, String>()
    val fetched = mutableListOf<String>()

    /** What the phone has kept, as HttpDataSource keeps each file it fetches. */
    private val kept = ConcurrentHashMap<String, String>()

    /**
     * Whether the site serves manifest.json. Off, the app falls back to
     * fetching every followed merger, as it does until the site deploys one.
     */
    @Volatile
    var serveManifest = true

    /** The manifest the site would build from the snapshot as it stands (overrides included), as data_manifest.py would. */
    private fun manifest(): String {
        val dir = File(Fixtures::class.java.classLoader!!.getResource("data/mergers")!!.toURI())
        val file = Regex("^(MN|WA)-\\d+\\.json$")
        // A merger a test has put on the register, as well as the snapshot's.
        val added = overrides.keys.filter { it.startsWith("mergers/") }.map { it.removePrefix("mergers/") }
        val ids = (dir.list()!!.toList() + added).filter { file.matches(it) }.map { it.removeSuffix(".json") }.distinct()
        val phase2 = MergersJson.decodeFromString(Phase2Feed.serializer(), read("phase2.json")!!)
            .current.mapNotNull { it.merger_id }
        val m = Manifest(
            format = Manifest.FORMAT,
            mergers = ids.sorted().associateWith { ManifestHash.of(read("mergers/$it.json")!!) },
            phase2_current = phase2,
            industries = File(dir.parentFile, "industries").list()!!.filter { it.endsWith(".json") }.sorted()
                .associate { it.removeSuffix(".json") to ManifestHash.of(read("industries/$it")!!) },
        )
        return MergersJson.encodeToString(Manifest.serializer(), m)
    }

    /** The snapshot's own date, so "upcoming" means the same thing every run. */
    fun pinToday() {
        Dates.clock = Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), Dates.SYDNEY)
    }

    fun read(path: String): String? =
        overrides[path] ?: Fixtures::class.java.classLoader!!.getResource("data/$path")?.readText()

    override suspend fun fetch(path: String): String {
        synchronized(fetched) { fetched += path }
        val text = if (path == Manifest.PATH) {
            if (serveManifest) manifest() else throw IOException("No manifest served")
        } else {
            read(path) ?: throw IOException("No fixture for $path")
        }
        kept[path] = text
        return text
    }

    override suspend fun cached(path: String): String? = kept[path]
}

/** The app with the fixtures for a network, no background schedule and no push. */
class TestApp : MergersApp() {
    override fun onCreate() {
        Fixtures.pinToday()
        super.onCreate()
    }

    override fun dataSource(): DataSource = Fixtures

    override fun scheduleChecks() {}

    override fun startPush() {}

    override fun digestSignup(): DigestSignup = FakeSignup
}

/** The digest's email signup, recording each address instead of emailing it. */
object FakeSignup : DigestSignup {
    val asked = mutableListOf<String>()

    override suspend fun subscribe(email: String): SignupResult {
        synchronized(asked) { asked += email }
        return SignupResult.Sent("Check your inbox: we’ve sent a link to confirm.")
    }
}
