// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Change
import com.kalendrium.domain.Dates
import com.kalendrium.domain.Engine
import com.kalendrium.domain.Period
import com.kalendrium.domain.Schedule
import com.kalendrium.ui.theme.Look
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------------------------------------------------------------- escala
@Composable
fun CycleSheet(ctl: AppController, sc: Schedule, mode: CycleMode) {
    val scope = rememberCoroutineScope()
    val initialRows = remember(sc) { sc.blocks.mapIndexed { i, b -> BlockRow("${b.w}", "${b.o}", b.name, i) } }
    // rememberSaveable: o que foi digitado sobrevive à recriação da Activity (tema do sistema, multitarefa, fonte)
    val rows = rememberSaveable(sc, saver = BlockRowsSaver) { mutableStateListOf<BlockRow>().apply { addAll(initialRows) } }
    var name by rememberSaveable(sc) { mutableStateOf(sc.name) }
    var color by rememberSaveable(sc) { mutableStateOf(sc.color) }
    var anchor by rememberSaveable(sc) { mutableStateOf(sc.anchor) }
    var keep by rememberSaveable(sc) { mutableStateOf(false) }
    var from by rememberSaveable(sc) { mutableStateOf(ctl.defaultFrom(sc)) }
    var shiftStart by rememberSaveable(sc) { mutableStateOf(sc.shiftStart) }
    var shiftEnd by rememberSaveable(sc) { mutableStateOf(sc.shiftEnd) }
    val dirty = rows.toList() != initialRows || name != sc.name || color != sc.color || anchor != sc.anchor || keep || shiftStart != sc.shiftStart || shiftEnd != sc.shiftEnd
    AppSheet(
        onDismiss = ctl::closeSheet,
        title = if (mode == CycleMode.First) L("Criar primeira escala", "Create first schedule", "Crear primer horario") else L("Editar escala", "Edit schedule", "Editar horario"),
        subtitle = L("Monte a sequência dos blocos. Use qualquer combinação, como 4×1, 6×2, 3×3 ou 7×1. Ela se repete continuamente.", "Build the sequence of blocks. Use any combination, like 4×1, 6×2, 3×3 or 7×1. It repeats continuously.", "Arme la secuencia de bloques. Use cualquier combinación, como 4×1, 6×2, 3×3 o 7×1. Se repite de forma continua."),
        dirty = dirty,
    ) {
        rows.forEachIndexed { i, r ->
            BlockEditor(r, onChange = { rows[i] = it }, onRemove = { if (rows.size > 1) rows.removeAt(i) })
        }
        AppButton(
            L("Adicionar escala personalizada", "Add custom block", "Añadir bloque personalizado"), { rows.add(BlockRow("4", "1", "", -1)) },
            Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 12.dp), icon = Icons.Filled.Add,
        )
        Field(L("Nome da escala", "Schedule name", "Nombre del horario")) { AppTextField(name, { name = it }, maxLength = 30) }
        Field(
            L("Horário do turno (opcional)", "Shift hours (optional)", "Horario del turno (opcional)"),
            help = L("Mostra quanto falta para o próximo turno, as horas do mês e avisa quando um evento cai no turno. Turno noturno: o fim pode ser no dia seguinte (ex.: 19:00 – 07:00).", "Shows the time to your next shift, the hours in the month and warns when an event falls on a shift. Night shift: the end can be on the next day (e.g. 19:00 – 07:00).", "Muestra cuánto falta para el próximo turno, las horas del mes y avisa cuando un evento cae en el turno. Turno de noche: el fin puede ser al día siguiente (ej.: 19:00 – 07:00)."),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { TimeField(shiftStart, { shiftStart = it; if (shiftEnd.isEmpty()) shiftEnd = plusHours(it, 8) }, clearable = true) }
                Box(Modifier.weight(1f)) { TimeField(shiftEnd, { shiftEnd = it }, clearable = true) }
            }
        }
        Field(L("Cor da escala", "Schedule color", "Color del horario")) { ColorPicker(color, { color = it }, L("Cor da escala", "Schedule color", "Color del horario")) }
        Field(
            L("Data de referência", "Reference date", "Fecha de referencia"),
            help = if (keep) L("Nesta data começa o primeiro dia do primeiro bloco. Ela acompanha a data \"a partir de\" abaixo, para a nova escala começar no 1º dia; altere se quiser começar em outro ponto do ciclo.", "The first day of the first block starts on this date. It follows the \"starting\" date below so the new schedule starts on day 1; change it to start at another point of the cycle.", "En esta fecha empieza el primer día del primer bloque. Sigue la fecha \"a partir de\" de abajo, para que el nuevo horario empiece en el día 1; cámbiela para empezar en otro punto del ciclo.")
            else L("Nesta data começa o primeiro dia do primeiro bloco.", "The first day of the first block starts on this date.", "En esta fecha empieza el primer día del primer bloque."),
        ) { DateField(anchor, { anchor = it }) }
        if (mode == CycleMode.Edit) {
            CheckRow(keep, { on ->
                // C5: com "manter", a escala nova começa no 1º dia do 1º bloco na data "a partir de"
                if (on && anchor == sc.anchor) anchor = from
                if (!on && anchor == from) anchor = sc.anchor
                keep = on
            }, L("Manter os dias anteriores como estão", "Keep earlier days as they are", "Mantener los días anteriores como están"), L("Marcado: a nova escala vale só a partir da data abaixo. Desmarcado: o calendário inteiro é recalculado.", "Checked: the new schedule applies only from the date below. Unchecked: the whole calendar is recalculated.", "Marcado: el nuevo horario se aplica solo a partir de la fecha de abajo. Desmarcado: se recalcula todo el calendario."))
            if (keep) Field(L("Nova escala vale a partir de", "New schedule starts on", "El nuevo horario se aplica a partir de")) {
                DateField(from, { v -> if (anchor == from) anchor = v; from = v })
            }
        }
        SheetActions {
            if (ctl.canDelete(mode, sc)) AppButton(L("Excluir escala", "Delete schedule", "Eliminar horario"), { ctl.deleteSchedule(sc) }, kind = BtnKind.Danger)
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Salvar", "Save", "Guardar"), {
                scope.launch { ctl.submitCycle(mode, sc, CycleForm(rows.toList(), name, color, anchor, keep, from, shiftStart, shiftEnd)) }
            }, kind = BtnKind.Filled)
        }
    }
}

/** "07:00" + 8 h = "15:00" (passa da meia-noite como relógio). */
private fun plusHours(t: String, h: Int): String {
    val x = com.kalendrium.domain.Personal.time(t) ?: return ""
    return x.plusHours(h.toLong()).toString()
}

/** Salva a lista de blocos do editor como texto simples (4 campos por bloco). */
private val BlockRowsSaver = listSaver<SnapshotStateList<BlockRow>, String>(
    save = { list -> list.flatMap { listOf(it.w, it.o, it.name, it.orig.toString()) } },
    restore = { flat -> mutableStateListOf<BlockRow>().apply { flat.chunked(4).forEach { (w, o, n, orig) -> add(BlockRow(w, o, n, orig.toInt())) } } },
)

@Composable
private fun BlockEditor(r: BlockRow, onChange: (BlockRow) -> Unit, onRemove: () -> Unit) {
    val c = Look.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).controlSurface(RoundedCornerShape(18.dp), c.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                MiniField(L("Trabalho", "Work", "Trabajo"), Modifier.weight(1f)) { AppTextField(r.w, { onChange(r.copy(w = it.filter(Char::isDigit))) }, maxLength = 2, numeric = true, compact = true) }
                Text("×", Modifier.padding(horizontal = 8.dp, vertical = 10.dp), fontWeight = FontWeight.Black, color = c.onSurfaceVar)
                MiniField(L("Folga", "Off", "Libre"), Modifier.weight(1f)) { AppTextField(r.o, { onChange(r.copy(o = it.filter(Char::isDigit))) }, maxLength = 2, numeric = true, compact = true) }
            }
            MiniField(L("Nome opcional", "Optional name", "Nombre opcional"), Modifier.padding(top = 8.dp)) { AppTextField(r.name, { onChange(r.copy(name = it)) }, placeholder = L("Ex.: Noturno", "E.g.: Night", "Ej.: Nocturno"), maxLength = 40, compact = true) }
        }
        Box(Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).press(onClick = onRemove), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, L("Remover bloco", "Remove block", "Quitar bloque"), tint = c.onSurface)
        }
    }
}

@Composable
private fun MiniField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Look.colors.onSurfaceVar)
        content()
    }
}

// ---------------------------------------------------------------- mudanças
@Composable
fun ChangeSheet(ctl: AppController, date0: String, existing: Change?) {
    val scope = rememberCoroutineScope()
    val sc = ctl.current
    val ps = Engine.positions(sc.blocks)
    var date by rememberSaveable { mutableStateOf(date0) }
    var pos by rememberSaveable { mutableIntStateOf(existing?.pos ?: 0) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val dirty = date != date0 || pos != (existing?.pos ?: 0)
    AppSheet(ctl::closeSheet, if (existing != null) L("Editar mudança", "Edit change", "Editar cambio") else L("Nova mudança", "New change", "Nuevo cambio"), L("Escolha a data e a posição do ciclo que passa a valer dali em diante.", "Choose the date and the cycle position that applies from then on.", "Elija la fecha y la posición del ciclo que se aplica desde entonces."), dirty = dirty) {
        Field(L("Data da mudança", "Change date", "Fecha del cambio")) { DateField(date, { date = it }, min = Engine.lastTo(sc).ifEmpty { null }) }
        Field(L("Posição no ciclo", "Position in the cycle", "Posición en el ciclo"), help = L("Ex.: 1º dia do bloco 4×2.", "E.g.: 1st day of block 4×2.", "Ej.: 1.er día del bloque 4×2.")) {
            SelectBox(ps.getOrNull(pos)?.label ?: "") { picking = true }
        }
        SheetActions {
            if (existing != null) AppButton(L("Excluir", "Delete", "Eliminar"), { ctl.deleteChange(existing.date) }, kind = BtnKind.Danger)
            Spacer(Modifier.weight(1f))
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Salvar", "Save", "Guardar"), { scope.launch { ctl.submitChange(existing?.date, date, pos) } }, kind = BtnKind.Filled)
        }
    }
    if (picking) {
        OptionListDialog(L("Posição no ciclo", "Position in the cycle", "Posición en el ciclo"), ps.map { it.label }, pos, onPick = { pos = it; picking = false }, onDismiss = { picking = false })
    }
}

@Composable
fun SelectBox(text: String, onClick: () -> Unit) {
    val c = Look.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().then(fieldSurface(shape)).press(onClick = onClick).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = c.onSurface, fontSize = 16.sp)
        Icon(Icons.Filled.ArrowDropDown, null, tint = c.onSurface)
    }
}

/** Lista de opções (o <select> do PWA), com rolagem para ciclos longos. */
@Composable
fun OptionListDialog(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = Look.colors
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 3).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainer).padding(20.dp)) {
            Text(title, fontSize = 22.sp, color = c.onSurface, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp), state = state) {
                itemsIndexed(options) { i, o ->
                    val on = i == selected
                    Text(
                        o,
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) c.primaryContainer else Color.Transparent)
                            .press { onPick(i) }.padding(horizontal = 12.dp, vertical = 12.dp),
                        color = if (on) c.onPrimaryContainer else c.onSurface, fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            SheetActions { AppButton(L("Cancelar", "Cancel", "Cancelar"), onDismiss, kind = BtnKind.Text) }
        }
    }
}

// ---------------------------------------------------------------- notas
@Composable
fun NoteSheet(ctl: AppController, date: String) {
    val sc = ctl.current
    val n = sc.notes[date]
    val d = Dates.parse(date)
    val st = Engine.status(d, sc)
    val text0 = n?.text ?: ""
    val holiday0 = n?.holiday ?: false
    val color0 = n?.color ?: if (n?.holiday == true) "#d93025" else "#f9ab00"
    val align0 = n?.align ?: "center"
    var text by rememberSaveable { mutableStateOf(text0) }
    var holiday by rememberSaveable { mutableStateOf(holiday0) }
    var color by rememberSaveable { mutableStateOf(color0) }
    var align by rememberSaveable { mutableStateOf(align0) }
    AppSheet(
        ctl::closeSheet,
        Dates.longDate(d),
        (if (st.work) L("Dia de trabalho", "Work day", "Día de trabajo") else L("Folga", "Day off", "Día libre")) + " · " + L("bloco", "block", "bloque") + " ${st.block.w}×${st.block.o} · ${sc.name}",
        dirty = text != text0 || holiday != holiday0 || color != color0 || align != align0,
    ) {
        Field(L("Nota", "Note", "Nota"), help = "${text.length}/500 " + L("caracteres", "characters", "caracteres")) {
            AppTextField(text, { text = it }, placeholder = L("Ex.: Plantão, consulta, aniversário...", "E.g.: On call, appointment, birthday...", "Ej.: Guardia, cita, cumpleaños..."), maxLength = 500, minLines = 5)
        }
        CheckRow(holiday, { holiday = it }, L("Feriado", "Holiday", "Festivo"), L("Identifica esta data como feriado no calendário.", "Marks this date as a holiday on the calendar.", "Marca esta fecha como festivo en el calendario."))
        Field(L("Cor da nota", "Note color", "Color de la nota")) { ColorPicker(color, { color = it }, L("Cor da nota", "Note color", "Color de la nota")) }
        Field(L("Alinhamento no dia", "Alignment in the day", "Alineación en el día")) {
            SegTabs(listOf("left", "center", "right"), align, { mapOf("left" to L("Esquerda", "Left", "Izquierda"), "center" to L("Centro", "Center", "Centro"), "right" to L("Direita", "Right", "Derecha")).getValue(it) }, { align = it })
        }
        SheetActions {
            if (n != null) AppButton(L("Excluir nota", "Delete note", "Eliminar nota"), { ctl.deleteNote(date) }, kind = BtnKind.Danger)
            Spacer(Modifier.weight(1f))
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Salvar", "Save", "Guardar"), { ctl.submitNote(date, text, holiday, color, align) }, kind = BtnKind.Filled)
        }
    }
}

// ---------------------------------------------------------------- períodos
@Composable
fun PeriodSheet(ctl: AppController, p: Period?) {
    val today = Dates.iso(ctl.today)
    val start0 = remember { p?.start ?: today }
    var start by rememberSaveable { mutableStateOf(start0) }
    var end by rememberSaveable { mutableStateOf(start0.let { p?.end ?: it }) }
    var title by rememberSaveable { mutableStateOf(p?.title ?: "") }
    var color by rememberSaveable { mutableStateOf(p?.color ?: "#1a73e8") }
    val dirty = start != start0 || end != (p?.end ?: start0) || title != (p?.title ?: "") || color != (p?.color ?: "#1a73e8")
    AppSheet(ctl::closeSheet, if (p != null) L("Editar período", "Edit period", "Editar período") else L("Novo período", "New period", "Nuevo período"), L("O marcador será exibido em todas as datas do intervalo, sem modificar Trabalho/Folga.", "The marker is shown on every date in the range, without changing Work/Off.", "El marcador se muestra en todas las fechas del intervalo, sin cambiar Trabajo/Libre."), dirty = dirty) {
        Field(L("De", "From", "Desde")) { DateField(start, { start = it }) }
        Field(L("Até", "To", "Hasta")) { DateField(end, { end = it }) }
        Field(L("Descrição (opcional)", "Description (optional)", "Descripción (opcional)")) { AppTextField(title, { title = it }, placeholder = L("Ex.: Férias, afastamento, licença...", "E.g.: Vacation, sick leave, leave...", "Ej.: Vacaciones, baja, permiso..."), maxLength = 80) }
        Field(L("Cor do marcador", "Marker color", "Color del marcador")) { ColorPicker(color, { color = it }, L("Cor do marcador", "Marker color", "Color del marcador")) }
        SheetActions {
            if (p != null) AppButton(L("Excluir período", "Delete period", "Eliminar período"), { ctl.deletePeriod(p.id) }, kind = BtnKind.Danger)
            Spacer(Modifier.weight(1f))
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Salvar", "Save", "Guardar"), { ctl.submitPeriod(p?.id, start, end, title, color) }, kind = BtnKind.Filled)
        }
    }
}

// ---------------------------------------------------------------- compartilhar
@Composable
fun ShareSheet(ctl: AppController, s0: String, e0: String) {
    var start by rememberSaveable { mutableStateOf(s0) }
    var end by rememberSaveable { mutableStateOf(e0) }
    var notes by rememberSaveable { mutableStateOf(true) }
    AppSheet(ctl::closeSheet, L("Compartilhar período", "Share period", "Compartir período"), L("Escolha um intervalo de até 12 meses. A imagem inclui a escala, notas, feriados e períodos cadastrados.", "Choose a range of up to 12 months. The image includes the schedule, notes, holidays and periods.", "Elija un intervalo de hasta 12 meses. La imagen incluye el horario, las notas, los festivos y los períodos.")) {
        Field(L("De", "From", "Desde")) { DateField(start, { start = it }) }
        Field(L("Até", "To", "Hasta")) { DateField(end, { end = it }) }
        CheckRow(notes, { notes = it }, L("Incluir o texto das notas", "Include the text of notes", "Incluir el texto de las notas"), L("Desmarque para não expor anotações pessoais na imagem (feriados continuam identificados).", "Uncheck to keep personal notes out of the image (holidays are still marked).", "Desmarque para no mostrar notas personales en la imagen (los festivos siguen marcados)."))
        SheetActions {
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Compartilhar", "Share", "Compartir"), { ctl.submitShare(start, end, notes) }, kind = BtnKind.Filled)
        }
    }
}

// ---------------------------------------------------------------- diálogos
@Composable
fun MsgDialog(m: Msg, onAnswer: (Boolean) -> Unit) {
    val c = Look.colors
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        containerColor = c.surfaceContainer,
        text = { Text(m.text, color = c.onSurface, fontSize = 15.sp, lineHeight = 22.sp) },
        confirmButton = { AppButton(m.ok, { onAnswer(true) }, kind = BtnKind.Filled) },
        dismissButton = m.cancel?.let { { AppButton(it, { onAnswer(false) }, kind = BtnKind.Text) } },
    )
}

/** "Ir para o mês" (o campo type=month do PWA). */
@Composable
fun MonthPickerDialog(view: LocalDate, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val c = Look.colors
    var year by rememberSaveable { mutableIntStateOf(view.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceContainer,
        title = { Text(L("Ir para o mês", "Go to month", "Ir al mes"), color = c.onSurface) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, L("Ano anterior", "Previous year", "Año anterior"), { if (year > 1900) year-- })
                    Text("$year", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, L("Próximo ano", "Next year", "Año siguiente"), { if (year < 2200) year++ })
                }
                (1..12).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            val on = year == view.year && m == view.monthValue
                            Text(
                                Dates.capitalize(Dates.monthName(LocalDate.of(2000, m, 1))).take(3),
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) c.primaryContainer else c.surface)
                                    .press { onPick(year, m) }.padding(vertical = 12.dp),
                                color = if (on) c.onPrimaryContainer else c.onSurface, fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { AppButton(L("Cancelar", "Cancel", "Cancelar"), onDismiss, kind = BtnKind.Text) },
    )
}
