package fr.nexaexchange.mobile.wallet

import android.content.Context
import android.net.Uri
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import fr.nexaexchange.mobile.data.AlertStore
import fr.nexaexchange.mobile.data.Base58
import fr.nexaexchange.mobile.data.PhoenixApi

/**
 * Connexion au portefeuille par Mobile Wallet Adapter.
 *
 * POURQUOI MWA ET PAS UNE CLE DANS L'APPLICATION
 * L'application ne voit jamais de cle privee. Elle demande au portefeuille installe sur
 * le telephone — Phantom, Solflare, ou le Seed Vault du Seeker — d'autoriser une session
 * et, plus tard, de signer. C'est le protocole officiel de Solana Mobile, et c'est la
 * seule facon acceptable de toucher aux fonds de quelqu'un.
 *
 * API relevee dans la documentation officielle le 13 septembre 2026
 * (docs.solanamobile.com/android-native/using_mobile_wallet_adapter), pas de memoire.
 *
 * ⚠️ CONTRAINTE ANDROID : [ActivityResultSender] enregistre un lanceur de resultat
 * d'activite. Il DOIT etre construit pendant onCreate, avant que l'activite n'atteigne
 * l'etat STARTED — sinon Android leve une IllegalStateException a l'execution, pas a la
 * compilation. C'est pour cela que MainActivity le cree et le passe ici.
 */
class WalletManager(context: Context) {

    private val store = AlertStore(context)

    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://nexa-exchange.fr"),
            iconUri = Uri.parse("favicon.png"),
            identityName = "NEXA EXCHANGE",
        )
    ).apply {
        // Rendre le jeton d'autorisation de la session precedente evite a l'utilisateur
        // de revalider la connexion a chaque ouverture de l'application.
        store.walletAuthToken?.let { authToken = it }
    }

    /** Adresse memorisee, ou null. Lisible sans reseau, des le premier rendu. */
    val savedAddress: String? get() = store.walletAddress

    sealed interface Outcome {
        data class Connected(val address: String) : Outcome
        data object NoWallet : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun connect(sender: ActivityResultSender): Outcome =
        when (val r = adapter.connect(sender)) {
            is TransactionResult.Success -> {
                val account = r.authResult.accounts.firstOrNull()
                if (account == null) {
                    Outcome.Failed("The wallet returned no account")
                } else {
                    val address = Base58.encode(account.publicKey)
                    store.walletAddress = address
                    store.walletAuthToken = adapter.authToken
                    Outcome.Connected(address)
                }
            }
            is TransactionResult.NoWalletFound ->
                Outcome.NoWallet
            is TransactionResult.Failure ->
                // On remonte le message tel quel plutot qu'un « erreur » generique :
                // l'utilisateur qui refuse la connexion et celui dont le portefeuille
                // plante ne doivent pas lire la meme chose.
                Outcome.Failed(r.e.message ?: "Wallet refused the connection")
        }

    /**
     * Deconnexion — INSTANTANEE et purement locale.
     *
     * 🔴 REECRIT LE 13 SEPTEMBRE APRES TEST SUR SEEKER.
     * La premiere version appelait adapter.disconnect(), qui envoie une intention au
     * portefeuille pour qu'il invalide le jeton. Resultat filme sur l'appareil : toucher
     * « Disconnect » ouvrait le selecteur d'application Android — Amine a quatre
     * portefeuilles installes, Backpack, Jupiter, Phantom, Solflare — puis exigeait une
     * approbation. Personne n'a jamais approuve une deconnexion, et sa reaction a ete
     * exactement la bonne : « c'est bizarre ».
     *
     * Ce que l'utilisateur veut dire par « deconnecter », c'est : cette application doit
     * oublier mon portefeuille. Cela s'obtient entierement en local, sans reseau et sans
     * sortir de l'application. On efface donc, point.
     *
     * Ce qu'on perd, dit franchement : le portefeuille garde une trace d'autorisation
     * pour NEXA. Elle est inexploitable — nous n'avons plus le jeton — mais elle reste
     * listee dans ses reglages. Qui veut l'effacer vraiment a [revokeInWallet], propose
     * separement et etiquete pour ce qu'il est.
     */
    fun disconnect() {
        adapter.authToken = null
        store.walletAddress = null
        store.walletAuthToken = null
        // Le compte trader derive du portefeuille : le garder en memoire apres une
        // deconnexion, ce serait continuer a tenir en RAM une donnee que l'utilisateur
        // vient justement de demander d'oublier.
        PhoenixApi.forgetTraderPda()
    }

    /**
     * Revocation cote portefeuille, puis deconnexion locale.
     *
     * Ouvre l'application de portefeuille et demande une approbation : c'est inevitable,
     * seul le portefeuille peut invalider un jeton qu'il a emis. L'interface previent
     * avant de le declencher, au lieu de le faire dans le dos de l'utilisateur.
     *
     * L'ordre compte : on revoque TANT QU'ON A ENCORE le jeton, puis on efface. L'inverse
     * ne reviendrait a rien faire.
     */
    suspend fun revokeInWallet(sender: ActivityResultSender) {
        try {
            adapter.disconnect(sender)
        } catch (e: Exception) {
            // L'utilisateur peut avoir ferme le selecteur ou refuse : on deconnecte
            // localement quand meme. Un bouton qui ne fait rien serait pire qu'un jeton
            // orphelin chez le portefeuille.
        } finally {
            disconnect()
        }
    }
}
