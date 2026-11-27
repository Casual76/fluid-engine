package dev.antigravity.fluidengine.sample.novita

import android.content.ComponentName
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.widget.FluidWidgetEntry
import dev.antigravity.fluidengine.widget.FluidWidgetPreview
import dev.antigravity.fluidengine.widget.FluidWidgetPreviewBox
import dev.antigravity.fluidengine.widget.FluidWidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/**
 * F8.25c · Le due correzioni dopo il Tab S9.
 *
 * 1. **L'anteprima per il launcher.** Il foglio di One UI «Aggiungere alla schermata Home?» scriveva «Aggiunta del widget
 *    non riuscita» al posto del widget: ogni widget di prova ora ha `previewLayout` (da Android 12) e `previewImage`
 *    (prima), e il layout si gonfia come `RemoteViews`, cioè come lo gonfia il launcher.
 * 2. **La taglia piccola.** Sul tablet il 2×2 troncava i nomi («Mat…», «Itali…»). Alla taglia piccola le righe ora si
 *    impilano come i widget piccoli di iOS: il nome ha tutta la riga e l'orario va sotto. La prova compone il widget vero
 *    (le `RemoteViews` di Glance, come nella vetrina) alle taglie vere di un 2×2 su un telefono e misura il testo: nessun
 *    nome troncato.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class F8p25WidgetSmallTest {

  @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

  private val context get() = rule.activity

  private fun providerAttribute(xml: Int, name: String): Int {
    val parser = context.resources.getXml(xml)
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
        return parser.getAttributeResourceValue("http://schemas.android.com/apk/res/android", name, 0)
      }
    }
    return 0
  }

  @Test
  fun ogniWidgetDiProvaHaLAnteprimaPerIlLauncher() {
    val widgets = listOf(
      Triple(R.xml.f8p25_widget_agenda, R.layout.f8p25_widget_agenda_preview, R.drawable.f8p25_widget_agenda_preview_image),
      Triple(R.xml.f8p25_widget_absences, R.layout.f8p25_widget_absences_preview, R.drawable.f8p25_widget_absences_preview_image),
    )
    for ((xml, layout, image) in widgets) {
      assertEquals(layout, providerAttribute(xml, "previewLayout"))
      assertEquals(image, providerAttribute(xml, "previewImage"))
      assertNotNull(ResourcesCompat.getDrawable(context.resources, image, context.theme))
    }
  }

  @Test
  fun leAnteprimeSiGonfianoComeRemoteViews() {
    // RemoteViews rifiuta le viste che il launcher non puo' gonfiare: se passa qui, passa anche nel foglio di One UI.
    val agenda = inflate(R.layout.f8p25_widget_agenda_preview)
    assertEquals(listOf("Oggi", "Matematica", "8:00", "Italiano", "9:00"), texts(agenda))
    val absences = inflate(R.layout.f8p25_widget_absences_preview)
    assertEquals(listOf("Assenze", "18 %", "Limite 25 %"), texts(absences))
    // F8.25d: ogni testo dell'anteprima sta su una riga e si taglia coi puntini, come i testi di Glance.
    for (view in textViews(agenda) + textViews(absences)) {
      assertEquals("«${view.text}»", TextUtils.TruncateAt.END, view.ellipsize)
      assertEquals("«${view.text}»", 1, view.maxLines)
    }
  }

  /**
   * F8.25d · Nella cella stretta del formato telefono simulato sul Tab S9 (il 2×2 della griglia del tablet, circa 90 dp)
   * «Matematica» non ci sta: si taglia **coi puntini** («Matem…»), sia nell'anteprima del launcher sia nel widget vero,
   * mai a meta' lettera senza segno («Matem»).
   */
  @Test
  fun inUnaCellaStrettaIlNomeSiTagliaCoiPuntini() {
    val density = context.resources.displayMetrics.density
    val preview = inflate(R.layout.f8p25_widget_agenda_preview)
    val width = View.MeasureSpec.makeMeasureSpec((NarrowCellDp * density).toInt(), View.MeasureSpec.EXACTLY)
    val height = View.MeasureSpec.makeMeasureSpec((158 * density).toInt(), View.MeasureSpec.EXACTLY)
    preview.measure(width, height)
    preview.layout(0, 0, preview.measuredWidth, preview.measuredHeight)
    val previewName = textViews(preview).first { it.text.toString() == "Matematica" }
    assertTrue("anteprima: «Matematica» senza puntini", (previewName.layout?.getEllipsisCount(0) ?: 0) > 0)

    val narrow = FluidWidgetSize("cella stretta", NarrowCellDp.dp, 158.dp, 2, 2)
    val entry = FluidWidgetEntry(
      provider = ComponentName(context, F8p25AgendaWidgetReceiver::class.java),
      title = "Agenda",
      description = "",
      preview = FluidWidgetPreview.Glance { F8p25AgendaContent(context) },
    )
    rule.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.width(400.dp)) { FluidWidgetPreviewBox(entry, narrow, contentDescription = "Agenda") }
      }
    }
    rule.waitForIdle()
    rule.waitUntil(5_000) { frame() != null }
    val widgetName = textViews(frame()!!).first { it.text.toString() == "Matematica" }
    assertEquals(TextUtils.TruncateAt.END, widgetName.ellipsize)
    assertTrue("widget: «Matematica» senza puntini", (widgetName.layout?.getEllipsisCount(0) ?: 0) > 0)
  }

  private fun inflate(layout: Int): View {
    val parent = FrameLayout(context)
    val view = RemoteViews(context.packageName, layout).apply(context, parent)
    parent.addView(view)
    return parent
  }

  /**
   * Le taglie vere di un 2×2 su un telefono: la piu' stretta di Android (130 dp, griglia a 5 colonne: (73·2 − 16) dp), il
   * piccolo di iPhone (158 × 158 pt) e quella che One UI dava sul Tab S9 (178 × 182 dp, da `dumpsys appwidget`).
   */
  private val smallSizes = listOf(
    FluidWidgetSize("2×2 stretto", 130.dp, 158.dp, 2, 2),
    FluidWidgetSize.Small,
    FluidWidgetSize("2×2 One UI", 178.dp, 182.dp, 2, 2),
  )

  @Test
  fun allaTagliaPiccolaINomiDelleMaterieSonoInteri() {
    var size by androidx.compose.runtime.mutableStateOf(smallSizes.first())
    val entry = FluidWidgetEntry(
      provider = ComponentName(context, F8p25AgendaWidgetReceiver::class.java),
      title = "Agenda",
      description = "",
      preview = FluidWidgetPreview.Glance { F8p25AgendaContent(context) },
    )
    rule.setContent {
      FluidTheme(EngineSettings()) {
        Box(Modifier.width(400.dp)) {
          androidx.compose.runtime.key(size) { FluidWidgetPreviewBox(entry, size, contentDescription = "Agenda") }
        }
      }
    }
    for (s in smallSizes) {
      size = s
      rule.waitForIdle()
      rule.waitUntil(5_000) { frame() != null }
      val frame = frame()!!
      val shown = texts(frame)
      assertTrue("${s.label}: niente lezioni in ${shown}", "Matematica" in shown)
      for (view in textViews(frame)) {
        val text = view.text.toString()
        val room = view.width - view.totalPaddingLeft - view.totalPaddingRight
        val needed = view.paint.measureText(text)
        assertTrue("${s.label}: «$text» troncato ($needed px su $room)", needed <= room + 1f)
        assertEquals("${s.label}: «$text» coi puntini", 0, view.layout?.getEllipsisCount(0) ?: 0)
      }
      if (s.width < 170.dp) {
        // Impilato: l'orario sta sotto il nome, non accanto.
        val name = textViews(frame).first { it.text.toString() == "Matematica" }
        val time = textViews(frame).first { it.text.toString() == "8:00" }
        assertTrue("${s.label}: l'orario non e' sotto il nome", top(time) >= top(name) + name.height - 1)
        assertNotEquals(0, name.width)
      }
    }
  }

  private fun frame(): ViewGroup? {
    var found: ViewGroup? = null
    fun walk(v: View) {
      if (found != null) return
      if (v.javaClass.simpleName == "PreviewFrame" && (v as ViewGroup).childCount > 0 && v.width > 0) found = v
      if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }
    walk(rule.activity.window.decorView)
    return found
  }

  private fun textViews(root: View): List<TextView> {
    val out = ArrayList<TextView>()
    fun walk(v: View) {
      if (v is TextView && v.visibility == View.VISIBLE && v.text.isNotEmpty()) out += v
      if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }
    walk(root)
    return out
  }

  private fun texts(root: View): List<String> = textViews(root).map { it.text.toString() }

  private fun top(v: View): Int = IntArray(2).also { v.getLocationInWindow(it) }[1]

  private companion object {
    /** Il 2×2 del Tab S9 nel formato telefono simulato: 240 pixel a 420 dpi, cioe' circa 91 dp (cattura del 26/11/2026). */
    const val NarrowCellDp = 90
  }
}
