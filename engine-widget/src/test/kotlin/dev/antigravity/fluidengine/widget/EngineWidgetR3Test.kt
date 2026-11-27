package dev.antigravity.fluidengine.widget

import android.content.ComponentName
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt

/**
 * R3 · Le correzioni della review R3 su engine-widget: il budget delle righe col carattere grande di sistema (15), e le
 * difese della vetrina e delle anteprime (23: le taglie che calano a pager vivo, un contenuto che lancia, i parametri
 * senza senso).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EngineWidgetR3Test {

  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val context get() = RuntimeEnvironment.getApplication()

  // --- 15 · il budget delle righe e la grandezza del carattere ----------------------------------------------------------

  private val lessons = listOf("Matematica" to "8:00", "Italiano" to "9:00", "Inglese" to "10:00", "Storia" to "11:00")

  /** L'agenda della galleria (F8p25AgendaContent): intestazione, le righe che il budget concede, le pillole in fondo. */
  @Composable
  private fun Agenda() {
    val size = LocalSize.current
    val palette = engineWidgetPalette(context, EngineSettings())
    val layout = resolveEngineWidgetLayout(size, hasFooter = size.width >= 240.dp)
    EngineWidgetSurface(palette, layout) {
      EngineWidgetHeader(title = "Oggi", subtitle = "4 lezioni", palette = palette, layout = layout)
      Spacer(GlanceModifier.height(EngineWidgetMetrics.Gap))
      EngineWidgetGroup(palette) {
        val shown = lessons.take(layout.rowLimit)
        for (i in shown.indices) {
          if (i > 0) EngineWidgetHairline(palette, layout)
          EngineWidgetRow(title = shown[i].first, subtitle = "Aula 3", trailing = shown[i].second, palette = palette, layout = layout)
        }
      }
      if (layout.showFooter) {
        Spacer(GlanceModifier.height(EngineWidgetMetrics.Gap))
        Row(GlanceModifier.fillMaxWidth()) {
          EngineWidgetPill("2", "Compiti", palette.primaryTone, GlanceModifier.defaultWeight(), compact = layout.compact)
          Spacer(GlanceModifier.width(8.dp))
          EngineWidgetPill("1", "Verifica", palette.warningTone, GlanceModifier.defaultWeight(), compact = layout.compact)
        }
      }
    }
  }

  /** Le RemoteViews vere di [size], applicate come il launcher in una cella di quella misura esatta. */
  @OptIn(ExperimentalGlanceRemoteViewsApi::class)
  private fun cell(size: DpSize): FrameLayout {
    val views = runBlocking { GlanceRemoteViews().compose(context, size) { Agenda() }.remoteViews }
    val frame = FrameLayout(context)
    frame.addView(views.apply(context, frame))
    val density = context.resources.displayMetrics.density
    val w = (size.width.value * density).roundToInt()
    val h = (size.height.value * density).roundToInt()
    frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
    frame.layout(0, 0, w, h)
    return frame
  }

  private fun texts(v: View, out: MutableList<TextView> = ArrayList()): List<TextView> {
    if (v is TextView && v.visibility == View.VISIBLE && v.text.isNotEmpty()) out += v
    if (v is ViewGroup) for (i in 0 until v.childCount) texts(v.getChildAt(i), out)
    return out
  }

  private fun bottomIn(frame: View, v: View): Int {
    var y = v.bottom
    var p = v.parent
    while (p is View && p !== frame) {
      y += p.top
      p = p.parent
    }
    return y
  }

  /**
   * Nessuna scritta della cella e' tagliata: ogni testo e' alto quanto le sue righe e finisce dentro la cella (il
   * launcher taglia senza dirlo, a meta' riga).
   */
  private fun assertNothingCut(scale: Float) {
    for (size in FluidWidgetSize.All) {
      val frame = cell(size.dpSize)
      val all = texts(frame)
      assertTrue("${size.label}: ci sono scritte", all.isNotEmpty())
      for (t in all) {
        val needed = t.layout.height + t.compoundPaddingTop + t.compoundPaddingBottom
        val bottom = bottomIn(frame, t)
        assertTrue("${size.label} a $scale: «${t.text}» alta ${t.height} px su $needed", t.height + 1 >= needed)
        assertTrue("${size.label} a $scale: «${t.text}» finisce a $bottom px, la cella a ${frame.height}", bottom <= frame.height + 1)
      }
    }
  }

  @Test
  fun `a carattere normale nessuna riga e' tagliata`() = assertNothingCut(1f)

  @Test
  @Config(fontScale = 1.3f)
  fun `col carattere a 1,3 nessuna riga e' tagliata`() {
    assertEquals(1.3f, context.resources.configuration.fontScale, 1e-3f)
    assertEquals("la scala del sistema e' quella del telefono", 1.3f, Resources.getSystem().configuration.fontScale, 1e-3f)
    assertNothingCut(1.3f)
  }

  @Test
  @Config(fontScale = 2f)
  fun `col carattere a 2 nessuna riga e' tagliata`() {
    assertEquals(2f, context.resources.configuration.fontScale, 1e-3f)
    assertNothingCut(2f)
  }

  @Test
  fun `il budget a carattere grande - meno righe, mai la cella vuota, e a 1 non cambia niente`() {
    // A 1× il budget e' quello di sempre (EngineWidgetLayoutTest).
    assertEquals(2, resolveEngineWidgetLayout(DpSize(158.dp, 158.dp), fontScale = 1f).rowLimit)
    assertEquals(4, resolveEngineWidgetLayout(DpSize(338.dp, 354.dp), hasFooter = true, fontScale = 1f).rowLimit)
    assertEquals("sotto 1× valgono le misure di 1×", 2, resolveEngineWidgetLayout(DpSize(158.dp, 158.dp), fontScale = 0.85f).rowLimit)
    // A 1,3×: il piccolo impilato tiene una riga, il grande meno di quattro col piede (quante, lo dice il carattere
    // del telefono: qui quello di Robolectric).
    val small = resolveEngineWidgetLayout(DpSize(158.dp, 158.dp), fontScale = 1.3f)
    assertTrue(small.stacked)
    assertEquals(1, small.rowLimit)
    val large = resolveEngineWidgetLayout(DpSize(338.dp, 354.dp), hasFooter = true, fontScale = 1.3f)
    assertTrue(large.showFooter)
    assertTrue("${large.rowLimit}", large.rowLimit in 2..3)
    // A 2× il medio non tiene nemmeno una riga normale: prende la forma compatta, con una riga intera.
    val medium = resolveEngineWidgetLayout(DpSize(338.dp, 158.dp), hasFooter = true, fontScale = 2f)
    assertTrue(medium.compact)
    assertEquals(1, medium.rowLimit)
    assertTrue(!medium.showFooter)
  }

  @Test
  fun `un tetto di righe sotto uno si rifiuta con un messaggio`() {
    val e = assertThrows(IllegalArgumentException::class.java) {
      resolveEngineWidgetLayout(DpSize(300.dp, 300.dp), maxRows = 0)
    }
    assertTrue("${e.message}", e.message.orEmpty().contains("maxRows"))
  }

  @Test
  fun `una taglia senza misura si rifiuta quando nasce`() {
    assertThrows(IllegalArgumentException::class.java) { FluidWidgetSize("Rotta", (-1).dp, 158.dp, 2, 2) }
    assertThrows(IllegalArgumentException::class.java) { FluidWidgetSize("Rotta", 158.dp, 0.dp, 2, 2) }
    assertThrows(IllegalArgumentException::class.java) { FluidWidgetSize("Rotta", 158.dp, 158.dp, 0, 2) }
  }

  // --- 23 · la vetrina e le anteprime ----------------------------------------------------------------------------------

  private val provider by lazy { ComponentName(context, "dev.prova.AgendaWidget") }

  @Test
  fun `le taglie che calano a pager vivo non fanno cadere la scheda`() {
    val three = FluidWidgetEntry(provider, "Agenda", "Gli impegni di oggi.", FluidWidgetPreview.Glance { Agenda() })
    var entry by mutableStateOf(three)
    rule.setContent {
      FluidTheme(EngineSettings()) {
        FluidMotionPolicyProvider(FluidMotionPolicy(durationScale = 0f)) {
          FluidWidgetShowcaseCard(entry, appName = "Pampa Widgets")
        }
      }
    }
    rule.waitForIdle()
    val card = rule.onNodeWithTag(FluidWidgetShowcaseTags.card("Agenda"))
    // Alla terza taglia (la grande), con le azioni di TalkBack.
    repeat(2) {
      val next = card.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == FluidWidgetShowcaseStrings().nextSize }
      rule.runOnUiThread { next.action() }
      rule.waitForIdle()
    }
    rule.onNodeWithText(FluidWidgetShowcaseStrings().sizeOf("Grande", 4, 4)).assertExists()
    // Lo stesso widget, ma l'app ora ne offre una taglia sola: prima `entry.sizes[2]` cadeva.
    entry = three.copy(sizes = listOf(FluidWidgetSize.Small))
    rule.waitForIdle()
    rule.onNodeWithText(FluidWidgetShowcaseStrings().sizeOf("Piccolo", 2, 2)).assertExists()
  }

  @Test
  fun `un contenuto che lancia lascia l'anteprima vuota, non fa cadere la pagina`() {
    val broken = FluidWidgetEntry(provider, "Rotto", "Legge uno stato che in vetrina non c'e'.", FluidWidgetPreview.Glance { error("stato assente") })
    val remote = FluidWidgetEntry(
      ComponentName(context, "dev.prova.Altro"), "Remoto", "Le RemoteViews dell'app.",
      FluidWidgetPreview.Remote { _, _ -> throw IllegalStateException("layout assente") },
    )
    rule.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.width(400.dp)) {
          androidx.compose.foundation.layout.Column {
            FluidWidgetPreviewBox(broken, FluidWidgetSize.Medium, contentDescription = "Rotto medio")
            FluidWidgetPreviewBox(remote, FluidWidgetSize.Small, contentDescription = "Remoto piccolo")
          }
        }
      }
    }
    rule.waitForIdle()
    // La pagina c'e', e TalkBack legge le due anteprime (vuote).
    for ((tag, said) in listOf("Medio" to "Rotto medio", "Piccolo" to "Remoto piccolo")) {
      val node = rule.onNodeWithTag(FluidWidgetShowcaseTags.preview(tag)).fetchSemanticsNode()
      assertEquals(listOf(said), node.config.getOrNull(SemanticsProperties.ContentDescription))
    }
  }
}
