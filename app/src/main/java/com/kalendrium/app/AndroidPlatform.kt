package com.kalendrium.app

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.AtomicFile
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.kalendrium.domain.Event
import com.kalendrium.ui.FileRead
import com.kalendrium.ui.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executors

/** Seletores de arquivo registrados pela Activity atual (recriada em mudanças de configuração). */
class Launchers(
    val activity: Activity,
    val createJson: ActivityResultLauncher<String>,
    val createPdf: ActivityResultLauncher<String>,
    val open: ActivityResultLauncher<Array<String>>,
    val notifications: ActivityResultLauncher<String>,
)

/**
 * Implementação Android de [Platform]: dados em arquivos privados do app (só no aparelho),
 * Storage Access Framework para backup/PDF e FileProvider para compartilhar a imagem.
 */
class AndroidPlatform(private val app: Application) : Platform {
    var launchers: Launchers? = null
    private var pending: CompletableDeferred<Uri?>? = null

    /**
     * Chamado quando um arquivo escolhido chega sem nenhum pedido esperando: o Android encerrou o app
     * enquanto o seletor estava aberto e a operação se perdeu.
     */
    var onOrphanResult: (() -> Unit)? = null

    /** Chamado pela Activity com o resultado de qualquer seletor de arquivo. */
    fun onPicked(uri: Uri?) {
        val p = pending
        pending = null
        if (p != null) p.complete(uri) else if (uri != null) onOrphanResult?.invoke()
    }

    /** Sem nenhum app de documentos no aparelho, `launch` lança [ActivityNotFoundException]. */
    private class NoPicker : Exception()

    private suspend fun pick(launch: (Launchers) -> Unit): Uri? {
        val l = launchers ?: return null
        pending?.complete(null)
        val d = CompletableDeferred<Uri?>()
        pending = d
        try {
            launch(l)
        } catch (_: ActivityNotFoundException) {
            // sem isso a exceção subia até o viewModelScope e fechava o app
            if (pending === d) pending = null
            throw NoPicker()
        }
        return d.await()
    }

    // ---------- armazenamento local ----------
    private fun fileFor(key: String) = dataFile(app, key)

    /**
     * AtomicFile (do próprio Android): grava num arquivo novo, faz fsync e só então substitui o antigo.
     * Se a gravação for interrompida, a próxima leitura recupera a versão anterior completa.
     * Arquivo inexistente = null; qualquer outro erro de leitura sobe como exceção (ver [Platform.read]).
     */
    override fun read(key: String): String? {
        val f = AtomicFile(fileFor(key))
        return try {
            f.openRead().use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun write(key: String, value: String): Boolean {
        val f = AtomicFile(fileFor(key))
        val out = try { f.startWrite() } catch (_: Exception) { return false }
        return try {
            out.write(value.toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
            true
        } catch (_: Exception) {
            f.failWrite(out)
            false
        }
    }

    // ---------- lembretes ----------
    /** Reagendar até 64 alarmes fica numa fila própria, fora da thread principal e sempre em ordem. */
    private val reminderQueue = Executors.newSingleThreadExecutor()

    override fun syncReminders(events: List<Event>) {
        reminderQueue.execute { runCatching { Reminders.sync(app, events) } }
    }

    private val prefs get() = app.getSharedPreferences("permissions", Context.MODE_PRIVATE)

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val l = launchers ?: return
        prefs.edit().putBoolean("notificationsAsked", true).apply()
        runCatching { l.notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    override fun notificationsBlocked(): Boolean =
        app.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == false

    override fun enableNotifications() {
        // primeira vez no Android 13+: o pedido do sistema; depois disso, só pelos ajustes do app
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notificationsAsked", false) &&
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission()
            return
        }
        val activity = launchers?.activity ?: return
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName))
        }
        runCatching { activity.startActivity(intent) }
    }

    // ---------- arquivos escolhidos pelo usuário ----------
    override suspend fun saveFile(fileName: String, mime: String, bytes: ByteArray): Boolean? {
        val uri = try {
            pick { (if (mime == "application/pdf") it.createPdf else it.createJson).launch(fileName) }
        } catch (_: NoPicker) {
            return false
        } ?: return null
        return withContext(Dispatchers.IO) {
            val ok = runCatching {
                val out = runCatching { app.contentResolver.openOutputStream(uri, "wt") }.getOrNull() ?: app.contentResolver.openOutputStream(uri)
                out!!.use { it.write(bytes) }
            }.isSuccess
            // o documento acabou de ser criado pelo seletor: um backup pela metade não fica para trás parecendo válido
            if (!ok) runCatching { DocumentsContract.deleteDocument(app.contentResolver, uri) }
            ok
        }
    }

    override suspend fun savePdf(fileName: String, width: Int, height: Int, draw: DrawScope.() -> Unit): Boolean? {
        // desenho fora da thread principal: o PDF anual mede e desenha centenas de textos
        val bytes = withContext(Dispatchers.Default) { runCatching {
            val doc = PdfDocument()
            try {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(page.canvas), Size(width.toFloat(), height.toFloat())) { draw() }
                doc.finishPage(page)
                ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
            } finally {
                // libera a memória nativa do PdfDocument também quando o desenho falha
                doc.close()
            }
        }.getOrNull() } ?: return false
        return saveFile(fileName, "application/pdf", bytes)
    }

    override suspend fun openTextFile(maxBytes: Long): FileRead {
        val uri = try {
            pick { it.open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        } catch (_: NoPicker) {
            return FileRead.Failed
        } ?: return FileRead.Cancelled
        return withContext(Dispatchers.IO) {
            try {
                val size = app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
                } ?: -1L
                // o tamanho informado pelo provedor é só uma pré-checagem: a leitura abaixo conta os bytes de verdade
                if (size > maxBytes) return@withContext FileRead.TooLarge
                val bytes = (app.contentResolver.openInputStream(uri) ?: return@withContext FileRead.Failed).use { input ->
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return@withContext FileRead.TooLarge
                        buf.write(chunk, 0, n)
                    }
                    buf.toByteArray()
                }
                // UTF-8 estrito: bytes inválidos recusam o arquivo em vez de virar "�" silenciosamente
                val text = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
                FileRead.Ok(text)
            } catch (_: CharacterCodingException) {
                FileRead.Invalid
            } catch (_: Exception) {
                // FileNotFoundException, SecurityException (permissão do URI revogada), IOException do provedor
                FileRead.Failed
            }
        }
    }

    override suspend fun shareImage(image: ImageBitmap, fileName: String, title: String, text: String): Boolean {
        val activity = launchers?.activity ?: return false
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                // apaga só imagens antigas: o app de destino pode ainda estar enviando a anterior
                val old = System.currentTimeMillis() - 60 * 60 * 1000L
                val dir = File(app.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { if (it.lastModified() < old) it.delete() } }
                val f = File(dir, fileName)
                f.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(app, app.packageName + ".files", f)
            }.getOrNull()
        } ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching { activity.startActivity(Intent.createChooser(send, title)) }.isSuccess
    }
}
