package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La sonda che sente il disegno (F3.10, il punto 2 di F3.8a chiuso nell'engine): un contenitore **fermo** sopra un fondo
 * che cambia **solo colore** (nessuno scorrimento, nessuna ricomposizione del vetro) riceve una lettura nuova e gira.
 *
 * In S3 nelle scene `adattamento` e `clear` del piano flottante e nella pagina «Assistente» niente girava: il fondo
 * cambiava dentro il layer fuori schermo della sorgente, e il nodo che la registra (e avvisa) sta fuori, dove non
 * ridisegna. Le due pagine lo dicevano a mano (`notifyChanged()` nel disegno del fondo); da F3.10 `glassBackdropSource`
 * monta l'avviso dentro il layer, e le pagine non annunciano più niente.
 *
 * Che cosa prova e come. Robolectric non disegna da solo (nessun fotogramma arriva al disegno), e il suo lettore non
 * consegna mai l'immagine. Il disegno qui lo fa il test, una volta, con `View.draw` su una bitmap (come
 * `FluidPageBarIconDrawTest`), col contenitore che non si disegna (il compilatore AGSL di Robolectric rifiuta il programma
 * del gruppo; la sonda non disegna, e il contenitore resta composto e misurato); la lettura la dà il lettore finto della sonda
 * ([FluidGlassProbeDiagnostics.readerForTesting]), che vede il colore che il fondo dipinge. Si prova così la catena dal
 * disegno al giro: il colore cambia, nessuno ricompone; senza disegno la sonda non legge; il ridisegno del contenuto della
 * sorgente avvisa (il contatore del nodo nuovo), la sonda del contenitore si sveglia per la revisione, legge, e il
 * contenitore gira. Che il nodo stia **dentro** il layer (che è ciò che lo fa ridisegnare sul tablet quando l'antenato no)
 * lo prova l'ordine della catena in `GlassProbeSchedulerTest`; sul tablet lo riprende `sessioni/f3-10/riprese.md`.
 * A riposo la sonda non si sveglia: `GlassProbeQuietTest` resta com'è.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class GlassSourceRedrawsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false
  private var warmUpWas = true

  /** Il colore del fondo, letto solo nel disegno. */
  private val fund = mutableStateOf(Color(0xFFF4F1EA))

  /** Quante volte si è composto il contenuto del contenitore, e l'inchiostro che ha ricevuto l'ultima volta. */
  private var compositions = 0
  private var ink = Color.Unspecified

  /** Ogni cambio d'inchiostro, con l'istante dell'orologio del test: se il giro torna indietro, si vede quando. */
  private val inkHistory = ArrayList<String>()

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    // Il riscaldamento dei programmi parte dopo il primo disegno della finestra, e qui il disegno lo fa il test: il
    // compilatore AGSL di Robolectric rifiuta il programma del gruppo. Non è ciò che si prova.
    warmUpWas = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.warmUpEnabled = false
    // Il lettore finto vede il colore che il fondo dipinge adesso.
    FluidGlassProbeDiagnostics.readerForTesting = { fund.value }
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.warmUpEnabled = warmUpWas
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
  }

  /**
   * Il tempo che passa, a passi di un fotogramma. La sonda scrive la lettura e il filtro il suo lato nei messaggi del filo
   * principale (fuori dalla composizione): `sendApplyNotifications` li consegna a chi li segue (lo stato adattivo, con
   * `snapshotFlow`). Senza, la consegna resta al gestore globale degli snapshot, che in certe posizioni della suite (dopo
   * altre classi Robolectric) non gira: il test passava da solo e cadeva in compagnia, con le letture arrivate e
   * l'inchiostro mai girato (come in `FluidGlassContainerTest.change`, F3.6 e F3.2b).
   */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  /** Disegna la finestra in una bitmap, come farebbe il fotogramma che Robolectric non fa (il vetro non si disegna). */
  private fun drawWindow() {
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      try {
        view.draw(Canvas(bitmap))
      } finally {
        bitmap.recycle()
      }
    }
  }

  @Test
  fun aStillContainerOverAFundThatOnlyChangesColourReadsAgainAndFlips() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          // Il fondo: un colore letto solo nel disegno, dentro la sorgente. Cambiarlo non ricompone niente.
          Box(Modifier.fillMaxSize().glassBackdropSource(page).drawBehind { drawRect(fund.value) })
          // Il contenitore è composto, misurato e sonda, ma non si disegna: in Robolectric il compilatore AGSL rifiuta il
          // programma del gruppo («program is too large») quando il test disegna la finestra. La sonda non disegna.
          FluidGlassContainer(Modifier.padding(24.dp).drawWithContent { }, spacing = 0.dp) {
            SideEffect {
              compositions++
            }
            val now = LocalContentColor.current
            SideEffect {
              if (now != ink) inkHistory += "${compose.mainClock.currentTime} ms: $now"
              ink = now
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              repeat(3) { Box(Modifier.size(44.dp).fluidGlass(backdrop = page)) }
            }
          }
        }
      }
    }
    advance(3_000)
    assertEquals("una sonda sola, quella del contenitore", 1, FluidGlassProbeDiagnostics.liveProbes)
    assertTrue("sul fondo chiaro l'inchiostro è scuro: $ink", ink.luminance() < 0.5f)
    val composed = compositions
    val requests = FluidGlassProbeDiagnostics.totalRequests
    val redraws = FluidGlassProbeDiagnostics.totalSourceRedraws
    val revisions = FluidGlassProbeDiagnostics.wakes().getValue("Revision")

    // Il fondo passa a scuro, solo nel disegno.
    fund.value = Color(0xFF15161A)
    Snapshot.sendApplyNotifications()
    advance(1_500)
    assertEquals("cambiare il colore del fondo non ricompone il vetro", composed, compositions)
    assertEquals("senza un disegno nessuna sonda rilegge", requests, FluidGlassProbeDiagnostics.totalRequests)
    assertTrue("e non gira: $ink", ink.luminance() < 0.5f)

    // Il fotogramma che Robolectric non fa: il contenuto della sorgente si ridisegna, e il nodo dentro il layer avvisa.
    drawWindow()
    assertTrue(
      "il ridisegno della sorgente avvisa (${FluidGlassProbeDiagnostics.totalSourceRedraws})",
      FluidGlassProbeDiagnostics.totalSourceRedraws > redraws,
    )
    val readings = FluidGlassProbeDiagnostics.totalReadings
    advance(1_000)
    assertTrue(
      "la sonda del contenitore si sveglia per la revisione (${FluidGlassProbeDiagnostics.wakes()})",
      FluidGlassProbeDiagnostics.wakes().getValue("Revision") > revisions,
    )
    assertTrue(
      "e legge di nuovo (${FluidGlassProbeDiagnostics.totalRequests} richieste, prima $requests)",
      FluidGlassProbeDiagnostics.totalRequests > requests,
    )
    assertTrue(
      "e la lettura arriva (${FluidGlassProbeDiagnostics.totalReadings} letture, prima $readings)",
      FluidGlassProbeDiagnostics.totalReadings > readings,
    )
    // Il giro: il filtro aspetta la sua soglia e la sua permanenza, poi l'inchiostro passa dall'altra parte. Al più 10 s.
    var waited = 0L
    while (ink.luminance() <= 0.5f && waited < 10_000L) {
      advance(16)
      waited += 16
    }
    println(
      "GlassSourceRedraws: giro dopo $waited ms; inchiostro $ink; risvegli ${FluidGlassProbeDiagnostics.wakes()}; " +
        "richieste ${FluidGlassProbeDiagnostics.totalRequests}, letture ${FluidGlassProbeDiagnostics.totalReadings}; " +
        "storia $inkHistory",
    )
    assertTrue("il contenitore gira: l'inchiostro è chiaro ($ink, dopo $waited ms; storia $inkHistory)", ink.luminance() > 0.5f)

    // E poi, fermo, resta girato e non legge più.
    val settled = FluidGlassProbeDiagnostics.totalRequests
    advance(3_000)
    assertTrue("resta girato ($ink; storia $inkHistory)", ink.luminance() > 0.5f)
    assertEquals("a riposo nessuna lettura", settled, FluidGlassProbeDiagnostics.totalRequests)
  }
}
