package dev.antigravity.fluidengine.sample.catalog

import android.print.PrintManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import dev.antigravity.fluidengine.sample.MatrixTextScale
import dev.antigravity.fluidengine.sample.matrixDemoSettings
import dev.antigravity.fluidengine.sample.rememberMatrixFlag
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/** Corpus del contenitore, intenzionalmente non sanificato. Non e' il corpus del sanificatore app. */
@Composable
fun WebContentDemo() {
  var index by remember { mutableIntStateOf(0) }
  // Nella galleria gli interruttori partono dalla matrice degli stati (e restano liberi); fuori, da spenti.
  var dark by rememberMatrixFlag { it.theme.isDark }
  var original by remember { mutableStateOf(false) }
  var large by rememberMatrixFlag { it.textScale == MatrixTextScale.Largest }
  var reduced by rememberMatrixFlag { it.reducedMotion }
  var lastLink by remember { mutableStateOf("Nessun link selezionato") }
  val samples = remember { webContentHostileSamples() }
  val state = rememberFluidWebContentState()
  val printManager = LocalContext.current.getSystemService(PrintManager::class.java)
  FluidTheme(matrixDemoSettings(dark), systemBars = false) {
    FluidMotionPolicyProvider(FluidMotionPolicy(if (reduced) 0f else 1f)) {
      FluidScreen(title = "Contenuto web", subtitle = "HTML locale e risorse controllate dall'app") {
        item {
          FluidListGroup {
            FluidListRow(title = samples.keys.elementAt(index), subtitle = "Tocca per il campione successivo",
              onClick = { index = (index + 1) % samples.size })
            FluidToggleRow(title = "Tema scuro", checked = dark, onCheckedChange = { dark = it })
            FluidToggleRow(title = "Mostra originale", checked = original, onCheckedChange = { original = it })
            FluidToggleRow(title = "Testo al 200%", checked = large, onCheckedChange = { large = it })
            FluidToggleRow(title = "Movimento ridotto", checked = reduced, onCheckedChange = { reduced = it })
            FluidListRow(title = "Ricrea il contenitore", subtitle = "Nuovo renderer e nuovo profilo", onClick = state::retry)
            FluidListRow(title = "Stampa documento", subtitle = "Disponibile dopo la prima pittura",
              onClick = { state.printAdapter("Documento locale")?.let { printManager.print("Documento locale", it, null) } })
          }
        }
        item { Text(lastLink, style = MaterialTheme.typography.bodyMedium) }
        item {
          FluidWebContent(html = samples.values.elementAt(index), state = state,
            onLinkClick = { lastLink = "${it.visibleText.orEmpty()} → ${it.url}" },
            onLinkLongPress = { lastLink = "Pressione lunga → ${it.url}" },
            modifier = Modifier.fillMaxWidth(), darkening = dark && !original,
            textScale = webContentDemoTextScale(LocalDensity.current.fontScale, large), sizing = FluidWebSizing.WrapContent(1000.dp),
            placeholder = { Text("Preparazione del contenuto…", style = MaterialTheme.typography.bodyMedium) },
            failure = {
              Column {
                Text("Contenuto non disponibile: $it", style = MaterialTheme.typography.bodyMedium)
                Text("Qui l'app mostra il testo alternativo.", style = MaterialTheme.typography.bodyMedium)
              }
            })
        }
      }
    }
  }
}

/**
 * Il `textScale` da dare al contenitore perche' «Testo al 200%» voglia dire **200%** e non di piu'.
 *
 * Il contenitore applica `textZoom = fontScale × textScale × 100`: la scala del carattere di sistema ([fontScale],
 * che nella galleria e' anche quella della matrice) si moltiplica con quella che passa la demo. Passare un 2 fisso
 * con la matrice al 200% voleva dire 400%. Qui si chiede al contenitore **solo quel che manca** per arrivare a 200%:
 * 2 a 1,0×, 1,54 a 1,3×, 1 a 2,0× (la matrice ha gia' fatto tutto); mai sotto 1, cosi' un sistema oltre il 200%
 * non viene rimpicciolito. Interruttore spento: 1, il testo e' quello del sistema.
 */
internal fun webContentDemoTextScale(fontScale: Float, large: Boolean): Float =
  if (large) (WebDemoLargeZoom / fontScale).coerceAtLeast(1f) else 1f

/** Il testo «grande» della demo: 200%. */
private const val WebDemoLargeZoom = 2f

/** Lo zoom del testo che il contenitore applica per [fontScale] e [textScale]: lo stesso conto di `FluidWebContent`. */
internal fun webContentEffectiveZoom(fontScale: Float, textScale: Float): Int =
  (fontScale * textScale * 100).roundToInt().coerceIn(1, 1000)

/** Campioni condivisi con le prove strumentate: rete sempre chiusa finche' l'app non fornisce byte. */
internal fun webContentHostileSamples(): Map<String, String> = linkedMapOf(
  "Vuoto" to "",
  "Documento semplice" to "<h1>Documento locale</h1><p>Testo selezionabile e leggibile.</p><a href='https://example.org/vero'>https://altro.example</a>",
  "Newsletter larga" to "<meta name='viewport' content='width=device-width'><table width='700'><tr><td>" + "Colonna larga. ".repeat(150) + "</td></tr></table>",
  "HTML ostile" to """
    <title>SAFE</title><meta http-equiv='refresh' content='1;url=https://tracker.invalid/refresh'>
    <link rel='preconnect' href='https://tracker.invalid'><link rel='dns-prefetch' href='//tracker.invalid'>
    <script>document.title='EXECUTED';fetch('https://tracker.invalid/script')</script>
    <script src='https://tracker.invalid/script.js'></script><script type='module'>document.title='EXECUTED'</script>
    <ScRiPt type='application/javascript'>document.title='EXECUTED'</ScRiPt>
    <body onload="document.title='EXECUTED'"><h1>SAFE</h1>
    <img src='https://tracker.invalid/pixel' width='1' height='1' onerror="document.title='EXECUTED'">
    <img src='cid:local-part'><svg onload="document.title='EXECUTED'"><script>document.title='EXECUTED'</script></svg>
    <img src="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' onload='document.title=%26quot;EXECUTED%26quot;'/%3E">
    <svg><foreignObject><div xmlns='http://www.w3.org/1999/xhtml' onload="document.title='EXECUTED'">SVG</div></foreignObject></svg>
    <details open ontoggle="document.title='EXECUTED'"><summary>Evento HTML</summary></details>
    <iframe src='https://tracker.invalid/frame'></iframe><iframe srcdoc="<script>document.title='EXECUTED'</script>"></iframe>
    <object data='https://tracker.invalid/object'></object><embed src='https://tracker.invalid/embed'>
    <form action='https://tracker.invalid/form'><input name='secret' value='private'><button>Invia</button></form>
    <input autofocus onfocus="document.title='EXECUTED'"><video src='https://tracker.invalid/video' onerror="document.title='EXECUTED'"></video>
    <style>@import url('https://tracker.invalid/style'); @font-face {font-family:remote;src:url('https://tracker.invalid/font')}
    p {background:url('https://tracker.invalid/background');font-family:remote}</style>
    <p>Risorse CSS</p><a href="javascript:document.title='EXECUTED'">Link script</a>
    <a href='intent://unsafe'>Intent</a><a href='file:///sdcard/private'>File</a>
    <a href='content://private'>Content</a><a href='https://real.invalid/destination'>https://display.invalid</a>
  """.trimIndent(),
  "Lungo e malformato" to "<h1>Documento lungo</h1>" + "<div><b>Testo non chiuso<p>Contenuto leggibile. ".repeat(12_000),
  "Stile scuro proprio" to "<meta name='color-scheme' content='dark light'><style>body{color-scheme:dark light}</style><h1>Tema dichiarato dal documento</h1>",
  "Errore dimensione" to "x".repeat(FluidWebContentDefaults.MaxHtmlCharacters + 1),
)
