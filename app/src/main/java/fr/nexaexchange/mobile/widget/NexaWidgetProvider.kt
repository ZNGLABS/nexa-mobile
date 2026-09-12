package fr.nexaexchange.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import fr.nexaexchange.mobile.MainActivity
import fr.nexaexchange.mobile.R
import fr.nexaexchange.mobile.data.AlertStore
import fr.nexaexchange.mobile.service.PriceMonitorService

/**
 * Widget d'ecran d'accueil : le prix du marche suivi, sans ouvrir l'application.
 *
 * C'est une capacite specifiquement Android, impossible depuis une page web quelle
 * qu'elle soit. Le widget ne fait AUCUNE requete lui-meme : il est rafraichi par le
 * service de fond, qui a deja les prix en main. Un widget qui irait chercher ses
 * propres donnees doublerait le trafic et la consommation pour afficher la meme chose.
 */
class NexaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val symbole = AlertStore(context).widgetSymbol
        // Au moment ou Android demande une mise a jour (ajout du widget, redemarrage),
        // on n'a pas forcement de prix sous la main. On dessine alors l'etat d'attente
        // plutot qu'un zero, et le service remplira des son prochain cycle.
        dessiner(context, manager, ids, symbole, null)
    }

    private fun dessiner(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
        symbole: String,
        prix: Double?,
    ) {
        for (id in ids) {
            val vues = RemoteViews(context.packageName, R.layout.widget_nexa)
            vues.setTextViewText(R.id.widget_symbol, symbole)
            vues.setTextViewText(
                R.id.widget_price,
                if (prix != null) "$" + PriceMonitorService.fmt(prix)
                else context.getString(R.string.widget_waiting),
            )
            vues.setTextColor(R.id.widget_price, if (prix != null) Color.WHITE else 0xFF8A8A8A.toInt())

            val ouvrir = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            vues.setOnClickPendingIntent(R.id.widget_root, ouvrir)
            manager.updateAppWidget(id, vues)
        }
    }

    companion object {
        /** Appele par le service a chaque cycle de sondage. */
        fun refresh(context: Context, prix: Map<String, Double>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NexaWidgetProvider::class.java))
            if (ids.isEmpty()) return   // aucun widget pose : rien a faire
            val symbole = AlertStore(context).widgetSymbol
            NexaWidgetProvider().dessiner(context, manager, ids, symbole, prix[symbole])
        }
    }
}
