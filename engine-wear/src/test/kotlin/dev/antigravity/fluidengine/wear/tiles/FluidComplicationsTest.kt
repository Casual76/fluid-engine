package dev.antigravity.fluidengine.wear.tiles

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.testing.TestTileClient
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import dev.antigravity.fluidengine.wear.sync.FluidWearFormat
import dev.antigravity.fluidengine.wear.sync.FluidWearPaths
import dev.antigravity.fluidengine.wear.sync.FluidWearRole
import dev.antigravity.fluidengine.wear.sync.FluidWearStatus
import dev.antigravity.fluidengine.wear.sync.FluidWearSync
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * F8.25 · Le complicazioni Fluid (testo breve, testo lungo, intervallo con la rampa dell'accento, lo stato vecchio che non
 * mente) e la Tile di uno stato del telefono servita davvero da `Material3TileService` (col client di prova di Tiles):
 * prima «In attesa», poi lo stato che il ponte ha tenuto, letto dal disco come farebbe la Tile ad app chiusa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidComplicationsTest {

  private val resources = RuntimeEnvironment.getApplication().resources
  private val status = FluidWearStatus(
    key = "lezione",
    title = "Matematica",
    value = "10:00",
    detail = "Aula 3",
    progress = 0.25f,
    updatedAtMillis = 1_000,
    staleAfterMillis = 10_000,
  )

  private fun ComplicationText.read(): String = getTextAt(resources, Instant.EPOCH).toString()

  @Test
  fun `testo breve, testo lungo e intervallo dallo stesso stato`() {
    val colors = fluidTileColors()
    val short = FluidComplications.dataFor(ComplicationType.SHORT_TEXT, status, 2_000, colors) as ShortTextComplicationData
    assertEquals("10:00", short.text.read())
    assertEquals("Matematica", short.title?.read())
    assertEquals("Matematica. 10:00. Aula 3", short.contentDescription?.read())

    val long = FluidComplications.dataFor(ComplicationType.LONG_TEXT, status, 2_000, colors) as LongTextComplicationData
    assertEquals("10:00 · Aula 3", long.text.read())

    val ranged = FluidComplications.dataFor(ComplicationType.RANGED_VALUE, status, 2_000, colors) as RangedValueComplicationData
    assertEquals(0.25f, ranged.value, 0f)
    assertEquals(0f, ranged.min, 0f)
    assertEquals(1f, ranged.max, 0f)
    assertEquals(listOf(colors.argb.accentDim, colors.argb.accent), ranged.colorRamp!!.colors.toList())
    assertTrue(ranged.colorRamp!!.interpolated)
  }

  @Test
  fun `il testo breve sta nei sette caratteri dei quadranti`() {
    val long = status.copy(value = "Ricevimento")
    val short = FluidComplications.dataFor(ComplicationType.SHORT_TEXT, long, 2_000) as ShortTextComplicationData
    assertEquals("Ricevim", short.text.read())
  }

  @Test
  fun `uno stato vecchio non mente`() {
    val short = FluidComplications.dataFor(ComplicationType.SHORT_TEXT, status, 20_000) as ShortTextComplicationData
    assertEquals("—", short.text.read())
    val long = FluidComplications.dataFor(ComplicationType.LONG_TEXT, status, 20_000) as LongTextComplicationData
    assertEquals(FluidTileContent.StaleDetail, long.text.read())
    val ranged = FluidComplications.dataFor(ComplicationType.RANGED_VALUE, status, 20_000) as RangedValueComplicationData
    assertEquals(0f, ranged.value, 0f)
  }

  @Test
  fun `senza avanzamento l'intervallo non c'e', e i tipi che non sa dare neanche`() {
    val noProgress = status.copy(progress = null)
    assertTrue(FluidComplications.dataFor(ComplicationType.RANGED_VALUE, noProgress, 0) is NoDataComplicationData)
    assertTrue(FluidComplications.dataFor(ComplicationType.SMALL_IMAGE, status, 0) is NoDataComplicationData)
    for (type in FluidComplications.SupportedTypes) {
      assertEquals(type, FluidComplications.preview(type).type)
    }
  }

  /** La Tile della prova: lo stato «lezione» del ponte. */
  class LessonTile : FluidStatusTileService(key = "lezione")

  @Test
  fun `la Tile dello stato dice In attesa, poi mostra lo stato tenuto dal ponte`() {
    // Il client di prova attacca da se' il contesto al servizio: gli si passa un servizio appena costruito.
    val client = TestTileClient(LessonTile()) { it.run() }
    val device = androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters.Builder()
      .setScreenWidthDp(225)
      .setScreenHeightDp(225)
      .setScreenDensity(2f)
      .setFontScale(1f)
      .setScreenShape(androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
      .build()
    val request = RequestBuilders.TileRequest.Builder().setDeviceConfiguration(device).build()

    fun texts(): List<String> {
      val future = client.requestTile(request)
      // La risposta passa dal filo principale (la coroutine di Material3TileService): lo si fa girare mentre si aspetta.
      val deadline = System.currentTimeMillis() + 10_000
      while (!future.isDone && System.currentTimeMillis() < deadline) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(5)
      }
      val tile = future.get(1, TimeUnit.SECONDS)
      val root = tile.tileTimeline!!.timelineEntries.single().layout!!.root!!
      return Texts().also { it.visit(root) }.all
    }

    assertTrue("prima del telefono: ${texts()}", "In attesa" in texts())

    // Lo stato arriva dal telefono (come farebbe FluidWearListenerService) e si tiene su disco.
    val sync = FluidWearSync.get(RuntimeEnvironment.getApplication(), FluidWearRole.Watch)
    val fresh = status.copy(updatedAtMillis = System.currentTimeMillis(), staleAfterMillis = null)
    sync.route(FluidWearPaths.status("lezione"), FluidWearFormat.encode(fresh))
    val after = texts()
    assertTrue("dopo: $after", "10:00" in after && "Matematica" in after && "Aula 3" in after)
  }

  private class Texts {
    val all = ArrayList<String>()

    fun visit(e: androidx.wear.protolayout.LayoutElementBuilders.LayoutElement) {
      when (e) {
        is androidx.wear.protolayout.LayoutElementBuilders.Box -> e.contents.forEach(::visit)
        is androidx.wear.protolayout.LayoutElementBuilders.Column -> e.contents.forEach(::visit)
        is androidx.wear.protolayout.LayoutElementBuilders.Row -> e.contents.forEach(::visit)
        is androidx.wear.protolayout.LayoutElementBuilders.Text -> e.text?.value?.let { all += it }
        else -> Unit
      }
    }
  }
}
