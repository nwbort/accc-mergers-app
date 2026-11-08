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
import fyi.mergers.app.ui.theme.MergersTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
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
     * A mergers.fyi merger or industry link (from a notification, or shared
     * from anywhere) opens that page.
     */
    private fun handle(intent: Intent) {
        val url = intent.data?.toString()
        mergerIdFrom(url)?.let { vm.openMerger(it); return }
        industryCodeFrom(url)?.let { vm.openIndustry(it); return }
        if (url != null && INDUSTRIES_LINK.matches(url)) vm.openIndustries()
    }

    companion object {
        private val MERGER_LINK = Regex("^https?://(?:www\\.)?mergers\\.fyi/mergers/([A-Za-z]{2}-\\d+)(?:[/?#].*)?$")

        /** "https://mergers.fyi/mergers/MN-01016/asahi-…" → "MN-01016". */
        fun mergerIdFrom(url: String?): String? = url?.let { MERGER_LINK.find(it)?.groupValues?.get(1)?.uppercase() }

        /** A division letter, or a 2 to 4 digit subdivision, group or class. */
        private val INDUSTRY_LINK = Regex("^https?://(?:www\\.)?mergers\\.fyi/industries/([A-Za-z]|\\d{2,4})(?:[/?#].*)?$")
        private val INDUSTRIES_LINK = Regex("^https?://(?:www\\.)?mergers\\.fyi/industries/?(?:[?#].*)?$")

        /** "https://mergers.fyi/industries/4110/supermarket-…" → "4110". */
        fun industryCodeFrom(url: String?): String? = url?.let { INDUSTRY_LINK.find(it)?.groupValues?.get(1)?.uppercase() }
    }
}
