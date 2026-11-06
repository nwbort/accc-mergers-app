package fyi.mergers.app

import android.app.Application
import android.content.Context
import fyi.mergers.app.data.DataSource
import fyi.mergers.app.data.HttpDataSource
import fyi.mergers.app.data.Repository
import fyi.mergers.app.notify.CheckWorker
import fyi.mergers.app.notify.Notifier
import fyi.mergers.app.tracking.Tracker
import fyi.mergers.app.tracking.TrackingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

open class MergersApp : Application() {
    // Before onCreate, so it also catches crashes in library start-up code
    // (androidx.startup content providers run between the two).
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReport.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // A failure here would kill every launch before anything could show
        // it, so record it instead and let MainActivity put it on screen.
        try {
            Graph.init(this, dataSource())
            Notifier.createChannels(this)
            scheduleChecks()
        } catch (e: Throwable) {
            CrashReport.save(this, e)
        }
    }

    /** mergers.fyi itself; the tests swap in the JSON they carry. */
    protected open fun dataSource(): DataSource = HttpDataSource(File(cacheDir, "data"))

    protected open fun scheduleChecks() = CheckWorker.schedule(this)
}

/** The app's few singletons. The background check reaches them here too. */
object Graph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set
    lateinit var store: TrackingStore
        private set
    lateinit var tracker: Tracker
        private set

    fun init(context: Context, source: DataSource) {
        repo = Repository(source)
        if (!::store.isInitialized) store = TrackingStore(context.applicationContext)
        tracker = Tracker(repo, store)
    }
}
