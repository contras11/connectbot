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
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import io.shellpilot.app.data.dao.HostDao
import io.shellpilot.app.data.dao.KnownHostDao
import io.shellpilot.app.data.dao.PortForwardDao
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.KnownHost
import io.shellpilot.app.data.entity.PortForward
import io.shellpilot.app.util.SecurePasswordStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing SSH host configurations and connections.
 * Handles host CRUD operations, known hosts, and port forwards.
 *
 * @param context Application context for accessing schema assets
 * @param database The Room database instance for export/import operations
 * @param hostDao The DAO for accessing host data
 * @param portForwardDao The DAO for accessing port forward data
 * @param knownHostDao The DAO for accessing known host data
 */
@Singleton
class HostRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ShellPilotDatabase,
    private val hostDao: HostDao,
    private val portForwardDao: PortForwardDao,
    private val knownHostDao: KnownHostDao,
    private val securePasswordStorage: SecurePasswordStorage
) {

    // ============================================================================
    // Host Operations
    // ============================================================================

    /**
     * Observe all hosts reactively.
     *
     * @return Flow of host list that updates automatically
     */
    fun observeHosts(): Flow<List<Host>> = hostDao.observeAll()
        .map { sanitizeHosts(it) }

    /**
     * Observe all hosts sorted by color reactively.
     *
     * @return Flow of host list sorted by color
     */
    fun observeHostsSortedByColor(): Flow<List<Host>> = hostDao.observeAllSortedByColor()
        .map { sanitizeHosts(it).sortedBy { host -> host.color } }

    /**
     * Observe a specific host reactively.
     *
     * @param hostId The host ID
     * @return Flow of host that updates automatically
     */
    fun observeHost(hostId: Long): Flow<Host?> = hostDao.observeById(hostId)
        .map { host -> host?.let { sanitizeHost(it) } }

    /**
     * Get all hosts.
     *
     * @param sortedByColor If true, hosts will be grouped by color
     * @return List of all hosts
     */
    suspend fun getHosts(sortedByColor: Boolean = false): List<Host> {
        val hosts = sanitizeHosts(hostDao.getAll())
        return if (sortedByColor) {
            // 変更理由: 壊れた参照を補正した後に表示順を決める。
            hosts.sortedBy { it.color }
        } else {
            hosts
        }
    }

    /**
     * Find a host by its unique ID.
     *
     * @param hostId The host ID
     * @return The host, or null if not found
     */
    suspend fun findHostById(hostId: Long): Host? = hostDao.getById(hostId)?.let { sanitizeHost(it) }

    /**
     * Get all SSH hosts that can be used as jump hosts.
     *
     * @return List of SSH hosts
     */
    suspend fun getSshHosts(): List<Host> = sanitizeHosts(hostDao.getSshHosts()).filter { it.protocol == "ssh" }

    /**
     * Observe all SSH hosts (for jump host selection UI).
     *
     * @return Flow of SSH hosts
     */
    fun observeSshHosts(): Flow<List<Host>> = hostDao.observeSshHosts()
        .map { hosts -> sanitizeHosts(hosts).filter { it.protocol == "ssh" } }

    /**
     * Get the count of hosts using a specific public key.
     */
    suspend fun getHostsUsingPubkey(pubkeyId: Long): Int = hostDao.getHostsUsingPubkey(pubkeyId)

    /**
     * Find a host by its unique ID (blocking version for Java interop).
     *
     * @param hostId The host ID
     * @return The host, or null if not found
     */
    fun findHostByIdBlocking(hostId: Long): Host? = runBlocking {
        findHostById(hostId)
    }

    /**
     * Save a host (insert or update).
     *
     * @param host The host to save
     * @return The saved host with updated ID
     */
    suspend fun saveHost(host: Host): Host {
        val sanitizedHost = sanitizeHost(host)
        return database.withTransaction {
            val savedHost = if (sanitizedHost.id <= 0L) {
                // New or temporary host - insert (assigns new positive ID)
                val newId = hostDao.insert(sanitizedHost)
                sanitizedHost.copy(id = newId)
            } else {
                // Existing host - update
                hostDao.update(sanitizedHost)
                sanitizedHost
            }

            if (savedHost.protocol != "ssh" && savedHost.id > 0L) {
                // 変更理由: 非SSH化したホストへSSH専用データが残ると、backup/import/接続時に不整合になる。
                portForwardDao.deleteByHost(savedHost.id)
                knownHostDao.deleteByHostId(savedHost.id)
                clearJumpHostReferences(savedHost.id)
            }
            savedHost
        }
    }

    /**
     * Delete a host.
     *
     * @param host The host to delete
     */
    suspend fun deleteHost(host: Host) {
        hostDao.delete(host)
        // Also clean up any saved password for this host
        securePasswordStorage.deletePassword(host.id)
    }

    /**
     * Update the last connected time for a host.
     *
     * @param host The host to update
     */
    suspend fun touchHost(host: Host) {
        if (host.id <= 0L) {
            // Skip for temporary hosts (negative IDs)
            return
        }
        val updatedHost = host.copy(lastConnect = System.currentTimeMillis())
        hostDao.update(updatedHost)
    }

    // ============================================================================
    // Port Forward Operations
    // ============================================================================

    /**
     * Observe port forwards for a host reactively.
     *
     * @param hostId The host ID
     * @return Flow of port forwards that updates automatically
     */
    fun observePortForwardsForHost(hostId: Long): Flow<List<PortForward>> = portForwardDao.observeByHost(hostId)

    /**
     * Get all port forwards for a host.
     *
     * @param hostId The host ID
     * @return List of port forwards
     */
    suspend fun getPortForwardsForHost(hostId: Long): List<PortForward> {
        val host = hostDao.getById(hostId) ?: return emptyList()
        // 変更理由: 旧DB/import由来の壊れた転送設定を接続時に成功扱いしない。
        return portForwardDao.getByHost(hostId).mapNotNull { CoreDataSanitizer.sanitizePortForward(it, host) }
    }

    /**
     * Save a port forward (insert or update).
     *
     * @param portForward The port forward to save
     * @return The saved port forward with updated ID
     */
    suspend fun savePortForward(portForward: PortForward): PortForward {
        val normalized = normalizePortForward(portForward)
        return if (normalized.id == 0L) {
            // New port forward - insert
            val newId = portForwardDao.insert(normalized)
            normalized.copy(id = newId)
        } else {
            // Existing port forward - update
            portForwardDao.update(normalized)
            normalized
        }
    }

    /**
     * Delete a port forward.
     *
     * @param portForward The port forward to delete
     */
    suspend fun deletePortForward(portForward: PortForward) {
        portForwardDao.delete(portForward)
    }

    // ============================================================================
    // Known Host Operations
    // ============================================================================

    suspend fun getKnownHostsForHost(hostId: Long): List<KnownHost> {
        val host = hostDao.getById(hostId) ?: return emptyList()
        // 変更理由: 壊れたknown_hostsは新規承認へ進めず、接続側には有効な鍵だけ渡す。
        return knownHostDao.getByHostId(hostId)
            .mapNotNull { CoreDataSanitizer.sanitizeKnownHost(it, host) }
    }

    /**
     * Get the list of host key algorithms known for a specific host.
     *
     * @param hostId The host ID
     * @return List of algorithm names
     */
    suspend fun getHostKeyAlgorithmsForHost(hostId: Long): List<String> {
        val knownHosts = getKnownHostsForHost(hostId)
        return knownHosts.map { it.hostKeyAlgo }.distinct()
    }

    suspend fun getHostKeyAlgorithmsForEndpoint(hostId: Long, hostname: String, port: Int): List<String> {
        val knownHosts = getKnownHostsForHost(hostId)
        return knownHosts
            .filter { it.hostname == hostname && it.port == port }
            .map { it.hostKeyAlgo }
            .distinct()
    }

    /**
     * Save a known host key to the database.
     *
     * @param host The Host entity to associate with this known host key
     * @param hostname The hostname
     * @param port The port
     * @param serverHostKeyAlgorithm The key algorithm (e.g., "ssh-rsa")
     * @param serverHostKey The public key bytes
     */
    suspend fun saveKnownHost(
        host: Host,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) {
        require(host.protocol == "ssh") { "Known hosts are only supported for SSH hosts" }
        if (host.id <= 0L) {
            // 変更理由: URI直指定などの未保存ホストはFK先が無いため、known_hostsへ永続化しない。
            return
        }
        val knownHost = CoreDataSanitizer.sanitizeKnownHost(
            KnownHost(
                hostId = host.id,
                hostname = hostname,
                port = port,
                hostKeyAlgo = serverHostKeyAlgorithm,
                hostKey = serverHostKey
            ),
            host
        ) ?: throw IllegalArgumentException("Invalid known host entry for ${host.nickname}")

        // Check if this exact key already exists for this endpoint.
        val existing = knownHostDao.getByHostEndpointAlgoAndKey(
            knownHost.hostId,
            knownHost.hostname,
            knownHost.port,
            knownHost.hostKeyAlgo,
            knownHost.hostKey
        )
        if (existing == null) {
            // Insert new key - this allows multiple keys per algorithm for key rotation
            knownHostDao.insert(knownHost)
        }
    }

    /**
     * Replace keys for the same host endpoint and algorithm, then save the accepted key.
     */
    suspend fun replaceKnownHostForEndpoint(
        host: Host,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) {
        require(host.protocol == "ssh") { "Known hosts are only supported for SSH hosts" }
        if (host.id <= 0L) {
            // 変更理由: 一時ホストはDB上の親行が無く、ホスト鍵置換の永続化対象にできない。
            return
        }
        val knownHost = CoreDataSanitizer.sanitizeKnownHost(
            KnownHost(
                hostId = host.id,
                hostname = hostname,
                port = port,
                hostKeyAlgo = serverHostKeyAlgorithm,
                hostKey = serverHostKey
            ),
            host
        ) ?: throw IllegalArgumentException("Invalid known host entry for ${host.nickname}")

        database.withTransaction {
            // 変更理由: ホスト鍵変更承認時に旧鍵を残すと、次回以降も変更警告が残る。
            knownHostDao.deleteByHostEndpointAndAlgorithm(
                knownHost.hostId,
                knownHost.hostname,
                knownHost.port,
                knownHost.hostKeyAlgo
            )
            knownHostDao.insert(knownHost)
        }
    }

    /**
     * Remove a known host key from the database.
     *
     * @param hostId The host ID
     * @param serverHostKeyAlgorithm The key algorithm
     * @param serverHostKey The public key bytes
     */
    suspend fun removeKnownHost(
        hostId: Long,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) {
        // Find the exact endpoint key to remove.
        val knownHost = knownHostDao.getByHostEndpointAlgoAndKey(
            hostId,
            hostname,
            port,
            serverHostKeyAlgorithm,
            serverHostKey
        )
        if (knownHost != null) {
            knownHostDao.delete(knownHost)
        }
    }

    /**
     * Legacy removal entrypoint kept for older callers that do not provide endpoint data.
     */
    suspend fun removeKnownHost(
        hostId: Long,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) {
        val knownHost = knownHostDao.getByHostIdAlgoAndKey(
            hostId,
            serverHostKeyAlgorithm,
            serverHostKey
        )
        if (knownHost != null) {
            knownHostDao.delete(knownHost)
        }
    }

    /**
     * Delete all known host keys for a specific host configuration.
     * Use this when the user wants to forget all stored host keys and
     * re-verify on next connection.
     *
     * @param hostId The host ID
     */
    suspend fun deleteKnownHostsForHost(hostId: Long) {
        knownHostDao.deleteByHostId(hostId)
    }

    // ============================================================================
    // Export/Import Operations
    // ============================================================================

    /**
     * Export all hosts and their port forwards to JSON string.
     * Uses schema-driven serialization that automatically adapts to database schema changes.
     *
     * @param pretty If true, format JSON with indentation
     * @return Pair of JSON string and export counts (hosts and profiles)
     */
    suspend fun exportHostsToJson(pretty: Boolean = true): Pair<String, ExportCounts> = HostConfigJson.exportToJson(context, database, pretty)

    /**
     * Import hosts from JSON string.
     * Uses schema-driven deserialization that automatically handles:
     * - Field mapping based on database schema
     * - Foreign key ID remapping
     * - Conflict resolution via unique constraints
     *
     * @param jsonString The JSON string containing host configurations
     * @return Import counts for hosts and profiles
     * @throws org.json.JSONException if JSON is invalid
     * @throws IllegalArgumentException if schema version is incompatible
     */
    suspend fun importHostsFromJson(jsonString: String): ImportCounts = HostConfigJson.importFromJson(context, database, jsonString)

    // ============================================================================
    // Blocking Methods for Java Interop
    // ============================================================================

    /**
     * Save a known host key (blocking version for Java interop).
     */
    fun saveKnownHostBlocking(
        host: Host,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) = runBlocking {
        saveKnownHost(host, hostname, port, serverHostKeyAlgorithm, serverHostKey)
    }

    /**
     * Replace a known host key (blocking version for Java interop).
     */
    fun replaceKnownHostForEndpointBlocking(
        host: Host,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) = runBlocking {
        replaceKnownHostForEndpoint(host, hostname, port, serverHostKeyAlgorithm, serverHostKey)
    }

    /**
     * Get known hosts for a specific host (blocking version for Java interop).
     */
    fun getKnownHostsForHostBlocking(hostId: Long): List<KnownHost> = runBlocking {
        getKnownHostsForHost(hostId)
    }

    /**
     * Get host key algorithms for a host (blocking version for Java interop).
     */
    fun getHostKeyAlgorithmsForHostBlocking(hostId: Long): List<String> = runBlocking {
        getHostKeyAlgorithmsForHost(hostId)
    }

    fun getHostKeyAlgorithmsForEndpointBlocking(hostId: Long, hostname: String, port: Int): List<String> = runBlocking {
        getHostKeyAlgorithmsForEndpoint(hostId, hostname, port)
    }

    /**
     * Remove a known host key (blocking version for Java interop).
     */
    fun removeKnownHostBlocking(
        hostId: Long,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) = runBlocking {
        removeKnownHost(hostId, hostname, port, serverHostKeyAlgorithm, serverHostKey)
    }

    /**
     * Remove a known host key (blocking legacy version for Java interop).
     */
    fun removeKnownHostBlocking(
        hostId: Long,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) = runBlocking {
        removeKnownHost(hostId, serverHostKeyAlgorithm, serverHostKey)
    }

    /**
     * Find a host by selection criteria (blocking version for Java interop).
     *
     * This method attempts to find a host matching the given criteria.
     * It tries to match by nickname first (most specific), then falls back
     * to matching by protocol, username, hostname, and port.
     *
     * @param selection Map of field names to values (e.g., "nickname", "protocol", "hostname", etc.)
     * @return The matching host, or null if not found
     */
    suspend fun findHost(selection: Map<String, String>): Host? {
        // Try to find by nickname first (most specific)
        val nickname = selection["nickname"]
        if (nickname != null) {
            val allHosts = getHosts()
            allHosts.find { it.nickname == nickname }?.let { return it }
        }

        // Fall back to finding by protocol, username, hostname, port
        val protocol = selection["protocol"]
        val hostname = selection["hostname"]
        val username = selection["username"]
        val portStr = selection["port"]
        val port = portStr?.toIntOrNull()

        if (protocol != null && hostname != null) {
            val allHosts = getHosts()
            allHosts.find { host ->
                host.protocol == protocol &&
                    host.hostname == hostname &&
                    (username == null || host.username == username) &&
                    (port == null || host.port == port)
            }?.let { return it }
        }

        return null
    }

    private suspend fun sanitizeHost(host: Host): Host = sanitizeHosts(listOf(host)).first()

    private suspend fun sanitizeHosts(hosts: List<Host>): List<Host> {
        val profileIds = database.profileDao().getAll().map { it.id }.toSet()
        val pubkeyIds = database.pubkeyDao().getAll().map { it.id }.toSet()
        val hostsById = hostDao.getAll().associateBy { it.id }

        // 変更理由: migrationだけでなく保存境界でも参照不整合をDBへ入れない。
        return hosts.map { host ->
            CoreDataSanitizer.sanitizeHost(
                host = host,
                profileExists = { it in profileIds },
                pubkeyExists = { it in pubkeyIds },
                jumpHostById = { hostsById[it] }
            )
        }
    }

    private suspend fun clearJumpHostReferences(jumpHostId: Long) {
        // 変更理由: 保存拒否より参照側だけを直す方が、既存ホスト編集のUXを壊さず不正なProxyJumpを残さない。
        hostDao.getAll()
            .filter { it.jumpHostId == jumpHostId }
            .forEach { hostDao.update(it.copy(jumpHostId = null)) }
    }

    private suspend fun normalizePortForward(portForward: PortForward): PortForward {
        val host = hostDao.getById(portForward.hostId)
            ?: throw IllegalArgumentException("Port forward host does not exist: ${portForward.hostId}")

        // 変更理由: Repository境界で接続処理が扱える値だけを永続化し、dynamic転送の宛先は実行時に使わせない。
        return CoreDataSanitizer.sanitizePortForward(portForward, host)
            ?: throw IllegalArgumentException("Invalid port forward for host ${host.nickname}")
    }
}
