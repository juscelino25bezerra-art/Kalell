// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalell.domain.Category
import com.kalell.domain.Event
import com.kalell.domain.L
import com.kalell.ui.theme.Display
import com.kalell.ui.theme.Look
import com.kalell.ui.theme.hexColor
import com.kalell.ui.theme.mix

/**
 * Ícones de traço (desenhados para o Kalell, viewBox 24). Na 7.0 o traço ficou fino (1,6), como no visual Ondas.
 * Ficam aqui em vez de material-icons-extended pelo mesmo motivo de [AppIcons]: build release leve.
 */
object KIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.6f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
        }.build()

    /** "Hoje" no visual Ondas: um ponto com ondas em volta (o mesmo motivo do card de destaque). */
    val Waves by lazy {
        ImageVector.Builder("k.waves", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(addPathNodes("M14.5 12a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0z"), fill = SolidColor(Color.Black))
            addPath(addPathNodes("M18 12a6 6 0 1 1-12 0a6 6 0 1 1 12 0z"), stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f)
            addPath(addPathNodes("M21.5 12a9.5 9.5 0 1 1-19 0a9.5 9.5 0 1 1 19 0z"), stroke = SolidColor(Color.Black), strokeAlpha = .5f, strokeLineWidth = 1.6f)
        }.build()
    }
    val Home by lazy { stroke("k.home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Calendar by lazy { stroke("k.calendar", "M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M8 3v4", "M16 3v4", "M3 10h18") }
    val Briefcase by lazy { stroke("k.briefcase", "M5 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", "M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M3 13h18") }
    val Notes by lazy { stroke("k.notes", "M5 4h10l4 4v12H5z", "M15 4v4h4", "M9 12h6", "M9 16h4") }
    val Search by lazy { stroke("k.search", "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z", "M20 20l-3.5-3.5") }
    val Bell by lazy { stroke("k.bell", "M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8", "M10 20a2 2 0 0 0 4 0") }
    val Menu by lazy { stroke("k.menu", "M4 6h16", "M4 12h16", "M4 18h16") }
    val Heart by lazy { stroke("k.heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z") }
    val Book by lazy { stroke("k.book", "M3 5h5a4 4 0 0 1 4 4v11a3 3 0 0 0-3-3H3z", "M21 5h-5a4 4 0 0 0-4 4v11a3 3 0 0 1 3-3h6z") }
    val More by lazy { stroke("k.more", "M5 12h.01", "M12 12h.01", "M19 12h.01", width = 3f) }
    val Swap by lazy { stroke("k.swap", "M4 12a8 8 0 0 1 14-5.3L20 9", "M20 4v5h-5", "M20 12a8 8 0 0 1-14 5.3L4 15", "M4 20v-5h5") }
    val Plus by lazy { stroke("k.plus", "M12 5v14", "M5 12h14", width = 2f) }
    val Close by lazy { stroke("k.close", "M6 6l12 12", "M18 6L6 18") }
    val ChevronRight by lazy { stroke("k.right", "M9 6l6 6-6 6") }
    val ChevronLeft by lazy { stroke("k.left", "M15 6l-6 6 6 6") }
    val ChevronDown by lazy { stroke("k.down", "M6 9l6 6 6-6") }
    val Pin by lazy { stroke("k.pin", "M12 21s-6-5.5-6-11a6 6 0 1 1 12 0c0 5.5-6 11-6 11z", "M14 10a2 2 0 1 1-4 0a2 2 0 1 1 4 0z") }
    val Repeat by lazy { stroke("k.repeat", "M17 2l4 4-4 4", "M3 11V9a3 3 0 0 1 3-3h15", "M7 22l-4-4 4-4", "M21 13v2a3 3 0 0 1-3 3H3") }
    val Clock by lazy { stroke("k.clock", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M12 7v5l3 2") }
    val Edit by lazy { stroke("k.edit", "M4 20h4L19 9l-4-4L4 16z", "M14 6l4 4") }
    val Eye by lazy { stroke("k.eye", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z") }
    val Share by lazy { stroke("k.share", "M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7", "M12 3v12", "M8 7l4-4 4 4") }
    val File by lazy { stroke("k.file", "M6 3h8l5 5v13H6z", "M14 3v5h5") }
    val Flag by lazy { stroke("k.flag", "M5 21V4", "M5 4h11l-2 4 2 4H5") }
    val Sun by lazy { stroke("k.sun", "M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M12 2v2", "M12 20v2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4", "M2 12h2", "M20 12h2", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4") }
    val Umbrella by lazy { stroke("k.umbrella", "M3 12a9 9 0 0 1 18 0z", "M12 12v7a2 2 0 0 0 4 0") }
    val Gear by lazy { stroke("k.gear", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z", "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z") }
    val Trash by lazy { stroke("k.trash", "M4 7h16", "M10 11v6", "M14 11v6", "M5 7l1 13a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1l1-13", "M9 7V4h6v3") }
}

/** Ícone de cada categoria (seletor do evento e linhas da agenda). */
val Category.icon: ImageVector get() = when (this) {
    Category.PERSONAL -> KIcons.Home
    Category.WORK -> KIcons.Briefcase
    Category.HEALTH -> KIcons.Heart
    Category.STUDY -> KIcons.Book
    Category.OTHER -> KIcons.More
}

val Category.tint: Color get() = hexColor(color)

/** Título de seção com ação opcional à direita ("Próximos dias · Ver todos"). */
@Composable
fun SectionHeader(title: String, action: String? = null, modifier: Modifier = Modifier, onAction: () -> Unit = {}) {
    val c = Look.colors
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, letterSpacing = (-.2).sp, color = c.onSurface)
        if (action != null) Text(
            action, Modifier.clip(RoundedCornerShape(10.dp)).press(onClick = onAction).padding(horizontal = 6.dp, vertical = 10.dp),
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary,
        )
    }
}

/** Botão redondo só com ícone (44dp de toque), transparente ou com fundo de cartão. */
@Composable
fun IconAction(icon: ImageVector, desc: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, badge: Boolean = false) {
    val look = Look.current
    val c = look.colors
    Box(
        modifier.size(44.dp).clip(CircleShape).then(if (filled) Modifier.cardSurface(CircleShape) else Modifier).press(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = c.onSurface, modifier = Modifier.size(22.dp))
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 11.dp).size(8.dp).clip(CircleShape).background(c.workStrong))
    }
}

/** Abas segmentadas do redesign (Mês | Semana | Agenda, Minhas escalas | Histórico). */
@Composable
fun <T> Segmented(options: List<T>, value: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Look.colors
    Row(
        modifier.fillMaxWidth().cardSurface(RoundedCornerShape(50)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == value
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(50)).background(if (on) c.primary else Color.Transparent)
                    .press(role = Role.Tab) { onSelect(o) }.semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(label(o), color = if (on) c.onPrimary else c.onSurfaceVar, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
        }
    }
}

/** Pílula selecionável (filtros, repetição, lembrete). */
@Composable
fun PillChip(text: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, dot: Color? = null, role: Role = Role.RadioButton) {
    val c = Look.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 38.dp).clip(shape)
            .background(if (on) mix(c.primary, .18f, Look.current.card) else Color.Transparent)
            .border(1.5.dp, if (on) c.primary else c.outlineVar, shape)
            .press(role = role, onClick = onClick).semantics { selected = on }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) c.onSurface else c.onSurfaceVar, maxLines = 1)
    }
}

/** Quadradinho colorido com ícone (atalhos, ações da escala, categorias). */
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .5f))
    }
}

/** Linha de ação em cartão: ícone, título, subtítulo e seta ("Editar escala › Alterar padrão e datas"). */
@Composable
fun ActionRow(icon: ImageVector, tint: Color, title: String, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val c = Look.colors
    Row(
        modifier.fillMaxWidth().cardSurface(RoundedCornerShape(18.dp)).press(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, fontSize = 12.sp, color = c.onSurfaceVar, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(KIcons.ChevronRight, null, tint = c.onSurfaceVar, modifier = Modifier.size(20.dp))
    }
}

/** Linha da agenda: barrinha na cor, horário, título e detalhe. */
@Composable
fun AgendaRow(color: Color, time: String, title: String, sub: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Look.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Look.current.page)
            .then(if (onClick != null) Modifier.press(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 34.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(10.dp))
        if (icon != null) { IconTile(icon, color, 32.dp); Spacer(Modifier.width(10.dp)) }
        Text(time, Modifier.width(if (time.length > 5) 74.dp else 48.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVar, maxLines = 2)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = c.onSurfaceVar, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Linha de um evento (a ocorrência de um repetido abre a série). */
@Composable
fun EventAgendaRow(ctl: AppController, e: Event, modifier: Modifier = Modifier) {
    val sub = listOfNotNull(
        e.category.label,
        e.timeText.takeIf { !e.allDay && e.end.isNotEmpty() },
        e.location.ifEmpty { null },
        e.repeat.takeIf { e.recurring }?.label,
    ).joinToString(" · ")
    AgendaRow(hexColor(e.color), if (e.allDay) L("Dia todo", "All day", "Todo el día") else e.start, e.title, sub, { ctl.openEvent(e.date, ctl.baseOf(e)) }, modifier, icon = e.category.icon)
}

/** Estatística em caixinha ("25 turnos em out"). */
@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    val c = Look.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.onSurface.copy(alpha = .06f)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, color = c.onSurface, maxLines = 1)
        Text(label, fontSize = 11.sp, color = c.onSurfaceVar, maxLines = 2, lineHeight = 14.sp)
    }
}

/** Selo pequeno ("Ativa", "Folga", "Simulação"). */
@Composable
fun Badge(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 11.dp, vertical = 5.dp),
        fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg, maxLines = 1,
    )
}

/** Barra de progresso do bloco: um segmento por dia; trabalho e folga com cores próprias, os já passados cheios. */
@Composable
fun BlockBar(total: Int, work: Int, index: Int, workColor: Color, offColor: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total.coerceAtMost(28)) { i ->
            val base = if (i < work) workColor else offColor
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(if (i <= index) base else base.copy(alpha = .28f)))
        }
    }
}
