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

    private const val RPC = "https://solana-rpc.publicnode.com"
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Donnees base64 du compte, ou null s'il n'existe pas. */
    suspend fun getAccountDataBase64(pubkey: String): String? = withContext(Dispatchers.IO) {
        val params = JSONArray()
            .put(pubkey)
            .put(JSONObject().put("encoding", "base64").put("commitment", "confirmed"))
        val body = JSONObject()
            .put("jsonrpc", "2.0").put("id", 1)
            .put("method", "getAccountInfo").put("params", params)
            .toString()

        val req = Request.Builder()
            .url(RPC)
            .post(body.toRequestBody(JSON))
            .header("User-Agent", "NEXA-Mobile-Android")
            .build()

        client.newCall(req).execute().use { res ->
            val txt = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw PhoenixException("RPC HTTP ${res.code}")
            val root = JSONObject(txt)
            if (root.has("error")) {
                throw PhoenixException(root.getJSONObject("error").optString("message", "RPC error"))
            }
            val value = root.optJSONObject("result")?.optJSONObject("value")
                ?: return@withContext null   // compte inexistant : ce n'est pas une erreur
            value.optJSONArray("data")?.optString(0)
        }
    }
}
