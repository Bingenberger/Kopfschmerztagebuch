package de.kopfschmerztagebuch.erinnerung

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.kopfschmerztagebuch.MainActivity
import de.kopfschmerztagebuch.R
import de.kopfschmerztagebuch.data.Speicher
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Tägliche Erinnerung über den AlarmManager – kommt auch, wenn die App geschlossen ist.
 * Die Benachrichtigung erscheint nur, wenn für heute noch kein Eintrag gemacht wurde.
 */
object Erinnerung {
    private const val KANAL = "erinnerung"
    private const val NOTIFICATION_ID = 1

    fun planen(context: Context, zeit: String?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context)
        am.cancel(pi)
        if (zeit == null) return
        val uhrzeit = runCatching { LocalTime.parse(zeit) }.getOrElse { return }
        var naechste = LocalDateTime.of(LocalDate.now(), uhrzeit)
        if (!naechste.isAfter(LocalDateTime.now())) naechste = naechste.plusDays(1)
        val ms = naechste.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ErinnerungEmpfaenger::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun kanalAnlegen(context: Context) {
        val kanal = NotificationChannel(KANAL, "Tägliche Erinnerung", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Erinnert an den Tageseintrag, falls er noch fehlt."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(kanal)
    }

    fun darfBenachrichtigen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun zeigen(context: Context) {
        if (!darfBenachrichtigen(context)) return
        kanalAnlegen(context)
        val oeffnen = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, KANAL)
            .setSmallIcon(R.drawable.ic_benachrichtigung)
            .setContentTitle("Kopfschmerz-Tagebuch")
            .setContentText("Zeit für Deinen Eintrag! Danach wartet der Sprungturm. 🏊")
            .setContentIntent(oeffnen)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun entfernen(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

class ErinnerungEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val daten = Speicher.von(context).daten.value
        if (!daten.eintraege.containsKey(LocalDate.now().toString())) Erinnerung.zeigen(context)
        Erinnerung.planen(context, daten.erinnerung)
    }
}

/** Plant die Erinnerung nach Neustart, Update oder Zeitumstellung neu. */
class NeustartEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Erinnerung.planen(context, Speicher.von(context).daten.value.erinnerung)
    }
}
