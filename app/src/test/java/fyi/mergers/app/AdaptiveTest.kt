package fyi.mergers.app

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import fyi.mergers.app.ui.AppViewModel
import fyi.mergers.app.ui.Route
import fyi.mergers.app.ui.theme.ThemeChoice
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
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
 * The app on wider windows: the navigation rail in place of the bottom bar,
 * and from Expanded up a merger opening beside the list it came from. One
 * method, as in [AppTest], with the window resized as it goes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class)
class AdaptiveTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val vm get() = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    private val rail = hasAnyAncestor(hasTestTag("rail"))

    /**
     * A desktop window, made narrower step by step: Large (the rail open, two
     * panes), Expanded (a tablet sideways: the rail collapsed, two panes),
     * Medium (a foldable opened: the rail, one pane) and Compact (the bar).
     */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun the_layout_follows_the_window() {
        Fixtures.serveManifest = false
        waitForText("Recent determinations")
        // The rail, open with its labels; no bottom bar.
        compose.onNode(hasText("Dashboard") and rail).assertExists()
        compose.onNode(hasContentDescription("Search") and rail).assertExists()
        // The rail's button is search: the dashboard's top bar doesn't repeat it.
        compose.onNode(hasContentDescription("Search") and !rail).assertDoesNotExist()
        shot("t1-large-dashboard")
        // The dashboard on its own, this wide: the latest determinations and
        // notifications as the merger list's cards, two to a row, and each
        // day's events in two columns.
        compose.onNodeWithTag("dashboard").performScrollToKey("h-Recent determinations")
        shot("t1b-large-dashboard-determinations")
        compose.onNodeWithTag("dashboard").performScrollToKey("h-Recently notified")
        shot("t1b2-large-dashboard-notified")
        compose.onNodeWithTag("dashboard").performScrollToKey("h-Coming up")
        shot("t1c-large-dashboard-coming-up")
        compose.onNodeWithTag("dashboard").performScrollToKey("h-Recent events")
        shot("t1d-large-dashboard-recent-events")
        compose.onNodeWithTag("dashboard").performScrollToIndex(0)

        // The Mergers tab keeps the detail pane, empty until a merger's picked.
        compose.onNode(hasText("Mergers") and rail).performClick()
        waitForText("40 matters")
        waitForText("Pick a merger")
        shot("t2-large-mergers")

        // Picked, it opens beside the list, which stays.
        compose.onAllNodesWithText("Autosports Group", substring = true).onFirst().performClick()
        waitForText("Consultation responses due")
        waitForText("40 matters")
        shot("t3-large-list-detail")

        // Another picked from the list takes its place, rather than piling on top.
        // (One the fixtures carry in full: not every card in the list has its file.)
        compose.runOnUiThread { vm.openMerger("MN-01068") }
        waitForText("Tribunal hearing")
        assertEquals(listOf(Route.Mergers, Route.Merger("MN-01068")), vm.backStack.toList())
        shot("t4-large-list-detail-2")

        // Over to Following and back, the merger stays open beside each.
        compose.onNode(hasText("Following") and rail).performClick()
        waitUntil { vm.tab == Route.Following }
        assertEquals(listOf(Route.Following, Route.Merger("MN-01068")), vm.backStack.toList())
        waitForText("not following anything", substring = true)
        waitForText("Tribunal hearing")
        shot("t4b-large-following-keeps-merger")
        compose.onNode(hasText("Mergers") and rail).performClick()
        waitUntil { vm.tab == Route.Mergers }
        assertEquals(listOf(Route.Mergers, Route.Merger("MN-01068")), vm.backStack.toList())

        // Its own tab, tapped again, closes the detail; the list is still there.
        compose.onNode(hasText("Mergers") and rail).performClick()
        waitForText("Pick a merger")
        assertEquals(listOf<Route>(Route.Mergers), vm.backStack.toList())

        // Dark, the empty pane's words are light.
        compose.runOnUiThread { vm.chooseTheme(ThemeChoice.DARK) }
        waitUntil { vm.theme == ThemeChoice.DARK }
        waitForText("Pick a merger")
        shot("t4c-large-mergers-dark")
        compose.runOnUiThread { vm.chooseTheme(ThemeChoice.SYSTEM) }
        waitUntil { vm.theme == ThemeChoice.SYSTEM }
        waitForText("Pick a merger")

        // Closed, the rail keeps its icons only.
        compose.onNodeWithContentDescription("Collapse navigation").performClick()
        waitUntil { compose.onAllNodes(hasContentDescription("Expand navigation")).fetchSemanticsNodes().isNotEmpty() }
        shot("t5-large-rail-collapsed")
        compose.onNodeWithContentDescription("Expand navigation").performClick()
        waitUntil { compose.onAllNodes(hasContentDescription("Collapse navigation")).fetchSemanticsNodes().isNotEmpty() }

        // The dashboard closes a merger open beside a list: it has the window to itself.
        compose.runOnUiThread { vm.openMerger("MN-20046") }
        waitForText("Consultation responses due")
        compose.onNode(hasText("Dashboard") and rail).performClick()
        waitUntil { vm.backStack.toList() == listOf<Route>(Route.Dashboard) }
        waitForText("Recent determinations")

        // From the dashboard, a merger opens beside it too.
        compose.runOnUiThread { vm.openMerger("MN-01068") }
        waitForText("Tribunal hearing")
        waitForText("Recent determinations")
        shot("t6-large-dashboard-detail")

        // Search, from the rail's button, is a list to pick from as well.
        compose.runOnUiThread { vm.selectTab(Route.Dashboard) }
        compose.onNode(hasContentDescription("Search") and rail).performClick()
        waitUntil { vm.backStack.lastOrNull() == Route.Search }
        waitForText("Pick a merger")
        shot("t7-large-search")

        // A tablet sideways: two panes still, and the rail as it was left (open).
        resize("w960dp-h600dp-land-xhdpi")
        compose.runOnUiThread { vm.selectTab(Route.Following) }
        waitForText("Pick a merger")
        compose.onNodeWithContentDescription("Collapse navigation").assertExists()
        compose.onNodeWithContentDescription("Collapse navigation").performClick()
        compose.runOnUiThread { vm.openMerger("MN-20046") }
        waitForText("Consultation responses due")
        shot("t8-expanded-following-detail")

        // An unfolded foldable: the rail, collapsed and without its menu
        // button, and the merger over the list rather than beside it.
        resize("w700dp-h1000dp-port-xhdpi")
        waitForText("Consultation responses due")
        compose.onNode(hasText("Following") and rail).assertExists()
        compose.onNodeWithContentDescription("Expand navigation").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("Pick a merger").fetchSemanticsNodes().isEmpty())
        shot("t9-medium-detail")
        back()
        waitForText("not following anything", substring = true)
        compose.onNode(hasText("Mergers") and rail).performClick()
        waitForText("40 matters")
        compose.onAllNodesWithText("Autosports Group", substring = true).onFirst().performClick()
        waitForText("Consultation responses due")
        assertTrue(compose.onAllNodesWithText("40 matters").fetchSemanticsNodes().isEmpty())
        // Opened from the list on one pane, it stacks as on a phone.
        assertEquals(listOf(Route.Mergers, Route.Merger("MN-20046")), vm.backStack.toList())
        back()

        // A phone: the bottom bar, no rail.
        resize("w411dp-h891dp-port-xxhdpi")
        waitForText("40 matters")
        compose.onNodeWithTag("rail").assertDoesNotExist()
        compose.onNode(hasText("Following") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertExists()
    }

    /** The window changes size, as a desktop window dragged or a foldable opened: the activity's recreated. */
    private fun resize(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun waitUntil(check: () -> Boolean) {
        repeat(100) {
            compose.waitForIdle()
            shadowOf(Looper.getMainLooper()).idle()
            if (check()) return
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
        compose.checkAccessibility(name)
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
