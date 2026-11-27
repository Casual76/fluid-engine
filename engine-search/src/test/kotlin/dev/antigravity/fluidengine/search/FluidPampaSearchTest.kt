package dev.antigravity.fluidengine.search

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.ai.bridge.AiToolClient
import dev.antigravity.fluidengine.ai.bridge.AiToolHostProvider
import dev.antigravity.fluidengine.ai.bridge.BridgeProtocol
import dev.antigravity.fluidengine.ai.bridge.RemoteCall
import dev.antigravity.fluidengine.ai.bridge.RemoteHost
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * «Cerca in Pampa» con due app finte e il ponte firmato **vero** (`AiToolHostProvider` e `AiToolClient`, F8.22): la
 * domanda va a tutte, i risultati tornano per app, coi pezzi evidenziati; un'app con un'altra firma non risponde, una
 * bloccata lo dice, un campo riservato non esce; il vettore della domanda si calcola una volta e va a tutte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidPampaSearchTest {

  /** Le app finte: ognuna col suo indice e il suo tool. */
  abstract class FakeApp : AiToolHostProvider<Unit>() {
    abstract val app: String
    abstract val label: String
    override fun registry(): ToolRegistry<Unit> =
      ToolRegistry(listOf(FluidSearchBridge.searchTool(indexes.getValue(app), exposure.getValue(app))), listOf(FluidSearchBridge.Group))
    override suspend fun context(call: RemoteCall) = Unit
    override fun domain(): String = app
    override fun appLabel(): String = label
  }

  class NotesApp : FakeApp() {
    override val app = "notes"
    override val label = "Pampa Notes"
  }

  class MailApp : FakeApp() {
    override val app = "mail"
    override val label = "Pampa Mail"
  }

  /** Un'app firmata con un'altra chiave (una build di sviluppo): il cancello del ponte la rifiuta. */
  class StrangerApp : FakeApp() {
    override val app = "classeviva"
    override val label = "ClasseViva di sviluppo"
    override fun compareSignatures(callingUid: Int, ownUid: Int): Int = PackageManager.SIGNATURE_NO_MATCH
  }

  /** L'app che tiene il modello (PampAI, domanda 1 del rapporto) e lo presta alle altre col ponte. */
  class ModelApp : AiToolHostProvider<Unit>() {
    override fun registry(): ToolRegistry<Unit> =
      ToolRegistry(listOf(FluidSearchBridge.embedTool(FixtureEmbedder())), listOf(FluidSearchBridge.Group))
    override suspend fun context(call: RemoteCall) = Unit
    override fun domain(): String = "pampai"
    override fun appLabel(): String = "PampAI"
  }

  companion object {
    val indexes = HashMap<String, FluidSearchIndex>()
    val exposure = HashMap<String, FluidSearchExposure>()
    var notesLocked = false
  }

  private val notes = FluidPampaApp("dev.pampa.notes", "dev.pampa.notes.ai.tools", "Pampa Notes")
  private val mail = FluidPampaApp("dev.pampa.mail", "dev.pampa.mail.ai.tools", "Pampa Mail")
  private val stranger = FluidPampaApp("dev.pampa.classeviva.dev", "dev.pampa.classeviva.ai.tools", "ClasseViva di sviluppo")
  private val modelAuthority = "dev.pampa.pampai.ai.tools"
  private lateinit var client: AiToolClient

  @Before
  fun setUp() = runBlocking {
    notesLocked = false
    for (app in listOf("notes", "mail", "classeviva")) {
      val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
      index.put(FluidSearchSamples.of(app))
      FluidSemanticIndexer(index, FixtureEmbedder()).update()
      indexes[app] = index
    }
    exposure["notes"] = FluidSearchExposure(kinds = null, kindLabels = FluidSearchSamples.kinds, locked = { notesLocked })
    exposure["mail"] = FluidSearchExposure(kinds = null, kindLabels = FluidSearchSamples.kinds)
    exposure["classeviva"] = FluidSearchExposure(kinds = null, kindLabels = FluidSearchSamples.kinds)
    Robolectric.buildContentProvider(NotesApp::class.java).create(notes.authority)
    Robolectric.buildContentProvider(MailApp::class.java).create(mail.authority)
    Robolectric.buildContentProvider(StrangerApp::class.java).create(stranger.authority)
    Robolectric.buildContentProvider(ModelApp::class.java).create(modelAuthority)
    client = AiToolClient(ApplicationProvider.getApplicationContext())
  }

  @After
  fun tearDown() {
    indexes.values.forEach { it.close() }
    indexes.clear()
  }

  /** La scoperta, come `discoverApps`: il catalogo di ogni app (che stabilisce il protocollo 2) e il tool della ricerca. */
  private suspend fun discovered(): List<FluidPampaApp> = listOf(notes, mail, stranger).filter { app ->
    val catalog = client.catalog(RemoteHost(app.packageName, app.authority, app.label))
    catalog != null && catalog.tools.any { it.name == FluidSearchBridge.ToolName }
  }

  @Test
  fun laScopertaTrovaSoloLeAppFirmateComeMe() = runBlocking {
    assertEquals(listOf(notes, mail), discovered())
    assertEquals(2, client.negotiation(notes.authority).protocol)
    assertTrue(BridgeProtocol.CAP_TYPED_RESULTS in client.negotiation(mail.authority).capabilities)
  }

  @Test
  fun laDomandaVaATutteEIRisultatiTornanoPerApp() = runBlocking {
    discovered()
    val search = FluidPampaSearch(client, discover = { listOf(notes, mail, stranger) })
    val steps = search.search("riunone ").toList()
    // Il primo passo ha tutte le app in attesa, l'ultimo nessuna.
    assertEquals(3, steps.first().pending)
    val last = steps.last()
    assertEquals(0, last.pending)
    val byApp = last.groups.associateBy { it.app.label }
    val noteHits = byApp.getValue("Pampa Notes").hits
    assertTrue(noteHits.map { it.id }.containsAll(listOf("n1", "n2", "n9", "n10")))
    assertEquals("Note", noteHits.first { it.id == "n1" }.kindLabel)
    assertEquals(listOf("Riunione"), noteHits.first { it.id == "n1" }.title.highlighted)
    assertTrue(byApp.getValue("Pampa Mail").hits.isEmpty())
    // L'app con un'altra firma non risponde: il cancello del ponte la ferma.
    assertNotNull(byApp.getValue("ClasseViva di sviluppo").error)
    // Si mostrano Notes (con i risultati) e l'app che non ha risposto; Mail senza risultati no.
    assertEquals(listOf("Pampa Notes", "ClasseViva di sviluppo"), last.shown.map { it.app.label })
  }

  @Test
  fun ilSignificatoSiCalcolaUnaVoltaEVaATutte() = runBlocking {
    discovered()
    val model = FixtureEmbedder()
    val search = FluidPampaSearch(client, discover = { listOf(notes, mail) }, embedder = model)
    val last = search.search("la mail del dentista").toList().last()
    assertEquals(listOf("la mail del dentista"), model.asked)
    assertTrue(last.meaning)
    val mailHits = last.groups.first { it.app == mail }.hits
    assertEquals(setOf("m1", "m2"), mailHits.take(2).map { it.id }.toSet())
    assertTrue(mailHits.first().byMeaning)
    // Notes trova la nota del controllo dei denti per significato.
    assertTrue("n4" in last.groups.first { it.app == notes }.hits.map { it.id })
  }

  @Test
  fun ilModelloPrestatoDaUnAltraApp() = runBlocking {
    discovered()
    assertNotNull(client.catalog(RemoteHost("dev.pampa.pampai", modelAuthority, "PampAI")))
    val remote = FluidSearchBridge.remoteEmbedder(client, modelAuthority)!!
    assertEquals(FluidEmbeddingSpace.EmbeddingGemma2, remote.space)
    val local = FixtureEmbedder().embed(listOf("la mail del dentista")).single()
    val lent = remote.embed(listOf("la mail del dentista")).single()
    assertTrue(local.contentEquals(lent))
    // «Cerca in Pampa» con il modello prestato: la stessa risposta di quello locale.
    val last = FluidPampaSearch(client, discover = { listOf(mail) }, embedder = remote).search("la mail del dentista").toList().last()
    assertTrue(last.meaning)
    assertEquals(setOf("m1", "m2"), last.groups.single().hits.take(2).map { it.id }.toSet())
    // Un'app che non presta il modello: null.
    assertNull(FluidSearchBridge.remoteEmbedder(client, mail.authority))
  }

  @Test
  fun unAppBloccataLoDiceSenzaDati() = runBlocking {
    discovered()
    notesLocked = true
    val last = FluidPampaSearch(client, discover = { listOf(notes) }).search("riunione ").toList().last()
    val group = last.groups.single()
    assertTrue(group.locked)
    assertTrue(group.hits.isEmpty())
    assertNull(group.error)
  }

  /**
   * R1 (Sol, bloccante): la persona blocca l'app mentre il modello calcola il vettore della domanda. Il tool aveva letto
   * il blocco una volta, prima delle sospensioni, e consegnava titoli e pezzi con `locked=false`.
   */
  @Test
  fun bloccataAMetaRicercaNonConsegnaDati() = runBlocking {
    var locked = false
    val model = FixtureEmbedder()
    val lockingModel = object : FluidTextEmbedder {
      override val space: FluidEmbeddingSpace = model.space
      override suspend fun embed(texts: List<String>): List<FloatArray> {
        locked = true // la persona blocca l'app durante il calcolo
        return model.embed(texts)
      }
    }
    val tool = FluidSearchBridge.searchTool<Unit>(indexes.getValue("notes"), FluidSearchExposure(kinds = null, kindLabels = FluidSearchSamples.kinds, locked = { locked }, embedder = lockingModel))
    val out = tool.run(kotlinx.serialization.json.buildJsonObject { put("query", kotlinx.serialization.json.JsonPrimitive("riunione")) }, Unit)
    assertTrue("il modello è stato chiamato", locked)
    val data = out.result!!.data!!
    assertEquals("true", (data["locked"] as kotlinx.serialization.json.JsonPrimitive).content)
    assertEquals(0, (data["total"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())
    assertTrue((data["hits"] as kotlinx.serialization.json.JsonArray).isEmpty())
    assertFalse(out.text.contains("Riunione"))
  }

  @Test
  fun iCampiRiservatiNonEsconoMai() = runBlocking {
    val secretSchema = FluidSearchSchema("riservate", 1, listOf(FluidSearchField("title", 3f), FluidSearchField("secret", sensitive = true)))
    val cipher = object : FluidSearchCipher {
      override suspend fun seal(plain: ByteArray, associated: ByteArray) = plain.reversedArray()
      override suspend fun open(sealed: ByteArray, associated: ByteArray) = sealed.reversedArray()
    }
    val index = FluidSearchIndex.inMemory(secretSchema, cipher)
    index.put(FluidSearchDocument("w", mapOf("title" to "Rete di casa", "secret" to "girasole"), kind = "nota"))
    val tool = FluidSearchBridge.searchTool<Unit>(index, FluidSearchExposure(kinds = null))
    val args = kotlinx.serialization.json.buildJsonObject { put("query", kotlinx.serialization.json.JsonPrimitive("girasole")) }
    val out = tool.run(args, Unit)
    assertEquals(0, (out.result!!.data!!["total"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())
    val byTitle = tool.run(kotlinx.serialization.json.buildJsonObject { put("query", kotlinx.serialization.json.JsonPrimitive("rete")) }, Unit)
    assertFalse(byTitle.text.contains("girasole"))
    assertFalse(byTitle.result!!.data.toString().contains("girasole"))
    index.close()
  }

  @Test
  fun iVettoriViaggianoInteri() {
    val v = FloatArray(768) { it / 768f }
    val back = FluidSearchBridge.decodeVector(FluidSearchBridge.encodeVector(v))!!
    assertTrue(v.contentEquals(back))
  }
}
