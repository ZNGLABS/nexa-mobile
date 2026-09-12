package fr.nexaexchange.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateOf
import fr.nexaexchange.mobile.service.Notifications
import fr.nexaexchange.mobile.ui.NexaTheme
import fr.nexaexchange.mobile.ui.MarketsScreen

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        rafraichirEtatPermission()

        setContent {
            NexaTheme {
                MarketsScreen(
                    notificationsAllowed = notificationsAutorisees.value,
                    onRequestNotifications = { demanderPermission() },
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
