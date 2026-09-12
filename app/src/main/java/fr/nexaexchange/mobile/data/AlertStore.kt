package fr.nexaexchange.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stockage local des alertes de prix.
 *
 * SharedPreferences plutot que Room : une poignee d'alertes, lues par le service
 * de fond a chaque sondage. Une base de donnees serait du poids et une dependance
 * de plus pour zero benefice, et le service doit pouvoir lire sans coroutine.
 *
 * Aucune donnee personnelle n'est stockee : un symbole, un seuil, un horodatage.
 * Rien ne quitte le telephone.
 */
class AlertStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("nexa_alerts", Context.MODE_PRIVATE)

    fun all(): List<PriceAlert> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PriceAlert(
                    id = o.getLong("id"),
                    symbol = o.getString("symbol"),
                    direction = AlertDirection.valueOf(o.getString("direction")),
                    threshold = o.getDouble("threshold"),
                    createdAt = o.optLong("createdAt", 0L),
                    lastFiredAt = o.optLong("lastFiredAt", 0L),
                    enabled = o.optBoolean("enabled", true),
                )
            }
        } catch (e: Exception) {
            // Une preference corrompue ne doit pas empecher l'application de demarrer.
            emptyList()
        }
    }

    fun add(symbol: String, direction: AlertDirection, threshold: Double): PriceAlert {
        val alert = PriceAlert(
            id = System.currentTimeMillis(),
            symbol = symbol,
            direction = direction,
            threshold = threshold,
            createdAt = System.currentTimeMillis(),
        )
        save(all() + alert)
        return alert
    }

    fun remove(id: Long) = save(all().filterNot { it.id == id })

    fun markFired(id: Long, at: Long = System.currentTimeMillis()) =
        save(all().map { if (it.id == id) it.copy(lastFiredAt = at) else it })

    fun save(list: List<PriceAlert>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("symbol", a.symbol)
                    .put("direction", a.direction.name)
                    .put("threshold", a.threshold)
                    .put("createdAt", a.createdAt)
                    .put("lastFiredAt", a.lastFiredAt)
                    .put("enabled", a.enabled)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** Symbole suivi par le widget d'ecran d'accueil. */
    var widgetSymbol: String
        get() = prefs.getString("widget_symbol", "SOL") ?: "SOL"
        set(v) { prefs.edit().putString("widget_symbol", v).apply() }

    /** Le moniteur de fond doit-il tourner ? Relu au demarrage du telephone. */
    var monitorEnabled: Boolean
        get() = prefs.getBoolean("monitor_enabled", false)
        set(v) { prefs.edit().putBoolean("monitor_enabled", v).apply() }

    private companion object {
        const val KEY = "alerts_v1"
    }
}
