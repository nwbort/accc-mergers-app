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

    /** A mergers.fyi merger link (from a notification, or shared from anywhere) opens that merger. */
    private fun handle(intent: Intent) {
        mergerIdFrom(intent.data?.toString())?.let { vm.openMerger(it) }
    }

    companion object {
        private val MERGER_LINK = Regex("^https?://(?:www\\.)?mergers\\.fyi/mergers/([A-Za-z]{2}-\\d+)(?:[/?#].*)?$")

        /** "https://mergers.fyi/mergers/MN-01016/asahi-…" → "MN-01016". */
        fun mergerIdFrom(url: String?): String? = url?.let { MERGER_LINK.find(it)?.groupValues?.get(1)?.uppercase() }
    }
}
