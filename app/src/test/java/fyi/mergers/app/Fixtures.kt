package fyi.mergers.app

import fyi.mergers.app.data.DataSource
import fyi.mergers.app.data.Dates
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

    /** The snapshot's own date, so "upcoming" means the same thing every run. */
    fun pinToday() {
        Dates.clock = Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), Dates.SYDNEY)
    }

    fun read(path: String): String? =
        overrides[path] ?: Fixtures::class.java.classLoader!!.getResource("data/$path")?.readText()

    override suspend fun fetch(path: String): String {
        synchronized(fetched) { fetched += path }
        return read(path) ?: throw IOException("No fixture for $path")
    }

    override suspend fun cached(path: String): String? = null
}

/** The app with the fixtures for a network and no background schedule. */
class TestApp : MergersApp() {
    override fun onCreate() {
        Fixtures.pinToday()
        super.onCreate()
    }

    override fun dataSource(): DataSource = Fixtures

    override fun scheduleChecks() {}
}
