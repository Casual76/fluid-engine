package dev.antigravity.fluidengine.viewer

import androidx.media3.common.C
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * L'adattatore Media3 con un player finto: un `Player` vero di `media3-common` (l'interfaccia della versione dei
 * test, 1.11.1 di serie), dove ogni metodo che l'adattatore non deve chiamare fa fallire il test.
 */
class FluidMedia3SessionTest {

  private class FakePlayer : Player by unsupportedPlayer() {
    var state = Player.STATE_IDLE
    var whenReady = false
    var suppression = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    var position = 0L
    var durationMs = C.TIME_UNSET
    var seekable = true
    var playerErrorValue: PlaybackException? = null
    var size: VideoSize = VideoSize.UNKNOWN
    val commands = mutableSetOf(
      Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
      Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    )
    val listeners = mutableListOf<Player.Listener>()
    val calls = mutableListOf<String>()

    override fun getPlaybackState() = state
    override fun getPlayWhenReady() = whenReady
    override fun getPlaybackSuppressionReason() = suppression
    override fun isPlaying() = whenReady && state == Player.STATE_READY && suppression == Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override fun getCurrentPosition() = position
    override fun getDuration() = durationMs
    override fun isCurrentMediaItemSeekable() = seekable
    override fun isCommandAvailable(command: Int) = command in commands
    override fun getPlayerError() = playerErrorValue
    override fun getVideoSize() = size
    override fun addListener(listener: Player.Listener) { listeners += listener }
    override fun removeListener(listener: Player.Listener) { listeners -= listener }

    override fun prepare() {
      calls += "prepare"
      state = Player.STATE_BUFFERING
      listeners.toList().forEach { it.onPlaybackStateChanged(state) }
    }

    override fun play() {
      calls += "play"
      whenReady = true
      listeners.toList().forEach { it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
    }

    override fun pause() {
      calls += "pause"
      whenReady = false
      listeners.toList().forEach { it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
    }

    override fun seekTo(positionMs: Long) {
      calls += "seekTo($positionMs)"
      position = positionMs
    }

    override fun seekToDefaultPosition() {
      calls += "seekToDefaultPosition"
      position = 0
      state = Player.STATE_BUFFERING
    }

    /** Il player è pronto con un file di [duration] ms. */
    fun ready(duration: Long) {
      durationMs = duration
      state = Player.STATE_READY
      listeners.toList().forEach { it.onPlaybackStateChanged(state) }
    }

  }

  private fun session(player: FakePlayer = FakePlayer()) = FluidMedia3Session(player).apply { attach() }

  @Test
  fun `the tests run on the media3 version they declare`() {
    val expected = System.getProperty("viewer.media3Test")
    if (expected != null) assertEquals(expected, MediaLibraryInfo.VERSION)
    println("F6C.4: adattatore Media3 provato su media3-common ${MediaLibraryInfo.VERSION}")
  }

  @Test
  fun `attach listens and close stops`() {
    val player = FakePlayer()
    val session = FluidMedia3Session(player)
    assertTrue(player.listeners.isEmpty())
    session.attach()
    session.attach()
    assertEquals(1, player.listeners.size)
    session.close()
    assertTrue(player.listeners.isEmpty())
    session.close()
  }

  @Test
  fun `an idle player is prepared then played`() {
    val player = FakePlayer()
    val session = session(player)
    assertFalse(session.playing)
    session.play()
    assertEquals(listOf("prepare", "play"), player.calls)
    // L'intenzione conta: mentre carica il tasto mostra già la pausa.
    assertTrue(session.playing)
    assertTrue(session.buffering)
    assertNull(session.durationMillis)
    assertFalse(session.canSeek)
    player.ready(60_000)
    assertTrue(session.playing)
    assertFalse(session.buffering)
    assertEquals(60_000L, session.durationMillis)
    assertTrue(session.canSeek)
  }

  @Test
  fun `pause and the position follow the player`() {
    val player = FakePlayer()
    val session = session(player)
    player.ready(10_000)
    session.play()
    player.position = 4_200
    session.refresh()
    assertEquals(4_200L, session.positionMillis)
    session.pause()
    assertFalse(session.playing)
    assertEquals(listOf("play", "pause"), player.calls)
  }

  @Test
  fun `seek is clamped to the duration and refused without a duration`() {
    val player = FakePlayer()
    val session = session(player)
    session.seekTo(5_000)
    assertTrue(player.calls.isEmpty())
    player.ready(8_000)
    session.seekTo(12_000)
    session.seekTo(-50)
    assertEquals(listOf("seekTo(8000)", "seekTo(0)"), player.calls)
    assertEquals(0L, session.positionMillis)
  }

  @Test
  fun `a finished file starts again from the beginning`() {
    val player = FakePlayer()
    val session = session(player)
    player.ready(3_000)
    player.whenReady = true
    player.position = 3_000
    player.state = Player.STATE_ENDED
    session.refresh()
    assertTrue(session.ended)
    assertFalse(session.playing)
    session.play()
    assertEquals(listOf("seekToDefaultPosition", "play"), player.calls)
  }

  @Test
  fun `a suppressed player is not playing`() {
    val player = FakePlayer()
    val session = session(player)
    player.ready(1_000)
    session.play()
    player.suppression = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
    player.listeners.forEach { it.onPlaybackSuppressionReasonChanged(player.suppression) }
    assertFalse(session.playing)
  }

  @Test
  fun `missing commands are respected`() {
    val player = FakePlayer()
    player.commands.clear()
    val session = session(player)
    player.durationMs = 5_000
    player.position = 1_000
    player.state = Player.STATE_READY
    session.refresh()
    // Senza COMMAND_GET_CURRENT_MEDIA_ITEM posizione e durata non si leggono.
    assertNull(session.durationMillis)
    assertEquals(0L, session.positionMillis)
    assertFalse(session.canSeek)
    session.play()
    session.pause()
    session.seekTo(100)
    assertTrue(player.calls.isEmpty())
  }

  @Test
  fun `an unseekable item cannot be scrubbed`() {
    val player = FakePlayer()
    player.seekable = false
    val session = session(player)
    player.ready(9_000)
    assertEquals(9_000L, session.durationMillis)
    assertFalse(session.canSeek)
  }

  @Test
  fun `the player error becomes the session error`() {
    val player = FakePlayer()
    val session = session(player)
    player.playerErrorValue = PlaybackException("Formato non supportato", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
    player.listeners.forEach { it.onPlayerErrorChanged(player.playerErrorValue) }
    assertEquals("Formato non supportato", session.error)
    player.playerErrorValue = PlaybackException(null, null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
    session.refresh()
    assertEquals("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED", session.error)
    player.playerErrorValue = null
    session.refresh()
    assertNull(session.error)
  }

  @Test
  fun `the video aspect ratio counts non square pixels`() {
    val player = FakePlayer()
    val session = session(player)
    assertNull(session.videoAspectRatio)
    player.size = VideoSize(1440, 1080, 4f / 3f)
    player.listeners.forEach { it.onVideoSizeChanged(player.size) }
    assertEquals(16f / 9f, session.videoAspectRatio!!, 0.001f)
  }

  @Test
  fun `the position never runs past the duration`() {
    val player = FakePlayer()
    val session = session(player)
    player.ready(2_000)
    player.position = 2_150
    session.refresh()
    assertEquals(2_000L, session.positionMillis)
  }

  @Test
  fun `the viewer content carries a surface only for video`() {
    val session = session()
    assertTrue(fluidMedia3Content(session).videoSurface != null)
    val audio = fluidMedia3Content(session, video = false)
    assertNull(audio.videoSurface)
    assertTrue(audio.session === session)
  }
}

/** Un `Player` in cui ogni metodo fallisce: il finto sopra ridefinisce solo quelli che l'adattatore può chiamare. */
private fun unsupportedPlayer(): Player = Proxy.newProxyInstance(
  Player::class.java.classLoader,
  arrayOf(Player::class.java),
) { _, method, _ -> throw UnsupportedOperationException("l'adattatore non deve chiamare ${method.name}") } as Player
