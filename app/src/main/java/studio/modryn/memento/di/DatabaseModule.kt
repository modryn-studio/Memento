package studio.modryn.memento.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import studio.modryn.memento.data.database.MementoDatabase
import studio.modryn.memento.data.database.dao.EmbeddingDao
import studio.modryn.memento.data.database.dao.NoteDao
import javax.inject.Singleton

/**
 * Hilt module for database dependencies.
 * 
 * Provides the Room database and DAOs as singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideMementoDatabase(
        @ApplicationContext context: Context
    ): MementoDatabase {
        return Room.databaseBuilder(
            context,
            MementoDatabase::class.java,
            MementoDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideNoteDao(database: MementoDatabase): NoteDao {
        return database.noteDao()
    }
    
    @Provides
    @Singleton
    fun provideEmbeddingDao(database: MementoDatabase): EmbeddingDao {
        return database.embeddingDao()
    }
}
