package studio.modryn.memento

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Memento Application - Local AI-powered note memory system.
 * 
 * Design Principles:
 * - Invisible First: UI only appears when needed
 * - Speed as Design: Every interaction must feel instant
 * - Privacy First: All processing is local, nothing leaves device
 * - Silent System: Works in background, surfaces information proactively
 */
@HiltAndroidApp
class MementoApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
