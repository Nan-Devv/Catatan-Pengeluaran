package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

data class DailySpendSemanticColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val cardGradientStart: Color,
    val cardGradientMid: Color,
    val cardGradientEnd: Color,
    val cardBorder: Color,
    val cardContentPrimary: Color,
    val cardContentSecondary: Color,
    val secondaryText: Color,
    val isDark: Boolean
)

val LocalDailySpendColors = staticCompositionLocalOf {
    DailySpendSemanticColors(
        income = EmeraldPrimary,
        incomeContainer = EmeraldSoft,
        expense = ExpenseRose,
        expenseContainer = ExpenseSoft,
        transfer = TransferPlum,
        transferContainer = TransferSoft,
        warning = SunlitAmber,
        warningContainer = SunlitAmberSoft,
        cardGradientStart = Color(0xFFFFFFFF),
        cardGradientMid = Color(0xFFECFDF5),
        cardGradientEnd = Color(0xFFFEF9C3),
        cardBorder = Color(0xFFA7F3D0),
        cardContentPrimary = LightTextPrimary,
        cardContentSecondary = LightTextSecondary,
        secondaryText = LightTextSecondary,
        isDark = false
    )
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldDeep,
    secondary = SunlitAmber,
    onSecondary = Color.White,
    secondaryContainer = SunlitAmberSoft,
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = TransferPlum,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = Color(0xFFF3F2EC),
    error = ExpenseRose,
    onError = Color.White,
    errorContainer = ExpenseSoft,
    onErrorContainer = Color(0xFF9F1239)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = EmeraldSoftDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF271A00),
    secondaryContainer = SunlitAmberSoftDark,
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF1E1B4B),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = Color(0xFF27272A),
    error = ExpenseRose,
    onError = Color.White,
    errorContainer = ExpenseSoftDark,
    onErrorContainer = Color(0xFFFECDD3)
)

private val DailySpendShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DailySpendTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) {
        DailySpendSemanticColors(
            income = EmeraldLight,
            incomeContainer = EmeraldSoftDark,
            expense = Color(0xFFFB7185),
            expenseContainer = ExpenseSoftDark,
            transfer = Color(0xFFA78BFA),
            transferContainer = TransferSoftDark,
            warning = Color(0xFFFBBF24),
            warningContainer = SunlitAmberSoftDark,
            cardGradientStart = Color(0xFF1C2621),
            cardGradientMid = Color(0xFF18241F),
            cardGradientEnd = Color(0xFF242117),
            cardBorder = Color(0xFF2E3D35),
            cardContentPrimary = DarkTextPrimary,
            cardContentSecondary = DarkTextSecondary,
            secondaryText = DarkTextSecondary,
            isDark = true
        )
    } else {
        DailySpendSemanticColors(
            income = EmeraldPrimary,
            incomeContainer = EmeraldSoft,
            expense = ExpenseRose,
            expenseContainer = ExpenseSoft,
            transfer = TransferPlum,
            transferContainer = TransferSoft,
            warning = SunlitAmber,
            warningContainer = SunlitAmberSoft,
            cardGradientStart = Color(0xFFFFFFFF),
            cardGradientMid = Color(0xFFECFDF5),
            cardGradientEnd = Color(0xFFFEF9C3),
            cardBorder = Color(0xFFA7F3D0),
            cardContentPrimary = LightTextPrimary,
            cardContentSecondary = LightTextSecondary,
            secondaryText = LightTextSecondary,
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalDailySpendColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DailySpendTypography,
            shapes = DailySpendShapes,
            content = content
        )
    }
}
