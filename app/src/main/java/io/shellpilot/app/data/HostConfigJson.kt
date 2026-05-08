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

package io.shellpilot.app.data

import android.content.Context
import androidx.room.RoomDatabase
import io.shellpilot.app.util.HostConstants
import java.nio.charset.Charset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Result of exporting host configurations.
 *
 * @param hostCount Number of hosts exported
 * @param profileCount Number of profiles exported
 */
data class ExportCounts(
    val hostCount: Int,
    val profileCount: Int
)

/**
 * Result of importing host configurations.
 *
 * @param hostsImported Number of hosts newly inserted
 * @param hostsSkipped Number of hosts skipped (already existed)
 * @param profilesImported Number of profiles newly inserted
 * @param profilesSkipped Number of profiles skipped (already existed)
 */
data class ImportCounts(
    val hostsImported: Int,
    val hostsSkipped: Int,
    val profilesImported: Int,
    val profilesSkipped: Int
)

/**
 * Configuration for host configuration export/import.
 *
 * This is a thin wrapper around SchemaBasedExporter that specifies
 * which tables to export for host configurations. All serialization
 * logic is handled generically by SchemaBasedExporter using the
 * Room database schema.
 *
 * Tables exported (in order for foreign key resolution):
 * 1. profiles - Terminal profile configurations
 * 2. hosts - Main host configurations (references profiles)
 * 3. port_forwards - Port forwarding rules (references hosts)
 */
object HostConfigJson {
    /**
     * Tables to export for host configuration, in order.
     * Parent tables must come before child tables for foreign key resolution.
     *
     * Note: Excluded fields (runtime state like last_connect, host_key_algo) are
     * configured in the generateExportSchema Gradle task and marked in the schema.
     */
    val EXPORT_TABLES = listOf("profiles", "hosts", "port_forwards")

    /**
     * Export host configurations to JSON.
     *
     * @param context Android context for loading schema
     * @param database The Room database instance
     * @param pretty Whether to format JSON with indentation
     * @return Pair of JSON string and export counts (hosts and profiles)
     */
    fun exportToJson(context: Context, database: RoomDatabase, pretty: Boolean = true): Pair<String, ExportCounts> {
        val schema = DatabaseSchema.load(context)
        val exporter = createExporter(database, schema)
        val json = exporter.exportToJson(EXPORT_TABLES, pretty)

        // Parse the JSON to count hosts and profiles
        val jsonObj = JSONObject(json)
        val hostCount = jsonObj.optJSONArray("hosts")?.length() ?: 0
        val profileCount = jsonObj.optJSONArray("profiles")?.length() ?: 0

        return Pair(json, ExportCounts(hostCount, profileCount))
    }

    /**
     * Import host configurations from JSON.
     *
     * @param context Android context for loading schema
     * @param database The Room database instance
     * @param jsonString JSON string containing host configurations
     * @return Import counts for hosts and profiles
     */
    fun importFromJson(context: Context, database: RoomDatabase, jsonString: String): ImportCounts {
        val schema = DatabaseSchema.load(context)
        val exporter = createExporter(database, schema)
        val results = exporter.importFromJson(sanitizeJsonForImport(jsonString), EXPORT_TABLES)

        val hostCounts = results["hosts"] ?: Pair(0, 0)
        val profileCounts = results["profiles"] ?: Pair(0, 0)

        return ImportCounts(
            hostsImported = hostCounts.first,
            hostsSkipped = hostCounts.second,
            profilesImported = profileCounts.first,
            profilesSkipped = profileCounts.second
        )
    }

    /**
     * Host import/export用の参照定義を明示する。
     *
     * 変更理由: pubkeyId はsentinel値を保持し、profileId / jumpHostId は
     * export/import単位で安全に再採番する必要があるため。
     */
    private fun createExporter(database: RoomDatabase, schema: DatabaseSchema): SchemaBasedExporter {
        return SchemaBasedExporter(
            database = database,
            schema = schema,
            importReferences = listOf(
                SchemaBasedExporter.ImportReference(
                    tableName = "hosts",
                    fieldPath = "profileId",
                    referencedTableName = "profiles",
                    missingValue = 1L
                ),
                SchemaBasedExporter.ImportReference(
                    tableName = "hosts",
                    fieldPath = "jumpHostId",
                    referencedTableName = "hosts",
                    missingValue = JSONObject.NULL
                )
            ),
            droppedReferences = listOf(
                SchemaBasedExporter.DroppedReference(
                    tableName = "hosts",
                    fieldPath = "pubkeyId",
                    replacementValue = HostConstants.PUBKEYID_NEVER
                ),
                SchemaBasedExporter.DroppedReference(
                    tableName = "profiles",
                    fieldPath = "colorSchemeId",
                    replacementValue = -1L
                )
            ),
            skipRowsWhenParentSkipped = listOf(
                SchemaBasedExporter.ImportReference(
                    tableName = "port_forwards",
                    fieldPath = "hostId",
                    referencedTableName = "hosts"
                )
            )
        )
    }

    /**
     * JSON import前に、export対象外テーブルや旧値に由来する危険な参照を落とす。
     */
    private fun sanitizeJsonForImport(jsonString: String): String {
        val json = JSONObject(jsonString)
        sanitizeProfiles(json)
        sanitizeHosts(json)
        sanitizePortForwards(json)
        return json.toString()
    }

    private fun sanitizeProfiles(json: JSONObject) {
        val profiles = json.optJSONArray("profiles") ?: return
        for (i in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(i)
            val id = profile.optLong("id", 0L)
            val name = if (id == CoreDataSanitizer.DEFAULT_PROFILE_ID) {
                CoreDataSanitizer.DEFAULT_PROFILE_NAME
            } else {
                profile.optString("name", "").trim().ifEmpty { "Profile" }
            }
            profile.put("name", name)
            if (profile.optLong("colorSchemeId", -1L) > 0L) {
                profile.put("colorSchemeId", -1L)
            }
            val fontSize = profile.optInt("fontSize", 10).coerceIn(6, 96)
            profile.put("fontSize", fontSize)
            if (profile.optString("delKey") !in setOf("del", "backspace")) {
                profile.put("delKey", "del")
            }
            val encoding = profile.optString("encoding").trim()
            if (encoding.isEmpty() || !runCatching { Charset.isSupported(encoding) }.getOrDefault(false)) {
                profile.put("encoding", "UTF-8")
            } else {
                profile.put("encoding", encoding)
            }
            if (profile.optString("emulation").trim().isEmpty()) {
                profile.put("emulation", "xterm-256color")
            }
            normalizeOptionalRange(profile, "forceSizeRows", 1..400)
            normalizeOptionalRange(profile, "forceSizeColumns", 1..400)
        }
    }

    private fun sanitizeHosts(json: JSONObject) {
        val hosts = json.optJSONArray("hosts") ?: return
        for (i in 0 until hosts.length()) {
            sanitizeHostObject(hosts.getJSONObject(i))
        }

        val hostById = mutableMapOf<Long, JSONObject>()
        for (i in 0 until hosts.length()) {
            val host = hosts.getJSONObject(i)
            val id = host.optLong("id", 0L)
            if (id > 0L) {
                // 変更理由: 後続の書き換えで循環判定が順序依存にならないよう、元JSONを保持する。
                hostById[id] = JSONObject(host.toString())
            }
        }

        val jumpHostIdsToClear = mutableSetOf<Long>()
        for (i in 0 until hosts.length()) {
            val host = hosts.getJSONObject(i)
            val jumpHostId = host.optLong("jumpHostId", 0L)
            if (jumpHostId <= 0L) continue

            val shouldClearJump = host.optString("protocol") != "ssh" ||
                jumpHostId == host.optLong("id", 0L) ||
                hostById[jumpHostId]?.optString("protocol") != "ssh" ||
                hasJumpCycle(host.optLong("id", 0L), jumpHostId, hostById)

            if (shouldClearJump) {
                jumpHostIdsToClear += host.optLong("id", 0L)
            }
        }

        for (i in 0 until hosts.length()) {
            val host = hosts.getJSONObject(i)
            if (host.optLong("id", 0L) in jumpHostIdsToClear) {
                host.put("jumpHostId", JSONObject.NULL)
            }
        }
    }

    private fun sanitizeHostObject(host: JSONObject) {
        val protocol = when (host.optString("protocol")) {
            "ssh", "telnet", "local" -> host.optString("protocol")
            else -> "ssh"
        }
        host.put("protocol", protocol)

        val port = host.optInt("port", if (protocol == "telnet") 23 else 22)
        host.put(
            "port",
            when {
                protocol == "local" -> 0
                port in 1..65535 -> port
                protocol == "telnet" -> 23
                else -> 22
            }
        )

        if (host.optString("useAuthAgent") !in setOf(
                HostConstants.AUTHAGENT_NO,
                HostConstants.AUTHAGENT_CONFIRM,
                HostConstants.AUTHAGENT_YES
            )
        ) {
            host.put("useAuthAgent", HostConstants.AUTHAGENT_NO)
        }
        if (host.optString("ipVersion") !in setOf("IPV4_AND_IPV6", "IPV4_ONLY", "IPV6_ONLY")) {
            host.put("ipVersion", "IPV4_AND_IPV6")
        }
        val scrollback = host.optInt("scrollbackLines", 140)
        if (scrollback !in 0..100000) {
            host.put("scrollbackLines", 140)
        }

        if (protocol != "ssh") {
            host.put("pubkeyId", HostConstants.PUBKEYID_NEVER)
            host.put("jumpHostId", JSONObject.NULL)
        } else if (host.optLong("pubkeyId", HostConstants.PUBKEYID_NEVER) > 0L) {
            host.put("pubkeyId", HostConstants.PUBKEYID_NEVER)
        }
    }

    private fun hasJumpCycle(rootId: Long, firstJumpHostId: Long, hostById: Map<Long, JSONObject>): Boolean {
        if (rootId <= 0L) return false
        val visited = mutableSetOf(rootId)
        var nextId = firstJumpHostId
        while (nextId > 0L) {
            if (!visited.add(nextId)) {
                return true
            }
            val nextHost = hostById[nextId] ?: return true
            nextId = nextHost.optLong("jumpHostId", 0L)
        }
        return false
    }

    private fun sanitizePortForwards(json: JSONObject) {
        val portForwards = json.optJSONArray("port_forwards") ?: return
        val hosts = json.optJSONArray("hosts") ?: JSONArray()
        val hostById = mutableMapOf<Long, JSONObject>()
        for (i in 0 until hosts.length()) {
            val host = hosts.getJSONObject(i)
            val id = host.optLong("id", 0L)
            if (id > 0L) hostById[id] = host
        }

        val sanitized = JSONArray()
        for (i in 0 until portForwards.length()) {
            val portForward = portForwards.getJSONObject(i)
            val host = hostById[portForward.optLong("hostId", 0L)]
            val normalizedType = when (portForward.optString("type")) {
                HostConstants.PORTFORWARD_DYNAMIC4 -> HostConstants.PORTFORWARD_DYNAMIC5
                HostConstants.PORTFORWARD_LOCAL,
                HostConstants.PORTFORWARD_REMOTE,
                HostConstants.PORTFORWARD_DYNAMIC5 -> portForward.optString("type")
                else -> continue
            }
            if (host?.optString("protocol") != "ssh") continue
            if (portForward.optInt("sourcePort", 0) !in 1..65535) continue

            portForward.put("type", normalizedType)
            if (normalizedType == HostConstants.PORTFORWARD_DYNAMIC5) {
                portForward.put("destAddr", JSONObject.NULL)
                portForward.put("destPort", 0)
                sanitized.put(portForward)
                continue
            }

            val destAddr = portForward.optString("destAddr", "").trim()
            if (destAddr.isEmpty() || portForward.optInt("destPort", 0) !in 1..65535) continue
            portForward.put("destAddr", destAddr)
            sanitized.put(portForward)
        }
        json.put("port_forwards", sanitized)
    }

    private fun normalizeOptionalRange(json: JSONObject, field: String, range: IntRange) {
        if (!json.has(field) || json.isNull(field)) return
        val value = json.optInt(field, Int.MIN_VALUE)
        if (value !in range) {
            json.put(field, JSONObject.NULL)
        }
    }
}
