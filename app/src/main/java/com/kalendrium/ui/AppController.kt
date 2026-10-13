package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextMeasurer
import com.kalendrium.domain.Agenda
import com.kalendrium.domain.AppData
import com.kalendrium.domain.Category
import com.kalendrium.domain.Repeat
import com.kalendrium.domain.Block
import com.kalendrium.domain.CalendarMode
import com.kalendrium.domain.DayNote
import com.kalendrium.domain.Event
import com.kalendrium.domain.EventKind
import com.kalendrium.domain.Change
import com.kalendrium.domain.Codec
import com.kalendrium.domain.Dates
import com.kalendrium.domain.Engine
import com.kalendrium.domain.Note
import com.kalendrium.domain.Period
import com.kalendrium.domain.Personal
import com.kalendrium.domain.PersonalData
import com.kalendrium.domain.Sanitize
import com.kalendrium.domain.Schedule
import com.kalendrium.domain.ViewMode
import com.kalendrium.ui.theme.AppColors
import com.kalendrium.domain.Ids
import com.kalendrium.domain.I18n
import com.kalendrium.domain.Lang
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class CycleMode { Edit, New, First }

/** Folhas inferiores (os <dialog> do PWA). Só uma fica aberta por vez; mensagens aparecem por cima. */
sealed interface Sheet {
    data class Cycle(val target: Schedule, val mode: CycleMode) : Sheet
    data class ChangeEdit(val date: String, val existing: Change?) : Sheet
    data class NoteEdit(val date: String) : Sheet
    data class PeriodEdit(val existing: Period?) : Sheet
    data class Share(val start: String, val end: String) : Sheet
    data class Settings(val tab: String) : Sheet
    data class EventEdit(val date: String, val existing: Event?, val start: String? = null) : Sheet
    data class DayNoteEdit(val date: String) : Sheet
    /** Nova nota com escolha de data e de onde guardar (pessoal ou escala). */
    data class QuickNote(val date: String) : Sheet
    data object Search : Sheet
    /** Próximos lembretes (sino da tela Hoje). */
    data object Upcoming : Sheet
}

/** Abas da barra inferior (redesign 6.0). O botão "+" do meio cria um evento. */
enum class Tab { TODAY, CALENDAR, SCHEDULES, NOTES }

/** Filtro "Escala" da legenda do calendário (as demais chaves são as das categorias). */
const val FILTER_SHIFT = "escala"

/** Campos da folha de evento do calendário pessoal. */
data class EventForm(
    val title: String,
    val kind: EventKind,
    val date: String,
    val allDay: Boolean,
    val start: String,
    val end: String,
    val description: String,
    val color: String,
    val remind: Int,
    val category: Category = Category.PERSONAL,
    val repeat: Repeat = Repeat.NONE,
    val until: String = "",
    val location: String = "",
)

/** Diálogo próprio de mensagem/confirmação (substitui alert/confirm do PWA). */
class Msg(val text: String, val ok: String, val cancel: String?, val result: CompletableDeferred<Boolean>)

data class BlockRow(val w: String, val o: String, val name: String, val orig: Int)

data class CycleForm(
    val rows: List<BlockRow>,
    val name: String,
    val color: String,
    val anchor: String,
    val keepPast: Boolean,
    val from: String,
    /** Horário do turno ("HH:mm"; os dois vazios = sem horário). */
    val shiftStart: String = "",
    val shiftEnd: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
private fun serialIo(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

/**
 * Estado e regras do app — port direto dos handlers do app.js.
 * Não depende do Android: tudo que é da plataforma passa por [Platform].
 * Disco em `io` (fila serial) e desenho de imagem em `work`; o estado da tela muda na thread de `scope`.
 */
class AppController(
    private val platform: Platform,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = serialIo(),
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    var data by mutableStateOf(AppData.default(Dates.iso(platform.today())))
        private set
    var today by mutableStateOf(platform.today())
        private set
    /** Primeiro dia do mês exibido. */
    var view by mutableStateOf(platform.today().withDayOfMonth(1))
        private set
    var sheet by mutableStateOf<Sheet?>(null)
        private set
    var monthPicker by mutableStateOf(false)
    var lastBackup by mutableStateOf<String?>(null)
        private set
    val messages = mutableStateListOf<Msg>()

    /** Contexto atual: calendário pessoal ou escala de trabalho. Os dois compartilham a tela e o mês exibido. */
    var mode by mutableStateOf(CalendarMode.PERSONAL)
        private set
    /** Dados do calendário pessoal (arquivo próprio, separado das escalas). */
    var personal by mutableStateOf(PersonalData())
        private set
    /** Dia selecionado (Pessoal e Work): base das visualizações diária, semanal e mensal + diária. */
    var selected by mutableStateOf(platform.today())
        private set
    /** Visualização escolhida no botão de grade (mensal é o padrão). Vale para os dois contextos. */
    var viewMode by mutableStateOf(ViewMode.DEFAULT)
        private set
    /** Menu de visualizações aberto. */
    var viewMenu by mutableStateOf(false)

    /** Aba da barra inferior. O app sempre abre em Hoje. */
    var tab by mutableStateOf(Tab.TODAY)
        private set
    /** Filtros desligados na legenda do calendário: [FILTER_SHIFT] e chaves de [Category]. */
    var hidden by mutableStateOf(emptySet<String>())
        private set
    /** Nome usado na saudação da tela Hoje (só neste aparelho; não vai no backup). */
    var userName by mutableStateOf("")
        private set
    /** Padrão em simulação na aba Escalas (null = nenhum). */
    var simulation by mutableStateOf<com.kalendrium.domain.Block?>(null)
        private set

    /** false enquanto os arquivos são lidos (a tela fica só com o fundo, por alguns milissegundos). */
    var loaded by mutableStateOf(false)
        private set
    /** Hora atual, atualizada a cada minuto por [tick] enquanto o app está visível. */
    var clock by mutableStateOf(platform.now())
        private set
    /** Mensagem de "aguarde" enquanto gera imagem ou PDF (null = nada em andamento). */
    var busy by mutableStateOf<String?>(null)
        private set
    /** Existe cópia dos dados anteriores à última importação/restauração. */
    var previousAvailable by mutableStateOf(false)
        private set
    /** Notificações bloqueadas no aparelho (lembretes não tocariam). */
    var notifBlocked by mutableStateOf(false)
        private set

    /** Idioma escolhido em Ajustes: "system" (segue o aparelho), "pt", "en" ou "es". */
    var langPref by mutableStateOf(I18n.SYSTEM)
        private set
    /** Idioma em uso. A tela é recomposta inteira quando ele muda. */
    var lang by mutableStateOf(I18n.resolve(I18n.SYSTEM, platform.systemLocale()).also { I18n.lang = it })
        private set

    /** Usado para desenhar a imagem compartilhada e o PDF (fornecido pela UI). */
    var textMeasurer: TextMeasurer? = null

    private val todayIso get() = Dates.iso(platform.today())

    fun now(): java.time.LocalTime = clock.toLocalTime()

    private val store = Storage(platform, scope, io, onWriteFailed = { warnOnce(WRITE_FAILED) }, onBlocked = { warnOnce(READ_FAILED) })

    init {
        scope.launch {
            val r = withContext(io) { readAll() }
            data = r.work.data
            personal = r.personal.data
            lastBackup = r.meta
            // quem já usava a escala continua abrindo nela; instalações novas começam no Pessoal
            mode = CalendarMode.of(r.mode) ?: if (data.initialized) CalendarMode.WORK else CalendarMode.PERSONAL
            viewMode = ViewMode.of(r.view)
            previousAvailable = r.previous
            langPref = r.langPref
            lang = I18n.lang
            userName = r.name
            loaded = true
            // redesign 6.0: quem estava na paleta padrão antiga passa uma vez para a paleta nova (dá para voltar em Ajustes)
            // (instalação nova já nasce na paleta nova e não grava nada aqui)
            if (r.design == null && r.workStored && !r.workFailed) {
                if (data.palette == "material") save(data.copy(palette = AppData.DEFAULT_PALETTE))
                store.persist(DESIGN_KEY) { DESIGN_VERSION }
            }
            if (r.workFailed || r.personalFailed) notify(READ_FAILED)
            // "cópia mantida" só quando a cópia foi de fato gravada; sem ela a chave fica bloqueada (READ_FAILED acima)
            r.work.warning?.let { notify(if (r.work.corruptRaw.isNullOrBlank() || r.workFailed) it else "$it " + L("Uma cópia dos dados originais foi mantida no aparelho.", "A copy of the original data was kept on the device.", "Se guardó una copia de los datos originales en el dispositivo.")) }
            r.personal.warning?.let { notify(if (r.personalFailed) it else "$it " + L("Uma cópia dos dados originais foi mantida no aparelho.", "A copy of the original data was kept on the device.", "Se guardó una copia de los datos originales en el dispositivo.")) }
            notifBlocked = platform.notificationsBlocked()
            syncReminders()
        }
    }

    private class Initial(
        val work: Codec.Loaded, val workFailed: Boolean,
        val personal: Personal.Loaded, val personalFailed: Boolean,
        val meta: String?, val mode: String?, val previous: Boolean, val langPref: String, val view: String?,
        val name: String, val design: String?, val workStored: Boolean,
    )

    /** Leitura inicial (fora da thread principal). Dados inválidos são corrigidos e o original vai para o .bak. */
    private fun readAll(): Initial {
        // o idioma vem primeiro: os avisos e os nomes padrão abaixo já saem nele
        val langPref = I18n.pref(store.readSafe(I18n.PREF_KEY))
        I18n.lang = I18n.resolve(langPref, platform.systemLocale())
        val workRaw = store.readSafe(Codec.KEY)
        val work = Codec.load(workRaw, todayIso)
        // sem a cópia do original, a versão corrigida não pode gravar por cima dele
        work.corruptRaw?.let { if (!store.keepBak(BAK, BAK2, it)) store.block(Codec.KEY) }
        val personal = Personal.loadChecked(store.readSafe(Personal.KEY))
        personal.corruptRaw?.let { if (!store.keepBak(PERSONAL_BAK, PERSONAL_BAK2, it)) store.block(Personal.KEY) }
        return Initial(
            work, store.failed(Codec.KEY), personal, store.failed(Personal.KEY),
            store.readSafe(META), store.readSafe(Personal.MODE_KEY), hasPrevious(), langPref, store.readSafe(ViewMode.KEY),
            cleanName(store.readSafe(NAME_KEY)), store.readSafe(DESIGN_KEY), workRaw != null,
        )
    }

    private fun hasPrevious(): Boolean =
        store.readSafe(BAK)?.let { Codec.importBackup(it, todayIso) } != null ||
            store.readSafe(PERSONAL_BAK)?.let { Personal.decodeChecked(Codec.parseOrNull(it)) } != null

    val ready get() = data.initialized
    val current: Schedule get() = data.current

    // ---------- mensagens ----------
    fun notify(text: String) {
        messages.add(Msg(text, "OK", null, CompletableDeferred()))
    }

    /** Avisa sem empilhar a mesma mensagem várias vezes (ex.: várias gravações falhando seguidas). */
    private fun warnOnce(text: String) {
        if (messages.none { it.text == text }) notify(text)
    }

    suspend fun confirm(text: String): Boolean {
        val m = Msg(text, L("Confirmar", "Confirm", "Confirmar"), L("Cancelar", "Cancel", "Cancelar"), CompletableDeferred())
        messages.add(m)
        return m.result.await()
    }

    fun answer(m: Msg, value: Boolean) {
        messages.remove(m)
        m.result.complete(value)
    }

    // ---------- persistência ----------
    /** Atualiza a tela na hora e grava em segundo plano. */
    private fun save(next: AppData) {
        val before = data.current
        data = next
        store.persist(Codec.KEY) { Codec.encode(next) }
        // "Nas minhas folgas" depende da escala ativa
        if (next.current != before && personal.repeating.any { it.repeat == Repeat.OFF_DAYS && it.remind >= 0 }) syncReminders()
    }

    // ---------- agenda (pessoal + escala) ----------
    /** Dia livre para "Nas minhas folgas" (folga ou período na escala ativa). */
    val isOff: (LocalDate) -> Boolean get() {
        val sch = if (ready) current else null
        return { d -> Agenda.isOff(d, sch) }
    }

    /** Eventos do dia, com as ocorrências dos que se repetem. */
    fun eventsOn(d: LocalDate): List<Event> = Agenda.eventsOn(personal, d, isOff)

    /** Evento original de uma ocorrência (editar uma ocorrência edita a série). */
    fun baseOf(e: Event): Event = personal.events.firstOrNull { it.id == e.id } ?: e

    private fun syncReminders() = platform.syncReminders(Agenda.reminderEvents(personal, today, isOff))

    /** Próximos lembretes (sino da tela Hoje): ocorrências com lembrete nos próximos `days` dias, pelo horário do aviso. */
    fun upcomingReminders(days: Long = 7): List<Pair<Event, LocalDateTime>> {
        val now = clock
        val end = now.plusDays(days)
        return Agenda.reminderEvents(personal, today, isOff, days).mapNotNull { e -> e.reminderAt()?.takeIf { it.isAfter(now) && !it.isAfter(end) }?.let { e to it } }
            .sortedBy { it.second }
    }

    // ---------- abas, filtros e nome ----------
    fun selectTab(t: Tab) {
        if (t == tab) return
        sheet = null
        viewMenu = false
        simulation = null
        tab = t
    }

    fun toggleFilter(key: String) { hidden = if (key in hidden) hidden - key else hidden + key }

    fun setName(name: String) {
        val n = cleanName(name)
        userName = n
        store.persist(NAME_KEY) { n }
    }

    private fun cleanName(raw: String?): String = raw?.trim()?.replace(Regex("\\s+"), " ")?.take(30) ?: ""

    fun openSearch() { sheet = Sheet.Search }
    fun openUpcoming() { sheet = Sheet.Upcoming }
    fun openQuickNote(date: String = Dates.iso(selected)) { sheet = Sheet.QuickNote(date) }

    /** Nota criada pela tela Notas ou pelo atalho "Nota rápida": pessoal ou da escala ativa. */
    fun submitQuickNote(date: String, text: String, toSchedule: Boolean) {
        if (!Dates.isDate(date)) { notify(L("Informe uma data válida.", "Enter a valid date.", "Indique una fecha válida.")); return }
        if (text.isBlank()) { notify(L("Escreva a nota.", "Write the note.", "Escriba la nota.")); return }
        if (toSchedule && ready) {
            val old = current.notes[date]
            submitNote(date, listOfNotNull(old?.text?.takeIf { it.isNotBlank() }, text.trim()).joinToString("\n").take(500), old?.holiday ?: false, old?.color ?: "#f9ab00", old?.align ?: "center")
        } else {
            val old = personal.notes[date]
            submitDayNote(date, listOfNotNull(old?.text?.takeIf { it.isNotBlank() }, text.trim()).joinToString("\n"), old?.color ?: "#f9ab00")
        }
    }

    // ---------- simulação de padrão (aba Escalas) ----------
    fun simulate(b: com.kalendrium.domain.Block?) { simulation = if (b == null || (current.blocks.size == 1 && current.blocks[0].w == b.w && current.blocks[0].o == b.o)) null else b }

    /** Escala como ficaria com o padrão simulado (null = sem simulação). */
    val simulated: Schedule? get() = simulation?.let { if (ready) Agenda.simulate(current, listOf(it), Agenda.simulationStart(today, current)) else null }

    /** Aplica o padrão simulado a partir de amanhã, preservando os dias anteriores. */
    fun applySimulation() = scope.launch {
        val b = simulation ?: return@launch
        if (!ready) return@launch
        val sc = current
        val from = Agenda.simulationStart(today, sc)
        val f = Dates.iso(from)
        val lost = sc.changes.count { it.date >= f }
        val msg = L("Aplicar o padrão ${b.w}×${b.o} na escala \"${sc.name}\" a partir de ${Dates.fmt(from)}? Os dias anteriores continuam como estão.", "Apply the ${b.w}×${b.o} pattern to \"${sc.name}\" from ${Dates.fmt(from)}? Earlier days stay as they are.", "¿Aplicar el patrón ${b.w}×${b.o} al horario \"${sc.name}\" desde el ${Dates.fmt(from)}? Los días anteriores se mantienen.") +
            (if (lost > 0) L(" $lost mudança(s) a partir dessa data serão removidas.", " $lost change(s) from that date on will be removed.", " Se eliminarán $lost cambio(s) desde esa fecha.") else "")
        if (!confirm(msg)) return@launch
        simulation = null
        saveSchedule(Agenda.simulate(sc, listOf(b), from))
    }

    private fun saveSchedule(s: Schedule) = save(data.replace(s))

    // ---------- navegação ----------
    fun refreshToday() {
        val t = platform.today()
        if (t != today) today = t
    }

    /** Chamado a cada minuto com o app visível: atualiza a hora do resumo e vira o dia à meia-noite. */
    fun tick() {
        clock = platform.now()
        refreshToday()
    }

    /** Chamado ao voltar ao app e depois da resposta do pedido de permissão. */
    fun onResume() {
        tick()
        notifBlocked = platform.notificationsBlocked()
        // o idioma do aparelho pode ter mudado com o app em segundo plano
        if (loaded) applyLang()
    }

    // ---------- idioma ----------
    /** Idioma que "Sistema" usaria agora. */
    val systemLang: Lang get() = I18n.resolve(I18n.SYSTEM, platform.systemLocale())

    /** Escolha em Ajustes › Idiomas. */
    fun setLanguage(pref: String) {
        val p = I18n.pref(pref)
        langPref = p
        store.persist(I18n.PREF_KEY) { p }
        applyLang()
    }

    private fun applyLang() {
        val l = I18n.resolve(langPref, platform.systemLocale())
        if (l == lang && l == I18n.lang) return
        I18n.lang = l
        lang = l
        // os textos das notificações são montados ao agendar
        syncReminders()
    }

    fun prevMonth() { showMonth(view.minusMonths(1)) }
    fun nextMonth() { showMonth(view.plusMonths(1)) }
    fun goToday() { refreshToday(); view = today.withDayOfMonth(1); selected = today }
    fun goTo(year: Int, month: Int) {
        if (year in 1900..2200 && month in 1..12) showMonth(LocalDate.of(year, month, 1))
    }

    // ---------- visualizações ----------
    /** Escolha no menu de grade. A escolha fica gravada e vale para Pessoal e Work. */
    fun selectView(v: ViewMode) {
        viewMenu = false
        if (v == viewMode) return
        viewMode = v
        store.persist(ViewMode.KEY) { v.key }
    }

    /** Seta "anterior" da visualização atual: dia, semana, mês ou ano. */
    fun prev() = step(-1)
    /** Seta "próximo" da visualização atual. */
    fun next() = step(1)

    private fun step(n: Long) {
        when (viewMode) {
            ViewMode.DAY -> showDay(selected.plusDays(n))
            ViewMode.WEEK -> showDay(selected.plusWeeks(n))
            ViewMode.YEAR -> if (view.year + n in 1900..2200) showMonth(view.plusYears(n))
            else -> showMonth(view.plusMonths(n))
        }
    }

    /** Seleciona o dia e leva o mês exibido junto. */
    fun showDay(d: LocalDate) {
        if (d.year !in 1900..2200) return
        selected = d
        view = d.withDayOfMonth(1)
    }

    /** Abre a visualização diária de um dia (toque na semana ou no mês). */
    fun openDay(d: LocalDate) {
        showDay(d)
        selectView(ViewMode.DAY)
    }

    /** Abre a visualização mensal de um mês (toque na visualização anual). */
    fun openMonth(year: Int, month: Int) {
        goTo(year, month)
        selectView(ViewMode.MONTH)
    }

    /**
     * Toque num dia da grade do mês.
     * Mensal: no Pessoal abre o dia; no Work abre a nota (como antes).
     * Mensal + diária: o primeiro toque seleciona e o segundo cria um evento (Pessoal) ou abre a nota (Work).
     */
    fun tapDay(date: String) {
        val d = Dates.parse(date)
        when {
            viewMode == ViewMode.MONTH && mode == CalendarMode.PERSONAL -> openDay(d)
            viewMode == ViewMode.MONTH -> openNote(date)
            d != selected -> selected = d
            mode == CalendarMode.PERSONAL -> openEvent(date)
            else -> openNote(date)
        }
    }

    /** Troca o mês exibido; no Pessoal, a seleção acompanha (hoje, se estiver no mês, senão o dia 1). */
    private fun showMonth(v: LocalDate) {
        view = v
        if (selected.year != v.year || selected.monthValue != v.monthValue) {
            selected = if (today.year == v.year && today.monthValue == v.monthValue) today else v
        }
    }

    // ---------- modo ----------
    /** Troca de contexto na mesma tela, mantendo o mês exibido. */
    fun selectMode(m: CalendarMode) {
        if (m == mode) return
        sheet = null
        monthPicker = false
        mode = m
        store.persist(Personal.MODE_KEY) { m.key }
    }

    fun closeSheet() { sheet = null }
    fun openSettings(tab: String = "themes") { sheet = Sheet.Settings(tab) }

    // ---------- escalas ----------
    fun openCycle() {
        if (!ready) return
        sheet = Sheet.Cycle(current, CycleMode.Edit)
    }

    fun firstSchedule() {
        if (data.initialized) return
        sheet = Sheet.Cycle(
            Schedule("first", L("Minha escala", "My schedule", "Mi horario"), "#65558f", todayIso, listOf(Block(4, 1)), emptyList()),
            CycleMode.First,
        )
    }

    fun newSchedule() {
        if (data.groups.size >= AppData.MAX_GROUPS) { notify(L("Limite de ${AppData.MAX_SCHEDULES} escalas.", "Limit of ${AppData.MAX_SCHEDULES} schedules.", "Límite de ${AppData.MAX_SCHEDULES} horarios.")); return }
        val k = data.groups.size
        val id = Ids.newId("g", 12) { id -> data.schedules.any { it.id == id } }
        sheet = Sheet.Cycle(
            Schedule(id, L("Escala ", "Schedule ", "Horario ") + (66 + minOf(k, 24)).toChar(), PAL[k % PAL.size], todayIso, listOf(Block(4, 1)), emptyList()),
            CycleMode.New,
        )
    }

    fun selectSchedule(s: Schedule) {
        save(data.copy(active = if (s.isMain) "" else s.id))
    }

    /** Data sugerida para "Nova escala vale a partir de". */
    fun defaultFrom(s: Schedule): String {
        val lastTo = Engine.lastTo(s)
        var from = todayIso
        if (lastTo.isNotEmpty() && from <= lastTo) from = Dates.iso(Dates.parse(lastTo).plusDays(1))
        return from
    }

    /** Salvar do editor de escala. Devolve true quando a folha deve fechar. */
    suspend fun submitCycle(mode: CycleMode, sc: Schedule, f: CycleForm): Boolean {
        val blocks = Sanitize.cleanBlocks(f.rows.map { Triple(it.w, it.o, it.name) })
        if (blocks == null) { notify(L("Use de 1 a 99 dias de trabalho e de folga em cada bloco (ciclo de até 1000 dias).", "Use 1 to 99 work days and days off in each block (cycle of up to 1000 days).", "Use de 1 a 99 días de trabajo y libres en cada bloque (ciclo de hasta 1000 días).")); return false }
        if (!Dates.isDate(f.anchor)) { notify(L("Informe uma data de referência válida.", "Enter a valid reference date.", "Indique una fecha de referencia válida.")); return false }
        val nm = Sanitize.cleanName(f.name, "")
        val col = Sanitize.cleanColor(f.color, "")
        if (nm.isEmpty()) { notify(L("Informe o nome da escala.", "Enter the schedule name.", "Indique el nombre del horario.")); return false }
        val hasStart = f.shiftStart.isNotEmpty()
        val hasEnd = f.shiftEnd.isNotEmpty()
        if (hasStart != hasEnd || (hasStart && (!Personal.isTime(f.shiftStart) || !Personal.isTime(f.shiftEnd)))) {
            notify(L("Informe o início e o fim do turno, ou deixe os dois em branco.", "Enter the shift start and end, or leave both empty.", "Indique el inicio y el fin del turno, o deje ambos vacíos.")); return false
        }
        if (mode == CycleMode.Edit && f.anchor == sc.anchor && blocks == sc.blocks) {
            // só nome/cor/turno: não mexe no histórico
            closeSheet()
            saveSchedule(sc.copy(name = nm, color = col.ifEmpty { sc.color }, shiftStart = f.shiftStart, shiftEnd = f.shiftEnd))
            return true
        }
        val keep = mode == CycleMode.Edit && f.keepPast
        val lastTo = Engine.lastTo(sc)
        if (keep) {
            if (!Dates.isDate(f.from)) { notify(L("Informe a data a partir da qual a nova escala passa a valer.", "Enter the date from which the new schedule applies.", "Indique la fecha a partir de la cual se aplica el nuevo horario.")); return false }
            if (lastTo.isNotEmpty() && f.from <= lastTo) { notify(L("Escolha uma data posterior a ${Dates.fmt(lastTo)}, quando começa o período atual.", "Choose a date after ${Dates.fmt(lastTo)}, when the current period starts.", "Elija una fecha posterior al ${Dates.fmt(lastTo)}, cuando empieza el período actual.")); return false }
        } else if (sc.history.isNotEmpty() && !confirm(L("Recalcular o calendário inteiro descarta ${sc.history.size} período(s) preservado(s). Continuar?", "Recalculating the whole calendar discards ${sc.history.size} preserved period(s). Continue?", "Recalcular todo el calendario descarta ${sc.history.size} período(s) conservado(s). ¿Continuar?"))) return false
        val r = Engine.applyCycleEdit(sc, blocks, f.anchor, keep, f.from, f.rows.map { it.orig })
        val updated = r.schedule.copy(name = nm, color = col.ifEmpty { sc.color }, shiftStart = f.shiftStart, shiftEnd = f.shiftEnd)
        closeSheet()
        when (mode) {
            CycleMode.First -> {
                val main = data.main.copy(anchor = updated.anchor, blocks = updated.blocks, changes = updated.changes, history = updated.history, name = updated.name, color = updated.color, shiftStart = updated.shiftStart, shiftEnd = updated.shiftEnd)
                save(data.copy(initialized = true, schedules = listOf(main) + data.groups))
            }
            CycleMode.New -> save(data.copy(schedules = data.schedules + updated, active = updated.id))
            CycleMode.Edit -> saveSchedule(updated)
        }
        if (r.moved > 0 || r.dropped > 0) notify(L("${r.moved} mudança(s) foram reposicionadas para acompanhar os blocos editados e ${r.dropped} foram removidas (o bloco não existe mais). Revise em \"Mudanças da escala\".", "${r.moved} change(s) were moved to follow the edited blocks and ${r.dropped} were removed (the block no longer exists). Review them in \"Schedule changes\".", "${r.moved} cambio(s) se reubicaron para seguir los bloques editados y ${r.dropped} se eliminaron (el bloque ya no existe). Revíselos en \"Cambios del horario\"."))
        return true
    }

    fun canDelete(mode: CycleMode, sc: Schedule) = mode == CycleMode.Edit && !(sc.isMain && data.groups.isEmpty())

    fun deleteSchedule(sc: Schedule) = scope.launch {
        if (!confirm(L("Excluir a escala \"${sc.name}\"?", "Delete the schedule \"${sc.name}\"?", "¿Eliminar el horario \"${sc.name}\"?"))) return@launch
        val next = if (sc.isMain) {
            val n = data.groups.firstOrNull() ?: return@launch
            // promove a próxima escala a principal
            data.copy(schedules = listOf(n.copy(id = "")) + data.groups.drop(1), active = "")
        } else data.copy(schedules = data.schedules.filter { it.id != sc.id }, active = "")
        closeSheet()
        save(next)
    }

    // ---------- mudanças ----------
    fun openChange(date: String = todayIso, c: Change? = null) { sheet = Sheet.ChangeEdit(date, c) }

    suspend fun submitChange(editing: String?, date: String, pos: Int): Boolean {
        val sc = current
        if (!Dates.isDate(date)) return false
        val lt = Engine.lastTo(sc)
        if (lt.isNotEmpty() && date < lt) { notify(L("Esta data pertence à escala preservada anterior a ${Dates.fmt(lt)}. Escolha ${Dates.fmt(lt)} ou uma data posterior.", "This date belongs to the preserved schedule before ${Dates.fmt(lt)}. Choose ${Dates.fmt(lt)} or a later date.", "Esta fecha pertenece al horario conservado anterior al ${Dates.fmt(lt)}. Elija el ${Dates.fmt(lt)} o una fecha posterior.")); return false }
        if (sc.changes.any { it.date == date && it.date != editing } && !confirm(L("Já existe uma mudança em ${Dates.fmt(date)}. Substituir?", "There is already a change on ${Dates.fmt(date)}. Replace it?", "Ya hay un cambio el ${Dates.fmt(date)}. ¿Reemplazarlo?"))) return false
        val changes = sc.changes.filter { it.date != editing && it.date != date } + Change(date, pos)
        closeSheet()
        saveSchedule(sc.copy(changes = changes))
        return true
    }

    fun deleteChange(editing: String) = scope.launch {
        if (!confirm(L("Excluir a mudança de ${Dates.fmt(editing)}? O calendário volta a seguir o ciclo anterior a partir dessa data.", "Delete the change on ${Dates.fmt(editing)}? The calendar goes back to the previous cycle from that date.", "¿Eliminar el cambio del ${Dates.fmt(editing)}? El calendario vuelve a seguir el ciclo anterior a partir de esa fecha."))) return@launch
        closeSheet()
        saveSchedule(current.copy(changes = current.changes.filter { it.date != editing }))
    }

    // ---------- notas e feriados ----------
    fun openNote(date: String) { sheet = Sheet.NoteEdit(date) }

    fun submitNote(date: String, textIn: String, holiday: Boolean, color: String, align: String) {
        val text = textIn.trim()
        val sc = current
        if ((text.isNotEmpty() || holiday) && date !in sc.notes && sc.notes.size >= AppData.MAX_NOTES) {
            notify(L("Limite de ${AppData.MAX_NOTES} notas nesta escala. Exclua notas antigas para criar novas.", "Limit of ${AppData.MAX_NOTES} notes in this schedule. Delete old notes to create new ones.", "Límite de ${AppData.MAX_NOTES} notas en este horario. Elimine notas antiguas para crear nuevas.")); return
        }
        val notes = LinkedHashMap(sc.notes)
        if (text.isEmpty() && !holiday) notes.remove(date) else notes[date] = Note(text, holiday, color, align)
        closeSheet()
        saveSchedule(sc.copy(notes = notes))
    }

    fun deleteNote(date: String) = scope.launch {
        if (confirm(L("Excluir a nota desta data?", "Delete the note for this date?", "¿Eliminar la nota de esta fecha?"))) {
            closeSheet()
            saveSchedule(current.copy(notes = current.notes - date))
        }
    }

    // ---------- períodos ----------
    fun openPeriod(p: Period? = null) { sheet = Sheet.PeriodEdit(p) }

    fun submitPeriod(editing: String?, start: String, end: String, title: String, color: String): Boolean {
        if (!Dates.isDate(start) || !Dates.isDate(end)) { notify(L("Informe datas válidas.", "Enter valid dates.", "Indique fechas válidas.")); return false }
        if (end < start) { notify(L("A data final não pode ser anterior à data inicial.", "The end date can't be before the start date.", "La fecha final no puede ser anterior a la inicial.")); return false }
        if (editing == null && current.periods.size >= AppData.MAX_PERIODS) {
            notify(L("Limite de ${AppData.MAX_PERIODS} períodos nesta escala. Exclua períodos antigos para criar novos.", "Limit of ${AppData.MAX_PERIODS} periods in this schedule. Delete old periods to create new ones.", "Límite de ${AppData.MAX_PERIODS} períodos en este horario. Elimine períodos antiguos para crear nuevos.")); return false
        }
        val id = editing ?: Ids.newId("p", 24) { id -> current.periods.any { it.id == id } }
        val item = Period(id, start, end, title.trim().take(80), color)
        val sc = current
        closeSheet()
        saveSchedule(sc.copy(periods = Sanitize.cleanPeriods(sc.periods.filter { it.id != item.id } + item)))
        return true
    }

    fun deletePeriod(id: String) = scope.launch {
        val p = current.periods.firstOrNull { it.id == id }
        if (confirm(L("Excluir este período", "Delete this period", "¿Eliminar este período") + (if (!p?.title.isNullOrEmpty()) " (${p!!.title})" else "") + "?")) {
            closeSheet()
            saveSchedule(current.copy(periods = current.periods.filter { it.id != id }))
        }
    }

    // ---------- temas ----------
    fun setTheme(t: String) { save(data.copy(theme = t)) }
    fun setPalette(p: String) {
        save(data.copy(palette = p))
        // escolha feita à mão: a migração da 6.0 não troca mais a paleta
        store.persist(DESIGN_KEY) { DESIGN_VERSION }
    }
    fun setDynamicColor(on: Boolean) { save(data.copy(dynamicColor = on)) }
    fun setHideRecents(on: Boolean) { save(data.copy(hideRecents = on)) }

    // ---------- backup ----------
    fun backupInfo(): String {
        val t = lastBackup
        val dt = t?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        return if (dt != null) L("Último backup exportado", "Last backup exported", "Última copia exportada") + ": ${Dates.fmt(dt.toLocalDate())}, ${dt.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}."
        else L("Você ainda não exportou nenhum backup neste aparelho.", "You haven't exported a backup on this device yet.", "Aún no ha exportado ninguna copia en este dispositivo.")
    }

    fun exportBackup() = scope.launch {
        val d = data
        val p = personal
        val bytes = withContext(work) { Codec.encodeBackup(d, p).toByteArray() }
        when (platform.saveFile("kalendrium-backup.json", "application/json", bytes)) {
            true -> {
                val now = platform.now().truncatedTo(ChronoUnit.SECONDS).toString()
                lastBackup = now
                store.persist(META) { now }
            }
            false -> notify(L("Não foi possível salvar o backup.", "Couldn't save the backup.", "No se pudo guardar la copia."))
            null -> Unit
        }
    }

    /** Backup lido e validado, pronto para gravar. */
    private class Parsed(val work: AppData, val personal: Personal.Decoded?)

    private fun parseBackup(text: String): Parsed? {
        val json = Codec.parseOrNull(text) ?: return null
        val work = Sanitize.sanitize(json, strict = true, today = todayIso) ?: return null
        val section = (json as? kotlinx.serialization.json.JsonObject)?.get("personal")
        // a parte pessoal, quando existe, precisa ter o formato certo; itens inválidos são contados e informados
        val personal = if (section == null) null else Personal.decodeChecked(section) ?: return null
        return Parsed(work, personal)
    }

    fun importBackup() = scope.launch {
        val text = when (val r = platform.openTextFile(MAX_IMPORT_BYTES)) {
            FileRead.Cancelled -> return@launch
            FileRead.Invalid -> null
            FileRead.TooLarge -> { notify(L("O arquivo passa do limite de ${MAX_IMPORT_BYTES / 1_000_000} MB e não foi importado.", "The file is over the ${MAX_IMPORT_BYTES / 1_000_000} MB limit and wasn't imported.", "El archivo supera el límite de ${MAX_IMPORT_BYTES / 1_000_000} MB y no se importó.")); return@launch }
            FileRead.Failed -> { notify(L("Não foi possível abrir o arquivo. Nada foi alterado.", "Couldn't open the file. Nothing was changed.", "No se pudo abrir el archivo. No se cambió nada.")); return@launch }
            is FileRead.Ok -> r.text
        }
        val parsed = text?.let { withContext(work) { parseBackup(it) } }
        if (parsed == null) { notify(L("Backup inválido.", "Invalid backup.", "Copia no válida.")); return@launch }   // nada foi alterado nem gravado
        val p = parsed.personal
        val what = if (p != null) L("TODOS os dados atuais (escalas, eventos, notas, períodos e ajustes)", "ALL current data (schedules, events, notes, periods and settings)", "TODOS los datos actuales (horarios, eventos, notas, períodos y ajustes)")
        else L("os dados da escala de trabalho (escalas, notas, períodos e ajustes). O calendário pessoal não está neste backup e será mantido", "the work schedule data (schedules, notes, periods and settings). The personal calendar isn't in this backup and will be kept", "los datos del horario de trabajo (horarios, notas, períodos y ajustes). El calendario personal no está en esta copia y se mantendrá")
        val dropped = if (p != null && p.dropped > 0) L("\n\nAtenção: ${p.droppedText()} deste arquivo estão inválidos e não serão importados.", "\n\nWarning: ${p.droppedText()} in this file are invalid and won't be imported.", "\n\nAtención: ${p.droppedText()} de este archivo no son válidos y no se importarán.") else ""
        val unreadable = if (store.failed(Codec.KEY) || (p != null && store.failed(Personal.KEY))) L("\n\nOs dados salvos atuais não puderam ser lidos e serão substituídos.", "\n\nThe current saved data couldn't be read and will be replaced.", "\n\nLos datos guardados actuales no se pudieron leer y serán reemplazados.") else ""
        if (!confirm(L("Importar este backup substitui $what. Uma cópia dos dados atuais fica guardada no aparelho e pode ser restaurada em Ajustes › Dados.$dropped$unreadable\n\nContinuar?", "Importing this backup replaces $what. A copy of the current data is kept on the device and can be restored in Settings › Data.$dropped$unreadable\n\nContinue?", "Importar esta copia reemplaza $what. Se guarda una copia de los datos actuales en el dispositivo y se puede restaurar en Ajustes › Datos.$dropped$unreadable\n\n¿Continuar?"))) return@launch
        if (replaceAll(parsed.work, p?.data)) notify(L("Backup importado.", "Backup imported.", "Copia importada."))
    }

    /**
     * Troca os dados atuais por `work` (e `pers`, se houver) guardando os atuais nos .bak.
     * Só altera a tela quando todas as gravações dão certo; se a segunda falhar, a primeira é desfeita.
     */
    private suspend fun replaceAll(work: AppData?, pers: PersonalData?): Boolean {
        val curWork = data
        val curPers = personal
        val workFailed = store.failed(Codec.KEY)
        val failMsg = L("Os dados são válidos, mas não puderam ser gravados. Seus dados anteriores foram mantidos.", "The data is valid but couldn't be saved. Your previous data was kept.", "Los datos son válidos, pero no se pudieron guardar. Se mantuvieron sus datos anteriores.")
        // codificação fora da thread principal (até 5.000 eventos + 5.000 notas)
        val prevWork = withContext(io) { Codec.encode(curWork) }
        // a confirmação promete uma cópia dos dados atuais: sem ela, nada é substituído
        val bakOk = withContext(io) {
            (work == null || workFailed || store.keepBak(BAK, BAK2, prevWork)) &&
                (pers == null || store.failed(Personal.KEY) || store.keepBak(PERSONAL_BAK, PERSONAL_BAK2, Personal.encode(curPers)))
        }
        if (!bakOk) { notify(failMsg); return false }
        if (work != null && !store.persistNow(Codec.KEY) { Codec.encode(work) }) { notify(failMsg); return false }
        if (pers != null && !store.persistNow(Personal.KEY) { Personal.encode(pers) }) {
            // desfaz a escala para não deixar a restauração pela metade
            if (work != null && !workFailed) store.persistNow(Codec.KEY) { prevWork }
            notify(failMsg)
            return false
        }
        if (work != null) data = work
        if (pers != null) {
            personal = pers
            syncReminders()
        }
        previousAvailable = withContext(io) { hasPrevious() }
        return true
    }

    /** Volta aos dados guardados antes da última importação (ou à cópia feita ao corrigir dados inválidos). */
    fun restorePrevious() = scope.launch {
        val (w, p) = withContext(io) {
            val w = store.readSafe(BAK)?.let { Codec.importBackup(it, todayIso) }
            val p = store.readSafe(PERSONAL_BAK)?.let { Personal.decodeChecked(Codec.parseOrNull(it))?.data }
            w to p
        }
        if (w == null && p == null) {
            previousAvailable = false
            notify(L("Não há uma cópia anterior válida guardada no aparelho.", "There's no valid previous copy saved on the device.", "No hay una copia anterior válida guardada en el dispositivo."))
            return@launch
        }
        val parts = listOfNotNull(w?.let { L("a escala de trabalho", "the work schedule", "el horario de trabajo") }, p?.let { L("o calendário pessoal (${it.events.size} evento(s))", "the personal calendar (${it.events.size} event(s))", "el calendario personal (${it.events.size} evento(s))") }).joinToString(L(" e ", " and ", " y "))
        if (!confirm(L("Voltar $parts para a cópia guardada antes da última importação? Os dados atuais passam a ser a cópia, então dá para desfazer usando esta mesma opção.", "Restore $parts to the copy saved before the last import? The current data becomes the copy, so you can undo this with the same option.", "¿Volver $parts a la copia guardada antes de la última importación? Los datos actuales pasan a ser la copia, así que puede deshacerlo con esta misma opción."))) return@launch
        if (replaceAll(w, p)) notify(L("Dados anteriores restaurados.", "Previous data restored.", "Datos anteriores restaurados."))
    }

    fun resetDefault() = scope.launch {
        if (!data.initialized) return@launch
        val sc = current
        if (confirm(L("Restaurar a escala \"${sc.name}\" para um ciclo 4×1 começando hoje e apagar as mudanças e períodos preservados dela?\n\nNome, cor, notas, feriados e outras escalas serão mantidos.", "Reset the schedule \"${sc.name}\" to a 4×1 cycle starting today and erase its changes and preserved periods?\n\nName, color, notes, holidays and other schedules will be kept.", "¿Restablecer el horario \"${sc.name}\" a un ciclo 4×1 que empieza hoy y borrar sus cambios y períodos conservados?\n\nSe mantendrán el nombre, el color, las notas, los festivos y los demás horarios."))) {
            saveSchedule(sc.copy(anchor = todayIso, blocks = listOf(Block(4, 1)), changes = emptyList(), history = emptyList()))
        }
    }

    // ---------- compartilhar período / PDF ----------
    fun openShare() {
        if (!ready) return
        sheet = Sheet.Share(Dates.iso(view), Dates.iso(view.plusMonths(1).minusDays(1)))
    }

    fun submitShare(start: String, end: String, withNotes: Boolean) {
        if (!Dates.isDate(start) || !Dates.isDate(end) || end < start) { notify(L("A data final deve ser igual ou posterior à inicial.", "The end date must be on or after the start date.", "La fecha final debe ser igual o posterior a la inicial.")); return }
        if (Render.monthsBetween(Dates.parse(start), Dates.parse(end)) > 12) { notify(L("Escolha um intervalo de no máximo 12 meses.", "Choose a range of up to 12 months.", "Elija un intervalo de 12 meses como máximo.")); return }
        if (busy != null) return
        closeSheet()
        scope.launch {
            val tm = textMeasurer ?: return@launch
            val sch = current
            busy = L("Gerando imagem…", "Creating image…", "Generando imagen…")
            val img = try {
                withContext(work) { runCatching { Render.periodImage(tm, sch, start, end, withNotes) }.getOrNull() }
            } finally {
                busy = null
            }
            if (img == null) { notify(L("Não foi possível gerar a imagem.", "Couldn't create the image.", "No se pudo generar la imagen.")); return@launch }
            val range = "${Dates.fmt(start)} — ${Dates.fmt(end)}"
            if (!platform.shareImage(img, "${slug(sch.name)}-$start-${L("a", "to", "a")}-$end.png", "${sch.name} — $range", L("Minha escala de $range.", "My schedule for $range.", "Mi horario del $range.")))
                notify(L("Não foi possível compartilhar a imagem.", "Couldn't share the image.", "No se pudo compartir la imagen."))
        }
    }

    fun exportPdf(colors: AppColors) = scope.launch {
        if (!ready || busy != null) return@launch
        val tm = textMeasurer ?: return@launch
        val year = view.year
        val sch = current
        val t = today
        val name = L("escala", "schedule", "horario") + "-${sch.name.replace(Regex("""[^\w-]+"""), "_")}-$year.pdf"
        // o desenho do PDF roda fora da thread principal (ver AndroidPlatform.savePdf)
        val ok = platform.savePdf(name, Render.PDF_W, Render.PDF_H) { Render.yearPdf(this, tm, sch, year, colors, t) }
        if (ok == false) notify(L("Não foi possível gerar o PDF.", "Couldn't create the PDF.", "No se pudo generar el PDF."))
    }

    // ---------- calendário pessoal ----------
    private fun savePersonal(next: PersonalData) {
        personal = next
        store.persist(Personal.KEY) { Personal.encode(next) }
        syncReminders()
    }

    /** Toque num dia do calendário pessoal: seleciona; no mesmo dia já selecionado, abre um novo evento. */
    fun selectDay(date: String) {
        val d = Dates.parse(date)
        if (d == selected) openEvent(date) else selected = d
    }

    fun openEvent(date: String = Dates.iso(selected), e: Event? = null, start: String? = null) { sheet = Sheet.EventEdit(date, e, start) }

    private fun newEventId(): String = Ids.newId("e", 24) { id -> personal.events.any { it.id == id } }

    /** Salvar da folha de evento. Devolve true quando a folha fecha. */
    fun submitEvent(editing: Event?, f: EventForm): Boolean {
        val title = f.title.trim()
        if (title.isEmpty()) { notify(L("Informe o título.", "Enter the title.", "Indique el título.")); return false }
        if (!Dates.isDate(f.date)) { notify(L("Informe uma data válida.", "Enter a valid date.", "Indique una fecha válida.")); return false }
        if (!f.allDay && !Personal.isTime(f.start)) { notify(L("Informe o horário de início ou marque \"Dia inteiro\".", "Enter the start time or check \"All day\".", "Indique la hora de inicio o marque \"Todo el día\".")); return false }
        if (!f.allDay && f.end.isNotEmpty() && f.end < f.start) { notify(L("O horário final não pode ser anterior ao inicial.", "The end time can't be before the start time.", "La hora final no puede ser anterior a la inicial.")); return false }
        if (f.repeat != Repeat.NONE && f.until.isNotEmpty() && (!Dates.isDate(f.until) || f.until < f.date)) { notify(L("A repetição deve terminar na data do evento ou depois.", "The repeat must end on or after the event date.", "La repetición debe terminar en la fecha del evento o después.")); return false }
        if (editing == null && personal.events.size >= Personal.MAX_EVENTS) { notify(L("Limite de ${Personal.MAX_EVENTS} eventos.", "Limit of ${Personal.MAX_EVENTS} events.", "Límite de ${Personal.MAX_EVENTS} eventos.")); return false }
        val e = Event(
            id = editing?.id ?: newEventId(), date = f.date, title = title.take(80), kind = f.kind, allDay = f.allDay,
            start = if (f.allDay) "" else f.start, end = if (f.allDay) "" else f.end,
            description = f.description.trim().take(500), color = Sanitize.cleanColor(f.color, "#1a73e8"), remind = f.remind,
            category = f.category, repeat = f.repeat, until = if (f.repeat == Repeat.NONE) "" else f.until, location = f.location.trim().take(80),
        )
        closeSheet()
        savePersonal(personal.replace(e))
        selected = Dates.parse(e.date)
        if (e.date.take(7) != Dates.iso(view).take(7)) view = selected.withDayOfMonth(1)
        if (e.remind >= 0) platform.requestNotificationPermission()
        return true
    }

    fun deleteEvent(e: Event) = scope.launch {
        val all = if (e.recurring) L(" Todas as repetições serão excluídas.", " All repeats will be deleted.", " Se eliminarán todas las repeticiones.") else ""
        if (!confirm(L("Excluir \"${e.title}\"?", "Delete \"${e.title}\"?", "¿Eliminar \"${e.title}\"?") + all)) return@launch
        closeSheet()
        savePersonal(personal.remove(e.id))
    }

    fun openDayNote(date: String = Dates.iso(selected)) { sheet = Sheet.DayNoteEdit(date) }

    fun submitDayNote(date: String, textIn: String, color: String) {
        val text = textIn.trim().take(500)
        if (text.isNotEmpty() && date !in personal.notes && personal.notes.size >= AppData.MAX_NOTES) {
            notify(L("Limite de ${AppData.MAX_NOTES} notas. Exclua notas antigas para criar novas.", "Limit of ${AppData.MAX_NOTES} notes. Delete old notes to create new ones.", "Límite de ${AppData.MAX_NOTES} notas. Elimine notas antiguas para crear nuevas.")); return
        }
        val notes = LinkedHashMap(personal.notes)
        if (text.isEmpty()) notes.remove(date) else notes[date] = DayNote(text, Sanitize.cleanColor(color, "#f9ab00"))
        closeSheet()
        savePersonal(personal.copy(notes = notes))
    }

    /** Botão "Ativar" do aviso de notificações bloqueadas. */
    fun enableNotifications() = platform.enableNotifications()

    fun deleteDayNote(date: String) = scope.launch {
        if (confirm(L("Excluir a nota desta data?", "Delete the note for this date?", "¿Eliminar la nota de esta fecha?"))) {
            closeSheet()
            savePersonal(personal.copy(notes = personal.notes - date))
        }
    }

    private fun slug(name: String): String =
        java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^a-zA-Z0-9]+"), "-").trim('-').lowercase().ifEmpty { L("escala", "schedule", "horario") }

    companion object {
        const val BAK = Codec.KEY + ".bak"
        const val BAK2 = Codec.KEY + ".bak2"
        const val META = Codec.KEY + ".meta"
        const val NAME_KEY = "kalendrium.name"
        /** Marca que a migração visual da 6.0 já rodou (troca única da paleta padrão antiga pela nova). */
        const val DESIGN_KEY = "kalendrium.design"
        const val DESIGN_VERSION = "6"
        const val PERSONAL_BAK = Personal.KEY + ".bak"
        const val PERSONAL_BAK2 = Personal.KEY + ".bak2"
        /** O backup é gravado com indentação: 5.000 eventos ocupam ~2,2 MB. */
        const val MAX_IMPORT_BYTES = 10_000_000L
        val WRITE_FAILED get() = L("Não foi possível salvar no aparelho (armazenamento cheio ou bloqueado). As alterações valem só até fechar o app — exporte um backup.", "Couldn't save on the device (storage full or blocked). Changes only last until you close the app — export a backup.", "No se pudo guardar en el dispositivo (almacenamiento lleno o bloqueado). Los cambios solo duran hasta cerrar la app — exporte una copia.")
        val READ_FAILED get() = L("Não foi possível ler os dados salvos no aparelho. Para protegê-los, nada será gravado por cima até você fechar e abrir o app de novo.", "Couldn't read the data saved on the device. To protect it, nothing will be written over it until you close and reopen the app.", "No se pudieron leer los datos guardados en el dispositivo. Para protegerlos, no se sobrescribirá nada hasta que cierre y vuelva a abrir la app.")
        val PAL = listOf("#0b8043", "#d93025", "#1a73e8", "#f9ab00", "#9334e6", "#e8710a", "#00838f", "#c2185b")
        val PRESETS = listOf("#d93025", "#e8710a", "#f9ab00", "#34a853", "#0b8043", "#00838f", "#1a73e8", "#3949ab", "#9334e6", "#c2185b", "#795548", "#5f6368", "#111111")
    }
}
