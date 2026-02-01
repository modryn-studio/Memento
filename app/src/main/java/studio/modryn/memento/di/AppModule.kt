package studio.modryn.memento.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import studio.modryn.memento.data.database.dao.EmbeddingDao
import studio.modryn.memento.data.database.dao.NoteDao
import studio.modryn.memento.data.embeddings.EmbeddingService
import studio.modryn.memento.data.parser.NoteParser
import studio.modryn.memento.data.repository.NoteRepository
import studio.modryn.memento.data.repository.SettingsRepository
import javax.inject.Singleton

/**
 * Hilt module for app-level dependencies.
 * 
 * Provides repositories and services as singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideNoteParser(): NoteParser {
        return NoteParser()
    }
    
    @Provides
    @Singleton
    fun provideEmbeddingService(
        @ApplicationContext context: Context
    ): EmbeddingService {
        return EmbeddingService(context)
    }
    
    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }
    
    @Provides
    @Singleton
    fun provideNoteRepository(
        noteDao: NoteDao,
        embeddingDao: EmbeddingDao,
        noteParser: NoteParser,
        embeddingService: EmbeddingService,
        settingsRepository: SettingsRepository
    ): NoteRepository {
        return NoteRepository(
            noteDao = noteDao,
            embeddingDao = embeddingDao,
            noteParser = noteParser,
            embeddingService = embeddingService,
            settingsRepository = settingsRepository
        )
    }
}
