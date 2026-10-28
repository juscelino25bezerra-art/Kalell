// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalell.domain.Dates
import com.kalell.domain.Engine
import com.kalell.domain.L
import com.kalell.domain.Schedule
import com.kalell.ui.theme.AppTheme
import com.kalell.ui.theme.Display
import com.kalell.ui.theme.Look
import com.kalell.ui.theme.hexColor

@Composable
fun KalellApp(ctl: AppController) {
    val data = ctl.data
    // trocar o idioma recompõe a tela inteira (os textos não são estado do Compose)
    key(ctl.lang) {
    AppTheme(data.palette, data.theme, ctl.current.color, dynamic = data.dynamicColor) {
        val tm = rememberTextMeasurer()
        SideEffect { ctl.textMeasurer = tm }
        val look = Look.current
        val c = look.colors
        Box(
            Modifier.fillMaxSize().background(look.page),
        ) {
            // leitura inicial dos arquivos (fora da thread principal): só o fundo por alguns milissegundos
            if (ctl.loaded) {
                // Voltar fora de Hoje leva para Hoje (como as abas do Material 3)
                BackHandler(enabled = ctl.tab != Tab.TODAY) { ctl.selectTab(Tab.TODAY) }
                when (ctl.tab) {
                    Tab.TODAY -> TodayScreen(ctl)
                    Tab.CALENDAR -> CalendarScreen(ctl)
                    Tab.SCHEDULES -> SchedulesScreen(ctl)
                    Tab.NOTES -> NotesScreen(ctl)
                }
                BottomNav(ctl, Modifier.align(Alignment.BottomCenter))
            }
        }
        when (val s = ctl.sheet) {
            is Sheet.Cycle -> CycleSheet(ctl, s.target, s.mode)
            is Sheet.ChangeEdit -> ChangeSheet(ctl, s.date, s.existing)
            is Sheet.NoteEdit -> NoteSheet(ctl, s.date)
            is Sheet.PeriodEdit -> PeriodSheet(ctl, s.existing)
            is Sheet.Share -> ShareSheet(ctl, s.start, s.end)
            is Sheet.Settings -> SettingsSheet(ctl, s.tab)
            is Sheet.EventEdit -> EventSheet(ctl, s.date, s.existing, s.start)
            is Sheet.DayNoteEdit -> DayNoteSheet(ctl, s.date)
            is Sheet.QuickNote -> QuickNoteSheet(ctl, s.date)
            Sheet.Search -> SearchSheet(ctl)
            Sheet.Upcoming -> UpcomingSheet(ctl)
            null -> Unit
        }
        if (ctl.monthPicker) MonthPickerDialog(ctl.view, { y, m -> ctl.goTo(y, m); ctl.monthPicker = false }, { ctl.monthPicker = false })
        ctl.busy?.let { BusyDialog(it) }
        ctl.messages.firstOrNull()?.let { m -> MsgDialog(m) { ctl.answer(m, it) } }
    }
    }
}

/** Área rolável de cada aba: largura máxima de leitura, espaço para a barra inferior e as barras do sistema. */
@Composable
fun ScreenScroll(state: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(state), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
        Spacer(Modifier.height(104.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** Título grande das abas Calendário, Escalas e Notas, com ações à direita. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, fontFamily = Display, letterSpacing = (-.8).sp, color = Look.colors.onSurface, maxLines = 1)
        actions()
    }
}

/**
 * Barra inferior única do redesign: Hoje, Calendário, "+" (novo evento), Escalas e Notas.
 */
@Composable
private fun BottomNav(ctl: AppController, modifier: Modifier) {
    val look = Look.current
    val c = look.colors
    val items: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        NavTab(KIcons.Waves, L("Hoje", "Today", "Hoy"), ctl.tab == Tab.TODAY) { ctl.selectTab(Tab.TODAY) }
        NavTab(KIcons.Calendar, L("Calendário", "Calendar", "Calendario"), ctl.tab == Tab.CALENDAR) { ctl.selectTab(Tab.CALENDAR) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(20.dp)
            // botão central saltado, com um anel da cor da tela separando-o da barra (visual Ondas)
            Box(
                Modifier.offset(y = (-18).dp).size(66.dp).clip(RoundedCornerShape(24.dp)).background(look.page),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(56.dp).clip(shape).background(c.primary)
                        .press { ctl.openEvent(if (ctl.tab == Tab.CALENDAR) Dates.iso(ctl.selected) else Dates.iso(ctl.today)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(KIcons.Plus, L("Novo evento", "New event", "Nuevo evento"), tint = c.onPrimary, modifier = Modifier.size(26.dp)) }
            }
        }
        NavTab(KIcons.Briefcase, L("Escalas", "Schedules", "Horarios"), ctl.tab == Tab.SCHEDULES) { ctl.selectTab(Tab.SCHEDULES) }
        NavTab(KIcons.Notes, L("Notas", "Notes", "Notas"), ctl.tab == Tab.NOTES) { ctl.selectTab(Tab.NOTES) }
    }
    // barra flutuante (visual Ondas): cartão arredondado com borda fina, longe das bordas da tela
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().height(70.dp).clip(shape).background(look.card).border(1.dp, c.outlineVar, shape).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { items() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavTab(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val c = Look.colors
    val fg = if (active) (if (c.dark) c.primary else c.onPrimaryContainer) else c.onSurfaceVar
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).press(role = Role.Tab, onClick = onClick).semantics { selected = active }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 30.dp).clip(RoundedCornerShape(50)).background(if (active) c.primary.copy(alpha = .16f) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            // o texto abaixo já descreve o botão; descrição no ícone faria o TalkBack ler duas vezes
            Icon(icon, null, tint = fg, modifier = Modifier.size(21.dp))
        }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** Texto do card superior no modo Work e se o destaque é de trabalho (true) ou de folga/afastamento (false). */
fun workSummary(today: java.time.LocalDate, sch: Schedule): Pair<String, Boolean> {
    fun whenText(d: java.time.LocalDate) = if (d == today.plusDays(1)) L("amanhã", "tomorrow", "mañana")
        else L("", "on ", "el ") + Dates.weekdayDayMonth(d)
    // férias, licença ou afastamento cadastrados em "Períodos" valem mais que o ciclo no resumo
    Engine.periodOn(Dates.iso(today), sch)?.let { p ->
        val name = p.title.ifEmpty { L(L("Período", "Period", "Período"), "Period", L("Período", "Period", "Período")) }
        val back = Engine.nextWorkAfter(Dates.parse(p.end), sch)
        val until = if (p.end == Dates.iso(today)) "$name " + L("até hoje", "until today", "hasta hoy")
        else "$name " + L("até ", "until ", "hasta el ") + Dates.dayMonth(Dates.parse(p.end))
        return (if (back != null) "$until  •  " + L("Volta ao trabalho", "Back to work", "Vuelta al trabajo") + " ${whenText(back)}" else until) to false
    }
    val st = Engine.status(today, sch)
    val next = Engine.nextSwitch(today, sch)
    val current = if (st.work) L("Trabalho hoje", "Working today", "Trabajo hoy") else L("Folga hoje", "Day off today", "Libre hoy")
    val upcoming = next?.let { (d, _) ->
        (if (st.work) L("Próxima folga", "Next day off", "Próximo día libre") else L("Próximo trabalho", "Next work day", "Próximo día de trabajo")) + " ${whenText(d)}"
    }
    val soon = Engine.upcomingPeriod(today, sch)?.let { p ->
        p.title.ifEmpty { L(L("Período", "Period", "Período"), "Period", L("Período", "Period", "Período")) } + L(" a partir de ", " starting ", " desde ") + whenText(Dates.parse(p.start))
    }
    return listOfNotNull(current, upcoming, soon).joinToString("  •  ") to st.work
}


/** "Gerando imagem…" enquanto a imagem ou o PDF são desenhados em segundo plano. */
@Composable
private fun BusyDialog(text: String) {
    val c = Look.colors
    AlertDialog(
        onDismissRequest = {},
        containerColor = c.surfaceContainer,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), color = c.primary, strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(text, color = c.onSurface, fontSize = 15.sp)
            }
        },
        confirmButton = {},
    )
}

/** Mudanças e períodos da escala (visualizações mensais). */
@Composable
fun WorkCards(ctl: AppController) {
    ChangesCard(ctl)
    PeriodsCard(ctl)
}


@Composable
fun SectionCard(title: String, text: String, action: String, onAction: () -> Unit, content: @Composable () -> Unit) {
    val c = Look.colors
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        val narrow = maxWidth < 520.dp
        Column(Modifier.fillMaxWidth().cardSurface().padding(if (narrow) 17.dp else 20.dp)) {
            Row(verticalAlignment = if (narrow) Alignment.Top else Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.2).sp, color = c.onSurface, modifier = Modifier.padding(bottom = 4.dp))
                    Para(text)
                }
                AppButton(action, onAction, kind = BtnKind.Filled, icon = Icons.Filled.Add,
                    padding = if (narrow) androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp) else androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 11.dp))
            }
            content()
        }
    }
}

@Composable
private fun ChangesCard(ctl: AppController) {
    val c = Look.colors
    val sc = ctl.current
    val ps = Engine.positions(sc.blocks)
    SectionCard(L("Mudanças da escala", "Schedule changes", "Cambios del horario"), L("Uma mudança recalcula somente daquela data em diante.", "A change only recalculates from that date on.", "Un cambio solo recalcula desde esa fecha en adelante."), L("Mudança", "Change", "Cambio"), { ctl.openChange() }) {
        val arr = sc.changes.sortedBy { it.date }
        if (arr.isEmpty()) Para(L("Nenhuma mudança cadastrada. O ciclo-base está sendo projetado continuamente.", "No changes yet. The base cycle repeats continuously.", "No hay cambios. El ciclo base se repite de forma continua."), Modifier.padding(top = 15.dp))
        arr.forEachIndexed { i, ch ->
            val p = ps.getOrNull(ch.pos) ?: ps[0]
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Dates.fmt(ch.date), fontWeight = FontWeight.Bold, color = c.onSurface)
                    Text(p.label, fontSize = 13.sp, color = c.onSurfaceVar)
                }
                AppButton(L("Editar", "Edit", "Editar"), { ctl.openChange(ch.date, ch) })
            }
            if (i < arr.lastIndex) HorizontalDivider(color = c.outlineVar)
        }
        sc.history.forEach { e ->
            Para(
                L("Antes de ${Dates.fmt(e.to)} vale a escala preservada", "Before ${Dates.fmt(e.to)} the preserved schedule applies", "Antes del ${Dates.fmt(e.to)} se aplica el horario conservado") +
                    ": ${Engine.cycleText(e.blocks)}" +
                    (if (e.changes.isNotEmpty()) " (${e.changes.size} " + L("mudança(s) preservada(s)", "preserved change(s)", "cambio(s) conservado(s)") + ")" else "") + ".",
                Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun PeriodsCard(ctl: AppController) {
    val c = Look.colors
    SectionCard(
        L("Períodos", "Periods", "Períodos"),
        L("Marque férias, licenças, afastamentos ou qualquer intervalo sem alterar sua escala. Valem só para a escala selecionada.", "Mark vacations, leaves or any time off without changing your schedule. They apply only to the selected schedule.", "Marque vacaciones, permisos, bajas o cualquier intervalo sin cambiar su horario. Solo se aplican al horario seleccionado."),
        L("Período", "Period", "Período"), { ctl.openPeriod() },
    ) {
        val arr = ctl.current.periods.sortedBy { it.start }
        if (arr.isEmpty()) Para(L("Nenhum período cadastrado.", "No periods yet.", "No hay períodos."), Modifier.padding(top = 15.dp))
        arr.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().press { ctl.openPeriod(p) }.padding(vertical = 13.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 32.dp).clip(RoundedCornerShape(50)).background(hexColor(p.color)))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(p.title.ifEmpty { L("Período sem descrição", "Untitled period", "Período sin descripción") }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1)
                    Text("${Dates.fmt(p.start)} – ${Dates.fmt(p.end)}", fontSize = 12.sp, color = c.onSurfaceVar)
                }
            }
            if (i < arr.lastIndex) HorizontalDivider(color = c.outlineVar)
        }
    }
}

@Composable
fun EmptySetupCard(onCreate: () -> Unit) {
    val c = Look.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp).cardSurface().padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(c.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(AppIcons.CalendarMonth, null, tint = c.onPrimaryContainer, modifier = Modifier.size(30.dp))
        }
        Text(L("Crie sua primeira escala", "Create your first schedule", "Cree su primer horario"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.onSurface, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Para(
            L("Comece do zero: escolha os dias de trabalho e folga, dê um nome à escala e informe uma data de referência.", "Start from scratch: choose the work days and days off, name the schedule and set a reference date.", "Empiece de cero: elija los días de trabajo y libres, ponga un nombre al horario e indique una fecha de referencia."),
            Modifier.widthIn(max = 440.dp).padding(bottom = 22.dp), size = 16.sp, align = TextAlign.Center,
        )
        AppButton(L("Criar minha escala", "Create my schedule", "Crear mi horario"), onCreate, kind = BtnKind.Filled, icon = Icons.Filled.Add)
    }
}

