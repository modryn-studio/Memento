package studio.modryn.memento.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import studio.modryn.memento.R
import studio.modryn.memento.data.repository.NoteRepository
import studio.modryn.memento.data.repository.SettingsRepository
import studio.modryn.memento.ui.MainActivity
import java.io.File
import javax.inject.Inject

/**
 * Foreground service that monitors the notes folder for changes.
 * 
 * This is the "invisible butler" that works silently in the background,
 * keeping your knowledge graph up to date without any user intervention.
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
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "memento_file_watcher"
        
        const val ACTION_START = "studio.modryn.memento.START_WATCHING"
        const val ACTION_STOP = "studio.modryn.memento.STOP_WATCHING"
        const val ACTION_RESCAN = "studio.modryn.memento.RESCAN_NOTES"
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
            val notesPath = settingsRepository.getNotesFolder()
            
            if (notesPath != null) {
                // Initial scan
                noteRepository.scanFolder(notesPath)
                
                // Set up file observer
                setupFileObserver(notesPath)
            }
        }
    }
    
    private fun setupFileObserver(path: String) {
        fileObserver?.stopWatching()
        
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) return
        
        fileObserver = object : FileObserver(folder, CREATE or DELETE or MODIFY or MOVED_FROM or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                
                // Only process markdown and text files
                if (!path.endsWith(".md") && !path.endsWith(".txt")) return
                
                val fullPath = File(folder, path).absolutePath
                
                serviceScope.launch {
                    when (event) {
                        CREATE, MOVED_TO -> {
                            noteRepository.processFile(fullPath)
                        }
                        DELETE, MOVED_FROM -> {
                            noteRepository.removeFile(fullPath)
                        }
                        MODIFY -> {
                            noteRepository.processFile(fullPath)
                        }
                    }
                }
            }
        }
        
        fileObserver?.startWatching()
    }
    
    private fun stopWatching() {
        fileObserver?.stopWatching()
        fileObserver = null
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
            .setSmallIcon(android.R.drawable.ic_menu_search) // TODO: Replace with Memento icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
