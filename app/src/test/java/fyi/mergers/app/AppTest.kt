package fyi.mergers.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import fyi.mergers.app.data.IndustryDivisionFile
import fyi.mergers.app.data.IndustryMerger
import fyi.mergers.app.data.MergerDetail
import fyi.mergers.app.data.MergerEvent
import fyi.mergers.app.data.MergersJson
import fyi.mergers.app.notify.CheckWorker
import fyi.mergers.app.ui.AppViewModel
import fyi.mergers.app.ui.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        compose.onNodeWithTag("dashboard").performScrollToIndex(0)

        // The merger list, swiped to from the dashboard, searched and cleared.
        compose.onNodeWithTag("dashboard").performTouchInput { swipeLeft() }
        waitUntil { vm.tab == fyi.mergers.app.ui.Route.Mergers }
        waitForText("40 matters")
        shot("2-mergers")
        compose.onNode(hasSetTextAction()).performTextInput("asahi")
        waitForText("1 matter")
        compose.onNode(hasSetTextAction()).performTextClearance()
        waitForText("40 matters")

        // Industries, from the list's link: searched, opened, followed, and up
        // the breadcrumb to its division.
        compose.onNodeWithText("By industry").performClick()
        waitForText("350 of 350 industries", substring = true)
        shot("2b-industries")
        compose.onNode(hasSetTextAction()).performTextInput("supermarket")
        waitForText("1 of 350 industries", substring = true)
        compose.onNodeWithText("Supermarket and Grocery Stores").performClick()
        waitForText("ANZSIC 4110")
        waitForText("Under assessment")
        shot("2c-industry")
        compose.onNodeWithContentDescription("Follow industry").performClick()
        waitUntil { Graph.store.current().isIndustryTracked("4110") }
        compose.onNodeWithText("Retail Trade").performClick()
        waitForText("ANZSIC G")
        waitForText("Sub-industries")
        shot("2d-industry-division")
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
        val result = runBlocking { TestListenableWorkerBuilder<CheckWorker>(app).build().doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        val autosports = posted.single { it.extras.getCharSequence("android.title").toString().startsWith("Autosports") }
        val text = autosports.extras.getCharSequence("android.text").toString()
        val lines = autosports.extras.getCharSequenceArray("android.textLines").orEmpty().map { it.toString() }
        // The new document, and the moved deadline; not the history from before the follow.
        assertTrue(text, text.startsWith("2 updates"))
        assertEquals(listOf("Determination due 15 Jan 2027", "Timeline extended by 10 business days"), lines)
        // The industry's one new filing; nothing from before it was followed.
        val industry = posted.single { it.extras.getCharSequence("android.title").toString() == "Supermarket and Grocery Stores" }
        assertEquals(
            "New merger filed: Grocer – Corner store",
            industry.extras.getCharSequence("android.text").toString(),
        )

        // A second check finds nothing new to say.
        val before = posted.size
        runBlocking { TestListenableWorkerBuilder<CheckWorker>(app).build().doWork() }
        assertEquals(
            before,
            shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.size,
        )

        // A mergers.fyi link (as a notification sends) opens that merger.
        compose.activity.onNewIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://mergers.fyi/mergers/MN-01016/asahi-warehouse")),
        )
        waitForText("Asahi – Warehouse site on Tilburn Rd, Deer Park (Vic)")
        shot("7-detail-decided")
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
