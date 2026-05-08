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

package io.shellpilot.app.data.migration

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.shellpilot.app.data.ShellPilotDatabase
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * exported schema を使って、古いDBが最新schemaへ到達できることを検証する。
 *
 * 変更理由: AutoMigration と手動migrationの組み合わせはidentity hash driftを
 * 見落としやすいため、全履歴からlatestへの最低限のschema validationを残す。
 */
@RunWith(AndroidJUnit4::class)
class ShellPilotDatabaseMigrationChainTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ShellPilotDatabase::class.java
    )

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        (1..8).forEach { version ->
            context.deleteDatabase(dbName(version))
        }
    }

    @Test
    fun allExportedSchemasMigrateToLatest() {
        (1..8).forEach { version ->
            helper.createDatabase(dbName(version), version).close()

            helper.runMigrationsAndValidate(
                dbName(version),
                ShellPilotDatabase.SCHEMA_VERSION,
                true,
                ShellPilotDatabase.MIGRATION_4_5,
                ShellPilotDatabase.MIGRATION_7_8,
                ShellPilotDatabase.MIGRATION_8_9
            ).close()
        }
    }

    private fun dbName(version: Int): String = "shellpilot-migration-chain-v$version"
}
