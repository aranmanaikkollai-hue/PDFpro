package com.propdf.editor.di

import android.content.Context
import com.propdf.editor.data.local.RecentFilesDatabase
import com.propdf.editor.data.local.RecentFilesDao
import com.propdf.editor.data.repository.AiSummaryManager
import com.propdf.editor.data.repository.OcrManager
import com.propdf.editor.data.repository.PdfOperationsManager
import com.propdf.editor.data.repository.RecentFilesRepository
import com.propdf.editor.data.repository.SignatureManager
import com.propdf.editor.utils.FileHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePdfOperationsManager(
        @ApplicationContext context: Context
    ): PdfOperationsManager = PdfOperationsManager(context)

    @Provides
    @Singleton
    fun provideOcrManager(): OcrManager = OcrManager()

    @Provides
    @Singleton
    fun provideAiSummaryManager(
        @ApplicationContext context: Context
    ): AiSummaryManager = AiSummaryManager(context)

    @Provides
    @Singleton
    fun provideSignatureManager(
        @ApplicationContext context: Context
    ): SignatureManager = SignatureManager(context)

    @Provides
    @Singleton
    fun provideRecentFilesRepository(
        dao: RecentFilesDao,
        @ApplicationContext context: Context
    ): RecentFilesRepository = RecentFilesRepository(dao, context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): RecentFilesDatabase = RecentFilesDatabase.get(context)

    @Provides
    @Singleton
    fun provideRecentFilesDao(
        db: RecentFilesDatabase
    ): RecentFilesDao = db.recentFilesDao()

    // FileHelper is now @Inject constructor - no need for provider
    // Dagger will auto-provide it
}
