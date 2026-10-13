// Kalendrium — Copyright (C) 2026 Juscelino Be
// SPDX-License-Identifier: GPL-3.0-or-later

package com.kalendrium.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Os textos de licença que o app mostra em Ajustes › Sobre › Licenças (sem internet) existem e
 * reproduzem os originais de third_party/licenses/ sem alteração.
 */
class LicenseAssetsTest {
    private fun file(path: String): File =
        listOf(File(path), File("..", path)).firstOrNull { it.exists() } ?: error("não encontrado: $path")

    private val notices by lazy { file("app/src/main/assets/licenca/TERCEIROS.txt").readText() }

    @Test
    fun gplDoAppIgualAoLicenseDaRaiz() {
        assertEquals(file("LICENSE").readText(), file("app/src/main/assets/licenca/LICENSE.txt").readText())
    }

    @Test
    fun avisosTrazemOsTextosOriginais() {
        listOf(
            "third_party/licenses/Apache-2.0.txt",
            "third_party/licenses/manrope/OFL.txt",
            "third_party/licenses/desugar_jdk_libs/LICENSE",
            "third_party/licenses/desugar_jdk_libs/THREETEN-BSD-NOTICE.txt",
            "third_party/licenses/kotlinx/kotlinx-coroutines-NOTICE.txt",
            "third_party/licenses/kotlinx/kotlinx-serialization-NOTICE.txt",
            "third_party/licenses/kotlin/NOTICE.txt",
        ).forEach { p ->
            assertTrue("$p não está inteiro em TERCEIROS.txt", notices.contains(file(p).readText().trimEnd('\n')))
        }
    }

    @Test
    fun avisosCitamOCodigoFonteDoDesugar() {
        val commit = Regex("Commit da versão: ([0-9a-f]{40})").find(file("third_party/licenses/desugar_jdk_libs/SOURCE.txt").readText())!!.groupValues[1]
        assertTrue(notices.contains(commit))
    }
}
