package studio.modryn.memento.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import studio.modryn.memento.ui.search.SearchScreen
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
                    SearchScreen()
                }
            }
        }
    }
}
