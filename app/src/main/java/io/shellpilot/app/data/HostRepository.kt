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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import io.shellpilot.app.data.dao.HostDao
import io.shellpilot.app.data.dao.KnownHostDao
import io.shellpilot.app.data.dao.PortForwardDao
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.KnownHost
import io.shellpilot.app.data.entity.PortForward
import io.shellpilot.app.util.HostConstants
import io.shellpilot.app.util.SecurePasswordStorage
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

    /**
     * Observe all hosts sorted by color reactively.
     *
     * @return Flow of host list sorted by color
     */
    fun observeHostsSortedByColor(): Flow<List<Host>> = hostDao.observeAllSortedByColor()

    /**
     * Observe a specific host reactively.
     *
     * @param hostId The host ID
     * @return Flow of host that updates automatically
     */
    fun observeHost(hostId: Long): Flow<Host?> = hostDao.observeById(hostId)

    /**
     * Get all hosts.
     *
     * @param sortedByColor If true, hosts will be grouped by color
     * @return List of all hosts
     */
    suspend fun getHosts(sortedByColor: Boolean = false): List<Host> = if (sortedByColor) {
        // For now, sort by color in memory
        // TODO: Add a proper DAO query for this
        hostDao.getAll().sortedBy { it.color }
    } else {
        hostDao.getAll()
    }

    /**
     * Find a host by its unique ID.
     *
     * @param hostId The host ID
     * @return The host, or null if not found
     */
    suspend fun findHostById(hostId: Long): Host? = hostDao.getById(hostId)

    /**
     * Get all SSH hosts that can be used as jump hosts.
     *
     * @return List of SSH hosts
     */
    suspend fun getSshHosts(): List<Host> = hostDao.getSshHosts()

    /**
     * Observe all SSH hosts (for jump host selection UI).
     *
     * @return Flow of SSH hosts
     */
    fun observeSshHosts(): Flow<List<Host>> = hostDao.observeSshHosts()

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
    suspend fun getPortForwardsForHost(hostId: Long): List<PortForward> = portForwardDao.getByHost(hostId)

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

    suspend fun getKnownHostsForHost(hostId: Long): List<KnownHost> = knownHostDao.getByHostId(hostId)

    /**
     * Get the list of host key algorithms known for a specific host.
     *
     * @param hostId The host ID
     * @return List of algorithm names
     */
    suspend fun getHostKeyAlgorithmsForHost(hostId: Long): List<String> {
        val knownHosts = knownHostDao.getByHostId(hostId)
        return knownHosts.map { it.hostKeyAlgo }.distinct()
    }

    suspend fun getHostKeyAlgorithmsForEndpoint(hostId: Long, hostname: String, port: Int): List<String> {
        val knownHosts = knownHostDao.getByHostId(hostId)
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
        // Check if this exact key already exists for this endpoint.
        val existing = knownHostDao.getByHostEndpointAlgoAndKey(
            host.id,
            hostname,
            port,
            serverHostKeyAlgorithm,
            serverHostKey
        )
        if (existing == null) {
            // Insert new key - this allows multiple keys per algorithm for key rotation
            val knownHost = KnownHost(
                hostId = host.id,
                hostname = hostname,
                port = port,
                hostKeyAlgo = serverHostKeyAlgorithm,
                hostKey = serverHostKey
            )
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
        database.withTransaction {
            // 変更理由: ホスト鍵変更承認時に旧鍵を残すと、次回以降も変更警告が残る。
            knownHostDao.deleteByHostEndpointAndAlgorithm(host.id, hostname, port, serverHostKeyAlgorithm)
            knownHostDao.insert(
                KnownHost(
                    hostId = host.id,
                    hostname = hostname,
                    port = port,
                    hostKeyAlgo = serverHostKeyAlgorithm,
                    hostKey = serverHostKey
                )
            )
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
            val allHosts = hostDao.getAll()
            allHosts.find { it.nickname == nickname }?.let { return it }
        }

        // Fall back to finding by protocol, username, hostname, port
        val protocol = selection["protocol"]
        val hostname = selection["hostname"]
        val username = selection["username"]
        val portStr = selection["port"]
        val port = portStr?.toIntOrNull()

        if (protocol != null && hostname != null) {
            val allHosts = hostDao.getAll()
            allHosts.find { host ->
                host.protocol == protocol &&
                    host.hostname == hostname &&
                    (username == null || host.username == username) &&
                    (port == null || host.port == port)
            }?.let { return it }
        }

        return null
    }

    private suspend fun sanitizeHost(host: Host): Host {
        val profileId = host.profileId.takeIf { profileId ->
            profileId > 0L && database.profileDao().getById(profileId) != null
        } ?: DEFAULT_PROFILE_ID

        val pubkeyId = when {
            host.pubkeyId <= 0L -> host.pubkeyId
            database.pubkeyDao().getById(host.pubkeyId) != null -> host.pubkeyId
            else -> HostConstants.PUBKEYID_NEVER
        }

        // 変更理由: migrationだけでなく保存境界でも参照不整合をDBへ入れない。
        return host.copy(
            profileId = profileId,
            pubkeyId = pubkeyId,
            jumpHostId = sanitizeJumpHostId(host)
        )
    }

    private suspend fun clearJumpHostReferences(jumpHostId: Long) {
        // 変更理由: 保存拒否より参照側だけを直す方が、既存ホスト編集のUXを壊さず不正なProxyJumpを残さない。
        hostDao.getAll()
            .filter { it.jumpHostId == jumpHostId }
            .forEach { hostDao.update(it.copy(jumpHostId = null)) }
    }

    private suspend fun sanitizeJumpHostId(host: Host): Long? {
        val jumpHostId = host.jumpHostId ?: return null
        if (jumpHostId <= 0L || jumpHostId == host.id) return null

        val jumpHost = hostDao.getById(jumpHostId) ?: return null
        if (jumpHost.protocol != "ssh") return null

        return if (wouldCreateJumpCycle(host, jumpHost)) null else jumpHostId
    }

    private suspend fun wouldCreateJumpCycle(host: Host, firstJumpHost: Host): Boolean {
        val visited = mutableSetOf<Long>()
        if (host.id > 0L) {
            visited.add(host.id)
        }

        var nextHost: Host? = firstJumpHost
        while (nextHost != null) {
            if (nextHost.id > 0L && !visited.add(nextHost.id)) {
                return true
            }
            val nextJumpHostId = nextHost.jumpHostId ?: return false
            nextHost = hostDao.getById(nextJumpHostId) ?: return true
            if (nextHost.protocol != "ssh") {
                return true
            }
        }
        return false
    }

    private suspend fun normalizePortForward(portForward: PortForward): PortForward {
        val normalizedType = when (portForward.type) {
            HostConstants.PORTFORWARD_DYNAMIC4 -> HostConstants.PORTFORWARD_DYNAMIC5
            HostConstants.PORTFORWARD_LOCAL,
            HostConstants.PORTFORWARD_REMOTE,
            HostConstants.PORTFORWARD_DYNAMIC5 -> portForward.type
            else -> throw IllegalArgumentException("Unsupported port forward type: ${portForward.type}")
        }

        val host = hostDao.getById(portForward.hostId)
            ?: throw IllegalArgumentException("Port forward host does not exist: ${portForward.hostId}")
        require(host.protocol == "ssh") { "Port forwarding is only supported for SSH hosts" }
        require(portForward.sourcePort in PORT_RANGE) { "Source port must be 1..65535" }

        val normalizedDestAddr = portForward.destAddr?.trim().orEmpty()
        if (normalizedType == HostConstants.PORTFORWARD_LOCAL || normalizedType == HostConstants.PORTFORWARD_REMOTE) {
            require(normalizedDestAddr.isNotEmpty()) { "Destination address is required" }
            require(portForward.destPort in PORT_RANGE) { "Destination port must be 1..65535" }
        }

        val nickname = portForward.nickname.trim().ifEmpty {
            when (normalizedType) {
                HostConstants.PORTFORWARD_LOCAL -> "Local ${portForward.sourcePort}"
                HostConstants.PORTFORWARD_REMOTE -> "Remote ${portForward.sourcePort}"
                else -> "Dynamic ${portForward.sourcePort}"
            }
        }

        // 変更理由: Repository境界で接続処理が扱える値だけを永続化し、dynamic転送の宛先は実行時に使わせない。
        return portForward.copy(
            nickname = nickname,
            type = normalizedType,
            destAddr = if (normalizedType == HostConstants.PORTFORWARD_DYNAMIC5) null else normalizedDestAddr,
            destPort = if (normalizedType == HostConstants.PORTFORWARD_DYNAMIC5) 0 else portForward.destPort
        )
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = 1L
        val PORT_RANGE = 1..65535
    }
}
