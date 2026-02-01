package studio.modryn.memento.ui.search

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import studio.modryn.memento.BuildConfig
import studio.modryn.memento.data.database.dao.ScanProgressDao
import studio.modryn.memento.data.embeddings.EmbeddingService
import studio.modryn.memento.data.repository.NoteRepository
import studio.modryn.memento.data.repository.SettingsRepository
import studio.modryn.memento.domain.model.AppState
import studio.modryn.memento.domain.model.ScanProgress
import studio.modryn.memento.domain.model.SearchResult
import studio.modryn.memento.domain.model.SearchState
import studio.modryn.memento.service.ModelSetupProgress
import studio.modryn.memento.service.ModelSetupService
import studio.modryn.memento.service.NoteProcessingWorker
import studio.modryn.memento.util.PermissionHandler
import javax.inject.Inject

/**
 * ViewModel for the search screen and onboarding flow.
 * 
 * Handles:
 * - Complete onboarding flow (model setup -> permissions -> folder selection -> indexing)
 * - Search query input with debouncing
 * - Combined semantic + FTS search
 * - App state management
 * 
 * Design: Enforces complete setup in production builds.
 * Debug builds can skip onboarding for testing.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val embeddingService: EmbeddingService,
    private val modelSetupService: ModelSetupService,
    private val scanProgressDao: ScanProgressDao
) : ViewModel() {
    
    // Search query input
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    
    // Search state
    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()
    
    // App state (onboarding + ready states)
    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()
    
    // Note count for status display
    val noteCount = noteRepository.getNoteCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    
    // Expanded result (for inline expansion)
    private val _expandedResultId = MutableStateFlow<String?>(null)
    val expandedResultId: StateFlow<String?> = _expandedResultId.asStateFlow()
    
    // Track if we can skip onboarding (debug builds only)
    val canSkipOnboarding: Boolean = BuildConfig.DEBUG
    
    init {
        setupSearchDebounce()
        checkAppState()
        observeModelSetupProgress()
        observeScanProgress()
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
    
    private fun observeModelSetupProgress() {
        modelSetupService.setupProgress
            .onEach { progress ->
                when (progress) {
                    is ModelSetupProgress.InProgress -> {
                        _appState.value = AppState.SettingUpModel(progress.percent, progress.message)
                    }
                    is ModelSetupProgress.Completed -> {
                        // Model ready, continue to next state
                        checkAppState()
                    }
                    is ModelSetupProgress.Error -> {
                        // Stay on model setup with error message
                        _appState.value = AppState.SettingUpModel(0, "Setup failed: ${progress.message}")
                    }
                    else -> { /* NotStarted - handled by checkAppState */ }
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun observeScanProgress() {
        scanProgressDao.observeCurrentProgress()
            .onEach { entity ->
                if (entity != null && !entity.isComplete && _appState.value is AppState.Indexing) {
                    _appState.value = AppState.Indexing(
                        ScanProgress(
                            processedFiles = entity.processedFiles,
                            totalFiles = entity.totalFiles,
                            message = "Reading your notes..."
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }
    
    /**
     * Check app state and determine what step of onboarding we're on.
     * 
     * Flow: SettingUpModel -> PermissionRequired -> FolderSelectionRequired -> Indexing -> Ready
     */
    fun checkAppState() {
        viewModelScope.launch {
            _appState.value = AppState.Loading
            
            // Step 1: Check if model is set up
            if (!embeddingService.isModelAvailable()) {
                if (!settingsRepository.isModelSetupCompleted()) {
                    _appState.value = AppState.SettingUpModel(0, "Preparing AI model...")
                    // Trigger model setup
                    modelSetupService.setupModel()
                    return@launch
                }
            }
            
            // Step 2: Check permissions (storage for pre-Android 11, notifications for Android 13+)
            if (!PermissionHandler.hasAllPermissions(context)) {
                val denialCount = settingsRepository.getPermissionDenialCount()
                _appState.value = AppState.PermissionRequired(denialCount)
                return@launch
            }
            
            // Step 3: Check if notes folder is configured
            val notesFolder = settingsRepository.getNotesFolder()
            val notesFolderUri = settingsRepository.getNotesFolderUri()
            
            if (notesFolder == null && notesFolderUri == null) {
                _appState.value = AppState.FolderSelectionRequired
                return@launch
            }
            
            // Step 4: Check for incomplete scan
            val scanProgress = scanProgressDao.getCurrentProgress()
            if (scanProgress != null && !scanProgress.isComplete) {
                _appState.value = AppState.Indexing(
                    ScanProgress(
                        processedFiles = scanProgress.processedFiles,
                        totalFiles = scanProgress.totalFiles,
                        message = "Resuming..."
                    )
                )
                // Resume scan
                resumeScan()
                return@launch
            }
            
            // Step 5: All set up, ready for search
            _appState.value = AppState.Ready(noteCount.value)
        }
    }
    
    /**
     * Called when permissions are granted.
     */
    fun onPermissionsGranted() {
        viewModelScope.launch {
            settingsRepository.resetPermissionDenialCount()
            checkAppState()
        }
    }
    
    /**
     * Called when permissions are denied.
     */
    fun onPermissionDenied() {
        viewModelScope.launch {
            settingsRepository.incrementPermissionDenialCount()
            val denialCount = settingsRepository.getPermissionDenialCount()
            _appState.value = AppState.PermissionRequired(denialCount)
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
     * Set notes folder from SAF URI.
     */
    fun setNotesFolderUri(uri: Uri) {
        viewModelScope.launch {
            // Persist URI permission
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            // Save URI
            settingsRepository.setNotesFolderUri(uri.toString())
            
            // Try to convert to file path for FileObserver
            val filePath = convertUriToPath(uri)
            if (filePath != null) {
                settingsRepository.setNotesFolder(filePath)
            }
            
            // Mark onboarding complete
            settingsRepository.setOnboardingCompleted(true)
            
            // Start indexing
            _appState.value = AppState.Indexing(null)
            startInitialScan(filePath ?: uri.toString())
        }
    }
    
    /**
     * Set notes folder path directly (for legacy support).
     */
    fun setNotesFolder(path: String) {
        viewModelScope.launch {
            settingsRepository.setNotesFolder(path)
            settingsRepository.setOnboardingCompleted(true)
            
            _appState.value = AppState.Indexing(null)
            startInitialScan(path)
        }
    }
    
    /**
     * Skip onboarding (debug builds only).
     */
    fun skipOnboarding() {
        if (!canSkipOnboarding) return
        
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            _appState.value = AppState.Ready(0)
        }
    }
    
    private fun startInitialScan(folderPath: String) {
        val workRequest = OneTimeWorkRequestBuilder<NoteProcessingWorker>()
            .setInputData(
                workDataOf(
                    NoteProcessingWorker.KEY_OPERATION to NoteProcessingWorker.OPERATION_FULL_SCAN,
                    NoteProcessingWorker.KEY_FOLDER_PATH to folderPath
                )
            )
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            NoteProcessingWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        
        // Observe work completion
        WorkManager.getInstance(context)
            .getWorkInfoByIdFlow(workRequest.id)
            .onEach { workInfo ->
                if (workInfo?.state?.isFinished == true) {
                    _appState.value = AppState.Ready(noteCount.value)
                }
            }
            .launchIn(viewModelScope)
    }
    
    private fun resumeScan() {
        viewModelScope.launch {
            val folderPath = settingsRepository.getNotesFolder() ?: return@launch
            
            val workRequest = OneTimeWorkRequestBuilder<NoteProcessingWorker>()
                .setInputData(
                    workDataOf(
                        NoteProcessingWorker.KEY_OPERATION to NoteProcessingWorker.OPERATION_RESUME,
                        NoteProcessingWorker.KEY_FOLDER_PATH to folderPath
                    )
                )
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            
            WorkManager.getInstance(context).enqueueUniqueWork(
                NoteProcessingWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
            
            WorkManager.getInstance(context)
                .getWorkInfoByIdFlow(workRequest.id)
                .onEach { workInfo ->
                    if (workInfo?.state?.isFinished == true) {
                        _appState.value = AppState.Ready(noteCount.value)
                    }
                }
                .launchIn(viewModelScope)
        }
    }
    
    private fun convertUriToPath(uri: Uri): String? {
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile != null && documentFile.exists()) {
                val docId = uri.lastPathSegment ?: return null
                if (docId.contains(":")) {
                    val split = docId.split(":")
                    val storageType = split[0]
                    val relativePath = split.getOrNull(1) ?: ""
                    
                    if (storageType == "primary") {
                        "/storage/emulated/0/$relativePath"
                    } else {
                        "/storage/$storageType/$relativePath"
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
