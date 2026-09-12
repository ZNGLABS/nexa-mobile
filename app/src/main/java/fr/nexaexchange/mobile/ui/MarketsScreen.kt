package fr.nexaexchange.mobile.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.nexaexchange.mobile.data.AlertDirection
import fr.nexaexchange.mobile.data.AlertStore
import fr.nexaexchange.mobile.data.Market
import fr.nexaexchange.mobile.data.PhoenixApi
import fr.nexaexchange.mobile.data.PriceAlert
import fr.nexaexchange.mobile.service.PriceMonitorService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketsScreen(
    notificationsAllowed: Boolean,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AlertStore(context) }

    var markets by remember { mutableStateOf<List<Market>>(emptyList()) }
    var alerts by remember { mutableStateOf<List<PriceAlert>>(store.all()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var monitorOn by remember { mutableStateOf(store.monitorEnabled) }
    var dialogFor by remember { mutableStateOf<Market?>(null) }

    // Rafraichissement pendant que l'ecran est visible. Le service de fond, lui,
    // continue independamment : les deux ne se marchent pas dessus.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                markets = PhoenixApi.fetchMarketsWithPrices()
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
                    Column {
                        Text("NEXA", fontWeight = FontWeight.Black, color = NexaGold, fontSize = 20.sp)
                        Text(
                            "Phoenix perps · ${markets.size} markets",
                            color = NexaMuted, fontSize = 11.sp,
                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertsStrip(alerts: List<PriceAlert>, onDelete: (Long) -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp)) {
        alerts.forEach { a ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = {
                        Text(
                            "${a.symbol} ${if (a.direction == AlertDirection.ABOVE) "↑" else "↓"} " +
                                "$" + PriceMonitorService.fmt(a.threshold),
                            fontSize = 12.sp,
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onDelete(a.id) }) { Text("Remove", color = NexaMuted, fontSize = 12.sp) }
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
                    chg?.let { (if (it >= 0) "+" else "") + String.format("%.2f", it) + "%" } ?: "—",
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
