package com.kalendrium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kalendrium.R

/** As variáveis de cor do PWA (--primary, --surface, --work...). */
@Immutable
data class AppColors(
    val dark: Boolean,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVar: Color,
    val outline: Color,
    val outlineVar: Color,
    val work: Color,
    val workStrong: Color,
    val off: Color,
    val offStrong: Color,
)

/** Estilo visual escolhido + cor da escala ativa (--sc). */
@Immutable
data class AppLook(val colors: AppColors, val glass: Boolean, val sc: Color) {
    // Fundo da tela e dos cartões. No claro, a tela fica levemente tingida e os cartões claros por cima;
    // no escuro, a tela fica mais escura e os cartões um tom acima (como no Material 3).
    val page get() = if (colors.dark) colors.surface else colors.surfaceContainer
    val card get() = if (colors.dark) colors.surfaceContainer else colors.surface

    // Liquid Glass: vidro fosco clareado (claro) ou levemente iluminado (escuro), borda clara e brilho no topo
    private val glassBase get() = if (colors.dark) mix(Color.White, .06f, colors.surfaceContainer) else mix(Color.White, .55f, colors.surfaceContainer)
    val glassColor get() = glassBase.copy(alpha = if (colors.dark) .58f else .56f)
    val glassStrong get() = glassBase.copy(alpha = if (colors.dark) .72f else .70f)
    val glassBorder get() = if (colors.dark) Color.White.copy(alpha = .14f) else Color.White.copy(alpha = .78f)
    val glassHighlight get() = Color.White.copy(alpha = if (colors.dark) .10f else .55f)
}

/** Manrope (SIL OFL, docs/MANROPE-OFL.txt): fonte do redesign, embutida no APK para funcionar sem internet. */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    Font(R.font.manrope_extrabold, FontWeight.Black),
)

private fun Typography.withFamily(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f), bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)

val LocalLook = staticCompositionLocalOf<AppLook> { error("AppTheme ausente") }

/**
 * Cores do papel de parede (Android 12+), claro e escuro, fornecidas pela Activity.
 * null = o aparelho não oferece cores dinâmicas.
 */
val LocalDynamicColors = staticCompositionLocalOf<Pair<AppColors, AppColors>?> { null }

/**
 * Converte um esquema do Material 3 (ex.: dynamicLightColorScheme) nas cores do app.
 * Trabalho e folga continuam com as cores da paleta Material, para manter o contraste conhecido.
 */
fun ColorScheme.toAppColors(dark: Boolean): AppColors {
    val base = Palettes.all.getValue("material").let { if (dark) it.second else it.first }
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondaryContainer = secondaryContainer,
        surface = surface, surfaceContainer = surfaceContainer, surfaceHigh = surfaceContainerHigh,
        onSurface = onSurface, onSurfaceVar = onSurfaceVariant,
        outline = outline, outlineVar = outlineVariant,
    )
}

object Look {
    val current: AppLook @Composable get() = LocalLook.current
    val colors: AppColors @Composable get() = LocalLook.current.colors
}

val Danger = Color(0xFFBA1A1A)
val HolidayRed = Color(0xFFB3261E)

/** `color-mix(in srgb, a p%, b)` */
fun mix(a: Color, p: Float, b: Color): Color = lerp(b, a, p)

fun hexColor(hex: String, fallback: Color = Color(0xFF65558F)): Color = try {
    Color(0xFF000000 or hex.removePrefix("#").toLong(16))
} catch (_: Exception) {
    fallback
}

fun Color.toHex(): String {
    fun c(v: Float) = (v * 255f + .5f).toInt().coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#" + c(red) + c(green) + c(blue)
}

/** `fgOn()` do PWA: preto ou branco conforme o brilho do fundo. */
fun fgOn(hex: String): Color {
    val c = hexColor(hex)
    val y = .299f * c.red * 255 + .587f * c.green * 255 + .114f * c.blue * 255
    return if (y > 150) Color.Black else Color.White
}

/** hsl -> #rrggbb, igual ao seletor de cor personalizado do PWA. */
fun hsl2hex(h: Float, sIn: Float, lIn: Float): String {
    val s = sIn / 100f
    val l = lIn / 100f
    val a = s * minOf(l, 1 - l)
    fun k(n: Int) = (n + h / 30f) % 12f
    fun f(n: Int) = l - a * maxOf(-1f, minOf(k(n) - 3, minOf(9 - k(n), 1f)))
    fun hx(x: Float) = (x * 255).let { Math.round(it) }.coerceIn(0, 255).toString(16).padStart(2, '0')
    return "#" + hx(f(0)) + hx(f(8)) + hx(f(4))
}

fun resolveColors(palette: String, mode: String, systemDark: Boolean, dynamic: Pair<AppColors, AppColors>? = null): AppColors {
    val dark = mode == "dark" || (mode == "system" && systemDark)
    val p = dynamic ?: Palettes.all[palette] ?: Palettes.all.getValue("material")
    return if (dark) p.second else p.first
}

@Composable
fun AppTheme(palette: String, mode: String, style: String, scheduleColor: String, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val c = resolveColors(palette, mode, isSystemInDarkTheme(), if (dynamic) LocalDynamicColors.current else null)
    val look = AppLook(c, style == "glass", hexColor(scheduleColor))
    val base = if (c.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.primary, onPrimary = c.onPrimary,
        primaryContainer = c.primaryContainer, onPrimaryContainer = c.onPrimaryContainer,
        secondaryContainer = c.secondaryContainer, onSecondaryContainer = c.onSurface,
        surface = c.surface, onSurface = c.onSurface, onSurfaceVariant = c.onSurfaceVar,
        background = c.surface, onBackground = c.onSurface,
        surfaceContainer = c.surfaceContainer, surfaceContainerHigh = c.surfaceHigh,
        surfaceContainerHighest = c.surfaceHigh, surfaceContainerLow = c.surfaceContainer,
        surfaceContainerLowest = c.surface, surfaceVariant = c.surfaceHigh,
        outline = c.outline, outlineVariant = c.outlineVar, error = Danger,
    )
    MaterialTheme(colorScheme = scheme, typography = Typography().withFamily(Manrope)) {
        // Altura de linha "normal" como no CSS (o padrão do Material 3 é 24sp fixo).
        CompositionLocalProvider(
            LocalLook provides look,
            LocalTextStyle provides TextStyle(color = c.onSurface, fontSize = 16.sp, fontFamily = Manrope),
            content = content,
        )
    }
}
