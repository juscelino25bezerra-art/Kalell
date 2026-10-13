// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kalendrium.domain.Event
import java.time.LocalDate
import java.time.LocalDateTime

/** Resultado de abrir um arquivo de texto escolhido pelo usuário. */
sealed interface FileRead {
    data class Ok(val text: String) : FileRead
    data object Cancelled : FileRead
    /** Conteúdo que não é texto UTF-8 válido. */
    data object Invalid : FileRead
    /** Maior que o limite pedido (nada foi lido além do limite). */
    data object TooLarge : FileRead
    /** Não deu para abrir ou ler (sem seletor de arquivos no aparelho, permissão revogada, erro de E/S). */
    data object Failed : FileRead
}

/** Tudo o que depende do Android fica atrás desta interface (o restante é Kotlin/Compose puro). */
interface Platform {
    /**
     * Armazenamento local chave → texto (equivalente ao localStorage do PWA).
     * read devolve null só quando a chave não existe; um erro de leitura lança exceção,
     * para o app não confundir "não consegui ler" com "instalação nova" e gravar por cima.
     * Os dois podem ser chamados fora da thread principal.
     */
    @Throws(java.io.IOException::class)
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean

    fun today(): LocalDate = LocalDate.now()

    /** Idioma do aparelho (com o idioma por app do Android 13+, o escolhido para o Kalendrium). */
    fun systemLocale(): java.util.Locale = java.util.Locale.getDefault()
    fun now(): LocalDateTime = LocalDateTime.now()

    /** Salva um arquivo onde o usuário escolher. true = salvo, false = falhou, null = cancelado. */
    suspend fun saveFile(fileName: String, mime: String, bytes: ByteArray): Boolean?

    /** Gera um PDF de uma página (tamanho em pontos) com o desenho dado e salva onde o usuário escolher. */
    suspend fun savePdf(fileName: String, width: Int, height: Int, draw: DrawScope.() -> Unit): Boolean?

    /** Abre o seletor de arquivos do sistema e lê um arquivo de texto (limite em bytes). */
    suspend fun openTextFile(maxBytes: Long): FileRead

    /** Abre a folha de compartilhamento do sistema com a imagem PNG. */
    suspend fun shareImage(image: ImageBitmap, fileName: String, title: String, text: String): Boolean

    /** Agenda (ou reagenda) as notificações dos lembretes do calendário pessoal. */
    fun syncReminders(events: List<Event>) {}

    /** Pede permissão para mostrar notificações (Android 13+), quando ainda não foi concedida. */
    fun requestNotificationPermission() {}

    /** true quando o app não pode mostrar notificações (permissão negada ou desligada nos ajustes do sistema). */
    fun notificationsBlocked(): Boolean = false

    /** Pede a permissão (primeira vez) ou abre os ajustes de notificação do app. */
    fun enableNotifications() {}
}
