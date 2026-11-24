package dev.antigravity.fluidengine.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.antigravity.fluidengine.foundation.FluidFirstUseKeys
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** F8.11: la memoria della prima volta resta su disco e si rilegge da un'apertura nuova. */
class EngineFirstUseStoreTest {

  @Test fun ricordaFraUnaperturaELaltra() = runBlocking {
    val dir = Files.createTempDirectory("first-use").toFile()
    val file = File(dir, "fluid_engine.preferences_pb")
    val first = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val store = EngineFirstUseStore(PreferenceDataStoreFactory.create(scope = first) { file })
    assertNull(store.read(FluidFirstUseKeys.permissionAsked("microfono")))
    store.write(FluidFirstUseKeys.permissionAsked("microfono"), "1")
    store.write(FluidFirstUseKeys.welcomeSeen(), "2.1.0")
    store.write(FluidFirstUseKeys.welcomeSeen(), "2.2.0")
    first.coroutineContext[Job]!!.cancelAndJoin()

    // Un processo nuovo: un altro DataStore sullo stesso file.
    val second = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val reopened = EngineFirstUseStore(PreferenceDataStoreFactory.create(scope = second) { file })
    assertEquals("1", reopened.read(FluidFirstUseKeys.permissionAsked("microfono")))
    assertEquals("2.2.0", reopened.read(FluidFirstUseKeys.welcomeSeen()))
    reopened.write(FluidFirstUseKeys.permissionAsked("microfono"), null)
    assertNull(reopened.read(FluidFirstUseKeys.permissionAsked("microfono")))
    second.coroutineContext[Job]!!.cancelAndJoin()
    dir.deleteRecursively()
    Unit
  }
}
