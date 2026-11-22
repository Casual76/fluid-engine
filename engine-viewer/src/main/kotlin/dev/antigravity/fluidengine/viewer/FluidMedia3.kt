package dev.antigravity.fluidengine.viewer

import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaSession
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidVideoGeometry
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import kotlinx.coroutines.flow.first

/**
 * Il `Player` di Media3 dell'app come `FluidMediaSession` del visore. Il player resta **dell'app**: lo crea, lo
 * prepara, sceglie il file e lo rilascia lei; la sessione lo legge e gli passa play, pausa e salto.
 *
 * **Media3 è dell'app.** Il modulo lo dichiara `compileOnly` contro la 1.5.1 (la più vecchia fra Fluidify e
 * Gallery v1) e i suoi test girano sulla 1.11.1 (Gallery-2, Pampa notes): a runtime c'è solo la versione dell'app.
 * Usa soltanto l'interfaccia `Player` di `media3-common`, presente così in tutte e due.
 *
 * - [playing] segue l'**intenzione**, come il tasto di Media3 (`Util.shouldShowPlayButton`): vero se il player deve
 *   suonare (`playWhenReady`) e non è fermo, finito o sospeso, anche mentre carica. [buffering] dice che carica.
 * - [positionMillis] si aggiorna **a ogni fotogramma** mentre suona (lo scrubber scorre senza scatti) e a ogni
 *   evento del player; a riposo non costa niente.
 * - [durationMillis] è `null` finché la durata non è nota (una diretta, un flusso che carica); allora [canSeek] è falso.
 * - [play] prepara un player fermo e riparte da capo se il file era finito; [seekTo] si limita alla durata.
 * - Tutto va chiamato sul thread del player (di solito il principale, quello di Compose).
 *
 * Si crea con [rememberFluidMedia3Session]; fuori da Compose, [attach] e [close] a mano.
 */
@Stable
class FluidMedia3Session(val player: Player) : FluidMediaSession, FluidVideoGeometry, AutoCloseable {

  override var playing: Boolean by mutableStateOf(false)
    private set
  override var positionMillis: Long by mutableLongStateOf(0L)
    private set
  override var durationMillis: Long? by mutableStateOf(null)
    private set
  override var canSeek: Boolean by mutableStateOf(false)
    private set
  override var error: String? by mutableStateOf(null)
    private set

  /** Vero mentre il player carica (`STATE_BUFFERING`). */
  var buffering: Boolean by mutableStateOf(false)
    private set

  /** Vero quando il file è finito (`STATE_ENDED`): [play] riparte da capo. */
  var ended: Boolean by mutableStateOf(false)
    private set

  /** Il rapporto d'aspetto del video (con i pixel non quadrati), `null` finché non c'è un fotogramma o è audio. */
  override var videoAspectRatio: Float? by mutableStateOf(null)
    private set

  private var attached = false

  private val listener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = refresh()
    override fun onPlaybackStateChanged(playbackState: Int) = refresh()
    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = refresh()
    override fun onPlayerErrorChanged(error: PlaybackException?) = refresh()
    override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) = refresh()
    override fun onVideoSizeChanged(videoSize: VideoSize) = refresh()
    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int,
    ) = refresh()
  }

  /** Comincia ad ascoltare il player. Si chiama una volta; [close] smette. */
  fun attach() {
    if (attached) return
    attached = true
    player.addListener(listener)
    refresh()
  }

  override fun close() {
    if (!attached) return
    attached = false
    player.removeListener(listener)
  }

  override fun play() {
    val state = player.playbackState
    if (state == Player.STATE_IDLE && player.isCommandAvailable(Player.COMMAND_PREPARE)) {
      player.prepare()
    } else if (state == Player.STATE_ENDED && player.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
      player.seekToDefaultPosition()
    }
    if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.play()
    refresh()
  }

  override fun pause() {
    if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.pause()
    refresh()
  }

  override fun seekTo(positionMillis: Long) {
    if (!canSeek) return
    val duration = durationMillis ?: return
    player.seekTo(positionMillis.coerceIn(0L, duration))
    refresh()
  }

  /** Rilegge tutto dal player. La chiamano gli eventi; serve a mano solo a chi non usa [attach]. */
  fun refresh() {
    val state = player.playbackState
    val readable = player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
    playing = player.playWhenReady && state != Player.STATE_IDLE && state != Player.STATE_ENDED &&
      player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
    buffering = state == Player.STATE_BUFFERING
    ended = state == Player.STATE_ENDED
    val duration = if (readable) player.duration else C.TIME_UNSET
    durationMillis = duration.takeIf { it != C.TIME_UNSET && it >= 0L }
    positionMillis = if (readable) {
      player.currentPosition.coerceAtLeast(0L).let { position -> durationMillis?.let { position.coerceAtMost(it) } ?: position }
    } else {
      0L
    }
    canSeek = durationMillis != null && readable &&
      player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) && player.isCurrentMediaItemSeekable
    error = player.playerError?.let { it.message ?: it.errorCodeName }
    val size = player.videoSize
    videoAspectRatio = if (size.width > 0 && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else null
  }

  /** Aggiorna la posizione a ogni fotogramma mentre suona, e si ferma quando smette. Non torna mai: si annulla. */
  suspend fun followPosition() {
    while (true) {
      if (!playing) snapshotFlow { playing }.first { it }
      withFrameNanos { }
      if (playing && player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
        val position = player.currentPosition.coerceAtLeast(0L)
        positionMillis = durationMillis?.let { position.coerceAtMost(it) } ?: position
      }
    }
  }
}

/**
 * La sessione di [player] per questa composizione: ascolta il player finché resta, e tiene la posizione al passo
 * coi fotogrammi mentre suona. Il player non si rilascia qui: è dell'app.
 */
@Composable
fun rememberFluidMedia3Session(player: Player): FluidMedia3Session {
  val session = remember(player) { FluidMedia3Session(player) }
  DisposableEffect(session) {
    session.attach()
    onDispose { session.close() }
  }
  LaunchedEffect(session) { session.followPosition() }
  return session
}

/**
 * La superficie video di [session], centrata e col suo rapporto d'aspetto.
 *
 * Di serie è un `TextureView`: si disegna nella gerarchia delle viste, quindi il vetro dei comandi sopra il video lo
 * cattura come ogni altro contenuto. [surfaceView] vero usa un `SurfaceView` (HDR, meno batteria, come Gallery-2),
 * che però sta in una finestra sua: il vetro sopra non lo vede e mostra il fondo nero.
 */
@Composable
fun FluidMedia3Video(session: FluidMedia3Session, modifier: Modifier = Modifier, surfaceView: Boolean = false) {
  val player = session.player
  val ratio = session.videoAspectRatio
  Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val fitted = if (ratio != null && ratio.isFinite() && ratio > 0f) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize()
    key(player, surfaceView) {
      if (surfaceView) {
        AndroidView(
          factory = { context -> SurfaceView(context).also(player::setVideoSurfaceView) },
          modifier = fitted,
          onRelease = { view -> player.clearVideoSurfaceView(view) },
        )
      } else {
        AndroidView(
          factory = { context -> TextureView(context).also(player::setVideoTextureView) },
          modifier = fitted,
          onRelease = { view -> player.clearVideoTextureView(view) },
        )
      }
    }
  }
}

/**
 * Il contenuto del visore per un file che [session] riproduce: un video con la superficie ([video] vero, di serie)
 * o un audio con la sua copertina ([artwork]). È il `FluidViewerContent.Media` che i preset video e audio leggono.
 */
fun fluidMedia3Content(
  session: FluidMedia3Session,
  video: Boolean = true,
  surfaceView: Boolean = false,
  artwork: (@Composable () -> Unit)? = null,
): FluidViewerContent.Media {
  val surface: @Composable () -> Unit = { FluidMedia3Video(session, surfaceView = surfaceView) }
  return FluidViewerContent.Media(session = session, sampleVideo = video && !surfaceView, videoSurface = surface.takeIf { video }, artwork = artwork)
}
