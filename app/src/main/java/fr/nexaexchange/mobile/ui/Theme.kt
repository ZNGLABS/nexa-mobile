package fr.nexaexchange.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Couleurs reprises telles quelles des variables CSS de nexa-exchange.fr, pour que
 * l'application native et le site soient reconnaissables comme un meme produit.
 * Relevees dans index.html le 12 septembre 2026.
 */
val NexaBg = Color(0xFF0C0A08)
val NexaCard = Color(0xFF1A1713)
val NexaGold = Color(0xFFF5B700)
val NexaGreen = Color(0xFF22C55E)
val NexaRed = Color(0xFFEF4444)
val NexaText = Color(0xFFF2EDE4)
val NexaMuted = Color(0xFF8C8377)

private val scheme = darkColorScheme(
    primary = NexaGold,
    onPrimary = Color(0xFF241B00),
    secondary = NexaGold,
    background = NexaBg,
    onBackground = NexaText,
    surface = NexaCard,
    onSurface = NexaText,
    surfaceVariant = Color(0xFF241F19),
    onSurfaceVariant = NexaMuted,
    error = NexaRed,
)

/**
 * Theme sombre uniquement. Ce n'est pas un oubli : un ecran de marches se consulte
 * souvent de nuit, et le site est lui-meme sombre. Proposer un theme clair aurait
 * demande une seconde palette a maintenir pour aucun besoin exprime.
 */
@Composable
fun NexaTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = scheme, content = content)
}
