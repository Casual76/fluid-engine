package dev.antigravity.fluidengine.ui.fluid.activity

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentLook
import dev.antigravity.fluidengine.ui.fluid.GlassPieceArrival
import dev.antigravity.fluidengine.ui.fluid.GlassPieceContentRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassArrivalOf
import dev.antigravity.fluidengine.ui.fluid.glassPieceContentDone
import dev.antigravity.fluidengine.ui.fluid.glassPieceContentFor
import dev.antigravity.fluidengine.ui.fluid.glassPieceContentLook
import dev.antigravity.fluidengine.ui.glass.field.GlassMaterializeSoftEnd
import dev.antigravity.fluidengine.ui.glass.field.GlassMaterializeSymbolScale
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneDisappearCurve
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneSoftAppearCurve
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneSoftDisappearCurve
import dev.antigravity.fluidengine.ui.glass.field.glassMaterializeChannels
import dev.antigravity.fluidengine.ui.fluid.FluidPaneRole
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassTransition
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarContentChange
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDeclaration
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarElementChange
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarGroupChange
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKinds
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMemory
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMinimizeProgress
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMotion
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarResolved
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarSide
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.diffFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarGlassTransition
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMetrics
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarModel
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarRetractingAfter
import dev.antigravity.fluidengine.ui.fluid.toolbar.resolveFluidToolbar
import dev.antigravity.fluidengine.ui.fluid.toolbar.resolveFluidToolbarFitting
import dev.antigravity.fluidengine.ui.fluid.toolbar.selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La pillola di attività (F6B.10) in logica pura: la coda e le priorità, il contatore, due attività che iniziano, finiscono
 * o cambiano insieme; la pillola nella barra (al posto del titolo, compatta o minima, il titolo che torna, `hidden`); la
 * larghezza che cambia solo con lo stato o la frase; le parole di TalkBack sulla pillola e sui comandi della card.
 *
 * La semantica dell'albero vero (Robolectric) è nella galleria: `FluidActivityPillUiTest`.
 */
class FluidActivityQueueTest {

  private val sync = FluidActivity.sync(progress = { 0.4f })
  private val upload = FluidActivity.transfer("upload", progress = { 0.25f }, upload = true)
  private val download = FluidActivity.transfer("download", progress = { 0.7f })
  private val recording = FluidActivity.recording(startedAtMillis = 1_000L)
  private val assistant = FluidActivity.assistant(detail = "Cerco nelle note")

  // ---------------------------------------------------------------------------------------------------------------
  // La coda
  // ---------------------------------------------------------------------------------------------------------------

  @Test fun `la piu importante prende la pillola, e le altre sono il contatore`() {
    val queue = FluidActivityQueue().publish(sync).publish(upload).publish(recording)
    val display = queue.display!!
    assertEquals("la registrazione vince su tutto", "recording", display.activity.id)
    assertEquals(2, display.others)
    assertEquals("le altre, dalla più importante", listOf("upload", "sync"), display.rest.map { it.id })
  }

  @Test fun `a parita la prima arrivata resta nella pillola`() {
    val queue = FluidActivityQueue().publish(upload).publish(download)
    assertEquals("upload", queue.display!!.activity.id)
    // Un aggiornamento della seconda non le dà la pillola: l'ordine d'arrivo non cambia.
    val updated = queue.publish(download.copy(title = "Scarico 3 file"))
    assertEquals("upload", updated.display!!.activity.id)
    assertEquals("Scarico 3 file", updated.display!!.rest.single().title)
  }

  @Test fun `un'importanza data dall'app vince su quella del genere`() {
    val urgent = sync.copy(id = "urgent", importance = 1_000)
    assertEquals("urgent", FluidActivityQueue().publish(recording).publish(urgent).display!!.activity.id)
  }

  @Test fun `pubblicare lo stesso id aggiorna sul posto`() {
    val queue = FluidActivityQueue().publish(sync).publish(sync.copy(title = "3 di 10"))
    assertEquals(1, queue.entries.size)
    assertEquals("3 di 10", queue.display!!.activity.title)
    assertEquals(0L, queue.entries.single().seq)
  }

  @Test fun `due attivita che iniziano insieme danno una pillola sola, gia ordinata`() {
    // Nello stesso fotogramma: la coda arriva alla barra una volta, con tutte e due.
    val together = FluidActivityQueue().publish(sync).publish(assistant)
    val display = together.display!!
    assertEquals("assistant", display.activity.id)
    assertEquals(listOf("sync"), display.rest.map { it.id })
    // L'ordine di pubblicazione non cambia chi vince.
    val reversed = FluidActivityQueue().publish(assistant).publish(sync).display!!
    assertEquals(display.activity.id, reversed.activity.id)
    assertEquals(display.rest.map { it.id }, reversed.rest.map { it.id })
  }

  @Test fun `la riuscita di chi e nella pillola si vede, poi esce col suo tempo`() {
    val running = FluidActivityQueue().publish(sync)
    val done = running.finish("sync", FluidActivityOutcome.succeeded(), nowMillis = 10_000L)
    val display = done.display!!
    assertTrue(display.outcome!!.succeeded)
    assertEquals(10_000L + FluidActivityProfile.SuccessHoldMs, done.nextDeadline())
    assertEquals(done, done.expire(10_000L + FluidActivityProfile.SuccessHoldMs - 1))
    assertNull("il tempo è passato: la pillola esce", done.expire(10_000L + FluidActivityProfile.SuccessHoldMs).display)
  }

  @Test fun `la riuscita di chi non e nella pillola se ne va in silenzio e il contatore scende`() {
    val queue = FluidActivityQueue().publish(recording).publish(sync)
    val after = queue.finish("sync", FluidActivityOutcome.succeeded(), 5_000L)
    assertEquals("recording", after.display!!.activity.id)
    assertNull(after.display!!.outcome)
    assertEquals(0, after.display!!.others)
    assertNull(after.entry("sync"))
  }

  @Test fun `un fallimento si vede sempre, anche di chi non era nella pillola`() {
    val queue = FluidActivityQueue().publish(recording).publish(sync)
    val failed = queue.finish("sync", FluidActivityOutcome.failed("Rete assente"), 5_000L)
    assertEquals("sync", failed.display!!.activity.id)
    assertTrue(failed.display!!.outcome!!.failed)
    assertEquals(listOf("recording"), failed.display!!.rest.map { it.id })
    assertEquals(5_000L + FluidActivityProfile.FailureHoldMs, failed.nextDeadline())
    // Finito il suo tempo, la pillola torna alla registrazione.
    assertEquals("recording", failed.expire(5_000L + FluidActivityProfile.FailureHoldMs).display!!.activity.id)
  }

  @Test fun `l'esito passa il testimone a chi resta`() {
    val queue = FluidActivityQueue().publish(download).publish(sync)
    val ended = queue.finish("download", FluidActivityOutcome.succeeded(), 0L)
    // Mentre l'esito è in vista il contatore conta chi resta.
    assertEquals("download", ended.display!!.activity.id)
    assertEquals(1, ended.display!!.others)
    val next = ended.expire(FluidActivityProfile.SuccessHoldMs)
    assertEquals("sync", next.display!!.activity.id)
    assertEquals(0, next.display!!.others)
  }

  @Test fun `due attivita che finiscono insieme danno un esito solo e poi il titolo torna`() {
    val queue = FluidActivityQueue().publish(download).publish(sync)
    val both = queue
      .finish("download", FluidActivityOutcome.succeeded(), 1_000L)
      .finish("sync", FluidActivityOutcome.succeeded(), 1_000L)
    // La seconda non era nella pillola: se ne va in silenzio, il contatore va a zero.
    assertEquals("download", both.display!!.activity.id)
    assertEquals(0, both.display!!.others)
    assertNull(both.expire(1_000L + FluidActivityProfile.SuccessHoldMs).display)
  }

  @Test fun `una cambia e l'altra finisce insieme`() {
    val queue = FluidActivityQueue().publish(download).publish(sync)
    // Nello stesso fotogramma: la seconda cambia frase e la prima passa il testimone. La pillola va diretta alla seconda,
    // già con la frase nuova: un cambio solo, non due.
    val changed = queue.publish(sync.copy(title = "Ultimi messaggi")).remove("download")
    assertEquals("sync", changed.display!!.activity.id)
    assertEquals("Ultimi messaggi", changed.display!!.activity.title)
    assertEquals(0, changed.display!!.others)
  }

  @Test fun `passare il testimone e togliere fanno uscire subito`() {
    val state = FluidActivityHostState { 0L }
    state.publish(download)
    state.publish(sync)
    state.finish("download", FluidActivityOutcome.HandedOff)
    assertEquals("sync", state.current!!.id)
    state.remove("sync")
    assertNull(state.current)
    assertTrue(state.activities.isEmpty())
  }

  @Test fun `la card aperta tiene ferma la sua attivita e il suo esito`() {
    var now = 0L
    val state = FluidActivityHostState { now }
    state.publish(sync)
    state.toggle(null)
    assertTrue(state.expanded)
    // Una più importante arriva mentre la card è aperta: la pillola non cambia sotto il dito.
    state.publish(recording)
    assertEquals("sync", state.current!!.id)
    // Finisce mentre la si guarda: l'esito resta finché la card è aperta.
    now = 1_000L
    state.finish("sync")
    now = 100_000L
    state.expire()
    assertEquals("sync", state.current!!.id)
    // Chiusa la card, l'esito si vede ancora per il suo tempo, poi passa il testimone.
    state.collapse()
    assertFalse(state.expanded)
    assertEquals("sync", state.current!!.id)
    now += FluidActivityProfile.SuccessHoldMs
    state.expire()
    assertEquals("recording", state.current!!.id)
  }

  @Test fun `togliere l'attivita della card la chiude`() {
    val state = FluidActivityHostState { 0L }
    state.publish(sync)
    state.toggle(null)
    state.remove("sync")
    assertFalse(state.expanded)
    // Un secondo tocco chiude la card aperta.
    state.publish(sync)
    state.toggle(null)
    state.toggle(null)
    assertFalse(state.expanded)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // La pillola nella barra
  // ---------------------------------------------------------------------------------------------------------------

  private val metrics = fluidToolbarMetrics()
  private val textWidth: (String) -> Float = { it.length * 10f + FluidToolbarDefaults.TextPadding.value * 2f }

  private fun declaration(block: FluidToolbarScope.() -> Unit): FluidToolbarDeclaration = buildFluidToolbar({}, block)

  private fun withPill(
    declaration: FluidToolbarDeclaration,
    display: FluidActivityDisplay,
    compactDp: Float = 160f,
    readout: FluidActivityReadout = FluidActivityReadout.Percent,
    minimalOnly: Boolean = false,
  ): FluidToolbarDeclaration {
    assertTrue(fluidActivityAccepts(declaration))
    return fluidToolbarWithActivityOf(declaration, display, FluidActivityLabels(), compactDp, readout, minimalOnly) {}
  }

  private fun resolve(declaration: FluidToolbarDeclaration, width: Float = 400f, narrow: Boolean = false): FluidToolbarResolved =
    resolveFluidToolbarFitting(fluidToolbarModel(declaration, textWidth, title = "Posta"), width, metrics = metrics, narrow = narrow)

  private val page: FluidToolbarScope.() -> Unit = {
    button("search", FluidIcons.Search, "Cerca", onClick = {})
    button("more", FluidIcons.MoreHoriz, "Altro", onClick = {})
  }

  @Test fun `la pillola sta al posto del titolo, al centro, col suo vetro per lente`() {
    val plain = declaration(page)
    assertFalse(plain.hasPrincipal)
    val bar = withPill(declaration(page), FluidActivityDisplay(sync))
    assertTrue("il titolo cede il posto", bar.hasPrincipal)
    val resolved = resolve(bar)
    val pill = resolved.element(FluidActivityIds.Pill)!!
    assertEquals(FluidToolbarSide.Center, pill.side)
    assertEquals(160f, pill.widthDp)
    assertEquals(FluidToolbarKinds.Activity, pill.item.kind!!.type)
    val group = resolved.groups.single { it.id == pill.groupId }
    assertEquals("un vetro suo", 1, group.items.size)
    assertTrue(group.sharedBackground)
    assertEquals("compare e se ne va per lente, e fra due stati resta ferma", FluidGlassTransition.MaterializeWhenAlone,
      fluidToolbarGlassTransition(group))
    // Il titolo torna quando la pillola non c'è più: la dichiarazione della pagina è quella di prima.
    assertFalse(declaration(page).hasPrincipal)
  }

  @Test fun `hidden tiene la pagina senza pillola`() {
    val hidden = declaration {
      page()
      activity(hidden = true)
    }
    assertFalse(fluidActivityAccepts(hidden))
    assertTrue(fluidActivityAccepts(declaration(page)))
    // La richiesta non lascia niente nel modello: nessun vetro, nessun tasto in più.
    assertEquals(
      resolveFluidToolbar(fluidToolbarModel(declaration(page), textWidth), 400f, metrics = metrics).groups.map { it.items.map { item -> item.id } },
      resolveFluidToolbar(fluidToolbarModel(hidden, textWidth), 400f, metrics = metrics).groups.map { it.items.map { item -> item.id } },
    )
  }

  @Test fun `la pillola fra i tasti lascia il titolo`() {
    val bar = withPill(declaration { page(); activity(placement = FluidActivityPlacement.Trailing) }, FluidActivityDisplay(sync))
    assertFalse(bar.hasPrincipal)
    assertEquals(FluidToolbarSide.Trailing, resolve(bar).element(FluidActivityIds.Pill)!!.side)
  }

  @Test fun `la pillola compare due volte non entra due volte`() {
    val once = withPill(declaration(page), FluidActivityDisplay(sync))
    assertFalse(fluidActivityAccepts(once))
  }

  @Test fun `con la barra piena la pillola diventa minima prima di far uscire un tasto`() {
    val full = declaration {
      button("a", FluidIcons.Search, "A", onClick = {})
      button("b", FluidIcons.IosShare, "B", onClick = {})
      button("c", FluidIcons.Delete, "C", onClick = {})
      text("edit", "Modifica", onClick = {})
    }
    val bar = withPill(full, FluidActivityDisplay(sync), compactDp = 170f)
    // Su 420 dp la forma compatta farebbe uscire un tasto: la barra prende la minima, e nessun tasto esce.
    val wide = resolveFluidToolbar(fluidToolbarModel(bar, textWidth), 420f, metrics = metrics)
    assertTrue("la compatta farebbe uscire un tasto", wide.overflow.isNotEmpty())
    val resolved = resolve(bar, width = 420f)
    assertTrue(resolved.overflow.isEmpty())
    assertEquals(FluidMetrics.GlassButtonLens.value, resolved.element(FluidActivityIds.Pill)!!.widthDp)
    val face = (resolved.element(FluidActivityIds.Pill)!!.item.kind as FluidToolbarActivityKind).face
    assertTrue(face.minimal)
    // Con il posto, la compatta.
    assertEquals(170f, resolve(bar, width = 600f).element(FluidActivityIds.Pill)!!.widthDp)
  }

  @Test fun `chiesta la forma stretta, la pillola e minima`() {
    val bar = withPill(declaration(page), FluidActivityDisplay(sync))
    assertEquals(FluidMetrics.GlassButtonLens.value, resolve(bar, narrow = true).element(FluidActivityIds.Pill)!!.widthDp)
  }

  @Test fun `al posto del titolo la pillola non conta nel tetto dei tre gruppi`() {
    val three = declaration {
      button("a", FluidIcons.Search, "A", onClick = {})
      spacer()
      button("b", FluidIcons.IosShare, "B", onClick = {})
      spacer()
      button("c", FluidIcons.Delete, "C", onClick = {})
    }
    val resolved = resolve(withPill(three, FluidActivityDisplay(sync), compactDp = 120f), width = 900f)
    assertTrue(resolved.overflow.isEmpty())
    assertTrue(resolved.fits)
  }

  @Test fun `con il centro occupato dalla selezione la pillola e minima`() {
    val selecting = declaration {
      selection(FluidSelectionState<String>(), count = 2, onSelectAll = null, onDone = {}) { }
    }
    val bar = withPill(selecting, FluidActivityDisplay(sync))
    val pill = resolve(bar, width = 600f).element(FluidActivityIds.Pill)!!
    assertFalse(pill.inOverflow)
    assertEquals(FluidMetrics.GlassButtonLens.value, pill.widthDp)
    // Una pagina di sempre (i suoi tasti la barra non li misura): minima anche lei.
    assertEquals(FluidMetrics.GlassButtonLens.value,
      resolve(withPill(declaration { }, FluidActivityDisplay(sync), minimalOnly = true)).element(FluidActivityIds.Pill)!!.widthDp)
  }

  @Test fun `fra due pagine la pillola resta ferma`() {
    val inbox = resolve(withPill(declaration(page), FluidActivityDisplay(sync)))
    val message = resolve(withPill(declaration { button("reply", FluidIcons.IosShare, "Rispondi", onClick = {}) },
      FluidActivityDisplay(sync)))
    val diff = diffFluidToolbar(inbox, message)
    val pill = diff.elements.single { it.to?.item?.id == FluidActivityIds.Pill }
    assertEquals(FluidToolbarElementChange.Matched, pill.change)
    assertEquals(FluidToolbarContentChange.None, pill.content)
    assertEquals(inbox.element(FluidActivityIds.Pill)!!.leftDp, message.element(FluidActivityIds.Pill)!!.leftDp, 0.01f)
    // Lo stesso vetro nei due stati.
    val memory = FluidToolbarMemory()
    memory.update(inbox, FluidToolbarMotion.Full)
    val before = memory.glassIds.getValue(inbox.element(FluidActivityIds.Pill)!!.groupId!!)
    memory.update(message, FluidToolbarMotion.Full)
    assertEquals(before, memory.glassIds.getValue(message.element(FluidActivityIds.Pill)!!.groupId!!))
  }

  @Test fun `la pillola che arriva e che se ne va passa per lente`() {
    val plain = resolve(declaration(page))
    val withActivity = resolve(withPill(declaration(page), FluidActivityDisplay(sync)))
    val appear = diffFluidToolbar(plain, withActivity)
    assertEquals(FluidToolbarGroupChange.Materialize,
      appear.groups.single { group -> group.to.any { it.items.any { item -> item.id == FluidActivityIds.Pill } } }.change)
    val leave = diffFluidToolbar(withActivity, plain)
    assertEquals(FluidToolbarGroupChange.Dematerialize,
      leave.groups.single { group -> group.from.any { it.items.any { item -> item.id == FluidActivityIds.Pill } } }.change)
    // Gli altri tasti restano dove sono.
    assertEquals(plain.groups.map { it.leftDp }, withActivity.groups.filter { it.side != FluidToolbarSide.Center }.map { it.leftDp })
  }

  // ---------------------------------------------------------------------------------------------------------------
  // La larghezza
  // ---------------------------------------------------------------------------------------------------------------

  private fun kind(display: FluidActivityDisplay, readout: FluidActivityReadout = FluidActivityReadout.Percent, compact: Float = 150f) =
    fluidToolbarActivityKind(display, compact, inTitle = true, minimal = false, forcedMinimal = false, readout = readout)

  @Test fun `la larghezza non cambia con l'avanzamento ne col tempo`() {
    val low = kind(FluidActivityDisplay(sync.copy(progress = { 0.1f })))
    val high = kind(FluidActivityDisplay(sync.copy(progress = { 0.9f })))
    assertEquals(low, high)
    assertEquals(low.widthDp, high.widthDp)
    // La regola della larghezza: la percentuale ha il suo posto fisso, «100%».
    assertEquals(fluidActivityPillWidthOf(80f, 30f, 0f), fluidActivityPillWidthOf(80f, 30f, 0f))
    assertEquals("m:ss sotto l'ora", "2:05", fluidActivityClockText(125_400L, countsDown = false))
    assertEquals("per eccesso nel conto alla rovescia", "2:06", fluidActivityClockText(125_400L, countsDown = true))
    assertEquals("1:00:00", fluidActivityClockText(3_600_000L, countsDown = false))
  }

  @Test fun `la larghezza cambia con la frase, il contatore e la classe del tempo`() {
    assertNotEquals(fluidActivityPillWidthOf(80f, 30f, 0f), fluidActivityPillWidthOf(120f, 30f, 0f))
    assertNotEquals(fluidActivityPillWidthOf(80f, 30f, 0f), fluidActivityPillWidthOf(80f, 30f, 24f))
    assertNotEquals(kind(FluidActivityDisplay(recording), FluidActivityReadout.Clock),
      kind(FluidActivityDisplay(recording), FluidActivityReadout.LongClock))
    assertEquals(FluidActivityReadout.Clock, fluidActivityReadout(recording, 1_000L + 59 * 60_000L))
    assertEquals(FluidActivityReadout.LongClock, fluidActivityReadout(recording, 1_000L + 60 * 60_000L))
    assertEquals(FluidActivityReadout.Percent, fluidActivityReadout(upload, 0L))
    assertEquals(FluidActivityReadout.None, fluidActivityReadout(assistant, 0L))
  }

  @Test fun `l'esito tiene la larghezza del lavoro e cambia la faccia`() {
    val running = kind(FluidActivityDisplay(sync))
    val done = kind(FluidActivityDisplay(sync, FluidActivityOutcome.succeeded()))
    assertEquals(running.widthDp, done.widthDp)
    assertNotEquals("l'esito si scambia sfocato", running.identity, done.identity)
    // Il contatore cambia dentro la pillola: lo stato, non l'identità.
    val counted = kind(FluidActivityDisplay(sync, rest = listOf(upload)))
    assertEquals(running.identity, counted.identity)
    assertNotEquals(running.state, counted.state)
    assertEquals("+3", fluidActivityCounterText(3))
    assertEquals("9+", fluidActivityCounterText(12))
  }

  @Test fun `l'esito si scambia nel vetro senza viaggi`() {
    val running = resolve(withPill(declaration(page), FluidActivityDisplay(sync)))
    val done = resolve(withPill(declaration(page), FluidActivityDisplay(sync, FluidActivityOutcome.succeeded())))
    val pill = diffFluidToolbar(running, done).elements.single { it.to?.item?.id == FluidActivityIds.Pill }
    assertEquals(FluidToolbarElementChange.Matched, pill.change)
    assertEquals(FluidToolbarContentChange.BlurReplace, pill.content)
    assertEquals(running.groups.map { it.leftDp to it.widthDp }, done.groups.map { it.leftDp to it.widthDp })
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Le parole
  // ---------------------------------------------------------------------------------------------------------------

  private val labels = FluidActivityLabels()

  @Test fun `TalkBack dice il titolo, lo stato e le altre attivita`() {
    val running = fluidActivitySpeech(FluidActivityDisplay(upload, rest = listOf(sync, download)), labels, "25%")
    assertEquals("Caricamento", running.name)
    assertEquals("25%, e altre 2 attività", running.state)
    assertFalse("l'avanzamento non si annuncia a ogni passo", running.live)
    val paused = fluidActivitySpeech(FluidActivityDisplay(upload.copy(paused = true), rest = listOf(sync)), labels, "25%")
    assertEquals("In pausa, 25%, e un'altra attività", paused.state)
    val done = fluidActivitySpeech(FluidActivityDisplay(sync, FluidActivityOutcome.succeeded()), labels, null)
    assertEquals("Fatto", done.state)
    assertTrue("l'esito è un'area viva", done.live)
    val failed = fluidActivitySpeech(FluidActivityDisplay(sync, FluidActivityOutcome.failed("Rete assente")), labels, null)
    assertEquals("Rete assente", failed.state)
    assertTrue(failed.live)
    assertEquals("Done", fluidActivitySpeech(FluidActivityDisplay(sync, FluidActivityOutcome.succeeded()),
      FluidActivityLabels.english(), null).state)
    assertEquals("25%", fluidActivitySpokenReadout(upload, 25, 0L, labels))
    assertEquals("2:05", fluidActivitySpokenReadout(recording, null, 125_400L, labels))
  }

  @Test fun `i comandi della card hanno i loro nomi, e Apri per ultimo`() {
    var paused = 0
    var cancelled = 0
    var opened = 0
    val transfer = FluidActivity.transfer("t", progress = { 0.5f }, onPause = { paused++ }, onCancel = { cancelled++ },
      onOpen = { opened++ })
    val commands = fluidActivityCardCommands(FluidActivityDisplay(transfer), labels)
    assertEquals(listOf("Pausa", "Annulla", "Apri"), commands.map { it.action.label })
    assertEquals(listOf(false, true, false), commands.map { it.action.destructive })
    assertEquals(listOf(false, false, true), commands.map { it.opens })
    commands.forEach { it.action.onClick() }
    assertEquals(listOf(1, 1, 1), listOf(paused, cancelled, opened))
    // In pausa offre Riprendi; finita, la card dice l'esito e non ha comandi.
    val resumed = FluidActivity.transfer("t", progress = { 0.5f }, paused = true, onPause = {}, onResume = {})
    assertEquals(listOf("Riprendi"), fluidActivityCardCommands(FluidActivityDisplay(resumed), labels).map { it.action.label })
    assertTrue(fluidActivityCardCommands(FluidActivityDisplay(transfer, FluidActivityOutcome.succeeded()), labels).isEmpty())
    // I preset: registrazione, assistente, timer.
    assertEquals(listOf("Pausa", "Interrompi"),
      fluidActivityCardCommands(FluidActivityDisplay(FluidActivity.recording(0L, onPause = {}, onStop = {})), labels).map { it.action.label })
    assertEquals(listOf("Interrompi", "Apri"),
      fluidActivityCardCommands(FluidActivityDisplay(FluidActivity.assistant(onStop = {}, onOpen = {})), labels).map { it.action.label })
    val timer = FluidActivity.timer(endsAtMillis = 60_000L, pausedRemainingMillis = 30_000L, totalMillis = 60_000L, onResume = {}, onCancel = {})
    assertEquals(listOf("Riprendi", "Annulla"), fluidActivityCardCommands(FluidActivityDisplay(timer), labels).map { it.action.label })
    assertEquals(0.5f, timer.progress!!(), 0.0001f)
    assertEquals(30_000L, timer.time!!.shownMillis(999_999L))
  }

  @Test fun `i preset hanno il genere, l'icona e il tempo giusti`() {
    assertEquals(FluidActivityKind.Sync, sync.kind)
    assertEquals(FluidActivityKind.Transfer, upload.kind)
    assertNotNull(upload.icon)
    assertEquals(FluidActivityKind.Recording, recording.kind)
    assertEquals(4_000L, recording.time!!.shownMillis(5_000L))
    assertEquals(FluidActivityKind.Assistant, assistant.kind)
    assertEquals(FluidActivityKind.Timer, FluidActivity.timer(endsAtMillis = 10_000L).kind)
    assertEquals(3_000L, FluidActivity.timer(endsAtMillis = 10_000L).time!!.shownMillis(7_000L))
    assertTrue(FluidActivityKind.Recording.importance > FluidActivityKind.Assistant.importance)
    assertTrue(FluidActivityKind.Assistant.importance > FluidActivityKind.Timer.importance)
    assertTrue(FluidActivityKind.Timer.importance > FluidActivityKind.Transfer.importance)
    assertTrue(FluidActivityKind.Transfer.importance > FluidActivityKind.Sync.importance)
    assertEquals(FluidActivityKind.custom("sync", FluidIcons.Search), FluidActivityKind.Sync)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Due barre in vista: una pillola sola (secondo giro)
  // ---------------------------------------------------------------------------------------------------------------

  @Test fun `con due barre in vista la pillola sta nel dettaglio, o dove l'app sceglie`() {
    val both = listOf(FluidPaneRole.List, FluidPaneRole.Detail)
    assertEquals(FluidPaneRole.Detail, fluidActivityOwnerPane(both, FluidPaneRole.Detail))
    assertEquals(FluidPaneRole.List, fluidActivityOwnerPane(both, FluidPaneRole.List))
    // Una barra sola in vista: la pillola sta in quella, qualunque pannello l'app abbia scelto.
    assertEquals(FluidPaneRole.List, fluidActivityOwnerPane(listOf(FluidPaneRole.List), FluidPaneRole.Detail))
    assertEquals(FluidPaneRole.Detail, fluidActivityOwnerPane(listOf(FluidPaneRole.Detail), FluidPaneRole.List))
    // Fuori dai pannelli nessuna regola; la scelta che non c'è cade sul dettaglio, poi sulla lista.
    assertNull(fluidActivityOwnerPane(listOf(null), FluidPaneRole.Detail))
    assertNull(fluidActivityOwnerPane(emptyList(), FluidPaneRole.Detail))
    assertEquals(FluidPaneRole.Detail,
      fluidActivityOwnerPane(listOf(FluidPaneRole.Side, FluidPaneRole.Detail, FluidPaneRole.List), FluidPaneRole.Detail))
    assertEquals(FluidPaneRole.List, fluidActivityOwnerPane(listOf(FluidPaneRole.Side, FluidPaneRole.List), FluidPaneRole.Detail))
    // Chi la mostra: la barra del pannello scelto; una barra fuori dai pannelli sempre.
    assertTrue(fluidActivityShowsIn(FluidPaneRole.Detail, FluidPaneRole.Detail))
    assertFalse(fluidActivityShowsIn(FluidPaneRole.List, FluidPaneRole.Detail))
    assertTrue(fluidActivityShowsIn(null, FluidPaneRole.Detail))
    assertTrue(fluidActivityShowsIn(FluidPaneRole.List, null))
  }

  @Test fun `le barre registrate decidono dove sta la pillola, anche quando una esce di vista`() {
    val host = FluidActivityHostState { 0L }
    val list = FluidActivitySeat(FluidPaneRole.List)
    val detail = FluidActivitySeat(FluidPaneRole.Detail)
    host.seats += list
    // La lista da sola: la pillola sta lì. Il dettaglio che apre la chiede già alla prima composizione.
    assertTrue(host.showsIn(FluidPaneRole.List))
    assertTrue(host.showsIn(FluidPaneRole.Detail, self = detail))
    host.seats += detail
    assertFalse("due barre in vista: non nella lista", host.showsIn(FluidPaneRole.List))
    assertTrue(host.showsIn(FluidPaneRole.Detail))
    // L'app sceglie la lista.
    host.pane = FluidPaneRole.List
    assertTrue(host.showsIn(FluidPaneRole.List))
    assertFalse(host.showsIn(FluidPaneRole.Detail))
    // La lista esce di vista (un telefono che mostra il dettaglio): la pillola va nel dettaglio.
    list.inView = false
    assertTrue(host.showsIn(FluidPaneRole.Detail))
    // Una barra fuori dai pannelli (un foglio) la mostra sempre.
    assertTrue(host.showsIn(null))
    host.seats -= detail
    host.seats -= list
    assertTrue(host.showsIn(FluidPaneRole.List))
  }

  // ---------------------------------------------------------------------------------------------------------------
  // L'anellino a barra ritirata (secondo giro)
  // ---------------------------------------------------------------------------------------------------------------

  @Test fun `la barra si stringe a meta ritiro e torna larga solo a zero`() {
    val from = FluidToolbarDefaults.RetractingFrom
    assertFalse("uno scorrimento piccolo non cambia forma", fluidToolbarRetractingAfter(false, from / 2f))
    assertTrue(fluidToolbarRetractingAfter(false, from))
    assertTrue("al ritorno resta stretta finché la barra non è ferma", fluidToolbarRetractingAfter(true, 0.01f))
    assertFalse(fluidToolbarRetractingAfter(true, 0f))
    assertFalse(fluidToolbarRetractingAfter(false, 0f))
  }

  @Test fun `l'anellino sta al centro della barra, a meta della barra di stato, fuori dal foro`() {
    val r = 16f
    val m = 12f
    // A metà della barra di stato, al centro della barra.
    assertEquals(Offset(400f, 48f), fluidActivityRingSpot(400f, statusBarPx = 96f, radiusPx = r, marginPx = m, cutouts = emptyList()))
    // Senza barra di stato non tocca il bordo.
    assertEquals(Offset(400f, r + m), fluidActivityRingSpot(400f, 0f, r, m, emptyList()))
    // Un foro al centro: l'anellino va alla sua destra.
    val hole = Rect(370f, 10f, 430f, 80f)
    assertEquals(Offset(430f + m + r, 48f), fluidActivityRingSpot(400f, 96f, r, m, listOf(hole)))
    // Un foro lontano (in un angolo) non lo sposta.
    assertEquals(Offset(400f, 48f), fluidActivityRingSpot(400f, 96f, r, m, listOf(Rect(0f, 0f, 120f, 80f))))
  }

  @Test fun `l'anellino parte dall'anello della pillola, lo segue mentre si stringe, e rimpicciolisce`() {
    val glyph = Offset(300f, 140f)
    val face = Offset(400f, 140f)
    assertEquals(glyph, fluidActivityRingHome(glyph, face, 0f))
    assertEquals(face, fluidActivityRingHome(glyph, face, 1f))
    assertEquals(Offset(350f, 140f), fluidActivityRingHome(glyph, face, 0.5f))
    assertEquals("senza il centro, l'anello", glyph, fluidActivityRingHome(glyph, Offset.Unspecified, 1f))
    assertNull("la pillola non si è posata", fluidActivityRingHome(Offset.Unspecified, Offset.Unspecified, 0f))
    val spot = Offset(400f, 48f)
    assertEquals(glyph, fluidActivityRingAt(glyph, spot, 0f))
    assertEquals(spot, fluidActivityRingAt(glyph, spot, 1f))
    val half = fluidActivityRingAt(glyph, spot, 0.5f)
    assertTrue(half.y < glyph.y && half.y > spot.y)
    // La misura: piena a riposo, piccola a barra ritirata; col movimento ridotto piccola subito.
    val small = FluidActivityProfile.RingSmall.value / FluidActivityProfile.Glyph.value
    assertEquals(1f, fluidActivityRingScale(0f, reduced = false), 0.0001f)
    assertEquals(small, fluidActivityRingScale(1f, reduced = false), 0.0001f)
    assertEquals(small, fluidActivityRingScale(0f, reduced = true), 0.0001f)
    // L'opacità: niente a riposo (lo disegna la pillola), piena appena il ritiro parte; in dissolvenza se ridotto.
    assertEquals(0f, fluidActivityRingAlpha(0f, reduced = false))
    assertEquals(1f, fluidActivityRingAlpha(0.01f, reduced = false))
    assertTrue(fluidActivityRingAlpha(0.3f, reduced = true) < 1f)
    assertEquals(1f, fluidActivityRingAlpha(1f, reduced = true), 0.0001f)
  }

  @Test fun `l'anello della pillola si nasconde solo mentre l'anellino lo porta`() {
    val ring = FluidActivityRing()
    val minimize = object : FluidToolbarMinimizeProgress {
      var value = 0f
      override val progress: Float get() = value
      override val reducedMotion: Boolean = false
    }
    assertFalse(ring.covers(minimize))
    assertTrue(ring.settled(minimize))
    ring.retraction = minimize
    assertFalse(ring.covers(minimize))
    assertTrue(ring.settled(minimize))
    minimize.value = 0.2f
    assertTrue(ring.covers(minimize))
    assertFalse("la pillola non scrive dove sta mentre la barra si sposta", ring.settled(minimize))
    // La pillola di un'altra barra (la schermata sotto, o una barra che non si ritira) non cede l'anello e non scrive.
    val other = object : FluidToolbarMinimizeProgress {
      override val progress: Float = 1f
      override val reducedMotion: Boolean = false
    }
    assertFalse(ring.covers(other))
    assertFalse(ring.covers(null))
    minimize.value = 0f
    assertFalse(ring.settled(other))
    assertFalse(ring.settled(null))
    val reduced = object : FluidToolbarMinimizeProgress {
      override val progress: Float = 0.6f
      override val reducedMotion: Boolean = true
    }
    ring.retraction = reduced
    assertFalse("col movimento ridotto la pillola svanisce col suo anello, sul posto", ring.covers(reduced))
  }

  // ---------------------------------------------------------------------------------------------------------------
  // La comparsa morbida della pillola (terzo giro)
  // ---------------------------------------------------------------------------------------------------------------

  @Test fun `la comparsa morbida accende vetro e lente insieme, e la lente di sempre non cambia`() {
    // Morbida: contorno e lente insieme, da zero a pieni a GlassMaterializeSoftEnd; il simbolo lo porta R7.
    for (m in listOf(0f, 0.1f, 0.3f, 0.5f, 0.6f, 0.8f, 1f)) {
      val soft = glassMaterializeChannels(m, soft = true)
      assertEquals(soft.contour, soft.lens, 0f)
      assertEquals(1f, soft.symbolScale, 0f)
      assertEquals(1f, soft.symbolAlpha, 0f)
    }
    assertEquals(0f, glassMaterializeChannels(0f, soft = true).contour, 0f)
    assertEquals(1f, glassMaterializeChannels(GlassMaterializeSoftEnd, soft = true).contour, 0f)
    val half = glassMaterializeChannels(GlassMaterializeSoftEnd / 2f, soft = true).contour
    assertTrue(half > 0.3f && half < 0.7f)
    // Di serie: il contorno prima della lente e il simbolo due volte più grande (R4 B3), com'era.
    val lens = glassMaterializeChannels(0.3f)
    assertEquals(1f, lens.contour, 0f)
    assertTrue(lens.lens < 0.3f)
    assertEquals(GlassMaterializeSymbolScale, glassMaterializeChannels(0.5f).symbolScale, 0f)
    // I tempi: il vetro c'è quasi pieno a 150 ms della comparsa (0,35 di 430) e se n'è andato a 330 ms della scomparsa
    // (0,7 di 470), senza fermarsi vuoto: scende già mentre la scritta si sfoca (0,15–0,45).
    assertTrue(GlassPlaneSoftAppearCurve.at(0.35f) >= 0.9f)
    assertEquals(1f, GlassPlaneSoftAppearCurve.at(0.6f), 1e-4f)
    assertEquals(0f, GlassPlaneSoftDisappearCurve.at(0.7f), 1e-4f)
    assertTrue(GlassPlaneSoftDisappearCurve.at(0.3f) < 0.8f)
    var previous = 1f
    for (i in 0..100) {
      val value = GlassPlaneSoftDisappearCurve.at(i / 100f)
      assertTrue("la scomparsa morbida scende sempre", value <= previous + 1e-5f)
      previous = value
    }
    // La lente di sempre resta quella di R4 B3: piena a 130 ms della scomparsa, il contorno fino a 470.
    assertTrue(GlassPlaneDisappearCurve.at(0.277f) > 0.96f)
  }

  @Test fun `la scritta della pillola entra ed esce con lo scambio di R7, mai due volte piu grande`() {
    assertEquals(GlassPieceArrival.Soft, fluidGlassArrivalOf(GlassPlaneChange.Materialize, soft = true))
    assertEquals(GlassPieceArrival.Soft, fluidGlassArrivalOf(GlassPlaneChange.Dematerialize, soft = true))
    assertEquals(GlassPieceArrival.ThroughLens, fluidGlassArrivalOf(GlassPlaneChange.Materialize))
    val enter = glassPieceContentFor(GlassPieceArrival.Soft, now = null, departed = false)
    assertEquals(GlassPieceContentRole.Swap, enter.role)
    var seen = false
    var phase = 0f
    while (phase <= 1.3f) {
      val look = glassPieceContentLook(enter, phase)
      assertTrue("alla fase $phase la scala ${look.scale} non supera 1", look.scale <= 1f + 1e-4f)
      if (look.alpha > 0f) {
        assertTrue("entra dalla scala di R7", look.scale >= GlassContentDefaults.EnterScale - 1e-4f)
        seen = true
      }
      phase += 0.01f
    }
    assertTrue(seen)
    // All'inizio non c'è, e a fine comparsa (il tempo di R7, non quello della lente) è nitida e piena.
    assertEquals(0f, glassPieceContentLook(enter, 0f).alpha, 0f)
    val endPhase = GlassContentDefaults.EnterEnd * GlassContentDefaults.DurationMs / FluidMotion.PlaneAppearMillis
    val done = glassPieceContentLook(enter, endPhase + 0.01f)
    assertEquals(1f, done.alpha, 1e-3f)
    assertEquals(0f, done.blur, 1e-3f)
    assertTrue(glassPieceContentDone(enter, endPhase + 0.01f))
    // L'uscita: sfocata, sull'orologio di R7, finita prima che il vetro se ne vada (la scomparsa dura 470 ms).
    val exit = glassPieceContentFor(GlassPieceArrival.Soft, now = GlassContentLook.Sharp, departed = true)
    assertEquals(GlassPieceContentRole.Exit, exit.role)
    val gone = GlassContentDefaults.ExitEnd * GlassContentDefaults.DurationMs / FluidMotion.PlaneDisappearMillis
    assertTrue(glassPieceContentLook(exit, gone * 0.5f).blur > 0f)
    assertEquals(0f, glassPieceContentLook(exit, gone + 0.01f).alpha, 1e-3f)
    assertTrue(gone < 1f)
  }
}
