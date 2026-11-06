package fyi.mergers.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fyi.mergers.app.data.Dates
import fyi.mergers.app.data.MergerSummary
import fyi.mergers.app.data.Party
import fyi.mergers.app.data.headerStatus
import fyi.mergers.app.data.outcomeInput
import fyi.mergers.app.data.outcomeText
import fyi.mergers.app.ui.theme.statusColors

/** Open a link in a Custom Tab (documents, the ACCC's page), or whatever can handle it. */
fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun share(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The site's solid status badge: an uppercase label on the outcome's colour. */
@Composable
fun StatusBadge(label: String, modifier: Modifier = Modifier, colorKey: String = label) {
    val c = statusColors(colorKey)
    Text(
        label.uppercase(),
        color = c.onFill,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.fill)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { role = Role.Image; contentDescription = "Status: $label" },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

fun partiesLine(acquirers: List<Party>, targets: List<Party>): String? {
    fun names(p: List<Party>) = p.joinToString(", ") { it.displayName }.ifBlank { null }
    val a = names(acquirers)
    val t = names(targets)
    return when {
        a != null && t != null -> "$a → $t"
        else -> a ?: t
    }
}

/**
 * One merger in a list: the outcome first (a solid badge, and a rail down the
 * left edge in the same colour), then the name, as the site's merger list does.
 */
@Composable
fun MergerCard(m: MergerSummary, followed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val input = m.outcomeInput()
    val header = input.headerStatus()
    val label = input.outcomeText() ?: m.status ?: "Unknown"
    val color = statusColors(header?.label ?: m.status)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(color.fill))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(label, colorKey = header?.label ?: m.status ?: "")
                    if (m.is_waiver) Tag("Waiver")
                    if (m.under_appeal) Tag("Under appeal")
                    if (m.is_refiled) Tag("Re-filed")
                    Spacer(Modifier.weight(1f))
                    if (followed) {
                        Icon(
                            Icons.Filled.Bookmark,
                            contentDescription = "Following",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    m.merger_name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                partiesLine(m.acquirers, m.targets)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                val dates = listOfNotNull(
                    m.merger_id,
                    Dates.format(m.effective_notification_datetime)?.let { "Notified $it" },
                    Dates.format(m.determination_publication_date)?.let { "Determined $it" },
                ).joinToString(" · ")
                Text(
                    dates,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun Tag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A short list row: a title, a line under it and a trailing date. */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    trailing: String?,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null,
    emphasis: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The register's prose: paragraphs split on blank lines, "* " and "- " lines
 * as bullets. Enough for merger descriptions and commentary, which are
 * markdown at their richest.
 */
@Composable
fun Prose(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text.trim().split(Regex("\n\\s*\n")).forEach { para ->
            val lines = para.lines()
            if (lines.all { it.trimStart().startsWith("* ") || it.trimStart().startsWith("- ") || it.isBlank() }) {
                lines.filter { it.isNotBlank() }.forEach { line ->
                    Row {
                        Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(clean(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Text(clean(para.replace("\n", " ")), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Drop markdown emphasis and turn [text](link) into its text. */
private fun clean(s: String): String =
    s.replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1").replace("**", "").replace("__", "")

@Composable
fun Centered(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
