@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.richtext.FluidRichTextChangeSource
import dev.antigravity.fluidengine.richtext.FluidRichTextDocument
import dev.antigravity.fluidengine.richtext.FluidRichTextEditor
import dev.antigravity.fluidengine.richtext.FluidRichTextListKind
import dev.antigravity.fluidengine.richtext.FluidRichTextParagraphStyle
import dev.antigravity.fluidengine.richtext.FluidRichTextState
import dev.antigravity.fluidengine.richtext.FluidRichTextToolbar
import dev.antigravity.fluidengine.richtext.RichTextDecorations
import dev.antigravity.fluidengine.richtext.buildFluidRichText
import dev.antigravity.fluidengine.richtext.rememberFluidRichTextState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * I difetti dell'editor con formattazione trovati dalla review R2 (muse-f8-prime) che si vedono solo con
 * l'editor vero: l'incolla di un HTML grande (51), le decorazioni di un elenco lungo (52), il foglio del
 * link col documento che cambia (53) e alla rotazione (minore). Le prove di costo confrontano due misure
 * dello stesso giro, non un tempo assoluto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidRichTextR2UiTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var host: View
  private lateinit var state: FluidRichTextState

  private val linked = buildFluidRichText {
    paragraph { append("vai al "); link("sito", "https://esempio.it"); append(" e poi avanti") }
  }

  @Composable
  private fun Scene(state: FluidRichTextState, editorModifier: Modifier = Modifier, toolbar: Boolean = true) {
    FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK) }) {
      host = LocalView.current
      val modals = rememberFluidGlassModalHostState()
      CompositionLocalProvider(
        LocalFluidMotionPolicy provides FluidMotionPolicy(0f),
        LocalFluidGlassModalHostState provides modals,
      ) {
        Box(Modifier.fillMaxSize()) {
          if (toolbar) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
              FluidRichTextEditor(state = state, modifier = editorModifier, contentDescription = "Corpo")
            }
            FluidRichTextToolbar(state = state, modifier = Modifier.align(Alignment.BottomCenter))
          } else {
            FluidRichTextEditor(state = state, modifier = editorModifier, contentDescription = "Corpo")
          }
          FluidGlassModalHost(modals, backdrop = null)
        }
      }
    }
  }

  private fun setEditor(initial: FluidRichTextDocument, editorModifier: Modifier = Modifier, toolbar: Boolean = true) {
    state = FluidRichTextState(initial)
    compose.setContent { Scene(state, editorModifier, toolbar) }
  }

  private fun editor() = compose.onNode(hasSetTextAction() and hasContentDescription("Corpo"))

  private fun click(matcher: androidx.compose.ui.test.SemanticsMatcher) =
    compose.onNode(matcher and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)

  /** Il foglio del link aperto dal tasto della barra, sulla parola «sito». */
  private fun openLinkSheet() {
    editor().performClick()
    compose.runOnIdle { state.select(TextRange(7, 11)) }
    compose.waitForIdle()
    click(hasContentDescription("Link"))
    compose.waitForIdle()
    compose.onNode(hasText("Fatto") and hasClickAction()).assertExists()
  }

  // --- 53: il foglio del link e il documento che cambia --------------------------------------------

  @Test fun confirmingTheLinkAfterTheDocumentShrankDoesNotCrash() {
    setEditor(linked)
    openLinkSheet()
    // Una sincronizzazione accorcia il documento mentre il foglio e' aperto.
    compose.runOnIdle { state.setDocument(FluidRichTextDocument.fromPlainText("corto")) }
    click(hasText("Fatto"))
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals("corto", state.document.text)
      assertTrue(state.document.spans.isEmpty())
    }
    compose.onNode(hasText("Fatto") and hasClickAction()).assertDoesNotExist()
  }

  @Test fun confirmingTheLinkAfterTheTextChangedDoesNotLinkTheWrongWords() {
    setEditor(linked)
    openLinkSheet()
    val other = "un altro testo, stessa lunghezza".take(linked.text.length).padEnd(linked.text.length, '.')
    compose.runOnIdle { state.setDocument(FluidRichTextDocument.fromPlainText(other)) }
    click(hasText("Fatto"))
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(other, state.document.text)
      assertTrue("nessun link su parole che non erano quelle del foglio", state.document.spans.isEmpty())
    }
  }

  @Test fun removingTheLinkAfterTheTextChangedLeavesTheNewText() {
    setEditor(linked)
    openLinkSheet()
    val other = buildFluidRichText { paragraph { append("vai al "); link("SITO", "https://altro.it"); append(" e poi avanti") } }
    compose.runOnIdle { state.setDocument(other) }
    click(hasText("Togli il link"))
    compose.waitForIdle()
    compose.runOnIdle { assertEquals(other, state.document) }
  }

  // --- minore: il foglio del link alla rotazione ---------------------------------------------------

  @Test fun theLinkSheetAndItsDraftSurviveRecreation() {
    val tester = StateRestorationTester(compose)
    tester.setContent {
      val remembered = rememberFluidRichTextState(linked)
      state = remembered
      Scene(remembered)
    }
    openLinkSheet()
    compose.onNode(hasSetTextAction() and hasText("sito")).performTextReplacement("il mio sito")
    compose.onNode(hasSetTextAction() and hasText("https://esempio.it")).performTextReplacement("altro.it")
    compose.waitForIdle()
    tester.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    compose.onNode(hasSetTextAction() and hasText("il mio sito")).assertExists()
    compose.onNode(hasSetTextAction() and hasText("altro.it")).assertExists()
    click(hasText("Fatto"))
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals("vai al il mio sito e poi avanti", state.document.text)
      assertEquals("https://altro.it", state.document.formatAt(8).link)
    }
  }

  // --- 51: incollare una pagina HTML grande --------------------------------------------------------

  /**
   * Un testo con stili che ricorda su quali thread `Html.toHtml` lo ha letto per farne HTML (lo legge anche
   * Compose, per conto suo: contano solo le letture che vengono da `android.text.Html`).
   */
  private class ProbeText(text: CharSequence) : SpannableString(text) {
    val readers: MutableSet<Thread> = java.util.Collections.synchronizedSet(HashSet())

    override fun nextSpanTransition(start: Int, limit: Int, kind: Class<*>?): Int {
      if (Thread.currentThread().stackTrace.any { it.className == "android.text.Html" }) readers.add(Thread.currentThread())
      return super.nextSpanTransition(start, limit, kind)
    }
  }

  private fun styledText(lines: Int): ProbeText {
    val plain = (0 until lines).joinToString("\n") { "riga $it forte" }
    return ProbeText(plain).apply {
      var start = 0
      repeat(lines) {
        val forte = plain.indexOf("forte", start)
        setSpan(StyleSpan(Typeface.BOLD), forte, forte + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        start = forte + 5
      }
    }
  }

  @Test fun aLargeStyledPasteIsReadOffTheMainThread() {
    // Il callback degli appunti gira sul thread principale: leggere li' un incolla grande bloccava
    // l'interfaccia. Il segno che non succede piu' e' il thread che legge il testo, non un tempo.
    setEditor(FluidRichTextDocument.fromPlainText("prima "))
    editor().performClick()
    compose.waitForIdle()
    val clipboard = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val probe = styledText(2_000)
    clipboard.setPrimaryClip(ClipData.newPlainText("note", probe))
    compose.runOnIdle { state.select(TextRange(6)) }
    editor().performSemanticsAction(SemanticsActions.PasteText)
    compose.waitUntil(timeoutMillis = 60_000) { state.document.paragraphCount == 2_000 }
    val main = Looper.getMainLooper().thread
    assertTrue("letto da ${probe.readers.map { it.name }}", probe.readers.isNotEmpty() && main !in probe.readers)
    compose.runOnIdle {
      assertTrue(state.document.text.startsWith("prima riga 0 forte\nriga 1 forte"))
      assertTrue(state.document.formatAt("prima riga 0 ".length).bold)
    }
  }

  private fun bigHtml(paragraphs: Int): String = buildString {
    repeat(paragraphs) { append("<p>riga ").append(it).append(" <b>forte</b> <a href=\"https://esempio.it/").append(it).append("\">link</a></p>") }
  }

  @Test fun aLargeHtmlPasteArrivesWhole() {
    setEditor(FluidRichTextDocument.fromPlainText("prima "))
    editor().performClick()
    compose.waitForIdle()
    val clipboard = host.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newHtmlText("web", "testo", bigHtml(2_000)))
    compose.runOnIdle { state.select(TextRange(6)) }
    editor().performSemanticsAction(SemanticsActions.PasteText)
    compose.waitUntil(timeoutMillis = 60_000) { state.document.paragraphCount == 2_000 }
    compose.runOnIdle {
      assertTrue(state.document.text.startsWith("prima riga 0 forte link\nriga 1 forte link"))
      assertTrue(state.document.formatAt("prima riga 0 ".length).bold)
      assertEquals("https://esempio.it/1", state.document.formatAt(state.document.text.indexOf("link\nriga 2")).link)
      assertEquals(FluidRichTextChangeSource.Paste, state.lastChangeSource)
    }
    // Si annulla in un passo, come ogni incolla.
    compose.runOnIdle { state.undo() }
    compose.runOnIdle { assertEquals("prima ", state.document.text) }
  }

  // --- 52: le decorazioni di un elenco lungo -------------------------------------------------------

  private fun longList(kind: FluidRichTextListKind, items: Int): FluidRichTextDocument = buildFluidRichText {
    repeat(items) { paragraph(FluidRichTextParagraphStyle(list = kind)) { append("voce numero $it") } }
  }

  /**
   * Un disegno del campo forzato, su una tela software: Robolectric da solo non disegna i fotogrammi, e
   * su una tela software Compose rifa' tutti i disegni (e' il caso peggiore, come un layer da registrare).
   */
  private fun drawNow(): Bitmap {
    lateinit var bitmap: Bitmap
    compose.runOnIdle {
      bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
      host.draw(Canvas(bitmap))
    }
    return bitmap
  }

  /** I pixel con inchiostro nella colonna dei segni (prima del rientro del testo), nella parte del campo in vista. */
  private fun markerInk(bitmap: Bitmap): Pair<Int, IntArray> {
    val bounds = editor().fetchSemanticsNode().boundsInRoot
    val left = bounds.left.toInt()
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = bounds.bottom.toInt().coerceAtMost(bitmap.height)
    // Il testo di una voce comincia a 1,6 em (51 px a 16 sp e xhdpi): i segni stanno prima.
    val width = 40
    val background = bitmap.getPixel(left + 1, top + 1)
    val strip = IntArray(width * (bottom - top))
    var ink = 0
    for (y in top until bottom) {
      for (x in 0 until width) {
        val pixel = bitmap.getPixel(left + x, y)
        strip[(y - top) * width + x] = pixel
        val distance = Math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(background)) +
          Math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(background)) +
          Math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(background))
        if (distance > 90) ink++
      }
    }
    return ink to strip
  }

  @Test fun aLongNumberedListDoesNotRemeasureItsMarkersAtEveryDraw() {
    // Un editor alto 300 dp che scorre da se': mille cinquecento voci, ne entrano una dozzina. I segni
    // numerati sono tutti diversi, quelli puntati tre: prima ogni disegno rimisurava tutti i numeri.
    setEditor(longList(FluidRichTextListKind.Numbered, 1_500), Modifier.height(300.dp), toolbar = false)
    editor().performClick()
    compose.waitForIdle()

    fun use(kind: FluidRichTextListKind) {
      compose.runOnIdle { state.setDocument(longList(kind, 1_500), TextRange(0)) }
      compose.waitForIdle()
    }

    /** Le misure dei segni durante [block] (F7.32: si contano, non si cronometrano). */
    fun measuresDuring(block: () -> Unit): Long {
      val before = compose.runOnIdle { RichTextDecorations.markerMeasures }
      block()
      return compose.runOnIdle { RichTextDecorations.markerMeasures } - before
    }

    // F7.32 · Prima la prova cronometrava un disegno di voci numerate contro uno di voci puntate (soglia 1,6; misurati
    // 1,1, e 2,3 col difetto): sul PC carico una pausa nel mezzo bastava a farla cadere. Ora conta le misure dei segni.
    // Col difetto ogni disegno ne faceva 1 500; ora il primo disegno misura solo i segni in vista, una volta, e gli altri
    // nessuno. Due giri alternati, come prima: cambiando elenco i segni dell'altro restano misurati.
    repeat(2) { round ->
      for (kind in listOf(FluidRichTextListKind.Numbered, FluidRichTextListKind.Bullet)) {
        use(kind)
        val first = measuresDuring { drawNow() }
        val again = measuresDuring { repeat(3) { drawNow() } }
        println("R2 decorazioni | giro ${round + 1}, $kind: il primo disegno misura $first segni, i tre dopo $again")
        assertTrue("$kind, giro ${round + 1}: il primo disegno misura $first segni su 1 500 voci, non solo quelli in vista", first <= MaxVisibleMarkers)
        assertEquals("$kind, giro ${round + 1}: i disegni dopo il primo rimisurano i segni", 0L, again)
      }
    }

    // I segni si vedono ancora, in cima e in fondo all'elenco (la parte in vista cambia con lo scorrimento).
    compose.runOnIdle { state.setDocument(longList(FluidRichTextListKind.Numbered, 1_500), TextRange(0)) }
    compose.waitForIdle()
    val (topInk, topStrip) = markerInk(drawNow())
    compose.runOnIdle { state.select(TextRange(state.document.text.length)) }
    compose.waitForIdle()
    val (endInk, endStrip) = markerInk(drawNow())
    println("R2 decorazioni | inchiostro dei segni: in cima $topInk, in fondo $endInk")
    assertTrue("i numeri in cima si vedono ($topInk)", topInk > 30)
    assertTrue("i numeri in fondo si vedono ($endInk)", endInk > 30)
    assertFalse("in fondo i numeri sono altri: il campo e' sceso", topStrip.contentEquals(endStrip))
  }
}

/**
 * F7.32 · Quanti segni può misurare il primo disegno dell'editor alto 300 dp: ne entrano una dozzina (con le righe a metà),
 * e il tetto lascia il margine di righe più basse. Col difetto di prima erano 1 500 a ogni disegno.
 */
private const val MaxVisibleMarkers = 40L
