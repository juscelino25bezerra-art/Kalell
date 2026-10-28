// Kalell — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalell.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Gravação e leitura dos arquivos do app fora da thread principal.
 *
 * - [persist] atualiza o arquivo em segundo plano numa fila serial (`io` com paralelismo 1): o estado
 *   na tela muda na hora e a gravação nunca roda em paralelo com outra. Se várias gravações da mesma
 *   chave se acumularem, só a mais recente é escrita.
 * - [readSafe] diferencia "não existe" (null) de "não consegui ler" (marca a chave em [readFailed]).
 *   Uma chave que falhou na leitura não é gravada até o app ser reaberto, para não sobrescrever os
 *   dados reais com os padrões.
 */
class Storage(
    private val platform: Platform,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onWriteFailed: () -> Unit,
    private val onBlocked: () -> Unit,
) {
    private val pending = ConcurrentHashMap<String, () -> String>()
    private val readFailed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun failed(key: String) = key in readFailed

    /** Leitura direta (chame dentro de `io`). */
    fun readSafe(key: String): String? = try {
        platform.read(key)
    } catch (_: Exception) {
        readFailed.add(key)
        null
    }

    suspend fun read(key: String): String? = withContext(io) { readSafe(key) }

    /** Grava em segundo plano o valor mais recente da chave. `encode` roda fora da thread principal. */
    fun persist(key: String, encode: () -> String) {
        if (key in readFailed) { onBlocked(); return }
        pending[key] = encode
        scope.launch {
            val ok = withContext(io) { pending.remove(key)?.let { platform.write(key, it()) } ?: true }
            if (!ok) onWriteFailed()
        }
    }

    /**
     * Grava agora e devolve se deu certo (para operações que precisam saber, como importar). `encode` roda em `io`.
     * Libera a chave se ela tinha falhado na leitura: o usuário escolheu substituir aqueles dados.
     */
    suspend fun persistNow(key: String, encode: () -> String): Boolean = withContext(io) {
        pending.remove(key)
        platform.write(key, encode()).also { if (it) readFailed.remove(key) }
    }

    /**
     * Impede gravações na chave até o app ser reaberto (como uma falha de leitura): usado quando não foi
     * possível guardar a cópia de dados corrompidos, para o original não ser substituído pelos padrões.
     */
    fun block(key: String) { readFailed.add(key) }

    /**
     * Guarda `raw` em `bak`, movendo a cópia anterior para `bak2` (chame dentro de `io`).
     * Devolve false se a cópia não pôde ser gravada (quem chama decide se pode seguir sem ela).
     */
    fun keepBak(bak: String, bak2: String, raw: String): Boolean {
        if (raw.isBlank()) return true
        val b = readSafe(bak)
        if (b == raw) return true
        if (!b.isNullOrBlank() && !platform.write(bak2, b)) return false
        return platform.write(bak, raw)
    }
}
