package com.kalendrium.domain

import java.util.Locale

/** Idiomas do app. */
enum class Lang(val code: String, val nativeName: String, val locale: Locale) {
    PT("pt", "Português", Locale("pt", "BR")),
    EN("en", "English", Locale("en", "US")),
    ES("es", "Español", Locale("es", "ES"));

    companion object {
        fun of(code: String?): Lang? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Idioma atual do app. A preferência ("system", "pt", "en" ou "es") fica no arquivo [PREF_KEY];
 * com "system" o app segue o idioma do aparelho e, se não for português nem espanhol, usa inglês.
 */
object I18n {
    const val PREF_KEY = "kalendrium.lang"
    const val SYSTEM = "system"

    @Volatile
    var lang: Lang = Lang.PT

    val locale: Locale get() = lang.locale

    /** Normaliza o conteúdo do arquivo de preferência. */
    fun pref(raw: String?): String = raw?.trim()?.takeIf { it == SYSTEM || Lang.of(it) != null } ?: SYSTEM

    fun resolve(pref: String?, system: Locale): Lang =
        Lang.of(pref(pref)) ?: when (system.language) {
            "pt" -> Lang.PT
            "es" -> Lang.ES
            else -> Lang.EN
        }
}

/** Texto no idioma atual: `L("Hoje", "Today", "Hoy")`. */
fun L(pt: String, en: String, es: String): String = when (I18n.lang) {
    Lang.PT -> pt
    Lang.EN -> en
    Lang.ES -> es
}

/** Singular ou plural no idioma atual: `"$n " + P(n, "dia", "dias", "day", "days", "día", "días")`. */
fun P(n: Int, pt1: String, ptN: String, en1: String, enN: String, es1: String, esN: String): String =
    if (n == 1) L(pt1, en1, es1) else L(ptN, enN, esN)

/** Ordinal curto: 1º (pt/es) ou 1st (en). */
fun ordinal(n: Int): String = when (I18n.lang) {
    Lang.EN -> n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    else -> "${n}º"
}

/** Cabeçalho da semana (começa no domingo, como o calendário). */
fun weekHeader(): List<String> = when (I18n.lang) {
    Lang.PT -> listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")
    Lang.EN -> listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
    Lang.ES -> listOf("DOM", "LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB")
}

/** Iniciais dos dias da semana, do domingo ao sábado (PDF anual). */
fun weekLetters(): String = L("DSTQQSS", "SMTWTFS", "DLMMJVS")
