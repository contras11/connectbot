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

import java.nio.charset.Charset
import io.shellpilot.app.data.entity.ColorPalette
import io.shellpilot.app.data.entity.ColorScheme
import io.shellpilot.app.data.entity.Host
import io.shellpilot.app.data.entity.KeyStorageType
import io.shellpilot.app.data.entity.KnownHost
import io.shellpilot.app.data.entity.PortForward
import io.shellpilot.app.data.entity.Profile
import io.shellpilot.app.data.entity.Pubkey
import io.shellpilot.app.util.HostConstants

/**
 * DB migration、import、backup、Repository境界で共有するコアデータ正規化。
 *
 * 変更理由: 同じ不変条件を複数経路で個別実装すると、JSON/backup/legacyだけ
 * 壊れた値を通してしまうため、Entity単位の安全値をここへ集約する。
 */
object CoreDataSanitizer {
    const val DEFAULT_PROFILE_ID = 1L
    const val DEFAULT_PROFILE_NAME = "Default"
    private const val DEFAULT_FONT_SIZE = 10
    private const val DEFAULT_ENCODING = "UTF-8"
    private const val DEFAULT_EMULATION = "xterm-256color"
    private const val DEFAULT_SCROLLBACK_LINES = 140

    private val portRange = 1..65535
    private val profileSizeRange = 6..96
    private val forcedTerminalSizeRange = 1..400
    private val validProtocols = setOf("ssh", "telnet", "local")
    private val validAuthAgentModes = setOf(
        HostConstants.AUTHAGENT_NO,
        HostConstants.AUTHAGENT_CONFIRM,
        HostConstants.AUTHAGENT_YES
    )
    private val validIpVersions = setOf("IPV4_AND_IPV6", "IPV4_ONLY", "IPV6_ONLY")
    private val validPortForwardTypes = setOf(
        HostConstants.PORTFORWARD_LOCAL,
        HostConstants.PORTFORWARD_REMOTE,
        HostConstants.PORTFORWARD_DYNAMIC5
    )

    fun sanitizeProfile(
        profile: Profile,
        validCustomColorSchemeIds: Set<Long> = emptySet(),
        forceDefaultIdentity: Boolean = profile.id == DEFAULT_PROFILE_ID
    ): Profile {
        val name = if (forceDefaultIdentity) {
            DEFAULT_PROFILE_NAME
        } else {
            profile.name.trim().ifEmpty { "Profile" }
        }
        val colorSchemeId = if (profile.colorSchemeId > 0L && profile.colorSchemeId !in validCustomColorSchemeIds) {
            -1L
        } else {
            profile.colorSchemeId
        }
        val encoding = profile.encoding.trim()
            .takeIf { it.isNotEmpty() && runCatching { Charset.isSupported(it) }.getOrDefault(false) }
            ?: DEFAULT_ENCODING

        return profile.copy(
            name = name,
            colorSchemeId = colorSchemeId,
            fontSize = profile.fontSize.coerceIn(profileSizeRange),
            delKey = profile.delKey.takeIf { it == "del" || it == "backspace" } ?: "del",
            encoding = encoding,
            emulation = profile.emulation.trim().ifEmpty { DEFAULT_EMULATION },
            forceSizeRows = profile.forceSizeRows?.takeIf { it in forcedTerminalSizeRange },
            forceSizeColumns = profile.forceSizeColumns?.takeIf { it in forcedTerminalSizeRange }
        )
    }

    fun sanitizeHost(
        host: Host,
        profileExists: (Long) -> Boolean,
        pubkeyExists: (Long) -> Boolean,
        jumpHostById: (Long) -> Host?
    ): Host {
        val protocol = host.protocol.takeIf { it in validProtocols } ?: "ssh"
        val isSsh = protocol == "ssh"
        val port = when {
            protocol == "local" -> 0
            host.port in portRange -> host.port
            protocol == "telnet" -> 23
            else -> 22
        }
        val pubkeyId = when {
            !isSsh -> HostConstants.PUBKEYID_NEVER
            host.pubkeyId <= 0L -> host.pubkeyId
            pubkeyExists(host.pubkeyId) -> host.pubkeyId
            else -> HostConstants.PUBKEYID_NEVER
        }
        val jumpHostId = if (isSsh) {
            sanitizeJumpHostId(host, jumpHostById)
        } else {
            null
        }

        return host.copy(
            protocol = protocol,
            port = port,
            useAuthAgent = host.useAuthAgent.takeIf { it in validAuthAgentModes } ?: HostConstants.AUTHAGENT_NO,
            pubkeyId = pubkeyId,
            jumpHostId = jumpHostId,
            scrollbackLines = host.scrollbackLines.takeIf { it in 0..100000 } ?: DEFAULT_SCROLLBACK_LINES,
            profileId = host.profileId.takeIf { it > 0L && profileExists(it) } ?: DEFAULT_PROFILE_ID,
            ipVersion = host.ipVersion.takeIf { it in validIpVersions } ?: "IPV4_AND_IPV6"
        )
    }

    fun sanitizePubkey(pubkey: Pubkey): Pubkey {
        val storageType = pubkey.storageType
        val nickname = pubkey.nickname.trim().ifEmpty { "SSH key" }
        return when (storageType) {
            KeyStorageType.ANDROID_KEYSTORE -> pubkey.copy(
                nickname = nickname,
                privateKey = null,
                startup = false,
                allowBackup = false
            )

            KeyStorageType.EXPORTABLE -> pubkey.copy(
                nickname = nickname,
                startup = pubkey.startup && pubkey.privateKey != null,
                allowBackup = pubkey.allowBackup && pubkey.privateKey != null,
                keystoreAlias = null
            )
        }
    }

    fun sanitizePortForward(
        portForward: PortForward,
        parentHost: Host?
    ): PortForward? {
        if (parentHost?.protocol != "ssh" || portForward.sourcePort !in portRange) return null
        val type = when (portForward.type) {
            HostConstants.PORTFORWARD_DYNAMIC4 -> HostConstants.PORTFORWARD_DYNAMIC5
            in validPortForwardTypes -> portForward.type
            else -> return null
        }
        val destAddr = portForward.destAddr?.trim().orEmpty()
        if (type == HostConstants.PORTFORWARD_LOCAL || type == HostConstants.PORTFORWARD_REMOTE) {
            if (destAddr.isEmpty() || portForward.destPort !in portRange) return null
        }
        val nickname = portForward.nickname.trim().ifEmpty {
            when (type) {
                HostConstants.PORTFORWARD_LOCAL -> "Local ${portForward.sourcePort}"
                HostConstants.PORTFORWARD_REMOTE -> "Remote ${portForward.sourcePort}"
                else -> "Dynamic ${portForward.sourcePort}"
            }
        }
        return portForward.copy(
            nickname = nickname,
            type = type,
            destAddr = if (type == HostConstants.PORTFORWARD_DYNAMIC5) null else destAddr,
            destPort = if (type == HostConstants.PORTFORWARD_DYNAMIC5) 0 else portForward.destPort
        )
    }

    fun sanitizeKnownHost(knownHost: KnownHost, parentHost: Host?): KnownHost? {
        if (parentHost?.protocol != "ssh") return null
        val hostname = knownHost.hostname.trim()
        val algo = knownHost.hostKeyAlgo.trim()
        if (hostname.isEmpty() || algo.isEmpty() || knownHost.port !in portRange || knownHost.hostKey.isEmpty()) {
            return null
        }
        return knownHost.copy(
            hostId = parentHost.id,
            hostname = hostname,
            port = knownHost.port,
            hostKeyAlgo = algo
        )
    }

    fun sanitizeColorScheme(scheme: ColorScheme): ColorScheme? {
        if (scheme.id <= 0L || scheme.name.isBlank()) return null
        return scheme.copy(
            name = scheme.name.trim(),
            description = scheme.description.trim(),
            isBuiltIn = false,
            foreground = sanitizeColorIndex(scheme.foreground, HostConstants.DEFAULT_FG_COLOR),
            background = sanitizeColorIndex(scheme.background, HostConstants.DEFAULT_BG_COLOR)
        )
    }

    fun sanitizeColorPalette(palette: ColorPalette, validCustomColorSchemeIds: Set<Long>): ColorPalette? {
        if (palette.schemeId !in validCustomColorSchemeIds || palette.colorIndex !in 0..15) return null
        return palette
    }

    fun sanitizeColorIndex(index: Int, fallback: Int): Int = index.takeIf { it in 0..15 } ?: fallback

    private fun sanitizeJumpHostId(host: Host, jumpHostById: (Long) -> Host?): Long? {
        val jumpHostId = host.jumpHostId ?: return null
        if (jumpHostId <= 0L || jumpHostId == host.id) return null
        val firstJumpHost = jumpHostById(jumpHostId) ?: return null
        if (firstJumpHost.protocol != "ssh") return null
        return if (wouldCreateJumpCycle(host, firstJumpHost, jumpHostById)) null else jumpHostId
    }

    private fun wouldCreateJumpCycle(
        host: Host,
        firstJumpHost: Host,
        jumpHostById: (Long) -> Host?
    ): Boolean {
        val visited = mutableSetOf<Long>()
        if (host.id > 0L) visited += host.id
        var nextHost: Host? = firstJumpHost
        while (nextHost != null) {
            if (nextHost.id > 0L && !visited.add(nextHost.id)) return true
            val nextJumpHostId = nextHost.jumpHostId ?: return false
            nextHost = jumpHostById(nextJumpHostId) ?: return true
            if (nextHost.protocol != "ssh") return true
        }
        return false
    }
}
