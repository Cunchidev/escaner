package com.cunchidev.escaner

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.cunchidev.escaner.data.db.AppDatabase
import com.cunchidev.escaner.data.db.DocumentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp
class EscanerApp : Application()

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Sen fallbackToDestructiveMigration a propósito: perder a base de datos é perder a
    // biblioteca. Cada cambio de esquema, coa súa Migration.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "escaner.db").build()

    @Provides
    fun provideDocumentDao(db: AppDatabase): DocumentDao = db.documentDao()
}
