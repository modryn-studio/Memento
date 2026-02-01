package studio.modryn.memento.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Handles runtime permission checks with version-aware logic.
 * 
 * Supports progressive denial escalation:
 * - 1st denial: Bottom sheet explanation
 * - 2nd denial: Full screen explainer
 * - 3rd+ denial: Deep link to Settings
 */
object PermissionHandler {
    
    /**
     * Storage permission required based on SDK version.
     * - SDK 26-32: READ_EXTERNAL_STORAGE
     * - SDK 33+: READ_MEDIA_DOCUMENTS (not strictly required for SAF)
     */
    val storagePermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses granular media permissions
            // For documents, SAF is preferred and doesn't require permissions
            Manifest.permission.READ_MEDIA_IMAGES // Placeholder - SAF handles documents
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    
    /**
     * Check if storage permission is granted.
     * On Android 11+, SAF (Storage Access Framework) is used instead,
     * which doesn't require runtime permissions.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: SAF doesn't require permission, check for persisted URIs
            true // SAF handles this via persisted URI permissions
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if notification permission is granted.
     * Only required on Android 13+.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required before Android 13
        }
    }
    
    /**
     * Get the notification permission string (Android 13+ only).
     */
    val notificationPermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }
    
    /**
     * Check if all required permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        return hasStoragePermission(context) && hasNotificationPermission(context)
    }
    
    /**
     * Create intent to open app settings for manual permission grant.
     */
    fun createSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    
    /**
     * Determine escalation level based on denial count.
     */
    fun getEscalationLevel(denialCount: Int): PermissionEscalation {
        return when {
            denialCount <= 0 -> PermissionEscalation.FIRST_REQUEST
            denialCount == 1 -> PermissionEscalation.BOTTOM_SHEET
            denialCount == 2 -> PermissionEscalation.FULL_SCREEN
            else -> PermissionEscalation.SETTINGS_REDIRECT
        }
    }
}

/**
 * Permission escalation levels for progressive denial handling.
 */
enum class PermissionEscalation {
    /** First time request - just ask normally */
    FIRST_REQUEST,
    /** 1st denial - show bottom sheet with brief explanation */
    BOTTOM_SHEET,
    /** 2nd denial - show full screen explainer with privacy benefits */
    FULL_SCREEN,
    /** 3rd+ denial - redirect to system Settings */
    SETTINGS_REDIRECT
}

/**
 * Result of permission check.
 */
sealed class PermissionStatus {
    object Granted : PermissionStatus()
    data class Denied(val escalation: PermissionEscalation) : PermissionStatus()
    object PermanentlyDenied : PermissionStatus()
}
