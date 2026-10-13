// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Dates
import com.kalendrium.ui.theme.Danger
import com.kalendrium.ui.theme.Look
import java.time.Instant
import java.time.ZoneOffset

/** Feedback de toque do PWA (v4.10.2): leve redução de escala, sem ripple. */
fun Modifier.press(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) .97f else 1f, tween(110), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled, role = role, onClick = onClick)
}

/** Fundo de controle. */
@Composable
fun Modifier.controlSurface(shape: Shape, solid: Color = Look.colors.secondaryContainer): Modifier {
    return this.clip(shape).background(solid)
}

/** Cartão grande (calendário, mudanças, períodos, resumo). */
@Composable
fun Modifier.cardSurface(shape: Shape = RoundedCornerShape(28.dp), solid: Color = Look.current.card): Modifier {
    return this.clip(shape).background(solid)
}

/** Contorno tracejado (botão "Nova escala"). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Inverse: botão claro sobre fundo na cor primária (card de hoje). */
enum class BtnKind { Tonal, Filled, Text, Danger, Inverse }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Tonal,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fontSize: TextUnit = 14.sp,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
) {
    val c = Look.colors
    val shape = RoundedCornerShape(50)
    val fg = when (kind) {
        BtnKind.Filled -> c.onPrimary
        BtnKind.Text -> c.primary
        BtnKind.Danger -> Danger
        BtnKind.Tonal -> c.onSurface
        BtnKind.Inverse -> c.primary
    }
    val bgMod = when (kind) {
        BtnKind.Tonal -> Modifier.controlSurface(shape)
        BtnKind.Filled -> Modifier.clip(shape).background(c.primary)
        BtnKind.Inverse -> Modifier.clip(shape).background(c.onPrimary)
        else -> Modifier.clip(shape)
    }
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .press(enabled, onClick = onClick)
            .then(bgMod)
            .heightIn(min = 44.dp)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = fontSize, maxLines = 1)
    }
}

/**
 * Botão circular de 44dp (⚙, ‹, ›). Por padrão usa a cor da tela (destaca dentro dos cartões);
 * no topo da tela passe [solid] = cor do cartão.
 */
@Composable
fun RoundIconButton(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, solid: Color = Look.current.page) {
    Box(
        modifier.size(44.dp).press(onClick = onClick).controlSurface(CircleShape, solid),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Look.colors.onSurface, modifier = Modifier.size(22.dp)) }
}

@Composable
fun Field(label: String, modifier: Modifier = Modifier, help: String? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Look.colors.onSurfaceVar)
        content()
        if (help != null) Text(help, fontSize = 14.sp, color = Look.colors.onSurfaceVar)
    }
}

@Composable
fun fieldSurface(shape: Shape): Modifier {
    val c = Look.colors
    return Modifier.clip(shape).background(c.surface).border(1.dp, c.outline, shape)
}

@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxLength: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    numeric: Boolean = false,
    compact: Boolean = false,
    desc: String? = null,
) {
    val c = Look.colors
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = { onChange(it.take(maxLength)) },
        singleLine = minLines == 1,
        minLines = minLines,
        textStyle = TextStyle(color = c.onSurface, fontSize = if (compact) 14.sp else 16.sp, fontWeight = if (compact) FontWeight.Bold else FontWeight.Normal),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                (if (compact) Modifier.clip(shape).background(c.surfaceContainer).border(1.dp, c.outlineVar, shape)
                else fieldSurface(shape)).padding(if (compact) 9.dp else 13.dp),
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, color = c.onSurfaceVar.copy(alpha = .7f), fontSize = if (compact) 14.sp else 16.sp, fontWeight = if (compact) FontWeight.Bold else FontWeight.Normal)
                inner()
            }
        },
    )
}

/** Campo de data (dd/mm/aaaa) que abre o seletor de data do Material 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(value: String, onChange: (String) -> Unit, min: String? = null, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val c = Look.colors
    Row(
        modifier.fillMaxWidth().then(fieldSurface(RoundedCornerShape(12.dp))).press(role = Role.Button) { open = true }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (Dates.isDate(value)) Dates.fmt(value) else Dates.shortHint(), color = c.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(AppIcons.CalendarToday, L("Escolher data", "Choose date", "Elegir fecha"), tint = c.onSurface, modifier = Modifier.size(22.dp))
    }
    if (open) DatePickDialog(value, { onChange(it); open = false }, { open = false }, min)
}

/** Seletor de data do Material 3 (DateField e os chips "Outra data"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(value: String, onPick: (String) -> Unit, onDismiss: () -> Unit, min: String? = null) {
    val c = Look.colors
    val minMillis = min?.takeIf { Dates.isDate(it) }?.let { Dates.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = if (Dates.isDate(value)) Dates.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() else null,
        yearRange = 1900..2200,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = minMillis == null || utcTimeMillis >= minMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AppButton("OK", {
                state.selectedDateMillis?.let { onPick(Dates.iso(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) } ?: onDismiss()
            }, kind = BtnKind.Text)
        },
        dismissButton = { AppButton(L("Cancelar", "Cancel", "Cancelar"), onDismiss, kind = BtnKind.Text) },
        colors = DatePickerDefaults.colors(containerColor = c.surfaceContainer),
    ) { DatePicker(state, colors = DatePickerDefaults.colors(containerColor = c.surfaceContainer)) }
}

/** Linha com caixa de seleção, título e explicação (checkrow do PWA). */
@Composable
fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, title: String, subtitle: String) {
    val c = Look.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp)
            .controlSurface(RoundedCornerShape(18.dp), c.surface)
            .press(role = Role.Checkbox) { onChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = c.primary, uncheckedColor = c.outline, checkmarkColor = c.onPrimary))
        Column(Modifier.padding(start = 2.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = c.onSurface, fontSize = 16.sp)
            Text(subtitle, color = c.onSurfaceVar, fontSize = 13.sp)
        }
    }
}

/** Abas segmentadas (Configurações e alinhamento da nota). */
@Composable
fun <T> SegTabs(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, icon: (T) -> ImageVector? = { null }) {
    val c = Look.colors
    Row(
        modifier.fillMaxWidth().controlSurface(RoundedCornerShape(20.dp), c.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) c.primaryContainer else Color.Transparent)
                    .press(role = Role.Tab) { onSelect(o) }.padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (on) c.onPrimaryContainer else c.onSurfaceVar
                icon(o)?.let { Icon(it, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                Text(label(o), color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Look.colors.onSurfaceVar, fontSize = 12.sp, letterSpacing = .8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun Para(text: String, modifier: Modifier = Modifier, size: TextUnit = 13.sp, color: Color = Look.colors.onSurfaceVar, align: TextAlign? = null) {
    Text(text, modifier = modifier, fontSize = size, color = color, lineHeight = size * 1.5f, textAlign = align)
}

/** Pré-visualização: desenha as folhas no próprio layout em vez de uma janela (usado em testes/@Preview). */
val LocalInlineSheets = staticCompositionLocalOf { false }

/** Folha inferior com o visual das <dialog class="sheet"> do PWA. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    /** Há alterações não salvas: fechar arrastando, tocando fora ou com Voltar pede confirmação. */
    dirty: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = Look.current
    val c = look.colors
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val container = c.surfaceContainer
    val handle = @Composable {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.padding(top = 12.dp, bottom = 14.dp).size(34.dp, 4.dp).clip(RoundedCornerShape(3.dp)).background(c.outline))
        }
    }
    val body = @Composable {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
        ) {
            if (title != null) Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.onSurface, modifier = Modifier.padding(bottom = 4.dp))
            if (subtitle != null) Para(subtitle, Modifier.padding(bottom = 16.dp))
            content()
        }
    }
    if (LocalInlineSheets.current) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).clip(shape).background(container)) { handle(); body() }
        }
        return
    }
    var askDiscard by remember { mutableStateOf(false) }
    val sheetScope = rememberCoroutineScope()
    val dirtyNow by rememberUpdatedState(dirty)
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { v -> if (v == SheetValue.Hidden && dirtyNow) { askDiscard = true; false } else true },
    )
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            containerColor = c.surfaceContainer,
            text = { Text(L("Descartar as alterações não salvas?", "Discard unsaved changes?", "¿Descartar los cambios no guardados?"), color = c.onSurface, fontSize = 15.sp, lineHeight = 22.sp) },
            confirmButton = { AppButton(L("Descartar", "Discard", "Descartar"), { askDiscard = false; onDismiss() }, kind = BtnKind.Danger) },
            dismissButton = {
                AppButton(L("Continuar editando", "Keep editing", "Seguir editando"), {
                    askDiscard = false
                    // se o sistema chegou a esconder a folha, ela volta a aparecer
                    if (!state.isVisible) sheetScope.launch { state.show() }
                }, kind = BtnKind.Text)
            },
        )
    }
    ModalBottomSheet(
        onDismissRequest = { if (dirtyNow) askDiscard = true else onDismiss() },
        sheetState = state,
        shape = shape,
        containerColor = container,
        contentColor = c.onSurface,
        scrimColor = Color.Black.copy(alpha = .53f),
        dragHandle = { handle() },
    ) { body() }
}

@Composable
fun SheetActions(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.End),
        content = content,
    )
}

@Composable
fun Dot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}
