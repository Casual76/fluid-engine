package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.people.crypto.NearbyHandshake
import dev.antigravity.fluidengine.people.crypto.PeopleLabels
import dev.antigravity.fluidengine.people.nearby.FakeNearbyLink
import dev.antigravity.fluidengine.people.nearby.NearbyLink
import dev.antigravity.fluidengine.people.nearby.NearbyPairing
import dev.antigravity.fluidengine.people.nearby.NearbyRefused
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** La vicinanza con la runa (`progetto.md` § 3.6, «Vicino»): le schede passano dirette, e tutti e due sono verificati. */
class NearbyPairingTest {

  @Test
  fun `due telefoni vicini - stessa runa, conferma, verificati subito e senza richieste al server`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    val before = server.requestLog.size
    val (la, lb) = FakeNearbyLink.pair()
    val (sa, sb) = coroutineScope {
      val a = async { NearbyPairing(alessio.client).handshake(la) }
      val b = async { NearbyPairing(marco.client).handshake(lb) }
      a.await() to b.await()
    }
    assertEquals(sa.rune, sb.rune)
    assertEquals(marco.code, sa.peer.code)
    assertEquals(alessio.code, sb.peer.code)
    assertTrue("il saluto non passa dal server", server.requestLog.size == before)
    val (ca, cb) = coroutineScope {
      val a = async { sa.confirm() }
      val b = async { sb.confirm() }
      a.await() to b.await()
    }
    assertEquals(ca.chatId, cb.chatId)
    assertTrue(alessio.client.isVerified(marco.code))
    assertTrue(marco.client.isVerified(alessio.code))
    assertEquals(ContactVia.NEARBY, alessio.client.contact(marco.code)!!.via)
    // Al server sono andate solo le due impronte dei gettoni (e la rubrica agli altri miei dispositivi): niente richieste.
    assertTrue(server.requestLog.drop(before).none { it.path.contains("/buste") && it.body.toString().contains("richiesta") })
    // E si scrivono subito, anche sull'altro dispositivo di Alessio, che ha la rubrica dal primo.
    marco.client.sendMessage(alessio.code, buildJsonObject { put("t", "messaggio"); put("text", "eccomi") })
    alessio.device(1).client.pickup()
    val msg = alessio.device(0).client.pickup().filterIsInstance<PeopleEvent.Message>().single()
    assertEquals("eccomi", msg.payload["text"]!!.jsonPrimitive.content)
    assertTrue(alessio.device(1).client.isVerified(marco.code))
  }

  @Test
  fun `chi si mette in mezzo nella stanza fa vedere due rune diverse`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    val intruso = BenchPerson(server, "Intruso", deviceCount = 1)
    // Due canali: Alessio ↔ intruso e intruso ↔ Marco. L'intruso non ha le chiavi di nessuno dei due.
    val (la, li1) = FakeNearbyLink.pair()
    val (li2, lb) = FakeNearbyLink.pair()
    val (sa, sia, sib, sb) = coroutineScope {
      val a = async { NearbyPairing(alessio.client).handshake(la) }
      val i1 = async { NearbyPairing(intruso.client).handshake(li1) }
      val i2 = async { NearbyPairing(intruso.client).handshake(li2) }
      val b = async { NearbyPairing(marco.client).handshake(lb) }
      listOf(a.await(), i1.await(), i2.await(), b.await())
    }
    assertEquals("Alessio vede l'intruso, non Marco", intruso.code, sa.peer.code)
    assertEquals(sa.rune, sia.rune)
    assertEquals(sb.rune, sib.rune)
    assertNotEquals("le due rune non tornano", sa.rune, sb.rune)
  }

  @Test
  fun `un saluto con la scheda di un altro, senza le sue chiavi, viene rifiutato`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    val (la, lb) = FakeNearbyLink.pair()
    // Il falso Marco manda la scheda vera di Marco, ma non sa firmare con le sue chiavi.
    val fake = object : NearbyLink by lb {}
    coroutineScope {
      val a = async { runCatching { NearbyPairing(alessio.client).handshake(la) } }
      val eph = dev.antigravity.fluidengine.people.crypto.X25519Key.random()
      fake.send(byteArrayOf(1) + NearbyHandshake.hello(marco.client.identity().card, NearbyHandshake.newChallenge(), eph.publicKey))
      lb.receive()
      fake.send(byteArrayOf(2) + ByteArray(64) { 7 })
      val r = a.await()
      assertTrue(r.exceptionOrNull() is NearbyRefused)
    }
    assertTrue(PeopleLabels.NEARBY_AUTH.isNotEmpty())
    try {
      NearbyHandshake.readHello(byteArrayOf(1, 2, 3))
      fail()
    } catch (_: NearbyHandshake.Refused) {
    }
  }
}
