package dev.antigravity.fluidengine.diagnostics

import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * R3 (review `sol-ai`, n. 5) · Gli indirizzi IPv6 in forma compressa (`2001:db8::1`), con la zona (`fe80::1%wlan0`),
 * con un IPv4 in coda (`::ffff:192.0.2.10`) e fra parentesi quadre in un indirizzo web non escono dal registro. Gli
 * indirizzi sono quelli della documentazione (RFC 3849 e RFC 5737). Sul JVM.
 */
class FluidLogR3Test {
  @After
  fun tearDown() {
    FluidLog.clear()
  }

  /** L'indirizzo [address] scritto per intero, non dentro un'altra parola o un altro indirizzo. */
  private fun isolated(address: String) = Regex("(?i)(?<![0-9a-z:.])" + Regex.escape(address) + "(?![0-9a-z])")

  @Test
  fun compressedIpv6NeverLeaves() {
    val cases = mapOf(
      "connessione a 2001:db8::1 rifiutata" to "connessione a «[ip]» rifiutata",
      "server 2001:DB8:AC10::FE01 lento" to "server «[ip]» lento",
      "loopback ::1 pronto" to "loopback «[ip]» pronto",
      "router fe80::1ff:fe23:4567:890a%wlan0 ok" to "router «[ip]» ok",
      "mappato ::ffff:192.0.2.10 chiuso" to "mappato «[ip]» chiuso",
      "verso 2001:db8::1." to "verso «[ip]».",
      "2001:db8::7: timeout" to "«[ip]»: timeout",
      "da [2001:db8:0:0::7]:443" to "da [«[ip]»]:443",
      "GET http://[2001:db8::42]:8080/v1/students/1234567?x=1" to "GET http://«[ip]»/«…»",
      "GET https://[2001:db8::42]" to "GET https://«[ip]»",
    )
    for ((input, expected) in cases) assertEquals(input, expected, FluidPrivacyScrubber.scrub(input))
  }

  @Test
  fun theExportedLogHasNoCompressedIpv6() {
    FluidLog.clear()
    FluidLog.warn("rete", "Timeout verso 2001:db8::1 dopo 15 s")
    FluidLog.event("rete", "GET http://[2001:db8::42]:8080/v1/students/1234567?x=1")
    FluidLog.event("rete", "Gateway fe80::1%wlan0, dns ::ffff:192.0.2.10")
    val all = FluidLog.export(zone = ZoneOffset.UTC).joinToString("\n")
    for (address in listOf("2001:db8::1", "2001:db8::42", "fe80::1", "::ffff:192.0.2.10", "192.0.2.10")) {
      assertFalse("$address è uscito: $all", isolated(address).containsMatchIn(all))
    }
    assertFalse("il percorso è uscito: $all", Regex("(?<!\\d)1234567(?!\\d)").containsMatchIn(all))
  }

  @Test
  fun textWithColonsStaysReadable() {
    val keep = listOf(
      "Il 26/11/2026 alle 14:03:59",
      "Ore 10:30, 11:45 e 12:00",
      "FluidLog::export e Note::class.java",
      "Rapporto 3:2, punteggio 1:0",
      "Sincronizzate 3 note in 1200 ms",
    )
    for (text in keep) assertEquals(text, FluidPrivacyScrubber.scrub(text))
  }
}
