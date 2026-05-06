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

package io.shellpilot.app.ui.theme

import androidx.compose.ui.graphics.Color

val ShellPilotBlack = Color(0xFF0B0B0C)
val ShellPilotInk = Color(0xFF151517)
val ShellPilotPanel = Color(0xFF222225)
val ShellPilotPanelHigh = Color(0xFF2F2F33)
val ShellPilotWhite = Color(0xFFF3F4F6)
val ShellPilotPaper = Color(0xFFFFFFFF)
val ShellPilotBorder = Color(0xFFBFC3CB)
val ShellPilotMuted = Color(0xFFA1A1AA)
val ShellPilotLightCanvas = Color(0xFFEDEFF2)
val ShellPilotLightPanel = Color(0xFFF8F8F9)
val ShellPilotLightPanelHigh = Color(0xFFFFFFFF)
val ShellPilotLightLine = Color(0xFFD0D3DA)
val ShellPilotDarkLine = Color(0xFF3A3A3F)
val ShellPilotSuccess = Color(0xFF2F7D4A)
val ShellPilotWarning = Color(0xFF9A6A12)
val ShellPilotError = Color(0xFFB42318)

val md_theme_light_primary = Color(0xFF18181B)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFE5E7EB)
val md_theme_light_onPrimaryContainer = Color(0xFF18181B)
val md_theme_light_secondary = Color(0xFF3F3F46)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE9EAEE)
val md_theme_light_onSecondaryContainer = Color(0xFF27272A)
val md_theme_light_tertiary = ShellPilotWarning
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_error = ShellPilotError
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = ShellPilotLightCanvas
val md_theme_light_onBackground = Color(0xFF18181B)
val md_theme_light_surface = ShellPilotLightPanelHigh
val md_theme_light_onSurface = Color(0xFF18181B)
val md_theme_light_surfaceVariant = ShellPilotLightPanel
val md_theme_light_onSurfaceVariant = Color(0xFF444850)
val md_theme_light_outline = Color(0xFF6B707A)
val md_theme_light_outlineVariant = ShellPilotLightLine
val md_theme_light_surfaceDim = Color(0xFFE2E4E9)
val md_theme_light_surfaceBright = ShellPilotLightPanelHigh
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_theme_light_surfaceContainerLow = Color(0xFFF6F7F8)
val md_theme_light_surfaceContainer = Color(0xFFF0F1F4)
val md_theme_light_surfaceContainerHigh = Color(0xFFE7E9EE)
val md_theme_light_surfaceContainerHighest = Color(0xFFDDE0E6)
val md_theme_light_inverseOnSurface = ShellPilotWhite
val md_theme_light_inverseSurface = Color(0xFF27272A)
val md_theme_light_inversePrimary = Color(0xFFE4E4E7)

val md_theme_dark_primary = Color(0xFFF5F5F5)
val md_theme_dark_onPrimary = Color(0xFF18181B)
val md_theme_dark_primaryContainer = Color(0xFF2D2D31)
val md_theme_dark_onPrimaryContainer = Color(0xFFF5F5F5)
val md_theme_dark_secondary = Color(0xFFE4E4E7)
val md_theme_dark_onSecondary = Color(0xFF18181B)
val md_theme_dark_secondaryContainer = ShellPilotPanel
val md_theme_dark_onSecondaryContainer = Color(0xFFE4E4E7)
val md_theme_dark_tertiary = ShellPilotWarning
val md_theme_dark_onTertiary = Color(0xFFFFFFFF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF7A0014)
val md_theme_dark_onError = Color(0xFF3A0008)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = ShellPilotBlack
val md_theme_dark_onBackground = Color(0xFFF5F5F5)
val md_theme_dark_surface = ShellPilotInk
val md_theme_dark_onSurface = Color(0xFFF5F5F5)
val md_theme_dark_surfaceVariant = ShellPilotPanel
val md_theme_dark_onSurfaceVariant = ShellPilotMuted
val md_theme_dark_outline = Color(0xFF71717A)
val md_theme_dark_outlineVariant = ShellPilotDarkLine
val md_theme_dark_surfaceDim = ShellPilotBlack
val md_theme_dark_surfaceBright = ShellPilotPanelHigh
val md_theme_dark_surfaceContainerLowest = Color(0xFF070708)
val md_theme_dark_surfaceContainerLow = Color(0xFF101012)
val md_theme_dark_surfaceContainer = ShellPilotInk
val md_theme_dark_surfaceContainerHigh = Color(0xFF1D1D20)
val md_theme_dark_surfaceContainerHighest = ShellPilotPanel
val md_theme_dark_inverseOnSurface = ShellPilotBlack
val md_theme_dark_inverseSurface = Color(0xFFE4E4E7)
val md_theme_dark_inversePrimary = Color(0xFF18181B)

val KeyBackgroundNormal = Color(0x55F0F0F0)
val KeyBackgroundPressed = Color(0xAAA0A0FF)
val KeyBackgroundLayout = Color(0x55000000)
val KeyboardBackground = Color(0x55B0B0F0)

// Terminal-specific colors (used for overlays over terminal)
// These are independent of light/dark theme since terminal background is always dark
val TerminalOverlayBackground = Color(0xE60B0B0C) // Semi-transparent neutral ink
val TerminalOverlayText = Color(0xFFFFFFFF) // White
val TerminalOverlayTextSecondary = Color(0xB3FFFFFF) // White at 70% opacity
