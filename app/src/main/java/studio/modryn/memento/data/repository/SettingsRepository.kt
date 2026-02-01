package studio.modryn.memento.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "memento_settings")

/**
 * Repository for app settings and preferences.
 * 
 * Stores user preferences like the notes folder path using DataStore.
 * DataStore is preferred over SharedPreferences for coroutine support.
 * 
 * Also tracks:
 * - Permission denial count for progressive escalation
 * - Onboarding completion status
 * - Model setup status
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private val KEY_NOTES_FOLDER = stringPreferencesKey("notes_folder")
        private val KEY_NOTES_FOLDER_URI = stringPreferencesKey("notes_folder_uri")
        private val KEY_PERMISSION_DENIAL_COUNT = intPreferencesKey("permission_denial_count")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_MODEL_SETUP_COMPLETED = booleanPreferencesKey("model_setup_completed")
    }
    
    val notesFolderFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[KEY_NOTES_FOLDER] }
    
    val notesFolderUriFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[KEY_NOTES_FOLDER_URI] }
    
    suspend fun getNotesFolder(): String? {
        return context.dataStore.data.first()[KEY_NOTES_FOLDER]
    }
    
    suspend fun setNotesFolder(path: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTES_FOLDER] = path
        }
    }
    
    suspend fun getNotesFolderUri(): String? {
        return context.dataStore.data.first()[KEY_NOTES_FOLDER_URI]
    }
    
    suspend fun setNotesFolderUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTES_FOLDER_URI] = uri
        }
    }
    
    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    // Permission denial tracking for progressive escalation
    
    val permissionDenialCountFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[KEY_PERMISSION_DENIAL_COUNT] ?: 0 }
    
    suspend fun getPermissionDenialCount(): Int {
        return context.dataStore.data.first()[KEY_PERMISSION_DENIAL_COUNT] ?: 0
    }
    
    suspend fun incrementPermissionDenialCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_PERMISSION_DENIAL_COUNT] ?: 0
            preferences[KEY_PERMISSION_DENIAL_COUNT] = current + 1
        }
    }
    
    suspend fun resetPermissionDenialCount() {
        context.dataStore.edit { preferences ->
            preferences[KEY_PERMISSION_DENIAL_COUNT] = 0
        }
    }
    
    // Onboarding status
    
    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_ONBOARDING_COMPLETED] ?: false }
    
    suspend fun isOnboardingCompleted(): Boolean {
        return context.dataStore.data.first()[KEY_ONBOARDING_COMPLETED] ?: false
    }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }
    
    // Model setup status
    
    val modelSetupCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_MODEL_SETUP_COMPLETED] ?: false }
    
    suspend fun isModelSetupCompleted(): Boolean {
        return context.dataStore.data.first()[KEY_MODEL_SETUP_COMPLETED] ?: false
    }
    
    suspend fun setModelSetupCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MODEL_SETUP_COMPLETED] = completed
        }
    }
}
