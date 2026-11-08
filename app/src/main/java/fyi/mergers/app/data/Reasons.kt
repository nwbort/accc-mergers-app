package fyi.mergers.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A determination's reasons, cut down to what is particular to the matter.
 * A port of the site's frontend/src/components/DeterminationExplanationSection.jsx:
 * the paragraphs every determination repeats word for word (the statutory test,
 * the pointer to the guidelines, what the ACCC considered) are dropped, and the
 * PDF's layout line breaks are told apart from its real ones. Keep
 * [BOILERPLATE_PREFIXES] in step with the site's list.
 */
object Reasons {
    sealed interface Block {
        data class Heading(val text: String) : Block
        data class Paragraph(val text: String, val number: String? = null) : Block
        data class Bullet(val text: String, val children: List<String> = emptyList())
        data class Bullets(val items: List<Bullet>) : Block
        data class Lettered(val items: List<Pair<String, String>>) : Block
    }

    val BOILERPLATE_PREFIXES = listOf(
        "In making this notification waiver determination, the Australian Competition and Consumer Commission",
        "When making a determination in Phase 1, the Australian Competition",
        "For more information about the ACCC",
        "In conducting its competition assessment, the ACCC has considered the information and documents",
        "For the reasons given below, the ACCC has determined",
    )

    fun isBoilerplate(p: String): Boolean = BOILERPLATE_PREFIXES.any { p.startsWith(it) }

    private val SUB_BULLET = Regex("^[o◦]\\s+")
    private val LETTERED = Regex("^[a-z]\\.\\s")
    private fun isBullet(p: String) = p.startsWith("•") || p.startsWith("▪")
    private fun isSubBullet(p: String) = SUB_BULLET.containsMatchIn(p)

    /**
     * Join the PDF's line breaks back up, keeping real ones: a hyphen before
     * the break is a split word, a bullet or lettered item after it starts a
     * new paragraph, as does a capital after a full stop or bracket.
     */
    fun clean(text: String): String {
        val out = StringBuilder()
        for (i in text.indices) {
            val c = text[i]
            if (c != '\n') { out.append(c); continue }
            val after = text.substring(i + 1)
            val last = out.lastOrNull()
            when {
                last == '-' -> Unit
                isBullet(after) || isSubBullet(after) -> out.append("\n\n")
                Regex("^[a-z]\\.").containsMatchIn(after) -> out.append("\n\n")
                (last == '.' || last == ')') && after.firstOrNull()?.isUpperCase() == true -> out.append("\n\n")
                else -> out.append(' ')
            }
        }
        return out.toString()
    }

    /** The blocks to show for one event, or none when there is nothing particular to say. */
    fun forEvent(e: MergerEvent): List<Block> {
        if (e.determination_statement_of_reasons.isNotEmpty()) {
            return fromStatement(e.determination_statement_of_reasons)
        }
        val details = explanationDetails(e) ?: return emptyList()
        // The placeholder row that just points to the statement of reasons.
        if (details.lowercase().contains("set out in section")) return emptyList()
        return fromInline(details)
    }

    private fun explanationDetails(e: MergerEvent): String? = e.determination_table_content.firstOrNull { row ->
        val n = row.item?.lowercase()?.replace(Regex("\\s+"), " ")?.trim() ?: return@firstOrNull false
        n.startsWith("explanation for determination") || n.startsWith("reasons for determination")
    }?.details?.takeIf { it.isNotBlank() }

    fun fromStatement(blocks: List<StatementBlock>): List<Block> = blocks.mapNotNull { b ->
        when (b.type) {
            "heading" -> b.text?.takeIf { it.isNotBlank() }?.let { Block.Heading(it) }
            "paragraph" -> b.text?.trim()?.takeIf { it.isNotEmpty() && !isBoilerplate(it) }
                ?.let { Block.Paragraph(it, b.number) }
            "bullet_list" -> b.items.mapIndexedNotNull { j, item ->
                (item as? JsonPrimitive)?.contentOrNull?.let { Block.Bullet(it, b.sub_items.getOrNull(j).orEmpty()) }
            }.takeIf { it.isNotEmpty() }?.let { Block.Bullets(it) }
            "lettered_list" -> b.items.mapNotNull { item ->
                val o = item as? JsonObject ?: return@mapNotNull null
                val letter = (o["letter"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val text = (o["text"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                "($letter)" to text
            }.takeIf { it.isNotEmpty() }?.let { Block.Lettered(it) }
            else -> null
        }
    }

    fun fromInline(details: String): List<Block> {
        val paragraphs = clean(details).split("\n\n").map { it.trim() }
            .filter { it.isNotEmpty() && !isBoilerplate(it) }.toMutableList()
        if (paragraphs.isNotEmpty() && isBullet(paragraphs[0])) {
            paragraphs[0] = paragraphs[0].replace(Regex("^[•▪]\\s*"), "")
        }
        return group(paragraphs)
    }

    /** Runs of bullets (with "o" sub-bullets nested under them) and lettered items become lists. */
    private fun group(paragraphs: List<String>): List<Block> {
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < paragraphs.size) {
            val p = paragraphs[i]
            when {
                isBullet(p) || isSubBullet(p) -> {
                    // Each item, and whether it is a sub-bullet with no bullet above it.
                    val items = mutableListOf<Pair<Block.Bullet, Boolean>>()
                    while (i < paragraphs.size && (isBullet(paragraphs[i]) || isSubBullet(paragraphs[i]))) {
                        val para = paragraphs[i]
                        val last = items.lastOrNull()
                        if (isSubBullet(para) && last != null && !last.second) {
                            items[items.size - 1] = last.first.copy(children = last.first.children + para.replace(SUB_BULLET, "")) to false
                        } else {
                            val text = para.replace(Regex("^[•▪]\\s*"), "").replace(SUB_BULLET, "")
                            items += Block.Bullet(text) to (isSubBullet(para) && (last == null || last.second))
                        }
                        i++
                    }
                    blocks += Block.Bullets(items.map { it.first })
                }
                LETTERED.containsMatchIn(p) -> {
                    val items = mutableListOf<Pair<String, String>>()
                    while (i < paragraphs.size && LETTERED.containsMatchIn(paragraphs[i])) {
                        val m = Regex("^([a-z]\\.)\\s*([\\s\\S]*)$").find(paragraphs[i])!!
                        items += m.groupValues[1] to m.groupValues[2]
                        i++
                    }
                    blocks += Block.Lettered(items)
                }
                else -> { blocks += Block.Paragraph(p); i++ }
            }
        }
        return blocks
    }
}
