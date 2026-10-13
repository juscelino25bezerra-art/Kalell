// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.ui

import com.kalendrium.domain.L
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.kalendrium.domain.Dates
import com.kalendrium.domain.weekHeader
import com.kalendrium.domain.weekLetters
import com.kalendrium.domain.Engine
import com.kalendrium.domain.Schedule
import com.kalendrium.ui.theme.AppColors
import com.kalendrium.ui.theme.fgOn
import com.kalendrium.ui.theme.hexColor
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Imagem de período e PDF anual, desenhados com as mesmas medidas do PWA (canvas e PDF). */
object Render {
    const val PDF_W = 842   // A4 paisagem, em pontos
    const val PDF_H = 595
    private val ONE = Density(1f)
    private val RED = Color(0xFFDB2626)

    fun monthsBetween(a: LocalDate, b: LocalDate) = (b.year - a.year) * 12 + b.monthValue - a.monthValue + 1

    /** Domingo = 0, como Date.getDay(). */
    private fun dow(d: LocalDate) = d.dayOfWeek.value % 7

    private class Ink(val tm: TextMeasurer, val s: DrawScope) {
        fun measure(t: String, size: Float, weight: Int): TextLayoutResult = tm.measure(
            AnnotatedString(t),
            style = TextStyle(fontSize = size.sp, fontWeight = FontWeight(weight)),
            overflow = TextOverflow.Clip, softWrap = false, maxLines = 1, density = ONE,
        )

        fun width(t: String, size: Float, weight: Int) = measure(t, size, weight).size.width.toFloat()

        /** Texto com a linha de base vertical no meio (textBaseline="middle" do canvas). */
        fun mid(t: Any, x: Float, y: Float, size: Float, color: Color, weight: Int = 600, align: String = "left") {
            val l = measure(t.toString(), size, weight)
            val dx = when (align) { "center" -> l.size.width / 2f; "right" -> l.size.width.toFloat(); else -> 0f }
            s.drawText(l, color, topLeft = Offset(x - dx, y - l.size.height / 2f))
        }

        /** Texto com a linha de base em y (como o operador Td do PDF). */
        fun base(t: String, x: Float, y: Float, size: Float, color: Color, bold: Boolean, center: Boolean = false) {
            val l = measure(t, size, if (bold) 700 else 400)
            val dx = if (center) l.size.width / 2f else 0f
            s.drawText(l, color, topLeft = Offset(x - dx, y - l.firstBaseline))
        }

        fun fit(t: String, size: Float, weight: Int, max: Float): String {
            if (width(t, size, weight) <= max) return t
            var v = t
            while (v.length > 1 && width("$v…", size, weight) > max) v = v.dropLast(1)
            return "$v…"
        }

        fun round(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color) {
            val rr = min(r, min(w / 2, h / 2))
            s.drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(rr, rr))
        }

        fun rect(x: Float, y: Float, w: Float, h: Float, c: Color) = s.drawRect(c, Offset(x, y), Size(w, h))
    }

    private fun firstLine(text: String) = text.split("\n").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""

    /** "Compartilhar período": até 12 meses numa imagem de 1080 px de largura. */
    fun periodImage(tm: TextMeasurer, sch: Schedule, startS: String, endS: String, withNotes: Boolean): ImageBitmap {
        val start = Dates.parse(startS)
        val end = Dates.parse(endS)
        val count = monthsBetween(start, end)
        val W = 1080f
        val headerH = 250f
        val days = weekHeader()
        val left = 55f
        val gap = 8f
        val cw = (970f - gap * 6) / 7
        val ch = 104f

        class Mo(val md: LocalDate, val nd: Int, val h: Float)
        val months = (0 until count).map { mi ->
            val md = start.withDayOfMonth(1).plusMonths(mi.toLong())
            val nd = md.lengthOfMonth()
            Mo(md, nd, 118 + ceil((dow(md) + nd) / 7.0).toFloat() * (ch + 8) + 30)
        }
        val H = headerH + months.sumOf { it.h.toDouble() }.toFloat() + 90
        // até ~6 milhões de pixels (~24 MB de bitmap): 12 meses continuam nítidos sem pesar em aparelhos simples
        val k = min(1.0, sqrt(6e6 / (W * H))).toFloat()
        val img = ImageBitmap((W * k).roundToInt(), (H * k).roundToInt())
        val bg = Color(0xFFF7F8FC)
        val card = Color.White
        val fg = Color(0xFF252A34)
        val mut = Color(0xFF667085)
        val off = Color(0xFFF4DDDD)
        val offFg = Color(0xFFA54858)
        val workHex = if (Regex("^#[0-9a-fA-F]{6}$").matches(sch.color)) sch.color else "#65558f"
        val work = hexColor(workHex)
        val workText = if (fgOn(workHex) == Color.Black) Color(0xFF17202A) else Color.White

        CanvasDrawScope().draw(ONE, LayoutDirection.Ltr, Canvas(img), Size(img.width.toFloat(), img.height.toFloat())) {
            scale(k, k, pivot = Offset.Zero) {
                val x = Ink(tm, this)
                drawRect(bg, Offset.Zero, Size(W, H))
                x.mid("KALENDRIUM", 55f, 60f, 23f, mut, 800)
                x.mid(sch.name, 55f, 112f, 43f, fg, 800)
                val range = "${Dates.fmt(start)} — ${Dates.fmt(end)}"
                x.mid(range, 55f, 164f, 27f, fg, 700)
                // todos os ciclos que valem no intervalo (inclui a escala preservada, se o intervalo a cobrir)
                x.mid(x.fit(Engine.cyclesText(startS, endS, sch), 20f, 500, 970f), 55f, 207f, 20f, mut, 500)
                var base = headerH
                for (mo in months) {
                    val first = dow(mo.md)
                    x.round(35f, base, 1010f, mo.h - 20, 34f, card)
                    x.mid(Dates.monthYear(mo.md), 55f, base + 48, 30f, fg, 800)
                    days.forEachIndexed { i, d -> x.mid(d, left + i * (cw + gap) + cw / 2, base + 92, 16f, mut, 800, "center") }
                    for (n in 1..mo.nd) {
                        val d = mo.md.withDayOfMonth(n)
                        val ds = Dates.iso(d)
                        if (ds < startS || ds > endS) continue
                        val idx = first + n - 1
                        val px = left + (idx % 7) * (cw + gap)
                        val py = base + 118 + (idx / 7) * (ch + 8)
                        val st = Engine.status(d, sch)
                        val note = sch.notes[ds]
                        val pers = sch.periods.filter { it.start <= ds && ds <= it.end }
                        x.round(px, py, cw, ch, 18f, if (st.work) work else off)
                        val tc = if (st.work) workText else fg
                        val sub = if (st.work) workText else offFg
                        x.mid(n, px + cw / 2, py + 20, 23f, tc, 800, "center")
                        x.mid(if (st.work) L("TRABALHO", "WORK", "TRABAJO") else L("FOLGA", "OFF", "LIBRE"), px + cw / 2, py + 45, 12f, sub, 800, "center")
                        x.mid("${st.block.w}×${st.block.o}", px + cw / 2, py + 63, 12f, if (st.work) workText else mut, 500, "center")
                        var lineY = py + 82
                        if (note != null && (withNotes || note.holiday)) {
                            val nc = note.color ?: if (note.holiday) "#d93025" else "#f9ab00"
                            val l1 = firstLine(note.text)
                            val label = if (withNotes) l1.ifEmpty { if (note.holiday) L("FERIADO", "HOLIDAY", "FESTIVO") else L("NOTA", "NOTE", "NOTA") } else L("FERIADO", "HOLIDAY", "FESTIVO")
                            val lab = x.fit(label.uppercase(), 11f, 800, cw - 12)
                            x.round(px + 5, lineY - 8, cw - 10, 16f, 6f, hexColor(nc))
                            x.mid(lab, px + cw / 2, lineY, 11f, fgOn(nc), 800, "center")
                            lineY += 18
                        }
                        if (pers.isNotEmpty() && lineY < py + 103) {
                            val p = pers[0]
                            val label = p.title.ifEmpty { L("PERÍODO", "PERIOD", "PERÍODO") } + if (pers.size > 1) " +${pers.size - 1}" else ""
                            val lab = x.fit(label.uppercase(), 10f, 800, cw - 12)
                            x.round(px + 5, lineY - 7, cw - 10, 15f, 6f, hexColor(p.color))
                            x.mid(lab, px + cw / 2, lineY + .5f, 10f, fgOn(p.color), 800, "center")
                        }
                    }
                    base += mo.h
                }
                x.mid(L("Gerado pelo Kalendrium", "Made with Kalendrium", "Generado con Kalendrium"), 1025f, H - 45, 18f, mut, 500, "right")
            }
        }
        return img
    }

    /** "PDF do ano": A4 paisagem com os 12 meses, nas cores do tema e da escala. */
    fun yearPdf(s: DrawScope, tm: TextMeasurer, sch: Schedule, year: Int, t: AppColors, today: LocalDate) {
        val x = Ink(tm, s)
        val W = PDF_W.toFloat()
        val H = PDF_H.toFloat()
        val M = 28f
        val rw = (H - 72 - M - 22) / 3
        val cwid = (W - 2 * M) / 4
        val work = hexColor(sch.color)
        val lum = .2126f * work.red + .7152f * work.green + .0722f * work.blue
        val onW = if (lum > .55f) Color.Black else Color.White
        x.rect(0f, 0f, W, H, t.surface)
        x.base("${sch.name} - $year", M, M + 16, 20f, t.primary, true)
        val cycles = Engine.cyclesIn("$year-01-01", "$year-12-31", sch)
        val header = if (cycles.size > 1) L("Ciclos no ano", "Cycles in the year", "Ciclos del año") + ": ${cycles.joinToString(" → ") { Engine.cycleText(it) }}"
        else "${Engine.cycleText(cycles.firstOrNull() ?: sch.blocks)}  |  " + L("ciclo de", "cycle of", "ciclo de") + " ${Engine.cycleLen(cycles.firstOrNull() ?: sch.blocks)} " + L("dias", "days", "días")
        x.base(x.fit(header, 9f, 400, W - 2 * M), M, M + 32, 9f, t.onSurfaceVar, false)
        for (m in 0 until 12) {
            val bx = M + (m % 4) * cwid
            val by = 72 + (m / 4) * rw
            val cw = (cwid - 10) / 7
            val ch = (rw - 36) / 6
            val md = LocalDate.of(year, m + 1, 1)
            x.base(Dates.capitalize(Dates.monthName(md)), bx, by + 10, 10.5f, t.onSurface, true)
            weekLetters().forEachIndexed { i, c -> x.base(c.toString(), bx + i * cw + cw / 2, by + 22, 7f, t.onSurfaceVar, false, center = true) }
            val first = dow(md)
            for (d in 1..md.lengthOfMonth()) {
                val k = first + d - 1
                val cx = bx + (k % 7) * cw
                val cy = by + 27 + (k / 7) * ch
                val dt = md.withDayOfMonth(d)
                val ds = Dates.iso(dt)
                val st = Engine.status(dt, sch)
                val n = sch.notes[ds]
                x.rect(cx + 1, cy + 1, cw - 2, ch - 2, if (st.work) work else t.surfaceHigh)
                x.base(d.toString(), cx + cw / 2, cy + ch / 2 + 2.6f, 7.5f, if (st.work) onW else t.onSurfaceVar, st.work, center = true)
                if (n?.holiday == true) x.rect(cx + cw - 7, cy + 1, 6f, 6f, n.color?.let { hexColor(it) } ?: RED)
                sch.periods.firstOrNull { it.start <= ds && ds <= it.end }?.let { x.rect(cx + 1, cy + ch - 4, cw - 2, 3f, hexColor(it.color)) }
            }
        }
        val ly = H - M + 4
        x.rect(M, ly - 8, 9f, 9f, work); x.base(L("Trabalho", "Work", "Trabajo"), M + 14, ly, 8f, t.onSurface, false)
        x.rect(M + 80, ly - 8, 9f, 9f, t.surfaceHigh); x.base(L("Folga", "Off", "Libre"), M + 94, ly, 8f, t.onSurface, false)
        x.rect(M + 140, ly - 8, 9f, 9f, RED); x.base(L("Feriado", "Holiday", "Festivo"), M + 154, ly, 8f, t.onSurface, false)
        x.rect(M + 210, ly - 3, 9f, 3f, hexColor("#1a73e8")); x.base(L("Período", "Period", "Período"), M + 224, ly, 8f, t.onSurface, false)
        x.base(L("Gerado em ", "Generated on ", "Generado el ") + Dates.fmt(today), M + 290, ly, 8f, t.onSurfaceVar, false)
    }
}
