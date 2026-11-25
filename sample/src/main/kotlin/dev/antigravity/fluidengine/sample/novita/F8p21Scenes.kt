package dev.antigravity.fluidengine.sample.novita

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.revisions.FluidVersion
import dev.antigravity.fluidengine.revisions.FluidVersionChoice
import dev.antigravity.fluidengine.revisions.FluidVersionChoiceSheet
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.sync.FluidSync
import dev.antigravity.fluidengine.sync.InMemorySyncStore
import dev.antigravity.fluidengine.sync.MemorySyncRelay
import dev.antigravity.fluidengine.sync.SyncConflict
import dev.antigravity.fluidengine.sync.SyncResolution
import dev.antigravity.fluidengine.sync.SyncScheduler
import dev.antigravity.fluidengine.sync.SyncSealContext
import dev.antigravity.fluidengine.sync.SyncSealException
import dev.antigravity.fluidengine.sync.SyncSealedPayload
import dev.antigravity.fluidengine.sync.SyncSealer
import dev.antigravity.fluidengine.sync.SyncSpace
import dev.antigravity.fluidengine.sync.SyncSpaceId
import dev.antigravity.fluidengine.sync.SyncSpaceState
import dev.antigravity.fluidengine.sync.testing.FakeSyncRelay
import dev.antigravity.fluidengine.sync.toSyncStatus
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidSyncIndicator
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// F8.21 · Sincronizzazione: dispositivi finti nella stessa scena, ciascuno col suo registro, la sua rete e il suo
// orologio, davanti a un relay in memoria che custodisce solo byte sigillati. Niente rete vera, niente lavoro in
// sottofondo: l'invio che in un'app farebbe WorkManager qui lo fa la scena, poco dopo ogni modifica, e quando un
// dispositivo manda qualcosa «suona il campanello» agli altri (come fara' l'Account Pampa).

internal val F8p21Scenes = listOf(
  NewElementScene("sync-due-dispositivi", "Due dispositivi", "La lista della gita sul telefono e sul tablet: senza rete si scrive lo stesso, poi si allineano") {
    TwoDevicesScene(it)
  },
  NewElementScene("sync-conflitto", "Un conflitto scelto col foglio", "Lo stesso orario cambiato senza rete su due dispositivi: il foglio di vetro chiede quale tenere") {
    ConflictScene(it)
  },
  NewElementScene("sync-fermo", "Fermo per quattro mesi", "Un vecchio tablet spento torna dopo la scadenza delle pietre tombali e non perde niente") {
    SleeperScene(it)
  },
)

internal val F8p21Section = NewElementSection(
  pack = "F8.21",
  title = "Sincronizzazione",
  scenes = F8p21Scenes,
  detail = "Il registro locale, l'invio una volta sola, l'unione e la scelta tra versioni, per l'Account Pampa",
)

internal val F8p21TryItems = listOf(
  TryItem(
    "f8-21-due", TryArea.Pages, "Due dispositivi senza rete",
    "Spegni la rete del tablet, aggiungi e spunta voci su tutti e due, poi riaccendila: le due liste diventano uguali e le voci arrivate entrano sfocate.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Due dispositivi"),
  ),
  TryItem(
    "f8-21-conflitto", TryArea.Pages, "Scegli la versione",
    "Fai cambiare l'orario a tutti e due senza rete, riaccendi, apri «Due versioni» e scegline una; poi riprova con «Tieni tutte e due» e con una versione nuova a foglio aperto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Un conflitto scelto col foglio"),
  ),
  TryItem(
    "f8-21-fermo", TryArea.Pages, "Quattro mesi spento",
    "Spegni il vecchio tablet, scrivi su di lui e sugli altri, fai passare quattro mesi e riaccendilo: rilegge tutto e niente si perde.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Fermo per quattro mesi"),
  ),
)

/** I tag dei test. */
internal object F8p21Tags {
  fun panel(device: String) = "sync:$device"
  fun add(device: String) = "sync:$device:aggiungi"
  fun network(device: String) = "sync:$device:rete"
  fun choose(device: String) = "sync:$device:scegli"
  fun item(device: String, title: String) = "sync:$device:voce:$title"
  const val Relay = "sync:relay"
  const val Step = "sync:passo"
}

// --- Il mondo finto --------------------------------------------------------------------------------------------------

/**
 * Il sigillo della scena: AES-256-GCM con i dati associati del contesto, e una chiave fissa di prova. Nell'app vera lo
 * mette l'Account Pampa con la chiave dell'app; qui serve a mostrare che il relay tiene solo byte illeggibili.
 */
private class DemoSealer : SyncSealer {
  private val key = SecretKeySpec(ByteArray(32) { (it * 13 + 5).toByte() }, "AES")
  private val random = SecureRandom()

  override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload {
    val iv = ByteArray(12).also(random::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
    cipher.updateAAD(context.associatedData())
    return SyncSealedPayload(iv + cipher.doFinal(plaintext), keyVersion = 1)
  }

  override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray {
    val bytes = payload.bytes
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12))
    cipher.updateAAD(context.associatedData())
    return runCatching { cipher.doFinal(bytes, 12, bytes.size - 12) }.getOrElse { throw SyncSealException("sigillo", it) }
  }
}

/** Un dispositivo finto: il suo motore, la sua rete e lo spazio della scena. */
internal class DemoDevice(val node: String, val label: String, val sync: FluidSync, val net: FakeSyncRelay) {
  val space: SyncSpace = sync.space(DemoSpace)

  private var connected by mutableStateOf(true)

  /** La rete del dispositivo: l'interruttore della testata la legge, le scene la cambiano. */
  var online: Boolean
    get() = connected
    set(value) {
      connected = value
      net.online = value
    }
}

private val DemoSpace = SyncSpaceId("gita")

/**
 * Il mondo della scena: l'ora (che si puo' far saltare avanti di mesi), il relay e i dispositivi. Ogni modifica chiede un
 * invio dopo [Debounce]; quando un dispositivo manda qualcosa, gli altri in rete sincronizzano (il campanello).
 */
internal class DemoWorld(private val scope: CoroutineScope) {
  @Volatile var jump: Long = 0
  fun now(): Long = System.currentTimeMillis() + jump

  val relay = MemorySyncRelay(now = ::now)
  private val sealer = DemoSealer()
  val devices = ArrayList<DemoDevice>()
  private val pending = HashMap<String, Job>()

  fun device(node: String, label: String): DemoDevice {
    val net = FakeSyncRelay(relay)
    lateinit var device: DemoDevice
    val sync = FluidSync(
      store = InMemorySyncStore(),
      relay = net,
      sealer = sealer,
      node = node,
      wall = ::now,
      dispatcher = Dispatchers.Default,
      scheduler = SyncScheduler { request(device) },
    )
    device = DemoDevice(node, label, sync, net)
    devices += device
    return device
  }

  /**
   * Un invio fra poco, come il lavoro unico di WorkManager: piu' richieste ravvicinate diventano un invio solo. Anche senza
   * rete si tenta: fallisce subito e lo stato dice «offline» con le modifiche in attesa (in un'app lo dira' l'osservatore
   * della rete di F8.20).
   */
  fun request(device: DemoDevice, after: Long = Debounce) {
    scope.launch {
      synchronized(pending) { pending[device.node]?.cancel() }
      val job = scope.launch {
        delay(after)
        syncNow(device)
      }
      synchronized(pending) { pending[device.node] = job }
    }
  }

  suspend fun syncNow(device: DemoDevice) {
    val report = device.space.sync()
    if (report.pushed > 0) devices.filter { it !== device && it.online }.forEach { request(it, Bell) }
  }

  fun label(node: String, viewer: DemoDevice): String =
    if (node == viewer.node) "Questo ${viewer.label.lowercase()}" else devices.firstOrNull { it.node == node }?.label ?: node

  companion object {
    /** L'attesa dell'invio dopo una modifica: piu' corta di quella di WorkManager, per vederlo partire. */
    const val Debounce = 700L

    /** Il campanello: quanto dopo un invio gli altri dispositivi sincronizzano. */
    const val Bell = 350L
  }
}

private val TripItems = listOf(
  "Panini", "Borraccia", "Cappello", "Crema solare", "Biglietto del treno", "Ombrello", "Macchina fotografica", "Merenda",
  "Felpa", "Cerotti",
)

// --- I fondi -----------------------------------------------------------------------------------------------------------

private enum class SyncBackdrop(val label: String) { Page("Pagina"), Light("Foto chiara"), Dark("Foto scura") }

/** I pannelli dei dispositivi su un fondo scelto: la pagina, o una foto con i pannelli di vetro sopra. */
@Composable
private fun DevicesOn(ground: SyncBackdrop, content: @Composable (Modifier) -> Unit) {
  if (ground == SyncBackdrop.Page) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content(Modifier) }
    return
  }
  val photo = rememberGlassBackdrop()
  val shape = ContinuousCornerShape(FluidRadius.Group)
  Box(Modifier.fillMaxWidth().clip(shape)) {
    Image(
      painter = painterResource(if (ground == SyncBackdrop.Light) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.matchParentSize().glassBackdropSource(photo),
    )
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      content(Modifier.fluidGlassPanel(photo, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Card), GlassRole.Modal))
    }
  }
}

@Composable
private fun BackdropPicker(ground: SyncBackdrop, onSelect: (SyncBackdrop) -> Unit) {
  FluidSegmentedControl(
    options = SyncBackdrop.entries,
    selected = ground,
    onSelect = onSelect,
    modifier = Modifier.fillMaxWidth(),
    label = { it.label },
  )
}

// --- Il pannello di un dispositivo -------------------------------------------------------------------------------------

/** La testata: il nome, lo stato della sincronizzazione e l'interruttore della rete. */
@Composable
private fun DeviceHeader(device: DemoDevice, world: DemoWorld, state: SyncSpaceState) {
  val overview by device.sync.overview.collectAsState()
  val online = device.online
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Column(Modifier.weight(1f)) {
      Text(device.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
      Text(
        text = when {
          !online && state.pending > 0 -> "Senza rete · ${pendingText(state.pending)}"
          !online -> "Senza rete"
          state.pending > 0 -> pendingText(state.pending)
          state.lastSuccessMillis != null -> "Aggiornato"
          else -> "Mai sincronizzato"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FluidSyncIndicator(status = overview.toSyncStatus())
    FluidSwitch(
      checked = online,
      onCheckedChange = { on ->
        device.online = on
        world.request(device, 0)
      },
      modifier = Modifier.testTag(F8p21Tags.network(device.node)),
    )
  }
}

private fun pendingText(n: Int) = if (n == 1) "1 modifica in attesa" else "$n modifiche in attesa"

/**
 * Una voce che arriva da un altro dispositivo entra sfocata e si mette a fuoco (la cambia l'app, non il dito); quella
 * scritta qui col dito compare nitida. Con «Riduci movimento», nitida subito.
 */
@Composable
private fun Modifier.arrival(fromElsewhere: Boolean): Modifier {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val blur = remember { Animatable(if (fromElsewhere && !reduced && Build.VERSION.SDK_INT >= 31) 1f else 0f) }
  LaunchedEffect(Unit) { if (blur.value > 0f) blur.animateTo(0f, dev.antigravity.fluidengine.ui.fluid.FluidMotion.standard()) }
  return graphicsLayer {
    val v = blur.value
    if (v > 0f) {
      val r = 10.dp.toPx() * v
      renderEffect = BlurEffect(r, r)
      alpha = 1f - 0.6f * v
    } else {
      renderEffect = null
      alpha = 1f
    }
  }
}

/** La lista della gita di un dispositivo: spunta col dito, «Aggiungi», e le voci arrivate dagli altri. */
@Composable
private fun TripList(device: DemoDevice, world: DemoWorld, modifier: Modifier, initialIds: Set<String>) {
  val state by device.space.state.collectAsState()
  val scope = rememberCoroutineScope()
  Column(
    modifier.fillMaxWidth().padding(14.dp).testTag(F8p21Tags.panel(device.node)),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DeviceHeader(device, world, state)
    val items = state.records.entries.sortedBy { it.value.integer("ordine") ?: 0L }
    if (items.isNotEmpty()) {
      FluidListGroup {
        items.forEachIndexed { index, (id, doc) ->
          if (index > 0) FluidListDivider()
          val title = doc.text("voce").orEmpty()
          val taken = doc.bool("presa") == true
          val lastNode = doc.fields["presa"]?.stamp?.takeIf { it > (doc.fields["voce"]?.stamp ?: it) }?.node
            ?: doc.fields["voce"]?.stamp?.node.orEmpty()
          androidx.compose.runtime.key(id) {
            val arrived = remember { id !in initialIds && lastNode != device.node }
            FluidListRow(
              title = title,
              subtitle = if (taken) "Presa · ${world.label(lastNode, device)}" else world.label(lastNode, device),
              leading = { FluidSelectionMark(selected = taken, contentDescription = null) },
              leadingTile = false,
              disclosure = false,
              selected = taken,
              modifier = Modifier.arrival(arrived).testTag(F8p21Tags.item(device.node, title)),
              onClick = { scope.launch { device.space.edit(id) { bool("presa", !taken) } } },
            )
          }
        }
      }
    }
    FluidButton(
      text = "Aggiungi",
      size = FluidButtonSize.Small,
      style = FluidButtonStyle.Tinted,
      modifier = Modifier.testTag(F8p21Tags.add(device.node)),
      onClick = {
        scope.launch {
          val used = state.records.values.mapNotNull { it.text("voce") }.toSet()
          val next = TripItems.firstOrNull { it !in used } ?: "Voce ${state.records.size + 1}"
          device.space.create { text("voce", next); bool("presa", false); integer("ordine", world.now()) }
        }
      },
    )
  }
}

/** Cosa custodisce il relay: quante righe e quanti byte, e l'inizio di una riga. Illeggibile, ed e' giusto cosi'. */
@Composable
private fun RelayRow(world: DemoWorld, vararg watch: Any?) {
  val rows = remember(*watch) { world.relay.rows(DemoSpace) }
  val bytes = rows.sumOf { it.payload?.size ?: 0 }
  val sample = rows.lastOrNull()?.payload?.bytes?.take(12)?.joinToString(" ") { "%02x".format(it) }
  FluidListGroup(modifier = Modifier.testTag(F8p21Tags.Relay)) {
    FluidListRow(
      title = "Il relay custodisce ${rows.size} ${if (rows.size == 1) "riga" else "righe"} · $bytes byte sigillati",
      subtitle = sample?.let { "Ultima: $it …  (non sa leggerle)" } ?: "Ancora niente",
      disclosure = false,
    )
  }
}

// --- Scena 1: due dispositivi ------------------------------------------------------------------------------------------

@Composable
private fun TwoDevicesScene(onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val world = remember { DemoWorld(scope) }
  val phone = remember { world.device("telefono", "Telefono") }
  val tablet = remember { world.device("tablet", "Tablet") }
  var ground by rememberSaveable { mutableStateOf(SyncBackdrop.Page) }
  val initial = remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(Unit) {
    // Il punto di partenza: tre voci scritte sul telefono e gia' arrivate al tablet.
    listOf("Panini", "Borraccia", "Cappello").forEachIndexed { i, item ->
      phone.space.create { text("voce", item); bool("presa", i == 1); integer("ordine", i.toLong()) }
    }
    world.syncNow(phone)
    world.syncNow(tablet)
    initial.value = phone.space.state.value.records.keys
  }
  val phoneState by phone.space.state.collectAsState()
  val tabletState by tablet.space.state.collectAsState()
  FluidScreen(title = "Due dispositivi", subtitle = "F8.21 · Sincronizzazione", onBack = onBack) {
    item(key = "fondo") { BackdropPicker(ground) { ground = it } }
    item(key = "dispositivi") {
      DevicesOn(ground) { glass ->
        TripList(phone, world, glass, initial.value)
        TripList(tablet, world, glass, initial.value)
      }
    }
    item(key = "relay") { RelayRow(world, phoneState, tabletState) }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Ogni modifica va subito nel registro del dispositivo e si vede, anche senza rete. Poi parte una volta " +
          "sola, sigillata: il relay tiene solo byte che non sa leggere. Per un campo vince l'ultimo, con l'orologio " +
          "logico e non con l'ora del telefono.",
      )
    }
  }
}

// --- Scena 2: il conflitto ---------------------------------------------------------------------------------------------

private const val NoteId = "gita-firenze"
private const val NoteField = "testo"
private const val NoteStart = "Ritrovo alle 8 davanti a scuola.\nPranzo al sacco ai giardini di Boboli.\n"

/** Il confronto da mostrare a [viewer] per un conflitto del motore: i nomi dei dispositivi li sa la scena (l'app). */
private fun SyncConflict.toChoice(world: DemoWorld, viewer: DemoDevice): FluidVersionChoice = FluidVersionChoice(
  title = "Gita a Firenze",
  key = "$recordId/$field",
  versions = versions.map { v ->
    FluidVersion(
      id = v.revision,
      source = world.label(v.node, viewer),
      timeMillis = v.millis,
      text = v.text.orEmpty(),
      current = v.current,
    )
  },
)

@Composable
private fun ConflictScene(onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val world = remember { DemoWorld(scope) }
  val phone = remember { world.device("telefono", "Telefono") }
  val tablet = remember { world.device("tablet", "Tablet") }
  // Il terzo dispositivo non si vede: scrive solo per far arrivare una versione nuova a foglio aperto.
  val computer = remember { world.device("computer", "Computer") }
  val namespace = rememberFluidGlassNamespace()
  var ground by rememberSaveable { mutableStateOf(SyncBackdrop.Page) }
  var openFor by remember { mutableStateOf<DemoDevice?>(null) }
  var step by remember { mutableStateOf(0) }

  suspend fun reset() {
    phone.space.edit(NoteId) { text("titolo", "Gita a Firenze"); longText(NoteField, NoteStart) }
    world.devices.forEach { world.syncNow(it) }
  }
  LaunchedEffect(Unit) { reset() }

  val phoneState by phone.space.state.collectAsState()
  val tabletState by tablet.space.state.collectAsState()
  val viewer = openFor
  val conflict = when (viewer) {
    phone -> phoneState.conflicts.firstOrNull()
    tablet -> tabletState.conflicts.firstOrNull()
    else -> null
  }
  val choice = if (viewer != null) conflict?.toChoice(world, viewer) else null

  FluidScreen(title = "Un conflitto", subtitle = "F8.21 · Scelta tra versioni", onBack = onBack) {
    item(key = "fondo") { BackdropPicker(ground) { ground = it } }
    item(key = "passi") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(F8p21Tags.Step)) {
        FluidButton(
          text = "1 · Senza rete, cambiano tutti e due l'orario",
          size = FluidButtonSize.Small,
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
          enabled = step == 0,
          onClick = {
            step = 1
            scope.launch {
              phone.online = false
              tablet.online = false
              phone.space.edit(NoteId) { longText(NoteField, NoteStart.replace("alle 8 davanti a scuola", "alle 7:45 in stazione")) }
              delay(400)
              tablet.space.edit(NoteId) { longText(NoteField, NoteStart.replace("alle 8 davanti a scuola", "alle 8:30 al parcheggio")) }
            }
          },
        )
        FluidButton(
          text = "2 · Torna la rete",
          size = FluidButtonSize.Small,
          style = FluidButtonStyle.Tinted,
          fillWidth = true,
          enabled = step == 1,
          onClick = {
            step = 2
            scope.launch {
              phone.online = true
              tablet.online = true
              world.syncNow(phone)
              world.syncNow(tablet)
              world.syncNow(phone)
            }
          },
        )
        // A foglio aperto la pagina sotto non si tocca: la versione nuova arriva da sola, quattro secondi dopo.
        FluidButton(
          text = "Fra 4 secondi arriva una versione dal computer",
          size = FluidButtonSize.Small,
          style = FluidButtonStyle.Plain,
          fillWidth = true,
          enabled = step == 2,
          onClick = {
            scope.launch {
              delay(4_000)
              world.syncNow(computer)
              computer.space.edit(NoteId) { longText(NoteField, NoteStart.replace("alle 8 davanti a scuola", "alle 8 in piazza San Marco")) }
              world.syncNow(computer)
            }
          },
        )
        FluidButton(
          text = "Ricomincia",
          size = FluidButtonSize.Small,
          style = FluidButtonStyle.Plain,
          fillWidth = true,
          onClick = {
            openFor = null
            step = 0
            scope.launch { reset() }
          },
        )
      }
    }
    item(key = "dispositivi") {
      DevicesOn(ground) { glass ->
        for ((device, state) in listOf(phone to phoneState, tablet to tabletState)) {
          NotePanel(device, world, state, glass, namespace) { openFor = device }
        }
      }
    }
    item(key = "relay") { RelayRow(world, phoneState, tabletState) }
  }

  FluidVersionChoiceSheet(
    choice = choice,
    source = viewer?.let { FluidTransitionSource(F8p21Tags.choose(it.node), namespace) },
    onDismissRequest = { openFor = null },
    onDecision = { decision ->
      val device = viewer ?: return@FluidVersionChoiceSheet
      val current = conflict ?: return@FluidVersionChoiceSheet
      scope.launch {
        when (device.space.resolve(current.recordId, current.field, decision.keep, decision.shown)) {
          is SyncResolution.Resolved -> openFor = null
          // Nel frattempo e' arrivata una revisione: il foglio si e' gia' aggiornato da solo.
          is SyncResolution.Stale, SyncResolution.NotFound -> Unit
        }
      }
    },
  )
}

@Composable
private fun NotePanel(
  device: DemoDevice,
  world: DemoWorld,
  state: SyncSpaceState,
  modifier: Modifier,
  namespace: dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace,
  onChoose: () -> Unit,
) {
  val note = state.records[NoteId]
  val copies = state.records.filterKeys { it != NoteId }
  Column(
    modifier.fillMaxWidth().padding(14.dp).testTag(F8p21Tags.panel(device.node)),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    DeviceHeader(device, world, state)
    Text(note?.text("titolo") ?: "Gita a Firenze", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition(
      targetState = note?.text(NoteField).orEmpty(),
      blurReference = 22.dp,
      contentAlignment = Alignment.TopStart,
      modifier = Modifier.fillMaxWidth(),
    ) { body ->
      Text(body.trimEnd(), style = MaterialTheme.typography.bodyMedium)
    }
    for ((_, copy) in copies) {
      Text("Copia: " + copy.text(NoteField).orEmpty().lineSequence().first(), style = MaterialTheme.typography.bodySmall)
    }
    if (state.conflicts.isNotEmpty()) {
      FluidButton(
        text = "${state.conflicts.first().versions.size} versioni · Scegli",
        size = FluidButtonSize.Small,
        style = FluidButtonStyle.Filled,
        onClick = onChoose,
        modifier = Modifier
          .fluidTransitionSource(F8p21Tags.choose(device.node), namespace, dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape)
          .testTag(F8p21Tags.choose(device.node)),
      )
    }
  }
}

// --- Scena 3: fermo per mesi -------------------------------------------------------------------------------------------

@Composable
private fun SleeperScene(onBack: () -> Unit) {
  val scope = rememberCoroutineScope()
  val world = remember { DemoWorld(scope) }
  val phone = remember { world.device("telefono", "Telefono") }
  val old = remember { world.device("vecchio", "Vecchio tablet") }
  var ground by rememberSaveable { mutableStateOf(SyncBackdrop.Page) }
  var step by remember { mutableStateOf(0) }
  var outcome by remember { mutableStateOf<String?>(null) }
  val initial = remember { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(Unit) {
    listOf("Panini", "Borraccia", "Cappello", "Crema solare").forEachIndexed { i, item ->
      phone.space.create { text("voce", item); bool("presa", false); integer("ordine", i.toLong()) }
    }
    world.syncNow(phone)
    world.syncNow(old)
    initial.value = phone.space.state.value.records.keys
  }
  val phoneState by phone.space.state.collectAsState()
  val oldState by old.space.state.collectAsState()
  DisposableEffect(Unit) { onDispose { world.jump = 0 } }

  FluidScreen(title = "Fermo per quattro mesi", subtitle = "F8.21 · Pietre tombali e ripresa", onBack = onBack) {
    item(key = "fondo") { BackdropPicker(ground) { ground = it } }
    item(key = "passi") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag(F8p21Tags.Step)) {
        FluidButton(
          text = "1 · Il vecchio tablet si spegne con due modifiche",
          size = FluidButtonSize.Small, style = FluidButtonStyle.Tinted, fillWidth = true, enabled = step == 0,
          onClick = {
            step = 1
            scope.launch {
              old.online = false
              old.space.create { text("voce", "Pile per la torcia"); bool("presa", false); integer("ordine", 10) }
              old.space.state.value.records.entries.firstOrNull { it.value.text("voce") == "Panini" }?.key
                ?.let { panini -> old.space.edit(panini) { bool("presa", true) } }
            }
          },
        )
        FluidButton(
          text = "2 · Sul telefono: via la crema, arriva l'ombrello",
          size = FluidButtonSize.Small, style = FluidButtonStyle.Tinted, fillWidth = true, enabled = step == 1,
          onClick = {
            step = 2
            scope.launch {
              phone.space.state.value.records.entries.firstOrNull { it.value.text("voce") == "Crema solare" }?.key
                ?.let { cream -> phone.space.delete(cream) }
              phone.space.create { text("voce", "Ombrello"); bool("presa", false); integer("ordine", 11) }
              world.syncNow(phone)
            }
          },
        )
        FluidButton(
          text = "3 · Passano quattro mesi",
          size = FluidButtonSize.Small, style = FluidButtonStyle.Tinted, fillWidth = true, enabled = step == 2,
          onClick = {
            step = 3
            world.jump += 120L * 24 * 60 * 60 * 1000
            val purged = world.relay.purge()
            outcome = "Il relay ha tolto $purged ${if (purged == 1) "pietra tombale scaduta" else "pietre tombali scadute"}."
          },
        )
        FluidButton(
          text = "4 · Il vecchio tablet si riaccende",
          size = FluidButtonSize.Small, style = FluidButtonStyle.Tinted, fillWidth = true, enabled = step == 3,
          onClick = {
            step = 4
            scope.launch {
              old.online = true
              val report = old.space.sync()
              world.syncNow(phone)
              outcome = (if (report.fullResync) "Ha riletto tutto da capo" else "Si e' rimesso in pari") +
                ": ${report.pushed} sue modifiche mandate, ${report.pulled} arrivate, " +
                "${report.supersededByDelete} superate da una cancellazione."
            }
          },
        )
        outcome?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
    }
    item(key = "dispositivi") {
      DevicesOn(ground) { glass ->
        TripList(phone, world, glass, initial.value)
        TripList(old, world, glass, initial.value)
      }
    }
    item(key = "relay") { RelayRow(world, phoneState, oldState) }
    item(key = "spazio") { Spacer(Modifier.padding(8.dp)) }
  }
}
