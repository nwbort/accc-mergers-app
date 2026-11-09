package fyi.mergers.app.data

/**
 * The register's merger ids: "MN-" for a notification, "WA-" for a waiver
 * application, then five digits.
 */
object MergerIds {
    private val TYPED = Regex("""^\s*(MN|WA)\s*[-–—]?\s*(\d{5})\s*$""", RegexOption.IGNORE_CASE)

    /**
     * [text] as a merger id in the register's form ("WA-01087"), or null when
     * it isn't one. Case, spaces and the hyphen are forgiven: "wa01087",
     * "WA 01087" and "wa-01087" are all WA-01087.
     */
    fun parse(text: String): String? =
        TYPED.matchEntire(text)?.let { "${it.groupValues[1].uppercase()}-${it.groupValues[2]}" }
}
