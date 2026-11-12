package app.hearth.domain

import app.hearth.data.OllamaTools
import app.hearth.data.PageService
import app.hearth.data.SearchService

/** The web as the domain sees it: a search engine to ask, and pages to read. */
class SearchDataProvider(
    /** The raw HTML of a page, for reading what a result actually says. */
    val fetchPage: suspend (String) -> String = { "" },
    /** The results for a request, given the search server's address. */
    val search: suspend (String, SearchRequest) -> List<SearchSource>,
) {
    companion object {
        /** The provider that asks a real SearXNG server and fetches real pages. */
        fun live(): SearchDataProvider = live(SearchService(), PageService())

        internal fun live(service: SearchService, pages: PageService): SearchDataProvider = SearchDataProvider(
            fetchPage = { address -> pages.fetchHtml(address) },
            search = { baseUrl, request ->
                val category = if (request.kind == SearchKind.NEWS) OllamaTools.NEWS_CATEGORY else null
                service.search(baseUrl, request.query, category).mapNotNull { result ->
                    // Results whose link is missing or is not a web page are dropped.
                    val url = result.url?.takeIf(SearchSource::isWebLink) ?: return@mapNotNull null
                    SearchSource(title = result.title ?: url, url = url, snippet = result.content.orEmpty())
                }
            },
        )
    }
}
