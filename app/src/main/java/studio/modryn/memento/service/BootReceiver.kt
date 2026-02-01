package studio.modryn.memento.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives BOOT_COMPLETED broadcast to restart the file watcher service.
 * 
 * The silent butler never truly sleeps - it resumes watching after device reboot.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, FileWatcherService::class.java).apply {
                action = FileWatcherService.ACTION_START
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
