package fyi.mergers.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import fyi.mergers.app.ui.EventTypeTile
import fyi.mergers.app.ui.theme.MergersTheme
import kotlinx.coroutines.launch

/**
 * A Coming up widget's settings: which mergers, and which kinds of date. The
 * launcher opens it when the widget is added, and again from the widget's
 * press-and-hold menu.
 */
class UpcomingWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of a new widget's settings means it isn't added.
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            MergersTheme {
                UpcomingWidgetConfig(WidgetPrefs.upcoming(this, id)) { config ->
                    WidgetPrefs.setUpcoming(this, id, config)
                    lifecycleScope.launch {
                        val context = applicationContext
                        UpcomingWidget.render(context, AppWidgetManager.getInstance(context), intArrayOf(id))
                        setResult(RESULT_OK, result)
                        finish()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpcomingWidgetConfig(initial: UpcomingConfig, onDone: (UpcomingConfig) -> Unit) {
    var followedOnly by rememberSaveable { mutableStateOf(initial.followedOnly) }
    var hidden by rememberSaveable { mutableStateOf(initial.hidden.map { it.name }.toSet()) }
    val anyShown = hidden.size < EventKind.choices.size

    Scaffold(
        topBar = { TopAppBar(title = { Text("Coming up widget") }) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        onDone(UpcomingConfig(followedOnly, EventKind.choices.filter { it.name in hidden }.toSet()))
                    },
                    enabled = anyShown,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Done") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Heading("Mergers")
            Column(Modifier.selectableGroup()) {
                Choice("All mergers", selected = !followedOnly) { followedOnly = false }
                Choice("Only mergers I follow", selected = followedOnly) { followedOnly = true }
            }

            Heading("Dates to show")
            EventKind.choices.forEach { kind ->
                val shown = kind.name !in hidden
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(shown, role = Role.Checkbox) { hidden = if (it) hidden - kind.name else hidden + kind.name }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EventTypeTile(kind.types.first(), description = null)
                    Spacer(Modifier.width(16.dp))
                    Text(kind.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(checked = shown, onCheckedChange = null)
                }
            }
            if (!anyShown) {
                Text(
                    "Choose at least one kind of date.",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
