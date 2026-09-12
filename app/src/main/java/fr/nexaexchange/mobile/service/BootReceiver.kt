package fr.nexaexchange.mobile.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.nexaexchange.mobile.data.AlertStore

/**
 * Relance le moniteur apres un redemarrage du telephone.
 *
 * Sans cela, une alerte posee le soir serait morte au reveil, sans que l'utilisateur
 * en sache rien : le pire des comportements pour une fonction de securite.
 * On ne relance QUE si l'utilisateur avait lui-meme active la surveillance.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = AlertStore(context)
        if (!store.monitorEnabled) return
        if (store.all().none { it.enabled }) return
        PriceMonitorService.start(context)
    }
}
