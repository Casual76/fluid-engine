package dev.antigravity.fluidengine.sync

import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * F8.21 · Le prove generative: tre dispositivi fanno a caso scritture, aggiunte di righe, cancellazioni, sincronizzazioni,
 * mentre la rete cade, le risposte si perdono, gli invii arrivano doppi, i processi muoiono, gli orologi sono sfasati di
 * ore e ogni tanto passano cento giorni (con le pietre tombali tolte dal relay). Alla fine tutti tornano in rete e si
 * controllano tre proprieta':
 * 1. **tutti convergono**: gli stessi elementi, uguali, su ogni dispositivo e sul relay, e nessuna modifica in attesa;
 * 2. **nessuna modifica si perde**: per ogni campo corto vince la scrittura con lo stamp piu' grande fra tutte quelle
 *    fatte (l'oracolo), ogni riga aggiunta a un testo lungo c'e', e un elemento e' cancellato se e solo se qualcuno l'ha
 *    cancellato;
 * 3. **nessuna modifica si applica due volte**: ogni riga aggiunta compare **una volta sola**.
 *
 * Una seconda prova generativa mette i dispositivi a cambiare **lo stesso passaggio** di un testo (conflitti veri) e a
 * scegliere a caso fra le versioni, anche «tutte e due»: alla fine i conflitti sono uguali dappertutto, e dopo le scelte
 * non ne resta nessuno.
 *
 * Il seme di ogni corsa e' nel messaggio d'errore, per rifarla uguale.
 */
class SyncPropertyTest {

  private class Write(val record: String, val field: String, val value: String, val stamp: SyncHlc)

  @Test fun `generativa - converge, non perde e non raddoppia`() {
    val runs = 400
    for (seed in 1L..runs) {
      try {
        scenario(seed)
      } catch (e: Throwable) {
        throw AssertionError("seme $seed: ${e.message}", e)
      }
    }
  }

  private fun scenario(seed: Long) {
    val random = Random(seed)
    val world = BenchWorld()
    try {
      val devices = List(3) { i ->
        world.device("d$i", skewMillis = random.nextLong(-6 * 3_600_000L, 6 * 3_600_000L))
      }
      val ids = List(5) { "r$it" }
      for (id in ids) devices[0].edit(id) { longText("body", "inizio $id\n"); text("a", "0") }
      world.settle(*devices.toTypedArray())

      val writes = ArrayList<Write>()
      val tokens = HashMap<String, MutableList<String>>()
      val deletedBySomeone = HashSet<String>()
      var counter = 0

      repeat(140) {
        val d = devices[random.nextInt(devices.size)]
        val visible = d.records().keys.toList()
        when (random.nextInt(100)) {
          in 0 until 30 -> if (visible.isNotEmpty()) {
            val id = visible.random(random)
            val field = listOf("a", "b", "c").random(random)
            val value = "${d.name}:${counter++}"
            if (d.edit(id) { text(field, value) }) {
              writes += Write(id, field, value, d.doc(id)!!.fields.getValue(field).stamp)
            }
          }
          in 30 until 55 -> if (visible.isNotEmpty()) {
            val id = visible.random(random)
            val body = d.records()[id]?.text("body") ?: return@repeat
            val lines = body.lines().dropLast(1).map { "$it\n" }.toMutableList()
            val token = "tok-${counter++}"
            lines.add(random.nextInt(lines.size + 1), "$token\n")
            if (d.edit(id) { longText("body", lines.joinToString("")) }) tokens.getOrPut(id) { mutableListOf() } += token
          }
          in 55 until 58 -> if (visible.isNotEmpty()) {
            val id = visible.random(random)
            if (d.delete(id)) deletedBySomeone += id
          }
          in 58 until 80 -> d.syncNow()
          in 80 until 86 -> d.net.online = !d.net.online
          in 86 until 89 -> when (random.nextInt(3)) {
            0 -> d.net.dropResponses = 1
            1 -> d.net.dropPushResponses = 1
            else -> d.net.dropRequests = 1
          }
          in 89 until 92 -> d.net.duplicatePushes = 1
          in 92 until 95 -> d.restart()
          in 95 until 99 -> world.advance(random.nextLong(0, 5 * 60_000L))
          else -> {
            world.advance(100 * Day)
            world.relay.purge()
          }
        }
      }

      devices.forEach {
        it.goOnline()
        it.net.dropResponses = 0
        it.net.dropPushResponses = 0
        it.net.dropRequests = 0
        it.net.duplicatePushes = 0
      }
      world.settle(*devices.toTypedArray(), rounds = 5)
      devices.forEach { assertNull("${it.name}: ${it.syncNow().failure}", it.syncNow().failure) }

      // 1. Tutti convergono.
      val final = devices[0].records()
      for (d in devices) {
        assertEquals("${d.name} diverge", final, d.records())
        assertEquals("${d.name} ha modifiche in attesa", 0, d.state().pending)
        assertTrue("${d.name} ha conflitti", d.state().conflicts.isEmpty())
      }
      assertEquals("il relay diverge", final, world.relayDocuments().filterValues { !it.isDeleted })

      // 2. Le cancellazioni: cancellato se e solo se qualcuno l'ha cancellato.
      for (id in ids) {
        assertEquals("$id cancellato", id in deletedBySomeone, id !in final)
      }

      // 2. I campi corti: vince lo stamp piu' grande fra tutte le scritture fatte.
      for ((key, group) in writes.groupBy { it.record to it.field }) {
        val (record, field) = key
        val doc = final[record] ?: continue
        val winner = group.maxByOrNull { it.stamp }!!
        assertEquals("$record.$field", winner.value, doc.text(field))
        assertEquals("$record.$field: lo stamp", winner.stamp, doc.fields.getValue(field).stamp)
      }

      // 2 e 3. Le righe aggiunte: ognuna c'e', e una volta sola.
      for ((record, list) in tokens) {
        val body = final[record]?.text("body") ?: continue
        val lines = body.lines()
        for (token in list) {
          val count = lines.count { it == token }
          if (count != 1) fail("$record: «$token» compare $count volte in:\n$body")
        }
        assertEquals("$record: righe in piu'", list.size + 1, lines.count { it.isNotEmpty() })
      }
    } finally {
      world.close()
      assertNull("il registro si tocca solo dal dispatcher dell'engine", world.wrongThread.get())
    }
  }

  @Test fun `generativa con conflitti - convergono anche i conflitti e le scelte`() {
    for (seed in 1L..200L) {
      try {
        conflicts(seed)
      } catch (e: Throwable) {
        throw AssertionError("seme $seed: ${e.message}", e)
      }
    }
  }

  private fun conflicts(seed: Long) {
    val random = Random(seed * 31 + 7)
    val world = BenchWorld()
    try {
      val devices = List(3) { i -> world.device("c$i", skewMillis = random.nextLong(-3_600_000L, 3_600_000L)) }
      val ids = List(3) { "n$it" }
      for (id in ids) devices[0].edit(id) { longText("body", "Ritrovo alle 8.\nPranzo al sacco.\n"); text("t", id) }
      world.settle(*devices.toTypedArray())
      var counter = 0
      repeat(120) {
        val d = devices[random.nextInt(devices.size)]
        val visible = d.records().keys.toList()
        when (random.nextInt(100)) {
          in 0 until 35 -> if (visible.isNotEmpty()) {
            val id = visible.random(random)
            val body = d.records()[id]?.text("body") ?: return@repeat
            val lines = body.lines().dropLast(1).toMutableList()
            if (lines.isEmpty()) return@repeat
            lines[0] = "Ritrovo alle ${d.name}-${counter++}."
            d.edit(id) { longText("body", lines.joinToString("") { "$it\n" }) }
          }
          in 35 until 50 -> {
            val conflict = d.state().conflicts.randomOrNull(random) ?: return@repeat
            val keep = conflict.revisions.shuffled(random).take(1 + random.nextInt(conflict.revisions.size))
            runBlocking { d.space().resolve(conflict.recordId, conflict.field, keep, conflict.revisions) }
          }
          in 50 until 75 -> d.syncNow()
          in 75 until 83 -> d.net.online = !d.net.online
          in 83 until 88 -> d.net.dropPushResponses = 1
          in 88 until 92 -> d.net.duplicatePushes = 1
          in 92 until 95 -> d.restart()
          else -> world.advance(random.nextLong(0, 60_000L))
        }
      }
      devices.forEach {
        it.goOnline()
        it.net.dropPushResponses = 0
        it.net.duplicatePushes = 0
      }
      world.settle(*devices.toTypedArray(), rounds = 5)
      val first = devices[0].records()
      for (d in devices) {
        assertEquals("${d.name} diverge (conflitti compresi)", first, d.records())
        assertEquals("${d.name}: conflitti", devices[0].state().conflicts, d.state().conflicts)
        assertEquals(0, d.state().pending)
      }
      // Le scelte che restano, tutte su un dispositivo: dopo, nessun conflitto da nessuna parte.
      for (conflict in devices[0].state().conflicts) {
        runBlocking { devices[0].space().resolve(conflict.recordId, conflict.field, listOf(conflict.revisions.first()), conflict.revisions) }
      }
      world.settle(*devices.toTypedArray(), rounds = 3)
      for (d in devices) {
        assertTrue("${d.name} ha ancora conflitti", d.state().conflicts.isEmpty())
        assertEquals("${d.name} diverge dopo le scelte", devices[0].records(), d.records())
      }
      assertEquals(devices[0].records(), world.relayDocuments().filterValues { !it.isDeleted })
    } finally {
      world.close()
    }
  }
}
