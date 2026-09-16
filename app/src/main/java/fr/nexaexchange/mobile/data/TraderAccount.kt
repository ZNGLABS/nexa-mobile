package fr.nexaexchange.mobile.data

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodage du compte trader Phoenix Flight.
 *
 * ── POURQUOI CE CODE EXISTE ────────────────────────────────────────────────
 * Le decodage officiel vit dans @ellipsis-labs/rise, du TypeScript. Ce SDK ne
 * demarre pas dans une fonction edge (il tire `ws`, mesure du 12 septembre 2026), et
 * Phoenix n'expose AUCUNE API REST pour les positions — douze points d'entree
 * plausibles testes le 13 septembre, douze reponses 404. Il faut donc lire les octets.
 *
 * ── COMMENT LA DISPOSITION A ETE ETABLIE ───────────────────────────────────
 * Pas par lecture de documentation : il n'y en a pas. Par mesure, le 14 septembre 2026,
 * sur deux comptes reels du mainnet :
 *
 *   4w1F9Dzua91TQwgsTKdzxugYYWPTiyThHtJE32xvA9rK  →  0 position (cas vide)
 *   HT7Ss3gsuTfrJJH77uKW2CrN3379qn3DTMDXsr7BeRfF  →  1 position ouverte
 *
 * Pour chacun, les octets bruts ont ete compares au resultat de `decodeTrader` du SDK
 * officiel execute dans un navigateur. **Les deux correspondent champ par champ.**
 * Un decodeur valide uniquement sur un compte vide n'aurait rien prouve : c'est pour
 * cela qu'une position reelle a ete ouverte pour l'occasion.
 *
 * ── LA DISPOSITION, EN OCTETS ──────────────────────────────────────────────
 * Compte de 1 520 octets, tout en petit-boutiste.
 *
 *    88  u64   quoteLotCollateral      (millioniemes d'USDC)
 *    96  u32   flags                   (bits 3-4-5 poses = autorise a trader)
 *   224  u64   positions.len
 *   232  u64   positions.capacity      (32 en pratique)
 *   240        debut des entrees, 40 octets chacune :
 *          +0   u64  assetId du marche
 *          +8   i64  baseLotPosition        signe, positif = long
 *          +16  i64  virtualQuoteLotPosition
 *          +24  i64  cumulativeFundingSnapshot
 *          +32  u32  positionSequenceNumber
 *          +36  i32  accumulatedFundingForActivePosition
 *
 * CONTROLE STRUCTUREL QUI VERROUILLE LE PAS DE 40 OCTETS :
 *   240 + 32 x 40 = 1520, exactement la taille du compte. La carte se termine au
 *   dernier octet, sans reste. Un pas de 48 octets deborderait de 256 octets.
 */
object TraderAccount {

    private const val OFF_COLLATERAL = 88
    private const val OFF_FLAGS = 96
    private const val OFF_LEN = 224
    private const val OFF_CAPACITY = 232
    private const val OFF_ENTRIES = 240
    private const val ENTRY_SIZE = 40

    /** Bits de permission. 62 sur un compte embarque par Phoenix, 6 sur un compte gele. */
    private const val FLAGS_TRADING = 56

    data class RawPosition(
        val assetId: Int,
        val baseLots: Long,
        val virtualQuoteLots: Long,
        val fundingSnapshot: Long,
        val sequence: Int,
    )

    data class Decoded(
        val collateralUsdc: Double,
        val flags: Int,
        val active: Boolean,
        val declaredLen: Int,
        val capacity: Int,
        val positions: List<RawPosition>,
    )

    fun decodeBase64(data: String): Decoded = decode(Base64.decode(data, Base64.DEFAULT))

    fun decode(raw: ByteArray): Decoded {
        require(raw.size >= OFF_ENTRIES) { "trader account too short: ${raw.size}" }
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)

        val collateralLots = bb.getLong(OFF_COLLATERAL)
        val flags = bb.getInt(OFF_FLAGS)
        val declaredLen = bb.getLong(OFF_LEN).toInt()
        val capacity = bb.getLong(OFF_CAPACITY).toInt()

        val out = ArrayList<RawPosition>()
        for (k in 0 until capacity) {
            val o = OFF_ENTRIES + k * ENTRY_SIZE
            // Le compte peut etre plus court que capacity x 40 si Phoenix change la
            // taille : on s'arrete plutot que de lire hors des clous.
            if (o + ENTRY_SIZE > raw.size) break
            val base = bb.getLong(o + 8)
            val quote = bb.getLong(o + 16)
            val seq = bb.getInt(o + 32)
            // Emplacement jamais utilise. On ne se fie PAS a `len` seul : la carte est
            // creuse, une position fermee laisse un trou au milieu.
            if (base == 0L && quote == 0L && seq == 0) continue
            // Position soldee mais emplacement encore occupe : rien a afficher.
            if (base == 0L) continue
            out.add(
                RawPosition(
                    assetId = bb.getLong(o).toInt(),
                    baseLots = base,
                    virtualQuoteLots = quote,
                    fundingSnapshot = bb.getLong(o + 24),
                    sequence = seq,
                )
            )
        }

        return Decoded(
            collateralUsdc = collateralLots / 1_000_000.0,
            flags = flags,
            active = (flags and FLAGS_TRADING) == FLAGS_TRADING,
            declaredLen = declaredLen,
            capacity = capacity,
            positions = out,
        )
    }
}

/**
 * Une position, convertie en grandeurs lisibles.
 *
 * L'arithmetique est reprise telle quelle de nexa-exchange.fr, ou elle tourne en
 * production sur des positions reelles depuis le 12 septembre 2026 :
 *
 *   base   = baseLotPosition / 10^baseLotsDecimals     (signe, + = long)
 *   quote  = virtualQuoteLotPosition / 1e6
 *   entree = |quote / base|
 *   pnl    = base x prix marque + quote
 */
data class Position(
    val symbol: String,
    val assetId: Int,
    val isLong: Boolean,
    val size: Double,
    val entryPrice: Double,
    val markPrice: Double,
    val notionalUsd: Double,
    val pnlUsd: Double,
    val pnlPct: Double,
    val displayColor: String?,
    /**
     * Prix de liquidation et distance, tels que renvoyes par le programme Phoenix
     * (voir Hawkeye.kt). `null` signifie « on ne sait pas » — et dans ce cas
     * l'interface n'affiche RIEN plutot qu'un chiffre inventé, et le service ne
     * declenche aucune alerte.
     */
    val liquidationPriceUsd: Double? = null,
    val liquidationDistancePct: Double? = null,
    /** Motif de l'echec de lecture, affiche tel quel pour pouvoir diagnostiquer. */
    val liquidationError: String? = null,
) {
    companion object {
        fun from(raw: TraderAccount.RawPosition, market: Market, mark: Double): Position {
            val dec = market.baseLotsDecimals
            val base = raw.baseLots / Math.pow(10.0, dec.toDouble())
            val quote = raw.virtualQuoteLots / 1_000_000.0
            val entry = if (base != 0.0) Math.abs(quote / base) else 0.0
            val pnl = base * mark + quote
            return Position(
                symbol = market.symbol,
                assetId = market.assetId,
                isLong = base > 0,
                size = Math.abs(base),
                entryPrice = entry,
                markPrice = mark,
                notionalUsd = Math.abs(base * mark),
                pnlUsd = pnl,
                pnlPct = if (Math.abs(quote) > 0) pnl / Math.abs(quote) * 100.0 else 0.0,
                displayColor = market.displayColor,
            )
        }
    }
}
