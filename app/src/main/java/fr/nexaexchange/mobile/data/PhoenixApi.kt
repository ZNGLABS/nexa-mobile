package fr.nexaexchange.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client HTTP de l'API publique Phoenix Flight.
 *
 * POURQUOI EN NATIF ET PAS VIA UN BACKEND
 * La premiere version prevoyait une fonction Supabase qui aurait decode les comptes
 * avec le SDK @ellipsis-labs/rise. Mesure du 12 septembre 2026 : ce SDK tire la
 * dependance `ws`, qui exige node:url, bufferutil et utf-8-validate, absents du
 * runtime edge de Supabase. La fonction ne demarre pas.
 * Conclusion : l'application parle directement a Phoenix et a Solana. C'est aussi
 * la meilleure architecture pour un moniteur de fond, puisqu'elle supprime un
 * intermediaire qui pourrait tomber pendant la nuit.
 *
 * Les deux points d'entree utilises sont publics et repondent avec CORS ouvert ;
 * aucune cle n'est necessaire, donc aucun secret n'est embarque dans l'APK.
 */
object PhoenixApi {

    private const val BASE = "https://perp-api.phoenix.trade"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun get(path: String): String {
        val req = Request.Builder()
            .url(BASE + path)
            .header("Accept", "application/json")
            // Identifie le trafic NEXA cote Phoenix. Utile s'ils ont un jour besoin
            // de nous joindre au sujet du volume genere par l'app.
            .header("User-Agent", "NEXA-Mobile-Android")
            .build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw PhoenixException("HTTP ${res.code} on $path")
            }
            return body
        }
    }

    /**
     * Liste des marches, sans les prix.
     * Les metadonnees changent rarement : l'appelant peut les mettre en cache
     * longtemps. Les prix, eux, viennent de [fetchStats].
     */
    suspend fun fetchMarkets(): List<Market> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get("/v1/view/exchange/markets"))
        val out = ArrayList<Market>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            // On ignore volontairement les marches qui ne sont pas actifs : les
            // afficher donnerait de faux prix a l'utilisateur.
            if (m.optString("marketStatus") != "active") continue
            val meta = m.optJSONObject("metadata")
            var maxLev = 1
            val tiers = m.optJSONArray("leverageTiers")
            if (tiers != null) {
                for (t in 0 until tiers.length()) {
                    val lv = tiers.getJSONObject(t).optInt("maxLeverage", 0)
                    if (lv > maxLev) maxLev = lv
                }
            }
            out.add(
                Market(
                    symbol = m.getString("symbol"),
                    assetId = m.optInt("assetId", -1),
                    name = meta?.optString("name").orEmpty().ifEmpty { m.getString("symbol") },
                    displayColor = meta?.optString("displayColor")?.ifEmpty { null },
                    logoUri = meta?.optString("logoUri")?.ifEmpty { null },
                    baseLotsDecimals = m.optInt("baseLotsDecimals", 2),
                    maxLeverage = maxLev,
                    takerFee = m.optDouble("takerFee", 0.00035),
                )
            )
        }
        out
    }

    /** Derniers prix, indexes par symbole. */
    suspend fun fetchStats(): Map<String, JSONObject> = withContext(Dispatchers.IO) {
        val root = JSONObject(get("/v1/markets/stats/latest"))
        val arr = root.optJSONArray("markets") ?: JSONArray()
        val map = HashMap<String, JSONObject>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            map[s.getString("symbol")] = s
        }
        map
    }

    /** Marches enrichis de leurs prix, tries par volume 24 h decroissant. */
    suspend fun fetchMarketsWithPrices(): List<Market> {
        val markets = fetchMarkets()
        val stats = fetchStats()
        return markets.map { m ->
            val s = stats[m.symbol] ?: return@map m
            m.copy(
                markPrice = s.optDoubleOrNull("mark_price"),
                oraclePrice = s.optDoubleOrNull("oracle_price"),
                prevDayMarkPrice = s.optDoubleOrNull("prev_day_mark_price"),
                dayVolumeUsd = s.optDoubleOrNull("day_volume_usd"),
                openInterest = s.optDoubleOrNull("open_interest"),
                annualizedFundingRate = s.optDoubleOrNull("annualized_funding_rate"),
            )
        }.sortedByDescending { it.dayVolumeUsd ?: 0.0 }
    }

    /**
     * Prix seuls, pour le service de fond : une seule requete legere au lieu des deux.
     */
    suspend fun fetchPrices(): Map<String, Double> {
        val stats = fetchStats()
        val out = HashMap<String, Double>(stats.size)
        for ((sym, o) in stats) {
            o.optDoubleOrNull("mark_price")?.let { out[sym] = it }
        }
        return out
    }

    /**
     * Adresse du compte trader d'un portefeuille.
     *
     * On la DEMANDE a Phoenix au lieu de la deriver nous-memes. Deriver un PDA exige de
     * recopier la regle de derivation du programme ; si Phoenix la change, notre copie
     * pointerait silencieusement sur un compte inexistant et l'application afficherait
     * « aucune position » a quelqu'un qui en a. Une requete de plus vaut mieux qu'un
     * mensonge muet.
     */
    suspend fun fetchTraderPda(wallet: String): String? = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("traderAuthority", wallet)
            .put("txFeePayer", wallet)
            .toString()
        val req = Request.Builder()
            .url("$BASE/v1/exchange/build-register-ixs")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", "NEXA-Mobile-Android")
            .build()
        client.newCall(req).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) return@withContext null
            JSONObject(txt).optString("traderPda").ifEmpty { null }
        }
    }

    /**
     * Positions ouvertes du portefeuille, enrichies des prix marque.
     *
     * Liste vide = le compte existe mais n'a aucune position. `null` = on n'a pas pu
     * savoir (pas de compte trader, ou lecture impossible). La distinction compte :
     * afficher « aucune position » a quelqu'un qui en a serait pire que d'afficher une
     * erreur.
     */
    suspend fun fetchPositions(wallet: String, markets: List<Market>): List<Position>? {
        val pda = fetchTraderPda(wallet) ?: return null
        val data = SolanaRpc.getAccountDataBase64(pda) ?: return null
        val decoded = TraderAccount.decodeBase64(data)
        if (decoded.positions.isEmpty()) return emptyList()

        val prices = fetchStats()
        val parAsset = markets.associateBy { it.assetId }
        return decoded.positions.mapNotNull { p ->
            val mk = parAsset[p.assetId] ?: return@mapNotNull null
            val mark = prices[mk.symbol]?.optDoubleOrNull("mark_price") ?: return@mapNotNull null
            Position.from(p, mk, mark)
        }
    }

    /** Collateral USDC depose sur le compte Phoenix, ou null si pas de compte. */
    suspend fun fetchCollateral(wallet: String): Double? {
        val pda = fetchTraderPda(wallet) ?: return null
        val data = SolanaRpc.getAccountDataBase64(pda) ?: return null
        return TraderAccount.decodeBase64(data).collateralUsdc
    }

    /**
     * optDouble renvoie NaN quand la cle manque, ce qui se propage silencieusement
     * dans tous les calculs. On convertit en null pour que l'absence soit visible.
     */
    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }
}

class PhoenixException(message: String) : Exception(message)
