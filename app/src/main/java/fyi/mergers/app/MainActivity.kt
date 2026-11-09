package fyi.mergers.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fyi.mergers.app.ui.AppViewModel
import fyi.mergers.app.ui.MergersApp
import fyi.mergers.app.ui.Route
import fyi.mergers.app.ui.openInBrowser
import fyi.mergers.app.ui.theme.MergersTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent, fresh = true)
        if (isFinishing) return
        val crash = CrashReport.read(this)
        setContent {
            // After a crash, show what happened before touching any app state.
            var report by remember { mutableStateOf(crash) }
            val shown = report
            if (shown != null) {
                CrashScreen(shown, onDismiss = { CrashReport.clear(this); report = null })
                return@setContent
            }
            MergersTheme { MergersApp(vm) }
        }
    }

    // Public so the UI test can deliver one the way a notification would.
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * A mergers.fyi link (from a notification, or tapped anywhere) opens the
     * page it names. One the app has no screen for goes to the browser, and
     * if it's what launched the app, the app closes behind it rather than
     * sitting on a screen that has nothing to do with the link.
     */
    private fun handle(intent: Intent, fresh: Boolean = false) {
        if (intent.action == ACTION_SEARCH) {
            vm.openSearch()
            return
        }
        if (intent.action != Intent.ACTION_VIEW) return
        val url = intent.data?.toString() ?: return
        when (val link = SiteLink.from(url)) {
            is SiteLink.Merger -> vm.openMerger(link.id)
            is SiteLink.Industry -> vm.openIndustry(link.code)
            SiteLink.Industries -> vm.openIndustries()
            SiteLink.Mergers -> vm.selectTab(Route.Mergers)
            SiteLink.Dashboard -> vm.selectTab(Route.Dashboard)
            null -> if (openInBrowser(this, url) && fresh) finish()
        }
    }

    companion object {
        /** The Search widget: straight to search, with the cursor in the field. */
        const val ACTION_SEARCH = "fyi.mergers.app.action.SEARCH"

        /** "https://mergers.fyi/mergers/MN-01016/asahi-…" → "MN-01016". */
        fun mergerIdFrom(url: String?): String? = (SiteLink.from(url) as? SiteLink.Merger)?.id

        /** "https://mergers.fyi/industries/4110/supermarket-…" → "4110". */
        fun industryCodeFrom(url: String?): String? = (SiteLink.from(url) as? SiteLink.Industry)?.code
    }
}
