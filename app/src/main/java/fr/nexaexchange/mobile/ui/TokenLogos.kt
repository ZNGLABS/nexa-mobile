package fr.nexaexchange.mobile.ui

import fr.nexaexchange.mobile.R

/**
 * Logos officiels des marches Phoenix, embarques dans l'application.
 *
 * ── POURQUOI ILS SONT DANS L'APK ET PAS TELECHARGES ────────────────────────
 * L'API Phoenix expose un `logoUri` par marche, mais ce sont des SVG : Android ne
 * sait pas les decoder nativement. Trois options existaient, et voici pourquoi
 * celle-ci a ete retenue.
 *
 * Ajouter une bibliotheque de chargement d'images avec decodeur SVG reglait le
 * probleme, au prix de deux dependances de plus et d'une requete reseau par ligne
 * de la liste — donc de lignes vides le temps du chargement, sur un ecran qui doit
 * etre filme.
 *
 * Convertir les SVG en VectorDrawable ne marchait que pour 25 des 82 : les autres
 * utilisent des degrades, des masques ou des classes CSS que le format ne sait pas
 * exprimer. Mesure le 18 septembre 2026, fichier par fichier.
 *
 * Reste ce qui est fait ici : les 82 SVG ont ete rendus une fois en PNG 96x96 et
 * ajoutes au depot. 237 Ko au total, soit environ 3 Ko par logo. Aucune dependance,
 * aucune requete, aucun temps de chargement, et l'ecran est identique hors ligne.
 *
 * ── CE QUE CA COUTE ────────────────────────────────────────────────────────
 * La liste est figee au moment de la compilation : un marche ouvert par Phoenix
 * apres cette date n'aura pas son logo. C'est pour cela que [forSymbol] rend null
 * plutot que de planter, et que l'interface retombe alors sur la pastille coloree
 * construite depuis `displayColor`, qui, elle, vient de l'API en direct.
 *
 * Source : GET /v1/view/exchange/markets, champ `metadata.logoUri`, releve le
 * 18 septembre 2026. 82 marches, 82 logos rendus, aucun echec.
 */
object TokenLogos {

    private val PAR_SYMBOLE: Map<String, Int> = mapOf(
    "AAPL" to R.drawable.logo_aapl,
    "AAVE" to R.drawable.logo_aave,
    "ADA" to R.drawable.logo_ada,
    "AMAT" to R.drawable.logo_amat,
    "AMD" to R.drawable.logo_amd,
    "AMZN" to R.drawable.logo_amzn,
    "ANSEM" to R.drawable.logo_ansem,
    "ARM" to R.drawable.logo_arm,
    "ASML" to R.drawable.logo_asml,
    "AVGO" to R.drawable.logo_avgo,
    "BABA" to R.drawable.logo_baba,
    "BNB" to R.drawable.logo_bnb,
    "BTC" to R.drawable.logo_btc,
    "CBRS" to R.drawable.logo_cbrs,
    "CHIP" to R.drawable.logo_chip,
    "COIN" to R.drawable.logo_coin,
    "COPPER" to R.drawable.logo_copper,
    "CRCL" to R.drawable.logo_crcl,
    "CRV" to R.drawable.logo_crv,
    "CRWD" to R.drawable.logo_crwd,
    "CRWV" to R.drawable.logo_crwv,
    "DELL" to R.drawable.logo_dell,
    "DOGE" to R.drawable.logo_doge,
    "ENA" to R.drawable.logo_ena,
    "ETH" to R.drawable.logo_eth,
    "FARTCOIN" to R.drawable.logo_fartcoin,
    "FET" to R.drawable.logo_fet,
    "GOLD" to R.drawable.logo_gold,
    "GOOGL" to R.drawable.logo_googl,
    "HOOD" to R.drawable.logo_hood,
    "HYPE" to R.drawable.logo_hype,
    "INTC" to R.drawable.logo_intc,
    "IREN" to R.drawable.logo_iren,
    "JTO" to R.drawable.logo_jto,
    "JUP" to R.drawable.logo_jup,
    "LINK" to R.drawable.logo_link,
    "LIT" to R.drawable.logo_lit,
    "LLY" to R.drawable.logo_lly,
    "MEGA" to R.drawable.logo_mega,
    "MET" to R.drawable.logo_met,
    "META" to R.drawable.logo_meta,
    "MON" to R.drawable.logo_mon,
    "MORPHO" to R.drawable.logo_morpho,
    "MRNA" to R.drawable.logo_mrna,
    "MRVL" to R.drawable.logo_mrvl,
    "MSFT" to R.drawable.logo_msft,
    "MSTR" to R.drawable.logo_mstr,
    "MU" to R.drawable.logo_mu,
    "NBIS" to R.drawable.logo_nbis,
    "NEAR" to R.drawable.logo_near,
    "NET" to R.drawable.logo_net,
    "NFLX" to R.drawable.logo_nflx,
    "NVDA" to R.drawable.logo_nvda,
    "ONDO" to R.drawable.logo_ondo,
    "ORCL" to R.drawable.logo_orcl,
    "PLTR" to R.drawable.logo_pltr,
    "PONS" to R.drawable.logo_pons,
    "PUMP" to R.drawable.logo_pump,
    "QCOM" to R.drawable.logo_qcom,
    "QQQ" to R.drawable.logo_qqq,
    "RENDER" to R.drawable.logo_render,
    "SILVER" to R.drawable.logo_silver,
    "SKHY" to R.drawable.logo_skhy,
    "SKR" to R.drawable.logo_skr,
    "SNDK" to R.drawable.logo_sndk,
    "SOL" to R.drawable.logo_sol,
    "SPCX" to R.drawable.logo_spcx,
    "SPY" to R.drawable.logo_spy,
    "STONK" to R.drawable.logo_stonk,
    "SUI" to R.drawable.logo_sui,
    "TAO" to R.drawable.logo_tao,
    "TRX" to R.drawable.logo_trx,
    "TSLA" to R.drawable.logo_tsla,
    "TSM" to R.drawable.logo_tsm,
    "VIRTUAL" to R.drawable.logo_virtual,
    "VVV" to R.drawable.logo_vvv,
    "WLD" to R.drawable.logo_wld,
    "WTIOIL" to R.drawable.logo_wtioil,
    "XLM" to R.drawable.logo_xlm,
    "XPL" to R.drawable.logo_xpl,
    "XRP" to R.drawable.logo_xrp,
    "ZEC" to R.drawable.logo_zec,
    )

    /** Ressource du logo, ou null si ce marche est arrive apres la compilation. */
    fun forSymbol(symbol: String): Int? = PAR_SYMBOLE[symbol.uppercase()]
}
