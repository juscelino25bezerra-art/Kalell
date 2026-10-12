package com.kalendrium.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Conversão AppData <-> JSON no MESMO formato do PWA (localStorage "minhaEscala.material.v4"
 * e arquivo de backup `version: 3`). Assim um backup exportado no PWA importa aqui e vice-versa.
 */
object Codec {
    const val KEY = "minhaEscala.material.v4"
    const val BACKUP_VERSION = 3

    private val compact = Json
    private val pretty = Json { prettyPrint = true }

    private fun blocks(b: List<Block>) = buildJsonArray {
        b.forEach { x ->
            add(buildJsonObject {
                put("w", x.w); put("o", x.o)
                if (x.name.isNotEmpty()) put("name", x.name)
            })
        }
    }

    private fun changes(c: List<Change>) = buildJsonArray {
        c.forEach { add(buildJsonObject { put("date", it.date); put("pos", it.pos) }) }
    }

    private fun history(h: List<Era>) = buildJsonArray {
        h.forEach { e ->
            add(buildJsonObject {
                put("to", e.to); put("anchor", e.anchor)
                put("blocks", blocks(e.blocks)); put("changes", changes(e.changes))
            })
        }
    }

    private fun notes(n: Map<String, Note>) = buildJsonObject {
        n.forEach { (k, v) ->
            put(k, buildJsonObject {
                put("text", v.text); put("holiday", v.holiday)
                v.color?.let { put("color", it) }
                v.align?.let { put("align", it) }
            })
        }
    }

    private fun periods(p: List<Period>) = buildJsonArray {
        p.forEach {
            add(buildJsonObject {
                put("id", it.id); put("start", it.start); put("end", it.end)
                put("title", it.title); put("color", it.color)
            })
        }
    }

    fun toJson(d: AppData, backup: Boolean = false, personal: PersonalData? = null): JsonObject {
        val m = d.main
        return buildJsonObject {
            if (backup) {
                put("app", "minha-escala"); put("version", BACKUP_VERSION)
            }
            put("initialized", d.initialized)
            put("anchor", m.anchor)
            put("blocks", blocks(m.blocks))
            put("changes", changes(m.changes))
            put("history", history(m.history))
            put("notes", notes(m.notes))
            put("periods", periods(m.periods))
            put("theme", d.theme)
            put("palette", d.palette)
            put("uiStyle", d.uiStyle)
            put("name", m.name)
            put("color", m.color)
            put("groups", JsonArray(d.groups.map { g ->
                buildJsonObject {
                    put("id", g.id); put("name", g.name); put("color", g.color)
                    put("anchor", g.anchor); put("blocks", blocks(g.blocks)); put("changes", changes(g.changes))
                    put("history", history(g.history)); put("notes", notes(g.notes)); put("periods", periods(g.periods))
                }
            }))
            put("active", d.active)
            // ajustes que só existem no Kalendrium (o PWA ignora chaves desconhecidas)
            if (d.dynamicColor) put("dynamicColor", true)
            if (d.hideRecents) put("hideRecents", true)
            // calendário pessoal do Kalendrium: o PWA ignora esta chave
            if (personal != null) put("personal", Personal.toJson(personal))
        }
    }

    fun encode(d: AppData): String = compact.encodeToString(JsonElement.serializer(), toJson(d))
    fun encodeBackup(d: AppData, personal: PersonalData? = null): String =
        pretty.encodeToString(JsonElement.serializer(), toJson(d, backup = true, personal = personal))

    /** Dados pessoais contidos num backup do Kalendrium (null = backup sem dados pessoais, como os do PWA). */
    fun personalFromBackup(raw: String): PersonalData? = ((parseOrNull(raw) as? JsonObject)?.get("personal"))?.let { Personal.decode(it) }

    /**
     * Aninhamento máximo de objetos/listas. O formato mais fundo (raiz › groups › escala › history › era › blocks › bloco) tem 7 níveis;
     * o parser do kotlinx é recursivo e estoura a pilha (StackOverflowError, que não é Exception) por volta de
     * alguns milhares de níveis, então um arquivo de poucos KB com "[[[[…" derrubaria o app na importação.
     */
    const val MAX_DEPTH = 32

    fun parseOrNull(raw: String): JsonElement? {
        if (!depthWithin(raw, MAX_DEPTH)) return null
        return try {
            compact.parseToJsonElement(raw)
        } catch (_: Exception) {
            null
        }
    }

    /** true se `{`/`[` fora de strings não passam de `max` níveis (o JSON em si é validado depois, pelo parser). */
    internal fun depthWithin(raw: String, max: Int): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in raw) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{', '[' -> if (++depth > max) return false
                '}', ']' -> depth--
            }
        }
        return true
    }

    /** Importação estrita de um backup. null = backup inválido (nada é alterado). */
    fun importBackup(raw: String, today: String): AppData? = Sanitize.sanitize(parseOrNull(raw), strict = true, today = today)

    data class Loaded(val data: AppData, val warning: String?, val corruptRaw: String?)

    /** Equivalente ao load() do PWA: corrige dados inválidos e informa o que foi descartado. */
    fun load(raw: String?, today: String): Loaded {
        if (raw == null) return Loaded(AppData.default(today), null, null)
        // arquivo existe mas está vazio (gravação interrompida): não é instalação nova
        if (raw.isBlank()) return Loaded(AppData.default(today), L("Os dados salvos da escala estavam vazios e não puderam ser lidos.", "The saved schedule data was empty and could not be read.", "Los datos guardados del horario estaban vacíos y no se pudieron leer."), raw)
        val p = parseOrNull(raw)
        if (Sanitize.validStored(p)) return Loaded(Sanitize.sanitize(p, false, today)!!, null, null)
        val r = Sanitize.sanitize(p, false, today)!!
        val o = p as? JsonObject
        val warning = if (o == null || Sanitize.cleanBlocks(o["blocks"]) == null || !Dates.isDate((o["anchor"] as? JsonPrimitive)?.takeIf { it.isString }?.content)) {
            L("Os dados salvos estavam inválidos e a escala principal voltou ao padrão.", "The saved data was invalid and the main schedule was reset to the default.", "Los datos guardados no eran válidos y el horario principal volvió al predeterminado.")
        } else {
            fun len(e: JsonElement?) = (e as? JsonArray)?.size ?: 0
            val notesBefore = (o["notes"] as? JsonObject)?.size ?: 0
            val lost = listOf(
                L("escala(s)", "schedule(s)", "horario(s)") to len(o["groups"]) - r.groups.size,
                L("período(s)", "period(s)", "período(s)") to len(o["periods"]) - r.main.periods.size,
                L("nota(s)", "note(s)", "nota(s)") to notesBefore - r.main.notes.size,
            ).filter { it.second > 0 }.map { "${it.second} ${it.first}" }
            L("Alguns dados salvos estavam inválidos e foram corrigidos", "Some saved data was invalid and was fixed", "Algunos datos guardados no eran válidos y se corrigieron") +
                (if (lost.isNotEmpty()) " (${L("descartados", "discarded", "descartados")}: ${lost.joinToString(", ")})" else "") + "."
        }
        return Loaded(r, warning, raw)
    }
}
