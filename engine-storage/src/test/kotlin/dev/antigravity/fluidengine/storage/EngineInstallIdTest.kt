package dev.antigravity.fluidengine.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.21: l'identita' dell'installazione nasce una volta, resta, e si rifa' solo se il file e' rovinato. */
class EngineInstallIdTest {

  @Test fun nasceUnaVoltaERestaFraUnAvvioELAltro() {
    val dir = Files.createTempDirectory("install-id").toFile()
    val file = File(dir, "id")
    val first = EngineInstallId.readOrCreate(file)
    assertTrue(EngineInstallId.isValid(first))
    assertEquals(first, EngineInstallId.readOrCreate(file))
    assertEquals(first, EngineInstallId.readOrCreate(File(dir, "id")))
    assertTrue("nessun file temporaneo rimasto", dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    dir.deleteRecursively()
  }

  @Test fun unFileRovinatoSiRifaEDueInstallazioniSonoDiverse() {
    val dir = Files.createTempDirectory("install-id").toFile()
    val file = File(dir, "id")
    file.writeText("non-valido!")
    val fresh = EngineInstallId.readOrCreate(file)
    assertTrue(EngineInstallId.isValid(fresh))
    assertNotEquals("non-valido!", fresh)
    val other = EngineInstallId.readOrCreate(File(dir, "altro"))
    assertNotEquals(fresh, other)
    dir.deleteRecursively()
  }
}
