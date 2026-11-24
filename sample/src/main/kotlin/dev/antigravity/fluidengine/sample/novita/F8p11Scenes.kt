package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineManifestParser
import dev.antigravity.fluidengine.foundation.FluidFirstUseKeys
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus
import dev.antigravity.fluidengine.foundation.FluidWelcomeKind
import dev.antigravity.fluidengine.foundation.releaseNotesFor
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidLiveBackground
import dev.antigravity.fluidengine.ui.fluid.FluidLiveBackgroundCanvas
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidPermission
import dev.antigravity.fluidengine.ui.fluid.FluidPermissionState
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonList
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeContent
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeFeature
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomePage
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeSheet
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.fluidPermissionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidLiveBackgroundState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPermission
import dev.antigravity.fluidengine.ui.fluid.rememberFluidWelcomeState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.toWelcomeFeature
import dev.antigravity.fluidengine.ui.theme.FluidContentState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// F8.11 · Prima volta, permessi e avvio: il benvenuto e le novità (`benvenuto-e-novita`), il permesso chiesto dal tasto
// (`permesso-nel-punto-giusto`), gli sfondi vivi (`sfondi-vivi`) e il primo fotogramma pronto (`apertura-dall-icona`,
// rivista), nei casi delle app di Alessio: Note, MyCloud Gallery, Pampa Mail, Fluid Transit, ClasseViva.

/** Le scene di F8.11, nell'ordine delle idee. */
internal val F8p11Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "benvenuto", "Benvenuto in Note",
    "Il primo avvio: titolo grande, quattro righe che entrano una dopo l'altra, la nota sulla privacy, «Continua» fermo in basso",
  ) { onBack -> WelcomeScene(onBack) },
  NewElementScene(
    "novita", "Novità di una versione",
    "Dopo un aggiornamento le novità dal manifest dello store, una volta sola; mai da un link, mai sopra un'azione in corso",
  ) { onBack -> NewsScene(onBack) },
  NewElementScene(
    "benvenuto-passi", "Tre passi coi pallini",
    "Il primo avvio in una pagina intera: l'aurora dietro, i pallini sopra il tasto, un passo che chiede le notifiche",
  ) { onBack -> StepsScene(onBack) },
  NewElementScene(
    "benvenuto-testo-grande", "Col testo grande",
    "Al 200% il tasto resta fermo in basso, le righe scorrono e la nota sulla privacy passa nel corpo",
  ) { onBack -> BigTextScene(onBack) },
  NewElementScene(
    "permessi", "Permesso nel punto giusto",
    "Dal tasto nasce la spiegazione, poi il dialogo di Android; le foto «solo alcune», il negato per sempre e «Apri Impostazioni»",
  ) { onBack -> PermissionScene(onBack) },
  NewElementScene(
    "sfondi-vivi", "Sfondi vivi",
    "Aurora, onde e stelle lentissime sotto la barra e il vetro della pagina; ferme col movimento ridotto",
  ) { onBack -> LiveScene(onBack) },
  NewElementScene(
    "traguardo", "Un traguardo",
    "Una pagina giocosa: lo sfondo vivo sotto un pannello di vetro, col ritmo da 30 a 120 Hz per misurarne il costo",
  ) { onBack -> MilestoneScene(onBack) },
  NewElementScene(
    "avvio-pronto", "Il primo fotogramma pronto",
    "La pagina vera subito: la barra, il titolo e gli scheletri, poi il contenuto che entra a fuoco quando arriva",
  ) { onBack -> ReadyScene(onBack) },
)

/** I tag delle prove. */
internal object F8p11Tags {
  const val Status = "f8p11:stato"
  const val Again = "f8p11:di-nuovo"
  const val Update = "f8p11:aggiorna"
  const val Reopen = "f8p11:riapri"
  const val FromLink = "f8p11:da-link"
  const val Busy = "f8p11:occupato"
  const val Photos = "f8p11:foto"
  const val PhotosMore = "f8p11:altre-foto"
  const val Notifications = "f8p11:notifiche"
  const val Location = "f8p11:posizione"
  const val Forget = "f8p11:dimentica"
  const val Style = "f8p11:sfondo"
  const val Rate = "f8p11:ritmo"
  const val Reduced = "f8p11:ridotto"
  const val Replay = "f8p11:riapri-avvio"

  fun permissionStatus(id: String) = "f8p11:permesso:$id"
}

internal val F8p11TryItems = listOf(
  TryItem("f8-11-benvenuto", TryArea.NavigationAlerts, "Benvenuto in Note",
    "Guarda le righe entrare una dopo l'altra e il «Continua» fermo; poi chiudi e riapri la scena: non torna.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Benvenuto in Note")),
  TryItem("f8-11-novita", TryArea.NavigationAlerts, "Le novità una volta sola",
    "Aggiorna alla versione nuova, chiudi e riapri l'app: niente; poi prova un aggiornamento aperto da un link e uno con un'azione in corso.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Novità di una versione")),
  TryItem("f8-11-passi", TryArea.NavigationAlerts, "Tre passi coi pallini",
    "Scorri i passi col dito e col tasto; al secondo attiva le notifiche dal tasto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Tre passi coi pallini")),
  TryItem("f8-11-permessi", TryArea.NavigationAlerts, "Il permesso dal tasto",
    "Tocca «Scegli le foto»: la spiegazione nasce dal tasto; scegli «Solo alcune»; nega due volte la posizione e apri le Impostazioni.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Permesso nel punto giusto")),
  TryItem("f8-11-sfondi", TryArea.NavigationAlerts, "Sfondi vivi sotto il vetro",
    "Guarda l'aurora, le onde e le stelle sotto la barra; accendi il movimento ridotto: si fermano su una posa leggibile.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Sfondi vivi")),
  TryItem("f8-11-avvio", TryArea.NavigationAlerts, "Il primo fotogramma pronto",
    "Chiudi Fluid Glass e riaprila dall'icona, in chiaro e in scuro: dopo la schermata di Android la pagina c'è subito, senza lampi.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il primo fotogramma pronto")),
)

// --- Il benvenuto ----------------------------------------------------------------------------------------------------

private val NoteFeatures = listOf(
  FluidWelcomeFeature(FluidIcons.Edit, "Scrivi a modo tuo", "Elenchi, titoli e citazioni, con la barra sopra la tastiera."),
  FluidWelcomeFeature(FluidIcons.Folder, "Tutto in ordine", "Quaderni e cartelle, anche una dentro l'altra."),
  FluidWelcomeFeature(FluidIcons.Search, "Trova al volo", "Cerca nelle note mentre scrivi, anche nel testo delle foto."),
  FluidWelcomeFeature(FluidIcons.Lock, "Note riservate", "Chiudi le note private con l'impronta."),
)

private const val NotePrivacy = "Le tue note restano sul telefono: Note non le manda a nessuno."

/** Il primo avvio di Note: il foglio si apre da solo sopra la pagina, una volta per versione. */
@Composable
private fun WelcomeScene(onBack: () -> Unit) {
  val labels = LocalFluidFirstUseLabels.current
  val welcome = rememberFluidWelcomeState(versionName = "1.0.0", freshInstall = true, scope = "galleria-benvenuto")
  val pages = remember { listOf(FluidWelcomePage(labels.welcomeTitle("Note"), NoteFeatures, icon = FluidIcons.Edit)) }
  FluidScreen(title = "Note", subtitle = "F8.11 · benvenuto", onBack = onBack) {
    item(key = "note") { FakeNotes() }
    item(key = "stato") {
      StatusLine(
        when {
          welcome.isShowing -> "Il benvenuto è aperto"
          welcome.isDecided && welcome.kind == null -> "Già visto per la versione 1.0.0"
          welcome.isDecided -> "Visto: la versione 1.0.0 è segnata"
          else -> "Leggo la memoria…"
        },
      )
    }
    item(key = "di-nuovo") {
      FluidButton(
        text = "Rivedi il benvenuto",
        onClick = welcome::forget,
        style = FluidButtonStyle.Tinted,
        fillWidth = true,
        modifier = Modifier.testTag(F8p11Tags.Again),
      )
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Il foglio compare qualche fotogramma dopo la pagina (la memoria si legge dopo il primo disegno) e la versione " +
          "si segna come vista quando compare. Tirato giù o con «Continua», non torna fino alla prossima versione.",
      )
    }
  }
  FluidWelcomeSheet(
    visible = welcome.isShowing,
    onFinish = welcome::dismiss,
    pages = pages,
    privacyNote = NotePrivacy,
    onPrivacyClick = {},
  )
}

/** Un manifest dello store finto, con una versione dalle novità scritte apposta e una dal solo changelog. */
private val FakeManifest = """
  {"app": {
    "stable": {"version": "2.1.0", "changelog": "Note fissate, cerca nelle foto",
      "highlights": [
        {"title": "Note fissate", "text": "Le note importanti restano in cima al quaderno.", "symbol": "star"},
        {"title": "Cerca nelle foto", "text": "Trova le parole scritte nelle foto e nelle scansioni.", "symbol": "search"},
        {"title": "Quaderni condivisi", "text": "Invita chi vuoi e scrivete insieme.", "symbol": "people"}
      ]},
    "beta": {"version": "2.2.0", "changelog": "- Promemoria: una nota può ricordarti di sé a un'ora o in un posto\n- Più veloce: i quaderni grandi si aprono subito"}
  }}
""".trimIndent()

private val Versions = listOf("2.0.0", "2.1.0", "2.2.0", "2.2.1")

/** Le novità dopo un aggiornamento, dal manifest, una volta sola; mai da un link, mai sopra un'azione in corso. */
@Composable
private fun NewsScene(onBack: () -> Unit) {
  val labels = LocalFluidFirstUseLabels.current
  val memory = LocalFluidFirstUseMemory.current
  val coroutines = rememberCoroutineScope()
  var installed by rememberSaveable { mutableIntStateOf(0) }
  var launches by rememberSaveable { mutableIntStateOf(0) }
  var fromLink by rememberSaveable { mutableStateOf(false) }
  var busy by rememberSaveable { mutableStateOf(false) }
  val version = Versions[installed]
  val manifest = remember { EngineManifestParser.parse(FakeManifest) }
  val notes = remember(version) { manifest.releaseNotesFor(version) }
  // Ogni «riapri» è un'apertura nuova dell'app: uno stato nuovo, la stessa memoria.
  key(launches, version) {
    val welcome = rememberFluidWelcomeState(
      versionName = version, hasNews = notes.isNotEmpty(), suppressed = fromLink, busy = busy, freshInstall = false,
      scope = "galleria-novita",
    )
    val features = remember(notes) { notes.map { it.toWelcomeFeature() } }
    FluidScreen(title = "Note", subtitle = "F8.11 · novita · versione $version", onBack = onBack) {
      item(key = "stato") {
        StatusLine(
          "Versione $version · " + when {
            welcome.isShowing -> "novità aperte"
            welcome.isDecided && welcome.kind == FluidWelcomeKind.WhatsNew -> "novità viste"
            welcome.isDecided -> if (notes.isEmpty()) "nessuna novità da mostrare" else "novità già viste"
            else -> "in attesa"
          } + (if (fromLink) " · aperta da un link" else "") + (if (busy) " · azione in corso" else ""),
        )
      }
      item(key = "azioni") {
        FluidListGroup {
          FluidListRow(
            title = if (installed < Versions.lastIndex) "Aggiorna a ${Versions[installed + 1]}" else "Ricomincia da 2.0.0",
            subtitle = when (installed) {
              0 -> "Le novità scritte apposta nel manifest (highlights)"
              1 -> "Le novità prese dal changelog, una riga per voce"
              2 -> "Un aggiornamento senza novità: niente foglio"
              else -> "Cancella la memoria della scena"
            },
            modifier = Modifier.testTag(F8p11Tags.Update),
            onClick = {
              if (installed < Versions.lastIndex) {
                installed++
              } else coroutines.launch {
                memory.write(FluidFirstUseKeys.welcomeSeen("galleria-novita"), null)
                installed = 0
                launches++
              }
            },
          )
          FluidListDivider()
          FluidListRow(
            title = "Riapri l'app", subtitle = "La stessa versione: le novità non tornano",
            modifier = Modifier.testTag(F8p11Tags.Reopen), onClick = { launches++ },
          )
        }
      }
      item(key = "casi") {
        FluidListGroup {
          FluidToggleRow(
            title = "Aperta da un link", subtitle = "Come una notifica o un indirizzo di F8.12: il foglio non compare",
            checked = fromLink, onCheckedChange = { fromLink = it }, modifier = Modifier.testTag(F8p11Tags.FromLink),
          )
          FluidListDivider()
          FluidToggleRow(
            title = "Azione in corso", subtitle = "Il foglio aspetta che finisca",
            checked = busy, onCheckedChange = { busy = it }, modifier = Modifier.testTag(F8p11Tags.Busy),
          )
        }
      }
      item(key = "nota") {
        FluidSectionFootnote(
          text = "Le novità vengono dal manifest dello store (engine-config, releaseNotes), la versione vista dalla memoria " +
            "della prima volta (engine-storage). Nella galleria la memoria è quella vera: resta anche chiudendo l'app.",
        )
      }
    }
    FluidWelcomeSheet(
      visible = welcome.isShowing,
      onFinish = welcome::dismiss,
      title = labels.whatsNewTitle("Note"),
      features = features,
    )
  }
}

/** Il primo avvio come pagina intera: l'aurora dietro, tre passi coi pallini, il secondo chiede le notifiche. */
@Composable
private fun StepsScene(onBack: () -> Unit) {
  val labels = LocalFluidFirstUseLabels.current
  val pages = remember {
    listOf(
      FluidWelcomePage(labels.welcomeTitle("Note"), NoteFeatures.take(3), icon = FluidIcons.Edit),
      // Come il secondo passo di Password in iOS 18, «Turn On Notifications»: l'icona, il titolo, una frase e il tasto.
      FluidWelcomePage(
        title = "Attiva le notifiche",
        message = "Note ti avvisa quando qualcuno cambia un quaderno che condividi, e solo allora.",
        content = { NotificationStep() },
        icon = FluidIcons.Notifications,
      ),
      FluidWelcomePage(
        title = "Tutto pronto",
        features = listOf(FluidWelcomeFeature(FluidIcons.Star, "Inizia da una nota", "Tocca il tasto in basso per scrivere la prima.")),
        actionLabel = "Inizia",
        icon = FluidIcons.Star,
      ),
    )
  }
  Box(Modifier.fillMaxSize()) {
    FluidLiveBackgroundCanvas(FluidLiveBackground.Aurora, Modifier.fillMaxSize())
    FluidWelcomeContent(
      pages = pages,
      onFinish = onBack,
      privacyNote = NotePrivacy,
      modifier = Modifier.fillMaxSize().systemBarsPadding(),
    )
  }
}

@Composable
private fun ColumnScope.NotificationStep() {
  val notifications = rememberFluidPermission(
    FluidPermission.Notifications,
    reason = "Note ti avvisa quando qualcuno cambia un quaderno che condividi, e solo allora.",
  )
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    FluidButton(
      text = if (notifications.status == FluidPermissionStatus.Granted) "Notifiche attive" else "Attiva le notifiche",
      onClick = { notifications.request() },
      style = FluidButtonStyle.Tinted,
      modifier = Modifier.fluidPermissionSource(notifications).testTag(F8p11Tags.Notifications),
    )
    Text(
      text = statusText(notifications),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 8.dp).testTag(F8p11Tags.permissionStatus("notifications")),
    )
  }
}

/** Il benvenuto al 200%: una pagina col contenuto del foglio, per vedere il tasto fermo e le righe che scorrono. */
@Composable
private fun BigTextScene(onBack: () -> Unit) {
  val labels = LocalFluidFirstUseLabels.current
  val density = LocalDensity.current
  val pages = remember { listOf(FluidWelcomePage(labels.welcomeTitle("Note"), NoteFeatures, icon = FluidIcons.Edit)) }
  CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
    Box(Modifier.fillMaxSize()) {
      FluidLiveBackgroundCanvas(FluidLiveBackground.Waves, Modifier.fillMaxSize(), intensity = 0.6f)
      FluidWelcomeContent(
        pages = pages,
        onFinish = onBack,
        privacyNote = NotePrivacy,
        onPrivacyClick = {},
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
      )
    }
  }
}

// --- Il permesso nel punto giusto ----------------------------------------------------------------------------------

/** Tre tasti che hanno bisogno di un permesso, nei casi delle app: le foto, le notifiche, la posizione. */
@Composable
private fun PermissionScene(onBack: () -> Unit) {
  val memory = LocalFluidFirstUseMemory.current
  val coroutines = rememberCoroutineScope()
  var epoch by rememberSaveable { mutableIntStateOf(0) }
  var outcome by rememberSaveable { mutableStateOf("Nessuna azione ancora") }
  key(epoch) {
    val photos = rememberFluidPermission(
      FluidPermission.Photos,
      reason = "MyCloud Gallery carica sul tuo cloud solo le foto che scegli; puoi darle tutte o solo alcune.",
    )
    val notifications = rememberFluidPermission(
      FluidPermission.Notifications,
      reason = "Pampa Mail ti avvisa delle mail nuove nelle cartelle che segui.",
    )
    val location = rememberFluidPermission(
      FluidPermission.ApproximateLocation,
      reason = "Fluid Transit usa la posizione approssimativa per mostrarti le fermate qui vicino.",
    )
    FluidScreen(title = "Permessi", subtitle = "F8.11 · permessi", onBack = onBack) {
      item(key = "foto") {
        PermissionBlock("MyCloud Gallery", "Scegli le foto da caricare", photos, F8p11Tags.Photos) { outcome = "Foto: si carica" }
      }
      item(key = "altre") {
        if (photos.status == FluidPermissionStatus.Partial) {
          FluidButton(
            text = "Scegli altre foto", onClick = photos::requestMore, style = FluidButtonStyle.Plain,
            modifier = Modifier.testTag(F8p11Tags.PhotosMore),
          )
        }
      }
      item(key = "notifiche") {
        PermissionBlock("Pampa Mail", "Avvisami delle mail nuove", notifications, F8p11Tags.Notifications) { outcome = "Notifiche: attive" }
      }
      item(key = "posizione") {
        PermissionBlock("Fluid Transit", "Fermate qui vicino", location, F8p11Tags.Location) { outcome = "Posizione: fermate vicine" }
      }
      item(key = "esito") { StatusLine(outcome) }
      item(key = "dimentica") {
        FluidButton(
          text = "Dimentica «chiesto» e «spiegato»",
          onClick = {
            coroutines.launch {
              for (id in listOf(photos, notifications, location).map { it.permission.id }) {
                memory.write(FluidFirstUseKeys.permissionAsked(id), null)
                memory.write(FluidFirstUseKeys.permissionExplained(id), null)
              }
              epoch++
            }
          },
          style = FluidButtonStyle.Plain,
          modifier = Modifier.testTag(F8p11Tags.Forget),
        )
      }
      item(key = "nota") {
        FluidSectionFootnote(
          text = "La spiegazione nasce dal tasto una volta sola; dopo, anche dopo un no, il tocco apre il dialogo di Android. " +
            "Al secondo no Android non chiede più: il tasto offre «Apri Impostazioni», e al ritorno lo stato si rilegge da " +
            "solo. I permessi sono dichiarati solo nella build di debug della galleria.",
        )
      }
    }
  }
}

@Composable
private fun PermissionBlock(app: String, action: String, state: FluidPermissionState, tag: String, onGranted: () -> Unit) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidSectionHeader(title = app)
    FluidButton(
      text = action,
      onClick = { state.request(onGranted) },
      modifier = Modifier.fluidPermissionSource(state).testTag(tag),
    )
    Text(
      text = statusText(state),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp).testTag(F8p11Tags.permissionStatus(state.permission.id)),
    )
  }
}

private fun statusText(state: FluidPermissionState): String = when (state.status) {
  FluidPermissionStatus.NeverAsked -> "Mai chiesto"
  FluidPermissionStatus.Granted -> "Concesso"
  FluidPermissionStatus.Partial -> "Concesso in parte: solo alcune foto"
  FluidPermissionStatus.Denied -> "Negato: il prossimo tocco chiede di nuovo"
  FluidPermissionStatus.DeniedForever -> "Negato per sempre: il tocco porta alle Impostazioni"
}

// --- Gli sfondi vivi -------------------------------------------------------------------------------------------------

private val LiveStyles = listOf(FluidLiveBackground.Aurora, FluidLiveBackground.Waves, FluidLiveBackground.Stars)

private fun liveLabel(style: FluidLiveBackground) = when (style) {
  FluidLiveBackground.Aurora -> "Aurora"
  FluidLiveBackground.Waves -> "Onde"
  else -> "Stelle"
}

/** Lo sfondo vivo come fondale di una FluidScreen: la barra e il gruppo di vetro lo rifrangono. */
@Composable
private fun LiveScene(onBack: () -> Unit) {
  var style by rememberSaveable { mutableIntStateOf(0) }
  var reduced by rememberSaveable { mutableStateOf(false) }
  val policy = if (reduced) FluidMotionPolicy(durationScale = 0f) else LocalFluidMotionPolicy.current
  CompositionLocalProvider(LocalFluidMotionPolicy provides policy) {
    FluidScreen(
      title = "Ti diamo il benvenuto",
      subtitle = "F8.11 · sfondi-vivi · ${liveLabel(LiveStyles[style])}",
      onBack = onBack,
      ambient = FluidAmbient(FluidHeroTone.PrimaryToSecondary, FluidHeroMotif.Glow, live = LiveStyles[style]),
    ) {
      item(key = "scelta") {
        FluidSegmentedControl(
          options = LiveStyles.indices.toList(),
          selected = style,
          onSelect = { style = it },
          modifier = Modifier.fillMaxWidth().testTag(F8p11Tags.Style),
          label = { liveLabel(LiveStyles[it]) },
        )
      }
      item(key = "vetro") {
        FluidListGroup(glass = true) {
          FluidListRow(title = "La pagina vuota dell'assistente", subtitle = "Chiedimi qualcosa: le aurore sono la mia specialità")
          FluidListDivider()
          FluidListRow(title = "Un onboarding", subtitle = "Lo sfondo resta dietro, lentissimo, mentre leggi")
          FluidListDivider()
          FluidListRow(title = "Mai nelle pagine di tutti i giorni", subtitle = "La nota di Alessio: distrae e basta")
        }
      }
      item(key = "ridotto") {
        FluidListGroup(glass = true) {
          FluidToggleRow(
            title = "Movimento ridotto (prova)",
            subtitle = "Come «Riduci movimento» del sistema: lo sfondo si ferma su una posa leggibile",
            checked = reduced,
            onCheckedChange = { reduced = it },
            modifier = Modifier.testTag(F8p11Tags.Reduced),
          )
        }
      }
      item(key = "nota") {
        FluidSectionFootnote(
          text = "Si ferma da solo anche col risparmio energetico, con la batteria scarica e quando la pagina non si vede. Il " +
            "tempo avanza a 30 Hz: sotto il vetro è una sorgente che cambia, e così il vetro ricampiona un quarto delle volte.",
        )
      }
    }
  }
}

private val Rates = listOf(0, 30, 60, 120)

/** Un traguardo: lo sfondo vivo sotto un pannello di vetro, col ritmo scelto (per misurare il costo). */
@Composable
private fun MilestoneScene(onBack: () -> Unit) {
  var style by rememberSaveable { mutableIntStateOf(2) }
  var rate by rememberSaveable { mutableIntStateOf(1) }
  val live = rememberFluidLiveBackgroundState()
  val backdrop = rememberGlassBackdrop()
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
      if (Rates[rate] > 0) {
        FluidLiveBackgroundCanvas(LiveStyles[style], Modifier.fillMaxSize(), tone = FluidHeroTone.SecondaryToTertiary,
          state = live, frameRateHz = Rates[rate])
      } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
      }
    }
    // Il fondale della pagina è lo sfondo vivo: la schermata non dipinge il suo fondo e la barra rifrange tutti e due.
    CompositionLocalProvider(LocalFluidCanvasBackdrop provides backdrop) {
    FluidScreen(title = "Traguardo", subtitle = "F8.11 · traguardo", onBack = onBack) {
      item(key = "pannello") {
        Column(
          Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .fluidGlassPanel(backdrop, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Group), GlassRole.Modal)
            .padding(vertical = 28.dp, horizontal = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text("100", style = FluidTextStyles.largeNumeric, color = MaterialTheme.colorScheme.onSurface)
          Text("giorni di fila", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
          Text(
            "ClasseViva · cento giorni senza un compito dimenticato",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
      item(key = "scelta") {
        FluidSegmentedControl(
          options = LiveStyles.indices.toList(), selected = style, onSelect = { style = it },
          modifier = Modifier.fillMaxWidth(), label = { liveLabel(LiveStyles[it]) },
        )
      }
      item(key = "ritmo") {
        FluidSegmentedControl(
          options = Rates.indices.toList(), selected = rate, onSelect = { rate = it },
          modifier = Modifier.fillMaxWidth().testTag(F8p11Tags.Rate),
          label = { if (Rates[it] == 0) "Spento" else "${Rates[it]} Hz" },
        )
      }
      item(key = "stato") {
        StatusLine(if (Rates[rate] == 0) "Senza sfondo vivo" else if (live.isRunning) "In movimento a ${Rates[rate]} Hz" else "Fermo")
      }
    }
    }
  }
}

// --- Il primo fotogramma pronto --------------------------------------------------------------------------------------

private val ReadyNotes = listOf(
  "Lista del viaggio" to "Borraccia, taccuino e macchina fotografica",
  "Compiti di fisica" to "Esercizi 4–9 a pagina 112",
  "Idee per il regalo" to "Un libro di fotografia, una pianta",
  "Ricetta del pane" to "500 g di farina, 350 g d'acqua, lievito madre",
)

/**
 * Un avvio come lo vede l'utente: la pagina vera dal primo fotogramma (la barra, il titolo, gli scheletri), e le note che
 * entrano a fuoco quando arrivano (1,2 s, come un database che si apre). «Riapri» rifà l'avvio.
 */
@Composable
private fun ReadyScene(onBack: () -> Unit) {
  var epoch by rememberSaveable { mutableIntStateOf(0) }
  key(epoch) {
    var state by remember { mutableStateOf<FluidLoadState<List<Pair<String, String>>>>(FluidLoadState.Loading) }
    LaunchedEffect(Unit) {
      delay(1_200)
      state = FluidLoadState.Ready(ReadyNotes)
    }
    FluidScreen(title = "Note", subtitle = "F8.11 · avvio-pronto", onBack = onBack) {
      item(key = "contenuto") {
        FluidContentState(state, loading = { FluidSkeletonList(rows = 4) }) { notes ->
          FluidListGroup {
            notes.forEachIndexed { index, (title, text) ->
              if (index > 0) FluidListDivider()
              FluidListRow(title = title, subtitle = text)
            }
          }
        }
      }
      item(key = "riapri") {
        FluidButton(
          text = "Riapri", onClick = { epoch++ }, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Medium,
          modifier = Modifier.testTag(F8p11Tags.Replay),
        )
      }
      item(key = "nota") {
        FluidSectionFootnote(
          text = "La finestra ha già il colore della pagina (Theme.Fluid e fluidFirstFrame), il primo disegno è già la pagina " +
            "con la sua struttura, e il contenuto entra sfocato quando c'è. La schermata d'avvio resta quella di Android.",
        )
      }
    }
  }
}

// --- Pezzi comuni ----------------------------------------------------------------------------------------------------

@Composable
private fun FakeNotes() {
  FluidListGroup {
    FluidListRow(title = "Lista del viaggio", subtitle = "Borraccia, taccuino e macchina fotografica")
    FluidListDivider()
    FluidListRow(title = "Compiti di fisica", subtitle = "Esercizi 4–9 a pagina 112")
  }
}

@Composable
private fun StatusLine(text: String) {
  Text(
    text = text,
    style = FluidTextStyles.numeric,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag(F8p11Tags.Status),
  )
}
