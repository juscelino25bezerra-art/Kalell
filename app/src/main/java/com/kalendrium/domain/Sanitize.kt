// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor

/**
 * Validação e limpeza dos dados, portadas 1:1 do PWA (cleanX / validX / sanitize).
 *
 * Convenção: um parâmetro Kotlin `null` equivale a `undefined` no JS (campo ausente);
 * `JsonNull` equivale a `null` (campo presente com valor null).
 */
object Sanitize {
    private val HEX = Regex("^#[0-9a-fA-F]{6}$")
    private val DEC = Regex("""^\s*\d+(\.\d+)?\s*$""")
    private val NON_WORD = Regex("""[^\w-]""")
    private val ALIGNS = listOf("left", "center", "right")

    // ---------- auxiliares de leitura ----------
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonElement?.bool(): Boolean? =
        (this as? JsonPrimitive)?.takeIf { !it.isString && this !is JsonNull }?.content?.let {
            when (it) { "true" -> true; "false" -> false; else -> null }
        }

    /** `num()` do PWA: aceita número JSON ou texto só com dígitos decimais; senão NaN. */
    fun num(v: JsonElement?): Double {
        val p = v as? JsonPrimitive ?: return Double.NaN
        if (p is JsonNull) return Double.NaN
        if (p.isString) return if (DEC.matches(p.content)) p.content.trim().toDouble() else Double.NaN
        if (p.content == "true" || p.content == "false") return Double.NaN
        return p.content.toDoubleOrNull() ?: Double.NaN
    }

    private fun isInt(d: Double) = !d.isNaN() && !d.isInfinite() && floor(d) == d

    private fun isHex(v: JsonElement?): Boolean = v.str()?.let { HEX.matches(it) } == true

    fun cleanColor(c: String?, d: String): String = if (c != null && HEX.matches(c)) c else d
    private fun cleanColor(c: JsonElement?, d: String) = cleanColor(c.str(), d)

    fun cleanName(n: String?, d: String): String {
        val t = n?.trim().orEmpty()
        return if (t.isNotEmpty()) t.take(30) else d
    }

    private fun cleanId(v: JsonElement?, max: Int): String = v.str()?.replace(NON_WORD, "")?.take(max) ?: ""

    // ---------- limpeza ----------
    fun cleanBlocks(b: JsonElement?): List<Block>? {
        val a = b.arr() ?: return null
        if (a.isEmpty() || a.size > 100) return null
        val out = ArrayList<Block>()
        for (x in a) {
            val o = x.obj() ?: return null
            val w = num(o["w"])
            val f = num(o["o"])
            if (!isInt(w) || !isInt(f) || w < 1 || w > 99 || f < 1 || f > 99) return null
            val name = o["name"].str()?.trim()?.take(40) ?: ""
            out.add(Block(w.toInt(), f.toInt(), name))
        }
        return if (out.sumOf { it.w + it.o } > 1000) null else out
    }

    /** Mesmas regras de cleanBlocks, para os valores digitados no editor. */
    fun cleanBlocks(b: List<Triple<String, String, String>>): List<Block>? {
        val arr = JsonArray(b.map { (w, o, n) ->
            JsonObject(mapOf("w" to JsonPrimitive(w), "o" to JsonPrimitive(o), "name" to JsonPrimitive(n)))
        })
        return cleanBlocks(arr)
    }

    fun cleanChanges(c: JsonElement?, len: Int): List<Change> {
        val a = c.arr() ?: return emptyList()
        val m = LinkedHashMap<String, Change>()
        for (x in a) {
            val o = x.obj() ?: continue
            val date = o["date"].str()
            if (!Dates.isDate(date)) continue
            val p = num(o["pos"])
            if (!isInt(p) || p < 0 || p >= len) continue
            m[date!!] = Change(date, p.toInt())
        }
        return m.values.sortedBy { it.date }
    }

    private fun cleanCfg(x: JsonElement?): PlainCfg? {
        val o = x.obj() ?: return null
        val blocks = cleanBlocks(o["blocks"]) ?: return null
        val anchor = o["anchor"].str()
        if (!Dates.isDate(anchor)) return null
        return PlainCfg(anchor!!, blocks, cleanChanges(o["changes"], Engine.cycleLen(blocks)))
    }

    fun cleanNotes(n: JsonElement?): Map<String, Note> {
        val out = LinkedHashMap<String, Note>()
        val o = n.obj() ?: return out
        for ((k, v) in o.entries.take(AppData.MAX_NOTES)) {
            val vo = v.obj()
            if (!Dates.isDate(k) || vo == null) continue
            val text = vo["text"].str()?.take(500) ?: ""
            val holiday = vo["holiday"].bool() == true
            if (text.isNotEmpty() || holiday) {
                val color = cleanColor(vo["color"], "").ifEmpty { null }
                val align = vo["align"].str()?.takeIf { it in ALIGNS }
                out[k] = Note(text, holiday, color, align)
            }
        }
        return out
    }

    private fun cleanHist(h: JsonElement?): List<Era> {
        val a = h.arr() ?: return emptyList()
        val out = ArrayList<Era>()
        var last = ""
        val entries = a.mapNotNull { e -> e.obj()?.let { o -> o["to"].str()?.takeIf { Dates.isDate(it) }?.let { it to o } } }
            .sortedBy { it.first }
        for ((to, e) in entries) {
            val c = cleanCfg(e)
            if (c != null && to > last) {
                out.add(Era(to, c.anchor, c.blocks, c.changes)); last = to
            }
        }
        return out
    }

    fun cleanPeriods(p: JsonElement?): List<Period> {
        val a = p.arr() ?: return emptyList()
        val out = ArrayList<Period>()
        val ids = HashSet<String>()
        for (x in a.take(AppData.MAX_PERIODS)) {
            val o = x.obj() ?: continue
            val start = o["start"].str()
            val end = o["end"].str()
            if (!Dates.isDate(start) || !Dates.isDate(end) || end!! < start!!) continue
            val id = cleanId(o["id"], 24)
            if (id.isEmpty() || !ids.add(id)) continue
            val title = o["title"].str()?.trim()?.take(80) ?: ""
            out.add(Period(id, start, end, title, cleanColor(o["color"], "#1a73e8")))
        }
        return out.sortedWith(compareBy<Period> { it.start }.thenBy { it.end })
    }

    fun cleanPeriods(list: List<Period>): List<Period> {
        val ids = HashSet<String>()
        // ordena antes de cortar: o limite é aplicado na criação ([AppData.MAX_PERIODS]); aqui é só uma rede de segurança
        return list.filter { Dates.isDate(it.start) && Dates.isDate(it.end) && it.end >= it.start && it.id.isNotEmpty() && ids.add(it.id) }
            .sortedWith(compareBy<Period> { it.start }.thenBy { it.end })
            .take(AppData.MAX_PERIODS)
    }

    private fun cleanGroups(g: JsonElement?): List<Schedule> {
        val a = g.arr() ?: return emptyList()
        val out = ArrayList<Schedule>()
        val ids = HashSet<String>()
        for (x in a.take(AppData.MAX_GROUPS)) {
            val c = cleanCfg(x)
            val o = x.obj()
            val id = cleanId(o?.get("id"), 12)
            if (c == null || o == null || id.isEmpty() || id in ids) continue
            ids.add(id)
            out.add(
                Schedule(
                    id = id, name = cleanName(o["name"].str(), "Escala"), color = cleanColor(o["color"], "#0b8043"),
                    anchor = c.anchor, blocks = c.blocks, changes = c.changes,
                    history = cleanHist(o["history"]), notes = cleanNotes(o["notes"]), periods = cleanPeriods(o["periods"]),
                ).withShift(o)
            )
        }
        return out
    }

    /** Horário do turno: só vale com início e fim válidos ("HH:mm"); senão fica sem horário. */
    private fun Schedule.withShift(o: JsonObject): Schedule {
        val a = o["shiftStart"].str()
        val b = o["shiftEnd"].str()
        return if (Personal.isTime(a) && Personal.isTime(b)) copy(shiftStart = a!!, shiftEnd = b!!) else this
    }

    // ---------- validação estrita (sem perdas) ----------
    private fun validChanges(c: JsonElement?, len: Int): Boolean {
        if (c == null) return true
        val a = c.arr() ?: return false
        return a.all { x ->
            val o = x.obj() ?: return@all false
            val p = num(o["pos"])
            Dates.isDate(o["date"].str()) && isInt(p) && p >= 0 && p < len
        }
    }

    private fun validHistory(h: JsonElement?): Boolean {
        if (h == null) return true
        val a = h.arr() ?: return false
        var last = ""
        for (e in a) {
            val c = cleanCfg(e)
            val to = e.obj()?.get("to").str()
            if (e.obj() == null || !Dates.isDate(to) || c == null || to!! <= last ||
                !validChanges(e.obj()!!["changes"], Engine.cycleLen(c.blocks))
            ) return false
            last = to
        }
        return true
    }

    private fun validNotes(n: JsonElement?): Boolean {
        if (n is JsonObject && n.size > AppData.MAX_NOTES) return false
        if (n == null) return true
        val o = n.obj() ?: return false
        for ((k, v) in o) {
            val vo = v.obj() ?: return false
            if (!Dates.isDate(k)) return false
            vo["text"]?.let { if (it.str() == null) return false }
            vo["holiday"]?.let { if (it.bool() == null) return false }
            vo["color"]?.let { if (!isHex(it)) return false }
            vo["align"]?.let { if (it.str() !in ALIGNS) return false }
        }
        return true
    }

    private fun validPeriods(p: JsonElement?): Boolean {
        if (p == null) return true
        val a = p.arr() ?: return false
        if (a.size > AppData.MAX_PERIODS) return false
        val ids = HashSet<String>()
        for (x in a) {
            val o = x.obj() ?: return false
            val id = o["id"].str() ?: return false
            if (id.isEmpty() || id != id.replace(NON_WORD, "").take(24) || !ids.add(id)) return false
            val s = o["start"].str()
            val e = o["end"].str()
            if (!Dates.isDate(s) || !Dates.isDate(e) || e!! < s!!) return false
            o["title"]?.let { if (it.str() == null) return false }
            o["color"]?.let { if (!isHex(it)) return false }
        }
        return true
    }

    private fun validGroups(g: JsonElement?): Boolean {
        if (g == null) return true
        val a = g.arr() ?: return false
        if (a.size > AppData.MAX_GROUPS) return false
        val ids = HashSet<String>()
        for (x in a) {
            val o = x.obj() ?: return false
            val c = cleanCfg(o)
            val id = cleanId(o["id"], 12)
            if (c == null || id.isEmpty() || id != o["id"].str() || id in ids ||
                !validChanges(o["changes"], Engine.cycleLen(c.blocks)) || !validHistory(o["history"])
            ) return false
            ids.add(id)
            o["color"]?.let { if (!isHex(it)) return false }
            o["name"]?.let { if (it.str() == null) return false }
            if (!validNotes(o["notes"]) || !validPeriods(o["periods"])) return false
        }
        return true
    }

    fun validStored(x: JsonElement?): Boolean {
        val o = x.obj() ?: return false
        o["initialized"]?.let { if (it.bool() == null) return false }
        val b = cleanBlocks(o["blocks"]) ?: return false
        if (!Dates.isDate(o["anchor"].str())) return false
        if (!validChanges(o["changes"], Engine.cycleLen(b)) || !validHistory(o["history"]) || !validNotes(o["notes"]) ||
            !validGroups(o["groups"]) || !validPeriods(o["periods"])
        ) return false
        o["active"]?.let { act ->
            val s = act.str() ?: return false
            if (s.isNotEmpty() && o["groups"].arr()?.any { it.obj()?.get("id").str() == s } != true) return false
        }
        o["theme"]?.let { if (it.str() !in AppData.THEMES) return false }
        o["palette"]?.let { if (it.str() !in AppData.PALETTES) return false }
        o["paletteX"]?.let { if (it.str() !in AppData.ALL_PALETTES) return false }
        o["uiStyle"]?.let { if (it.str() !in AppData.STYLES) return false }
        o["color"]?.let { if (!isHex(it)) return false }
        o["name"]?.let { if (it.str() == null) return false }
        o["dynamicColor"]?.let { if (it.bool() == null) return false }
        o["hideRecents"]?.let { if (it.bool() == null) return false }
        return true
    }

    /**
     * strict=true (importação): devolve null se algo for inválido.
     * strict=false (carregamento): cai no padrão para o que estiver inválido.
     */
    fun sanitize(x: JsonElement?, strict: Boolean, today: String): AppData? {
        val def = AppData.default(today)
        val o = x.obj() ?: return if (strict) null else def
        val blocks = cleanBlocks(o["blocks"])
        val anchor = o["anchor"].str()?.takeIf { Dates.isDate(it) }
        if (strict && !validStored(o)) return null
        if (strict && (blocks == null || anchor == null)) return null
        val b = blocks ?: def.main.blocks
        val groups = cleanGroups(o["groups"])
        val activeRaw = o["active"].str()
        val main = Schedule(
            id = "",
            name = cleanName(o["name"].str(), def.main.name),
            color = cleanColor(o["color"], def.main.color),
            anchor = anchor ?: def.main.anchor,
            blocks = b,
            changes = if (blocks != null) cleanChanges(o["changes"], Engine.cycleLen(b)) else emptyList(),
            history = if (blocks != null) cleanHist(o["history"]) else emptyList(),
            notes = cleanNotes(o["notes"]),
            periods = cleanPeriods(o["periods"]),
        ).withShift(o)
        return AppData(
            // só `false` literal marca "sem escala ainda"; ausente = instalação antiga já configurada
            initialized = o["initialized"].bool() != false,
            theme = o["theme"].str()?.takeIf { it in AppData.THEMES } ?: "system",
            palette = o["paletteX"].str()?.takeIf { it in AppData.ALL_PALETTES }
                ?: o["palette"].str()?.takeIf { it in AppData.PALETTES } ?: "material",
            uiStyle = o["uiStyle"].str()?.takeIf { it in AppData.STYLES } ?: "material",
            schedules = listOf(main) + groups,
            active = if (activeRaw != null && groups.any { it.id == activeRaw }) activeRaw else "",
            dynamicColor = o["dynamicColor"].bool() == true,
            hideRecents = o["hideRecents"].bool() == true,
        )
    }
}
