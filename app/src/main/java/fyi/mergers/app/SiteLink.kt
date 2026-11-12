package fyi.mergers.app

/**
 * A mergers.fyi page the app has a screen for. The manifest claims the
 * site's pages broadly (and every PDF under /mergers/ is excluded there);
 * anything [from] doesn't recognise goes back to the browser.
 */
sealed interface SiteLink {
    data class Merger(val id: String) : SiteLink
    data class Industry(val code: String) : SiteLink
    data object Industries : SiteLink
    data object Mergers : SiteLink
    data object Dashboard : SiteLink

    /** The weekly digest, scrolled to [section] (its anchor, as the weekly email links them) if one is named. */
    data class Digest(val section: String? = null) : SiteLink

    companion object {
        private val SITE = Regex("^https?://(?:www\\.)?mergers\\.fyi(/[^?#]*)?(?:\\?[^#]*)?(?:#(.*))?$", RegexOption.IGNORE_CASE)
        private val MERGER = Regex("^/mergers/([A-Za-z]{2}-\\d+)(?:/[a-z0-9-]*)?/?$")

        /** A division letter, or a 2 to 4 digit subdivision, group or class. */
        private val INDUSTRY = Regex("^/industries/([A-Za-z]|\\d{2,4})(?:/[a-z0-9-]*)?/?$")

        /**
         * The page [url] names, or null for one the app can't show: another
         * site, a document (/mergers/MN-01016/notice.pdf), or a page with no
         * screen here (/phase-2, /parties/…, /quarterly …).
         */
        fun from(url: String?): SiteLink? {
            val match = url?.let { SITE.find(it) } ?: return null
            val path = match.groupValues[1].ifEmpty { "/" }
            MERGER.find(path)?.let { return Merger(it.groupValues[1].uppercase()) }
            INDUSTRY.find(path)?.let { return Industry(it.groupValues[1].uppercase()) }
            return when (path.trimEnd('/')) {
                "" -> Dashboard
                "/mergers" -> Mergers
                "/industries" -> Industries
                "/digest" -> Digest(match.groupValues[2].ifEmpty { null })
                else -> null
            }
        }
    }
}
