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
import fr.nexaexchange.mobile.data.PhoenixApi
import fr.nexaexchange.mobile.service.PriceMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widget d'ecran d'accueil : le prix du marche suivi, sans ouvrir l'application.
 *
 * C'est une capacite specifiquement Android, impossible depuis une page web quelle
 * qu'elle soit.
 *
 * 🔴 CORRIGE LE 13 SEPTEMBRE, APRES OBSERVATION SUR UN VRAI SEEKER.
 * La premiere version ne dessinait que ce que le service lui donnait. Consequence
 * filmee sur l'appareil : le widget qu'on vient de poser restait sur « waiting… »
 * jusqu'au cycle suivant du service, soit jusqu'a une minute — et indefiniment si le
 * moniteur n'etait pas active. Le code etait "correct" et le resultat inutilisable.
 *
 * Trois voies d'alimentation desormais, de la plus rapide a la plus lente :
 *  1. le prix memorise par le service, redessine INSTANTANEMENT a la creation ;
 *  2. une requete unique lancee par le widget lui-meme s'il n'a rien en memoire,
 *     ou si ce qu'il a est perime — le widget n'est donc plus dependant du service ;
 *  3. le service de fond, qui le rafraichit a chaque cycle de 60 s quand il tourne.
 */
class NexaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = AlertStore(context)
        val symbole = store.widgetSymbol
        val memorise = store.rememberedPrice(symbole)

        // 1. On dessine tout de suite ce qu'on sait, meme si c'est un peu ancien.
        //    Un prix d'il y a deux minutes vaut infiniment mieux qu'un « waiting… ».
        dessiner(context, manager, ids, symbole, memorise)

        // 2. Si rien en memoire ou si c'est perime, on va le chercher nous-memes.
        //    goAsync() permet a un BroadcastReceiver de vivre au-dela de onUpdate ;
        //    Android accorde une dizaine de secondes, le timeout ci-dessous reste
        //    tres en dessous.
        if (memorise == null || store.rememberedPriceAge() > FRAICHEUR_MS) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prix = withTimeoutOrNull(7_000) { PhoenixApi.fetchPrices() }
                    val p = prix?.get(symbole)
                    if (p != null) {
                        store.rememberPrice(symbole, p)
                        dessiner(context, manager, ids, symbole, p)
                    }
                } catch (e: Exception) {
                    // Le widget garde l'affichage dessine a l'etape 1. On ne remplace
                    // jamais une valeur connue par une erreur.
                } finally {
                    pending.finish()
                }
            }
        }
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
        /** Au-dela, le prix memorise est considere trop vieux pour etre affiche seul. */
        private const val FRAICHEUR_MS = 90_000L

        /** Appele par le service a chaque cycle de sondage. */
        fun refresh(context: Context, prix: Map<String, Double>) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NexaWidgetProvider::class.java))
            val store = AlertStore(context)
            val symbole = store.widgetSymbol
            val p = prix[symbole] ?: return
            // On memorise meme si aucun widget n'est pose : le jour ou l'utilisateur
            // en posera un, il aura une valeur a afficher des la premiere seconde.
            store.rememberPrice(symbole, p)
            if (ids.isEmpty()) return
            NexaWidgetProvider().dessiner(context, manager, ids, symbole, p)
        }
    }
}
