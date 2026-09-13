package fr.nexaexchange.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import fr.nexaexchange.mobile.R
import fr.nexaexchange.mobile.data.AlertDirection
import fr.nexaexchange.mobile.data.AlertStore
import fr.nexaexchange.mobile.data.Base58
import fr.nexaexchange.mobile.data.Market
import fr.nexaexchange.mobile.data.PhoenixApi
import fr.nexaexchange.mobile.data.PriceAlert
import fr.nexaexchange.mobile.service.PriceMonitorService
import fr.nexaexchange.mobile.wallet.WalletManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
    wallet: WalletManager,
    sender: ActivityResultSender,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AlertStore(context) }

    // Adresse lue depuis les preferences : l'etat « connecte » s'affiche des le premier
    // rendu, sans attendre le portefeuille ni le reseau.
    var address by remember { mutableStateOf(wallet.savedAddress) }
    var walletBusy by remember { mutableStateOf(false) }
    var walletMessage by remember { mutableStateOf<String?>(null) }

    var markets by remember { mutableStateOf<List<Market>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<PriceAlert>>(store.all()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var monitorOn by remember { mutableStateOf(store.monitorEnabled) }
    var dialogFor by remember { mutableStateOf<Market?>(null) }

    // Ordre d'affichage FIGE au premier chargement.
    // 🔴 Sans cela, la liste est retriee par volume 24 h a chaque rafraichissement et
    // les lignes changent de place sous le doigt de l'utilisateur : sur la video de
    // test du 13 septembre, ZEC et ETH permutaient toutes les dix secondes. Le volume
    // bouge en permanence, l'ordre ne doit pas.
    var order by remember { mutableStateOf<List<String>>(emptyList()) }

    // Rafraichissement pendant que l'ecran est visible. Le service de fond, lui,
    // continue independamment : les deux ne se marchent pas dessus.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val fresh = PhoenixApi.fetchMarketsWithPrices()
                if (order.isEmpty()) order = fresh.map { it.symbol }
                val rang = order.withIndex().associate { (i, s) -> s to i }
                // Un marche apparu apres le premier chargement va en fin de liste
                // plutot que de decaler tout le reste.
                markets = fresh.sortedBy { rang[it.symbol] ?: Int.MAX_VALUE }
                error = null
            } catch (e: Exception) {
                // On n'efface pas la liste deja affichee : mieux vaut des prix d'il y
                // a dix secondes qu'un ecran vide.
                if (markets.isEmpty()) error = e.message ?: "Network error"
            }
            loading = false
            delay(10_000)
        }
    }

    val filtered = remember(markets, query) {
        if (query.isBlank()) markets
        else markets.filter {
            it.symbol.contains(query, true) || it.name.contains(query, true)
        }
    }

    Scaffold(
        containerColor = NexaBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // L'embleme NEXA, le meme fichier que l'icone de lancement :
                        // une seule image dans l'APK, et l'en-tete ne peut pas diverger
                        // de l'icone si la marque evolue.
                        Image(
                            painter = painterResource(id = R.mipmap.nexa_emblem),
                            contentDescription = "NEXA",
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("NEXA", fontWeight = FontWeight.Black, color = NexaGold, fontSize = 20.sp)
                            Text(
                                "Phoenix perps · ${markets.size} markets",
                                color = NexaMuted, fontSize = 11.sp,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NexaBg),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            if (!notificationsAllowed) {
                WarningBanner(
                    text = "Notifications are off — alerts will not reach you.",
                    actionLabel = "Allow",
                    onAction = onRequestNotifications,
                )
            }

            WalletRow(
                address = address,
                busy = walletBusy,
                message = walletMessage,
                onConnect = {
                    walletBusy = true; walletMessage = null
                    scope.launch {
                        when (val r = wallet.connect(sender)) {
                            is WalletManager.Outcome.Connected -> {
                                address = r.address
                            }
                            is WalletManager.Outcome.NoWallet -> {
                                walletMessage = "No Solana wallet app found on this device."
                            }
                            is WalletManager.Outcome.Failed -> {
                                walletMessage = r.message
                            }
                        }
                        walletBusy = false
                    }
                },
                onDisconnect = {
                    walletBusy = true; walletMessage = null
                    scope.launch {
                        wallet.disconnect(sender)
                        address = null
                        walletBusy = false
                    }
                },
            )

            MonitorRow(
                on = monitorOn,
                alertCount = alerts.count { it.enabled },
                onToggle = { want ->
                    monitorOn = want
                    if (want) PriceMonitorService.start(context) else PriceMonitorService.stop(context)
                },
            )

            if (alerts.isNotEmpty()) {
                AlertsStrip(
                    alerts = alerts,
                    onDelete = { id -> store.remove(id); alerts = store.all() },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search a market", color = NexaMuted) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            )

            when {
                loading && markets.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = NexaGold)
                }
                error != null && markets.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Could not reach Phoenix: $error", color = NexaMuted)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.symbol }) { m ->
                        MarketRow(m) { dialogFor = m }
                    }
                }
            }
        }
    }

    dialogFor?.let { m ->
        NewAlertDialog(
            market = m,
            onDismiss = { dialogFor = null },
            onCreate = { direction, threshold ->
                store.add(m.symbol, direction, threshold)
                alerts = store.all()
                store.widgetSymbol = m.symbol
                dialogFor = null
                if (!monitorOn) {
                    monitorOn = true
                    PriceMonitorService.start(context)
                }
            },
        )
    }
}

@Composable
private fun WarningBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(Color(0x33EF4444), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = NexaText, fontSize = 12.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(actionLabel, color = NexaGold) }
    }
}

/**
 * Ligne du portefeuille.
 *
 * Quand rien n'est connecte, elle explique ce que la connexion apporte AVANT de la
 * demander. Un bouton « Connect wallet » nu, dans une application de trading, se fait
 * refuser par prudence — et l'utilisateur a raison de se mefier.
 */
@Composable
private fun WalletRow(
    address: String?,
    busy: Boolean,
    message: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (address != null) "Wallet connected" else "Wallet",
                    color = NexaText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
                Text(
                    address?.let { Base58.shorten(it, 6, 6) }
                        ?: "Connect to see your Phoenix positions",
                    color = if (address != null) NexaGold else NexaMuted,
                    fontSize = 11.sp,
                    fontFamily = if (address != null) FontFamily.Monospace else FontFamily.Default,
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    color = NexaGold,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                TextButton(onClick = if (address != null) onDisconnect else onConnect) {
                    Text(
                        if (address != null) "Disconnect" else "Connect",
                        color = if (address != null) NexaMuted else NexaGold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        if (message != null) {
            Text(message, color = NexaRed, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (address == null) {
            // Dit explicitement ce que l'application NE fait PAS. C'est la phrase qui
            // decide un utilisateur prudent, pas le bouton.
            Text(
                "NEXA never sees your private key. Your wallet app approves every action.",
                color = NexaMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun MonitorRow(on: Boolean, alertCount: Int, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Background monitor", color = NexaText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                if (on) "Running · $alertCount alert(s) · checks every 60s"
                else "Off · alerts will not fire",
                color = if (on) NexaGreen else NexaMuted, fontSize = 11.sp,
            )
        }
        Switch(checked = on, onCheckedChange = onToggle)
    }
}

/**
 * Bandeau des alertes armees.
 *
 * 🔴 REECRIT APRES LE TEST DU 13 SEPTEMBRE. La premiere version empilait une ligne
 * pleine largeur par alerte, avec un bouton « Remove » a droite. Avec cinq alertes,
 * elles occupaient la moitie de l'ecran et repoussaient la liste des marches tout en
 * bas : filme sur le Seeker, c'etait le defaut le plus visible de l'application.
 * Desormais une seule rangee qui defile horizontalement, quel que soit le nombre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsStrip(alerts: List<PriceAlert>, onDelete: (Long) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(alerts, key = { it.id }) { a ->
            Row(
                Modifier
                    .background(Color(0x1AF5B700), RoundedCornerShape(14.dp))
                    .clickable { onDelete(a.id) }
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${a.symbol} ${if (a.direction == AlertDirection.ABOVE) "↑" else "↓"} " +
                        "$" + PriceMonitorService.fmt(a.threshold),
                    color = NexaGold, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(7.dp))
                // Croix plutot que le mot « Remove » : meme fonction, une fraction de
                // la largeur, et l'intention reste lisible.
                Text("×", color = NexaMuted, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun MarketRow(m: Market, onClick: () -> Unit) {
    val chg = m.change24hPct
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = NexaCard),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pastille coloree plutot que le logo distant : les logos Phoenix sont
            // des SVG, qui demanderaient une bibliotheque de plus et une requete
            // reseau par ligne. La couleur vient des metadonnees du marche.
            Box(
                Modifier.size(34.dp)
                    .background(parseColor(m.displayColor), RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.symbol.take(2),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.symbol, color = NexaText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("${m.maxLeverage}x", color = NexaGold, fontSize = 10.sp)
                }
                Text(m.name, color = NexaMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    m.markPrice?.let { "$" + PriceMonitorService.fmt(it) } ?: "—",
                    color = NexaText, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                )
                Text(
                    chg?.let { PriceMonitorService.fmtPct(it) } ?: "—",
                    color = when {
                        chg == null -> NexaMuted
                        chg >= 0 -> NexaGreen
                        else -> NexaRed
                    },
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun NewAlertDialog(
    market: Market,
    onDismiss: () -> Unit,
    onCreate: (AlertDirection, Double) -> Unit,
) {
    val current = market.markPrice
    var value by remember { mutableStateOf(current?.let { PriceMonitorService.fmt(it) } ?: "") }
    var direction by remember {
        mutableStateOf(AlertDirection.ABOVE)
    }
    val parsed = value.replace(",", "").toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NexaCard,
        title = { Text("Alert on ${market.symbol}", color = NexaText) },
        text = {
            Column {
                Text(
                    "Now: " + (current?.let { "$" + PriceMonitorService.fmt(it) } ?: "unknown"),
                    color = NexaMuted, fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row {
                    FilterChip(
                        selected = direction == AlertDirection.ABOVE,
                        onClick = { direction = AlertDirection.ABOVE },
                        label = { Text("Rises above") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = direction == AlertDirection.BELOW,
                        onClick = { direction = AlertDirection.BELOW },
                        label = { Text("Falls below") },
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = { Text("Price in USD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed > 0,
                onClick = { parsed?.let { onCreate(direction, it) } },
            ) { Text("Create alert", color = NexaGold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = NexaMuted) } },
    )
}

/** #RRGGBB venant des metadonnees Phoenix ; gris neutre si absent ou illisible. */
private fun parseColor(hex: String?): Color = try {
    if (hex.isNullOrBlank()) Color(0xFF3A342C)
    else Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
} catch (e: Exception) {
    Color(0xFF3A342C)
}
