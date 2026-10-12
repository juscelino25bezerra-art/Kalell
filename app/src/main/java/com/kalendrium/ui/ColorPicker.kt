package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.fgOn
import com.kalendrium.ui.theme.hexColor
import com.kalendrium.ui.theme.hsl2hex

/** Nomes lidos pelo TalkBack para as cores prontas. */
private val COLOR_NAMES get() = mapOf(
    "#d93025" to L("Vermelho", "Red", "Rojo"), "#e8710a" to L("Laranja", "Orange", "Naranja"), "#f9ab00" to L("Amarelo", "Yellow", "Amarillo"),
    "#34a853" to L("Verde", "Green", "Verde"), "#0b8043" to L("Verde-escuro", "Dark green", "Verde oscuro"), "#00838f" to L("Ciano", "Cyan", "Cian"),
    "#1a73e8" to L("Azul", "Blue", "Azul"), "#3949ab" to L("Índigo", "Indigo", "Índigo"), "#9334e6" to L("Roxo", "Purple", "Morado"),
    "#c2185b" to L("Rosa", "Pink", "Rosa"), "#795548" to L("Marrom", "Brown", "Marrón"), "#5f6368" to L("Cinza", "Gray", "Gris"), "#111111" to L("Preto", "Black", "Negro"),
)

/**
 * Seletor de cor do app (v4.7): 13 cores prontas + personalizada (matiz e brilho),
 * seguindo o tema em vez do seletor nativo.
 */
@Composable
fun ColorPicker(value: String, onChange: (String) -> Unit, label: String) {
    val c = Look.colors
    var custom by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(210f) }
    var light by remember { mutableFloatStateOf(50f) }
    val isPreset = AppController.PRESETS.any { it.equals(value, ignoreCase = true) }
    val items = AppController.PRESETS + listOf("+")
    Column(Modifier.semantics { contentDescription = label }) {
        items.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { item ->
                    val more = item == "+"
                    val on = if (more) !isPreset else item.equals(value, ignoreCase = true)
                    val bg = when {
                        !more -> hexColor(item)
                        !isPreset -> hexColor(value)
                        else -> c.surfaceContainer
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .then(if (on) Modifier.border(3.dp, c.primary, CircleShape).padding(5.dp) else Modifier)
                            .clip(CircleShape).background(bg)
                            .border(2.dp, c.onSurface.copy(alpha = .22f), CircleShape)
                            .press(role = if (more) Role.Button else Role.RadioButton) { if (more) custom = !custom else onChange(item) }
                            .semantics {
                                contentDescription = if (more) L("Cor personalizada", "Custom color", "Color personalizado") else "$label: ${COLOR_NAMES[item.lowercase()] ?: item}"
                                selected = on
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (more) Icon(Icons.Filled.Add, null, tint = if (isPreset) c.onSurface else fgOn(value))
                    }
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (custom) {
            Column(Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GradientSlider(L("Matiz", "Hue", "Tono"), hue, 0f, 360f, Brush.horizontalGradient((0..6).map { hexColor(hsl2hex(it * 60f, 70f, 50f)) })) {
                    hue = it; onChange(hsl2hex(hue, 70f, light))
                }
                GradientSlider(L("Brilho", "Brightness", "Brillo"), light, 20f, 80f, Brush.horizontalGradient(listOf(Color.Black, Color.White))) {
                    light = it; onChange(hsl2hex(hue, 70f, light))
                }
            }
        }
    }
}

@Composable
private fun GradientSlider(label: String, value: Float, min: Float, max: Float, track: Brush, onChange: (Float) -> Unit) {
    val c = Look.colors
    val cb by rememberUpdatedState(onChange)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar)
        fun at(x: Float, w: Float) = (min + (x / w).coerceIn(0f, 1f) * (max - min))
        Canvas(
            Modifier.fillMaxWidth().height(28.dp)
                // acessível ao TalkBack: lê o valor e aceita ajuste por gesto de volume/deslize
                .semantics {
                    contentDescription = label
                    stateDescription = "${value.roundToInt()}"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, steps = 0)
                    setProgress { v -> cb(v.coerceIn(min, max)); true }
                }
                .pointerInput(Unit) { detectTapGestures { cb(at(it.x, size.width.toFloat())) } }
                .pointerInput(Unit) { detectDragGestures { ch, _ -> cb(at(ch.position.x, size.width.toFloat())) } },
        ) {
            val h = 16.dp.toPx()
            val top = (size.height - h) / 2
            drawRoundRect(track, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, h), CornerRadius(h / 2))
            val r = 12.dp.toPx()
            val x = ((value - min) / (max - min)) * (size.width - 2 * r) + r
            drawCircle(c.surface, r, Offset(x, size.height / 2))
            drawCircle(c.primary, r - 1.5.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
        }
    }
}
