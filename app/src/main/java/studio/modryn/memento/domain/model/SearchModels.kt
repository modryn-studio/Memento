package studio.modryn.memento.domain.model

/**
 * Represents a search result from the knowledge graph.
 * 
 * Combines results from both FTS (keyword) and semantic (vector) search.
 */
data class SearchResult(
    val noteId: String,
    val noteTitle: String,
    val noteFileName: String,
    val filePath: String,
    val matchedText: String,
    val score: Float,
    val matchType: MatchType
) {
    enum class MatchType {
        SEMANTIC,  // Found via vector similarity
        KEYWORD,   // Found via FTS keyword match
        HYBRID     // Matched in both
    }
}

/**
 * UI state for search results.
 */
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<SearchResult>) : SearchState()
    data class Empty(val query: String) : SearchState()
    data class Error(val message: String) : SearchState()
}

/**
 * UI state for the overall app.
 */
sealed class AppState {
    object Loading : AppState()
    object PermissionRequired : AppState()
    object FolderSelectionRequired : AppState()
    object Indexing : AppState()
    data class Ready(val noteCount: Int) : AppState()
}
