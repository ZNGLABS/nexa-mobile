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
 * Lecture directe de la chaine Solana depuis le telephone.
 *
 * 🔴 `commitment: confirmed` N'EST PAS OPTIONNEL. Sans lui, Solana repond en
 * `finalized`, qui retarde de 13 a 30 secondes. Mesure du 7 septembre 2026 sur le
 * site : une position ouverte a 13:49:29 etait encore invisible a 13:49:33, et
 * l'interface affirmait qu'il ne s'etait rien passe. Pour une application qui doit
 * prevenir d'une liquidation, une demi-minute de retard n'est pas un detail.
 *
 * Choix du RPC : publicnode accepte `getAccountInfo` sans jeton. Il REFUSE en revanche
 * les requetes indexees — `getTokenAccountsByOwner`, `getMultipleAccounts`,
 * `getProgramAccounts` — verifie le 13 septembre. On ne s'en sert donc que pour lire
 * un compte precis, ce qui est exactement notre besoin.
 */
object SolanaRpc {

    /**
     * Deux fournisseurs, pas un.
     *
     * Ajoute le 17 septembre 2026 pendant la revue de securite. [Hawkeye] basculait deja
     * d'un RPC a l'autre, mais la lecture du compte trader — celle qui precede TOUTE
     * evaluation de liquidation — ne tenait qu'a publicnode. Un seul fournisseur en panne
     * et la fonction de securite de l'application s'eteignait entierement, sans que rien
     * ne soit casse chez nous. Une alerte de liquidation qui depend d'un point unique
     * n'est pas une alerte de liquidation.
     */
    private val RPCS = listOf(
        "https://solana-rpc.publicnode.com",
        "https://api.mainnet-beta.solana.com",
    )
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Donnees base64 du compte, ou null s'il n'existe pas.
     *
     * On essaie les fournisseurs dans l'ordre et on retient le premier qui repond
     * vraiment. Un compte absent est une reponse LEGITIME : on la rend telle quelle sans
     * interroger le second fournisseur, sinon un portefeuille sans compte trader
     * declencherait deux requetes a chaque cycle pour rien.
     */
    suspend fun getAccountDataBase64(pubkey: String): String? = withContext(Dispatchers.IO) {
        // Filet, pas assertion : on REND null plutot que de lever. Les appelants traitent
        // deja le null (« compte introuvable ») ; une exception ici remonterait dans une
        // coroutine d'interface et ferait tomber l'ecran pour une adresse mal formee.
        if (!Base58.isValidAddress(pubkey)) return@withContext null

        val params = JSONArray()
            .put(pubkey)
            .put(JSONObject().put("encoding", "base64").put("commitment", "confirmed"))
        val body = JSONObject()
            .put("jsonrpc", "2.0").put("id", 1)
            .put("method", "getAccountInfo").put("params", params)
            .toString()

        var dernier: Exception? = null
        for (url in RPCS) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody(JSON))
                    .header("User-Agent", "NEXA-Mobile-Android")
                    .build()

                client.newCall(req).execute().use { res ->
                    val txt = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw PhoenixException("RPC HTTP ${res.code}")
                    val root = JSONObject(txt)
                    if (root.has("error")) {
                        throw PhoenixException(
                            root.getJSONObject("error").optString("message", "RPC error")
                        )
                    }
                    val value = root.optJSONObject("result")?.optJSONObject("value")
                        ?: return@withContext null   // compte inexistant : ce n'est pas une erreur
                    return@withContext value.optJSONArray("data")?.optString(0)
                }
            } catch (e: Exception) {
                dernier = e   // on note et on passe au fournisseur suivant
            }
        }
        throw dernier ?: PhoenixException("aucun RPC disponible")
    }
}
