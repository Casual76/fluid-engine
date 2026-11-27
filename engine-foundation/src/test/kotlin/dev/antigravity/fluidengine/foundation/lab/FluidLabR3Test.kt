package dev.antigravity.fluidengine.foundation.lab

import android.content.SharedPreferences
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R3 · Il Laboratorio dopo la review R3: la scadenza vale anche per un processo che resta vivo oltre l'ultimo giorno, e
 * la memoria nelle preferenze dell'app non tocca le chiavi che non sono del Laboratorio.
 */
class FluidLabR3Test {
  private val trial = FluidExperiment("prova-breve", "Prova breve", "Finisce il 30.", expires = LocalDate.of(2026, 11, 30))
  private val engine = FluidExperiment(
    "motore-db", "Motore nuovo", "Il database nuovo.", applies = FluidExperimentApplies.NextLaunch, expires = LocalDate.of(2026, 11, 30),
  )
  private val editor = FluidExperiment("nuovo-editor", "Nuovo editor", "Senza scadenza.")

  @Test
  fun `la prova scaduta a processo vivo torna di serie e sparisce dalla pagina`() {
    var day = LocalDate.of(2026, 11, 30)
    val lab = FluidLab(listOf(trial, engine, editor), FluidLabStore.inMemory(), today = { day })
    lab.setEnabled(trial, true)
    lab.setEnabled(editor, true)
    assertTrue("l'ultimo giorno vale ancora", lab.isEnabled(trial))
    assertEquals(listOf(trial, engine, editor), lab.experiments)
    // L'app resta aperta oltre la mezzanotte.
    day = LocalDate.of(2026, 12, 1)
    assertFalse("scaduta: il valore di serie", lab.isEnabled(trial))
    assertFalse(lab.isEnabled("prova-breve"))
    assertEquals("e non e' piu' nella pagina", listOf(editor), lab.experiments)
    assertEquals(null, lab.experiment("prova-breve"))
    assertEquals(listOf(editor), lab.enabledExperiments())
    // Una scelta su una prova scaduta non si prende.
    lab.setEnabled(engine, true)
    assertFalse(lab.isEnabled(engine))
    assertTrue("le altre restano", lab.isEnabled(editor))
  }

  @Test
  fun `la data fissa delle prove di prima vale ancora`() {
    val lab = FluidLab(listOf(trial, editor), FluidLabStore.inMemory(), LocalDate.of(2026, 12, 1))
    assertEquals(listOf(editor), lab.experiments)
  }

  @Test
  fun `nelle preferenze dell'app il Laboratorio tocca solo le sue chiavi`() {
    val prefs = MemoryPreferences(mutableMapOf("tema-scuro" to true, "notifiche" to false, "nome" to "Alessio"))
    val store = FluidLabStore.preferences(prefs)
    // Prima la nascita del Laboratorio toglieva ogni booleano che non conosceva: il tema scuro e le notifiche dell'app.
    val lab = FluidLab(listOf(editor), store, LocalDate.of(2026, 11, 27))
    assertEquals(true, prefs.values["tema-scuro"])
    assertEquals(false, prefs.values["notifiche"])
    assertFalse("una chiave dell'app non e' una scelta del Laboratorio", lab.isEnabled(editor))
    lab.setEnabled(editor, true)
    assertTrue(FluidLab(listOf(editor), FluidLabStore.preferences(prefs), LocalDate.of(2026, 11, 27)).isEnabled(editor))
    // «Ripristina tutto» svuotava il file intero.
    lab.resetAll()
    assertFalse(lab.isEnabled(editor))
    assertEquals(mapOf("tema-scuro" to true, "notifiche" to false, "nome" to "Alessio"), prefs.values)
  }

  @Test
  fun `una chiave dell'app con lo stesso nome di una funzione non la accende`() {
    val prefs = MemoryPreferences(mutableMapOf("nuovo-editor" to true))
    val lab = FluidLab(listOf(editor), FluidLabStore.preferences(prefs), LocalDate.of(2026, 11, 27))
    assertFalse(lab.isEnabled(editor))
    assertEquals(true, prefs.values["nuovo-editor"])
  }

  /** Delle `SharedPreferences` in memoria, come quelle di un'app. */
  private class MemoryPreferences(val values: MutableMap<String, Any?>) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = HashMap(values)
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
      private val puts = LinkedHashMap<String, Any?>()
      private val removes = LinkedHashSet<String>()
      private var clear = false
      override fun putString(key: String, value: String?) = apply { puts[key] = value }
      override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values }
      override fun putInt(key: String, value: Int) = apply { puts[key] = value }
      override fun putLong(key: String, value: Long) = apply { puts[key] = value }
      override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
      override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
      override fun remove(key: String) = apply { removes += key }
      override fun clear() = apply { clear = true }
      override fun commit(): Boolean {
        if (clear) values.clear()
        removes.forEach { values.remove(it) }
        values.putAll(puts)
        return true
      }
      override fun apply() {
        commit()
      }
    }
  }
}
