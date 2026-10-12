package com.kalendrium.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kalendrium.domain.AppData
import com.kalendrium.domain.Block
import com.kalendrium.domain.CalendarMode
import com.kalendrium.domain.Change
import com.kalendrium.domain.Codec
import com.kalendrium.domain.Event
import com.kalendrium.domain.EventKind
import com.kalendrium.domain.Period
import com.kalendrium.domain.Personal
import com.kalendrium.domain.PersonalData
import com.kalendrium.domain.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

/** AppController com uma plataforma falsa (JVM, sem emulador). Disco e desenho rodam na mesma thread. */
class AppControllerTest {
    class Fake(init: Map<String, String> = emptyMap()) : Platform {
        val store = HashMap(init)
        val unreadable = HashSet<String>()
        val unwritable = HashSet<String>()
        var file: FileRead = FileRead.Cancelled
        var synced: List<Event>? = null
        var writes = 0
        override fun read(key: String): String? {
            if (key in unreadable) throw IOException("falha simulada")
            return store[key]
        }
        override fun write(key: String, value: String): Boolean {
            if (key in unwritable) return false
            writes++
            store[key] = value
            return true
        }
        var locale: java.util.Locale = java.util.Locale("pt", "BR")
        override fun systemLocale() = locale
        override fun today(): LocalDate = LocalDate.of(2026, 10, 3)
        override fun now(): LocalDateTime = LocalDateTime.of(2026, 10, 3, 10, 30)
        override suspend fun saveFile(fileName: String, mime: String, bytes: ByteArray) = true
        override suspend fun savePdf(fileName: String, width: Int, height: Int, draw: DrawScope.() -> Unit) = true
        override suspend fun openTextFile(maxBytes: Long): FileRead = file
        override suspend fun shareImage(image: ImageBitmap, fileName: String, title: String, text: String) = true
        override fun syncReminders(events: List<Event>) { synced = events }
    }

    private val today = "2026-10-03"
    private fun ctl(p: Fake) = AppController(p, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Dispatchers.Unconfined, Dispatchers.Unconfined)
    private fun work(blocks: List<Block> = listOf(Block(4, 1))) = AppData.default(today).let { d -> d.copy(initialized = true).replace(d.main.copy(blocks = blocks, anchor = "2026-10-01")) }
    private fun AppController.lastMsg() = messages.lastOrNull()?.text
    private fun AppController.answerAll(v: Boolean) { while (messages.isNotEmpty()) answer(messages.first(), v) }
    private fun form(title: String = "Dentista", allDay: Boolean = false, start: String = "14:00", end: String = "15:00", remind: Int = -1) =
        EventForm(title, EventKind.APPOINTMENT, today, allDay, start, end, "", "#9334e6", remind)

    // ---------- modo ----------
    @Test fun `instalacao nova abre no Pessoal e usuario antigo no Work`() {
        assertEquals(CalendarMode.PERSONAL, ctl(Fake()).mode)
        assertEquals(CalendarMode.WORK, ctl(Fake(mapOf(Codec.KEY to Codec.encode(work())))).mode)
    }

    @Test fun `selectMode mantem o mes fecha a folha e grava o modo`() {
        val p = Fake()
        val c = ctl(p)
        c.nextMonth()
        val view = c.view
        c.openEvent()
        c.selectMode(CalendarMode.WORK)
        assertEquals(CalendarMode.WORK, c.mode)
        assertEquals(view, c.view)
        assertNull(c.sheet)
        assertEquals("work", p.store[Personal.MODE_KEY])
        assertEquals(CalendarMode.WORK, ctl(p).mode)    // reabrindo
    }

    // ---------- visualizações ----------
    @Test fun `visualizacao comeca na mensal e a escolha fica gravada`() {
        val p = Fake()
        val c = ctl(p)
        assertEquals(ViewMode.MONTH, c.viewMode)
        c.selectView(ViewMode.WEEK)
        assertEquals("week", p.store[ViewMode.KEY])
        assertEquals(ViewMode.WEEK, ctl(p).viewMode)    // reabrindo
        p.store[ViewMode.KEY] = "lixo"
        assertEquals(ViewMode.MONTH, ctl(p).viewMode)
    }

    @Test fun `setas andam por dia semana mes e ano conforme a visualizacao`() {
        val c = ctl(Fake())
        val t = LocalDate.of(2026, 10, 3)
        c.selectView(ViewMode.DAY); c.next()
        assertEquals(t.plusDays(1), c.selected)
        c.selectView(ViewMode.WEEK); c.prev()
        assertEquals(t.minusDays(6), c.selected)
        assertEquals(LocalDate.of(2026, 9, 1), c.view)    // o mês acompanha o dia
        c.selectView(ViewMode.YEAR); c.next()
        assertEquals(2027, c.view.year)
        c.goToday()
        assertEquals(t, c.selected)
        assertEquals(LocalDate.of(2026, 10, 1), c.view)
    }

    @Test fun `toque no dia depende da visualizacao`() {
        val c = ctl(Fake())
        c.tapDay("2026-10-10")    // mensal no Pessoal: abre o dia
        assertEquals(ViewMode.DAY, c.viewMode)
        assertEquals(LocalDate.of(2026, 10, 10), c.selected)
        c.selectView(ViewMode.MONTH_DAY)
        c.tapDay("2026-10-12")    // mensal + diária: seleciona
        assertEquals(LocalDate.of(2026, 10, 12), c.selected)
        assertNull(c.sheet)
        c.tapDay("2026-10-12")    // segundo toque: novo evento
        assertTrue(c.sheet is Sheet.EventEdit)
    }

    @Test fun `ano abre o mes na visualizacao mensal`() {
        val c = ctl(Fake())
        c.selectView(ViewMode.YEAR)
        c.openMonth(2027, 3)
        assertEquals(ViewMode.MONTH, c.viewMode)
        assertEquals(LocalDate.of(2027, 3, 1), c.view)
    }

    // ---------- eventos ----------
    @Test fun `submitEvent valida titulo e horario e grava com lembretes`() {
        val p = Fake()
        val c = ctl(p)
        assertFalse(c.submitEvent(null, form(title = " ")))
        assertFalse(c.submitEvent(null, form(start = "")))
        assertFalse(c.submitEvent(null, form(start = "15:00", end = "14:00")))
        c.answerAll(true)
        assertTrue(c.submitEvent(null, form(remind = 30)))
        assertEquals(1, Personal.load(p.store[Personal.KEY]).events.size)
        assertEquals(1, p.synced?.size)
        c.deleteEvent(c.personal.events.single())
        c.answerAll(true)
        assertTrue(Personal.load(p.store[Personal.KEY]).events.isEmpty())
    }

    @Test fun `resumo conta por tipo`() {
        val pd = PersonalData(listOf(
            Event("a", today, "Dentista", EventKind.APPOINTMENT, false, "14:00"),
            Event("b", today, "Remédio", EventKind.REMINDER, false, "08:00"),
            Event("c", today, "Remédio 2", EventKind.REMINDER, false, "20:00"),
        ))
        val s = personalSummary(pd, LocalDate.of(2026, 10, 3), java.time.LocalTime.of(10, 30))
        assertTrue(s, s.startsWith("1 compromisso e 2 lembretes hoje"))
        assertTrue(s, s.contains("Próximo às 14:00"))
        assertEquals("Nenhum compromisso para hoje", personalSummary(PersonalData(), LocalDate.of(2026, 10, 3), java.time.LocalTime.NOON))
    }

    // ---------- C2 / C3: leitura ----------
    @Test fun `C2 calendario pessoal corrompido avisa e guarda copia`() {
        val raw = """{"events":["""
        val p = Fake(mapOf(Personal.KEY to raw))
        val c = ctl(p)
        assertTrue(c.messages.any { it.text.contains("calendário pessoal") })
        assertEquals(raw, p.store[AppController.PERSONAL_BAK])
    }

    @Test fun `C3 falha de leitura nao grava por cima`() {
        val real = Codec.encode(work())
        val p = Fake(mapOf(Codec.KEY to real))
        p.unreadable.add(Codec.KEY)
        val c = ctl(p)
        assertEquals(AppController.READ_FAILED, c.lastMsg())
        c.answerAll(true)
        c.setTheme("dark")
        assertEquals(real, p.store[Codec.KEY])             // dados reais intactos
        assertEquals(AppController.READ_FAILED, c.lastMsg())
    }

    @Test fun `C3 arquivo vazio avisa em vez de comecar do zero`() {
        val c = ctl(Fake(mapOf(Codec.KEY to "")))
        assertTrue(c.messages.any { it.text.contains("vazios") })
    }

    @Test fun `falha de gravacao avisa uma vez`() {
        val p = Fake()
        p.unwritable.add(Personal.KEY)
        val c = ctl(p)
        c.submitDayNote(today, "a", "#f9ab00")
        c.submitDayNote(today, "b", "#f9ab00")
        assertEquals(1, c.messages.count { it.text == AppController.WRITE_FAILED })
    }

    // ---------- C7 / C8 / C9: backup ----------
    private fun backup(personal: PersonalData? = PersonalData(listOf(Event("x", today, "Do backup")))) =
        Codec.encodeBackup(work(listOf(Block(6, 2))), personal)

    @Test fun `C8 cancelar a confirmacao nao altera nada`() {
        val p = Fake()
        val c = ctl(p)
        p.file = FileRead.Ok(backup())
        c.importBackup()
        c.answerAll(false)
        assertFalse(c.ready)
        assertTrue(c.personal.events.isEmpty())
        assertEquals(0, p.writes)
    }

    @Test fun `C8 falha na parte pessoal desfaz a escala`() {
        val before = Codec.encode(work())
        val p = Fake(mapOf(Codec.KEY to before))
        val c = ctl(p)
        p.unwritable.add(Personal.KEY)
        p.file = FileRead.Ok(backup())
        c.importBackup()
        c.answerAll(true)
        assertEquals(before, p.store[Codec.KEY])
        assertEquals(listOf(Block(4, 1)), c.current.blocks)
        assertTrue(c.personal.events.isEmpty())
    }

    @Test fun `C8 parte pessoal com tipo errado recusa o arquivo e itens invalidos sao informados`() {
        val p = Fake()
        val c = ctl(p)
        p.file = FileRead.Ok(backup(null).dropLast(2) + ""","personal":{"events":"x"}}""")
        c.importBackup()
        assertEquals("Backup inválido.", c.lastMsg())
        c.answerAll(true)
        p.file = FileRead.Ok(backup(null).dropLast(2) + ""","personal":{"events":[{"id":"a","date":"2026-10-03","title":"Ok"},{"id":"b"}]}}""")
        c.importBackup()
        assertTrue(c.lastMsg()!!.contains("1 evento(s) deste arquivo estão inválidos"))
    }

    @Test fun `C9 importar e depois restaurar os dados anteriores`() {
        val mine = PersonalData(listOf(Event("m", today, "Meu")))
        val p = Fake(mapOf(Codec.KEY to Codec.encode(work()), Personal.KEY to Personal.encode(mine)))
        val c = ctl(p)
        assertFalse(c.previousAvailable)
        p.file = FileRead.Ok(backup())
        c.importBackup()
        c.answerAll(true)
        assertEquals(listOf(Block(6, 2)), c.current.blocks)
        assertEquals("x", c.personal.events.single().id)
        assertTrue(c.previousAvailable)
        c.restorePrevious()
        c.answerAll(true)
        assertEquals(listOf(Block(4, 1)), c.current.blocks)
        assertEquals("m", c.personal.events.single().id)
        assertEquals(listOf(Block(4, 1)), Codec.load(p.store[Codec.KEY], today).data.main.blocks)
    }

    @Test fun `C7 limite de importacao e de 10 MB`() {
        var asked = 0L
        val p = object : Platform by Fake() {
            override suspend fun openTextFile(maxBytes: Long): FileRead { asked = maxBytes; return FileRead.Cancelled }
        }
        ctl2(p).importBackup()
        assertEquals(10_000_000L, asked)
    }

    private fun ctl2(p: Platform) = AppController(p, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), Dispatchers.Unconfined, Dispatchers.Unconfined)

    // ---------- auditoria 5.3 ----------
    @Test fun `importar JSON muito aninhado recusa sem fechar o app`() {
        val before = Codec.encode(work())
        val p = Fake(mapOf(Codec.KEY to before))
        val c = ctl(p)
        val writes = p.writes
        p.file = FileRead.Ok("[".repeat(100_000) + "]".repeat(100_000))
        c.importBackup()
        assertEquals("Backup inválido.", c.lastMsg())
        assertEquals(writes, p.writes)
        assertEquals(before, p.store[Codec.KEY])
    }

    @Test fun `arquivo grande demais ou ilegivel tem mensagem propria e nada muda`() {
        val p = Fake()
        val c = ctl(p)
        p.file = FileRead.TooLarge
        c.importBackup()
        assertTrue(c.lastMsg()!!.contains("10 MB"))
        c.answerAll(true)
        p.file = FileRead.Failed
        c.importBackup()
        assertTrue(c.lastMsg()!!.contains("Não foi possível abrir o arquivo"))
        assertEquals(0, p.writes)
    }

    @Test fun `sem conseguir guardar a copia dos dados atuais a importacao nao substitui nada`() {
        val before = Codec.encode(work())
        val mine = Personal.encode(PersonalData(listOf(Event("m", today, "Meu"))))
        val p = Fake(mapOf(Codec.KEY to before, Personal.KEY to mine))
        val c = ctl(p)
        p.unwritable.add(AppController.BAK)
        p.file = FileRead.Ok(backup())
        c.importBackup()
        c.answerAll(true)
        assertEquals(before, p.store[Codec.KEY])
        assertEquals(mine, p.store[Personal.KEY])
        assertEquals(listOf(Block(4, 1)), c.current.blocks)
        assertEquals("m", c.personal.events.single().id)
        assertFalse(c.messages.any { it.text == "Backup importado." })
    }

    @Test fun `dados corrompidos sem copia possivel nao sao sobrescritos pelos padroes`() {
        val raw = "{corrompido"
        val p = Fake(mapOf(Codec.KEY to raw))
        p.unwritable.add(AppController.BAK)
        val c = ctl(p)
        assertTrue(c.messages.any { it.text == AppController.READ_FAILED })
        assertFalse(c.messages.any { it.text.contains("Uma cópia dos dados originais foi mantida") })
        c.answerAll(true)
        c.setTheme("dark")
        assertEquals(raw, p.store[Codec.KEY])
    }

    @Test fun `copia de seguranca informa falha de gravacao`() {
        val p = Fake(mapOf(AppController.BAK to "antigo"))
        val s = Storage(p, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, {}, {})
        p.unwritable.add(AppController.BAK2)
        assertFalse(s.keepBak(AppController.BAK, AppController.BAK2, "novo"))
        assertEquals("antigo", p.store[AppController.BAK])   // a cópia anterior não é perdida
        p.unwritable.clear()
        assertTrue(s.keepBak(AppController.BAK, AppController.BAK2, "novo"))
        assertEquals("antigo", p.store[AppController.BAK2])
        assertEquals("novo", p.store[AppController.BAK])
    }

    // ---------- D1 / D2 ----------
    @Test fun `D1 excluir mudanca pede confirmacao`() {
        val d = work().let { it.replace(it.main.copy(changes = listOf(Change("2026-10-10", 2)))) }
        val c = ctl(Fake(mapOf(Codec.KEY to Codec.encode(d))))
        c.deleteChange("2026-10-10")
        assertEquals(1, c.current.changes.size)
        c.answerAll(true)
        assertTrue(c.current.changes.isEmpty())
    }

    @Test fun `D2 limite de periodos na criacao`() {
        val periods = (0 until AppData.MAX_PERIODS).map { Period("p$it", "2026-01-01", "2026-01-02", "", "#1a73e8") }
        val d = work().let { it.replace(it.main.copy(periods = periods)) }
        val c = ctl(Fake(mapOf(Codec.KEY to Codec.encode(d))))
        assertFalse(c.submitPeriod(null, "2026-02-01", "2026-02-02", "Novo", "#1a73e8"))
        assertTrue(c.lastMsg()!!.contains("Limite de 200 períodos"))
        assertEquals(AppData.MAX_PERIODS, c.current.periods.size)
    }

    // ---------- C5 pelo controlador ----------
    @Test fun `C5 manter dias anteriores com a data de referencia na data a partir de`() {
        val c = ctl(Fake(mapOf(Codec.KEY to Codec.encode(work()))))
        val sc = c.current
        val rows = listOf(BlockRow("5", "2", "", 0))
        kotlinx.coroutines.runBlocking { c.submitCycle(CycleMode.Edit, sc, CycleForm(rows, sc.name, sc.color, "2026-11-01", true, "2026-11-01")) }
        val st = com.kalendrium.domain.Engine.status(LocalDate.of(2026, 11, 1), c.current)
        assertTrue(st.work)
        assertEquals(1, st.n)
        assertEquals("2026-11-01", c.current.history.single().to)
    }

    // ---------- C6 ----------
    @Test fun `C6 card Hoje mostra ferias e a volta ao trabalho`() {
        val sch = work().main.copy(periods = listOf(Period("f", "2026-10-01", "2026-10-20", "Férias", "#1a73e8")))
        val (text, isWork) = workSummary(LocalDate.of(2026, 10, 3), sch)
        assertFalse(isWork)
        assertTrue(text, text.startsWith("Férias até 20 de outubro"))
        assertTrue(text, text.contains("Volta ao trabalho"))
        val (soon, _) = workSummary(LocalDate.of(2026, 9, 25), sch)
        assertTrue(soon, soon.contains("Férias a partir de"))
    }

    // ---------- C12 ----------
    @Test fun `C12 estado das notificacoes acompanha a plataforma`() {
        var blocked = true
        val p = object : Platform by Fake() { override fun notificationsBlocked() = blocked }
        val c = ctl2(p)
        assertTrue(c.notifBlocked)
        blocked = false
        c.onResume()
        assertFalse(c.notifBlocked)
    }

    @Test fun `D3 relogio avanca com tick`() {
        var now = LocalDateTime.of(2026, 10, 3, 23, 59)
        val p = object : Platform by Fake() {
            override fun now() = now
            override fun today(): LocalDate = now.toLocalDate()
        }
        val c = ctl2(p)
        now = LocalDateTime.of(2026, 10, 4, 0, 1)
        c.tick()
        assertEquals(LocalDate.of(2026, 10, 4), c.today)
        assertEquals(1, c.now().minute)
        assertNotNull(c.clock)
    }

    @Test fun `idioma segue o aparelho e a escolha fica gravada`() {
        val p = Fake()
        p.locale = java.util.Locale("es", "AR")
        val c = ctl2(p)
        assertEquals(com.kalendrium.domain.Lang.ES, c.lang)
        assertEquals("system", c.langPref)
        c.setLanguage("en")
        assertEquals(com.kalendrium.domain.Lang.EN, c.lang)
        assertEquals("en", p.store[com.kalendrium.domain.I18n.PREF_KEY])
        // reaberto: a escolha vale mesmo com o aparelho em espanhol
        val c2 = ctl2(p)
        assertEquals(com.kalendrium.domain.Lang.EN, c2.lang)
        assertEquals("en", c2.langPref)
        c2.setLanguage("system")
        assertEquals(com.kalendrium.domain.Lang.ES, c2.lang)
        // o idioma do aparelho muda com o app em segundo plano
        p.locale = java.util.Locale.GERMANY
        c2.onResume()
        assertEquals(com.kalendrium.domain.Lang.EN, c2.lang)
        p.locale = java.util.Locale("pt", "BR")
        c2.onResume()
        assertEquals(com.kalendrium.domain.Lang.PT, c2.lang)
    }

    @Test fun `trocar o idioma reagenda os lembretes com o texto novo`() {
        var synced = 0
        val p = object : Platform by Fake() { override fun syncReminders(events: List<com.kalendrium.domain.Event>) { synced++ } }
        val c = ctl2(p)
        val before = synced
        c.setLanguage("es")
        assertEquals(before + 1, synced)
        c.setLanguage("es")
        assertEquals(before + 1, synced)
        c.setLanguage("pt")
    }
}
