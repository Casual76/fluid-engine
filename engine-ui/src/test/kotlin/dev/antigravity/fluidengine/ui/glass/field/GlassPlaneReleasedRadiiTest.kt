package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * f3-rifiniture, la testa piatta al colmo (S5 difetto 6, f3-corpo §6): nella scena `bordo` «Preferiti» nasce da «Modifica»
 * verso il bordo di un contenitore stretto, e al colmo del superamento la sua testa era un lato quasi diritto per ~100 ms.
 *
 * Il registro `PALCO` del tablet lo spiega: la lastra al colmo è 113,8 × 113,8 px col raggio 56,1, cioè 0,8 px sotto mezzo
 * lato corto. Chi nasce esce dal donatore col raggio d'arrivo, e quel canale non ha strada da fare mentre i lati superano
 * l'arrivo. Il campo rende una forma continua piccola che non è una capsula al raggio **equivalente** (per 0,82,
 * `glassSlabGeometry`): da un cerchio a un quadrato arrotondato per meno di un pixel. Sul banco la testa era tonda perché le
 * tracce hanno il posto del palco uguale al contenitore, e lì la testa non supera.
 *
 * La geometria è quella del Tab S9 in pixel del contenitore (dal registro `FINE`): tondi da 94 px a 17 px fra i vetri,
 * spacing 34 px, il contenitore che finisce col donatore, la finestra come posto del palco.
 */
class GlassPlaneReleasedRadiiTest {
  private val window = Rect(-40f, -700f, 1560f, 1860f)
  private fun round(key: String, left: Float, radius: Float = 47f) =
    GlassPlanePiece(key, Rect(left, 4f, left + 94f, 98f), FluidCornerRadii.all(radius), id = key)
  private fun state(vararg pieces: GlassPlanePiece) = GlassPlaneState(
    pieces.toList(), 34f, Rect(0f, 0f, pieces.maxOf { it.bounds.right } + 4f, 102f), room = window,
  )
  private fun GlassPlaneFrame.of(key: Any) = pieces.single { it.flightId.targetKey == key }
  private val back = round("indietro", 4f)
  private val edit = round("modifica", 115f)
  private val star = round("preferiti", 226f)

  /** La forma che il campo dà alla lastra di [frame], come la vede `fluidPhysics` (forma continua di serie, «piccolo»). */
  private fun fieldRadius(frame: GlassPlanePieceFrame): Float {
    val b = frame.bounds
    val r = frame.radii
    return glassSlabGeometry(
      b.width, b.height, r.topLeft, r.topRight, r.bottomRight, r.bottomLeft,
      smoothing = ContinuousCornerShape.IosSmoothing, continuous = true, small = glassSlabIsSmall(b.maxDimension),
    ).topRight
  }

  @Test fun aRoundBornTowardTheEdgeStaysRoundAtThePeak() {
    val journey = GlassPlaneJourney(state(back, edit), state(back, edit, star))
    assertEquals(GlassPlaneChange.BornFromNeighbour, journey.frame(0.0).of("preferiti").change)
    var biggest = 0f
    for (ms in 0..(journey.durationMs.toInt() + 1)) {
      val born = journey.frame(ms.toDouble()).of("preferiti")
      val half = born.bounds.minDimension / 2f
      biggest = maxOf(biggest, born.bounds.minDimension)
      for (radius in listOf(born.radii.topLeft, born.radii.topRight, born.radii.bottomRight, born.radii.bottomLeft)) {
        assertEquals("t=$ms ms: ${born.bounds} r=${born.radii}", half, radius, half * 1e-4f)
      }
      // E il campo la rende tonda: mezzo lato, non il raggio equivalente di un piccolo.
      assertEquals("t=$ms ms", half, fieldRadius(born), half * 1e-3f)
    }
    // La prova ha senso solo se il nato supera la sua misura (fuori dal contenitore stretto, fino alla finestra).
    assertTrue("al colmo ${biggest} px", biggest > 94f + 1.2f)
  }

  @Test fun theSwollenPeakIsStillACapsuleForTheField() {
    // Al colmo la lastra si gonfia ancora (rigonfiamento proprio di chi nasce): raggio e lati crescono insieme, e la
    // capsula resta capsula. Prima la lastra gonfia era 113,8 px col raggio 56,1 e il campo la rendeva a 46.
    val journey = GlassPlaneJourney(state(back, edit), state(back, edit, star))
    val peak = (0..journey.durationMs.toInt()).maxBy { journey.frame(it.toDouble()).of("preferiti").bounds.minDimension }
    val born = journey.frame(peak.toDouble()).of("preferiti")
    val swollen = glassJourneySwollen(FluidForm.Slab(born.bounds, born.radii, ContinuousCornerShape.IosSmoothing), 8f)
    val f = swollen.frame
    val r = swollen.cornerRadii
    val geometry = glassSlabGeometry(f.width, f.height, r.topLeft, r.topRight, r.bottomRight, r.bottomLeft,
      smoothing = ContinuousCornerShape.IosSmoothing, continuous = true, small = glassSlabIsSmall(f.maxDimension))
    assertEquals(f.minDimension / 2f, geometry.topLeft, 1e-3f)
    assertEquals(0f, geometry.smoothing, 0f)
  }

  @Test fun aRoundBornFromAWiderCapsuleStaysRound() {
    // `due-nati`: il donatore è una capsula più alta (raggio 51) e il nato un tondo da 94 px. Il canale dei raggi parte
    // dal raggio del donatore e la sua molla scende **sotto** 47 mentre i lati superano l'arrivo: la stessa testa piatta.
    val donor = GlassPlanePiece("seleziona", Rect(111f, 0f, 291f, 102f), FluidCornerRadii.all(51f), id = "seleziona")
    val moved = GlassPlanePiece("seleziona", Rect(222f, 0f, 402f, 102f), FluidCornerRadii.all(51f), id = "seleziona")
    val born = round("condividi", 115f)
    val journey = GlassPlaneJourney(state(back, donor), state(back, born, moved))
    assertEquals(GlassPlaneChange.BornFromNeighbour, journey.frame(0.0).of("condividi").change)
    for (ms in 0..(journey.durationMs.toInt() + 1)) {
      val frame = journey.frame(ms.toDouble()).of("condividi")
      val half = frame.bounds.minDimension / 2f
      assertEquals("t=$ms ms: ${frame.bounds} r=${frame.radii}", half, frame.radii.topLeft, half * 1e-4f)
    }
  }

  @Test fun aRoundedRectBornKeepsTheRadiiOfItsSpring() {
    // Un vetro che arriva con angoli più stretti di mezzo lato (12 px su 94) non è una capsula: il campo lo rende sempre al
    // raggio equivalente, non c'è un salto da evitare, e i suoi raggi restano quelli della molla (come prima).
    val square = round("preferiti", 226f, radius = 12f)
    val journey = GlassPlaneJourney(state(back, edit), state(back, edit, square))
    val late = journey.frame(journey.durationMs * 0.6).of("preferiti")
    assertTrue("${late.radii} su ${late.bounds}", late.radii.topLeft < late.bounds.minDimension / 2f - 10f)
    assertEquals(square.bounds, journey.frame(journey.durationMs + 0.01).of("preferiti").bounds)
    assertEquals(square.radii, journey.frame(journey.durationMs + 0.01).of("preferiti").radii)
  }
}
