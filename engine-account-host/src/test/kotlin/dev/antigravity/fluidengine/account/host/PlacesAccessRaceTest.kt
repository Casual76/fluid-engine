package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import java.nio.file.Files
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R1 (sospetto di Sol, F10.8): le scelte dei luoghi stanno in una mappa cifrata sola. `setAccess` la leggeva e la
 * riscriveva con due blocchi separati: due scelte insieme (la domanda dello Store e la pagina «Chi usa i tuoi luoghi»,
 * o la migrazione all'avvio) potevano rimettere la mappa di prima, e una decisione spariva.
 */
class PlacesAccessRaceTest {
  private val apps = listOf("dev.pampa.pampai", "dev.antigravity.classevivaexpressive", "dev.antigravity.fluidtransit", "dev.pampa.fluidweather")

  @Test
  fun `R1 - quattro scelte insieme restano tutte e quattro`() {
    val places = PlacesStore(MemoryCustodyVault(), Files.createTempDirectory("luoghi").toFile(), AppPolicy())
    repeat(ROUNDS) { round ->
      for (app in apps) places.forget(app)
      val start = CyclicBarrier(apps.size)
      apps.map { app -> thread { start.await(); places.setAccess(app, PlaceAccess.READ) } }.forEach { it.join() }
      assertEquals("giro $round: una scelta è sparita", apps.associateWith { PlaceAccess.READ }, places.decisions())
    }
  }

  private companion object {
    const val ROUNDS = 200
  }
}
