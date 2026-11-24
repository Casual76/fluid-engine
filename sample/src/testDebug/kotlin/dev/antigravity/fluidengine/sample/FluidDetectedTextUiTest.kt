package dev.antigravity.fluidengine.sample

import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import android.widget.Magnifier
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidDetectedText
import dev.antigravity.fluidengine.ui.fluid.FluidDetectedTextKind
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidTextDetection
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w420dp-h900dp-xhdpi")
class FluidDetectedTextUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var classifierManager: TextClassificationManager
  private lateinit var originalClassifier: TextClassifier
  private val fake = FakeClassifier()
  private val detection = FluidTextDetection(locale = Locale.ITALY)

  @Before fun setUp() {
    classifierManager = checkNotNull(compose.activity.applicationContext.getSystemService(TextClassificationManager::class.java))
    originalClassifier = classifierManager.textClassifier
    classifierManager.setTextClassifier(fake)
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun tearDown() {
    fake.release.countDown()
    classifierManager.setTextClassifier(originalClassifier)
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun scene(
    reducedMotion: Boolean = true,
    theme: () -> ThemeMode = { ThemeMode.LIGHT },
    withHost: Boolean = true,
    content: @Composable () -> Unit,
  ) {
    compose.setContent {
      val host = if (withHost) rememberFluidGlassModalHostState() else null
      val backdrop = rememberGlassBackdrop()
      FluidTheme(settings = EngineSettings(themeMode = theme())) {
        CompositionLocalProvider(
          LocalFluidGlassModalHostState provides host,
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reducedMotion) 0f else 1f),
        ) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(MaterialTheme.colorScheme.surface)) {
              Box(Modifier.padding(start = 26.dp, top = 190.dp, end = 26.dp)) { content() }
            }
            if (host != null) FluidGlassModalHost(state = host, backdrop = backdrop)
          }
        }
      }
    }
  }

  private fun layoutNode(raw: String) = compose.onNode(
    hasText(raw) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
    useUnmergedTree = true,
  ).fetchSemanticsNode()

  private fun layout(raw: String): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    val getLayout = layoutNode(raw).config[SemanticsActions.GetTextLayoutResult].action
    compose.runOnIdle { getLayout?.invoke(layouts) }
    return layouts.single()
  }

  private fun touch(raw: String, word: String, hold: Boolean = false) {
    val point = layoutNode(raw).positionInRoot + layout(raw).getBoundingBox(raw.indexOf(word) + 1).center
    compose.onRoot().performTouchInput { if (hold) longClick(point) else click(point) }
  }

  private fun waitForLinks(count: Int) {
    compose.waitUntil(8_000) { compose.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size >= count }
  }

  private fun tapMenu(label: String) {
    compose.waitUntil(5_000) { compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
    val position = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center
    compose.onRoot().performTouchInput { click(position) }
    compose.waitForIdle()
  }

  private fun nextIntent(): Intent = checkNotNull(shadowOf(compose.activity).nextStartedActivity)

  @Test fun realTapsOpenTheMenuForDataAndDispatchTheWebLink() {
    val cases = listOf(
      TapCase("il 28/11/2026", Intent.ACTION_INSERT, "content", "Add to calendar"),
      TapCase("alle 8:15", Intent.ACTION_INSERT, "content", "Add to calendar"),
      TapCase(FakeClassifier.Address, Intent.ACTION_VIEW, "geo", "Open in Maps"),
      TapCase("+39 02 5550 1234", Intent.ACTION_DIAL, "tel", "Call"),
      TapCase("anna@example.org", Intent.ACTION_SENDTO, "mailto", "Send email"),
      TapCase("https://example.org/visita", Intent.ACTION_VIEW, "https"),
    )
    val raw = cases.joinToString("\n") { it.phrase }
    scene {
      FluidDetectedText(raw, detection = detection.copy(additionalActions = {
        if (it.kind == FluidDetectedTextKind.Date) listOf(FluidContextAction("Aggiungi ai compiti") {}) else emptyList()
      }))
    }
    waitForLinks(cases.size)
    for ((phrase, action, scheme, label) in cases) {
      touch(raw, phrase)
      if (label != null) {
        assertNull(phrase, shadowOf(compose.activity).nextStartedActivity)
        if (phrase.startsWith("il")) {
          val preview = LocalDate.of(2026, 11, 28).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.ITALY))
          compose.onNodeWithText(preview).assertExists()
          compose.onNodeWithText("Aggiungi ai compiti").assertExists()
          compose.onNodeWithText("Show in calendar").assertExists()
        }
        tapMenu(label)
      } else {
        compose.onNodeWithText("Open link").assertDoesNotExist()
      }
      val intent = nextIntent()
      assertEquals(phrase, action, intent.action)
      assertEquals(phrase, scheme, intent.scheme)
      when (scheme) {
        "geo" -> assertEquals("0,0?q=${FakeClassifier.Address}", intent.data?.schemeSpecificPart)
        "tel" -> assertEquals("+390255501234", intent.data?.schemeSpecificPart)
        "mailto" -> assertEquals(phrase, intent.data?.schemeSpecificPart)
        "https" -> assertEquals(phrase, intent.data.toString())
      }
      if (action == Intent.ACTION_INSERT) {
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        val allDay = phrase.startsWith("il")
        assertEquals(allDay, intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, !allDay))
        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1)
        val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1)
        assertEquals(if (allDay) 86_400_000L else 3_600_000L, end - begin)
        if (allDay) assertEquals(Instant.parse("2026-11-28T00:00:00Z").toEpochMilli(), begin)
      }
    }
    assertFalse(fake.calledOnMain.get())
  }

  @Test fun calendarDayAddressDirectionsAndContactActionsTranslateToAndroidIntents() {
    val date = "il 28/11/2026"
    val time = "alle 8:15"
    val phone = "+39 02 5550 1234"
    val email = "anna@example.org"
    val raw = listOf(date, time, FakeClassifier.Address, phone, email).joinToString("\n")
    scene { FluidDetectedText(raw, detection = detection) }
    waitForLinks(5)
    for ((phrase, day) in listOf(date to LocalDate.of(2026, 11, 28), time to LocalDate.now())) {
      touch(raw, phrase)
      tapMenu("Show in calendar")
      val intent = nextIntent()
      assertEquals(Intent.ACTION_VIEW, intent.action)
      val data = checkNotNull(intent.data)
      assertEquals(CalendarContract.CONTENT_URI.authority, data.authority)
      assertEquals("time", data.pathSegments.first())
      assertEquals(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), ContentUris.parseId(data))
    }
    touch(raw, FakeClassifier.Address)
    tapMenu("Directions")
    val directions = nextIntent()
    assertEquals(Intent.ACTION_VIEW, directions.action)
    val destination = checkNotNull(directions.data)
    assertEquals("https", destination.scheme)
    assertEquals("/maps/dir/", destination.path)
    assertEquals("1", destination.getQueryParameter("api"))
    assertEquals(FakeClassifier.Address, destination.getQueryParameter("destination"))

    for ((phrase, field, expected) in listOf(
      Triple(phone, ContactsContract.Intents.Insert.PHONE, "+390255501234"),
      Triple(email, ContactsContract.Intents.Insert.EMAIL, email),
    )) {
      touch(raw, phrase)
      tapMenu("Add to contacts")
      val intent = nextIntent()
      assertEquals(Intent.ACTION_INSERT_OR_EDIT, intent.action)
      assertEquals(ContactsContract.Contacts.CONTENT_ITEM_TYPE, intent.type)
      assertEquals(expected, intent.getStringExtra(field))
    }
  }

  @Test fun webLinksRemainUsableWithoutTheModalHost() {
    val date = "il 28/11/2026"
    val url = "https://example.org/visita"
    val raw = "$date\n$url"
    scene(withHost = false) { FluidDetectedText(raw, detection = detection) }
    waitForLinks(1)
    compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    touch(raw, date)
    assertNull(shadowOf(compose.activity).nextStartedActivity)
    touch(raw, url)
    assertEquals(url, nextIntent().data.toString())
  }

  @Test fun realLongPressOpensEachMenuWithReducedMotionAndCopy() {
    val cases = listOf(
      "il 28/11/2026" to "Add to calendar",
      "alle 8:15" to "Add to calendar",
      FakeClassifier.Address to "Open in Maps",
      "+39 02 5550 1234" to "Call",
      "anna@example.org" to "Send email",
      "https://example.org/visita" to "Open link",
    )
    val raw = cases.joinToString("\n") { it.first }
    scene { FluidDetectedText(raw, detection = detection) }
    waitForLinks(cases.size)
    val clipboard = checkNotNull(compose.activity.getSystemService(ClipboardManager::class.java))
    for ((phrase, primary) in cases) {
      touch(raw, phrase, hold = true)
      compose.onNodeWithText(primary).assertExists()
      if (phrase.startsWith('+')) compose.onNodeWithText("Send message").assertExists()
      tapMenu(if (phrase == FakeClassifier.Address) "Copy address" else "Copy")
      assertEquals(phrase, clipboard.primaryClip?.getItemAt(0)?.text)
    }
    touch(raw, "+39 02 5550 1234", hold = true)
    tapMenu("Send message")
    val sms = nextIntent()
    assertEquals(Intent.ACTION_SENDTO, sms.action)
    assertEquals("smsto", sms.scheme)
    assertEquals("+390255501234", sms.data?.schemeSpecificPart)
  }

  @Test
  @Config(shadows = [MagnifierWithoutSurface::class])
  fun linkSemanticsOpenMenuAndOrdinaryTextStillSelects() {
    val raw = "Quaderno\nil 28/11/2026."
    var chosen: String? = null
    scene(reducedMotion = false) {
      FluidDetectedText(raw, detection = detection.copy(additionalActions = {
        listOf(FluidContextAction("Aggiungi ai compiti") { chosen = it.text })
      }))
    }
    waitForLinks(1)
    compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    compose.onNode(hasClickAction(), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    tapMenu("Aggiungi ai compiti")
    assertEquals("il 28/11/2026", chosen)
    touch(raw, "Quaderno", hold = true)
    val selectionHandle = SemanticsMatcher("cursore della selezione") { node ->
      node.config.any { it.key.name == "SelectionHandleInfo" }
    }
    compose.onAllNodes(selectionHandle, useUnmergedTree = true).assertCountEquals(2)
  }

  @Test fun markdownKeepsAuthorLinkAndInlineCode() {
    val displayed = "Domani alle 8. Portale. Segnaposto ore 16:30."
    var opened: String? = null
    scene {
      FluidMarkdown(
        "Domani alle 8. [Portale](https://example.org/portale). Segnaposto `ore 16:30`.",
        onLinkClick = { opened = it },
        detection = detection,
      )
    }
    waitForLinks(2)
    val links = layout(displayed).layoutInput.text.getLinkAnnotations(0, displayed.length)
    assertEquals(2, links.size)
    assertTrue(links[0].item is LinkAnnotation.Url || links[1].item is LinkAnnotation.Url)
    touch(displayed, "Portale")
    assertEquals("https://example.org/portale", opened)
  }

  @Test fun longTextPaintsBeforeWorkerAndDoesNotReflow() {
    fake.blocked.set(true)
    val raw = FakeClassifier.Address + "\n" + "La lezione si svolge in aula. ".repeat(690)
    val draws = AtomicInteger()
    scene(reducedMotion = false) {
      FluidDetectedText(raw, detection = detection, modifier = Modifier.drawWithContent {
        draws.incrementAndGet()
        drawContent()
      })
    }
    assertTrue(fake.entered.await(5, TimeUnit.SECONDS))
    val before = layout(raw)
    assertEquals(0, before.layoutInput.text.getLinkAnnotations(0, raw.length).size)
    // Robolectric può misurare la vista senza far partire il renderer della finestra.
    compose.runOnIdle {
      val view = compose.activity.window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      try {
        view.draw(Canvas(bitmap))
      } finally {
        bitmap.recycle()
      }
    }
    assertTrue(draws.get() > 0)
    fake.release.countDown()
    waitForLinks(1)
    val after = layout(raw)
    assertEquals(before.lineCount, after.lineCount)
    assertEquals(before.size, after.size)
    assertEquals((0 until before.lineCount).map { before.getLineStart(it) }, (0 until after.lineCount).map { after.getLineStart(it) })
    assertTrue(fake.calls.get() > 1)
    assertFalse(fake.calledOnMain.get())
    compose.waitForIdle()
    val settled = draws.get()
    compose.mainClock.advanceTimeBy(1_000)
    compose.waitForIdle()
    assertEquals(settled, draws.get())
  }

  @Test fun underlineKeepsReadableInkInBothThemes() {
    val theme = mutableStateOf(ThemeMode.LIGHT)
    var surface = Color.Unspecified
    val raw = "Riunione il 28/11/2026."
    scene(theme = { theme.value }) {
      val background = MaterialTheme.colorScheme.surface
      SideEffect { surface = background }
      FluidDetectedText(raw, detection = detection)
    }
    waitForLinks(1)
    for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
      compose.runOnIdle { theme.value = mode }
      val input = layout(raw).layoutInput
      val link = input.text.getLinkAnnotations(0, raw.length).single().item
      assertEquals(TextDecoration.Underline, link.styles?.style?.textDecoration)
      assertEquals(Color.Unspecified, link.styles?.style?.color)
      val a = input.style.color.luminance()
      val b = surface.luminance()
      assertTrue("$mode contrasto", (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f)
    }
  }

  private data class TapCase(val phrase: String, val action: String, val scheme: String, val label: String? = null)

  // Su Robolectric manca la Surface della lente Android. La selezione e i suoi due
  // cursori restano quelli di Compose e vengono azionati dallo stesso gesto del telefono.
  @Implements(Magnifier::class)
  class MagnifierWithoutSurface {
    @Implementation fun show(x: Float, y: Float) = Unit
    @Implementation fun show(x: Float, y: Float, centerX: Float, centerY: Float) = Unit
    @Implementation fun dismiss() = Unit
    @Implementation fun update() = Unit
  }

  private class FakeClassifier : TextClassifier {
    val calls = AtomicInteger()
    val calledOnMain = AtomicBoolean()
    val blocked = AtomicBoolean()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)

    override fun getMaxGenerateLinksTextLength(): Int = 512

    override fun generateLinks(request: TextLinks.Request): TextLinks {
      calls.incrementAndGet()
      if (Looper.myLooper() == Looper.getMainLooper()) calledOnMain.set(true)
      entered.countDown()
      if (blocked.get()) check(release.await(20, TimeUnit.SECONDS))
      val text = request.text.toString()
      val builder = TextLinks.Builder(text)
      val start = text.indexOf(Address)
      if (start >= 0) builder.addLink(start, start + Address.length, mapOf(TextClassifier.TYPE_ADDRESS to 0.95f))
      return builder.build()
    }

    companion object {
      const val Address = "Cortile dei Frassini 39"
    }
  }

}
