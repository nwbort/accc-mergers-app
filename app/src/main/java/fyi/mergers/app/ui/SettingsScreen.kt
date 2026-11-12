package fyi.mergers.app.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import fyi.mergers.app.notify.Push
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.material3.Switch
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fyi.mergers.app.ui.theme.ThemeChoice

/**
 * Settings, from the dashboard's cog, the Following tab's Alerts row, or the
 * phone's App info.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // What reaches the phone, first: it's what most people come here for.
            SectionTitle("Notifications")
            NotificationsBanner()
            AlertSettings(vm)

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionTitle("Appearance")
            Text(
                "Theme",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // Expressive's connected button group: the chosen one rounds off fully.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                val n = ThemeChoice.entries.size
                ThemeChoice.entries.forEachIndexed { i, choice ->
                    val selected = vm.theme == choice
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { vm.chooseTheme(choice) },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            n - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    ) {
                        Icon(
                            when (choice) {
                                ThemeChoice.SYSTEM -> Icons.Outlined.PhoneAndroid
                                ThemeChoice.LIGHT -> Icons.Outlined.LightMode
                                ThemeChoice.DARK -> Icons.Outlined.DarkMode
                            },
                            contentDescription = null,
                            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                        Text(choice.label)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 16.dp))
            SectionTitle("About")
            ListRow(
                title = "mergers.fyi",
                subtitle = "The Australian merger tracker, on the web",
                trailing = null,
                onClick = { openUrl(context, "https://mergers.fyi/") },
            )
            if (version != null) {
                Text(
                    "Version $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Which alerts the background check raises, and how often it looks. Each row
 * is the switch, so a tap anywhere on it toggles, and TalkBack reads the
 * switch with its words rather than as a bare "Switch, on".
 */
@Composable
private fun AlertSettings(vm: AppViewModel) {
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    ListItem(
        supportingContent = { Text("Matters referred to phase 2 are followed automatically") },
        trailingContent = { Switch(checked = tracking.autoTrackPhase2, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.toggleable(tracking.autoTrackPhase2, role = Role.Switch, onValueChange = vm::setAutoTrackPhase2),
    ) { Text("Follow every phase 2 matter") }
    ListItem(
        supportingContent = { Text("A notification for each new merger or waiver on the register") },
        trailingContent = { Switch(checked = tracking.notifyNewFilings, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.toggleable(tracking.notifyNewFilings, role = Role.Switch, onValueChange = vm::setNotifyNewFilings),
    ) { Text("Every new filing") }
    Text(
        if (Push.ready(LocalContext.current)) {
            "Checked as soon as mergers.fyi publishes a change, which it does several times a weekday from the ACCC register."
        } else {
            "Checked about every two hours. mergers.fyi refreshes from the ACCC register several times a weekday."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
