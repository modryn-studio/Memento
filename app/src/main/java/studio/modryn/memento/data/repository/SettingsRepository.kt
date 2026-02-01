package studio.modryn.memento.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private val KEY_NOTES_FOLDER = stringPreferencesKey("notes_folder")
        private val KEY_NOTES_FOLDER_URI = stringPreferencesKey("notes_folder_uri")
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
}
