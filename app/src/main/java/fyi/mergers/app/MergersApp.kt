package fyi.mergers.app

import android.app.Application
import android.content.Context
import fyi.mergers.app.data.DataSource
import fyi.mergers.app.data.DigestSignup
import fyi.mergers.app.data.HttpDigestSignup
import fyi.mergers.app.data.HttpDataSource
import fyi.mergers.app.data.Repository
import fyi.mergers.app.notify.CheckWorker
import fyi.mergers.app.notify.Notifier
import fyi.mergers.app.notify.Push
import fyi.mergers.app.tracking.Tracker
import fyi.mergers.app.tracking.TrackingStore
import fyi.mergers.app.ui.theme.Appearance
import fyi.mergers.app.widget.UpcomingWidget
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
            Appearance.apply(this)
            Graph.init(this, dataSource(), digestSignup())
            Notifier.createChannels(this)
            scheduleChecks()
            startPush()
            UpcomingWidget.watch(this, Graph.scope)
        } catch (e: Throwable) {
            CrashReport.save(this, e)
        }
    }

    /** mergers.fyi itself; the tests swap in the JSON they carry. */
    protected open fun dataSource(): DataSource = HttpDataSource(File(cacheDir, "data"))

    /** The weekly digest's email signup; the tests swap in one that only records. */
    protected open fun digestSignup(): DigestSignup = HttpDigestSignup()

    protected open fun scheduleChecks() = CheckWorker.schedule(this)

    /** Firebase push, when this build has a project configured (notify/Push.kt). */
    protected open fun startPush() = Push.start(this)
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
    lateinit var signup: DigestSignup
        private set

    fun init(context: Context, source: DataSource, digestSignup: DigestSignup = HttpDigestSignup()) {
        repo = Repository(source)
        signup = digestSignup
        if (!::store.isInitialized) store = TrackingStore(context.applicationContext)
        tracker = Tracker(repo, store)
    }
}
