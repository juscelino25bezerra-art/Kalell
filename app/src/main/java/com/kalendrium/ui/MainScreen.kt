package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.CalendarMode
import com.kalendrium.domain.Dates
import com.kalendrium.domain.Engine
import com.kalendrium.domain.Schedule
import com.kalendrium.ui.theme.AppTheme
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.hexColor
import com.kalendrium.ui.theme.mix

@Composable
fun KalendriumApp(ctl: AppController) {
    val data = ctl.data
    // trocar o idioma recompõe a tela inteira (os textos não são estado do Compose)
    key(ctl.lang) {
    AppTheme(data.palette, data.theme, data.uiStyle, ctl.current.color, dynamic = data.dynamicColor) {
        val tm = rememberTextMeasurer()
        SideEffect { ctl.textMeasurer = tm }
        val look = Look.current
        val c = look.colors
        Box(
            Modifier.fillMaxSize().background(look.page).drawBehind {
                if (look.glass) {
                    // manchas de cor por trás do vidro: gradientes radiais (leves e iguais em qualquer versão do Android)
                    val a = if (c.dark) .55f else 1f
                    drawRect(Brush.radialGradient(listOf(c.primary.copy(alpha = .34f * a), Color.Transparent), Offset(size.width * .05f, size.height * .10f), 300.dp.toPx()))
                    drawRect(Brush.radialGradient(listOf(c.offStrong.copy(alpha = .22f * a), Color.Transparent), Offset(size.width * 1.02f, size.height * .36f), 280.dp.toPx()))
                    drawRect(Brush.radialGradient(listOf(look.sc.copy(alpha = .26f * a), Color.Transparent), Offset(size.width * .02f, size.height * .70f), 280.dp.toPx()))
                    drawRect(Brush.radialGradient(listOf(c.primary.copy(alpha = .20f * a), Color.Transparent), Offset(size.width * .95f, size.height * .96f), 260.dp.toPx()))
                }
            },
        ) {
            // leitura inicial dos arquivos (fora da thread principal): só o fundo por alguns milissegundos
            if (ctl.loaded) Column(Modifier.fillMaxSize()) {
                TopBar(ctl)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 30.dp)) {
                        TodayHeader(ctl)
                        // a mesma tela nos dois modos: só os dados, as ações e os indicadores mudam (ver Views.kt)
                        CalendarBody(ctl)
                    }
                    Spacer(Modifier.height(if (look.glass) 92.dp else 80.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
            if (ctl.loaded) BottomBar(ctl, Modifier.align(Alignment.BottomCenter))
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
            null -> Unit
        }
        if (ctl.monthPicker) MonthPickerDialog(ctl.view, { y, m -> ctl.goTo(y, m); ctl.monthPicker = false }, { ctl.monthPicker = false })
        ctl.busy?.let { BusyDialog(it) }
        ctl.messages.firstOrNull()?.let { m -> MsgDialog(m) { ctl.answer(m, it) } }
    }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TopBar(ctl: AppController) {
    val look = Look.current
    val c = look.colors
    Column(
        Modifier.fillMaxWidth().background(if (look.glass) Color.Transparent else look.page.copy(alpha = .96f)).statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)) {
                // linha 1: título à esquerda; visualização e ajustes à direita
                Row(Modifier.fillMaxWidth().padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Kalendrium", Modifier.weight(1f).padding(end = 8.dp),
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    ViewMenuButton(ctl)
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(Icons.Outlined.Settings, L("Configurações", "Settings", "Configuración"), { ctl.openSettings() }, solid = look.card)
                }
            }
        }
    }
}

/**
 * Pessoal | Work na largura toda e o card de hoje. Fica no início da área rolável (e não presa no topo),
 * para o calendário ganhar espaço quando a pessoa rola a tela.
 */
@Composable
private fun TodayHeader(ctl: AppController) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ModeSwitch(ctl.mode, ctl::selectMode, Modifier.fillMaxWidth().padding(bottom = 14.dp), fill = true)
        when {
            ctl.mode == CalendarMode.PERSONAL -> Hero(
                Modifier.fillMaxWidth(),
                info = personalSummary(ctl.personal, ctl.today, ctl.now()),
                action = L("Novo evento", "New event", "Nuevo evento"), icon = Icons.Filled.Add, onAction = { ctl.openEvent(Dates.iso(ctl.today)) },
                today = ctl.today,
            )
            ctl.ready -> WorkHero(ctl, Modifier.fillMaxWidth())
            else -> Unit
        }
    }
}

/** Seletor segmentado Pessoal | Work: a opção ativa ganha fundo suave e ✓, a outra fica só com o texto. */
@Composable
fun ModeSwitch(mode: CalendarMode, onSelect: (CalendarMode) -> Unit, modifier: Modifier = Modifier, fill: Boolean = false) {
    val look = Look.current
    val c = look.colors
    val track = RoundedCornerShape(50)
    Row(
        modifier.then(if (look.glass) Modifier.glassSurface(track) else Modifier.clip(track).background(look.card).border(1.dp, c.outlineVar, track)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarMode.entries.forEach { m ->
            val on = m == mode
            val bg by animateColorAsState(if (on) c.primaryContainer else Color.Transparent, tween(160), label = "mode")
            Row(
                (if (fill) Modifier.weight(1f) else Modifier).clip(RoundedCornerShape(50)).background(bg)
                    .press(role = Role.Tab) { onSelect(m) }
                    .semantics { selected = on }
                    .heightIn(min = if (fill) 40.dp else 0.dp)
                    .padding(start = if (on) 10.dp else 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Icon(Icons.Filled.Check, null, tint = c.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(m.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) c.onPrimaryContainer else c.onSurfaceVar, maxLines = 1)
            }
        }
    }
}

/**
 * Posição de hoje dentro do bloco atual, para a barrinha do card de hoje.
 * [total] = dias do bloco (trabalho + folga), [work] = dias de trabalho, [index] = dia de hoje (0 = primeiro).
 */
data class BlockProgress(val total: Int, val work: Int, val index: Int, val label: String)

/** Card superior: "Hoje · dia", a situação do dia em destaque, o restante do resumo e a ação principal do modo. */
@Composable
private fun Hero(
    modifier: Modifier,
    info: String,
    action: String,
    icon: ImageVector?,
    onAction: () -> Unit,
    today: java.time.LocalDate,
    progress: BlockProgress? = null,
) {
    val look = Look.current
    val c = look.colors
    val shape = RoundedCornerShape(28.dp)
    // o resumo vem como "Trabalho hoje  •  Próxima folga ...": a primeira parte vira o título do card
    val parts = info.split("  •  ")
    val headline = parts.first()
    val detail = parts.drop(1).joinToString("\n")
    val soft = c.onPrimary.copy(alpha = .78f)
    Column(
        modifier.clip(shape)
            .background(if (look.glass) c.primary.copy(alpha = .86f) else c.primary)
            .then(
                if (look.glass) Modifier.drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), endY = minOf(size.height * .5f, 64.dp.toPx())))
                }.border(1.dp, Color.White.copy(alpha = .30f), shape) else Modifier,
            )
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                L("Hoje", "Today", "Hoy") + " · ${Dates.dayMonth(today)}", Modifier.weight(1f),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (progress != null) {
                Text(
                    progress.label,
                    Modifier.padding(start = 8.dp).clip(RoundedCornerShape(10.dp)).background(c.onPrimary.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onPrimary, maxLines = 1,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(headline, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.5).sp, color = c.onPrimary)
            if (detail.isNotEmpty()) Text(detail, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, color = soft)
        }
        if (progress != null) {
            // um segmento por dia do bloco: trabalho na cor do texto, folga na cor de folga; os já passados ficam cheios
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(progress.total) { i ->
                    val isWork = i < progress.work
                    val base = if (isWork) c.onPrimary else c.off
                    Box(
                        Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50))
                            .background(if (i <= progress.index) base else base.copy(alpha = .32f)),
                    )
                }
            }
        }
        AppButton(action, onAction, Modifier.align(Alignment.End), kind = BtnKind.Inverse, icon = icon)
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

@Composable
private fun WorkHero(ctl: AppController, modifier: Modifier) {
    val sch = ctl.current
    val (info, _) = workSummary(ctl.today, sch)
    // em férias/licença o ciclo não descreve o dia, então a barrinha do bloco some
    val progress = if (Engine.periodOn(Dates.iso(ctl.today), sch) != null) null else {
        val st = Engine.status(ctl.today, sch)
        val b = st.block
        val total = b.w + b.o
        if (total in 2..14) BlockProgress(total, b.w, if (st.work) st.n - 1 else b.w + st.n - 1, L("Bloco", "Block", "Bloque") + " ${b.w}×${b.o}") else null
    }
    Hero(modifier, info, L("Editar escala", "Edit schedule", "Editar horario"), Icons.Outlined.Edit, ctl::openCycle, ctl.today, progress)
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
fun WorkChips(ctl: AppController) {
    val look = Look.current
    val c = look.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val cur = ctl.current
        ctl.data.schedules.forEach { s -> Chip(s, s.id == cur.id) { ctl.selectSchedule(s) } }
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.press(onClick = ctl::newSchedule).height(40.dp)
                .then(if (look.glass) Modifier.glassSurface(shape) else Modifier.clip(shape).dashedBorder(c.outline, 20.dp))
                .padding(start = 12.dp, end = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = c.onSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(L("Nova escala", "New schedule", "Nuevo horario"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
        }
    }
}

/** Escala como chip: a selecionada ganha ✓ e fundo na cor dela; as outras ficam com o ponto colorido. */
@Composable
private fun Chip(s: Schedule, on: Boolean, onClick: () -> Unit) {
    val look = Look.current
    val c = look.colors
    val col = hexColor(s.color)
    val shape = RoundedCornerShape(50)
    val bg = when {
        on && look.glass -> Modifier.clip(shape).background(mix(col, .26f, look.glassStrong)).border(1.5.dp, col, shape)
        on -> Modifier.clip(shape).background(mix(col, .22f, c.surface)).border(1.5.dp, col, shape)
        look.glass -> Modifier.glassSurface(shape)
        else -> Modifier.clip(shape).background(look.card).border(1.dp, c.outlineVar, shape)
    }
    Row(
        Modifier.press(onClick = onClick).semantics { selected = on }.height(40.dp).then(bg).padding(start = if (on) 10.dp else 13.dp, end = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) Icon(Icons.Filled.Check, null, tint = c.onSurface, modifier = Modifier.size(16.dp)) else Dot(col, 10.dp)
        Spacer(Modifier.width(7.dp))
        Text(s.name, fontSize = 14.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, color = c.onSurface, maxLines = 1)
    }
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

@Composable
private fun BottomBar(ctl: AppController, modifier: Modifier) {
    val look = Look.current
    val c = look.colors
    val items = @Composable {
        // "Hoje" é a tela principal: leva o indicador em pílula, como na barra de navegação do Material 3
        NavItem(AppIcons.Today, L("Hoje", "Today", "Hoy"), true, active = true, onClick = ctl::goToday)
        if (ctl.mode == CalendarMode.PERSONAL) NavItem(Icons.Filled.Add, L("Evento", "Event", "Evento"), true) { ctl.openEvent() }
        else NavItem(Icons.Outlined.Edit, L("Escala", "Schedule", "Horario"), ctl.ready, onClick = ctl::openCycle)
        NavItem(Icons.Outlined.Settings, L("Ajustes", "Settings", "Ajustes"), true) { ctl.openSettings() }
    }
    if (look.glass) {
        // pílula de vidro flutuante
        val shape = RoundedCornerShape(36.dp)
        Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = 480.dp).padding(horizontal = 18.dp).fillMaxWidth().height(72.dp)
                    .glassSurface(shape, strong = true).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) { items() }
        }
    } else {
        Column(modifier.fillMaxWidth().background(look.card)) {
            HorizontalDivider(color = c.outlineVar, thickness = 1.dp)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(25.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) { items() }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, enabled: Boolean, active: Boolean = false, onClick: () -> Unit) {
    val c = Look.colors
    val fg = if (active) c.onPrimaryContainer else c.onSurfaceVar
    Column(
        Modifier.width(84.dp).graphicsLayer { alpha = if (enabled) 1f else .45f }.press(enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(width = 60.dp, height = 32.dp).clip(RoundedCornerShape(50)).background(if (active) c.primaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            // o texto abaixo já descreve o botão; descrição no ícone faria o TalkBack ler duas vezes
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold, color = fg)
    }
}
