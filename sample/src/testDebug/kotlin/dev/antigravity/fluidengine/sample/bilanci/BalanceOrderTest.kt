package dev.antigravity.fluidengine.sample.bilanci

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.manipulation.Filter
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * F7.32 · Le prove dei bilanci con un passato scelto, nella stessa JVM: una prova di prima che lascia un timbro, un PC
 * carico. Gradle fa girare le classi in ordine alfabetico (F8 prima delle scene di «Misure»), tranne quando l'ultima corsa
 * ne ha fatta cadere una, che allora passa in testa; e il PC dei lotti ha spesso tre build insieme. Un guasto che dipende
 * da chi ha girato prima, o da quanto è carico il PC, si vedeva solo a volte, e costava un lotto ripetuto.
 *
 * La prova gira fuori dalla sandbox di Robolectric e fa partire le classi con `JUnitCore`, come farebbe Gradle: le
 * classi della sandbox (e i loro campi statici) sono le stesse per tutte, come nel lotto. Parla col banco con le
 * proprietà di sistema, l'unica cosa che passa dentro la sandbox.
 *
 * Gli ordini completi delle due classi (più di un minuto ciascuno) sono uno strumento: girano solo con la variabile
 * d'ambiente `FLUID_BILANCI_ORDINE=1`, e nel lotto si saltano.
 */
class BalanceOrderTest {

  private fun only(type: Class<*>, vararg methods: String): Request =
    Request.aClass(type).filterWith(object : Filter() {
      override fun shouldRun(description: Description): Boolean =
        description.methodName == null || description.methodName in methods

      override fun describe(): String = methods.joinToString()
    })

  private fun runInOrder(vararg requests: Request): List<String> {
    val core = JUnitCore()
    val failures = ArrayList<String>()
    core.addListener(object : RunListener() {
      override fun testStarted(description: Description) {
        println("Ordine: ${description.className.substringAfterLast('.')}.${description.methodName}")
      }

      override fun testFailure(failure: Failure) {
        failures += "${failure.description.className.substringAfterLast('.')}.${failure.description.methodName}: " +
          (failure.message ?: failure.exception.toString()).take(4000)
      }
    })
    for (request in requests) core.run(request)
    return failures
  }

  private fun <T> withProperty(name: String, value: String, block: () -> T): T {
    System.setProperty(name, value)
    try {
      return block()
    } finally {
      System.clearProperty(name)
    }
  }

  private fun stamp(untilMillis: Long): List<String> =
    withProperty(RouteStampForOrderTest.Property, untilMillis.toString()) {
      runInOrder(Request.aClass(RouteStampForOrderTest::class.java))
    }

  private fun tool() = assumeTrue("strumento: FLUID_BILANCI_ORDINE=1", System.getenv("FLUID_BILANCI_ORDINE") == "1")

  /**
   * Il guasto di F7.29 («le scene di Misure prima di F8 fanno cadere le pose di dieci pacchetti»), ridotto all'osso. Una
   * scena di «Misure» che passa da `FluidPageHost` timbra i segnali del movimento fra pagine di serie, uno per processo;
   * Robolectric rimette l'orologio a 100 ms a ogni prova, e il timbro resta nel futuro della prova dopo. Qui il timbro lo
   * mette [RouteStampForOrderTest]: 4 s, dentro la posa della prima scena di un pacchetto (da 2,9 a 4,9 s), dove nel lotto
   * R3 la sonda faceva la sua prima lettura. Coi segnali di serie le pose devono cadere, così si sa che la prova vede
   * ancora il guasto (l'orologio di Robolectric è simulato: succede a ogni corsa); coi segnali nuovi per ogni scena (il
   * banco di F7.32) è tutto verde. Alla fine il timbro si toglie.
   */
  @Test
  fun unTimbroLasciatoDaUnaProvaPrimaNonCambiaLePoseDiF8() {
    // Due pacchetti che nel lotto R3 cadevano così e altrimenti mai: nessuna loro scena aspetta un lavoro in tempo vero
    // (F9.2, che cadeva anche lui, ha `ricerca-tool`, che a volte cade nel moto per conto suo).
    val packs = { only(F8BalanceTest::class.java, "f8p6", "f8p11") }
    try {
      assertTrue(stamp(StaleStampMillis).isEmpty())
      val before = withProperty(BalanceHarness.SharedRouteSignalsProperty, "1") { runInOrder(packs()) }
      println("Coi segnali di serie, col timbro a $StaleStampMillis ms:\n  ${before.joinToString("\n  ")}")
      assertTrue(
        "coi segnali del movimento di serie le pose di F8 dovevano cadere (il guasto di F7.29), invece: $before",
        before.any { it.startsWith("F8BalanceTest.") && it.contains("/posa") },
      )
      assertTrue(stamp(StaleStampMillis).isEmpty())
      val after = runInOrder(packs())
      assertTrue("coi segnali per scena:\n  ${after.joinToString("\n\n  ")}", after.isEmpty())
    } finally {
      stamp(0L)
    }
  }

  /**
   * Il PC carico: quando entra una scena, i fili di `Dispatchers.Default` e `IO` restano occupati per 1,5 s (attese che
   * dormono, nessuna CPU), e il lavoro in sottofondo delle scene parte solo dopo. Per le scene dichiarate con
   * `@sottofondo` (la sincronia di F8.21, i dati nel testo di F8.9) il banco di F7.32 tiene fermo l'orologio della prova
   * finché il sottofondo non ha finito ([BackgroundWork]), e i bilanci restano quelli del PC scarico. Senza la barriera
   * cadevano: lo mostra [senzaLaBarrieraIlPcCaricoCambiaIBilanci], uno strumento (dipende dal tempo vero, quindi non è
   * una prova fissa).
   */
  @Test
  fun unPcCaricoNonCambiaIBilanciDelLavoroInSottofondo() {
    assertTrue("lo stato dei fili in sottofondo non si legge più (kotlinx.coroutines cambiato?)", BackgroundWork.available)
    val failures = withProperty(BalanceHarness.BackgroundDelayProperty, LoadMillis.toString()) {
      runInOrder(only(F8BalanceTest::class.java, *LoadedPacks))
    }
    assertTrue("col sottofondo in ritardo di $LoadMillis ms:\n  ${failures.joinToString("\n\n  ")}", failures.isEmpty())
  }

  /** Strumento: lo stesso PC carico senza la barriera, cioè com'era prima di F7.32. Deve cadere. */
  @Test
  fun senzaLaBarrieraIlPcCaricoCambiaIBilanci() {
    tool()
    val failures = withProperty(BalanceHarness.NoBackgroundBarrierProperty, "1") {
      withProperty(BalanceHarness.BackgroundDelayProperty, ToolLoadMillis.toString()) {
        runInOrder(only(F8BalanceTest::class.java, *ToolPacks))
      }
    }
    // L'ultima occupazione dei fili finisce prima della prova dopo.
    Thread.sleep(ToolLoadMillis + 500)
    println("Senza la barriera, col sottofondo in ritardo di $ToolLoadMillis ms:\n  ${failures.joinToString("\n\n  ")}")
    assertTrue("senza la barriera il PC carico doveva cambiare i bilanci", failures.isNotEmpty())
  }

  /** Strumento: le scene di «Misure» prima di quelle di F8, l'ordine che nel lotto R3 faceva cadere dieci pose. */
  @Test
  fun misurePrimaDiF8() {
    tool()
    val failures = runInOrder(Request.aClass(GlassBalanceScenesTest::class.java), Request.aClass(F8BalanceTest::class.java))
    assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
  }

  /** Strumento: l'ordine di serie, per confronto. */
  @Test
  fun f8PrimaDelleMisure() {
    tool()
    val failures = runInOrder(Request.aClass(F8BalanceTest::class.java), Request.aClass(GlassBalanceScenesTest::class.java))
    assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
  }

  private companion object {
    /** Il timbro della prova di prima: 4 s, dentro la posa della prima scena di un pacchetto di F8. */
    const val StaleStampMillis = 4_000L

    /** Quanto restano occupati i fili in sottofondo quando entra una scena, nel PC carico. */
    const val LoadMillis = 1_500L

    /** I pacchetti con le scene dichiarate `@sottofondo`: i dati nel testo e la sincronia. */
    val LoadedPacks = arrayOf("f8p9", "f8p21")

    /**
     * Lo strumento senza barriera: anche F9.1 (`pampai-assente`, F7.32b), che non sta nella guardia fissa perché le altre
     * sue scene aspettano PampAI in tempo vero per conto loro.
     */
    val ToolPacks = LoadedPacks + "f9p1"

    /** Quanto restano occupati i fili nello strumento: abbastanza perché lo stato di PampAI arrivi dopo la posa. */
    const val ToolLoadMillis = 4_000L
  }
}
