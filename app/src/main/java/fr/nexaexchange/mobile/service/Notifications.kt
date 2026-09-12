package fr.nexaexchange.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.nexaexchange.mobile.MainActivity
import fr.nexaexchange.mobile.R

/**
 * Deux canaux, volontairement separes.
 *
 * Le canal du service tourne en IMPORTANCE_LOW : Android exige une notification
 * permanente pour un service de premier plan, mais elle ne doit ni sonner ni
 * vibrer, sinon l'utilisateur desinstalle l'application au bout d'une journee.
 *
 * Le canal des alertes tourne en IMPORTANCE_HIGH : c'est l'evenement que
 * l'utilisateur a explicitement demande, il doit passer devant.
 */
object Notifications {

    const val CHANNEL_SERVICE = "nexa_monitor"
    const val CHANNEL_ALERTS = "nexa_alerts"
    const val SERVICE_NOTIF_ID = 1001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.channel_monitor),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_monitor_desc)
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
                enableVibration(true)
            }
        )
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Notification permanente du service. Discrete par construction. */
    fun serviceNotification(context: Context, text: String) =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_nexa)
            .setContentTitle(context.getString(R.string.monitor_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
            .build()

    /**
     * Alerte de franchissement. L'identifiant derive de celui de l'alerte pour que
     * deux alertes distinctes ne s'ecrasent pas l'une l'autre.
     */
    fun fireAlert(context: Context, alertId: Long, title: String, body: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_nexa)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(alertId.toInt(), n)
        } catch (se: SecurityException) {
            // POST_NOTIFICATIONS refusee : on ne fait pas planter le service pour
            // autant, le moniteur continue et l'interface signale le probleme.
        }
    }
}
