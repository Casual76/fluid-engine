package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.layer.GraphicsLayer
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerRevisionListener
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.materialLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Quando la sonda dello sfondo legge (F2.1, piano 3.9): le regole di [GlassProbeScheduler], e il contatore di
 * revisione delle sorgenti che le sveglia.
 *
 * Il piano chiede tre prove: una revisione senza alcuna cattura produce una lettura; niente eventi, niente letture; la
 * cadenza massima. Le altre sono le regole che servono perché quelle tre reggano su una pagina vera: la miniatura si
 * rifà solo quando cambia la geometria, il silenzio durante un movimento veloce e la lettura che lo chiude, la cadenza
 * fissa per le sorgenti che non avvisano, una lettura sola in volo, l'attesa che cresce dopo un fallimento.
 *
 * Il «come» (il lettore della finestra, il `HardwareRenderer`, la richiesta dopo il disegno) chiede il dispositivo: lo
 * prova `GlassProbeInstrumentedTest` nella galleria, in S3.
 */
class GlassProbeSchedulerTest {

  /**
   * Fa girare lo schedulatore come il nodo: un passo per fotogramma (8 ms, 120 Hz), gli eventi a istanti dati, ogni
   * lettura che torna dopo [latencyMillis] (S1-bis: 3 ms dalla richiesta al numero, qui un fotogramma intero).
   */
  private class Bench(
    val scheduler: GlassProbeScheduler = GlassProbeScheduler(
      minIntervalMillis = 120L,
      cadenceMillis = 500L,
      fastPxPerSecond = 1_000f,
      motionQuietMillis = 100L,
      maxBackoffMillis = 2_000L,
    ),
    val latencyMillis: Long = 8L,
  ) {
    val reads = mutableListOf<Long>()
    val rerecords = mutableListOf<Boolean>()
    var outcome: (Long) -> GlassProbeOutcome = { GlassProbeOutcome.Read }
    private var returnsAt = Long.MAX_VALUE
    private var returning = GlassProbeOutcome.Read

    /** Un fotogramma a [now]: prima torna ciò che è in volo, poi gli eventi del fotogramma, poi la decisione. */
    fun frame(now: Long, events: GlassProbeScheduler.() -> Unit = {}) {
      if (now >= returnsAt) {
        returnsAt = Long.MAX_VALUE
        scheduler.onReadDone(now, returning)
      }
      scheduler.events()
      val decision = scheduler.next(now)
      if (decision is GlassProbeDecision.Read) {
        scheduler.onReadSent(now, decision.rerecord)
        reads += now
        rerecords += decision.rerecord
        returning = outcome(now)
        returnsAt = now + latencyMillis
      }
    }

    /** Fotogrammi da [from] a [until] (escluso), con gli stessi eventi a ogni fotogramma. */
    fun run(from: Long, until: Long, events: GlassProbeScheduler.(Long) -> Unit = {}) {
      var now = from
      while (now < until) {
        val at = now
        frame(at) { events(this, at) }
        now += FrameMillis
      }
    }

    fun readsBetween(from: Long, until: Long): List<Long> = reads.filter { it in from until until }
  }

  // --- Le tre prove del piano ------------------------------------------------------------------------------

  @Test
  fun `niente eventi, niente letture`() {
    val bench = Bench()
    bench.run(0L, 10_000L)
    assertEquals("senza un evento la sonda non legge mai", emptyList<Long>(), bench.reads)
    assertEquals(GlassProbeDecision.Idle, bench.scheduler.next(10_000L))
  }

  @Test
  fun `dopo la prima lettura, ferma, nessun'altra`() {
    val bench = Bench()
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.run(8L, 10_000L)
    assertEquals("la nascita legge una volta, poi niente", listOf(0L), bench.reads)
  }

  @Test
  fun `una revisione senza alcuna cattura produce una lettura`() {
    val bench = Bench()
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.run(8L, 2_000L)
    // Il vetro non si muove e non ricattura: cambia solo la sorgente, che si è registrata di nuovo.
    bench.frame(2_000L) { onRevision(2_000L) }
    bench.run(2_008L, 4_000L)
    assertEquals(listOf(0L, 2_000L), bench.reads)
    assertEquals("dopo una revisione la miniatura non si rifà: basta rileggerla", listOf(true, false), bench.rerecords)
  }

  @Test
  fun `cadenza massima, e l'ultimo evento non si perde`() {
    val bench = Bench()
    bench.frame(0L) { onGeometry(0L, 0f) }
    // Una lista che si ri-registra a ogni fotogramma per due secondi.
    bench.run(8L, 2_000L) { now -> onRevision(now) }
    bench.run(2_000L, 4_000L)
    val reads = bench.reads
    for (i in 1 until reads.size) {
      assertTrue("due letture a ${reads[i - 1]} e ${reads[i]}: meno di 120 ms", reads[i] - reads[i - 1] >= 120L)
    }
    // 2 s a una lettura ogni 120 ms: 17 letture (0, 120, … 1 920), più quella che chiude la raffica.
    assertTrue("letture: ${reads.size}", reads.size in 15..18)
    assertTrue(
      "l'ultima revisione (1 992 ms) deve essere coperta da una lettura partita dopo: ${reads.last()}",
      reads.last() >= 1_992L,
    )
    assertEquals("finita la raffica, la sonda si ferma", emptyList<Long>(), bench.readsBetween(2_200L, 4_000L))
  }

  // --- La miniatura -----------------------------------------------------------------------------------------

  @Test
  fun `la prima lettura registra sempre la miniatura, anche se l'evento e' una revisione`() {
    val bench = Bench()
    bench.frame(0L) { onRevision(0L) }
    assertEquals(listOf(true), bench.rerecords)
  }

  @Test
  fun `lo spostamento chiede una nuova miniatura, la revisione no`() {
    val bench = Bench()
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.frame(1_000L) { onRevision(1_000L) }
    bench.frame(2_000L) { onGeometry(2_000L, 3f) }
    bench.frame(3_000L) { onRevision(3_000L) }
    assertEquals(listOf(true, false, true, false), bench.rerecords)
  }

  @Test
  fun `uno spostamento durante una lettura in volo arriva alla lettura dopo`() {
    val bench = Bench(latencyMillis = 50L)
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.frame(16L) { onGeometry(16L, 1f) }
    bench.run(24L, 1_000L)
    assertEquals(listOf(0L, 120L), bench.reads)
    assertEquals(listOf(true, true), bench.rerecords)
  }

  // --- Il movimento veloce ----------------------------------------------------------------------------------

  @Test
  fun `silenzio durante un movimento veloce, una lettura quando si posa`() {
    val bench = Bench()
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.run(8L, 500L)
    // 40 px per fotogramma: 5 000 px/s, sopra la soglia di 1 000.
    bench.run(500L, 1_500L) { now -> onGeometry(now, 40f) }
    bench.run(1_500L, 3_000L)
    assertEquals("nessuna lettura mentre vola", emptyList<Long>(), bench.readsBetween(500L, 1_500L))
    val after = bench.readsBetween(1_500L, 3_000L)
    assertEquals("una lettura sola quando si posa: $after", 1, after.size)
    // L'ultimo spostamento è a 1 496 ms; la lettura parte dopo 100 ms di quiete.
    assertTrue("la lettura parte dopo la quiete: ${after.single()}", after.single() >= 1_596L)
    assertTrue("e non molto dopo: ${after.single()}", after.single() <= 1_604L)
    assertEquals("e rifà la miniatura", true, bench.rerecords.last())
  }

  @Test
  fun `uno spostamento lento si legge alla cadenza massima`() {
    val bench = Bench()
    bench.frame(0L) { onGeometry(0L, 0f) }
    // 2 px per fotogramma: 250 px/s, sotto la soglia.
    bench.run(8L, 1_000L) { now -> onGeometry(now, 2f) }
    val reads = bench.readsBetween(8L, 1_000L)
    assertTrue("un vetro che scivola piano si segue: ${reads.size} letture in un secondo", reads.size in 7..9)
  }

  // --- Le sorgenti che non avvisano ------------------------------------------------------------------------

  @Test
  fun `le sorgenti che non avvisano si leggono a cadenza fissa, e solo loro`() {
    val bench = Bench()
    bench.frame(0L) {
      setUnannounced(true)
      onGeometry(0L, 0f)
    }
    bench.run(8L, 2_100L)
    assertEquals(listOf(0L, 504L, 1_008L, 1_512L, 2_016L), bench.reads)
    bench.frame(2_104L) { setUnannounced(false) }
    bench.run(2_112L, 5_000L)
    assertEquals("tornate a sorgenti che avvisano, la cadenza fissa si ferma", 5, bench.reads.size)
  }

  @Test
  fun `una sorgente che non avvisa e' letta anche senza alcun evento`() {
    val bench = Bench()
    bench.frame(0L) { setUnannounced(true) }
    assertEquals("la cadenza fissa parte subito", listOf(0L), bench.reads)
  }

  // --- Sospensione, letture in volo, fallimenti -------------------------------------------------------------

  @Test
  fun `sospesa non legge, e al ritorno legge una volta sola`() {
    val bench = Bench()
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.frame(200L) { setSuspended(true) }
    bench.run(208L, 1_200L) { now -> onRevision(now) }
    assertEquals(listOf(0L), bench.reads)
    bench.frame(1_200L) { setSuspended(false) }
    bench.run(1_208L, 3_000L)
    assertEquals(listOf(0L, 1_200L), bench.reads)
  }

  @Test
  fun `una lettura in volo non ne fa partire un'altra`() {
    val bench = Bench(latencyMillis = 400L)
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.run(8L, 1_000L) { now -> onRevision(now) }
    // In volo da 0 a 400: niente; poi la cadenza da 400 (torna a 400, parte subito perché 400 - 0 > 120).
    assertEquals(listOf(0L, 400L, 800L), bench.reads)
  }

  @Test
  fun `dopo un fallimento si riprova, con un'attesa che raddoppia fino al tetto`() {
    val bench = Bench()
    bench.outcome = { GlassProbeOutcome.Failed }
    bench.frame(0L) { onGeometry(0L, 0f) }
    bench.run(8L, 8_000L)
    val gaps = bench.reads.zipWithNext { a, b -> b - a }
    // 240, 480, 960, 1 920, poi il tetto di 2 000 (arrotondati al fotogramma di 8 ms).
    assertTrue("attese: $gaps", gaps.first() in 240L..248L)
    assertTrue("attese: $gaps", gaps[1] in 480L..488L)
    assertTrue("attese: $gaps", gaps[2] in 960L..968L)
    assertTrue("il tetto: $gaps", gaps.drop(4).all { it in 2_000L..2_008L })
    assertTrue("ogni tentativo rifà la miniatura che non è mai stata letta", bench.rerecords.all { it })
  }

  @Test
  fun `il primo successo riporta l'attesa alla cadenza massima`() {
    val scheduler = GlassProbeScheduler(minIntervalMillis = 120L)
    scheduler.onGeometry(0L, 0f)
    repeat(3) { attempt ->
      val now = attempt * 5_000L
      assertTrue(scheduler.next(now) is GlassProbeDecision.Read)
      scheduler.onReadSent(now, rerecord = true)
      scheduler.onReadDone(now + 5L, GlassProbeOutcome.Failed)
    }
    assertEquals(960L, scheduler.interval())
    scheduler.onReadSent(15_000L, rerecord = true)
    scheduler.onReadDone(15_005L, GlassProbeOutcome.Read)
    assertEquals(120L, scheduler.interval())
  }

  @Test
  fun `una lettura saltata lascia l'evento in attesa senza far crescere l'attesa`() {
    val scheduler = GlassProbeScheduler(minIntervalMillis = 120L)
    scheduler.onGeometry(0L, 0f)
    scheduler.onReadSent(0L, rerecord = true)
    scheduler.onReadDone(1L, GlassProbeOutcome.Read)
    scheduler.onRevision(500L)
    assertEquals(GlassProbeDecision.Read(rerecord = false), scheduler.next(500L))
    scheduler.onReadSent(500L, rerecord = false)
    // La finestra è in secondo piano: il lettore non ha letto niente.
    scheduler.onReadDone(501L, GlassProbeOutcome.Skipped)
    assertTrue("l'evento resta in attesa", scheduler.hasPending)
    assertEquals("nessuna attesa che cresce", 120L, scheduler.interval())
    // Al prossimo evento (la finestra che torna e ridisegna) si legge subito: la lettura saltata non conta come fatta.
    scheduler.onRevision(510L)
    assertEquals(GlassProbeDecision.Read(rerecord = false), scheduler.next(510L))
  }

  @Test
  fun `un evento durante il silenzio chiede di essere richiamato a fine silenzio`() {
    val scheduler = GlassProbeScheduler(fastPxPerSecond = 1_000f, motionQuietMillis = 100L)
    scheduler.onGeometry(0L, 0f)
    scheduler.onReadSent(0L, rerecord = true)
    scheduler.onReadDone(2L, GlassProbeOutcome.Read)
    scheduler.onGeometry(500L, 0f)
    scheduler.onGeometry(508L, 40f)
    assertTrue(scheduler.isMovingFast(510L))
    assertEquals(GlassProbeDecision.WaitUntil(608L), scheduler.next(510L))
    assertEquals(GlassProbeDecision.Read(rerecord = true), scheduler.next(608L))
  }

  @Test
  fun `due spostamenti nello stesso millisecondo non fanno una velocita' infinita`() {
    val scheduler = GlassProbeScheduler(fastPxPerSecond = 1_000f)
    scheduler.onGeometry(100L, 0f)
    scheduler.onGeometry(100L, 30f)
    assertFalse(scheduler.isMovingFast(100L))
  }

  // --- La revisione della sorgente --------------------------------------------------------------------------

  @Test
  fun `la revisione avvisa ogni iscritto una volta, e chi se ne va non e' piu' avvisato`() {
    val source = materialLayerBackdrop(fakeLayer())
    val heard = mutableListOf<String>()
    val first = LayerRevisionListener { _, revision -> heard += "a$revision" }
    val second = LayerRevisionListener { _, revision -> heard += "b$revision" }
    source.addRevisionListener(first)
    source.addRevisionListener(second)
    source.addRevisionListener(first)
    assertEquals("un iscritto due volte è un iscritto", 2, source.revisionListenerCount)
    source.bumpRevision()
    source.removeRevisionListener(first)
    source.bumpRevision()
    assertEquals(listOf("a1", "b1", "b2"), heard)
    assertEquals(2L, source.revision)
  }

  @Test
  fun `chi se ne va durante l'avviso non disturba il giro in corso`() {
    val source = materialLayerBackdrop(fakeLayer())
    val heard = mutableListOf<String>()
    lateinit var leaving: LayerRevisionListener
    leaving = LayerRevisionListener { layer, _ ->
      heard += "via"
      layer.removeRevisionListener(leaving)
    }
    val staying = LayerRevisionListener { _, _ -> heard += "resto" }
    source.addRevisionListener(leaving)
    source.addRevisionListener(staying)
    source.bumpRevision()
    source.bumpRevision()
    assertEquals(listOf("via", "resto", "resto"), heard)
  }

  @Test
  fun `il materiale di un pannello non avvisa, la sonda lo legge a cadenza fissa`() {
    // Lo registra `DrawBackdropNode`, non un `layerBackdrop`: nessuno chiama la revisione.
    val source: LayerBackdrop = materialLayerBackdrop(fakeLayer())
    assertFalse(source.announcesRevisions)
  }

  private companion object {
    const val FrameMillis = 8L

    /**
     * Un `GraphicsLayer` di Compose sopra un doppio della piattaforma, come in `PhysicsShadowSlotsTest` (da cui è
     * copiato): qui serve solo a costruire un `LayerBackdrop`, nessuno lo disegna. Se un aggiornamento di Compose
     * rinomina le interne, il test si rompe qui, ad alta voce: va riscritto il doppio, non tolto il test.
     */
    fun fakeLayer(): GraphicsLayer {
      givePlatformAFingerprint()
      val implClass = Class.forName("androidx.compose.ui.graphics.layer.GraphicsLayerImpl")
      val properties = HashMap<String, Any?>()
      val impl = Proxy.newProxyInstance(implClass.classLoader, arrayOf(implClass)) { proxy, method, args ->
        val name = method.name.substringBefore('-')
        when {
          name == "equals" -> proxy === args?.get(0)
          name == "hashCode" -> System.identityHashCode(proxy)
          name == "toString" -> "FakeGraphicsLayerImpl"
          name.startsWith("set") && args?.size == 1 -> {
            properties[name.removePrefix("set")] = args[0]
            null
          }
          name.startsWith("get") && args.isNullOrEmpty() ->
            properties[name.removePrefix("get")] ?: defaultOf(method)
          name.startsWith("is") && args.isNullOrEmpty() ->
            properties[name.removePrefix("is")] ?: defaultOf(method)
          else -> defaultOf(method)
        }
      }
      return GraphicsLayer::class.java.getConstructor(implClass).newInstance(impl)
    }

    /** Vedi `PhysicsShadowSlotsTest`: `GraphicsLayer` legge `Build.FINGERPRINT`, che negli stub del JVM è null. */
    private fun givePlatformAFingerprint() {
      val field = android.os.Build::class.java.getDeclaredField("FINGERPRINT")
      if (field.get(null) != null) return
      val unsafeClass = Class.forName("sun.misc.Unsafe")
      val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
      val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
      val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java)
        .invoke(unsafe, field) as Long
      unsafeClass.getMethod("putObject", Any::class.java, java.lang.Long.TYPE, Any::class.java)
        .invoke(unsafe, base, offset, "fluid-engine/jvm-unit-test")
    }

    private fun defaultOf(method: Method): Any? = when (method.returnType) {
      java.lang.Boolean.TYPE -> false
      java.lang.Integer.TYPE -> 0
      java.lang.Long.TYPE -> 0L
      java.lang.Float.TYPE -> 0f
      java.lang.Double.TYPE -> 0.0
      java.lang.Short.TYPE -> 0.toShort()
      java.lang.Byte.TYPE -> 0.toByte()
      java.lang.Character.TYPE -> 0.toChar()
      else -> null
    }
  }
}
