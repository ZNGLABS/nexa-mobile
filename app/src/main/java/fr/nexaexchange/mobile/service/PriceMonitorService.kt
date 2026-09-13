package fr.nexaexchange.mobile.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import fr.nexaexchange.mobile.data.AlertStore
import fr.nexaexchange.mobile.data.PhoenixApi
import fr.nexaexchange.mobile.widget.NexaWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Service de premier plan qui surveille les prix meme quand l'application est fermee.
 *
 * C'EST LE COEUR DE LA COUCHE NATIVE : une page web, meme installee, ne peut pas
 * faire cela. Fermez l'onglet et la surveillance s'arrete. Ici, l'utilisateur peut
 * eteindre l'ecran, lancer un jeu, ou redemarrer le telephone : le moniteur tourne.
 *
 * CHOIX DU RYTHME
 * 60 secondes. Un sondage plus rapide n'apporterait rien — les alertes de prix ne
 * se jouent pas a la seconde — et userait la batterie pour rien. Plus lent ferait
 * rater des mouvements brusques. Une seule requete HTTP par cycle, sur un point
 * d'entree qui renvoie les 82 marches d'un coup.
 *
 * ANTI-REBOND
 * Une alerte franchie ne se redeclenche pas avant [COOLDOWN_MS]. Sans cela, un prix
 * qui oscille autour du seuil enverrait une notification par minute.
 */
class PriceMonitorService : Service() {

    private var job: Job? = null
    private lateinit var scope: CoroutineScope
    private lateinit var store: AlertStore

    override fun onCreate() {
        super.onCreate()
        store = AlertStore(this)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android impose que startForeground soit appele dans les secondes qui
        // suivent le demarrage, sinon le systeme tue le service avec une
        // ForegroundServiceDidNotStartInTimeException. On le fait donc AVANT toute
        // requete reseau.
        startForeground(
            Notifications.SERVICE_NOTIF_ID,
            Notifications.serviceNotification(this, getString(fr.nexaexchange.mobile.R.string.monitor_starting)),
        )
        store.monitorEnabled = true
        if (job?.isActive != true) job = scope.launch { boucle() }
        return START_STICKY
    }

    private suspend fun boucle() {
        var echecs = 0
        while (scope.isActive) {
            try {
                val prix = PhoenixApi.fetchPrices()
                echecs = 0
                evaluer(prix)
                majNotification(prix)
                NexaWidgetProvider.refresh(applicationContext, prix)
            } catch (e: Exception) {
                echecs++
                // On ne masque pas la panne : au bout de trois echecs consecutifs,
                // la notification permanente le dit. Un moniteur qui pretend
                // surveiller alors qu'il est aveugle serait pire que pas de moniteur.
                if (echecs >= 3) {
                    majTexte(getString(fr.nexaexchange.mobile.R.string.monitor_offline))
                }
            }
            delay(INTERVAL_MS)
        }
    }

    private fun evaluer(prix: Map<String, Double>) {
        val maintenant = System.currentTimeMillis()
        for (alerte in store.all()) {
            if (!alerte.enabled) continue
            val p = prix[alerte.symbol] ?: continue
            if (!alerte.isTriggeredBy(p)) continue
            if (maintenant - alerte.lastFiredAt < COOLDOWN_MS) continue

            val sens = if (alerte.direction == fr.nexaexchange.mobile.data.AlertDirection.ABOVE)
                getString(fr.nexaexchange.mobile.R.string.alert_above)
            else
                getString(fr.nexaexchange.mobile.R.string.alert_below)

            Notifications.fireAlert(
                this,
                alerte.id,
                getString(fr.nexaexchange.mobile.R.string.alert_title, alerte.symbol),
                getString(
                    fr.nexaexchange.mobile.R.string.alert_body,
                    alerte.symbol, sens, fmt(alerte.threshold), fmt(p),
                ),
            )
            store.markFired(alerte.id, maintenant)
        }
    }

    private fun majNotification(prix: Map<String, Double>) {
        val alertes = store.all().count { it.enabled }
        val suivi = store.widgetSymbol
        val p = prix[suivi]
        val texte = if (p != null) {
            getString(fr.nexaexchange.mobile.R.string.monitor_running, suivi, fmt(p), alertes)
        } else {
            getString(fr.nexaexchange.mobile.R.string.monitor_running_nodata, alertes)
        }
        majTexte(texte)
    }

    private fun majTexte(texte: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(Notifications.SERVICE_NOTIF_ID, Notifications.serviceNotification(this, texte))
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val INTERVAL_MS = 60_000L
        private const val COOLDOWN_MS = 15 * 60_000L

        /**
         * Affiche un prix avec une precision adaptee a son ordre de grandeur.
         *
         * 🔴 Locale.US N'EST PAS FACULTATIF. Sans lui, String.format suit la langue du
         * telephone : sur un appareil francais, un prix s'affichait « $76 761,00 » et
         * une variation « -2,06% », au milieu d'une interface entierement en anglais.
         * Constate le 13 septembre 2026 sur la video de test tournee sur le Seeker.
         * Le prix d'un actif en dollars s'ecrit avec un point decimal, quelle que soit
         * la langue de l'utilisateur.
         */
        fun fmt(v: Double): String = when {
            v >= 1000 -> String.format(Locale.US, "%,.2f", v)
            v >= 1 -> String.format(Locale.US, "%.2f", v)
            v >= 0.01 -> String.format(Locale.US, "%.4f", v)
            else -> String.format(Locale.US, "%.6f", v)
        }

        /** Variation en pourcentage, signe compris. Meme regle de locale. */
        fun fmtPct(v: Double): String =
            (if (v >= 0) "+" else "") + String.format(Locale.US, "%.2f", v) + "%"

        fun start(context: Context) {
            val i = Intent(context, PriceMonitorService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            AlertStore(context).monitorEnabled = false
            context.stopService(Intent(context, PriceMonitorService::class.java))
        }
    }
}
