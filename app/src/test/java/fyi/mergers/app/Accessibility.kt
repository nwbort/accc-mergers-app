package fyi.mergers.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * What TalkBack needs of whatever is on [screen], as the merged semantics
 * tree it walks has it (the screen and any menu over it):
 *  - everything that acts on a tap says what it is: words, a description
 *    or a state. A link in a run of text is exempt: it's read, and opened,
 *    from the text it sits in.
 *  - no action is out of reach: a custom action (a swipe's Follow, say)
 *    sits on a node a screen reader can land on, not on a wrapper.
 * Touch targets aren't checked: Compose widens every tap area to 48 dp
 * itself, and reports that to TalkBack, so here it can't fail.
 */
internal fun ComposeTestRule.checkAccessibility(screen: String) {
    val dp = density.density
    val problems = mutableListOf<String>()
    fun SemanticsNode.words(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
        config.getOrNull(SemanticsProperties.EditableText)?.text,
        config.getOrNull(SemanticsProperties.StateDescription),
    ).joinToString(" ").trim()
    fun SemanticsNode.focusable(): Boolean =
        config.isMergingSemanticsOfDescendants || words().isNotEmpty() || SemanticsActions.OnClick in config
    fun SemanticsNode.describe(): String = words().ifEmpty {
        val role = config.getOrNull(SemanticsProperties.Role) ?: "node"
        "($role at ${(boundsInRoot.left / dp).toInt()},${(boundsInRoot.top / dp).toInt()} dp," +
            " under ${parent?.words()?.ifEmpty { null } ?: "?"})"
    }.take(90)
    fun walk(n: SemanticsNode) {
        if (SemanticsProperties.HideFromAccessibility in n.config) return
        val link = SemanticsProperties.LinkTestMarker in n.config
        if (SemanticsActions.OnClick in n.config && !link && n.words().isEmpty()) {
            problems += "tappable with nothing to read: ${n.describe()}"
        }
        if (SemanticsActions.CustomActions in n.config && !n.focusable()) {
            val labels = n.config[SemanticsActions.CustomActions].joinToString { it.label }
            problems += "custom action ($labels) where a screen reader can't land: ${n.describe()}"
        }
        n.children.forEach(::walk)
    }
    onAllNodes(isRoot()).fetchSemanticsNodes().forEach(::walk)
    if (problems.isNotEmpty()) throw AssertionError("Accessibility on $screen:\n" + problems.distinct().joinToString("\n"))
}
