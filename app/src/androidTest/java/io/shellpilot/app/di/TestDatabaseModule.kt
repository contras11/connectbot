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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import io.shellpilot.app.data.ShellPilotDatabase

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {
    private const val TEST_DATABASE_NAME = "connectbot_test.db"

    @Provides
    @Singleton
    fun provideShellPilotDatabase(@ApplicationContext context: Context): ShellPilotDatabase {
        return Room.databaseBuilder(
            context,
            ShellPilotDatabase::class.java,
            TEST_DATABASE_NAME
        )
            .addMigrations(
                ShellPilotDatabase.MIGRATION_4_5,
                ShellPilotDatabase.MIGRATION_7_8,
                ShellPilotDatabase.MIGRATION_8_9,
                ShellPilotDatabase.MIGRATION_9_10,
                ShellPilotDatabase.MIGRATION_10_11
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // 変更理由: テストDBでも本番DBと同じDefault profile不変条件を使う。
                    ShellPilotDatabase.ensureDefaultProfileInvariant(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // 変更理由: 計測テストも本番と同じDB整合性guardで動かす。
                    ShellPilotDatabase.normalizeRuntimeInvariants(db)
                }
            })
            .allowMainThreadQueries()
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
