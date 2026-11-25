package dev.antigravity.fluidengine.sample.novita

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.security.FluidAppLock
import dev.antigravity.fluidengine.security.FluidAppLockHost
import dev.antigravity.fluidengine.security.FluidAppLockSettingsGroup
import dev.antigravity.fluidengine.security.FluidAppLockState
import dev.antigravity.fluidengine.security.FluidLockedContent
import dev.antigravity.fluidengine.security.FluidVaultContent
import dev.antigravity.fluidengine.security.LocalFluidAppLock
import dev.antigravity.fluidengine.security.asLockAuthenticator
import dev.antigravity.fluidengine.security.rememberFluidBiometricGate
import dev.antigravity.fluidengine.security.rememberFluidSystemAuthenticator
import dev.antigravity.fluidengine.security.startExternal
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.LocalFluidPrivateGate
import dev.antigravity.fluidengine.ui.fluid.fluidPrivate
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPrivateState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import dev.antigravity.fluidengine.vault.VaultAccess
import dev.antigravity.fluidengine.vault.VaultAuthMethod
import dev.antigravity.fluidengine.vault.VaultAuthResult
import dev.antigravity.fluidengine.vault.VaultAuthenticator
import dev.antigravity.fluidengine.vault.VaultException
import dev.antigravity.fluidengine.vault.VaultReserve
import dev.antigravity.fluidengine.vault.VaultSectionState
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// F8.23 · Sicurezza: il blocco dell'app (Pampa Mail), le note bloccate (Pampa Notes), l'album nascosto (MyCloud
// Gallery), la chiave che non vale più e le chiavi di PampAI che si scoprono con l'impronta, rifatti col solo engine. Il
// dialogo dell'impronta è quello del sistema; tutto il resto (la schermata di blocco, il segno del contenuto chiuso, la
// riga delle impostazioni) è dell'engine. Nessun numero di movimento qui.

/** Il fondo dietro la schermata di blocco. */
internal enum class F8p23Backdrop(val label: String) {
  Page("Pagina"),
  LightPhoto("Foto chiara"),
  DarkPhoto("Foto scura"),
}

/** I tag dei test. */
internal object F8p23Tags {
  const val LockNow = "f8p23:blocca-ora"
  const val Share = "f8p23:condividi"
  const val Inbox = "f8p23:posta"
  const val NotesLock = "f8p23:note:blocca"
  const val Enroll = "f8p23:caso:impronta"
  const val RemoveLock = "f8p23:caso:schermo"
  const val Reset = "f8p23:caso:ricomincia"
  const val Event = "f8p23:caso:evento"

  fun backdrop(b: F8p23Backdrop): String = "f8p23:fondo:${b.name}"
}

/** Le cinque scene di F8.23, nella pagina «Elementi nuovi». */
internal val F8p23Section: NewElementSection = NewElementSection(
  pack = "F8.23",
  title = "Sicurezza",
  detail = "Il blocco dell'app con l'impronta, il contenuto che resta chiuso e la cassaforte che si richiude da sola",
  scenes = listOf(
    NewElementScene("blocco-app", "Il blocco dell'app", "Pampa Mail · si blocca al rientro, si apre con l'impronta o il PIN") { LockAppScene(it) },
    NewElementScene("note-bloccate", "Le note bloccate", "Pampa Notes · una sezione all'impronta che si richiude da sola") { LockedNotesScene(it) },
    NewElementScene("album-nascosto", "L'album nascosto", "MyCloud Gallery · «Visualizza», e si richiude quando esci") { HiddenAlbumScene(it) },
    NewElementScene("chiave-cambiata", "Quando la chiave non vale più", "Un'impronta aggiunta, il blocco schermo tolto: la frase pronta e la riserva") { KeyChangedScene(it) },
    NewElementScene("chiavi-pampai", "Le chiavi con l'impronta", "PampAI · la chiave di un servizio si scopre solo dopo l'impronta") { PampaiKeysScene(it) },
  ),
)

/** Le voci da provare a mano (in coda a `ToTry.kt`). */
internal val F8p23TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-23-blocco", TryArea.AppParts, "Il blocco dell'app",
    "Apri «Il blocco dell'app», esci con Home e torna: la posta è coperta dal vetro e il dialogo parte da solo; apri " +
      "e guarda l'app entrare a fuoco, poi tocca «Condividi» e torna senza blocco.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il blocco dell'app"),
  ),
  TryItem(
    "f8-23-recenti", TryArea.AppParts, "L'anteprima nelle recenti",
    "Con il blocco acceso apri le app recenti: l'anteprima di Fluid Glass è vuota; spegnilo e guarda di nuovo.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il blocco dell'app"),
  ),
  TryItem(
    "f8-23-pin", TryArea.AppParts, "Il PIN dell'app",
    "Nella riga delle impostazioni scegli un PIN e blocca: scegli «Usa il PIN dell'app», sbaglialo cinque volte e " +
      "leggi l'attesa, poi scrivilo giusto.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il blocco dell'app"),
  ),
  TryItem(
    "f8-23-note", TryArea.AppParts, "Le note bloccate",
    "Apri le note con l'impronta e lasciale un minuto senza toccarle: si richiudono da sole, e anche spegnendo lo schermo.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Le note bloccate"),
  ),
  TryItem(
    "f8-23-chiave", TryArea.AppParts, "La chiave che non vale più",
    "Nella scena della chiave aggiungi un'impronta finta e riapri: leggi la frase; poi togli il blocco schermo finto e " +
      "giudica se la frase dice cosa fare.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Quando la chiave non vale più"),
  ),
)

// ================================================================================================================
// I pezzi comuni
// ================================================================================================================

@Composable
private fun BackdropChips(selected: F8p23Backdrop, onSelect: (F8p23Backdrop) -> Unit) {
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    F8p23Backdrop.entries.forEach { b ->
      FluidChip(label = b.label, selected = b == selected, onClick = { onSelect(b) }, modifier = Modifier.testTag(F8p23Tags.backdrop(b)))
    }
  }
}

/** Lo sfondo della schermata di blocco per il fondo scelto: la foto, o (pagina) quello di serie dell'engine. */
private fun lockBackdrop(backdrop: F8p23Backdrop): (@Composable BoxScope.() -> Unit)? = when (backdrop) {
  F8p23Backdrop.Page -> null
  F8p23Backdrop.LightPhoto -> { { Photo(R.drawable.viewer_astronaut) } }
  F8p23Backdrop.DarkPhoto -> { { Photo(R.drawable.viewer_rocket) } }
}

@Composable
private fun Photo(id: Int) {
  Image(painterResource(id), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

/** Il blocco di una scena: uno suo, staccato dal conto delle attività quando la scena se ne va. */
@Composable
private fun rememberSceneLock(name: String): FluidAppLock {
  val context = LocalContext.current
  val lock = remember(name) { FluidAppLock(context, name = name, lockVaultOnLock = false) }
  DisposableEffect(lock) { onDispose { lock.close() } }
  // La scena parte col blocco acceso: è quello che mostra.
  LaunchedEffect(lock) {
    lock.state.first { it != FluidAppLockState.Loading }
    // Un Keystore che non risponde lascia il blocco spento: la scena resta aperta e lo dice la riga.
    if (!lock.settings.value.enabled) runCatching { lock.setEnabled(true) }
  }
  return lock
}

// ================================================================================================================
// Il blocco dell'app (Pampa Mail)
// ================================================================================================================

@Composable
private fun LockAppScene(onBack: () -> Unit) {
  var backdrop by rememberSaveable { mutableStateOf(F8p23Backdrop.Page) }
  val lock = rememberSceneLock("f8p23-posta")
  FluidAppLockHost(lock = lock, appName = "Pampa Mail", backdrop = lockBackdrop(backdrop)) {
    LockAppContent(onBack, backdrop) { backdrop = it }
  }
}

@Composable
private fun LockAppContent(onBack: () -> Unit, backdrop: F8p23Backdrop, onBackdrop: (F8p23Backdrop) -> Unit) {
  val lock = LocalFluidAppLock.current
  val context = LocalContext.current
  FluidScreen(title = "Posta", subtitle = "F8.23 · Pampa Mail con il blocco dell'app", onBack = onBack) {
    item(key = "fondi") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackdropChips(backdrop, onBackdrop)
        FluidSectionFootnote(text = "Il fondo è quello dietro il vetro della schermata di blocco.")
      }
    }
    item(key = "posta") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "In arrivo", detail = "Esci con Home e torna: la posta si copre e chiede l'impronta.")
        FluidListGroup(Modifier.testTag(F8p23Tags.Inbox)) {
          listOf(
            Triple("Marco", "Cena di sabato", "Ci sei? Porto io il dolce."),
            Triple("Segreteria", "Pagella del primo periodo", "La trovi nel registro da domani."),
            Triple("Giulia", "Foto della gita", "Te le mando stasera."),
          ).forEachIndexed { index, (from, subject, preview) ->
            if (index > 0) FluidListDivider()
            FluidListRow(title = "$from · $subject", subtitle = preview, onClick = {})
          }
        }
      }
    }
    item(key = "azioni") {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidButton(
          text = "Blocca ora",
          onClick = { lock?.lock() },
          style = FluidButtonStyle.Filled,
          modifier = Modifier.testTag(F8p23Tags.LockNow),
        )
        FluidButton(
          text = "Condividi",
          onClick = {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Ci sei sabato?")
            lock.startExternal(context, Intent.createChooser(send, null))
          },
          style = FluidButtonStyle.Tinted,
          modifier = Modifier.testTag(F8p23Tags.Share),
        )
      }
    }
    item(key = "impostazioni") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "La riga delle impostazioni", detail = "Quella che un'app mette in Impostazioni › Privacy.")
        if (lock != null) FluidAppLockSettingsGroup(lock = lock, appName = "Pampa Mail")
        FluidSectionFootnote(
          text = "Mentre condividi o scegli un file l'app non si blocca. Nelle app recenti l'anteprima è vuota finché il " +
            "blocco è acceso. Il blocco è una porta, non una cassaforte: i dati che contano vanno nella cassaforte.",
        )
      }
    }
  }
}

// ================================================================================================================
// Le note bloccate (Pampa Notes)
// ================================================================================================================

private val DemoNotes = listOf(
  "Il codice del portone" to "4821, poi il tasto con la campanella.",
  "Regali di Natale" to "Mamma: il libro di ricette. Luca: le cuffie.",
  "Wi-Fi della nonna" to "CasaNonna_5G · la password è sul router.",
)

@Composable
private fun LockedNotesScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val vault = remember(context) { FluidVault.get(context) }
  // Un minuto per vederla richiudersi; di serie sono cinque (VaultDefaults.RelockAfter).
  val section = remember(vault) { vault.section("f8p23-note", VaultAccess.Biometric(relockAfter = 1.minutes)) }
  val state by section.state.collectAsState()
  var notes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(state) {
    notes = if (state is VaultSectionState.Open) {
      try {
        val values = section.values
        if (values.keys().isEmpty()) DemoNotes.forEachIndexed { i, (t, b) -> values.putString("nota-$i", "$t\n$b") }
        values.keys().sorted().mapNotNull { values.getString(it)?.split('\n', limit = 2)?.let { p -> p[0] to p.getOrElse(1) { "" } } }
      } catch (e: VaultException) {
        // Richiusa mentre si leggeva (il tempo, lo schermo spento): il segno di chiuso torna da solo.
        emptyList()
      }
    } else {
      emptyList()
    }
  }
  FluidScreen(title = "Note bloccate", subtitle = "F8.23 · Pampa Notes, una cartella all'impronta", onBack = onBack) {
    item(key = "cartella") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Privata", detail = "Cifrata con una chiave che chiede l'impronta a ogni apertura.")
        FluidListGroup {
          FluidVaultContent(section = section, title = "Privata") {
            Column {
              notes.forEachIndexed { index, (title, body) ->
                if (index > 0) FluidListDivider()
                FluidListRow(title = title, subtitle = body, onClick = {})
              }
              FluidButton(
                text = "Blocca",
                onClick = { section.lock() },
                style = FluidButtonStyle.Plain,
                modifier = Modifier.padding(12.dp).testTag(F8p23Tags.NotesLock),
              )
            }
          }
        }
        FluidSectionFootnote(
          text = "Si richiude da sola dopo un minuto senza usarla (cinque, di serie), quando lo schermo si spegne e con " +
            "«Blocca». Il codice del telefono è la riserva: se aggiungi un'impronta, la chiave si chiude per sicurezza e " +
            "il codice la rifà.",
        )
      }
    }
  }
}

// ================================================================================================================
// L'album nascosto (MyCloud Gallery)
// ================================================================================================================

@Composable
private fun HiddenAlbumScene(onBack: () -> Unit) {
  var revealed by remember { mutableStateOf(false) }
  val authenticator = rememberFluidSystemAuthenticator()?.asLockAuthenticator()
  val scope = rememberCoroutineScope()
  var message by remember { mutableStateOf<String?>(null) }
  // Come Foto: l'album si richiude quando esci dall'app.
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) revealed = false }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  FluidScreen(title = "Album", subtitle = "F8.23 · MyCloud Gallery, l'album nascosto", onBack = onBack) {
    item(key = "nascosto") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Nascosto", detail = "Come Foto di iOS: «Visualizza», l'impronta, e si richiude quando esci.")
        FluidListGroup {
          FluidLockedContent(
            unlocked = revealed,
            title = "L'album Nascosto",
            message = message,
            onView = {
              scope.launch {
                val result = authenticator?.authenticateUser("Visualizza l'album Nascosto", null, null)
                message = (result as? VaultAuthResult.Failed)?.message
                if (result == VaultAuthResult.Success) revealed = true
              }
            },
          ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(
                listOf(R.drawable.viewer_flower, R.drawable.viewer_astronaut, R.drawable.viewer_rocket),
                listOf(R.drawable.viewer_rocket, R.drawable.viewer_flower, R.drawable.viewer_astronaut),
              ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  row.forEach { id ->
                    Image(
                      painterResource(id), contentDescription = "Foto nascosta", contentScale = ContentScale.Crop,
                      modifier = Modifier.weight(1f).aspectRatio(1f).clip(ContinuousCornerShape(FluidRadius.Small)),
                    )
                  }
                }
              }
              FluidButton(text = "Blocca", onClick = { revealed = false }, style = FluidButtonStyle.Plain)
            }
          }
        }
      }
    }
  }
}

// ================================================================================================================
// Quando la chiave non vale più: il Keystore finto della cassaforte
// ================================================================================================================

/** Il dialogo finto della scena: dice di sì, e scrive cosa avrebbe chiesto il sistema. */
private class ShownAuthenticator(private val keystore: MemoryVaultKeystore, private val onRequest: (String) -> Unit) : VaultAuthenticator {
  override suspend fun authenticate(request: dev.antigravity.fluidengine.vault.VaultAuthRequest): VaultAuthResult {
    val what = if (request.method == VaultAuthMethod.Biometric) "l'impronta" else "il codice del telefono"
    onRequest("Il dialogo ha chiesto $what" + (request.subtitle?.let { ": «$it»" } ?: "."))
    val cipher = request.cipher
    if (cipher != null) keystore.approve(cipher) else keystore.authenticate()
    return VaultAuthResult.Success
  }
}

@Composable
private fun KeyChangedScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val keystore = remember { MemoryVaultKeystore() }
  var generation by remember { mutableStateOf(0) }
  var event by remember { mutableStateOf("Apri la sezione: la prima volta nasce, vuota e aperta.") }
  val vault = remember(generation) {
    FluidVault.create(dir = File(context.cacheDir, "f8p23-prova/$generation"), keystore = keystore)
  }
  val section = remember(vault) { vault.section("prova", VaultAccess.Biometric(reserve = VaultReserve.DeviceCredential)) }
  val authenticator = remember(keystore) { ShownAuthenticator(keystore) { event = it } }
  val scope = rememberCoroutineScope()
  FluidScreen(title = "La chiave cambiata", subtitle = "F8.23 · il Keystore finto della cassaforte, per vedere i casi", onBack = onBack) {
    item(key = "sezione") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "Una sezione all'impronta", detail = "Con il codice del telefono come riserva.")
        FluidListGroup {
          FluidVaultContent(section = section, title = "La sezione", authenticator = authenticator) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Aperta. I dati ci sono.", style = MaterialTheme.typography.bodyLarge)
              FluidButton(text = "Blocca", onClick = { section.lock() }, style = FluidButtonStyle.Plain)
            }
          }
        }
        Text(event, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(F8p23Tags.Event))
      }
    }
    item(key = "casi") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "I casi", detail = "Quello che succede sul telefono vero, fatto sul Keystore finto.")
        FluidButton(
          text = "Aggiungi un'impronta",
          onClick = { section.lock(); keystore.enrollBiometric(); event = "Impronta aggiunta: riapri la sezione." },
          style = FluidButtonStyle.Tinted,
          modifier = Modifier.testTag(F8p23Tags.Enroll),
        )
        FluidButton(
          text = "Togli il blocco schermo",
          onClick = { section.lock(); keystore.removeScreenLock(); event = "Blocco schermo tolto: riapri la sezione." },
          style = FluidButtonStyle.Tinted,
          modifier = Modifier.testTag(F8p23Tags.RemoveLock),
        )
        FluidButton(
          text = "Ricomincia",
          onClick = {
            scope.launch {
              section.reset()
              keystore.deviceSecure = true
              keystore.biometricsEnrolled = true
              generation++
              event = "Da capo: una sezione nuova."
            }
          },
          style = FluidButtonStyle.Plain,
          modifier = Modifier.testTag(F8p23Tags.Reset),
        )
        FluidSectionFootnote(
          text = "Il telefono vero non lascia aggiungere un'impronta da un'app: qui la cassaforte gira su un Keystore in " +
            "memoria che finge l'impronta, le impronte aggiunte e il blocco schermo tolto, come nei test.",
        )
      }
    }
  }
}

// ================================================================================================================
// Le chiavi di PampAI
// ================================================================================================================

@Composable
private fun PampaiKeysScene(onBack: () -> Unit) {
  val gate = rememberFluidBiometricGate(title = "Mostra la chiave")
  FluidScreen(title = "Chiavi", subtitle = "F8.23 · PampAI, la chiave si scopre dopo l'impronta", onBack = onBack) {
    item(key = "chiavi") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionHeader(title = "I servizi", detail = "Il velo di F8.3, col cancello dell'impronta di F8.23.")
        CompositionLocalProvider(LocalFluidPrivateGate provides gate) {
          FluidListGroup {
            listOf("OpenRouter" to "sk-or-v1-7f3a9c2e41b8d605", "Anthropic" to "sk-ant-api03-Qm4x8LpZ2v").forEachIndexed { index, (name, key) ->
              if (index > 0) FluidListDivider()
              val state = rememberFluidPrivateState()
              Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(120.dp))
                Text(key, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).fluidPrivate(state))
              }
            }
          }
        }
        FluidSectionFootnote(
          text = "Le chiavi stanno nella cassaforte dell'engine (engine-ai cifra con lei, con lo stesso alias di prima): " +
            "qui si vede il cancello, il dialogo di sistema prima del tocco che scopre.",
        )
      }
    }
  }
}
