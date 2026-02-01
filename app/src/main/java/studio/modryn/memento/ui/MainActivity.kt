package studio.modryn.memento.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import studio.modryn.memento.domain.model.AppState
import studio.modryn.memento.service.FileWatcherService
import studio.modryn.memento.ui.onboarding.OnboardingScreen
import studio.modryn.memento.ui.search.SearchScreen
import studio.modryn.memento.ui.search.SearchViewModel
import studio.modryn.memento.ui.theme.MementoTheme

/**
 * Main entry point for Memento.
 * 
 * In true Silent System fashion, this activity should rarely be opened directly.
 * The primary interaction is through the global search overlay.
 * This screen exists for:
 * - Initial setup and permission granting
 * - Settings and folder selection
 * - Status overview (optional)
 * 
 * Routes between OnboardingScreen and SearchScreen based on app state.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MementoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewModel: SearchViewModel = hiltViewModel()
                    val appState by viewModel.appState.collectAsState()
                    
                    AnimatedContent(
                        targetState = appState,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        contentKey = { state ->
                            when (state) {
                                is AppState.Ready -> "ready"
                                else -> "onboarding"
                            }
                        },
                        label = "main_content"
                    ) { state ->
                        when (state) {
                            is AppState.Ready -> {
                                // Use LaunchedEffect to avoid calling on every recomposition
                                LaunchedEffect(Unit) {
                                    startFileWatcherService()
                                }
                                
                                SearchScreen(viewModel = viewModel)
                            }
                            else -> {
                                // Show onboarding for all non-ready states
                                OnboardingScreen(
                                    appState = state,
                                    onPermissionsGranted = viewModel::onPermissionsGranted,
                                    onPermissionDenied = viewModel::onPermissionDenied,
                                    onFolderSelected = viewModel::setNotesFolderUri,
                                    onSkipOnboarding = if (viewModel.canSkipOnboarding) {
                                        viewModel::skipOnboarding
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning from Settings
        // The ViewModel will be re-observed and update state
    }
    
    private fun startFileWatcherService() {
        val serviceIntent = Intent(this, FileWatcherService::class.java).apply {
            action = FileWatcherService.ACTION_START
        }
        startForegroundService(serviceIntent)
    }
}
