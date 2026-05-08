/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.shellpilot.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.shellpilot.app.data.ShellPilotDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "connectbot.db"

    @Provides
    @Singleton
    fun provideShellPilotDatabase(@ApplicationContext context: Context): ShellPilotDatabase {
        return Room.databaseBuilder(
            context,
            ShellPilotDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(ShellPilotDatabase.MIGRATION_4_5, ShellPilotDatabase.MIGRATION_7_8, ShellPilotDatabase.MIGRATION_8_9)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Create default profile on fresh database creation
                    db.execSQL("""
                        INSERT INTO profiles (name, color_scheme_id, font_size, del_key, encoding, emulation)
                        VALUES ('Default', -1, 10, 'del', 'UTF-8', 'xterm-256color')
                    """.trimIndent())
                }
            })
            .build()
    }

    @Provides
    fun provideHostDao(database: ShellPilotDatabase) = database.hostDao()

    @Provides
    fun providePubkeyDao(database: ShellPilotDatabase) = database.pubkeyDao()

    @Provides
    fun providePortForwardDao(database: ShellPilotDatabase) = database.portForwardDao()

    @Provides
    fun provideKnownHostDao(database: ShellPilotDatabase) = database.knownHostDao()

    @Provides
    fun provideColorSchemeDao(database: ShellPilotDatabase) = database.colorSchemeDao()

    @Provides
    fun provideProfileDao(database: ShellPilotDatabase) = database.profileDao()
}
