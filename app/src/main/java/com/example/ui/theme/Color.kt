package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.model.SpendingCategory

val EmeraldPrimary = Color(0xFF0B4F3F)
val OnEmeraldPrimary = Color(0xFFFFFFFF)
val EmeraldContainer = Color(0xFFC5F4E2)
val OnEmeraldContainer = Color(0xFF002117)

val SlateSecondary = Color(0xFF3E524B)
val OnSlateSecondary = Color(0xFFFFFFFF)
val SlateContainer = Color(0xFFDCE7E2)
val OnSlateContainer = Color(0xFF152620)

val AmberTertiary = Color(0xFFB45309)
val OnAmberTertiary = Color(0xFFFFFFFF)
val AmberContainer = Color(0xFFFEF3C7)
val OnAmberContainer = Color(0xFF451A03)

val AlabasterBackground = Color(0xFFF6F7F5)
val InkOnBackground = Color(0xFF121A17)
val PureSurface = Color(0xFFFFFFFF)
val SageSurfaceVariant = Color(0xFFE8EEEC)
val MutedOnSurfaceVariant = Color(0xFF53635D)
val SubtleOutline = Color(0xFFD0DBD6)

// Dark palette
val EmeraldPrimaryDark = Color(0xFF6EE7B7)
val OnEmeraldPrimaryDark = Color(0xFF00382B)
val EmeraldContainerDark = Color(0xFF0D3B2F)
val OnEmeraldContainerDark = Color(0xFFC5F4E2)

val SlateSecondaryDark = Color(0xFFB4C9C0)
val OnSlateSecondaryDark = Color(0xFF1F352E)
val SlateContainerDark = Color(0xFF283D36)
val OnSlateContainerDark = Color(0xFFDCE7E2)

val AmberTertiaryDark = Color(0xFFFBBF24)
val OnAmberTertiaryDark = Color(0xFF451A03)
val AmberContainerDark = Color(0xFF5A3107)
val OnAmberContainerDark = Color(0xFFFEF3C7)

val ObsidianBackground = Color(0xFF0B110F)
val MistOnBackground = Color(0xFFE6ECE9)
val ObsidianSurface = Color(0xFF131C19)
val ObsidianSurfaceVariant = Color(0xFF1D2925)
val MistOnSurfaceVariant = Color(0xFFA7B8B1)
val ObsidianOutline = Color(0xFF2F3F39)

// Semantic Finance Colors
val IncomeEmerald = Color(0xFF059669)
val ExpenseCoral = Color(0xFFDC2626)
val WarningAmber = Color(0xFFD97706)
val HeroGradientStart = Color(0xFF083D30)
val HeroGradientEnd = Color(0xFF116B55)

fun SpendingCategory.accentColor(): Color =
  when (this) {
    SpendingCategory.GROCERIES -> Color(0xFF10B981)
    SpendingCategory.DINING -> Color(0xFFF59E0B)
    SpendingCategory.TRANSPORT -> Color(0xFF3B82F6)
    SpendingCategory.HOUSING -> Color(0xFF6366F1)
    SpendingCategory.SHOPPING -> Color(0xFFEC4899)
    SpendingCategory.SUBSCRIPTIONS -> Color(0xFF8B5CF6)
    SpendingCategory.ENTERTAINMENT -> Color(0xFFF97316)
    SpendingCategory.HEALTH -> Color(0xFF14B8A6)
    SpendingCategory.TRAVEL -> Color(0xFF0EA5E9)
    SpendingCategory.INCOME -> Color(0xFF059669)
    SpendingCategory.TRANSFER -> Color(0xFF64748B)
    SpendingCategory.OTHER -> Color(0xFF78716C)
  }
