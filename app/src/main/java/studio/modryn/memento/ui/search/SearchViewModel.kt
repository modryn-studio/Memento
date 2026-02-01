package studio.modryn.memento.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.modryn.memento.data.repository.NoteRepository
import studio.modryn.memento.data.repository.SettingsRepository
import studio.modryn.memento.domain.model.AppState
import studio.modryn.memento.domain.model.SearchResult
import studio.modryn.memento.domain.model.SearchState
import javax.inject.Inject

/**
 * ViewModel for the search screen.
 * 
 * Handles:
 * - Search query input with debouncing
 * - Combined semantic + FTS search
 * - App state management
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    // Search query input
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    
    // Search state
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()
    
    // App state
    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()
    
    // Note count for status display
    val noteCount = noteRepository.getNoteCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // Expanded result (for inline expansion)
    private val _expandedResultId = MutableStateFlow<String?>(null)
    val expandedResultId: StateFlow<String?> = _expandedResultId.asStateFlow()
    
    init {
        setupSearchDebounce()
        checkAppState()
    }
    
    @OptIn(FlowPreview::class)
    private fun setupSearchDebounce() {
        _query
            .debounce(300) // Wait 300ms after typing stops
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isBlank()) {
                    _searchState.value = SearchState.Idle
                } else {
                    performSearch(query)
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun checkAppState() {
        viewModelScope.launch {
            val notesFolder = settingsRepository.getNotesFolder()
            
            _appState.value = when {
                notesFolder == null -> AppState.FolderSelectionRequired
                else -> AppState.Ready(noteRepository.getNoteCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0).value)
            }
        }
    }
    
    /**
     * Update search query.
     */
    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }
    
    /**
     * Clear search query.
     */
    fun clearQuery() {
        _query.value = ""
        _searchState.value = SearchState.Idle
        _expandedResultId.value = null
    }
    
    /**
     * Trigger immediate search (e.g., on enter key).
     */
    fun searchNow() {
        val currentQuery = _query.value
        if (currentQuery.isNotBlank()) {
            viewModelScope.launch {
                performSearch(currentQuery)
            }
        }
    }
    
    /**
     * Toggle result expansion.
     */
    fun toggleExpanded(resultId: String) {
        _expandedResultId.value = if (_expandedResultId.value == resultId) null else resultId
    }
    
    /**
     * Collapse expanded result.
     */
    fun collapseResult() {
        _expandedResultId.value = null
    }
    
    /**
     * Set notes folder path.
     */
    fun setNotesFolder(path: String) {
        viewModelScope.launch {
            settingsRepository.setNotesFolder(path)
            _appState.value = AppState.Indexing
            
            // Trigger initial scan
            noteRepository.scanFolder(path)
            
            _appState.value = AppState.Ready(noteCount.value)
        }
    }
    
    private suspend fun performSearch(query: String) {
        _searchState.value = SearchState.Loading
        
        try {
            val results = noteRepository.search(query)
            
            _searchState.value = if (results.isEmpty()) {
                SearchState.Empty(query)
            } else {
                SearchState.Success(results)
            }
        } catch (e: Exception) {
            _searchState.value = SearchState.Error(e.message ?: "Search failed")
        }
    }
}
