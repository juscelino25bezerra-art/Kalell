package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Dates
import com.kalendrium.domain.weekHeader
import com.kalendrium.domain.Engine
import com.kalendrium.domain.Note
import com.kalendrium.domain.Period
import com.kalendrium.domain.Schedule
import com.kalendrium.ui.theme.HolidayRed
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.fgOn
import com.kalendrium.ui.theme.hexColor
import com.kalendrium.ui.theme.mix
import java.time.LocalDate


/** Célula de dia: (data, tela estreita, altura mínima, modificador com largura/altura já definidas). */
typealias DayCellContent = @Composable (LocalDate, Boolean, Dp, Modifier) -> Unit

/**
 * Cartão do calendário mensal, compartilhado pelos dois modos: cabeçalho com navegação, dias da semana e grade.
 * Só o conteúdo de cada dia ([cell]) e a legenda mudam entre Pessoal e Work.
 */
@Composable
fun CalendarCard(
    view: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMonth: () -> Unit,
    legend: @Composable RowScope.() -> Unit,
    cell: DayCellContent,
) {
    val c = Look.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val narrow = maxWidth < 520.dp
        Column(Modifier.fillMaxWidth().cardSurface().padding(if (narrow) 12.dp else 16.dp)) {
            // mês e ano à esquerda (toque abre o seletor de mês), setas à direita
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).press(onClick = onMonth).semantics { contentDescription = L("Escolher mês", "Choose month", "Elegir mes") }
                        .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(Dates.capitalize(Dates.monthName(view)), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.4).sp, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(view.year.toString(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVar, modifier = Modifier.padding(bottom = 2.dp))
                }
                RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, L("Mês anterior", "Previous month", "Mes anterior"), onPrev)
                Spacer(Modifier.width(6.dp))
                RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, L("Próximo mês", "Next month", "Mes siguiente"), onNext)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                weekHeader().forEach {
                    Text(it, Modifier.weight(1f).padding(vertical = 7.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVar)
                }
            }
            MonthGrid(view, narrow, cell)
            Row(
                Modifier.padding(start = 3.dp, end = 3.dp, top = 13.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                content = legend,
            )
        }
    }
}

/** Legenda da escala de trabalho. */
@Composable
fun RowScope.WorkLegend() {
    LegendItem(Look.current.sc, L("Trabalho", "Work", "Trabajo"))
    LegendItem(Look.colors.offStrong, L("Folga", "Off", "Libre"))
    LegendItem(Look.colors.primary, L("Mudança", "Change", "Cambio"), round = true)
}

@Composable
fun LegendItem(color: Color, text: String, round: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(if (round) CircleShape else RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 12.sp, color = Look.colors.onSurfaceVar)
    }
}

@Composable
private fun MonthGrid(view: LocalDate, narrow: Boolean, cell: DayCellContent) {
    val first = view.dayOfWeek.value % 7
    val nd = view.lengthOfMonth()
    val cells = List(first) { null } + (1..nd).map { view.withDayOfMonth(it) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 5.dp
        val cellW = (maxWidth - gap * 6) / 7
        val minH = cellW / .88f
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    week.forEach { d ->
                        if (d == null) Spacer(Modifier.weight(1f))
                        else cell(d, narrow, minH, Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Dia da escala de trabalho: trabalho/folga, bloco, outras escalas, notas, feriados e períodos. */
@Composable
fun WorkDayCell(
    d: LocalDate,
    sch: Schedule,
    others: List<Schedule>,
    isToday: Boolean,
    narrow: Boolean,
    minH: Dp,
    modifier: Modifier,
    onDay: (String) -> Unit,
    isSelected: Boolean = false,
) {
    val look = Look.current
    val c = look.colors
    val s = Dates.iso(d)
    val st = Engine.status(d, sch)
    val note = sch.notes[s]
    val periods = sch.periods.filter { it.start <= s && s <= it.end }
    val isChange = Engine.cfgFor(s, sch).changes.any { it.date == s }
    val dots = others.filter { Engine.status(d, it).work }.map { hexColor(it.color) }
    val shape = RoundedCornerShape(if (narrow) 14.dp else 16.dp)
    val bg = when {
        isToday -> c.primary
        look.glass && st.work -> mix(look.sc, .30f, look.glassStrong)
        look.glass -> mix(c.off, .80f, look.glassStrong)
        st.work -> mix(look.sc, .24f, c.surface)
        else -> c.off
    }
    // número na cor do estado (trabalho/folga); hoje fica com o cartão inteiro na cor primária
    val numColor = when {
        isToday -> c.onPrimary
        st.work -> mix(look.sc, .55f, c.onSurface)
        else -> c.offStrong
    }
    val marker = if (isToday) c.onPrimary else c.primary
    val desc = Dates.dayMonth(d) + (if (isToday) L(", hoje", ", today", ", hoy") else "") + ", " + (if (st.work) L("trabalho", "work", "trabajo") else L("folga", "day off", "libre")) +
        ", " + L("bloco", "block", "bloque") + " ${st.block.w}×${st.block.o}" + (if (note != null) L(", com nota", ", with note", ", con nota") else "")
    Box(
        modifier.heightIn(min = minH)
            .clip(shape).background(bg)
            .then(if (look.glass && !isToday) Modifier.border(1.dp, look.glassBorder, shape) else Modifier)
            // dia selecionado (mensal + diária): contorno mais grosso na cor do texto
            .then(if (isSelected) Modifier.border(2.5.dp, if (isToday) c.onPrimary else c.onSurface, shape) else Modifier)
            .press { onDay(s) }
            .semantics { contentDescription = desc },
    ) {
        // visual limpo: só o número e, embaixo, os pontos das outras escalas que trabalham no dia.
        // Trabalho/folga ficam pela cor; o bloco e a situação completa aparecem ao tocar no dia e no leitor de tela.
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = if (narrow) 3.dp else 5.dp, vertical = if (narrow) 6.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "${d.dayOfMonth}", fontSize = if (narrow) 15.sp else 16.sp, fontWeight = FontWeight.ExtraBold,
                color = numColor, maxLines = 1,
            )
            Row(Modifier.padding(top = 5.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                dots.forEach { Dot(if (isToday) c.onPrimary else it, 6.dp) }
            }
            if (note != null) NoteChip(note)
            periods.firstOrNull { it.title.isNotEmpty() }?.let { PeriodChip(it) }
        }
        if (isChange) Dot(marker, 7.dp, Modifier.align(Alignment.TopEnd).offset((-7).dp, 7.dp))
        if (note != null) {
            val nc = if (note.holiday) HolidayRed else marker
            Box(
                Modifier.align(Alignment.TopStart).offset(7.dp, 7.dp).size(7.dp)
                    .then(if (note.holiday) Modifier.border(2.dp, nc.copy(alpha = .18f), CircleShape) else Modifier)
                    .clip(CircleShape).background(nc),
            )
        }
        if (periods.isNotEmpty()) {
            Row(Modifier.align(Alignment.TopCenter).offset(y = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                periods.take(3).forEach { p ->
                    Box(Modifier.size(12.dp, 3.dp).clip(RoundedCornerShape(50)).background(hexColor(p.color)))
                }
            }
        }
    }
}

@Composable
private fun NoteChip(n: Note) {
    val t = n.text.split("\n").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
    DayChip(t.ifEmpty { if (n.holiday) "FERIADO" else "NOTA" }, n.color ?: if (n.holiday) "#d93025" else "#f9ab00", n.align)
}

/** Etiqueta colorida dentro do dia (nota da escala ou evento pessoal). */
@Composable
fun DayChip(t: String, col: String, align: String? = "center") {
    Text(
        t,
        Modifier.padding(top = 2.dp).fillMaxWidth().clip(RoundedCornerShape(5.dp)).background(hexColor(col)).padding(horizontal = 3.dp, vertical = 1.dp),
        color = fgOn(col), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = when (align) { "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center },
    )
}

@Composable
private fun PeriodChip(p: Period) {
    val col = hexColor(p.color)
    val shape = RoundedCornerShape(5.dp)
    Text(
        p.title,
        Modifier.padding(top = 2.dp).fillMaxWidth().clip(shape).background(Look.colors.surface.copy(alpha = .82f)).border(1.dp, col, shape).padding(horizontal = 3.dp, vertical = 1.dp),
        color = col, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
    )
}
