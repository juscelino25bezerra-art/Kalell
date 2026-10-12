package com.kalendrium.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Gravação atômica e leitura dos arquivos do app no aparelho (C3). */
@RunWith(AndroidJUnit4::class)
class AndroidPlatformTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val p = AndroidPlatform(app)
    private val key = "teste.instrumentado"

    @Test fun arquivoInexistenteDevolveNull() {
        dataFile(app, key).delete()
        assertNull(p.read(key))
    }

    @Test fun gravaELeAMesmaCoisa() {
        val big = "x".repeat(2_000_000) + "fim"
        assertTrue(p.write(key, big))
        assertEquals(big, p.read(key))
        assertTrue(p.write(key, "curto"))
        assertEquals("curto", p.read(key))
    }

    @Test fun gravacaoInterrompidaMantemAVersaoAnterior() {
        assertTrue(p.write(key, "bom"))
        // simula queda no meio: AtomicFile deixa o arquivo novo pela metade e o .bak com a versão boa
        val f = dataFile(app, key)
        val bak = File(f.path + ".bak")
        f.renameTo(bak)
        f.writeText("pela met")
        assertEquals("bom", p.read(key))
    }

    @Test fun erroDeLeituraNaoViraArquivoInexistente() {
        val f = dataFile(app, key)
        f.delete()
        f.mkdirs()   // um diretório no lugar do arquivo: abrir para leitura falha com IOException
        try {
            p.read(key)
            fail("deveria lançar IOException")
        } catch (_: IOException) {
        } finally {
            f.delete()
        }
    }
}
