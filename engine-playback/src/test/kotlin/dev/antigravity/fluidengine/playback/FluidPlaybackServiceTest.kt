package dev.antigravity.fluidengine.playback

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Process
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * F8.13: la sessione di Media3 del servizio di riproduzione, con l'ExoPlayer vero (Media3 alla versione dei test). Senza
 * decodificatori: si prova cio' che la sessione decide (comandi, tasti, timer, salto dei silenzi, coda ricordata), non
 * il suono, che si sente sul tablet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FluidPlaybackServiceTest {

  private val services = ArrayList<ServiceController<FluidPlaybackService>>()

  private fun startService(): FluidPlaybackService =
    Robolectric.buildService(FluidPlaybackService::class.java).also { services += it }.create().get()

  @After
  fun tearDown() {
    services.forEach { runCatching { it.destroy() } }
    FluidPlayback.configure(FluidPlaybackConfig())
    FluidPlaybackStore(RuntimeEnvironment.getApplication()).clear()
  }

  private fun controllerInfo(): MediaSession.ControllerInfo = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
    "dev.antigravity.prova", 1_000, 10_000, MediaLibraryInfo.VERSION_INT, 1, false, Bundle.EMPTY, true,
  )

  /** Il controllo dell'app stessa (il suo uid): l'interfaccia, la notifica di Media3. */
  private fun ownController(): MediaSession.ControllerInfo = MediaSession.ControllerInfo.createTestOnlyControllerInfo(
    RuntimeEnvironment.getApplication().packageName, Process.myPid(), Process.myUid(), MediaLibraryInfo.VERSION_INT, 1, false,
    Bundle.EMPTY, true,
  )

  /**
   * Il controllo di un'altra app: il pacchetto che dice di essere e il suo uid, di cui è davvero [realPackage]. [trusted] è
   * quello che Media3 1.5.1 calcola, anche dal solo nome (gli ascoltatori delle notifiche).
   */
  private fun otherApp(packageName: String, uid: Int, trusted: Boolean = false, realPackage: String = packageName): MediaSession.ControllerInfo {
    shadowOf(RuntimeEnvironment.getApplication().packageManager).setPackagesForUid(uid, realPackage)
    return MediaSession.ControllerInfo.createTestOnlyControllerInfo(
      packageName, 4_000, uid, MediaLibraryInfo.VERSION_INT, 1, trusted, Bundle.EMPTY, true,
    )
  }

  private fun item(id: String, seconds: Long = 60L) = FluidPlaybackItem(
    id = id,
    uri = Uri.parse("file:///data/memo/$id.m4a"),
    title = "Memo $id",
    subtitle = "Lezione di scienze",
    durationMillis = seconds * 1000L,
    mimeType = "audio/mp4",
  )

  @Test
  fun theSessionSkipsFifteenSecondsAndShowsTheButtonsBesidePlay() {
    val service = startService()
    val player = service.player!!
    assertEquals(15_000L, player.seekBackIncrement)
    assertEquals(15_000L, player.seekForwardIncrement)
    val session = service.session!!
    val buttons = session.mediaButtonPreferences
    assertEquals(2, buttons.size)
    assertEquals(Player.COMMAND_SEEK_BACK, buttons[0].playerCommand)
    assertEquals(Player.COMMAND_SEEK_FORWARD, buttons[1].playerCommand)
  }

  @Test
  fun aControllerGetsTheEngineCommands() {
    // R1: il controllo dell'app stessa (prima della review: un controllo qualunque, anche di un'altra app).
    val service = startService()
    val result = service.callback.onConnect(service.session!!, ownController())
    assertTrue(result.isAccepted)
    val commands = result.availableSessionCommands
    assertTrue(commands.contains(SessionCommand(FluidPlayback.CommandSleepTimer, Bundle.EMPTY)))
    assertTrue(commands.contains(SessionCommand(FluidPlayback.CommandSkipSilence, Bundle.EMPTY)))
    assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_SET_SPEED_AND_PITCH))
    assertTrue(result.availablePlayerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
  }

  @Test
  fun aForeignAppCannotConnectSoItReadsNoTitleAndSendsNoCommand() {
    // R1 (Sol, bloccante): il servizio è esportato. Un'app qualunque, col suo nome vero e nessun permesso.
    val service = startService()
    service.player!!.setMediaItems(listOf(item("segreto").toMediaItem()))
    val result = service.callback.onConnect(service.session!!, otherApp("com.estranea", 10_777))
    assertFalse("un'app estranea si collega", result.isAccepted)
  }

  @Test
  fun anAppThatBorrowsTheNameOfATrustedOneIsRefused() {
    // Media3 1.5.1 non verifica il pacchetto che un controllo dice di avere, e da quel nome decide «fidato» (un ascoltatore
    // delle notifiche, come l'app dell'orologio): il pacchetto deve essere dell'uid che chiama.
    val service = startService()
    val result = service.callback.onConnect(
      service.session!!,
      otherApp("com.google.android.wearable.app", 10_778, trusted = true, realPackage = "com.estranea"),
    )
    assertFalse("un nome preso in prestito passa", result.isAccepted)
  }

  @Test
  fun theSystemShowsTheTitleAndControlsPlaybackButCannotChangeTheQueue() {
    val service = startService()
    val systems = listOf(
      otherApp("com.android.systemui", 10_050, trusted = true),
      // Un controllo della sessione di piattaforma prima di Android 9: senza nome. Ci arriva solo chi ha già il gettone
      // della sessione (il sistema, chi ascolta le notifiche).
      MediaSession.ControllerInfo.createTestOnlyControllerInfo(
        MediaSession.ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME, -1, -1, MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION,
        MediaSession.ControllerInfo.LEGACY_CONTROLLER_INTERFACE_VERSION, false, Bundle.EMPTY, false,
      ),
    )
    for (system in systems) {
      val result = service.callback.onConnect(service.session!!, system)
      assertTrue("${system.packageName} non si collega", result.isAccepted)
      val player = result.availablePlayerCommands
      for (command in listOf(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_GET_METADATA)) {
        assertTrue("${system.packageName}: manca il comando $command", player.contains(command))
      }
      assertFalse("${system.packageName} cambia la coda", player.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS))
      assertFalse("${system.packageName} mette elementi", player.contains(Player.COMMAND_SET_MEDIA_ITEM))
      assertFalse(result.availableSessionCommands.contains(SessionCommand(FluidPlayback.CommandSleepTimer, Bundle.EMPTY)))
      assertFalse(result.availableSessionCommands.contains(SessionCommand(FluidPlayback.CommandSkipSilence, Bundle.EMPTY)))
    }
  }

  @Test
  fun skipSilenceIsTheExoPlayerOneAndIsPublished() {
    val service = startService()
    val session = service.session!!
    val result = service.callback.onCustomCommand(
      session, controllerInfo(), SessionCommand(FluidPlayback.CommandSkipSilence, Bundle.EMPTY),
      Bundle().apply { putBoolean(FluidPlayback.ArgEnabled, true) },
    ).get()
    assertEquals(SessionResult.RESULT_SUCCESS, result.resultCode)
    assertTrue(service.player!!.skipSilenceEnabled)
    assertTrue(session.sessionExtras.getBoolean(FluidPlayback.ExtraSkipSilence))
  }

  @Test
  fun theSleepTimerPausesAfterItsMinutes() {
    val service = startService()
    val player = service.player!!
    player.playWhenReady = true
    service.callback.onCustomCommand(
      service.session!!, controllerInfo(), SessionCommand(FluidPlayback.CommandSleepTimer, Bundle.EMPTY),
      Bundle().apply { putInt(FluidPlayback.ArgMinutes, 1) },
    )
    assertTrue(service.session!!.sessionExtras.containsKey(FluidPlayback.ExtraSleepAt))
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59))
    assertTrue(player.playWhenReady)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
    assertFalse(player.playWhenReady)
    assertFalse(service.session!!.sessionExtras.containsKey(FluidPlayback.ExtraSleepAt))
  }

  @Test
  fun theSleepTimerAtEndOfItemUsesThePlayer() {
    val service = startService()
    service.setSleepTimer(0, endOfItem = true)
    assertTrue(service.player!!.pauseAtEndOfMediaItems)
    assertTrue(service.session!!.sessionExtras.getBoolean(FluidPlayback.ExtraSleepAtEndOfItem))
    service.setSleepTimer(0)
    assertFalse(service.player!!.pauseAtEndOfMediaItems)
  }

  @Test
  fun itemsFromAControllerGetTheirAddressBack() {
    val service = startService()
    // Cosi' arriva un elemento da un altro processo: senza l'indirizzo locale, con quello della richiesta.
    val remote = MediaItem.Builder()
      .setMediaId("a")
      .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse("file:///data/memo/a.m4a")).build())
      .build()
    val resolved = service.callback.onAddMediaItems(service.session!!, controllerInfo(), mutableListOf(remote)).get()
    assertEquals(Uri.parse("file:///data/memo/a.m4a"), resolved.single().localConfiguration?.uri)
  }

  @Test
  fun itemsSurviveTheTripThroughMediaItem() {
    val original = item("lezione", seconds = 75).copy(artworkUri = Uri.parse("content://copertine/1"))
    val back = original.toMediaItem().toFluidPlaybackItem()
    assertEquals(original, back)
  }

  @Test
  fun theQueueIsRememberedAndResumedWithoutPlaying() {
    val context = RuntimeEnvironment.getApplication()
    FluidPlaybackStore(context).save(listOf(item("a").toMediaItem(), item("b").toMediaItem()), 1, 42_000L)

    val service = startService()
    val player = service.player!!
    assertEquals(2, player.mediaItemCount)
    assertEquals(1, player.currentMediaItemIndex)
    assertEquals(42_000L, player.currentPosition)
    assertFalse("la coda ricordata non suona da sola", player.playWhenReady)

    val resumption = service.callback.onPlaybackResumption(service.session!!, controllerInfo()).get()
    assertEquals(listOf("a", "b"), resumption.mediaItems.map { it.mediaId })
    assertEquals(1, resumption.startIndex)
    assertEquals(42_000L, resumption.startPositionMs)
  }

  @Test
  fun aQueueChangedWhileStoppedIsRememberedAndAnEmptyQueueIsForgotten() {
    // R1 (Muse 5): la coda cambiata da ferma, senza Play, salti né cambio di elemento, e poi il processo che muore.
    val context = RuntimeEnvironment.getApplication()
    FluidPlaybackStore(context).save(listOf(item("a").toMediaItem()), 0, 0L)
    val service = startService()
    val controller = FluidPlaybackController(service.player!!)
    controller.attach()
    controller.player.addMediaItem(item("b").toMediaItem())
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("la coda ricordata è quella vecchia", listOf("a", "b"), FluidPlaybackStore(context).load()?.mediaItems?.map { it.mediaId })
    controller.setItems(listOf(item("c"), item("d")), startIndex = 1, play = false)
    shadowOf(Looper.getMainLooper()).idle()
    val saved = FluidPlaybackStore(context).load()!!
    assertEquals(listOf("c", "d"), saved.mediaItems.map { it.mediaId })
    assertEquals(1, saved.startIndex)
    controller.setItems(emptyList())
    shadowOf(Looper.getMainLooper()).idle()
    assertNull("svuotata, la coda risuscita al riavvio", FluidPlaybackStore(context).load())
    controller.close()
  }

  @Test
  fun anItemWithoutAddressDoesNotMoveTheResumePoint() {
    // R1 (Muse 14): un elemento senza indirizzo (messo da fuori con la sola mediaId) non si ricorda, e il punto di ripresa
    // resta sull'elemento che si ascoltava.
    val store = FluidPlaybackStore(RuntimeEnvironment.getApplication())
    val bare = MediaItem.Builder().setMediaId("senza-indirizzo").build()
    store.save(listOf(bare, item("a").toMediaItem(), item("b").toMediaItem()), 1, 7_000L)
    val saved = store.load()!!
    assertEquals(listOf("a", "b"), saved.mediaItems.map { it.mediaId })
    assertEquals("a", saved.mediaItems[saved.startIndex].mediaId)
    assertEquals(7_000L, saved.startPositionMs)
    // Se è proprio quello che si ascoltava a non avere indirizzo, si riprende dal successivo, dall'inizio.
    store.save(listOf(item("a").toMediaItem(), bare, item("b").toMediaItem()), 1, 7_000L)
    val next = store.load()!!
    assertEquals("b", next.mediaItems[next.startIndex].mediaId)
    assertEquals(0L, next.startPositionMs)
  }

  @Test
  fun restoreCanBeTurnedOff() {
    FluidPlaybackStore(RuntimeEnvironment.getApplication()).save(listOf(item("a").toMediaItem()), 0, 1_000L)
    FluidPlayback.configure(FluidPlaybackConfig(restoreOnStart = false, skipForwardMillis = 30_000L))
    val service = startService()
    assertEquals(0, service.player!!.mediaItemCount)
    assertEquals(30_000L, service.player!!.seekForwardIncrement)
  }

  @Test
  fun aRememberedQueueCanBeSeekedBeforeItIsPreparedAndRestartsWhenFinished() {
    // Il caso del Tab S9 (25/11/2026): la coda ricordata torna ferma in fondo, non preparata. Il tocco sull'ora di un
    // paragrafo deve saltare lo stesso, e Play da in fondo deve ripartire da capo invece di finire subito.
    FluidPlaybackStore(RuntimeEnvironment.getApplication()).save(listOf(item("lezione", seconds = 60).toMediaItem()), 0, 59_900L)
    val service = startService()
    val player = service.player!!
    assertEquals(Player.STATE_IDLE, player.playbackState)
    val controller = FluidPlaybackController(player)
    controller.attach()
    assertTrue("si puo' saltare anche prima di preparare", controller.canSeek)
    controller.seekTo(13_000L)
    assertEquals(13_000L, player.currentPosition)

    controller.seekTo(59_900L)
    controller.play()
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("riparte da capo: ${player.currentPosition}", player.currentPosition < 1_000L)
    assertTrue(player.playWhenReady)
    controller.close()
  }

  @Test
  fun aRememberedQueueWithoutDurationsCanStillBeSeekedFromTheTranscript() {
    // R3 (difetto 13): la coda ricordata riaperta ferma, ma l'app non aveva dato `durationMillis` agli elementi. Il lettore
    // non è preparato e la durata non si sa: il tocco sull'ora di un paragrafo deve saltare lo stesso.
    FluidPlaybackStore(RuntimeEnvironment.getApplication())
      .save(listOf(item("lezione").copy(durationMillis = null).toMediaItem()), 0, 2_000L)
    val service = startService()
    val player = service.player!!
    assertEquals(Player.STATE_IDLE, player.playbackState)
    val controller = FluidPlaybackController(player)
    controller.attach()
    assertNull("la durata non si sa ancora", controller.durationMillis)
    controller.seekTo(13_000L)
    assertEquals("il salto arriva al lettore", 13_000L, player.currentPosition)
    assertEquals(13_000L, controller.positionMillis)
    assertEquals("e non lo fa suonare", false, player.playWhenReady)
    assertEquals("ne' lo prepara da solo", Player.STATE_IDLE, player.playbackState)
    controller.close()
  }

  @Test
  fun theControllerStateFollowsThePlayer() {
    val service = startService()
    val controller = FluidPlaybackController(service.player!!)
    controller.attach()
    assertNull(controller.item)
    controller.setItems(listOf(item("a", 30), item("b", 40)), startIndex = 1, startPositionMillis = 5_000L, play = false)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("b", controller.item?.id)
    assertEquals(2, controller.queueSize)
    assertEquals(1, controller.index)
    assertFalse(controller.playing)
    assertTrue(controller.isCurrent("b"))
    controller.changePlaybackSpeed(1.5f)
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(1.5f, controller.playbackSpeed, 0f)
    assertTrue(controller.canChangePlaybackSpeed)
    controller.close()
  }
}
