package studio.modryn.memento.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import studio.modryn.memento.domain.model.AppState
import studio.modryn.memento.domain.model.ScanProgress
import studio.modryn.memento.ui.theme.MementoColors
import studio.modryn.memento.util.PermissionEscalation
import studio.modryn.memento.util.PermissionHandler

/**
 * Onboarding screen for first-run setup.
 * 
 * Follows the "calm butler" design philosophy:
 * - Privacy-first messaging (local processing emphasized)
 * - Minimal, focused UI
 * - Conversational tone
 * - Dark theme matching the search UI
 * 
 * Flow: Privacy Promise -> Model Setup -> Permissions -> Folder Selection -> Ready
 */
@Composable
fun OnboardingScreen(
    appState: AppState,
    onPermissionsGranted: () -> Unit,
    onPermissionDenied: () -> Unit,
    onFolderSelected: (Uri) -> Unit,
    onSkipOnboarding: (() -> Unit)? = null, // Debug only
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MementoColors.Background)
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = appState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            contentKey = { state ->
                when (state) {
                    is AppState.Loading -> "loading"
                    is AppState.SettingUpModel -> "model"
                    is AppState.PermissionRequired -> "permission"
                    is AppState.FolderSelectionRequired -> "folder"
                    is AppState.Indexing -> "indexing"
                    is AppState.Ready -> "ready"
                }
            },
            label = "onboarding_content"
        ) { state ->
            when (state) {
                is AppState.Loading -> LoadingPage()
                is AppState.SettingUpModel -> ModelSetupPage(
                    progress = state.progress,
                    message = state.message
                )
                is AppState.PermissionRequired -> PermissionPage(
                    denialCount = state.denialCount,
                    onPermissionsGranted = onPermissionsGranted,
                    onPermissionDenied = onPermissionDenied
                )
                is AppState.FolderSelectionRequired -> FolderSelectionPage(
                    onFolderSelected = onFolderSelected,
                    onSkip = onSkipOnboarding
                )
                is AppState.Indexing -> IndexingPage(
                    progress = state.progress
                )
                is AppState.Ready -> {
                    // Should not be shown - parent navigates away
                }
            }
        }
    }
}

@Composable
private fun LoadingPage() {
    CenteredContent {
        CircularProgressIndicator(
            color = MementoColors.Primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Just a moment...",
            style = MaterialTheme.typography.bodyLarge,
            color = MementoColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun ModelSetupPage(
    progress: Int,
    message: String
) {
    OnboardingPage(
        icon = Icons.Default.Memory,
        title = "Setting up your memory",
        description = "The AI runs completely on your device.\nThis only takes a moment."
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        if (progress > 0) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                color = MementoColors.Primary,
                trackColor = MementoColors.SurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                color = MementoColors.Primary,
                trackColor = MementoColors.SurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MementoColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionPage(
    denialCount: Int,
    onPermissionsGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    val escalation = PermissionHandler.getEscalationLevel(denialCount)
    
    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState()
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            onPermissionDenied()
        }
    }
    
    // Build list of permissions to request
    val permissionsToRequest = buildList {
        if (!PermissionHandler.hasStoragePermission(context)) {
            add(PermissionHandler.storagePermission)
        }
        PermissionHandler.notificationPermission?.let { perm ->
            if (!PermissionHandler.hasNotificationPermission(context)) {
                add(perm)
            }
        }
    }
    
    // Show bottom sheet for first denial
    if (showBottomSheet && escalation == PermissionEscalation.BOTTOM_SHEET) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            containerColor = MementoColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Memento needs storage access",
                    style = MaterialTheme.typography.titleLarge,
                    color = MementoColors.OnBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "To read your notes and build your memory, Memento needs access to your files.\n\nAll processing happens locally on your device. Your notes never leave your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MementoColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showBottomSheet = false
                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MementoColors.Primary
                    )
                ) {
                    Text("Grant Access")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // Full screen explainer for second denial
    if (escalation == PermissionEscalation.FULL_SCREEN) {
        FullScreenExplainer(
            onRequestPermission = {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        )
        return
    }
    
    // Settings redirect for 3+ denials
    if (escalation == PermissionEscalation.SETTINGS_REDIRECT) {
        SettingsRedirectPage(
            onOpenSettings = {
                context.startActivity(PermissionHandler.createSettingsIntent(context))
            },
            onCheckPermissions = {
                if (PermissionHandler.hasAllPermissions(context)) {
                    onPermissionsGranted()
                }
            }
        )
        return
    }
    
    // Default: first request or bottom sheet trigger
    OnboardingPage(
        icon = Icons.Default.Lock,
        title = "Privacy is everything",
        description = "Memento reads your notes to build your memory.\nAll processing happens locally on your device.\nYour notes never leave your phone."
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (escalation == PermissionEscalation.BOTTOM_SHEET) {
                    showBottomSheet = true
                } else {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MementoColors.Primary
            ),
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun FullScreenExplainer(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MementoColors.Primary,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Why Memento needs access",
            style = MaterialTheme.typography.headlineSmall,
            color = MementoColors.OnBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PrivacyPoint(
            title = "Read your notes",
            description = "To understand what you've written and build searchable memory"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PrivacyPoint(
            title = "Completely local",
            description = "All AI processing runs on your device. No cloud, no servers"
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PrivacyPoint(
            title = "Deep personalization",
            description = "Because it's local, we can learn your patterns without privacy concerns"
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = MementoColors.Primary
            ),
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Grant Access")
        }
    }
}

@Composable
private fun PrivacyPoint(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MementoColors.Surface, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MementoColors.OnBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MementoColors.OnSurfaceVariant
        )
    }
}

@Composable
private fun SettingsRedirectPage(
    onOpenSettings: () -> Unit,
    onCheckPermissions: () -> Unit
) {
    OnboardingPage(
        icon = Icons.Default.Settings,
        title = "Open Settings to continue",
        description = "Please enable storage access in Settings to use Memento."
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = MementoColors.Primary
            ),
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Open Settings")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onCheckPermissions) {
            Text(
                text = "I've granted permission",
                color = MementoColors.OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun FolderSelectionPage(
    onFolderSelected: (Uri) -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onFolderSelected(uri)
        }
    }
    
    OnboardingPage(
        icon = Icons.Default.Folder,
        title = "Choose your notes folder",
        description = "Select the folder where you keep your notes.\nMemento will watch for changes and keep your memory up to date."
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { folderPickerLauncher.launch(null) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MementoColors.Primary
            ),
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Folder")
        }
        
        // Debug skip option
        if (onSkip != null) {
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip (Debug)",
                    color = MementoColors.OnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun IndexingPage(
    progress: ScanProgress?
) {
    OnboardingPage(
        icon = Icons.Default.Memory,
        title = "Reading your notes",
        description = "Building your memory..."
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        if (progress != null && progress.totalFiles > 0) {
            Text(
                text = "${progress.processedFiles} of ${progress.totalFiles} notes",
                style = MaterialTheme.typography.bodyLarge,
                color = MementoColors.OnSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = { progress.progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                color = MementoColors.Primary,
                trackColor = MementoColors.SurfaceVariant
            )
        } else {
            CircularProgressIndicator(
                color = MementoColors.Primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = progress?.message ?: "This may take a moment...",
            style = MaterialTheme.typography.bodyMedium,
            color = MementoColors.OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    description: String,
    content: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MementoColors.Primary,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MementoColors.OnBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MementoColors.OnSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        content()
    }
}

@Composable
private fun CenteredContent(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}
