package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidConditionPhrases
import dev.antigravity.fluidengine.foundation.FluidConditions
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.FluidThermal
import dev.antigravity.fluidengine.foundation.FluidWork
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import dev.antigravity.fluidengine.net.EngineConditions
import dev.antigravity.fluidengine.net.testing.FakeNetworks
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.sync.FluidSync
import dev.antigravity.fluidengine.sync.InMemorySyncStore
import dev.antigravity.fluidengine.sync.MemorySyncRelay
import dev.antigravity.fluidengine.sync.SyncSealContext
import dev.antigravity.fluidengine.sync.SyncSealedPayload
import dev.antigravity.fluidengine.sync.SyncSealer
import dev.antigravity.fluidengine.sync.SyncSpaceId
import dev.antigravity.fluidengine.sync.testing.FakeSyncRelay
import dev.antigravity.fluidengine.transfer.FluidTransferRequest
import dev.antigravity.fluidengine.transfer.FluidTransfers
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidConnectionCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.rememberFluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.fluid.transfer.FluidTransferActivity
import dev.antigravity.fluidengine.ui.fluid.transfer.FluidTransfersPage
import dev.antigravity.fluidengine.ui.fluid.transfer.fluidTransferPhrases
import dev.antigravity.fluidengine.ui.fluid.transfer.LocalFluidTransferLabels
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidSyncIndicator
import dev.antigravity.fluidengine.ui.theme.FluidSyncNotice
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// F8.20 · Rete, condizioni del telefono e trasferimenti. Niente qui cambia la rete del tablet: la rete delle scene e'
// finta (le reti di `FakeNetworks`, con la regola e la tenuta vere dell'osservatore), le condizioni si simulano con
// l'interruttore di sviluppo della galleria (`FluidDeviceConditions.simulate`), e i trasferimenti scaricano da un server
// locale (quello della galleria sull'indirizzo di loopback, o quello del PC con `adb reverse tcp:8765 tcp:8765`).

internal val F8p20Scenes = listOf(
  NewElementScene("rete-capsula", "Offline · 3 modifiche in attesa", "La capsula di vetro della rete: con la rete simulata, le micro-cadute che non lampeggiano e il pannello con «Sincronizza ora»") {
    CapsuleScene(it)
  },
  NewElementScene("rete-condizioni", "Pochi dati, poca batteria", "Un posto solo per rete a consumo, roaming, risparmio dati ed energetico, calore e carica; ogni lavoro chiede il permesso e offre «Fallo comunque»") {
    ConditionsScene(it)
  },
  NewElementScene("trasferimenti", "Centro trasferimenti", "La coda vera con WorkManager: pausa e ripresa con Range, solo Wi-Fi, l'app chiusa a meta', la pagina «Trasferimenti» e la pillola") {
    TransfersScene(it)
  },
)

internal val F8p20Section = NewElementSection(
  pack = "F8.20",
  title = "Rete e trasferimenti",
  scenes = F8p20Scenes,
  detail = "La capsula «Offline», le condizioni del telefono e la coda dei trasferimenti, per l'Account Pampa e Pampa Store",
)

internal val F8p20TryItems = listOf(
  TryItem(
    "f8-20-capsula", TryArea.Pages, "La capsula offline",
    "Scegli «Senza rete» e scrivi tre modifiche («Offline · 3 modifiche in attesa»), torna «In linea» («Di nuovo in linea · sincronizzo», poi «Tutto aggiornato», poi sparisce) e prova «Micro-caduta», che non deve lampeggiare.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Offline · 3 modifiche in attesa"),
  ),
  TryItem(
    "f8-20-pannello", TryArea.Pages, "Il pannello della capsula",
    "Senza rete tocca la capsula: il pannello nasce da lei e dice l'ultima sincronizzazione e cosa aspetta; torna in linea a pannello aperto e premi «Sincronizza ora».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Offline · 3 modifiche in attesa"),
  ),
  TryItem(
    "f8-20-condizioni", TryArea.Pages, "Rete a consumo e «Fallo comunque»",
    "Accendi «Rete a consumo» e premi «Scarica la mappa», che aspetta e dice perché, poi «Fallo comunque» la fa partire; col «Risparmio energetico» la luce del vetro e l'aptica continua si fermano.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Pochi dati, poca batteria"),
  ),
  TryItem(
    "f8-20-trasferimenti", TryArea.Pages, "Pausa, ripresa e app chiusa",
    "Scarica Pampa Notes, mettilo in pausa a metà e riprendi (riparte da dov'era), poi esci dall'app a metà e riaprila: il file arriva intero.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Centro trasferimenti"),
  ),
)

/** I tag delle prove. */
internal object F8p20Tags {
  const val Network = "rete:simulata"
  const val Write = "rete:scrivi"
  const val MicroCut = "rete:micro-caduta"
  const val Handover = "rete:cambio"
  const val Frame = "rete:cornice"
  fun simulate(name: String) = "condizioni:$name"
  fun work(name: String) = "condizioni:lavoro:$name"
  const val Download = "condizioni:scarica"
  const val Force = "condizioni:forza"
  fun app(name: String) = "trasferimenti:$name"
}

// --- I fondi ---------------------------------------------------------------------------------------------------------

private enum class NetBackdrop(val label: String) { Page("Pagina"), Light("Foto chiara"), Dark("Foto scura") }

@Composable
private fun NetBackdropPicker(ground: NetBackdrop, onSelect: (NetBackdrop) -> Unit) {
  FluidSegmentedControl(
    options = NetBackdrop.entries,
    selected = ground,
    onSelect = onSelect,
    modifier = Modifier.fillMaxWidth(),
    label = { it.label },
  )
}

// --- Scena 1: la capsula -----------------------------------------------------------------------------------------------

/** La rete della scena: cosa sceglie il segmentato. Nessuna tocca il tablet. */
private enum class SimulatedNet(val label: String) {
  Online("In linea"), Offline("Senza rete"), Captive("Wi-Fi senza internet"), VpnDown("VPN, sotto niente"),
}

/** Un sigillo che non sigilla: la scena mostra la rete, non la cifratura (quella e' di F8.21 e dell'account). */
private object PlainSealer : SyncSealer {
  override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload = SyncSealedPayload(plaintext)
  override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray = payload.bytes
}

private val NotesSpace = SyncSpaceId("note")

@Composable
private fun CapsuleScene(onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val nets = remember { FakeNetworks() }
  val relay = remember { FakeSyncRelay(MemorySyncRelay()).apply { latencyMillis = 1_200L } }
  val sync = remember { FluidSync(InMemorySyncStore(), relay, PlainSealer, node = "telefono", dispatcher = Dispatchers.Default) }
  val notes = remember { sync.space(NotesSpace) }
  val network by remember { nets.states }.collectAsState(initial = nets.raw.value)
  val status by sync.status.collectAsState()
  var simulated by rememberSaveable { mutableStateOf(SimulatedNet.Online) }
  var ground by rememberSaveable { mutableStateOf(NetBackdrop.Page) }
  var written by remember { mutableIntStateOf(0) }

  // La rete del relay finto segue quella senza tenuta (come un socket vero); quando la rete torna si sincronizza (come
  // farebbe WorkManager, con la rete come vincolo).
  LaunchedEffect(nets) { nets.raw.collect { relay.online = it.online } }
  LaunchedEffect(network.online) { if (network.online) withContext(Dispatchers.Default) { sync.syncAll() } }

  fun choose(next: SimulatedNet) {
    simulated = next
    nets.vpnOff()
    when (next) {
      SimulatedNet.Online -> nets.wifi()
      SimulatedNet.Offline -> nets.disconnect()
      SimulatedNet.Captive -> nets.wifi(validated = false, captivePortal = true)
      SimulatedNet.VpnDown -> {
        nets.wifi(validated = false)
        nets.vpn(claimsValidated = true)
      }
    }
  }

  FluidScreen(
    title = "Note",
    subtitle = "F8.20 · La capsula della rete",
    onBack = onBack,
    actions = { FluidSyncIndicator(status) },
    // F8.20b: la capsula sta nell'overlay della schermata e rifrange la pagina (il fondale della chrome), in basso al
    // centro come ha scelto Alessio. Prima stava dentro la cornice con un fondale suo: una registrazione in piu' per il
    // tetto del vetro, e sul Tab S9 il vetro limpido.
    overlay = { pageGlass ->
      FluidConnectionCapsule(
        online = network.online,
        status = status,
        connected = network.connected,
        onSyncNow = { withContext(Dispatchers.Default) { sync.syncAll() } },
        backdrop = pageGlass,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
      )
    },
  ) {
    item(key = "fondo") { NetBackdropPicker(ground) { ground = it } }
    item(key = "rete") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Rete simulata", detail = "Il tablet resta com'è: cambia solo la rete della scena")
        FluidSegmentedControl(
          options = SimulatedNet.entries,
          selected = simulated,
          onSelect = ::choose,
          modifier = Modifier.fillMaxWidth().testTag(F8p20Tags.Network),
          label = { it.label },
        )
        FluidButton(
          text = "Scrivi una modifica",
          onClick = {
            written++
            scope.launch(Dispatchers.Default) {
              notes.create { text("titolo", "Nota $written"); longText("testo", "Scritta alle ${System.currentTimeMillis() % 100_000}") }
              delay(700)
              sync.syncAll()
            }
          },
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
          fillWidth = true,
          modifier = Modifier.testTag(F8p20Tags.Write),
        )
        FluidButton(
          text = "Micro-caduta di 0,6 s",
          onClick = { scope.launch { nets.microCut(600) } },
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Small,
          fillWidth = true,
          enabled = simulated == SimulatedNet.Online,
          modifier = Modifier.testTag(F8p20Tags.MicroCut),
        )
        FluidButton(
          text = "Dal Wi-Fi ai dati mobili",
          onClick = { scope.launch { nets.handover() } },
          style = FluidButtonStyle.Plain,
          size = FluidButtonSize.Small,
          fillWidth = true,
          enabled = simulated == SimulatedNet.Online,
          modifier = Modifier.testTag(F8p20Tags.Handover),
        )
      }
    }
    item(key = "avviso") { FluidSyncNotice(status, onRetry = { scope.launch(Dispatchers.Default) { sync.syncAll() } }) }
    item(key = "cornice") { NotesFrame(ground) }
    item(key = "nota") {
      FluidSectionFootnote(
        "La capsula legge «online» dall'osservatore della rete, con la sua tenuta di 2,5 s (le reti qui sono finte, la " +
          "regola e la tenuta sono quelle vere) e le modifiche in attesa da engine-sync. Rete: ${describe(network.online, network.connected, network.vpn, network.metered)}.",
      )
    }
  }
}

private fun describe(online: Boolean, connected: Boolean, vpn: Boolean, metered: Boolean): String = when {
  online && vpn -> "in linea con la VPN"
  online && metered -> "in linea, a consumo"
  online -> "in linea"
  connected -> "c'è, ma non porta a internet"
  else -> "nessuna"
}

/** Una pagina di note dentro una cornice, sul fondo scelto: si fa scorrere sotto la capsula. */
@Composable
private fun NotesFrame(ground: NetBackdrop) {
  val shape = ContinuousCornerShape(FluidRadius.Group)
  Box(
    Modifier
      .fillMaxWidth()
      .height(360.dp)
      .clip(shape)
      .testTag(F8p20Tags.Frame),
  ) {
    if (ground == NetBackdrop.Page) {
      Column(
        Modifier
          .matchParentSize()
          .background(MaterialTheme.colorScheme.surfaceContainerLow)
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        listOf("Spesa", "Gita a Firenze", "Compiti di fisica", "Idee per il regalo", "Libri da leggere", "Ricetta del pane").forEach {
          Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
          Text("Modificata da poco su questo telefono", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      Image(
        painter = painterResource(if (ground == NetBackdrop.Light) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize(),
      )
    }
  }
}

// --- Scena 2: le condizioni --------------------------------------------------------------------------------------------

/** Cosa simula l'interruttore di sviluppo: ogni voce cambia una condizione sopra quelle vere. */
private class Simulation(
  val metered: Boolean = false,
  val roaming: Boolean = false,
  val dataSaver: Boolean = false,
  val powerSave: Boolean = false,
  val hot: Boolean = false,
  val charging: Boolean = false,
) {
  val active: Boolean get() = metered || roaming || dataSaver || powerSave || hot || charging

  fun apply(real: FluidConditions): FluidConditions = real.copy(
    metered = real.metered || metered || roaming,
    roaming = real.roaming || roaming,
    dataSaver = real.dataSaver || dataSaver,
    powerSave = real.powerSave || powerSave,
    thermal = if (hot) FluidThermal.Serious else real.thermal,
    charging = real.charging || charging,
  )

  fun copy(
    metered: Boolean = this.metered,
    roaming: Boolean = this.roaming,
    dataSaver: Boolean = this.dataSaver,
    powerSave: Boolean = this.powerSave,
    hot: Boolean = this.hot,
    charging: Boolean = this.charging,
  ) = Simulation(metered, roaming, dataSaver, powerSave, hot, charging)
}

private class DemoWork(val name: String, val title: String, val work: FluidWork)

private val DemoWorks = listOf(
  DemoWork("mappa", "Scarica la mappa della Toscana (120 MB)", FluidWork.LargeDownload),
  DemoWork("video", "Carica il video della gita", FluidWork.LargeUpload),
  DemoWork("anticipo", "Scarica in anticipo le prossime puntate", FluidWork.Prefetch),
  DemoWork("media", "Video che partono da soli", FluidWork.RichMedia),
  DemoWork("note", "Sincronizza le note", FluidWork.Sync),
  DemoWork("luce", "La luce del vetro che segue l'inclinazione", FluidWork.MotionEffects),
  DemoWork("aptica", "L'aptica continua di cursori e ruote", FluidWork.ContinuousHaptics),
)

@Composable
private fun ConditionsScene(onBack: () -> Unit) {
  val context = LocalContext.current
  LaunchedEffect(Unit) { EngineConditions.install(context) }
  var simulation by remember { mutableStateOf(Simulation()) }
  var lowData by remember { mutableStateOf(FluidDeviceConditions.lowData) }
  DisposableEffect(Unit) {
    onDispose {
      // L'interruttore di sviluppo vale solo nella scena: uscendo si torna alle condizioni vere.
      FluidDeviceConditions.simulate(null)
      FluidDeviceConditions.lowData = false
    }
  }
  LaunchedEffect(simulation) {
    FluidDeviceConditions.simulate(if (simulation.active) simulation::apply else null)
  }
  val conditions by FluidDeviceConditions.state.collectAsState()
  val measured = remember(conditions) { FluidDeviceConditions.measured }
  val phrases = remember { FluidConditionPhrases() }
  var forced by remember { mutableStateOf(false) }
  var downloaded by remember { mutableStateOf(false) }
  var asked by remember { mutableStateOf(false) }

  FluidScreen(title = "Pochi dati, poca batteria", subtitle = "F8.20 · Le condizioni del telefono", onBack = onBack) {
    item(key = "vere") {
      Column {
        FluidSectionHeader(title = "Lette dal telefono", detail = "EngineConditions: rete, risparmio dati ed energetico, calore, carica")
        FluidListGroup {
          FluidListRow(title = "Rete", subtitle = describe(measured.online, measured.connected, false, measured.metered) + if (measured.roaming) ", in roaming" else "", disclosure = false)
          FluidListDivider()
          FluidListRow(title = "Risparmio dati", subtitle = if (measured.dataSaver) "Acceso su questa rete" else "Spento (o rete che non costa)", disclosure = false)
          FluidListDivider()
          FluidListRow(title = "Risparmio energetico", subtitle = if (measured.powerSave) "Acceso" else "Spento", disclosure = false)
          FluidListDivider()
          FluidListRow(title = "Calore", subtitle = thermalLabel(measured.thermal), disclosure = false)
          FluidListDivider()
          FluidListRow(title = "Corrente", subtitle = if (measured.charging) "In carica" else "A batteria", disclosure = false)
        }
      }
    }
    item(key = "simula") {
      Column {
        FluidSectionHeader(title = "Interruttore di sviluppo", detail = "Simula le condizioni sopra quelle vere: il tablet non cambia")
        FluidListGroup {
          SimToggle("Rete a consumo", simulation.metered, "metered") { simulation = simulation.copy(metered = it) }
          FluidListDivider()
          SimToggle("Roaming", simulation.roaming, "roaming") { simulation = simulation.copy(roaming = it) }
          FluidListDivider()
          SimToggle("Risparmio dati", simulation.dataSaver, "datasaver") { simulation = simulation.copy(dataSaver = it, metered = it || simulation.metered) }
          FluidListDivider()
          SimToggle("Risparmio energetico", simulation.powerSave, "powersave") { simulation = simulation.copy(powerSave = it) }
          FluidListDivider()
          SimToggle("Telefono caldo", simulation.hot, "hot") { simulation = simulation.copy(hot = it) }
          FluidListDivider()
          SimToggle("In carica", simulation.charging, "charging") { simulation = simulation.copy(charging = it) }
          FluidListDivider()
          SimToggle("Usa pochi dati (dell'app)", lowData, "lowdata") {
            lowData = it
            FluidDeviceConditions.lowData = it
          }
        }
      }
    }
    item(key = "lavori") {
      Column {
        FluidSectionHeader(title = "Chi chiede il permesso", detail = "FluidDeviceConditions.allows(FluidWork…)")
        FluidListGroup {
          DemoWorks.forEachIndexed { index, demo ->
            if (index > 0) FluidListDivider()
            val permit = conditions.allows(demo.work)
            FluidListRow(
              title = demo.title,
              subtitle = if (permit.allowed) "Si può" else "Aspetta · " + permit.reasons.joinToString(", ") { phrases.reason(it) },
              tone = if (permit.allowed) FluidTone.Success else FluidTone.Warning,
              disclosure = false,
              modifier = Modifier.testTag(F8p20Tags.work(demo.name)),
            )
          }
        }
      }
    }
    item(key = "scarica") {
      val permit = conditions.allows(FluidWork.LargeDownload, force = forced)
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          text = if (downloaded) "Mappa scaricata" else "Scarica la mappa (120 MB)",
          onClick = {
            asked = true
            if (permit.allowed) downloaded = true
          },
          enabled = !downloaded,
          fillWidth = true,
          modifier = Modifier.testTag(F8p20Tags.Download),
        )
        if (asked && !downloaded && permit.waiting) {
          FluidInlineMessage(
            title = phrases.waitingFor(permit.reasons),
            message = permit.reasons.joinToString(", ") { phrases.reason(it) } +
              if (permit.canForce) ". La mappa parte da sola quando le condizioni cambiano." else ".",
            tone = FluidTone.Warning,
            action = if (permit.canForce) "Fallo comunque" else null,
            onClick = if (permit.canForce) ({
              forced = true
              downloaded = true
            }) else null,
            modifier = Modifier.testTag(F8p20Tags.Force),
          )
        }
        if (downloaded) {
          FluidButton(
            text = "Ricomincia",
            onClick = {
              downloaded = false
              forced = false
              asked = false
            },
            style = FluidButtonStyle.Plain,
            size = FluidButtonSize.Small,
          )
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "La luce del vetro che segue l'inclinazione e l'aptica continua leggono da qui: col risparmio energetico (vero o " +
          "simulato) si fermano. Il vetro e il risparmio energetico hanno le loro regole di F7, che non cambiano.",
      )
    }
  }
}

@Composable
private fun SimToggle(title: String, checked: Boolean, name: String, onChange: (Boolean) -> Unit) {
  FluidToggleRow(title = title, checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(F8p20Tags.simulate(name)))
}

private fun thermalLabel(thermal: FluidThermal): String = when (thermal) {
  FluidThermal.Nominal -> "Normale"
  FluidThermal.Fair -> "Un po' caldo"
  FluidThermal.Serious -> "Caldo"
  else -> "Molto caldo"
}

// --- Scena 3: i trasferimenti ------------------------------------------------------------------------------------------

/** Da dove si scarica: il server della galleria (in questo processo) o quello del PC con `adb reverse`. */
private enum class DemoSource(val label: String) { Gallery("Server della galleria"), Pc("Server del PC (:8765)") }

/** Le app della scena: cosa scaricare o caricare. */
private class DemoTransfer(val id: String, val title: String, val bytes: Long, val upload: Boolean = false, val wifiOnly: Boolean = false)

private val DemoTransfers = listOf(
  DemoTransfer("notes-apk", "Pampa Notes 2.4", 48L * 1_000_000L),
  DemoTransfer("mappa", "Mappa della Toscana", 120L * 1_000_000L, wifiOnly = true),
  DemoTransfer("video", "Video della gita", 24L * 1_000_000L, upload = true),
)

/** La cartella dei file della scena: la si svuota col tasto, e il rapporto ne scrive il percorso esatto. */
internal fun transfersFolder(context: Context): File = File(context.filesDir, "novita-trasferimenti")

/**
 * Toglie le notifiche dei trasferimenti della galleria: l'attivita' viva (o il suo esito, che engine-notify tiene quattro
 * ore) col tag `fluid.live/transfers`, e la notifica semplice del servizio. Solo le notifiche di quest'app.
 */
internal fun clearTransferNotifications(context: Context) {
  val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
  val tag = "fluid.live/" + dev.antigravity.fluidengine.transfer.LiveTransferNotifications.UpdateId
  runCatching {
    manager.activeNotifications
      .filter { it.tag == tag || it.notification.channelId == dev.antigravity.fluidengine.transfer.SimpleTransferNotifications.ChannelId }
      .forEach { manager.cancel(it.tag, it.id) }
  }
}

@Composable
private fun TransfersScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val transfers = remember { FluidTransfers.from(context) }
  val server = remember { DemoFileServer() }
  DisposableEffect(server) { onDispose { server.close() } }
  var source by rememberSaveable { mutableStateOf(DemoSource.Gallery) }
  var metered by remember { mutableStateOf(false) }
  DisposableEffect(Unit) { onDispose { FluidDeviceConditions.simulate(null) } }
  LaunchedEffect(metered) { FluidDeviceConditions.simulate(if (metered) ({ it.copy(metered = true) }) else null) }
  val items by transfers.items.collectAsState()
  val overview by transfers.overview.collectAsState()
  val activities = rememberFluidActivityHostState()
  var page by rememberSaveable { mutableStateOf(0) }
  val scope = rememberCoroutineScope()
  BackHandler(enabled = page == 1) { page = 0 }

  FluidActivityHost(activities) {
    FluidTransferActivity(activities, overview, transfers, onOpen = { page = 1 })
    if (page == 1) {
      FluidTransfersPage(items, transfers, onBack = { page = 0 })
    } else {
      FluidScreen(
        title = "Le tue app",
        subtitle = "F8.20 · Centro trasferimenti",
        onBack = onBack,
        toolbar = { title(FluidToolbarTitleMode.Inline) },
      ) {
        item(key = "fonte") {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FluidSegmentedControl(
              options = DemoSource.entries,
              selected = source,
              onSelect = { source = it },
              modifier = Modifier.fillMaxWidth(),
              label = { it.label },
            )
            FluidListGroup {
              FluidToggleRow(
                title = "Simula una rete a consumo",
                subtitle = "L'interruttore di sviluppo: la rete del tablet non cambia",
                checked = metered,
                onCheckedChange = { metered = it },
                modifier = Modifier.testTag(F8p20Tags.simulate("metered-transfers")),
              )
            }
          }
        }
        item(key = "app") {
          Column {
            FluidSectionHeader(title = "Da scaricare e caricare")
            FluidListGroup {
              DemoTransfers.forEachIndexed { index, demo ->
                if (index > 0) FluidListDivider()
                val item = items.firstOrNull { it.id == demo.id }
                DemoTransferRow(demo, item) {
                  scope.launch(Dispatchers.IO) { enqueue(context, transfers, server, source, demo) }
                }
              }
            }
          }
        }
        item(key = "pagina") {
          FluidListGroup {
            FluidListRow(
              title = "Trasferimenti",
              subtitle = if (overview.active.isEmpty()) "Niente in corso" else "${overview.active.size} in corso o in attesa",
              onClick = { page = 1 },
            )
          }
        }
        item(key = "pulizia") {
          FluidButton(
            text = "Togli i file della scena",
            onClick = {
              DemoTransfers.forEach { transfers.remove(it.id) }
              scope.launch(Dispatchers.IO) {
                delay(300)
                transfersFolder(context).deleteRecursively()
              }
              // F8.20b: anche l'esito della notifica viva (resta quattro ore): la scena non lascia niente sul telefono.
              clearTransferNotifications(context)
            },
            style = FluidButtonStyle.Plain,
            size = FluidButtonSize.Small,
            fillWidth = true,
          )
        }
        item(key = "nota") {
          FluidSectionFootnote(
            "La coda vera dell'engine (SQLite e WorkManager, col servizio in primo piano dataSync). I file stanno in " +
              "${transfersFolder(context).absolutePath}. Col server del PC: adb reverse tcp:8765 tcp:8765.",
          )
        }
      }
    }
  }
}

@Composable
private fun DemoTransferRow(demo: DemoTransfer, item: FluidTransferItem?, onStart: () -> Unit) {
  val labels = LocalFluidTransferLabels.current
  val subtitle = when {
    item == null || item.state == FluidTransferState.Cancelled ->
      (if (demo.upload) "Caricamento" else "Scaricamento") + " · " + dev.antigravity.fluidengine.foundation.FluidFormat.bytes(demo.bytes) +
        if (demo.wifiOnly) " · solo Wi-Fi" else ""
    else -> fluidTransferPhrases(item, labels).let { listOfNotNull(it.status, it.pace).joinToString(" · ") }
  }
  FluidListRow(
    title = demo.title,
    subtitle = subtitle,
    meta = if (item == null || item.state.finished || item.state == FluidTransferState.Failed) (if (demo.upload) "Carica" else "Scarica") else null,
    onClick = if (item == null || item.state.finished || item.state == FluidTransferState.Failed) onStart else null,
    disclosure = false,
    modifier = Modifier.testTag(F8p20Tags.app(demo.id)),
  )
}

private fun enqueue(context: Context, transfers: FluidTransfers, server: DemoFileServer, source: DemoSource, demo: DemoTransfer) {
  val folder = transfersFolder(context).apply { mkdirs() }
  val base = if (source == DemoSource.Gallery) "http://127.0.0.1:${server.port}" else "http://127.0.0.1:${DemoFileServer.PcPort}"
  // I comandi della coda vanno in ordine: il vecchio esce prima che entri il nuovo.
  transfers.remove(demo.id)
  val request = if (demo.upload) {
    val file = File(folder, "${demo.id}.bin")
    if (file.length() != demo.bytes) DemoFileServer.writeContent(file, demo.bytes)
    FluidTransferRequest.upload(
      url = "$base/upload/${demo.id}-${System.currentTimeMillis()}",
      source = file,
      title = demo.title,
      id = demo.id,
      subtitle = "Verso il server di prova",
      mimeType = "video/mp4",
      resumable = true,
    )
  } else {
    FluidTransferRequest.download(
      url = "$base/file/${demo.id}?bytes=${demo.bytes}",
      target = File(folder, if (demo.id == "notes-apk") "pampa-notes-2.4.apk" else "${demo.id}.bin"),
      title = demo.title,
      id = demo.id,
      subtitle = if (demo.id == "notes-apk") "Aggiornamento dal Pampa Store" else "Per usarla senza rete",
      expectedBytes = demo.bytes,
      wifiOnly = demo.wifiOnly,
    )
  }
  transfers.enqueue(request)
}

/**
 * Il server di prova della galleria, sull'indirizzo di loopback (niente rete vera): file generati di qualsiasi peso con
 * `Range` e `If-Range`, e caricamenti a pezzi con `Content-Range` (308), un po' lenti per fare in tempo a metterli in
 * pausa. Lo stesso protocollo del server del PC (`sessioni\f8-20\server.py`).
 */
internal class DemoFileServer(private val bytesPerSecond: Long = 3_000_000L) : Closeable {
  private val socket = ServerSocket(0, 16, InetAddress.getLoopbackAddress())
  private val pool = Executors.newCachedThreadPool { runnable -> Thread(runnable, "galleria-server").apply { isDaemon = true } }
  private val uploads = ConcurrentHashMap<String, Long>()

  val port: Int get() = socket.localPort

  init {
    pool.execute {
      while (!socket.isClosed) {
        val client = runCatching { socket.accept() }.getOrNull() ?: break
        pool.execute { runCatching { serve(client) } }
      }
    }
  }

  override fun close() {
    runCatching { socket.close() }
    pool.shutdownNow()
  }

  private fun serve(client: Socket) = client.use { socket ->
    val input = BufferedInputStream(socket.getInputStream())
    val output = socket.getOutputStream()
    val requestLine = readLine(input) ?: return
    val headers = HashMap<String, String>()
    while (true) {
      val line = readLine(input) ?: return
      if (line.isEmpty()) break
      val colon = line.indexOf(':')
      if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
    }
    val (method, target) = requestLine.split(' ').let { it.getOrElse(0) { "" } to it.getOrElse(1) { "/" } }
    val path = target.substringBefore('?')
    val query = target.substringAfter('?', "").split('&').mapNotNull { part ->
      part.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }.toMap()
    when {
      method == "GET" && path.startsWith("/file/") -> serveFile(path.removePrefix("/file/"), query["bytes"]?.toLongOrNull() ?: 0L, headers, output)
      method == "PUT" && path.startsWith("/upload/") -> serveUpload(path.removePrefix("/upload/"), headers, input, output)
      else -> respond(output, 404, "Not Found", emptyMap())
    }
  }

  private fun serveFile(name: String, size: Long, headers: Map<String, String>, output: OutputStream) {
    val etag = "\"$name-$size\""
    val range = headers["range"]
    val ifRange = headers["if-range"]
    val honor = range != null && range.startsWith("bytes=") && (ifRange == null || ifRange == etag)
    val start = if (honor) range!!.removePrefix("bytes=").substringBefore('-').toLongOrNull() ?: 0L else 0L
    if (honor && start >= size) {
      respond(output, 416, "Range Not Satisfiable", mapOf("Content-Range" to "bytes */$size"))
      return
    }
    val extra = mutableMapOf("ETag" to etag, "Accept-Ranges" to "bytes", "Content-Length" to "${size - start}", "Content-Type" to "application/octet-stream")
    if (honor) extra["Content-Range"] = "bytes $start-${size - 1}/$size"
    respond(output, if (honor) 206 else 200, if (honor) "Partial Content" else "OK", extra, close = false)
    val buffer = ByteArray(16 * 1024)
    var at = start
    val began = System.nanoTime()
    while (at < size) {
      val count = minOf(buffer.size.toLong(), size - at).toInt()
      for (i in 0 until count) buffer[i] = byteAt(at + i)
      output.write(buffer, 0, count)
      at += count
      // Il ritmo: non piu' di bytesPerSecond.
      val due = (at - start) * 1_000_000_000L / bytesPerSecond
      val ahead = due - (System.nanoTime() - began)
      if (ahead > 0) Thread.sleep(ahead / 1_000_000L, (ahead % 1_000_000L).toInt())
    }
    output.flush()
  }

  private fun serveUpload(id: String, headers: Map<String, String>, input: InputStream, output: OutputStream) {
    val range = headers["content-range"] ?: return respond(output, 400, "Bad Request", emptyMap())
    val length = headers["content-length"]?.toLongOrNull() ?: 0L
    val acked = uploads[id] ?: 0L
    if (range.startsWith("bytes */")) {
      skip(input, length)
      respond(output, 308, "Resume Incomplete", if (acked > 0) mapOf("Range" to "bytes=0-${acked - 1}") else emptyMap())
      return
    }
    val match = Regex("""bytes (\d+)-(\d+)/(\d+)""").find(range) ?: return respond(output, 400, "Bad Request", emptyMap())
    val (from, to, total) = match.destructured.toList().map { it.toLong() }
    if (from != acked) {
      skip(input, length)
      return respond(output, 308, "Resume Incomplete", if (acked > 0) mapOf("Range" to "bytes=0-${acked - 1}") else emptyMap())
    }
    val began = System.nanoTime()
    val got = skip(input, length, throttle = { done ->
      val due = done * 1_000_000_000L / bytesPerSecond
      val ahead = due - (System.nanoTime() - began)
      if (ahead > 0) Thread.sleep(ahead / 1_000_000L)
    })
    if (got < length) return
    uploads[id] = to + 1
    if (to + 1 >= total) respond(output, 201, "Created", emptyMap())
    else respond(output, 308, "Resume Incomplete", mapOf("Range" to "bytes=0-$to"))
  }

  private fun skip(input: InputStream, length: Long, throttle: (Long) -> Unit = {}): Long {
    val buffer = ByteArray(16 * 1024)
    var done = 0L
    while (done < length) {
      val read = input.read(buffer, 0, minOf(buffer.size.toLong(), length - done).toInt())
      if (read < 0) break
      done += read
      throttle(done)
    }
    return done
  }

  private fun respond(output: OutputStream, code: Int, reason: String, headers: Map<String, String>, close: Boolean = true) {
    val all = LinkedHashMap(headers)
    all["Connection"] = "close"
    if (close && "Content-Length" !in all) all["Content-Length"] = "0"
    val text = buildString {
      append("HTTP/1.1 $code $reason\r\n")
      all.forEach { (name, value) -> append("$name: $value\r\n") }
      append("\r\n")
    }
    output.write(text.toByteArray(Charsets.ISO_8859_1))
    output.flush()
  }

  private fun readLine(input: InputStream): String? {
    val line = StringBuilder()
    while (true) {
      val c = input.read()
      if (c < 0) return if (line.isEmpty()) null else line.toString()
      if (c == '\n'.code) return line.toString().trimEnd('\r')
      line.append(c.toChar())
    }
  }

  companion object {
    /** La porta del server del PC (`adb reverse tcp:8765 tcp:8765`). */
    const val PcPort: Int = 8765

    /** Il byte [index] dei file di prova: lo stesso sul server della galleria e su quello del PC. */
    fun byteAt(index: Long): Byte = ((index * 2654435761L) ushr 13).toByte()

    /** Scrive [size] byte del contenuto di prova in [file] (il file da caricare). */
    fun writeContent(file: File, size: Long) {
      file.parentFile?.mkdirs()
      file.outputStream().buffered().use { out ->
        var at = 0L
        val buffer = ByteArray(64 * 1024)
        while (at < size) {
          val count = minOf(buffer.size.toLong(), size - at).toInt()
          for (i in 0 until count) buffer[i] = byteAt(at + i)
          out.write(buffer, 0, count)
          at += count
        }
      }
    }
  }
}
