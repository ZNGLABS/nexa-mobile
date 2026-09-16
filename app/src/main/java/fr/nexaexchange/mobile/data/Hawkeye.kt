package fr.nexaexchange.mobile.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * Prix de liquidation — demande au programme Phoenix lui-meme.
 *
 * ── LE PRINCIPE, ET POURQUOI IL COMPTE ─────────────────────────────────────
 * Phoenix embarque un module de lecture, « Hawkeye », dont les instructions ne
 * modifient rien : on les SIMULE et le programme renvoie son calcul dans les donnees
 * de retour. Le prix de liquidation affiche par cette application n'est donc pas une
 * formule reconstituee de notre cote — c'est **le chiffre du programme**.
 *
 * Pour un seuil de securite, la difference n'est pas academique. Une formule
 * approchee qui se trompe de 5 % previent trop tard.
 *
 * ── ETABLI ET VERIFIE LE 16 SEPTEMBRE 2026, SUR LE MAINNET ─────────────────
 * Position reelle : 0,02 SOL long, entree 97,38 $, collateral 0,4817 USDC.
 *
 *   Le programme repond    liquidationPriceTicks = 7478  →  74,78 $
 *   Calcul independant                                      74,7788 $
 *   Ecart                                                    0,0012 $ (arrondi au tick)
 *
 * Le calcul independant part de l'egalite « capitaux propres = marge de maintenance » :
 *   collateral + taille x P + quote = mm x taille x P
 * avec mm = maintenanceMarginQuoteLots / notionnel = exactement 2,00 % sur ce marche.
 *
 * Le prix d'entree a lui aussi ete recoupe deux fois : 97,38 $ depuis les octets bruts
 * du compte, 97,38 $ depuis le champ `entryPriceQuoteLotsPerBaseLot` du programme.
 *
 * ── LES TROIS BRIQUES, CHACUNE VERIFIEE SEPAREMENT ─────────────────────────
 *  1. Base58.decode — compare a web3.js sur 5 adresses (voir Base58.kt)
 *  2. Serialisation de transaction ecrite a la main ici — la transaction produite
 *     simule sans erreur sur le mainnet et renvoie les bonnes donnees
 *  3. Decodage des 56 octets de retour — dispositions relevees sur une reponse reelle
 *     et confrontees au decodeur officiel du SDK, champ par champ
 *
 * ── PAS DE BLOCKHASH A ALLER CHERCHER ──────────────────────────────────────
 * `replaceRecentBlockhash: true` demande au RPC de mettre le sien. On envoie donc un
 * blockhash nul et on economise un aller-retour reseau a chaque sondage — ce qui
 * compte pour un service qui tourne toutes les minutes sur batterie.
 */
object Hawkeye {

    /**
     * `simulateTransaction` n'est pas servi par tous les RPC publics — publicnode
     * refuse deja les requetes indexees (`getMultipleAccounts`, `getProgramAccounts`),
     * verifie le 13 septembre 2026. On essaie donc plusieurs points d'entree dans
     * l'ordre, et on retient le premier qui repond vraiment.
     *
     * L'en-tete User-Agent personnalise a ete RETIRE : plusieurs RPC publics filtrent
     * les clients qu'ils ne reconnaissent pas, et c'est la difference la plus visible
     * entre l'appel qui marche depuis un navigateur et celui du telephone.
     */
    private val RPCS = listOf(
        "https://solana-rpc.publicnode.com",
        "https://api.mainnet-beta.solana.com",
    )
    private val JSON_MEDIA = "application/json".toMediaType()

    /** Programme de lecture Hawkeye (distinct du programme Phoenix lui-meme). */
    private const val PROGRAM = "RiSeVw3ZjNfsaXPRb4mgaqYaEEt41pNNJoDvVh7pgQj"

    /**
     * Payeur de frais pour la simulation. Adresse publique quelconque : la transaction
     * n'est jamais envoyee, et `sigVerify: false` dispense de toute signature. C'est la
     * valeur qu'utilise le SDK officiel (HAWKEYE_SIMULATION_FEE_PAYER).
     */
    private const val SIM_FEE_PAYER = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM"

    /**
     * Les cinq comptes fixes de l'instruction. VERIFIE : la liste est identique d'un
     * marche a l'autre — comparee entre SOL et BTC le 13 septembre. Seul le compte
     * trader, ajoute en sixieme position, change d'un utilisateur a l'autre.
     */
    private val FIXED_ACCOUNTS = listOf(
        "EtrnLzgbS7nMMy5fbD42kXiUzGg8XQzJ972Xtk1cjWih",
        "2zskx2iyCvb6Stg7RBZkt1f6MrF4dpYtMG3yMvKwqtUZ",
        "HCrPXLByGqRh2szQi3gj7oRdRVBNi1gccAyn4CQCT3HK",
        "2U32rSzzrQS3eVmGHsnbw5kcqKF3wQXpHGd3hMq5YJok",
        "2nHGAaEw3D5dd4hVueaUNoygkQFmoeKqRQWnSPqSMFUC",
    )

    /** Discriminant de `view_liquidation_price`. */
    private val DISC_VIEW_LIQUIDATION =
        byteArrayOf(249.toByte(), 201.toByte(), 188.toByte(), 96, 44, 104, 68, 20)

    /** Marqueur en tete de la reponse. S'il manque, on ne lit pas plus loin. */
    private const val MAGIC = -3297421415725176767L   // 0xd23d34b160ef6841 signe

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Reponse du programme, telle quelle. Les prix sont en « ticks » du marche. */
    data class LiquidationView(
        val assetId: Int,
        val hasPosition: Boolean,
        val isLong: Boolean,
        val liquidationPriceTicks: Long,
        val markPriceTicks: Long,
        val effectiveCollateralUsdc: Double,
        val maintenanceMarginUsdc: Double,
    )

    // ── Serialisation -------------------------------------------------------

    /** Entier compact de Solana (« shortvec ») : 7 bits utiles par octet. */
    private fun compactU16(value: Int, out: MutableList<Byte>) {
        var v = value
        while (true) {
            val b = v and 0x7F
            v = v shr 7
            if (v == 0) { out.add(b.toByte()); return }
            out.add((b or 0x80).toByte())
        }
    }

    /**
     * Transaction non signee contenant la seule instruction de lecture.
     *
     * Ordre des comptes : le payeur (unique signataire) en tete, puis les six comptes
     * de l'instruction, puis le programme — tous en lecture seule. Les index de
     * l'instruction pointent sur les positions 1 a 6, ce qui est ce que le programme
     * lit reellement ; l'ordre au niveau du message n'a pas d'autre contrainte.
     */
    private fun buildTransaction(traderPda: String, assetId: Int): ByteArray {
        val keys = ArrayList<String>(8)
        keys.add(SIM_FEE_PAYER)
        keys.addAll(FIXED_ACCOUNTS)
        keys.add(traderPda)
        keys.add(PROGRAM)

        val data = ByteArray(16)
        DISC_VIEW_LIQUIDATION.copyInto(data, 0)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putLong(8, assetId.toLong())

        val out = ArrayList<Byte>(400)

        // Une signature, vide : sigVerify sera a false.
        compactU16(1, out)
        repeat(64) { out.add(0) }

        // En-tete : 1 signataire, 0 signataire en lecture seule, tout le reste en
        // lecture seule non signataire.
        out.add(1)
        out.add(0)
        out.add((keys.size - 1).toByte())

        compactU16(keys.size, out)
        keys.forEach { k -> Base58.decode(k).forEach { out.add(it) } }

        // Blockhash nul : remplace par le RPC (replaceRecentBlockhash).
        repeat(32) { out.add(0) }

        compactU16(1, out)                       // une instruction
        out.add((keys.size - 1).toByte())        // index du programme = dernier
        compactU16(FIXED_ACCOUNTS.size + 1, out) // six comptes
        for (i in 1..FIXED_ACCOUNTS.size + 1) out.add(i.toByte())
        compactU16(data.size, out)
        data.forEach { out.add(it) }

        return out.toByteArray()
    }

    // ── Decodage ------------------------------------------------------------

    private fun decodeReturn(raw: ByteArray): LiquidationView? {
        if (raw.size < 56) return null
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        if (bb.getLong(0) != MAGIC) return null      // pas notre reponse
        if (raw[12].toInt() != 1) return null        // version inattendue : on s'abstient
        return LiquidationView(
            assetId = bb.getInt(8),
            hasPosition = raw[13].toInt() == 1,
            isLong = raw[14].toInt() == 1,
            liquidationPriceTicks = bb.getLong(16),
            markPriceTicks = bb.getLong(24),
            effectiveCollateralUsdc = bb.getLong(40) / 1_000_000.0,
            maintenanceMarginUsdc = bb.getLong(48) / 1_000_000.0,
        )
    }

    // ── Appel ---------------------------------------------------------------

    /**
     * Resultat d'une lecture : soit la vue, soit la raison de l'echec.
     *
     * POURQUOI UNE RAISON ET PAS UN SIMPLE null : le 16 septembre 2026, l'application
     * a affiche « prix de liquidation indisponible » sur un vrai appareil alors que la
     * meme requete fonctionnait depuis un navigateur. Sans motif remonte jusqu'a
     * l'ecran, diagnostiquer revenait a deviner — une compilation et un test par
     * hypothese. Le motif coute trois lignes et supprime la devinette.
     */
    data class Outcome(val view: LiquidationView?, val error: String?)

    suspend fun viewLiquidation(traderPda: String, assetId: Int): Outcome =
        withContext(Dispatchers.IO) {
            val tx = try {
                Base64.encodeToString(buildTransaction(traderPda, assetId), Base64.NO_WRAP)
            } catch (e: Exception) {
                // Echec de construction : c'est notre code, pas le reseau. On le nomme.
                return@withContext Outcome(null, "build: ${e.javaClass.simpleName} ${e.message.orEmpty()}".take(80))
            }

            val body = JSONObject()
                .put("jsonrpc", "2.0").put("id", 1)
                .put("method", "simulateTransaction")
                .put(
                    "params",
                    JSONArray().put(tx).put(
                        JSONObject()
                            .put("encoding", "base64")
                            .put("sigVerify", false)
                            .put("replaceRecentBlockhash", true)
                    )
                )
                .toString()

            val motifs = ArrayList<String>(RPCS.size)
            for (url in RPCS) {
                val court = url.removePrefix("https://").substringBefore('/').take(14)
                try {
                    val req = Request.Builder().url(url).post(body.toRequestBody(JSON_MEDIA)).build()
                    client.newCall(req).execute().use { res ->
                        val txt = res.body?.string().orEmpty()
                        if (!res.isSuccessful) {
                            motifs.add("$court HTTP ${res.code}"); return@use
                        }
                        val root = JSONObject(txt)
                        root.optJSONObject("error")?.let {
                            motifs.add("$court ${it.optString("message").take(40)}"); return@use
                        }
                        val value = root.optJSONObject("result")?.optJSONObject("value")
                        if (value == null) { motifs.add("$court no value"); return@use }
                        // Une simulation en erreur ne produit JAMAIS de seuil affiche.
                        if (!value.isNull("err")) {
                            motifs.add("$court sim ${value.opt("err").toString().take(40)}"); return@use
                        }
                        val b64 = value.optJSONObject("returnData")?.optJSONArray("data")?.optString(0)
                        if (b64.isNullOrEmpty()) { motifs.add("$court no returnData"); return@use }
                        val raw = Base64.decode(b64, Base64.DEFAULT)
                        val decoded = decodeReturn(raw)
                        if (decoded == null) { motifs.add("$court decode ${raw.size}B"); return@use }
                        return@withContext Outcome(decoded, null)
                    }
                } catch (e: Exception) {
                    motifs.add("$court ${e.javaClass.simpleName}")
                }
            }
            Outcome(null, motifs.joinToString(" | ").take(110))
        }

    /**
     * Distance jusqu'a la liquidation, en pourcentage du prix actuel.
     *
     * Calculee ENTIEREMENT en ticks : les deux grandeurs sont dans la meme unite, donc
     * aucune conversion n'intervient et aucune erreur de conversion ne peut fausser le
     * seuil. C'est la propriete qu'on veut pour une alerte de securite.
     *
     * null si le programme n'a pas donne de prix de liquidation exploitable.
     */
    fun distancePct(v: LiquidationView): Double? {
        if (!v.hasPosition) return null
        if (v.liquidationPriceTicks <= 0L || v.markPriceTicks <= 0L) return null
        val mark = v.markPriceTicks.toDouble()
        val liq = v.liquidationPriceTicks.toDouble()
        // Un long se liquide en dessous, un short au-dessus.
        val d = if (v.isLong) (mark - liq) / mark else (liq - mark) / mark
        return if (d.isFinite()) d * 100.0 else null
    }

    /**
     * Prix de liquidation en dollars, pour l'AFFICHAGE seulement.
     *
     * Le facteur tick → dollar n'est pas code en dur : on le deduit du marche lui-meme
     * en rapprochant `markPriceTicks` du prix marque publie par l'API. L'application
     * n'a donc aucune convention d'unite a supposer, et cela reste juste si Phoenix
     * change d'echelle sur un marche.
     */
    fun liquidationUsd(v: LiquidationView, markPriceUsd: Double): Double? {
        if (!v.hasPosition || v.markPriceTicks <= 0L || markPriceUsd <= 0.0) return null
        val perTick = markPriceUsd / v.markPriceTicks.toDouble()
        return v.liquidationPriceTicks.toDouble() * perTick
    }
}
