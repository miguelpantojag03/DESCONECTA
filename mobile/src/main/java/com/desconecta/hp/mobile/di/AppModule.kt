package com.desconecta.hp.mobile.di

import android.content.Context
import androidx.room.Room
import com.desconecta.hp.mobile.data.db.HpDatabase
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
    fun database(@ApplicationContext context: Context): HpDatabase =
        Room.databaseBuilder(context, HpDatabase::class.java, "hp.db").build()
}
