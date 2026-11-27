package dev.antigravity.fluidengine.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidAudioSpeed
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaDurationLoader
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaSession
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Il **controllo** della riproduzione di sistema per l'interfaccia (F8.13): il `MediaController` di Media3 collegato a
 * [FluidPlaybackService], letto in stati di Compose. È anche la [FluidMediaSession] (con la velocità, [FluidAudioSpeed])
 * che leggono il visore audio e l'onda di `engine-ui`: lo stesso stato alimenta la pagina, il mini-riproduttore, la
 * notifica e la schermata di blocco.
 *
 * - [playing] segue l'**intenzione** (Play premuto e non finito né sospeso), come il tasto di Media3; [buffering] dice
 *   che carica.
 * - [positionMillis] si aggiorna a ogni fotogramma mentre suona ([followPosition]) e a ogni evento; fermo non costa.
 * - [item] è l'elemento che si ascolta, [index] e [queueSize] la coda.
 * - Il timer ([sleepAtMillis], [sleepAtEndOfItem]) e il salto dei silenzi ([skipSilence]) arrivano dagli extra della
 *   sessione: li vede anche un secondo controllo (l'orologio) e restano veri dopo che l'app si è chiusa e riaperta.
 *
 * Tutto si chiama sul thread principale. Si crea con [rememberFluidPlaybackController]; fuori da Compose, [connect] e
 * [close].
 */
@Stable
class FluidPlaybackController internal constructor(
  /** Il lettore: il `MediaController` collegato al servizio (o un `Player` qualunque, nelle prove). */
  val player: Player,
) : FluidMediaSession, FluidAudioSpeed, FluidMediaDurationLoader, AutoCloseable {

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
  override var playbackSpeed: Float by mutableFloatStateOf(1f)
    private set
  override var canChangePlaybackSpeed: Boolean by mutableStateOf(false)
    private set

  /** Vero mentre carica. */
  var buffering: Boolean by mutableStateOf(false)
    private set

  /** Vero quando l'elemento è finito: [play] riparte da capo. */
  var ended: Boolean by mutableStateOf(false)
    private set

  /** L'elemento che si ascolta, o null con la coda vuota. */
  var item: FluidPlaybackItem? by mutableStateOf(null)
    private set

  /** La posizione nella coda e quanto è lunga. */
  var index: Int by mutableIntStateOf(0)
    private set
  var queueSize: Int by mutableIntStateOf(0)
    private set

  /** Quando scatta il timer, in millisecondi di `System.currentTimeMillis`, o null. */
  var sleepAtMillis: Long? by mutableStateOf(null)
    private set

  /** Vero se il timer scatta a fine elemento. */
  var sleepAtEndOfItem: Boolean by mutableStateOf(false)
    private set

  /** Vero se il salto dei silenzi è acceso. */
  var skipSilence: Boolean by mutableStateOf(false)
    private set

  private var attached = false

  private val listener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) = refresh()
    override fun onPlayerErrorChanged(error: PlaybackException?) = refresh()
    override fun onTimelineChanged(timeline: Timeline, reason: Int) = refresh()
    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = refresh()
  }

  internal val controllerListener = object : MediaController.Listener {
    override fun onExtrasChanged(controller: MediaController, extras: Bundle) = readExtras(extras)
  }

  /** Comincia ad ascoltare il lettore. [close] smette e scollega. */
  fun attach() {
    if (attached) return
    attached = true
    player.addListener(listener)
    (player as? MediaController)?.let { readExtras(it.sessionExtras) }
    refresh()
  }

  override fun close() {
    if (!attached) return
    attached = false
    player.removeListener(listener)
    (player as? MediaController)?.release()
  }

  /**
   * Mette in coda [items] e comincia da [startIndex] a [startPositionMillis]. Con [play] falso prepara e basta: la
   * lezione si apre al punto giusto senza suonare.
   */
  fun setItems(items: List<FluidPlaybackItem>, startIndex: Int = 0, startPositionMillis: Long = 0L, play: Boolean = true) {
    if (items.isEmpty()) {
      player.clearMediaItems()
      refresh()
      return
    }
    player.setMediaItems(items.map { it.toMediaItem() }, startIndex.coerceIn(0, items.lastIndex), startPositionMillis.coerceAtLeast(0L))
    player.prepare()
    player.playWhenReady = play
    refresh()
  }

  /** Vero se si sta già ascoltando l'elemento con [id]. */
  fun isCurrent(id: String): Boolean = player.currentMediaItem?.mediaId == id

  override fun requestDuration() {
    if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
  }

  override fun play() {
    // Un elemento finito, anche quello della coda ricordata (fermo in fondo), riparte da capo.
    val atEnd = durationMillis?.let { player.currentPosition >= it - EndSlackMillis } == true
    when {
      player.playbackState == Player.STATE_ENDED -> player.seekToDefaultPosition()
      atEnd && player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) -> player.seekTo(player.currentMediaItemIndex, 0L)
    }
    if (player.playbackState == Player.STATE_IDLE) player.prepare()
    player.play()
    refresh()
  }

  override fun pause() {
    player.pause()
    refresh()
  }

  /**
   * Salta a [positionMillis] nell'elemento che si ascolta. Funziona anche **prima che la durata si sappia** (R3): la coda
   * ricordata riaperta ferma, con elementi senza `durationMillis`, non è ancora preparata e non ha durata ([canSeek] resta
   * falso, perché una barra senza durata non si disegna); il tocco sull'ora di un paragrafo salta lo stesso, con l'indice,
   * senza tenerlo dentro una durata che non c'è. Il lettore non si prepara da solo: la durata arriva con Play, o con
   * [requestDuration].
   */
  override fun seekTo(positionMillis: Long) {
    if (!player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) || player.mediaItemCount == 0) return
    val duration = durationMillis
    val target = if (duration != null) positionMillis.coerceIn(0L, duration) else positionMillis.coerceAtLeast(0L)
    when {
      player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) -> player.seekTo(target)
      // Prima che il lettore sia preparato (la coda ricordata, riaperta ferma) Media3 non offre il salto nell'elemento: si
      // salta con l'indice, che c'e' gia'. Trovato sul Tab S9 il 25/11/2026: il tocco sull'ora non faceva niente.
      player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) -> player.seekTo(player.currentMediaItemIndex, target)
      else -> return
    }
    this.positionMillis = target
  }

  /** Indietro o avanti di [deltaMillis], dentro l'elemento. */
  fun skipBy(deltaMillis: Long) {
    seekTo(player.currentPosition + deltaMillis)
  }

  override fun changePlaybackSpeed(speed: Float) {
    if (!speed.isFinite() || speed <= 0f) return
    player.setPlaybackSpeed(speed)
    refresh()
  }

  /** Il timer: fra [minutes] minuti; 0 lo spegne. */
  fun setSleepTimer(minutes: Int) = send(FluidPlayback.CommandSleepTimer, Bundle().apply { putInt(FluidPlayback.ArgMinutes, minutes.coerceAtLeast(0)) })

  /** Il timer a fine elemento. */
  fun sleepAtEndOfItem() = send(FluidPlayback.CommandSleepTimer, Bundle().apply { putBoolean(FluidPlayback.ArgEndOfItem, true) })

  /** Spegne il timer. */
  fun cancelSleepTimer() = setSleepTimer(0)

  /** Accende o spegne il salto dei silenzi. */
  fun changeSkipSilence(enabled: Boolean) {
    skipSilence = enabled
    send(FluidPlayback.CommandSkipSilence, Bundle().apply { putBoolean(FluidPlayback.ArgEnabled, enabled) })
  }

  private fun send(action: String, args: Bundle) {
    (player as? MediaController)?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
  }

  private fun readExtras(extras: Bundle) {
    sleepAtMillis = if (extras.containsKey(FluidPlayback.ExtraSleepAt)) extras.getLong(FluidPlayback.ExtraSleepAt) else null
    sleepAtEndOfItem = extras.getBoolean(FluidPlayback.ExtraSleepAtEndOfItem, false)
    skipSilence = extras.getBoolean(FluidPlayback.ExtraSkipSilence, false)
  }

  /** Rilegge tutto dal lettore. La chiamano gli eventi. */
  fun refresh() {
    val state = player.playbackState
    val readable = player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
    playing = player.playWhenReady && state != Player.STATE_IDLE && state != Player.STATE_ENDED &&
      player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
    buffering = state == Player.STATE_BUFFERING
    ended = state == Player.STATE_ENDED
    val current: MediaItem? = if (readable) player.currentMediaItem else null
    item = current?.toFluidPlaybackItem()
    index = if (readable) player.currentMediaItemIndex else 0
    queueSize = player.mediaItemCount
    val duration = if (readable) player.duration else C.TIME_UNSET
    durationMillis = duration.takeIf { it != C.TIME_UNSET && it >= 0L } ?: item?.durationMillis
    positionMillis = if (readable) {
      player.currentPosition.coerceAtLeast(0L).let { p -> durationMillis?.let { p.coerceAtMost(it) } ?: p }
    } else {
      0L
    }
    canSeek = durationMillis != null && readable && (player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
      player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM))
    canChangePlaybackSpeed = player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)
    playbackSpeed = player.playbackParameters.speed
    error = player.playerError?.let { it.message ?: it.errorCodeName }
  }

  /** La posizione a ogni fotogramma mentre suona; ferma quando non suona. Non torna: si annulla. */
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

  companion object {
    /** Sotto quanto dalla fine un elemento conta come finito, per [play]. */
    private const val EndSlackMillis = 500L

    /**
     * Si collega al servizio di riproduzione (che parte, se non c'è) e torna il controllo, già attaccato. Sul thread
     * principale. Da chiudere con [close].
     */
    suspend fun connect(context: Context): FluidPlaybackController {
      val app = context.applicationContext
      val token = SessionToken(app, ComponentName(app, FluidPlaybackService::class.java))
      val holder = arrayOfNulls<FluidPlaybackController>(1)
      val future = MediaController.Builder(app, token)
        .setListener(object : MediaController.Listener {
          override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            holder[0]?.controllerListener?.onExtrasChanged(controller, extras)
          }
        })
        .buildAsync()
      val controller = suspendCancellableCoroutine { continuation ->
        future.addListener({
          val result = runCatching { future.get() }
          result.onSuccess { continuation.resume(it) }.onFailure { continuation.resumeWith(Result.failure(it)) }
        }, { Handler(Looper.getMainLooper()).post(it) })
        continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
      }
      return FluidPlaybackController(controller).also {
        holder[0] = it
        it.attach()
      }
    }
  }
}

/**
 * Il controllo della riproduzione per questa composizione: null finché si collega (un attimo), poi il controllo, che
 * segue la posizione a ogni fotogramma mentre suona e si scollega quando la composizione se ne va. La riproduzione no:
 * continua nel servizio, con la notifica.
 */
@Composable
fun rememberFluidPlaybackController(): FluidPlaybackController? {
  val context = LocalContext.current
  var controller by remember { mutableStateOf<FluidPlaybackController?>(null) }
  LaunchedEffect(context) {
    controller = runCatching { FluidPlaybackController.connect(context) }.getOrNull()
  }
  DisposableEffect(Unit) {
    onDispose { controller?.close() }
  }
  controller?.let { connected -> LaunchedEffect(connected) { connected.followPosition() } }
  return controller
}
