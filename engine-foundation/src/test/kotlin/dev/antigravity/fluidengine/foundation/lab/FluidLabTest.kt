package dev.antigravity.fluidengine.foundation.lab

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.24 · Il Laboratorio: accendi, spegni, sopravvive al riavvio, al prossimo avvio, le prove scadute. */
class FluidLabTest {
  private val editor = FluidExperiment("nuovo-editor", "Nuovo editor", "L'editor che arriva con la 4.1.", group = "Interfaccia")
  private val engine = FluidExperiment(
    "motore-db", "Motore nuovo", "Il database nuovo.", group = "Prestazioni", applies = FluidExperimentApplies.NextLaunch,
  )
  private val onByDefault = FluidExperiment("anteprime", "Anteprime", "Le anteprime grandi.", defaultEnabled = true)
  private val old = FluidExperiment("vecchia", "Vecchia", "Finita.", expires = LocalDate.of(2026, 11, 1))
  private val today = LocalDate.of(2026, 11, 26)

  @Test
  fun onOffAndTheRestart() {
    val store = FluidLabStore.inMemory()
    val lab = FluidLab(listOf(editor, engine, onByDefault), store, today)
    assertFalse(lab.isEnabled(editor))
    assertTrue(lab.isEnabled(onByDefault))
    lab.setEnabled(editor, true)
    assertTrue(lab.isEnabled(editor))
    assertEquals(2, lab.state.value.enabledCount)
    // Un processo nuovo sulla stessa memoria: la scelta è rimasta.
    val again = FluidLab(listOf(editor, engine, onByDefault), store, today)
    assertTrue(again.isEnabled(editor))
    again.setEnabled(editor, false)
    assertFalse(FluidLab(listOf(editor, engine, onByDefault), store, today).isEnabled(editor))
    // Una scelta uguale al valore di serie non resta scritta.
    assertTrue(store.readAll().isEmpty())
  }

  @Test
  fun nextLaunchKeepsTheValueOfThisProcess() {
    val store = FluidLabStore.inMemory()
    val lab = FluidLab(listOf(engine), store, today)
    lab.setEnabled(engine, true)
    // La pagina mostra la scelta, ma chi legge in questo processo vede ancora il valore di partenza.
    assertTrue(lab.state.value.isChosen(engine.key))
    assertFalse(lab.isEnabled(engine))
    assertEquals(setOf(engine.key), lab.state.value.pendingRestart)
    // Rimessa com'era, non aspetta più niente.
    lab.setEnabled(engine, false)
    assertTrue(lab.state.value.pendingRestart.isEmpty())
    lab.setEnabled(engine, true)
    // Al riavvio vale.
    val next = FluidLab(listOf(engine), store, today)
    assertTrue(next.isEnabled(engine))
    assertTrue(next.state.value.pendingRestart.isEmpty())
  }

  @Test
  fun resetAllBringsEverythingBack() {
    val store = FluidLabStore.inMemory()
    val lab = FluidLab(listOf(editor, onByDefault), store, today)
    lab.setEnabled(editor, true)
    lab.setEnabled(onByDefault, false)
    lab.resetAll()
    assertFalse(lab.isEnabled(editor))
    assertTrue(lab.isEnabled(onByDefault))
    assertTrue(store.readAll().isEmpty())
  }

  @Test
  fun expiredExperimentsDisappearAndForgetTheChoice() {
    val store = FluidLabStore.inMemory(mapOf("vecchia" to true, "tolta" to true))
    val lab = FluidLab(listOf(editor, old), store, today)
    assertEquals(listOf(editor), lab.experiments)
    assertFalse(lab.isEnabled(old))
    assertFalse(lab.isEnabled("tolta"))
    // Una funzione tolta e poi rimessa col suo nome non si ritrova accesa.
    assertTrue(store.readAll().isEmpty())
    // Il giorno di scadenza vale ancora.
    assertEquals(listOf(editor, old), FluidLab(listOf(editor, old), FluidLabStore.inMemory(), LocalDate.of(2026, 11, 1)).experiments)
  }

  @Test
  fun keysAreCodeNames() {
    for (bad in listOf("", "Nuovo Editor", "nuovo editor", "-x", "x".repeat(49), "perché")) {
      assertTrue(bad, runCatching { FluidExperiment(bad, "t", "s") }.isFailure)
    }
    assertTrue(FluidLab.isValidKey("editor.v2_prova-1"))
  }

  @Test
  fun enabledExperimentsForAReport() {
    val lab = FluidLab(listOf(editor, engine, onByDefault), FluidLabStore.inMemory(), today)
    lab.setEnabled(editor, true)
    assertEquals(listOf(editor, onByDefault), lab.enabledExperiments())
    assertEquals(editor, lab.experiment("nuovo-editor"))
  }
}
