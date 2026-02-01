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
 * 
 * Follows onboarding flow:
 * SettingUpModel -> PermissionRequired -> FolderSelectionRequired -> Indexing -> Ready
 */
sealed class AppState {
    /** Initial loading state */
    object Loading : AppState()
    
    /** Model files being decompressed on first run */
    data class SettingUpModel(val progress: Int, val message: String) : AppState()
    
    /** Storage/notification permissions needed */
    data class PermissionRequired(val denialCount: Int) : AppState()
    
    /** User needs to select notes folder */
    object FolderSelectionRequired : AppState()
    
    /** Initial scan in progress */
    data class Indexing(val progress: ScanProgress?) : AppState()
    
    /** App is ready for search */
    data class Ready(val noteCount: Int) : AppState()
}

/**
 * Progress information for folder scanning.
 */
data class ScanProgress(
    val processedFiles: Int,
    val totalFiles: Int,
    val message: String = ""
) {
    val progressPercent: Int
        get() = if (totalFiles > 0) (processedFiles * 100) / totalFiles else 0
    
    val isComplete: Boolean
        get() = processedFiles >= totalFiles
}
