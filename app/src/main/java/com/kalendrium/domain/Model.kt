package com.kalendrium.domain

/** Bloco de trabalho × folga. `name` vazio = sem nome. */
data class Block(val w: Int, val o: Int, val name: String = "")

/** A partir de `date`, o ciclo recomeça na posição `pos`. */
data class Change(val date: String, val pos: Int)

/** Configuração de ciclo: data de referência, blocos e mudanças. */
interface Cfg {
    val anchor: String
    val blocks: List<Block>
    val changes: List<Change>
}

data class PlainCfg(
    override val anchor: String,
    override val blocks: List<Block>,
    override val changes: List<Change>,
) : Cfg

/** Período preservado: a configuração que valia antes de `to`. */
data class Era(
    val to: String,
    override val anchor: String,
    override val blocks: List<Block>,
    override val changes: List<Change>,
) : Cfg

data class Note(
    val text: String,
    val holiday: Boolean,
    val color: String? = null,
    val align: String? = null,
)

data class Period(
    val id: String,
    val start: String,
    val end: String,
    val title: String,
    val color: String,
)

/**
 * Uma escala. A escala principal (a que no PWA fica na raiz do JSON) tem `id` vazio;
 * as demais ficam em `groups`.
 */
data class Schedule(
    val id: String,
    val name: String,
    val color: String,
    override val anchor: String,
    override val blocks: List<Block>,
    override val changes: List<Change>,
    val history: List<Era> = emptyList(),
    val notes: Map<String, Note> = emptyMap(),
    val periods: List<Period> = emptyList(),
) : Cfg {
    val isMain: Boolean get() = id.isEmpty()
}

data class AppData(
    val initialized: Boolean,
    val theme: String,
    val palette: String,
    val uiStyle: String,
    /** schedules[0] é a escala principal; as demais correspondem a `groups`. */
    val schedules: List<Schedule>,
    val active: String,
    /** Usa as cores do papel de parede (Android 12+). Chave nova: o PWA ignora. */
    val dynamicColor: Boolean = false,
    /** Esconde o conteúdo do app na tela de recentes (FLAG_SECURE). Chave nova: o PWA ignora. */
    val hideRecents: Boolean = false,
) {
    val main: Schedule get() = schedules.first()
    val groups: List<Schedule> get() = schedules.drop(1)
    val current: Schedule get() = schedules.firstOrNull { it.id == active && active.isNotEmpty() } ?: main

    fun replace(s: Schedule): AppData = copy(schedules = schedules.map { if (it.id == s.id) s else it })

    companion object {
        val PALETTES = listOf(
            "material", "gruvbox", "nord", "dracula", "catppuccin",
            "tokyonight", "solarized", "everforest", "onedark", "rosepine",
        )
        val THEMES = listOf("system", "light", "dark")
        val STYLES = listOf("material", "glass")
        const val MAX_SCHEDULES = 20
        /** Escalas além da principal (ficam em `groups`). */
        const val MAX_GROUPS = MAX_SCHEDULES - 1
        const val MAX_PERIODS = 200
        const val MAX_NOTES = 5000

        fun default(today: String) = AppData(
            initialized = false,
            theme = "system",
            palette = "material",
            uiStyle = "material",
            schedules = listOf(
                Schedule(
                    id = "", name = L("Escala A", "Schedule A", "Horario A"), color = "#65558f", anchor = today,
                    blocks = listOf(Block(4, 1)), changes = emptyList(),
                )
            ),
            active = "",
        )
    }
}

/** Gera ids curtos ("p" + tempo em base 36 + 4 caracteres aleatórios) que não colidem com os existentes. */
object Ids {
    private const val ALPHA = "0123456789abcdefghijklmnopqrstuvwxyz"
    private val rnd = java.util.Random()

    fun newId(prefix: String, max: Int, now: Long = System.currentTimeMillis(), exists: (String) -> Boolean): String {
        var id: String
        do {
            id = (prefix + now.toString(36) + (0 until 4).map { ALPHA[rnd.nextInt(36)] }.joinToString("")).take(max)
        } while (exists(id))
        return id
    }
}
