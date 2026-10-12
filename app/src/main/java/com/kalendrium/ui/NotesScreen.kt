package com.kalendrium.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Dates
import com.kalendrium.domain.L
import com.kalendrium.ui.theme.HolidayRed
import com.kalendrium.ui.theme.Look
import com.kalendrium.ui.theme.hexColor

/** Item da aba Notas: nota pessoal ou nota/feriado da escala ativa. */
private data class NoteItem(val date: String, val text: String, val color: String, val work: Boolean, val holiday: Boolean)

@Composable
fun NotesScreen(ctl: AppController) {
    val c = Look.colors
    var past by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }
    val today = Dates.iso(ctl.today)
    val all = buildList {
        ctl.personal.notes.forEach { (d, n) -> add(NoteItem(d, n.text, n.color, work = false, holiday = false)) }
        if (ctl.ready) ctl.current.notes.forEach { (d, n) -> add(NoteItem(d, n.text, n.color ?: if (n.holiday) "#d93025" else "#f9ab00", work = true, holiday = n.holiday)) }
    }
    val list = if (past) all.filter { it.date < today }.sortedByDescending { it.date } else all.filter { it.date >= today }.sortedBy { it.date }
    ScreenScroll {
        ScreenTitle(L("Notas", "Notes", "Notas")) {
            IconAction(KIcons.Plus, L("Nova nota", "New note", "Nueva nota"), { ctl.openQuickNote(today) }, filled = true)
        }
        Segmented(listOf(false, true), past, { if (it) L("Anteriores", "Past", "Anteriores") else L("Próximas", "Upcoming", "Próximas") }, { past = it; shown = PAGE })
        if (list.isEmpty()) {
            Column(Modifier.fillMaxWidth().cardSurface(RoundedCornerShape(22.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (past) L("Nenhuma nota anterior.", "No past notes.", "No hay notas anteriores.")
                    else L("Nenhuma nota daqui para a frente. Anote lembretes do dia, recados ou feriados da escala.", "No notes ahead. Write down reminders for the day, messages or schedule holidays.", "No hay notas próximas. Anote recordatorios del día, recados o festivos del horario."),
                    fontSize = 14.sp, color = c.onSurfaceVar, lineHeight = 20.sp,
                )
                if (!past) AddDashedButton(L("Nova nota", "New note", "Nueva nota")) { ctl.openQuickNote(today) }
            }
        }
        var lastDate = ""
        list.take(shown).forEach { n ->
            if (n.date != lastDate) {
                lastDate = n.date
                val d = Dates.parse(n.date)
                Text(dayLabel(d, ctl.today).let { if (d == ctl.today || d == ctl.today.plusDays(1)) "$it · ${Dates.fmt(d)}" else it }, fontSize = 13.sp, color = if (n.date == today) c.primary else c.onSurfaceVar, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            }
            AgendaRow(
                if (n.holiday) HolidayRed else hexColor(n.color), "",
                when {
                    n.holiday && n.text.isEmpty() -> L("Feriado", "Holiday", "Festivo")
                    n.holiday -> L("Feriado: ", "Holiday: ", "Festivo: ") + n.text
                    else -> n.text
                },
                if (n.work) L("Escala ", "Schedule ", "Horario ") + ctl.current.name else L("Nota pessoal", "Personal note", "Nota personal"),
                { if (n.work) ctl.openNote(n.date) else ctl.openDayNote(n.date) },
                Modifier.cardSurface(RoundedCornerShape(16.dp)),
                icon = if (n.work) KIcons.Flag else KIcons.Notes,
            )
        }
        if (list.size > shown) AppButton(L("Mostrar mais", "Show more", "Mostrar más") + " (${list.size - shown})", { shown += PAGE }, Modifier.fillMaxWidth())
        Spacer(Modifier)
    }
}

private const val PAGE = 60

/** Nova nota: data, texto e onde guardar (pessoal ou na escala ativa). Se a data já tem nota, o texto é acrescentado. */
@Composable
fun QuickNoteSheet(ctl: AppController, date0: String) {
    var date by rememberSaveable { mutableStateOf(date0) }
    var text by rememberSaveable { mutableStateOf("") }
    var toSchedule by rememberSaveable { mutableStateOf(false) }
    AppSheet(ctl::closeSheet, L("Nova nota", "New note", "Nueva nota"), dirty = text.isNotBlank()) {
        Field(L("Nota", "Note", "Nota")) {
            AppTextField(text, { text = it }, placeholder = L("Ex.: Levar documentos, ligar para a escola...", "E.g.: Bring documents, call the school...", "Ej.: Llevar documentos, llamar al colegio..."), maxLength = 500, minLines = 4)
        }
        Field(L("Data", "Date", "Fecha")) { DateField(date, { date = it }) }
        if (ctl.ready) Field(L("Guardar em", "Save in", "Guardar en")) {
            Segmented(listOf(false, true), toSchedule, { if (it) L("Escala", "Schedule", "Horario") + " ${ctl.current.name}" else L("Pessoal", "Personal", "Personal") }, { toSchedule = it })
        }
        val existing = if (toSchedule) ctl.current.notes[date]?.text else ctl.personal.notes[date]?.text
        if (!existing.isNullOrBlank()) Para(L("Esta data já tem uma nota; o texto novo entra numa linha abaixo.", "This date already has a note; the new text goes on a new line.", "Esta fecha ya tiene una nota; el texto nuevo va en una línea debajo."))
        SheetActions {
            AppButton(L("Cancelar", "Cancel", "Cancelar"), ctl::closeSheet, kind = BtnKind.Text)
            AppButton(L("Salvar", "Save", "Guardar"), { ctl.submitQuickNote(date, text, toSchedule) }, kind = BtnKind.Filled, enabled = text.isNotBlank())
        }
    }
}
