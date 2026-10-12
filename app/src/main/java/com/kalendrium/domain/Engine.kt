package com.kalendrium.domain

import java.time.LocalDate
import kotlin.math.min

data class Pos(val idx: Int, val bi: Int, val work: Boolean, val n: Int, val block: Block) {
    /** "2º trabalho · bloco 4×2 · Noite", no idioma atual. */
    val label: String get() = Engine.posLabel(work, n, block)
}

data class DayStatus(val work: Boolean, val bi: Int, val block: Block, val idx: Int, val n: Int) {
    val label: String get() = Engine.posLabel(work, n, block)
}

data class RemapResult(val changes: List<Change>, val dropped: Int, val moved: Int)

/** Escala depois de editar os blocos, com quantas mudanças foram reposicionadas ou removidas. */
data class CycleEdit(val schedule: Schedule, val moved: Int, val dropped: Int)

/** Motor do ciclo: mesmo algoritmo do PWA (app.js). */
object Engine {
    /** Rótulo de uma posição do ciclo: "2º trabalho · bloco 4×2", "2nd work day · block 4×2"… */
    fun posLabel(work: Boolean, n: Int, b: Block): String {
        val nm = if (b.name.isNotEmpty()) " · ${b.name}" else ""
        val day = if (work) L("${n}º trabalho", "${ordinal(n)} work day", "${n}º trabajo")
        else L("${n}º folga", "${ordinal(n)} day off", "${n}º libre")
        return "$day · ${L("bloco", "block", "bloque")} ${b.w}×${b.o}$nm"
    }

    fun cycleLen(blocks: List<Block>): Int = blocks.sumOf { it.w + it.o }

    private val posCache = LinkedHashMap<String, List<Pos>>()

    fun positions(blocks: List<Block>): List<Pos> {
        val sig = blocks.joinToString("|") { "${it.w}x${it.o}x${it.name}" }
        synchronized(posCache) { posCache[sig]?.let { return it } }
        val a = ArrayList<Pos>(cycleLen(blocks))
        var i = 0
        blocks.forEachIndexed { bi, b ->
            for (x in 0 until b.w) a.add(Pos(i++, bi, true, x + 1, b))
            for (x in 0 until b.o) a.add(Pos(i++, bi, false, x + 1, b))
        }
        synchronized(posCache) {
            if (posCache.size > 20) posCache.clear()
            posCache[sig] = a
        }
        return a
    }

    /** Configuração que vale na data `s` (era preservada ou a atual). */
    fun cfgFor(s: String, sch: Schedule): Cfg {
        for (e in sch.history) if (s < e.to) return e
        return sch
    }

    private data class Origin(val date: String, val pos: Int, val isChange: Boolean)

    private fun originFor(s: String, cfg: Cfg): Origin {
        var origin = Origin(cfg.anchor, 0, false)
        for (c in cfg.changes.sortedBy { it.date }) {
            if (c.date <= s) origin = Origin(c.date, c.pos, true) else break
        }
        return origin
    }

    fun status(date: LocalDate, sch: Schedule): DayStatus {
        val s = Dates.iso(date)
        val cfg = cfgFor(s, sch)
        val len = cycleLen(cfg.blocks)
        val origin = originFor(s, cfg)
        val idx = Dates.mod(origin.pos + Dates.dd(Dates.parse(origin.date), date), len)
        val p = positions(cfg.blocks)[idx]
        return DayStatus(p.work, p.bi, cfg.blocks[p.bi], idx, p.n)
    }

    fun cycleText(blocks: List<Block>): String =
        blocks.joinToString(" · ") { if (it.name.isNotEmpty()) "${it.w}×${it.o} (${it.name})" else "${it.w}×${it.o}" }

    fun lastTo(sch: Schedule): String = sch.history.lastOrNull()?.to ?: ""

    /**
     * Reposiciona as mudanças pelo (bloco, dia dentro do bloco) quando os blocos são editados.
     * `origs[i]` = índice do bloco antigo que originou o novo bloco i (-1 = bloco novo).
     * Trabalho continua trabalho e folga continua folga: um dia de trabalho que não existe mais
     * vira o último dia de trabalho do bloco novo (idem para a folga). Conta como "movida" toda
     * mudança cujo dia mudou ("3º trabalho" → "2º trabalho"), mesmo que o número da posição seja igual.
     */
    fun remapChanges(ob: List<Block>, nb: List<Block>, changes: List<Change>, origs: List<Int>? = null): RemapResult {
        fun starts(b: List<Block>): List<Int> {
            var n = 0
            return b.map { x -> val s = n; n += x.w + x.o; s }
        }
        val op = positions(ob)
        val np = positions(nb)
        val os = starts(ob)
        val ns = starts(nb)
        val map = HashMap<Int, Int>()
        (origs ?: nb.indices.toList()).forEachIndexed { ni, o -> if (o >= 0 && o < ob.size) map[o] = ni }
        var dropped = 0
        var moved = 0
        val out = ArrayList<Change>()
        for (c in changes) {
            val p = op.getOrNull(c.pos)
            val ni = p?.let { map[it.bi] }
            if (p == null || ni == null) { dropped++; continue }
            val nbk = nb[ni]
            val k = c.pos - os[p.bi]           // dia dentro do bloco antigo (0-based)
            val inBlock = if (p.work) min(k, nbk.w - 1) else nbk.w + min(k - ob[p.bi].w, nbk.o - 1)
            val pos = ns[ni] + inBlock
            if (pos != c.pos || np[pos].work != p.work || np[pos].n != p.n) moved++
            out.add(Change(c.date, pos))
        }
        return RemapResult(out, dropped, moved)
    }

    /** Próxima troca trabalho↔folga a partir de hoje (como o resumo do topo do PWA). */
    fun nextSwitch(today: LocalDate, sch: Schedule): Pair<LocalDate, Int>? {
        val st = status(today, sch)
        val limit = maxOf(62, cycleLen(sch.blocks) * 2)
        for (i in 1..limit) {
            val d = today.plusDays(i.toLong())
            if (status(d, sch).work != st.work) return d to i
        }
        return null
    }

    /** Primeiro dia de trabalho depois de `date` (null se o ciclo não tiver trabalho no horizonte). */
    fun nextWorkAfter(date: LocalDate, sch: Schedule): LocalDate? {
        val limit = maxOf(62, cycleLen(sch.blocks) * 2)
        for (i in 1..limit) {
            val d = date.plusDays(i.toLong())
            if (status(d, sch).work) return d
        }
        return null
    }

    /** Período (férias, licença…) que inclui `date`; se houver mais de um, o que termina por último. */
    fun periodOn(date: String, sch: Schedule): Period? =
        sch.periods.filter { date >= it.start && date <= it.end }.maxByOrNull { it.end }

    /** Próximo período que começa depois de `date` e dentro de `days` dias. */
    fun upcomingPeriod(date: LocalDate, sch: Schedule, days: Long = 14): Period? {
        val from = Dates.iso(date)
        val to = Dates.iso(date.plusDays(days))
        return sch.periods.filter { it.start > from && it.start <= to }.minByOrNull { it.start }
    }

    /**
     * Ciclos que valem em algum dia do intervalo [start, end], do mais antigo ao atual
     * (as eras preservadas cobrem do `to` da era anterior até o seu `to`, exclusive).
     */
    fun cyclesIn(start: String, end: String, sch: Schedule): List<List<Block>> {
        val out = ArrayList<List<Block>>()
        var from = ""
        for (e in sch.history) {
            if (start < e.to && end >= from) out.add(e.blocks)
            from = e.to
        }
        if (end >= from) out.add(sch.blocks)
        return out.distinct()
    }

    /** Texto dos ciclos do intervalo: "4×1" ou "4×1 → 5×2" quando há troca de escala no meio. */
    fun cyclesText(start: String, end: String, sch: Schedule): String =
        cyclesIn(start, end, sch).joinToString(" → ") { cycleText(it) }.ifEmpty { cycleText(sch.blocks) }

    /**
     * Regra do editor de escala, sem interface: troca blocos e data de referência e reposiciona as mudanças.
     * keep = "Manter os dias anteriores": o que valia antes de `from` vira uma era preservada (só com as
     * mudanças anteriores a `from`) e as mudanças a partir de `from` são reposicionadas nos blocos novos.
     * Sem keep, todas as mudanças são reposicionadas e o histórico é descartado.
     */
    fun applyCycleEdit(sc: Schedule, blocks: List<Block>, anchor: String, keep: Boolean, from: String, origs: List<Int>?): CycleEdit {
        val r = remapChanges(sc.blocks, blocks, if (keep) sc.changes.filter { it.date >= from } else sc.changes, origs)
        val history = if (keep) sc.history + Era(from, sc.anchor, sc.blocks, sc.changes.filter { it.date < from }) else emptyList()
        return CycleEdit(sc.copy(blocks = blocks, anchor = anchor, changes = r.changes, history = history), r.moved, r.dropped)
    }
}
