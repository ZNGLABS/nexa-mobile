package fr.nexaexchange.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import fr.nexaexchange.mobile.service.Notifications
import fr.nexaexchange.mobile.ui.MarketsScreen
import fr.nexaexchange.mobile.ui.NexaTheme
import fr.nexaexchange.mobile.wallet.WalletManager

class MainActivity : ComponentActivity() {

    /**
     * Etat de la permission de notification, observe par l'interface.
     * Sans elle, le moniteur tournerait sans jamais rien afficher : l'utilisateur
     * croirait etre alerte alors qu'il ne l'est pas. L'ecran le dit explicitement.
     */
    private val notificationsAutorisees = mutableStateOf(true)

    private val demandePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { accorde -> notificationsAutorisees.value = accorde }

    /**
     * 🔴 CONSTRUIT ICI, ET NULLE PART AILLEURS.
     * ActivityResultSender enregistre un lanceur de resultat d'activite, ce qu'Android
     * n'autorise QUE tant que l'activite n'a pas atteint l'etat STARTED. Le creer plus
     * tard — depuis un composable, par exemple — compile parfaitement et plante a
     * l'execution avec une IllegalStateException.
     */
    private lateinit var sender: ActivityResultSender
    private lateinit var wallet: WalletManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sender = ActivityResultSender(this)
        wallet = WalletManager(this)

        Notifications.ensureChannels(this)
        rafraichirEtatPermission()

        setContent {
            NexaTheme {
                MarketsScreen(
                    notificationsAllowed = notificationsAutorisees.value,
                    onRequestNotifications = { demanderPermission() },
                    wallet = wallet,
                    sender = sender,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // L'utilisateur peut avoir change la permission dans les reglages systeme
        // pendant que l'application etait en arriere-plan.
        rafraichirEtatPermission()
    }

    private fun rafraichirEtatPermission() {
        notificationsAutorisees.value =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
            else ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun demanderPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            demandePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
