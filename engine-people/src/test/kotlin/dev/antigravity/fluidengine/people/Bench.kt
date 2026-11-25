package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import java.io.File
import java.nio.file.Files

/** Un dispositivo finto del banco: la sua cassaforte, la sua cartella, il suo client verso il server finto. */
class BenchDevice(val person: BenchPerson, val device: FakePeopleServer.Device, val vault: MemoryPersonVault = MemoryPersonVault()) {
  val dir: File = Files.createTempDirectory("persone").toFile()
  val persona = PersonaStore(dir, vault, clock = { person.server.now })
  val stateStore = PeopleStateStore(File(dir, "persone.bin"), vault)
  val client = PeopleClient(persona, stateStore, PeopleApi(device.http, device.auth), clock = { person.server.now })
}

/**
 * Una persona del banco con [devices] dispositivi (`d\server-e-chiavi.md` § 13). La persona nasce sul primo; gli altri
 * la ritrovano dalla copia sotto `K_com(persona)`, come un telefono nuovo, senza chiavi copiate a mano.
 */
class BenchPerson(val server: FakePeopleServer, val name: String, deviceCount: Int = 2, publish: Boolean = true) {
  val account = server.newAccount(deviceCount)
  /** `K_com(persona)` dell'epoca 1: la ricava ogni dispositivo dalla chiave dei dati (qui è uguale per costruzione). */
  val kPersona: ByteArray = PampaCrypto.random(32)
  val devices: List<BenchDevice> = account.devices.map { BenchDevice(this, it) }
  val first: BenchDevice get() = devices[0]
  val client: PeopleClient get() = first.client

  init {
    first.persona.create(name)
    val copy = first.persona.exportSealed(account.id, 1, kPersona, 1)
    for (d in devices.drop(1)) d.persona.importSealed(account.id, copy, 1) { kPersona }
    if (publish) kotlinx.coroutines.runBlocking { first.client.publish() }
  }

  val code: String get() = first.persona.identity()!!.code
  val mailbox: String get() = first.persona.identity()!!.mailbox
  fun device(i: Int) = devices[i]
}
