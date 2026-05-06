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

package io.shellpilot.app.ui.components

import android.view.ViewConfiguration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.shellpilot.app.R
import io.shellpilot.app.service.ModifierLevel
import io.shellpilot.app.service.ModifierState
import io.shellpilot.app.service.TerminalBridge
import io.shellpilot.app.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey

/**
 * Height of the virtual keyboard key row in dp.
 *
 * 変更理由: Ctrl+Cなどの制御キー追加後も矢印が初期表示に収まるよう、
 * 端末専用バーでは44dpへ圧縮する。
 */
const val TERMINAL_KEYBOARD_HEIGHT_DP = 36

/**
 * Width of the virtual keyboard keys in dp.
 * 変更理由: ImageGen参照ボードの固定キー列に合わせ、省スペースな正方形ボタンに統一。
 */
private const val TERMINAL_KEYBOARD_WIDTH_DP = 34

/**
 * Size of the content (icons and text) for the virtual keyboard keys in dp.
 */
private const val TERMINAL_KEYBOARD_CONTENT_SIZE_DP = 16

/**
 * Virtual keyboard with terminal special keys (Ctrl, Esc, arrows, function keys, etc.)
 *
 * 変更理由: デザインをShortcutBarと統一。
 * - Surface背景: surface.copy(alpha=0.5f) → surfaceContainerLow (ShortcutBarと同じ)
 * - tonalElevation を除去しフラットデザインに統一
 * - キーボタン背景: surfaceContainerHigh (ShortcutBarのcontainerから視認できる程度)
 * - 上部に HorizontalDivider を追加してターミナル領域との区切りを明確化
 */
@Composable
fun TerminalKeyboard(
    bridge: TerminalBridge,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    onHideIme: () -> Unit = {},
    onShowIme: () -> Unit = {},
    onOpenTextInput: () -> Unit = {},
    onScrollInProgressChange: (Boolean) -> Unit = {},
    imeVisible: Boolean = false,
    playAnimation: Boolean = false
) {
    val keyHandler = bridge.keyHandler
    val modifierState by keyHandler.modifierState.collectAsState()

    TerminalKeyboardContent(
        modifierState = modifierState,
        onControlSequence = { sequence ->
            bridge.injectString(sequence)
            onInteraction()
        },
        onCtrlPress = {
            keyHandler.metaPress(TerminalKeyListener.OUR_CTRL_ON, true)
            onInteraction()
        },
        onEscPress = {
            keyHandler.sendEscape()
            onInteraction()
        },
        onTabPress = {
            keyHandler.sendTab()
            onInteraction()
        },
        onKeyPress = { key ->
            keyHandler.sendPressedKey(key)
            onInteraction()
        },
        onInteraction = onInteraction,
        onHideIme = onHideIme,
        onShowIme = onShowIme,
        onOpenTextInput = onOpenTextInput,
        onScrollInProgressChange = onScrollInProgressChange,
        imeVisible = imeVisible,
        playAnimation = playAnimation,
        modifier = modifier
    )
}

/**
 * Stateless UI component for the terminal keyboard.
 * Separated from [TerminalKeyboard] to enable preview without TerminalBridge dependency.
 */
@Composable
private fun TerminalKeyboardContent(
    modifierState: ModifierState,
    onControlSequence: (String) -> Unit,
    onCtrlPress: () -> Unit,
    onEscPress: () -> Unit,
    onTabPress: () -> Unit,
    onKeyPress: (Int) -> Unit,
    onInteraction: () -> Unit,
    onHideIme: () -> Unit,
    onShowIme: () -> Unit,
    onOpenTextInput: () -> Unit,
    onScrollInProgressChange: (Boolean) -> Unit,
    imeVisible: Boolean,
    playAnimation: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val currentOnScrollInProgressChange by rememberUpdatedState(onScrollInProgressChange)

    // Notify parent when scroll state changes
    LaunchedEffect(scrollState.isScrollInProgress) {
        currentOnScrollInProgressChange(scrollState.isScrollInProgress)
    }

    // Auto-scroll animation on first appearance (only if playAnimation is true)
    LaunchedEffect(playAnimation) {
        if (playAnimation) {
            delay(100)
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = tween(durationMillis = 500)
            )
            delay(300)
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = 500)
            )
        }
    }

    // 変更理由: ShortcutBarと同じsurfaceContainerLowを使用し、
    // 上段バー(TerminalKeyboard)と下段バー(ShortcutBar)のデザインを統一する。
    // tonalElevation を除去してフラットスタイルに統一。
    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onInteraction()
                        tryAwaitRelease()
                    }
                )
            },
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            // 変更理由: ShortcutBarのHorizontalDividerと対称になるよう
            // ターミナル領域との区切りを上部に追加する。
            HorizontalDivider(thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TERMINAL_KEYBOARD_HEIGHT_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 変更理由: ShortcutBarのChipと統一したspacingとpaddingを適用
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // 変更理由: ^C と矢印を先頭に固定し、Claude Code / Codexの
                    // 中断操作を追加してもカーソル移動キーが初期表示から隠れないようにする。
                    ControlSequenceButton(
                        text = "^C",
                        contentDescription = "Ctrl+Cを送信",
                        onClick = { onControlSequence("\u0003") }
                    )

                    // Arrow keys (repeatable)
                    RepeatableKeyButton(
                        icon = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.image_description_up),
                        onPress = { onKeyPress(VTermKey.UP) }
                    )

                    RepeatableKeyButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.image_description_down),
                        onPress = { onKeyPress(VTermKey.DOWN) }
                    )

                    RepeatableKeyButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.image_description_left),
                        onPress = { onKeyPress(VTermKey.LEFT) }
                    )

                    RepeatableKeyButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.image_description_right),
                        onPress = { onKeyPress(VTermKey.RIGHT) }
                    )

                    // Ctrl key (sticky modifier)
                    ModifierKeyButton(
                        text = stringResource(R.string.button_key_ctrl),
                        contentDescription = stringResource(R.string.image_description_toggle_control_character),
                        modifierLevel = modifierState.ctrlState,
                        onClick = onCtrlPress
                    )

                    // Esc key
                    KeyButton(
                        text = stringResource(R.string.button_key_esc),
                        contentDescription = stringResource(R.string.image_description_send_escape_character),
                        onClick = onEscPress
                    )

                    // Tab key
                    KeyButton(
                        text = "⇥",
                        contentDescription = stringResource(R.string.image_description_send_tab_character),
                        onClick = onTabPress
                    )

                    ControlSequenceButton(
                        text = "^D",
                        contentDescription = "Ctrl+Dを送信",
                        onClick = { onControlSequence("\u0004") }
                    )

                    ControlSequenceButton(
                        text = "^Z",
                        contentDescription = "Ctrl+Zを送信",
                        onClick = { onControlSequence("\u001A") }
                    )

                    // Home/End
                    KeyButton(
                        text = stringResource(R.string.button_key_home),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.HOME) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_end),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.END) }
                    )

                    // Page Up/Down
                    KeyButton(
                        text = stringResource(R.string.button_key_pgup),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.PAGEUP) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_pgdn),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.PAGEDOWN) }
                    )

                    // Function keys F1-F12
                    KeyButton(
                        text = stringResource(R.string.button_key_f1),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_1) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f2),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_2) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f3),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_3) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f4),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_4) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f5),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_5) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f6),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_6) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f7),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_7) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f8),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_8) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f9),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_9) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f10),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_10) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f11),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_11) }
                    )

                    KeyButton(
                        text = stringResource(R.string.button_key_f12),
                        contentDescription = null,
                        onClick = { onKeyPress(VTermKey.FUNCTION_12) }
                    )
                }

                // Text input button (always visible on right)
                // 変更理由: ShortcutBarのChipと統一した角丸デザイン
                Surface(
                    onClick = {
                        onOpenTextInput()
                        onInteraction()
                    },
                    modifier = Modifier.size(
                        width = TERMINAL_KEYBOARD_WIDTH_DP.dp,
                        height = TERMINAL_KEYBOARD_HEIGHT_DP.dp
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.terminal_keyboard_text_input_button),
                            modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Keyboard toggle button (always visible on right)
                // 変更理由: ShortcutBarのChipと統一した角丸デザイン
                Surface(
                    onClick = {
                        if (imeVisible) {
                            onHideIme()
                        } else {
                            onShowIme()
                        }
                        onInteraction()
                    },
                    modifier = Modifier.size(
                        width = TERMINAL_KEYBOARD_WIDTH_DP.dp,
                        height = TERMINAL_KEYBOARD_HEIGHT_DP.dp
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            if (imeVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                            contentDescription = stringResource(
                                if (imeVisible) {
                                    R.string.image_description_hide_keyboard
                                } else {
                                    R.string.image_description_show_keyboard
                                }
                            ),
                            modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * A button for single-press keys (Ctrl, Esc, Tab, Home, End, PgUp, PgDn, F1-F12)
 *
 * 変更理由: デザインをShortcutBarと統一。
 * - デフォルト背景: surface.copy(alpha) → surfaceContainerHigh (surfaceContainerLowバー上で視認可能)
 * - ボーダー色: outline → outlineVariant (ShortcutBarのchipと同等の軽い区切り)
 * - tint: onSurfaceVariant に統一
 */
@Composable
private fun KeyButton(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val surfaceModifier = modifier
        .size(width = TERMINAL_KEYBOARD_WIDTH_DP.dp, height = TERMINAL_KEYBOARD_HEIGHT_DP.dp)

    val content: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp)
                )
            }
        }
    }

    // 変更理由: ShortcutBarのChipと統一した角丸デザインを適用
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            color = backgroundColor,
            content = content
        )
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
            color = backgroundColor,
            content = content
        )
    }
}

@Composable
private fun ControlSequenceButton(
    text: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(36.dp)
            .height(TERMINAL_KEYBOARD_HEIGHT_DP.dp),
        shape = RoundedCornerShape(8.dp),
        // 変更理由: ^Cなどは重要だが危険色ではないため、白黒テーマに馴染む
        // primaryContainerの控えめな強調に留める。
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * A button for repeatable keys (arrow keys)
 */
@Composable
private fun RepeatableKeyButton(
    icon: ImageVector,
    contentDescription: String?,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            repeatJob?.cancel()
        }
    }

    // 変更理由: 押下状態の背景をprimaryContainerに変更 (ModifierKeyButtonと一致)
    val backgroundColor = if (isPressed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    KeyButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = null,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true

                    val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
                    repeatJob = coroutineScope.launch {
                        delay(tapTimeout)
                        if (!isPressed) return@launch
                        onPress()
                        delay(500 - tapTimeout)
                        while (isPressed) {
                            onPress()
                            delay(50)
                        }
                    }

                    val released = tryAwaitRelease()
                    isPressed = false

                    if (released && repeatJob?.isActive == true) {
                        repeatJob?.cancel()
                        onPress()
                    } else {
                        repeatJob?.cancel()
                    }
                }
            )
        },
        backgroundColor = backgroundColor
    )
}

/**
 * Modifier key button (Ctrl) with 3 states: OFF / TRANSIENT / LOCKED.
 *
 * 変更理由: OFFの背景をsurfaceContainerHighに変更して他のキーと統一。
 */
@Composable
private fun ModifierKeyButton(
    text: String,
    contentDescription: String?,
    modifierLevel: ModifierLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (modifierLevel) {
        ModifierLevel.OFF -> MaterialTheme.colorScheme.surfaceContainerHigh
        ModifierLevel.TRANSIENT -> MaterialTheme.colorScheme.primaryContainer
        ModifierLevel.LOCKED -> MaterialTheme.colorScheme.primary
    }

    val textColor = when (modifierLevel) {
        ModifierLevel.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
        ModifierLevel.TRANSIENT -> MaterialTheme.colorScheme.onPrimaryContainer
        ModifierLevel.LOCKED -> MaterialTheme.colorScheme.onPrimary
    }

    KeyButton(
        text = text,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        backgroundColor = backgroundColor,
        tint = textColor
    )
}

@Preview(name = "Terminal Keyboard - Default State", showBackground = true)
@Composable
private fun TerminalKeyboardPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF
            ),
            onControlSequence = {},
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false
        )
    }
}

@Preview(name = "Terminal Keyboard - Ctrl Pressed", showBackground = true)
@Composable
private fun TerminalKeyboardCtrlPressedPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.TRANSIENT,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF
            ),
            onControlSequence = {},
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false
        )
    }
}

@Preview(name = "Terminal Keyboard - Ctrl Locked", showBackground = true)
@Composable
private fun TerminalKeyboardCtrlLockedPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.LOCKED,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF
            ),
            onControlSequence = {},
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false
        )
    }
}

@Preview(name = "Terminal Keyboard - IME Visible", showBackground = true)
@Composable
private fun TerminalKeyboardImeVisiblePreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF
            ),
            onControlSequence = {},
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = true,
            playAnimation = false
        )
    }
}
