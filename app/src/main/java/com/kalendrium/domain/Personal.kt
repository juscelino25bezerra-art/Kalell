package com.kalendrium.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.LocalTime

/** Os dois contextos do app. Cada um tem seus próprios dados, guardados em arquivos separados. */
enum class CalendarMode(val key: String) {
    PERSONAL("personal"),
    WORK("work");

    val label: String get() = when (this) {
        PERSONAL -> L("Pessoal", "Personal", "Personal")
        WORK -> L("Work", "Work", "Trabajo")
    }

    companion object {
        fun of(key: String?): CalendarMode? = entries.firstOrNull { it.key == key }
    }
}

/** Tipo de item do calendário pessoal. */
enum class EventKind(val key: String) {
    EVENT("event"),
    APPOINTMENT("appointment"),
    REMINDER("reminder");

    val label: String get() = when (this) {
        EVENT -> L("Evento", "Event", "Evento")
        APPOINTMENT -> L("Compromisso", "Appointment", "Cita")
        REMINDER -> L("Lembrete", "Reminder", "Recordatorio")
    }

    companion object {
        fun of(key: String?): EventKind = entries.firstOrNull { it.key == key } ?: EVENT
    }
}

/**
 * Evento do calendário pessoal. Datas em "AAAA-MM-DD" e horários em "HH:mm", como no restante do app.
 * [remind] = minutos de antecedência do lembrete (-1 = sem lembrete).
 */
data class Event(
    val id: String,
    val date: String,
    val title: String,
    val kind: EventKind = EventKind.EVENT,
    val allDay: Boolean = true,
    val start: String = "",
    val end: String = "",
    val description: String = "",
    val color: String = "#1a73e8",
    val remind: Int = -1,
) {
    /** Momento em que o lembrete deve tocar (eventos de dia inteiro usam 9:00 como referência). */
    fun reminderAt(): LocalDateTime? {
        if (remind < 0) return null
        val base = Dates.parse(date).atTime(if (allDay) LocalTime.of(9, 0) else Personal.time(start) ?: return null)
        return base.minusMinutes(remind.toLong())
    }

    /** "Dia inteiro", "14:00" ou "14:00 – 15:30". */
    val timeText: String
        get() = when {
            allDay -> L("Dia inteiro", "All day", "Todo el día")
            end.isNotEmpty() -> "$start – $end"
            else -> start
        }
}

/** Nota de um dia no calendário pessoal (independente das notas da escala). */
data class DayNote(val text: String, val color: String)

data class PersonalData(
    val events: List<Event> = emptyList(),
    val notes: Map<String, DayNote> = emptyMap(),
) {
    /** Eventos agrupados por data e já ordenados (calculado uma vez por versão dos dados). */
    private val byDate: Map<String, List<Event>> by lazy {
        events.groupBy { it.date }.mapValues { (_, v) -> v.sortedWith(compareBy<Event>({ !it.allDay }, { it.start }, { it.title.lowercase() })) }
    }

    /** Eventos do dia: primeiro os de dia inteiro, depois por horário. */
    fun eventsOn(date: String): List<Event> = byDate[date] ?: emptyList()

    fun replace(e: Event) = copy(events = events.filter { it.id != e.id } + e)
    fun remove(id: String) = copy(events = events.filter { it.id != id })
}

object Personal {
    const val KEY = "kalendrium.personal.v1"
    const val MODE_KEY = "kalendrium.mode"
    const val MAX_EVENTS = 5000

    /** Opções de lembrete (minutos antes). */
    val REMINDER_MINUTES = listOf(-1, 0, 10, 30, 60, 1440)

    /** Opções de lembrete com o texto no idioma atual. */
    val REMINDERS: List<Pair<Int, String>> get() = REMINDER_MINUTES.map { it to reminderText(it) }

    fun reminderText(m: Int): String = when (m) {
        -1 -> L("Sem lembrete", "No reminder", "Sin recordatorio")
        0 -> L("Na hora", "At time of event", "A la hora")
        10 -> L("10 minutos antes", "10 minutes before", "10 minutos antes")
        30 -> L("30 minutos antes", "30 minutes before", "30 minutos antes")
        60 -> L("1 hora antes", "1 hour before", "1 hora antes")
        else -> L("1 dia antes", "1 day before", "1 día antes")
    }

    private val TIME_RE = Regex("""^\d{2}:\d{2}$""")

    fun time(s: String?): LocalTime? {
        if (s == null || !TIME_RE.matches(s)) return null
        val h = s.take(2).toInt()
        val m = s.takeLast(2).toInt()
        return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
    }

    fun isTime(s: String?) = time(s) != null

    // ---------- JSON ----------
    private val compact = Json

    fun toJson(p: PersonalData): JsonObject = buildJsonObject {
        put("version", 1)
        put("events", buildJsonArray {
            p.events.sortedWith(compareBy({ it.date }, { it.start }, { it.id })).forEach { e ->
                add(buildJsonObject {
                    put("id", e.id); put("date", e.date); put("title", e.title); put("kind", e.kind.key)
                    put("allDay", e.allDay)
                    if (!e.allDay) { put("start", e.start); if (e.end.isNotEmpty()) put("end", e.end) }
                    if (e.description.isNotEmpty()) put("description", e.description)
                    put("color", e.color)
                    if (e.remind >= 0) put("remind", e.remind)
                })
            }
        })
        put("notes", buildJsonObject {
            p.notes.toSortedMap().forEach { (k, v) -> put(k, buildJsonObject { put("text", v.text); put("color", v.color) }) }
        })
    }

    fun encode(p: PersonalData): String = compact.encodeToString(JsonElement.serializer(), toJson(p))

    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun bool(o: JsonObject, k: String): Boolean? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
    private fun int(o: JsonObject, k: String): Int? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()

    /** Lê e valida um evento. null = inválido (é descartado). */
    fun cleanEvent(x: JsonElement?): Event? {
        val o = x as? JsonObject ?: return null
        val id = str(o, "id")?.takeIf { it.isNotEmpty() && it.length <= 40 } ?: return null
        val date = str(o, "date")?.takeIf { Dates.isDate(it) } ?: return null
        val title = str(o, "title")?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: return null
        val allDay = bool(o, "allDay") ?: true
        var start = ""
        var end = ""
        if (!allDay) {
            start = str(o, "start")?.takeIf { isTime(it) } ?: return null
            end = str(o, "end")?.takeIf { isTime(it) && it >= start } ?: ""
        }
        val remind = int(o, "remind")?.takeIf { r -> r in REMINDER_MINUTES } ?: -1
        return Event(
            id = id, date = date, title = title, kind = EventKind.of(str(o, "kind")), allDay = allDay, start = start, end = end,
            description = (str(o, "description") ?: "").take(500),
            color = Sanitize.cleanColor(str(o, "color"), "#1a73e8"),
            remind = remind,
        )
    }

    /** Resultado da leitura com a contagem do que foi descartado por estar inválido. */
    data class Decoded(val data: PersonalData, val droppedEvents: Int, val droppedNotes: Int) {
        val dropped get() = droppedEvents + droppedNotes
        fun droppedText(): String = listOf(L("evento(s)", "event(s)", "evento(s)") to droppedEvents, L("nota(s)", "note(s)", "nota(s)") to droppedNotes)
            .filter { it.second > 0 }.joinToString(", ") { "${it.second} ${it.first}" }
    }

    /**
     * Lê os dados pessoais descartando o que for inválido e contando o descarte.
     * null = não é um objeto de dados pessoais (ou "events"/"notes" têm o tipo errado).
     */
    fun decodeChecked(x: JsonElement?): Decoded? {
        val o = x as? JsonObject ?: return null
        val rawEvents = o["events"]?.let { it as? JsonArray ?: return null } ?: JsonArray(emptyList())
        val rawNotes = o["notes"]?.let { it as? JsonObject ?: return null } ?: JsonObject(emptyMap())
        val seen = HashSet<String>()
        val events = rawEvents.mapNotNull { cleanEvent(it) }.filter { seen.add(it.id) }.take(MAX_EVENTS)
        val notes = LinkedHashMap<String, DayNote>()
        rawNotes.forEach { (k, v) ->
            val n = v as? JsonObject ?: return@forEach
            val text = str(n, "text")?.trim()?.take(500) ?: return@forEach
            if (Dates.isDate(k) && text.isNotEmpty() && notes.size < AppData.MAX_NOTES) notes[k] = DayNote(text, Sanitize.cleanColor(str(n, "color"), "#f9ab00"))
        }
        return Decoded(PersonalData(events, notes), rawEvents.size - events.size, rawNotes.size - notes.size)
    }

    /** Lê os dados pessoais descartando o que for inválido. null = não é um objeto de dados pessoais. */
    fun decode(x: JsonElement?): PersonalData? = decodeChecked(x)?.data

    /** Igual ao [Codec.Loaded]: dados, aviso para o usuário e o texto original quando algo foi descartado. */
    data class Loaded(val data: PersonalData, val warning: String?, val corruptRaw: String?)

    fun loadChecked(raw: String?): Loaded {
        if (raw == null) return Loaded(PersonalData(), null, null)
        val d = (if (raw.isBlank()) null else decodeChecked(Codec.parseOrNull(raw)))
            ?: return Loaded(PersonalData(), L("Os dados salvos do calendário pessoal estavam inválidos e não puderam ser lidos.", "The saved personal calendar data was invalid and could not be read.", "Los datos guardados del calendario personal no eran válidos y no se pudieron leer."), raw)
        if (d.dropped == 0) return Loaded(d.data, null, null)
        return Loaded(d.data, L("Alguns dados do calendário pessoal estavam inválidos e foram descartados", "Some personal calendar data was invalid and was discarded", "Algunos datos del calendario personal no eran válidos y se descartaron") + " (${d.droppedText()}).", raw)
    }

    fun load(raw: String?): PersonalData = loadChecked(raw).data
}
