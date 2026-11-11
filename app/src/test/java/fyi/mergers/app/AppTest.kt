package fyi.mergers.app

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import fyi.mergers.app.widget.EventKind
import fyi.mergers.app.widget.SearchWidget
import fyi.mergers.app.widget.UpcomingConfig
import fyi.mergers.app.widget.UpcomingWidget
import fyi.mergers.app.widget.UpcomingWidgetConfigActivity
import fyi.mergers.app.widget.WidgetPrefs
import org.robolectric.Robolectric
import android.Manifest
import android.app.NotificationManager
import android.app.UiModeManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import fyi.mergers.app.data.IndustryDivisionFile
import fyi.mergers.app.data.IndustryMerger
import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerEvent
import fyi.mergers.app.data.MergersJson
import fyi.mergers.app.notify.CheckWorker
import fyi.mergers.app.notify.Push
import fyi.mergers.app.ui.AppViewModel
import fyi.mergers.app.ui.Route
import fyi.mergers.app.ui.theme.Appearance
import fyi.mergers.app.ui.theme.ThemeChoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the real app through every screen on the JVM, against a snapshot of
 * the site's data, so a crash anywhere in composition fails the build. The
 * PNGs it leaves in app/build/screenshots/ are for checking layout without a
 * phone to hand.
 *
 * One test method on purpose: the app's singletons and DataStore files belong
 * to one Application, and Robolectric makes a new one per test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    @Test
    fun browse_follow_and_get_notified() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // Browsing and the Following screen first run without a manifest, as
        // before the site deployed one; the background checks below use it.
        Fixtures.serveManifest = false

        // Dashboard
        waitForText("Recent determinations")
        waitForText("Mergers")
        shot("1-dashboard")
        // Partway down Recent determinations, its title stays pinned at the top.
        compose.onNodeWithTag("dashboard").performScrollToIndex(5)
        shot("1b-dashboard-scrolled")
        // Partway down a day in Coming up, that day stays pinned too.
        compose.onNodeWithTag("dashboard").performScrollToKey("u-MN-55046-consultation_due-2026-11-12T12:00:00Z")
        shot("1c-dashboard-coming-up")
        // Recent events, below Coming up: newest day first, its day pinned too.
        compose.onNodeWithTag("dashboard").performScrollToKey("h-Recent events")
        shot("1d-dashboard-recent-events")
        compose.onNodeWithTag("dashboard").performScrollToKey("r-MN-55024-remedy_offer-2026-11-04T12:00:00Z-PSP Investments - AAA Egg Company - remedy offer - 4 November 2026")
        shot("1e-dashboard-recent-events-day")
        compose.onNodeWithTag("dashboard").performScrollToIndex(0)

        // Settings, from the cog on the dashboard's top bar. Dark is kept,
        // handed to the system, and the dashboard behind it is drawn dark.
        assertEquals(ThemeChoice.SYSTEM, vm.theme)
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Appearance")
        shot("1f-settings")
        compose.onNodeWithText("Dark").performClick()
        waitUntil { vm.theme == ThemeChoice.DARK }
        assertEquals(ThemeChoice.DARK, Appearance.theme(app))
        assertEquals(
            UiModeManager.MODE_NIGHT_YES,
            shadowOf(app.getSystemService(UiModeManager::class.java)).applicationNightMode,
        )
        shot("1g-settings-dark")
        back()
        waitUntil { vm.backStack.lastOrNull() == Route.Dashboard }
        shot("1h-dashboard-dark")
        // The phone's App info ("Additional settings in the app") opens it too.
        compose.activity.onNewIntent(Intent(Intent.ACTION_APPLICATION_PREFERENCES))
        waitUntil { vm.backStack.lastOrNull() == Route.Settings }
        compose.onNodeWithText("System").performClick()
        waitUntil { vm.theme == ThemeChoice.SYSTEM }
        assertEquals(ThemeChoice.SYSTEM, Appearance.theme(app))
        back()
        waitUntil { vm.backStack.lastOrNull() == Route.Dashboard }

        // Search from the dashboard's top bar: industries and mergers together,
        // the cursor already in the field.
        compose.onNodeWithContentDescription("Search").performClick()
        waitUntil { vm.backStack.lastOrNull() == Route.Search && !vm.focusSearch }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNode(hasSetTextAction()).performTextInput("retail")
        waitForText("Industries (26)")
        waitForText("Mergers", substring = true)
        shot("1d-search")
        compose.onNodeWithText("Show all 26 industries").performClick()
        compose.onNodeWithTag("search-results").performScrollToKey("i-4213")
        waitForText("Houseware Retailing")
        // An industry result opens its page; back returns to the search as it was.
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("supermarket")
        waitForText("Supermarket and Grocery Stores")
        compose.onNodeWithText("Supermarket and Grocery Stores").performClick()
        waitForText("ANZSIC 4110")
        assertEquals(listOf(Route.Dashboard, Route.Search, Route.Industry("4110")), vm.backStack.toList())
        back()
        waitForText("Supermarket and Grocery Stores")
        // Lots of mergers match: "See all" hands the search to the merger list.
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("pty")
        compose.onNodeWithTag("search-results").performScrollToKey("more-mergers")
        compose.onNodeWithText("See all", substring = true).performClick()
        waitUntil { vm.backStack.toList() == listOf(Route.Mergers) }
        assertEquals("pty", vm.filter.query)
        compose.runOnUiThread {
            vm.filter = fyi.mergers.app.ui.ListFilter()
            vm.selectTab(Route.Dashboard)
        }
        waitUntil { vm.tab == Route.Dashboard }
        waitForText("Recent determinations")

        // The merger list, swiped to from the dashboard, searched and cleared.
        compose.onNodeWithTag("dashboard").performTouchInput { swipeLeft() }
        waitUntil { vm.tab == fyi.mergers.app.ui.Route.Mergers }
        waitForText("40 matters")
        shot("2-mergers")
        compose.onNode(hasSetTextAction()).performTextInput("asahi")
        waitForText("1 matter")
        compose.onNode(hasSetTextAction()).performTextClearance()
        waitForText("40 matters")

        // Press and hold a card to follow it from the list.
        compose.onNode(hasSetTextAction()).performTextInput("MN-01068")
        waitForText("1 matter")
        compose.onAllNodesWithText("Kalgoorlie", substring = true).onFirst().performTouchInput { longClick() }
        waitForText("Follow")
        shot("2b-mergers-hold")
        compose.onNodeWithText("Follow").performClick()
        waitUntil { Graph.store.current().isTracked("MN-01068") }
        compose.runOnUiThread { vm.toggleFollow("MN-01068") }
        waitUntil { !Graph.store.current().isTracked("MN-01068") }
        compose.onNode(hasSetTextAction()).performTextClearance()
        waitForText("40 matters")

        // Industries, from the list's link: searched, opened, followed, and up
        // the breadcrumb to its division.
        compose.onNodeWithText("By industry").performClick()
        waitForText("350 of 350 industries", substring = true)
        shot("2c-industries")
        compose.onNode(hasSetTextAction()).performTextInput("supermarket")
        waitForText("1 of 350 industries", substring = true)
        compose.onNodeWithText("Supermarket and Grocery Stores").performClick()
        waitForText("ANZSIC 4110")
        waitForText("Under assessment")
        shot("2d-industry")
        compose.onNodeWithContentDescription("Follow industry").performClick()
        waitUntil { Graph.store.current().isIndustryTracked("4110") }
        compose.onNodeWithText("Retail Trade").performClick()
        waitForText("ANZSIC G")
        waitForText("Sub-industries")
        shot("2e-industry-division")
        // Down to a sub-industry and back up: the breadcrumb returns to the
        // division already open rather than stacking another copy.
        compose.onNodeWithText("Food Retailing").performClick()
        waitForText("ANZSIC 41")
        compose.onNodeWithText("Retail Trade").performClick()
        waitForText("ANZSIC G")
        assertEquals(
            listOf(Route.Mergers, Route.Industries, Route.Industry("4110"), Route.Industry("G")),
            vm.backStack.toList(),
        )
        back(); back(); back()
        waitUntil { vm.backStack.size == 1 }

        // A live matter: open it and follow it.
        compose.onAllNodesWithText("Autosports Group", substring = true).onFirst().performClick()
        waitForText("Consultation responses due")
        shot("3-detail-live")
        // Scrolled past its header, the bar wears the header's colour and the merger's name.
        compose.onNodeWithTag("detail").performScrollToIndex(4)
        waitForText("Autosports Group - Mercedes-Benz", substring = true)
        shot("3b-detail-scrolled")
        compose.onNodeWithTag("detail").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Follow").performClick()
        waitUntil { Graph.store.current().isTracked("MN-20046") }
        waitForText("Following: you", substring = true)
        back()

        // A decided matter under appeal and judicial review.
        compose.runOnUiThread { vm.openMerger("MN-01068") }
        waitForText("Tribunal hearing")
        shot("4-detail-appeal")

        // Following a declined waiver also follows the notification it was
        // re-filed as, once the follow list is next brought up to date.
        compose.runOnUiThread { vm.openMerger("WA-01087") }
        waitForText("Re-filed as")
        shot("5-detail-waiver")
        compose.onNodeWithContentDescription("Follow").performClick()
        waitUntil { Graph.store.current().isTracked("WA-01087") }
        compose.runOnUiThread { vm.selectTab(fyi.mergers.app.ui.Route.Following) }
        waitUntil { Graph.store.current().isTracked("MN-20013") }
        waitForText("Mergers you follow (3)")
        waitForText("New", substring = true)
        shot("6-following")
        // The alert switches live in Settings; Following links there, saying how they're set.
        compose.onNodeWithTag("following").performScrollToKey("alerts")
        waitForText("Phase 2 matters", substring = true)
        shot("6b-following-alerts")
        compose.onNodeWithText("Alerts").performClick()
        waitForText("Every new filing")
        shot("6c-settings-from-following")
        back()
        waitUntil { vm.backStack.lastOrNull() == Route.Following }
        compose.onNodeWithTag("following").performScrollToIndex(0)

        // The followed matter moves on: the background check notices and posts it.
        val m = MergersJson.decodeFromString(MergerDetail.serializer(), Fixtures.read("mergers/MN-20046.json")!!)
        val moved = m.copy(
            end_of_determination_period = "2027-01-15T12:00:00Z",
            events = listOf(
                MergerEvent(
                    date = "2026-11-10T12:00:00Z",
                    title = "Timeline extended",
                    display_title = "Timeline extended by 10 business days",
                ),
            ) + m.events,
        )
        Fixtures.overrides["mergers/MN-20046.json"] = MergersJson.encodeToString(MergerDetail.serializer(), moved)

        // And a merger is filed in the followed industry.
        val g = MergersJson.decodeFromString(IndustryDivisionFile.serializer(), Fixtures.read("industries/G.json")!!)
        val filed = IndustryMerger(
            merger_id = "MN-99001",
            merger_name = "Grocer – Corner store",
            status = "Under assessment",
            phase = "Phase 1",
            notification_date = "2026-11-09T12:00:00Z",
        )
        val supermarkets = g.nodes.getValue("4110")
        val grown = g.copy(
            nodes = g.nodes + ("4110" to supermarkets.copy(mergers = listOf(filed.merger_id) + supermarkets.mergers)),
            mergers = g.mergers + (filed.merger_id to filed),
        )
        Fixtures.overrides["industries/G.json"] = MergersJson.encodeToString(IndustryDivisionFile.serializer(), grown)
        Fixtures.serveManifest = true
        val result = runBlocking { TestListenableWorkerBuilder<CheckWorker>(app).build().doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        val autosports = posted.single { it.extras.getCharSequence("android.title").toString().startsWith("Autosports") }
        val text = autosports.extras.getCharSequence("android.text").toString()
        val lines = autosports.extras.getCharSequenceArray("android.textLines").orEmpty().map { it.toString() }
        // The new document, then the moved deadline; not the history from before the follow.
        assertEquals("Extended by 10 business days +1 more", text)
        assertEquals(listOf("Extended by 10 business days", "Determination due 15 Jan 2027"), lines)
        // The industry's one new filing; nothing from before it was followed.
        val industry = posted.single { it.extras.getCharSequence("android.title").toString() == "Supermarket and Grocery Stores" }
        assertEquals(
            "New merger: Grocer – Corner store",
            industry.extras.getCharSequence("android.text").toString(),
        )

        // A second check finds nothing new to say, and with nothing changed on
        // the site it asks for the manifest alone: every followed merger and
        // industry is read from the copy kept on the phone.
        val before = posted.size
        val fetchedBefore = synchronized(Fixtures.fetched) { Fixtures.fetched.size }
        runBlocking { TestListenableWorkerBuilder<CheckWorker>(app).build().doWork() }
        assertEquals(
            before,
            shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.size,
        )
        val secondCheck = synchronized(Fixtures.fetched) { Fixtures.fetched.drop(fetchedBefore) }
        assertEquals(listOf("manifest.json"), secondCheck)

        // A mergers.fyi link (as a notification sends) opens that merger.
        compose.activity.onNewIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://mergers.fyi/mergers/MN-01016/asahi-warehouse")),
        )
        waitForText("Asahi – Warehouse site on Tilburn Rd, Deer Park (Vic)")
        shot("7-detail-decided")

        // A page the app has no screen for goes to the browser, and the app stays put.
        val phase2 = "https://mergers.fyi/phase-2"
        compose.activity.onNewIntent(Intent(Intent.ACTION_VIEW, Uri.parse(phase2)))
        val handedOn = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, handedOn.action)
        assertEquals(phase2, handedOn.dataString)
        waitForText("Asahi – Warehouse site on Tilburn Rd, Deer Park (Vic)")

        // A number someone knows before the register has it (a waiver appears
        // only once decided): search says so, and offers to follow it anyway.
        compose.runOnUiThread { vm.openSearch() }
        waitUntil { vm.backStack.lastOrNull() == Route.Search }
        compose.onNode(hasSetTextAction()).performTextInput("wa 99001")
        waitForText("WA-99001 isn't on the register")
        shot("8-search-unlisted")
        compose.onNodeWithText("Follow anyway").performClick()
        waitUntil { Graph.store.current().isAwaiting("WA-99001") }
        waitForText("Following WA-99001")
        // Typed without the hyphen, a number the register has finds that merger.
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("mn01016")
        waitForText("Asahi", substring = true)
        assertTrue(compose.onAllNodesWithTag("not-on-register").fetchSemanticsNodes().isEmpty())

        // Checks before it appears neither ask for its file nor count it a failure to retry.
        val fetchedBeforeWaiting = synchronized(Fixtures.fetched) { Fixtures.fetched.size }
        assertEquals(
            ListenableWorker.Result.success(),
            runBlocking { TestListenableWorkerBuilder<CheckWorker>(app).build().doWork() },
        )
        assertTrue("mergers/WA-99001.json" !in synchronized(Fixtures.fetched) { Fixtures.fetched.drop(fetchedBeforeWaiting) })
        assertTrue(runBlocking { Graph.store.current() }.isAwaiting("WA-99001"))
        // The site messages its own topic when it first appears.
        assertTrue("merger-WA-99001" in Push.topics(runBlocking { Graph.store.current() }))

        compose.runOnUiThread { vm.selectTab(Route.Following) }
        compose.onNodeWithTag("following").performScrollToKey("f-WA-99001")
        waitForText("Not on the register yet")
        shot("9-following-waiting")
        compose.onNodeWithText("Not on the register yet").performClick()
        waitForText("It isn't on the register yet", substring = true)
        shot("10-detail-waiting")

        // It's decided and published: the next check tells the follower.
        val template = MergersJson.decodeFromString(MergerDetail.serializer(), Fixtures.read("mergers/WA-01087.json")!!)
        Fixtures.overrides["mergers/WA-99001.json"] = MergersJson.encodeToString(
            MergerDetail.serializer(),
            template.copy(merger_id = "WA-99001", merger_name = "Acquirer – Waited-for target", related_merger = null),
        )
        val shownBefore = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.size
        runBlocking { TestListenableWorkerBuilder<CheckWorker>(app).build().doWork() }
        val nowPosted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        assertTrue(nowPosted.size > shownBefore)
        assertTrue(nowPosted.any { it.extras.getCharSequence("android.title").toString() == "Acquirer – Waited-for target" })
        assertFalse(runBlocking { Graph.store.current() }.isAwaiting("WA-99001"))
        Fixtures.overrides.remove("mergers/WA-99001.json")

        // A link to a number the register doesn't have says so, rather than failing to load.
        compose.activity.onNewIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://mergers.fyi/mergers/MN-99999")))
        waitForText("MN-99999 isn't on the register")

        // ---- home-screen widgets ----------------------------------------------

        // The Search widget's tap lands in search, cursor in the field.
        widgetShot("9-widget-search", SearchWidget.views(app), 380, 56)
        compose.activity.onNewIntent(Intent(MainActivity.ACTION_SEARCH))
        waitUntil { vm.backStack.lastOrNull() == Route.Search && !vm.focusSearch }
        compose.onNode(hasSetTextAction()).assertIsFocused()

        // Coming up as it's added: every merger, every kind of date.
        val events = runBlocking { Graph.repo.keptUpcoming() }!!
        val followed = runBlocking { Graph.store.current() }.trackedSet
        widgetShot("9b-widget-upcoming", UpcomingWidget.views(app, UpcomingConfig(), events, followed), 380, 420)
        // Nothing kept yet, and nothing followed.
        widgetShot("9c-widget-upcoming-empty", UpcomingWidget.views(app, UpcomingConfig(), null, followed), 380, 200)

        // Its settings: leave out consultations, and keep to what's followed.
        // (Putting one on the home screen draws it from what's kept, and
        // queues a fetch for when there's a network.)
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val widgetId = shadowOf(AppWidgetManager.getInstance(app))
            .createWidget(UpcomingWidget::class.java, R.layout.widget_upcoming)
        val config = Robolectric.buildActivity(
            UpcomingWidgetConfigActivity::class.java,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        ).setup()
        waitForText("Dates to show")
        compose.onNodeWithText("Consultation responses due").performClick()
        compose.onNodeWithText("Only mergers I follow").performClick()
        compose.waitForIdle()
        File("build/screenshots/9d-widget-settings.png").outputStream().use {
            compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("Done").performClick()
        waitUntil { config.get().isFinishing }
        assertEquals(Activity.RESULT_OK, shadowOf(config.get()).resultCode)
        val saved = WidgetPrefs.upcoming(app, widgetId)
        assertEquals(UpcomingConfig(followedOnly = true, hidden = setOf(EventKind.CONSULTATION)), saved)
        config.pause().stop().destroy()
        widgetShot("9e-widget-upcoming-filtered", UpcomingWidget.views(app, saved, events, followed), 380, 300)

        // Taking the widget off the home screen forgets its settings.
        UpcomingWidget().onDeleted(app, intArrayOf(widgetId))
        assertEquals(UpcomingConfig(), WidgetPrefs.upcoming(app, widgetId))
    }

    /** A widget as the launcher would draw it, at [widthDp] by [heightDp]. */
    private fun widgetShot(name: String, views: RemoteViews, widthDp: Int, heightDp: Int) {
        val activity = compose.activity
        val density = activity.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread {
            // A list only takes its rows inside a widget host.
            val parent = AppWidgetHostView(activity)
            parent.addView(views.apply(activity, parent))
            repeat(2) {
                parent.measure(
                    View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
                )
                parent.layout(0, 0, w, h)
                shadowOf(Looper.getMainLooper()).idle()
            }
            parent.draw(Canvas(bitmap))
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    /**
     * Wait on app state rather than on the screen. Polls between idles: the
     * check blocks the main thread, which the coroutines it waits on need.
     */
    private fun waitUntil(check: suspend () -> Boolean) {
        repeat(100) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            if (runBlocking { check() }) return
            Thread.sleep(100)
        }
        throw AssertionError("Condition not met within 10 s")
    }

    private fun back() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
