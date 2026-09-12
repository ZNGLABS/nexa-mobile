package fr.nexaexchange.mobile.data

/**
 * Un marche perpetuel Phoenix Flight.
 *
 * Les champs reprennent exactement ceux renvoyes par
 * GET /v1/view/exchange/markets et GET /v1/markets/stats/latest,
 * releves sur l'API en production le 12 septembre 2026. Rien n'est invente ici :
 * si un champ manque cote serveur, il vaut null et l'interface affiche un tiret
 * plutot qu'un zero trompeur.
 */
data class Market(
    val symbol: String,
    val assetId: Int,
    val name: String,
    val displayColor: String?,
    val logoUri: String?,
    val baseLotsDecimals: Int,
    val maxLeverage: Int,
    val takerFee: Double,
    // Renseignes depuis /v1/markets/stats/latest, absents tant que les stats n'ont
    // pas ete chargees.
    val markPrice: Double? = null,
    val oraclePrice: Double? = null,
    val prevDayMarkPrice: Double? = null,
    val dayVolumeUsd: Double? = null,
    val openInterest: Double? = null,
    val annualizedFundingRate: Double? = null,
) {
    /** Variation sur 24 h, en pourcentage. Null si une des deux bornes manque. */
    val change24hPct: Double?
        get() {
            val m = markPrice ?: return null
            val p = prevDayMarkPrice ?: return null
            if (p == 0.0) return null
            return (m - p) / p * 100.0
        }
}

/** Sens d'un franchissement de seuil. */
enum class AlertDirection { ABOVE, BELOW }

/**
 * Une alerte de prix posee par l'utilisateur.
 *
 * [lastFiredAt] sert d'anti-rebond : sans lui, un prix qui oscille autour du seuil
 * declencherait une notification a chaque sondage, soit une par minute.
 */
data class PriceAlert(
    val id: Long,
    val symbol: String,
    val direction: AlertDirection,
    val threshold: Double,
    val createdAt: Long,
    val lastFiredAt: Long = 0L,
    val enabled: Boolean = true,
) {
    fun isTriggeredBy(price: Double): Boolean = when (direction) {
        AlertDirection.ABOVE -> price >= threshold
        AlertDirection.BELOW -> price <= threshold
    }
}
