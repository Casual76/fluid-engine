package dev.antigravity.fluidengine.ai.bridge

import dev.antigravity.fluidengine.ai.tools.ConfirmationText
import dev.antigravity.fluidengine.ai.tools.Schema
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un ospite finto: risponde come gli si dice, e annota cosa gli e' stato chiesto. */
private class FakeHost(
  private val runResult: (Boolean) -> RemoteResult,
  private val statusResults: MutableList<RemoteResult> = mutableListOf(),
  private val describeText: Pair<String?, String?>? = null,
) : BridgeCalls {
  var confirmedSeen: Boolean? = null
  var cancelled = false
  var statusCalls = 0

  override suspend fun describe(authority: String, name: String, args: JsonObject, language: String): Pair<String?, String?>? = describeText

  override suspend fun run(authority: String, name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): RemoteResult {
    confirmedSeen = confirmed
    return runResult(confirmed)
  }

  override suspend fun status(authority: String, jobId: String): RemoteResult {
    statusCalls++
    return statusResults.removeFirstOrNull() ?: RemoteResult.Status(BridgeProtocol.STATE_RUNNING, "in corso", 0.5, null, false, emptyList())
  }

  override suspend fun cancel(authority: String, jobId: String) {
    cancelled = true
  }

  override fun readPart(part: RemotePart): ByteArray? = "ciao".toByteArray()
}

/** Il cliente finto: dice si', dice no, o dice che le azioni sono spente. */
private class FakeToolHost(
  private val answer: ToolOutput? = null,
  private val actions: Boolean = true,
) : RemoteToolHost<Unit> {
  var asked: ConfirmationText? = null
  override suspend fun confirm(ctx: Unit, toolName: String, text: ConfirmationText): ToolOutput? {
    asked = text
    return answer
  }

  override fun actionsEnabled(ctx: Unit): Boolean = actions
  override val longRunningTimeoutMillis: Long get() = 3_000L
}

private fun info(name: String, needsConfirmation: Boolean = false, longRunning: Boolean = false, action: Boolean = false) =
  RemoteToolInfo(name, "g", "descrizione", Schema.obj(emptyMap()), needsConfirmation, longRunning, action)

private val catalog = RemoteCatalog(
  protocol = 1, authority = "a", packageName = "p", appId = "p", domain = "cv", appLabel = "ClasseViva", appVersion = "1",
  hint = null, vocabulary = emptyList(), categories = emptyList(),
  groups = listOf(RemoteGroupInfo("g", "g", "", null, null, loadsWithCategory = true, action = false)),
  tools = emptyList(),
)

private val group = RemoteGroup("cv_g", "g", "", null, false, remoteId = "g")

class RemoteToolTest {

  @Test
  fun confirmationIsAskedByTheCallerAndPassedToTheHost() = runTest {
    val calls = FakeHost(runResult = { RemoteResult.Done("fatto", false, emptyList()) }, describeText = "Segnare come letta?" to "Gita a Roma")
    val host = FakeToolHost()
    val tool = RemoteTool(calls, catalog, info("presa_visione", needsConfirmation = true, action = true), group, host)
    val out = tool.run(buildJsonObject { }, Unit)
    assertEquals("Segnare come letta?", host.asked?.title)
    assertEquals("Gita a Roma", host.asked?.detail)
    assertEquals(true, calls.confirmedSeen)
    assertEquals("fatto", out.text)
  }

  @Test
  fun aRefusedConfirmationNeverReachesTheHost() = runTest {
    val calls = FakeHost(runResult = { RemoteResult.Done("non doveva succedere", false, emptyList()) })
    val host = FakeToolHost(answer = ToolOutput("l'utente ha annullato: non fatto"))
    val tool = RemoteTool(calls, catalog, info("presa_visione", needsConfirmation = true, action = true), group, host)
    val out = tool.run(buildJsonObject { }, Unit)
    assertEquals("l'utente ha annullato: non fatto", out.text)
    assertNull(calls.confirmedSeen)
  }

  @Test
  fun actionsOffStopTheCallBeforeItLeavesTheApp() = runTest {
    val calls = FakeHost(runResult = { RemoteResult.Done("non doveva succedere", false, emptyList()) })
    val tool = RemoteTool(calls, catalog, info("apri", action = true), group, FakeToolHost(actions = false))
    val out = tool.run(buildJsonObject { }, Unit)
    assertTrue(out.text.contains("disattivate"))
    assertNull(calls.confirmedSeen)
  }

  @Test
  fun aLongJobIsFollowedUntilItFinishes() = runTest {
    val calls = FakeHost(
      runResult = { RemoteResult.Job("j1") },
      statusResults = mutableListOf(
        RemoteResult.Status(BridgeProtocol.STATE_RUNNING, "scarico", 0.3, null, false, emptyList()),
        RemoteResult.Status(BridgeProtocol.STATE_DONE, null, 1.0, "installata la 1.2.0", false, emptyList()),
      ),
    )
    val tool = RemoteTool(calls, catalog, info("installa", longRunning = true, action = true), group, FakeToolHost())
    val out = tool.run(buildJsonObject { }, Unit)
    assertEquals("installata la 1.2.0", out.text)
    assertEquals(2, calls.statusCalls)
  }

  @Test
  fun aFailedJobBecomesAnError() = runTest {
    val calls = FakeHost(
      runResult = { RemoteResult.Job("j1") },
      statusResults = mutableListOf(RemoteResult.Status(BridgeProtocol.STATE_FAILED, "niente rete", null, null, false, emptyList())),
    )
    val tool = RemoteTool(calls, catalog, info("installa", longRunning = true), group, FakeToolHost())
    val out = tool.run(buildJsonObject { }, Unit)
    assertTrue(out.text.startsWith("errore:"))
    assertTrue(out.text.contains("niente rete"))
  }

  @Test
  fun partsComeBackAsContentForTheModel() = runTest {
    val calls = FakeHost(runResult = { RemoteResult.Done("ecco l'allegato", true, listOf(RemotePart(RemotePart.KIND_DOCUMENT, "content://x/1", "application/pdf", "circolare.pdf"))) })
    val tool = RemoteTool(calls, catalog, info("allegato_leggi"), group, FakeToolHost())
    val out = tool.run(buildJsonObject { }, Unit)
    assertEquals(1, out.parts.size)
    assertTrue(out.escalate)
  }

  @Test
  fun anErrorFromTheHostStaysAnError() = runTest {
    val calls = FakeHost(runResult = { RemoteResult.Error("l'app non risponde") })
    val tool = RemoteTool(calls, catalog, info("voti_media"), group, FakeToolHost())
    assertEquals("errore: l'app non risponde", tool.run(buildJsonObject { }, Unit).text)
  }
}
