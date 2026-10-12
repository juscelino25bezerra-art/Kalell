package com.kalendrium.app

import com.kalendrium.domain.L
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.kalendrium.R
import com.kalendrium.domain.Dates
import com.kalendrium.domain.Event
import com.kalendrium.domain.I18n
import com.kalendrium.domain.Personal
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors

/** Arquivo privado do app que guarda o valor de uma chave (mesmo esquema do [AndroidPlatform]). */
fun dataFile(ctx: Context, key: String): File =
    File(File(ctx.filesDir, "data").apply { mkdirs() }, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

/**
 * Lembretes do calendário pessoal com o AlarmManager do sistema (sem internet, sem serviço em segundo plano).
 * A cada alteração, os alarmes anteriores são cancelados e os próximos lembretes são agendados de novo.
 */
object Reminders {
    private const val CHANNEL = "lembretes"
    private const val ACTION_FIRE = "com.kalendrium.action.REMINDER"
    private const val MAX = 64
    private const val REQUEST_BASE = 1000

    /**
     * Cancela os alarmes anteriores e agenda os próximos [MAX] lembretes.
     * `@Synchronized`: o app (fila própria) e o [ReminderReceiver] (reinício, fuso) podem chamar ao mesmo tempo;
     * intercalados, um cancelava os alarmes do outro pela metade e deixava lembretes de eventos já excluídos.
     */
    @Synchronized
    fun sync(ctx: Context, events: List<Event>, now: LocalDateTime = LocalDateTime.now()) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        // os requestCodes são sempre REQUEST_BASE..REQUEST_BASE+MAX-1: cancela a faixa inteira em vez de depender
        // de uma lista gravada à parte (que podia ficar desatualizada e deixar alarmes órfãos)
        for (rc in REQUEST_BASE until REQUEST_BASE + MAX) {
            PendingIntent.getBroadcast(ctx, rc, fireIntent(ctx), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                am.cancel(it)
                it.cancel()
            }
        }
        val upcoming = events.mapNotNull { e -> e.reminderAt()?.takeIf { it.isAfter(now) }?.let { e to it } }
            .sortedBy { it.second }
            .take(MAX)
        var exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        // requestCode = posição na lista (0..63): dois eventos nunca disputam o mesmo alarme
        upcoming.forEachIndexed { i, (e, at) ->
            val rc = REQUEST_BASE + i
            val intent = fireIntent(ctx)
                .putExtra("code", rc)
                .putExtra("notif", e.id.hashCode())
                .putExtra("title", e.title)
                .putExtra("text", "${Dates.capitalize(Dates.weekdayDayMonth(Dates.parse(e.date)))} · ${e.timeText}")
            val pi = PendingIntent.getBroadcast(ctx, rc, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (exact) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                } catch (_: SecurityException) {
                    // Android 12: a permissão de alarme exato foi revogada depois da checagem; segue sem horário exato
                    exact = false
                }
            }
            if (!exact) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    /** Reagenda a partir do arquivo salvo (depois de reiniciar o aparelho, atualizar o app ou mudar o fuso). */
    fun syncFromStorage(ctx: Context) {
        loadLang(ctx)
        // mesma leitura atômica do app; se não der para ler, mantém os alarmes já agendados
        val raw = try {
            android.util.AtomicFile(dataFile(ctx, Personal.KEY)).openRead().use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (_: java.io.FileNotFoundException) {
            null
        } catch (_: Exception) {
            return
        }
        val loaded = Personal.loadChecked(raw)
        if (loaded.corruptRaw != null && loaded.data.events.isEmpty()) return
        sync(ctx, loaded.data.events)
    }

    /** Fora do app (alarme, reinício do aparelho) o idioma vem do arquivo de preferência. */
    fun loadLang(ctx: Context) {
        val pref = runCatching {
            android.util.AtomicFile(dataFile(ctx, I18n.PREF_KEY)).openRead().use { it.readBytes() }.toString(Charsets.UTF_8)
        }.getOrNull()
        I18n.lang = I18n.resolve(pref, java.util.Locale.getDefault())
    }

    private fun fireIntent(ctx: Context) = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)

    fun show(ctx: Context, code: Int, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, L("Lembretes", "Reminders", "Recordatorios"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = L("Lembretes dos eventos do calendário pessoal", "Reminders for personal calendar events", "Recordatorios de los eventos del calendario personal")
            })
            Notification.Builder(ctx, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL)
        }
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val n = builder
            .setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFF65558F.toInt())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        nm.notify(code, n)
    }

    fun isFire(intent: Intent) = intent.action == ACTION_FIRE
}

/**
 * Recebe o alarme do lembrete e os avisos do sistema que exigem reagendar.
 * A leitura do arquivo (até 10 MB de JSON) roda fora da thread principal com [goAsync]: no onReceive ela
 * disputava a thread principal com o início do app e podia estourar o tempo limite do broadcast (ANR).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val result = goAsync()
        worker.execute {
            try {
                if (Reminders.isFire(intent)) {
                    Reminders.loadLang(app)
                    // id da notificação vem do evento: lembretes diferentes não se substituem na bandeja
                    Reminders.show(app, intent.getIntExtra("notif", intent.getIntExtra("code", 0)), intent.getStringExtra("title") ?: L("Lembrete", "Reminder", "Recordatorio"), intent.getStringExtra("text") ?: "")
                } else {
                    // BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED
                    Reminders.syncFromStorage(app)
                }
            } catch (_: Exception) {
                // sem log: título e horário do evento são dados pessoais; os alarmes já agendados continuam valendo
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val worker = Executors.newSingleThreadExecutor()
    }
}
