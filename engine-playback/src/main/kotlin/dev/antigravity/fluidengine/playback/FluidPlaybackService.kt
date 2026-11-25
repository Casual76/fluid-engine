package dev.antigravity.fluidengine.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray
import org.json.JSONObject

/**
 * Il **servizio di riproduzione** di `engine-playback` (F8.13): un `MediaSessionService` di Media3 con un ExoPlayer, già
 * dichiarato nel manifesto del modulo. L'app non lo avvia mai a mano: lo avvia [FluidPlaybackController] collegandosi, e
 * il sistema (la notifica, la schermata di blocco, i tasti delle cuffie, l'orologio, l'auto) attraverso la sessione.
 *
 * Quello che fa da solo:
 *  - **la notifica e la schermata di blocco** con Play, e meno e più 15 secondi ai lati (le icone di Media3), e il tocco
 *    che riapre l'app ([FluidPlaybackConfig.sessionActivity]);
 *  - **la cuffia che si stacca** mette in pausa (`AUDIO_BECOMING_NOISY`), e così **una chiamata** o un'altra app che
 *    suona (il fuoco audio, che il parlato cede mettendosi in pausa);
 *  - **la velocità** (dal controllo) e **il salto dei silenzi** ([FluidPlayback.CommandSkipSilence], il «Salta silenzi» di
 *    Memo Vocali, fatto dal lettore senza toccare il file);
 *  - **il timer di spegnimento** ([FluidPlayback.CommandSleepTimer]): fra tot minuti, o a fine elemento;
 *  - **la coda ricordata**: l'elenco, l'elemento e il punto si salvano mentre si ascolta, e tornano alla ripresa di sistema
 *    (Play sulle cuffie con l'app chiusa) e all'avvio, fermi ([FluidPlaybackConfig.restoreOnStart]): niente che suona da
 *    solo.
 *
 * Android Auto mostra solo le app passate dalla sua revisione (o col modo sviluppatore): la sessione è quella giusta, ma
 * l'app nell'auto va chiesta a Google, quando servirà.
 */
@OptIn(UnstableApi::class)
open class FluidPlaybackService : MediaSessionService() {

  /** La sessione, finché il servizio vive. Per le prove e per chi estende il servizio. */
  var session: MediaSession? = null
    private set

  /** Il lettore, finché il servizio vive. */
  val player: ExoPlayer? get() = session?.player as? ExoPlayer

  /** Le risposte della sessione (per le prove). */
  internal val callback = FluidPlaybackCallback()

  private lateinit var store: FluidPlaybackStore
  private val main = Handler(Looper.getMainLooper())
  private var sleepAt: Long? = null
  private var sleepAtEndOfItem = false
  private val sleep = Runnable { fireSleep() }
  private val saver = object : Runnable {
    override fun run() {
      saveNow()
      if (player?.isPlaying == true) main.postDelayed(this, SaveEveryMillis)
    }
  }

  override fun onCreate() {
    super.onCreate()
    val config = FluidPlayback.config
    store = FluidPlaybackStore(this)
    val exo = ExoPlayer.Builder(this)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(C.USAGE_MEDIA)
          .setContentType(if (config.speech) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC)
          .build(),
        /* handleAudioFocus = */ true,
      )
      .setHandleAudioBecomingNoisy(true)
      .setSeekBackIncrementMs(config.skipBackMillis)
      .setSeekForwardIncrementMs(config.skipForwardMillis)
      .setWakeMode(if (config.networkWakeLock) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL)
      .build()
    exo.addListener(object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        main.removeCallbacks(saver)
        if (isPlaying) main.postDelayed(saver, SaveEveryMillis) else saveNow()
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        saveNow()
        if (sleepAtEndOfItem && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
          // La transizione automatica vuol dire che l'elemento è finito: il timer a fine elemento scatta.
          fireSleep()
        }
      }

      override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) saveNow()
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED && sleepAtEndOfItem) fireSleep()
      }
    })
    val created = MediaSession.Builder(this, exo)
      .setId(SessionId)
      .setCallback(callback)
      .setMediaButtonPreferences(skipButtons(config))
      .apply { sessionActivity(config)?.let(::setSessionActivity) }
      .build()
    session = created
    setMediaNotificationProvider(
      DefaultMediaNotificationProvider.Builder(this)
        .setChannelName(R.string.fluid_playback_channel)
        .build(),
    )
    publishExtras()
    if (config.restoreOnStart) {
      store.load()?.let { saved ->
        exo.setMediaItems(saved.mediaItems, saved.startIndex, saved.startPositionMs)
        // Fermo: la coda c'è, il punto è giusto, ma non suona finché qualcuno non preme Play.
        exo.playWhenReady = false
      }
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

  override fun onDestroy() {
    saveNow()
    main.removeCallbacksAndMessages(null)
    session?.run {
      player.release()
      release()
    }
    session = null
    super.onDestroy()
  }

  /** Accende o spegne il salto dei silenzi. */
  fun setSkipSilence(enabled: Boolean) {
    player?.skipSilenceEnabled = enabled
    publishExtras()
  }

  /** Il timer: fra [minutes] minuti (0 lo spegne), o a fine elemento con [endOfItem]. */
  fun setSleepTimer(minutes: Int, endOfItem: Boolean = false) {
    main.removeCallbacks(sleep)
    sleepAt = null
    sleepAtEndOfItem = false
    player?.pauseAtEndOfMediaItems = false
    when {
      endOfItem -> {
        sleepAtEndOfItem = true
        player?.pauseAtEndOfMediaItems = true
      }
      minutes > 0 -> {
        val delay = minutes * 60_000L
        sleepAt = System.currentTimeMillis() + delay
        main.postDelayed(sleep, delay)
      }
    }
    publishExtras()
  }

  private fun fireSleep() {
    player?.pause()
    main.removeCallbacks(sleep)
    sleepAt = null
    sleepAtEndOfItem = false
    player?.pauseAtEndOfMediaItems = false
    publishExtras()
  }

  private fun publishExtras() {
    val extras = Bundle().apply {
      sleepAt?.let { putLong(FluidPlayback.ExtraSleepAt, it) }
      putBoolean(FluidPlayback.ExtraSleepAtEndOfItem, sleepAtEndOfItem)
      putBoolean(FluidPlayback.ExtraSkipSilence, player?.skipSilenceEnabled == true)
    }
    session?.setSessionExtras(extras)
  }

  private fun saveNow() {
    val exo = player ?: return
    if (exo.mediaItemCount == 0) return
    val items = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it) }
    // Un elemento finito si ricorda dall'inizio: riaperto, riparte da capo invece di finire subito.
    val position = if (exo.playbackState == Player.STATE_ENDED) 0L else exo.currentPosition.coerceAtLeast(0L)
    store.save(items, exo.currentMediaItemIndex, position)
  }

  private fun sessionActivity(config: FluidPlaybackConfig): PendingIntent? {
    config.sessionActivity?.let { return it(this) }
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
    launch.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }

  /** Le risposte della sessione: chi si collega, gli elementi che arrivano, i comandi dell'engine, la ripresa. */
  internal inner class FluidPlaybackCallback : MediaSession.Callback {

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
      val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        .add(SessionCommand(FluidPlayback.CommandSleepTimer, Bundle.EMPTY))
        .add(SessionCommand(FluidPlayback.CommandSkipSilence, Bundle.EMPTY))
        .build()
      return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(commands)
        .setMediaButtonPreferences(skipButtons(FluidPlayback.config))
        .build()
    }

    override fun onAddMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems.map(::resolve).toMutableList())

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      when (customCommand.customAction) {
        FluidPlayback.CommandSleepTimer -> setSleepTimer(args.getInt(FluidPlayback.ArgMinutes, 0), args.getBoolean(FluidPlayback.ArgEndOfItem, false))
        FluidPlayback.CommandSkipSilence -> setSkipSilence(args.getBoolean(FluidPlayback.ArgEnabled, false))
        else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
      }
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onPlaybackResumption(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
      val saved = store.load() ?: MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
      return Futures.immediateFuture(saved)
    }
  }

  internal companion object {
    const val SessionId = "fluid-playback"
    const val SaveEveryMillis = 5_000L

    /** Meno e più 15 secondi ai lati di Play, come comandi del lettore (le icone e i posti sono di Media3). */
    fun skipButtons(config: FluidPlaybackConfig): ImmutableList<CommandButton> = ImmutableList.of(
      CommandButton.Builder(iconForBack(config.skipBackMillis))
        .setPlayerCommand(Player.COMMAND_SEEK_BACK)
        .setDisplayName("−${config.skipBackMillis / 1000}")
        .setSlots(CommandButton.SLOT_BACK)
        .build(),
      CommandButton.Builder(iconForForward(config.skipForwardMillis))
        .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
        .setDisplayName("+${config.skipForwardMillis / 1000}")
        .setSlots(CommandButton.SLOT_FORWARD)
        .build(),
    )

    private fun iconForBack(millis: Long): Int = when (millis) {
      5_000L -> CommandButton.ICON_SKIP_BACK_5
      10_000L -> CommandButton.ICON_SKIP_BACK_10
      15_000L -> CommandButton.ICON_SKIP_BACK_15
      30_000L -> CommandButton.ICON_SKIP_BACK_30
      else -> CommandButton.ICON_SKIP_BACK
    }

    private fun iconForForward(millis: Long): Int = when (millis) {
      5_000L -> CommandButton.ICON_SKIP_FORWARD_5
      10_000L -> CommandButton.ICON_SKIP_FORWARD_10
      15_000L -> CommandButton.ICON_SKIP_FORWARD_15
      30_000L -> CommandButton.ICON_SKIP_FORWARD_30
      else -> CommandButton.ICON_SKIP_FORWARD
    }

    /**
     * Un elemento arrivato da un controllo: l'indirizzo viaggia nei metadati della richiesta (Media3 non porta quello
     * locale attraverso la sessione), e qui torna al suo posto.
     */
    fun resolve(item: MediaItem): MediaItem {
      if (item.localConfiguration != null) return item
      val uri = item.requestMetadata.mediaUri ?: return item
      return item.buildUpon().setUri(uri).build()
    }
  }
}

/**
 * La coda ricordata: gli elementi (con l'indirizzo e i metadati che servono alla notifica), quale si ascoltava e dove. In
 * un file di preferenze del modulo, in JSON: è piccola e si scrive a ogni cambio e ogni 5 secondi mentre suona.
 */
internal class FluidPlaybackStore(context: Context) {
  private val prefs = context.getSharedPreferences("fluid-playback", Context.MODE_PRIVATE)

  fun save(items: List<MediaItem>, index: Int, positionMillis: Long) {
    val array = JSONArray()
    items.forEach { item ->
      val fluid = item.toFluidPlaybackItem() ?: return@forEach
      array.put(
        JSONObject()
          .put("id", fluid.id)
          .put("uri", fluid.uri.toString())
          .put("title", fluid.title)
          .putOpt("subtitle", fluid.subtitle)
          .putOpt("artwork", fluid.artworkUri?.toString())
          .putOpt("duration", fluid.durationMillis)
          .putOpt("mime", fluid.mimeType),
      )
    }
    prefs.edit()
      .putString(KeyQueue, array.toString())
      .putInt(KeyIndex, index)
      .putLong(KeyPosition, positionMillis)
      .apply()
  }

  fun load(): MediaSession.MediaItemsWithStartPosition? {
    val raw = prefs.getString(KeyQueue, null) ?: return null
    val items = runCatching {
      val array = JSONArray(raw)
      (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        FluidPlaybackItem(
          id = o.getString("id"),
          uri = Uri.parse(o.getString("uri")),
          title = o.optString("title"),
          subtitle = o.optString("subtitle").takeIf { o.has("subtitle") },
          artworkUri = o.optString("artwork").takeIf { o.has("artwork") }?.let(Uri::parse),
          durationMillis = if (o.has("duration")) o.getLong("duration") else null,
          mimeType = o.optString("mime").takeIf { o.has("mime") },
        ).toMediaItem()
      }
    }.getOrNull() ?: return null
    if (items.isEmpty()) return null
    val index = prefs.getInt(KeyIndex, 0).coerceIn(0, items.lastIndex)
    return MediaSession.MediaItemsWithStartPosition(items, index, prefs.getLong(KeyPosition, 0L).coerceAtLeast(0L))
  }

  fun clear() {
    prefs.edit().clear().apply()
  }

  private companion object {
    const val KeyQueue = "queue"
    const val KeyIndex = "index"
    const val KeyPosition = "position"
  }
}
