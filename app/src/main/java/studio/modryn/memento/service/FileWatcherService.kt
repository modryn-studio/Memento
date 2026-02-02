package studio.modryn.memento.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import studio.modryn.memento.R
import studio.modryn.memento.data.repository.NoteRepository
import studio.modryn.memento.data.repository.SettingsRepository
import studio.modryn.memento.ui.MainActivity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Foreground service that monitors the notes folder for changes.
 * 
 * This is the "invisible butler" that works silently in the background,
 * keeping your knowledge graph up to date without any user intervention.
 * 
 * Hybrid approach for file monitoring:
 * 1. Primary: FileObserver when file path is available (fast, real-time)
 * 2. Fallback: WorkManager periodic polling when using SAF URIs
 * 
 * Uses FileObserver to detect:
 * - New files added
 * - Existing files modified
 * - Files deleted
 * 
 * When changes are detected, it triggers the note processing pipeline
 * to update embeddings and the search index.
 */
@AndroidEntryPoint
class FileWatcherService : Service() {
    
    @Inject
    lateinit var noteRepository: NoteRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: FileObserver? = null
    private var usingPollingFallback = false
    
    // Debounce map to coalesce rapid file events (e.g., editor auto-save)
    private val pendingEvents = ConcurrentHashMap<String, Job>()
    private val eventDebounceMs = 500L // Wait 500ms before processing
    
    companion object {
        private const val TAG = "FileWatcherService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "memento_file_watcher"
        
        const val ACTION_START = "studio.modryn.memento.START_WATCHING"
        const val ACTION_STOP = "studio.modryn.memento.STOP_WATCHING"
        const val ACTION_RESCAN = "studio.modryn.memento.RESCAN_NOTES"
        
        // Polling fallback interval (15 minutes minimum for WorkManager)
        private const val POLLING_INTERVAL_MINUTES = 15L
        private const val POLLING_WORK_NAME = "memento_polling_scan"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWatching()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESCAN -> {
                serviceScope.launch {
                    noteRepository.fullRescan()
                }
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startWatching()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        stopWatching()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    private fun startWatching() {
        serviceScope.launch {
            // Try file path first (works on most devices)
            val notesPath = settingsRepository.getNotesFolder()
            val notesUri = settingsRepository.getNotesFolderUri()
            
            when {
                // Direct file path available
                notesPath != null && File(notesPath).exists() -> {
                    Log.d(TAG, "Using FileObserver with path: $notesPath")
                    noteRepository.scanFolder(notesPath)
                    setupFileObserver(notesPath)
                    usingPollingFallback = false
                }
                // SAF URI available - try to convert to path
                notesUri != null -> {
                    val uri = Uri.parse(notesUri)
                    val convertedPath = convertUriToPath(uri)
                    
                    if (convertedPath != null && File(convertedPath).exists()) {
                        Log.d(TAG, "Converted SAF URI to path: $convertedPath")
                        settingsRepository.setNotesFolder(convertedPath)
                        noteRepository.scanFolder(convertedPath)
                        setupFileObserver(convertedPath)
                        usingPollingFallback = false
                    } else {
                        // Fallback to polling with SAF
                        Log.d(TAG, "Using WorkManager polling fallback for SAF URI")
                        setupPollingFallback()
                        usingPollingFallback = true
                    }
                }
                else -> {
                    Log.w(TAG, "No notes folder configured")
                }
            }
        }
    }
    
    /**
     * Attempt to convert a SAF URI to a file path.
     * This works for most external storage locations on Android 11+.
     */
    private fun convertUriToPath(uri: Uri): String? {
        return try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            if (documentFile != null && documentFile.exists()) {
                // Try to extract path from URI
                // Format: content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FNotes
                val docId = uri.lastPathSegment ?: return null
                if (docId.contains(":")) {
                    val split = docId.split(":")
                    val storageType = split[0]
                    val relativePath = split.getOrNull(1) ?: ""
                    
                    if (storageType == "primary") {
                        // Primary storage
                        val basePath = "/storage/emulated/0"
                        "$basePath/$relativePath"
                    } else {
                        // External SD card or other storage
                        "/storage/$storageType/$relativePath"
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert URI to path", e)
            null
        }
    }
    
    /**
     * Set up periodic WorkManager polling as fallback when FileObserver can't be used.
     */
    private fun setupPollingFallback() {
        val workRequest = PeriodicWorkRequestBuilder<NoteProcessingWorker>(
            POLLING_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setInputData(
                workDataOf(
                    NoteProcessingWorker.KEY_OPERATION to NoteProcessingWorker.OPERATION_INCREMENTAL
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            POLLING_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
    
    /**
     * Cancel polling fallback when switching to FileObserver.
     */
    private fun cancelPollingFallback() {
        WorkManager.getInstance(this).cancelUniqueWork(POLLING_WORK_NAME)
    }
    
    private fun setupFileObserver(path: String) {
        fileObserver?.stopWatching()
        cancelPollingFallback()
        
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) {
            Log.w(TAG, "Folder does not exist: $path")
            return
        }
        
        fileObserver = object : FileObserver(folder, CREATE or DELETE or MODIFY or MOVED_FROM or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                
                // Only process markdown and text files
                if (!path.endsWith(".md") && !path.endsWith(".txt")) return
                
                val fullPath = File(folder, path).absolutePath
                
                // Debounce events - cancel previous pending job and schedule new one
                // This coalesces rapid events (e.g., editor saving multiple times)
                pendingEvents[fullPath]?.cancel()
                
                val isDelete = event == DELETE || event == MOVED_FROM
                
                pendingEvents[fullPath] = serviceScope.launch {
                    delay(eventDebounceMs)
                    pendingEvents.remove(fullPath)
                    
                    try {
                        if (isDelete) {
                            noteRepository.removeFile(fullPath)
                        } else {
                            noteRepository.processFile(fullPath)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing file event: $fullPath", e)
                    }
                }
            }
        }
        
        fileObserver?.startWatching()
        Log.d(TAG, "FileObserver started for: $path")
    }
    
    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
        if (usingPollingFallback) {
            cancelPollingFallback()
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Note Watcher",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps your notes synchronized"
            setShowBadge(false)
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.file_watcher_notification_title))
            .setContentText(getString(R.string.file_watcher_notification_text))
            .setSmallIcon(R.drawable.ic_memento)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
