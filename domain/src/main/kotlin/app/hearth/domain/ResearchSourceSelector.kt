package app.hearth.domain

import java.net.URI

/** Chooses which of the pages found are read: no page twice, a share for every search, and no site crowding out the rest. */
class ResearchSourceSelector {
    /**
     * The pages to read, in the order they are numbered.
     *
     * The searches take turns: the best result of each, then the next best of each, until the limit is reached.
     *
     * [results] is what each search found, best first; [limit] the most pages to choose. Pages in [taken] were
     * chosen earlier: they are not chosen again and count towards their sites' share. [isReadable] says whether a
     * page at an address could be read at all; one that could not is left out.
     */
    fun select(
        results: List<List<SearchSource>>,
        limit: Int,
        taken: List<SearchSource> = emptyList(),
        isReadable: (String) -> Boolean = { true },
    ): List<SearchSource> {
        val seen = taken.map { normalisedAddress(it.url) }.toMutableSet()
        val perSite = mutableMapOf<String, Int>()
        taken.forEach { perSite.merge(site(it.url), 1, Int::plus) }
        val chosen = mutableListOf<SearchSource>()
        val deepest = results.maxOfOrNull { it.size } ?: 0
        for (rank in 0 until deepest) {
            for (found in results) {
                if (rank >= found.size) continue
                if (chosen.size >= limit) return chosen
                val source = found[rank]
                val site = site(source.url)
                val hasRoom = (perSite[site] ?: 0) < ResearchLimits.MAXIMUM_SOURCES_PER_SITE
                if (!isReadable(source.url) || !hasRoom || !seen.add(normalisedAddress(source.url))) continue
                perSite.merge(site, 1, Int::plus)
                chosen += source
            }
        }
        return chosen
    }

    companion object {
        /** Parts of an address that only say where a visitor came from, and so do not make a different page. */
        private val TRACKING_NAMES = setOf("fbclid", "gclid", "msclkid", "mc_cid", "mc_eid", "ref", "ref_src", "igshid")

        /**
         * The address with everything taken out that does not change which page it is: the scheme, a leading `www.`,
         * the case of the name, a closing slash, the fragment and anything that only tracks the visitor.
         */
        fun normalisedAddress(address: String): String {
            val uri = runCatching { URI(address.trim()) }.getOrNull() ?: return address.lowercase()
            val kept = uri.rawQuery.orEmpty().split("&")
                .filter { it.isNotEmpty() }
                .map { it.substringBefore("=") to it.substringAfter("=", "") }
                .filter { (name, _) -> !name.lowercase().startsWith("utm_") && name.lowercase() !in TRACKING_NAMES }
                .sortedWith(compareBy({ it.first }, { it.second }))
            val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&") { (name, value) -> if (value.isEmpty()) name else "$name=$value" }
            return site(address) + uri.rawPath.orEmpty().trimEnd('/') + query
        }

        /** The site a page belongs to: its host in lower case, without a leading `www.`. */
        fun site(address: String): String = runCatching { URI(address.trim()).host }.getOrNull().orEmpty().lowercase().removePrefix("www.")
    }
}
