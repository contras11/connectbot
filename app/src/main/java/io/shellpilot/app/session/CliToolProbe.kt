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

package io.shellpilot.app.session

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import io.shellpilot.app.service.TerminalBridge
import org.connectbot.terminal.TerminalEmulator
import timber.log.Timber

/**
 * SSH接続先でCLIツールの存在を動的に検出するプローブ。
 *
 * 変更理由: Claude Code / Codex 等のCLIツールがリモートホストに
 * インストールされているかを自動検出し、対応するスラッシュコマンドを
 * ショートカットバーに動的に追加するため新設。
 *
 * 検出方式:
 * 1. プローブコマンド (`command -v <binary> && echo __CB_<BINARY>_OK__`) を送信
 * 2. 一定時間後にTerminalEmulatorの画面バッファをリフレクション経由で読み取り
 * 3. マーカー文字列の有無で検出結果を判定
 *
 * 制約:
 * - termlibのTerminalEmulatorImplはinternal classのためリフレクションで読み取り
 * - プローブコマンドはシェルに入力されるため、ターミナル画面に表示される
 * - ネットワーク遅延やシェル応答時間により検出漏れの可能性がある
 * - 既存SSHコア (TerminalBridge) への変更は行わない
 */
object CliToolProbe {

    /** プローブマーカーの接頭辞/接尾辞 */
    private const val MARKER_PREFIX = "__CB_PROBE_"
    private const val MARKER_SUFFIX = "_OK__"

    /** プローブコマンド送信後の待機時間 (ミリ秒) */
    private const val PROBE_DELAY_MS = 2000L

    /**
     * 検出結果。
     * @param toolId CliCommandRegistry内のカテゴリID
     * @param binary 検出対象バイナリ名
     * @param found 検出されたかどうか
     */
    data class ProbeResult(
        val toolId: String,
        val binary: String,
        val found: Boolean
    )

    /**
     * 指定されたバイナリ群の存在を検出する。
     *
     * TerminalBridge経由でプローブコマンドを送信し、
     * 画面バッファのスキャンで結果を判定する。
     *
     * @param bridge 接続中のTerminalBridge
     * @return 各ツールの検出結果
     */
    suspend fun probe(bridge: TerminalBridge): List<ProbeResult> {
        val categories = CliCommandRegistry.categories.filter { it.probeBinary != null }
        if (categories.isEmpty()) return emptyList()

        // プローブコマンドを構築して送信
        // 各バイナリに対して: command -v <binary> >/dev/null 2>&1 && echo __CB_PROBE_<BINARY>_OK__
        val probeScript = categories.joinToString("; ") { category ->
            val binary = category.probeBinary!!
            val marker = buildMarker(binary)
            "command -v $binary >/dev/null 2>&1 && echo $marker"
        }

        bridge.injectString("$probeScript\n")

        // レスポンス待機
        delay(PROBE_DELAY_MS)

        // 画面バッファをスキャン (リフレクション経由)
        val screenText = readScreenText(bridge.terminalEmulator)

        return categories.map { category ->
            val binary = category.probeBinary!!
            val marker = buildMarker(binary)
            val found = screenText.contains(marker)
            Timber.d("CliToolProbe: $binary → ${if (found) "検出" else "未検出"}")
            ProbeResult(
                toolId = category.id,
                binary = binary,
                found = found
            )
        }
    }

    /**
     * TerminalEmulatorから現在の画面テキストをリフレクション経由で読み取る。
     *
     * termlibの TerminalEmulatorImpl.snapshot (StateFlow<TerminalSnapshot>) は
     * internal visibility のため、リフレクションでアクセスする。
     * TerminalSnapshot.lines / scrollback の各 TerminalLine.getText() でテキスト取得。
     *
     * 変更理由: 既存SSHコア (TerminalBridge) を改変せずに画面バッファを
     * 読み取るためリフレクションを使用。
     */
    @Suppress("UNCHECKED_CAST")
    private fun readScreenText(emulator: TerminalEmulator): String {
        return try {
            // TerminalEmulatorImpl の snapshot$lib フィールドを取得
            val snapshotFlowField = emulator.javaClass.methods
                .firstOrNull { it.name == "getSnapshot\$lib" }

            if (snapshotFlowField == null) {
                Timber.w("CliToolProbe: snapshot フィールドが見つかりません")
                return ""
            }

            val snapshotFlow = snapshotFlowField.invoke(emulator) as? StateFlow<*>
                ?: return ""
            val snapshot = snapshotFlow.value ?: return ""

            // TerminalSnapshot.getLines() と getScrollback() を取得
            val sb = StringBuilder()
            val snapshotClass = snapshot.javaClass

            // scrollback行を取得
            val scrollbackMethod = snapshotClass.getMethod("getScrollback")
            val scrollback = scrollbackMethod.invoke(snapshot) as? List<*>
            scrollback?.forEach { line ->
                val text = line?.javaClass?.getMethod("getText")?.invoke(line) as? String
                if (text != null) sb.appendLine(text)
            }

            // 画面表示行を取得
            val linesMethod = snapshotClass.getMethod("getLines")
            val lines = linesMethod.invoke(snapshot) as? List<*>
            lines?.forEach { line ->
                val text = line?.javaClass?.getMethod("getText")?.invoke(line) as? String
                if (text != null) sb.appendLine(text)
            }

            sb.toString()
        } catch (e: Exception) {
            Timber.e(e, "CliToolProbe: 画面バッファの読み取りに失敗")
            ""
        }
    }

    /** バイナリ名からプローブマーカー文字列を生成 */
    private fun buildMarker(binary: String): String {
        return "$MARKER_PREFIX${binary.uppercase()}$MARKER_SUFFIX"
    }
}
