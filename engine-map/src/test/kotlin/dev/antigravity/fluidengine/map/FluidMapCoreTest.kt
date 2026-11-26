package dev.antigravity.fluidengine.map

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * F8.26 · Il codice puro della mappa: la proiezione di Mercatore (quella di MapLibre), i movimenti del dito, i grappoli
 * (che non perdono nessun segnaposto, sono deterministici e si dividono dove promettono) e le coordinate scritte.
 */
class FluidMapCoreTest {
  private val florence = FluidGeoPoint(43.77310, 11.25560)

  @Test
  fun `la proiezione mette l'equatore e Greenwich al centro e i bordi del quadrato dove dice MapLibre`() {
    assertEquals(0.5, FluidMapProjection.x(0.0), 1e-12)
    assertEquals(0.5, FluidMapProjection.y(0.0), 1e-12)
    assertEquals(0.0, FluidMapProjection.y(FluidMapProjection.MaxLatitude), 1e-9)
    assertEquals(1.0, FluidMapProjection.y(-FluidMapProjection.MaxLatitude), 1e-9)
    assertEquals(0.0, FluidMapProjection.y(89.9), 1e-9) // fermata al bordo
    // A zoom 0 il mondo e' 512 dp, a zoom 3 4096.
    assertEquals(512.0, FluidMapProjection.worldSize(0.0), 0.0)
    assertEquals(4096.0, FluidMapProjection.worldSize(3.0), 0.0)
    // Andata e ritorno.
    for (lat in listOf(-80.0, -33.86, 0.0, 43.7731, 85.0)) assertEquals(lat, FluidMapProjection.latitude(FluidMapProjection.y(lat)), 1e-9)
    assertEquals(-179.0, FluidMapProjection.longitude(FluidMapProjection.x(181.0)), 1e-9)
  }

  @Test
  fun `dallo schermo alla Terra e ritorno anche con la mappa ruotata`() {
    for (bearing in listOf(0.0, 37.0, 180.0, -95.0)) {
      val p = FluidMapProjector(FluidMapCamera(florence, 15.3, bearing), 1080f, 2340f, 2.625f)
      assertEquals(Offset(540f, 1170f), p.toScreen(florence))
      for (screen in listOf(Offset(0f, 0f), Offset(1080f, 2340f), Offset(200f, 1900f))) {
        val geo = p.toGeo(screen)
        val back = p.toScreen(geo)
        assertEquals(screen.x, back.x, 0.01f)
        assertEquals(screen.y, back.y, 0.01f)
      }
    }
  }

  @Test
  fun `col nord a est un punto a est sta in alto`() {
    val p = FluidMapProjector(FluidMapCamera(florence, 14.0, 90.0), 1000f, 1000f, 1f)
    val east = p.toScreen(FluidGeoPoint(florence.latitude, florence.longitude + 0.01))
    assertEquals(500f, east.x, 0.5f)
    assertTrue("a est e' in alto: ${east.y}", east.y < 500f)
  }

  @Test
  fun `trascinare porta il punto sotto il dito con il dito, e lo zoom tiene fermo il punto fra le dita`() {
    val camera = FluidMapCamera(florence, 14.0, 20.0)
    val p = FluidMapProjector(camera, 1080f, 2340f, 3f)
    val under = p.toGeo(Offset(300f, 800f))
    val moved = FluidMapProjector(p.panned(Offset(120f, -340f)), 1080f, 2340f, 3f)
    val nowAt = moved.toScreen(under)
    assertEquals(420f, nowAt.x, 0.05f)
    assertEquals(460f, nowAt.y, 0.05f)

    val focus = Offset(700f, 1500f)
    val anchor = p.toGeo(focus)
    val zoomed = FluidMapProjector(p.transformed(focus, 1.4, 25.0, 1.0, 19.0), 1080f, 2340f, 3f)
    assertEquals(15.4, zoomed.camera.zoom, 1e-9)
    assertEquals(45.0, zoomed.camera.bearing, 1e-9)
    val stays = zoomed.toScreen(anchor)
    assertEquals(focus.x, stays.x, 0.05f)
    assertEquals(focus.y, stays.y, 0.05f)
    // Lo zoom si ferma ai limiti.
    assertEquals(19.0, p.transformed(focus, 10.0, 0.0, 1.0, 19.0).zoom, 0.0)
  }

  @Test
  fun `mostrare tutti i luoghi li mette dentro lo schermo col margine`() {
    val points = listOf(FluidGeoPoint(43.70, 11.10), FluidGeoPoint(43.85, 11.40), FluidGeoPoint(43.76, 11.25))
    val bounds = FluidGeoBounds.of(points)!!
    val camera = FluidMapProjection.fit(bounds, 400.0, 800.0, paddingDp = 40.0)
    val p = FluidMapProjector(camera, 400f, 800f, 1f)
    for (point in points) {
      val s = p.toScreen(point)
      assertTrue("$s", s.x >= 39.5f && s.x <= 360.5f && s.y >= 39.5f && s.y <= 760.5f)
    }
    // Un punto solo non diventa una tessera a zoom 22.
    assertEquals(16.0, FluidMapProjection.fit(FluidGeoBounds.of(listOf(florence))!!, 400.0, 800.0).zoom, 0.0)
  }

  @Test
  fun `la bussola gira per la strada breve e il volo tiene fermo l'arrivo`() {
    assertEquals(360.0, nearestNorth(350.0), 0.0)
    assertEquals(0.0, nearestNorth(10.0), 0.0)
    assertEquals(-360.0, nearestNorth(-190.0), 0.0)
    assertFalse(FluidMapCamera(florence, 12.0, 0.3).isRotated)
    assertTrue(FluidMapCamera(florence, 12.0, 359.0).isRotated)
    assertFalse(FluidMapCamera(florence, 12.0, 359.8).isRotated)
    assertEquals(0.0, scaleProgress(10.0, 14.0, 0.0), 1e-12)
    assertEquals(1.0, scaleProgress(10.0, 14.0, 1.0), 1e-12)
    // Avvicinando il centro corre prima (la scala e' piccola), allontanando dopo.
    assertTrue(scaleProgress(10.0, 14.0, 0.5) > 0.5)
    assertTrue(scaleProgress(14.0, 10.0, 0.5) < 0.5)
  }

  private fun markers(n: Int, seed: Int = 7): List<FluidMapMarker> {
    val r = Random(seed)
    return List(n) { i ->
      FluidMapMarker("m$i", FluidGeoPoint(43.6 + r.nextDouble() * 0.4, 11.0 + r.nextDouble() * 0.6), "Luogo $i", count = 1 + r.nextInt(4))
    }
  }

  @Test
  fun `i grappoli non perdono ne' raddoppiano niente a nessun livello`() {
    val list = markers(400)
    val total = list.sumOf { it.count }
    val index = FluidMapClusterIndex.build(list)
    for (level in index.minZoom..index.maxZoom) {
      val clusters = index.clusters(level)
      assertEquals("livello $level", total, clusters.sumOf { it.count })
      assertEquals("livello $level", list.size, clusters.flatMap { it.markers }.map { it.id }.toSet().size)
    }
    // Al livello piu' alto ognuno e' solo; a zoom 3 Firenze e' un grappolo solo.
    assertTrue(index.clusters(index.maxZoom).all { it.isSingle })
    assertEquals(1, index.clusters(3).size)
  }

  @Test
  fun `i grappoli sono gli stessi in qualunque ordine arrivino i segnaposto`() {
    val list = markers(300)
    val a = FluidMapClusterIndex.build(list)
    val b = FluidMapClusterIndex.build(list.shuffled(Random(3)))
    for (level in a.minZoom..a.maxZoom) {
      assertEquals(a.clusters(level).map { it.id to it.count }.sortedBy { it.first }, b.clusters(level).map { it.id to it.count }.sortedBy { it.first })
    }
  }

  @Test
  fun `due punti a dieci metri stanno insieme lontano e si separano vicino, due citta' mai a zoom 10`() {
    val a = FluidMapMarker("a", FluidGeoPoint(43.77310, 11.25560), "A")
    val b = FluidMapMarker("b", FluidGeoPoint(43.77319, 11.25560), "B") // 10 m a nord
    val rome = FluidMapMarker("roma", FluidGeoPoint(41.9028, 12.4964), "Roma")
    val index = FluidMapClusterIndex.build(listOf(a, b, rome))
    assertEquals(2, index.clusters(10).size)
    assertTrue(index.clusters(14).any { it.markers.size == 2 })
    assertEquals(3, index.clusters(18).size)
    val pair = index.clusters(14).first { it.markers.size == 2 }
    val split = index.expansionZoom(pair)
    assertTrue("si divide a $split", index.clusters(split).none { c -> c.markers.map { it.id }.containsAll(listOf("a", "b")) })
    // L'antenato a un livello piu' basso contiene i segnaposto del discendente.
    val ancestor = index.ancestor("a", 18, 12)
    assertNotNull(ancestor)
    assertTrue(ancestor!!.markers.any { it.id == "a" })
    assertEquals(12, ancestor.level)
  }

  @Test
  fun `il riquadro attorno trova i grappoli vicini anche oltre l'antimeridiano`() {
    val east = FluidMapMarker("est", FluidGeoPoint(-17.0, 179.9), "Est")
    val west = FluidMapMarker("ovest", FluidGeoPoint(-17.0, -179.9), "Ovest")
    val far = FluidMapMarker("lontano", FluidGeoPoint(45.0, 9.0), "Lontano")
    val index = FluidMapClusterIndex.build(listOf(east, west, far))
    val around = index.around(12, FluidMapProjection.x(180.0), FluidMapProjection.y(-17.0), 0.001)
    assertEquals(setOf("est", "ovest"), around.map { it.id }.toSet())
  }

  @Test
  fun `diecimila segnaposto si raggruppano in poco tempo`() {
    val list = markers(10_000, seed = 11)
    val start = System.nanoTime()
    val index = FluidMapClusterIndex.build(list)
    val ms = (System.nanoTime() - start) / 1_000_000
    assertEquals(10_000, index.size)
    assertTrue("costruzione in $ms ms", ms < 4_000)
  }

  @Test
  fun `le coordinate si scrivono come Mappe e si copiano col punto`() {
    assertEquals("43,77310° N, 11,25560° E", FluidGeoFormat.display(florence, Locale.ITALY))
    assertEquals("33,86880° S, 151,20930° E", FluidGeoFormat.display(FluidGeoPoint(-33.8688, 151.2093), Locale.ITALY))
    assertEquals("40,71280° N, 74,00600° O", FluidGeoFormat.display(FluidGeoPoint(40.7128, -74.006), Locale.ITALY))
    assertEquals("40.71280° N, 74.00600° W", FluidGeoFormat.display(FluidGeoPoint(40.7128, -74.006), Locale.US))
    assertEquals("43.77310, 11.25560", FluidGeoFormat.copy(florence))
    assertEquals("geo:43.773100,11.255600?q=43.773100,11.255600(Piazza%20della%20Signoria)", FluidGeoFormat.geoUri(florence, "Piazza della Signoria"))
    assertEquals("https://www.openstreetmap.org/?mlat=43.77310&mlon=11.25560#map=17/43.77310/11.25560", FluidGeoFormat.openStreetMapUrl(florence))
  }

  @Test
  fun `l'attribuzione di serie dice OpenStreetMap e ha i collegamenti`() {
    val a = FluidMapStyles.OpenFreeMapAttribution
    assertTrue(a.short.contains("OpenStreetMap"))
    assertTrue(a.credits.any { it.url == "https://www.openstreetmap.org/copyright" && it.text.contains("ODbL") })
    assertTrue(FluidMapStyles.OpenFreeMap.all { it.uri.startsWith("https://tiles.openfreemap.org/styles/") && it.attribution == a })
    assertEquals("https://tiles.openfreemap.org/styles/dark", FluidMapStyles.Explore.uriFor(dark = true))
  }

  @Test
  fun `la chiave della cartolina non cambia per un millimetro e cambia per lo stile`() {
    val r = FluidMapSnapshotRequest(FluidMapCamera(florence, 14.0), 600, 320, 3f, FluidMapStyles.Explore, false)
    val same = r.copy(camera = FluidMapCamera(FluidGeoPoint(43.773100001, 11.255600001), 14.0))
    assertEquals(FluidMapSnapshots.keyOf(r), FluidMapSnapshots.keyOf(same))
    assertTrue(FluidMapSnapshots.keyOf(r) != FluidMapSnapshots.keyOf(r.copy(style = FluidMapStyles.Vivid)))
    assertTrue(FluidMapSnapshots.keyOf(r) != FluidMapSnapshots.keyOf(r.copy(dark = true)))
  }

  @Test
  fun `le misure di un grappolo crescono col numero e restano fra 34 e 56 dp`() {
    assertEquals(34f, clusterDiameter(2).value, 0.01f)
    assertTrue(clusterDiameter(16) > clusterDiameter(4))
    assertEquals(56f, clusterDiameter(100_000).value, 0.01f)
    assertEquals("999+", clusterCountText(1500))
    assertTrue(abs(clusterDiameter(8).value - 46f) < 0.01f)
  }
}
