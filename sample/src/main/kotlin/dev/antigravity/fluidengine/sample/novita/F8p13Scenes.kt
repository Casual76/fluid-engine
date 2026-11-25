package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.audio.FluidAudioEnvelopes
import dev.antigravity.fluidengine.audio.FluidAudioRecorder
import dev.antigravity.fluidengine.audio.FluidRecordingFormat
import dev.antigravity.fluidengine.audio.FluidRecorderStatus
import dev.antigravity.fluidengine.foundation.audio.FluidAudioEnvelope
import dev.antigravity.fluidengine.foundation.audio.FluidRecordingQuality
import dev.antigravity.fluidengine.playback.FluidPlaybackController
import dev.antigravity.fluidengine.playback.FluidPlaybackItem
import dev.antigravity.fluidengine.playback.rememberFluidPlaybackController
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.transcript.FluidTranscript
import dev.antigravity.fluidengine.transcript.FluidTranscriptParagraph
import dev.antigravity.fluidengine.transcript.FluidTranscriptView
import dev.antigravity.fluidengine.transcript.rememberFluidTranscriptState
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAudioTimeline
import dev.antigravity.fluidengine.ui.fluid.FluidAudioWaveform
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidPermission
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidRecorderPhase
import dev.antigravity.fluidengine.ui.fluid.FluidRecordingQualityRow
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrubber
import dev.antigravity.fluidengine.ui.fluid.FluidScrubbing
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSpokenWord
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidVoiceRecorder
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.fluidPermissionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.rememberFluidAudioTimelineState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPermission
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaSession
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

// F8.13 · Audio. Quattro scene, coi tre moduli nuovi veri: il registratore (engine-audio) col microfono del telefono, la
// riproduzione di sistema (engine-playback, Media3: la notifica e la schermata di blocco sono quelle di Android) e
// l'ascolto dal paragrafo (engine-transcript). La lezione di prova e' un audio di 59 secondi letto dalla voce italiana di
// Windows, con i tempi veri di ogni parola (res/raw/novita_lezione*, scritti il 25 novembre 2026 per questa pagina).
// Il microfono si chiede solo al tasto rosso, mai all'apertura.

internal val F8p13Scenes = listOf(
  NewElementScene(
    "memo-vocali", "I memo vocali",
    "Il tasto rosso diventa il pannello: l'onda dal vivo, pausa e ripresa, Fine; poi il memo si riascolta",
  ) { MemoScene(it) },
  NewElementScene(
    "onda-audio", "L'onda da scorrere",
    "Il messaggio audio di Messaggi e l'onda di Memo Vocali col pizzico per lo zoom, su tre fondi",
  ) { WaveScene(it) },
  NewElementScene(
    "riproduzione-sistema", "La lezione in sottofondo",
    "Notifica e schermata di blocco, meno e più 15 secondi, velocità, salta i silenzi, il timer",
  ) { PlaybackScene(it) },
  NewElementScene(
    "ascolto-paragrafo", "L'ascolto dal paragrafo",
    "Tocca una parola per sentirla, l'ora per il paragrafo; la pagina segue la voce senza saltare",
  ) { TranscriptScene(it) },
)

internal val F8p13Section = NewElementSection(
  pack = "F8.13",
  title = "Audio",
  scenes = F8p13Scenes,
  detail = "Il registratore di Memo Vocali, l'onda da scorrere, la riproduzione fuori dall'app e l'ascolto dal paragrafo",
)

internal val F8p13TryItems = listOf(
  TryItem(
    "f8-13-memo", TryArea.Pages, "Registra un memo",
    "Tocca il tasto rosso, concedi il microfono, parla, metti in pausa e riprendi, poi Fine: il memo nuovo si riascolta dall'elenco con l'onda.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › I memo vocali"),
  ),
  TryItem(
    "f8-13-onda", TryArea.Pages, "Scorri l'onda col dito",
    "Trascina sul messaggio audio e sull'onda di Memo Vocali, lanciala, pizzica per lo zoom: deve restare nitida e il punto deve tenere.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › L'onda da scorrere"),
  ),
  TryItem(
    "f8-13-sistema", TryArea.Pages, "La lezione a schermo spento",
    "Avvia la lezione, blocca il telefono: i comandi devono esserci sulla schermata di blocco e nella tendina, con meno e più 15 secondi; stacca le cuffie e deve fermarsi.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La lezione in sottofondo"),
  ),
  TryItem(
    "f8-13-paragrafo", TryArea.Pages, "Tocca una parola",
    "Avvia la lezione e tocca «ghiacciaio»: deve partire da quella parola, non dall'inizio della frase; poi scorri altrove e usa «Torna alla voce».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › L'ascolto dal paragrafo"),
  ),
)

/** I tag dei test. */
internal object F8p13Tags {
  const val Memos = "f8p13:memo"
  const val Recorder = "f8p13:registratore"
  const val Bubble = "f8p13:bolla"
  const val Timeline = "f8p13:onda"
  const val Ground = "f8p13:fondo"
  const val Transcript = "f8p13:trascrizione"
  const val Timing = "f8p13:tempi"
  const val Speed = "f8p13:velocita"
  const val Sleep = "f8p13:timer"
  const val SkipSilence = "f8p13:silenzi"
  const val Play = "f8p13:play"
  const val Quality = "f8p13:qualita"

  fun memo(name: String): String = "f8p13:memo:$name"
}

/**
 * I ganci delle prove: il registratore (un microfono finto) e la cartella dei memo. La galleria usa quelli veri.
 */
internal object F8p13Audio {
  var recorderFactory: (Context) -> FluidAudioRecorder = { FluidAudioRecorder(it) }
  var memoFolder: (Context) -> File = { File(it.filesDir, "novita-memo") }
}

/** Un lettore da usare al posto della riproduzione di sistema (le prove: Robolectric non si collega al servizio). */
internal val LocalF8p13Player = staticCompositionLocalOf<FluidMediaSession?> { null }

// --- La lezione di prova ------------------------------------------------------------------------------------------

/** L'elemento della lezione per la riproduzione di sistema. */
private fun lectureItem(context: Context): FluidPlaybackItem = FluidPlaybackItem(
  id = "novita-lezione",
  uri = Uri.parse("android.resource://${context.packageName}/${R.raw.novita_lezione}"),
  title = "Il ciclo dell'acqua",
  subtitle = "Lezione di scienze · Fluid Glass",
  durationMillis = LectureDurationMillis,
  mimeType = "audio/mp4",
)

private const val LectureDurationMillis = 58_804L

/** La trascrizione della lezione, coi tempi per parola, dal file della galleria. */
internal fun lectureTranscript(context: Context, words: Boolean = true): FluidTranscript {
  val json = context.resources.openRawResource(R.raw.novita_lezione_tempi).use { it.readBytes().toString(Charsets.UTF_8) }
  val paragraphs = JSONObject(json).getJSONArray("paragraphs")
  return FluidTranscript(
    (0 until paragraphs.length()).map { i ->
      val p = paragraphs.getJSONObject(i)
      val list = p.getJSONArray("words")
      FluidTranscriptParagraph(
        text = p.getString("text"),
        startMillis = p.getLong("startMs"),
        endMillis = p.getLong("endMs"),
        words = if (!words) emptyList() else (0 until list.length()).map { k ->
          val w = list.getJSONArray(k)
          FluidSpokenWord(w.getInt(0), w.getInt(1), w.getLong(2), w.getLong(3))
        },
        key = "p$i",
      )
    },
  )
}

/** L'onda della lezione, salvata accanto all'audio della galleria nel formato dell'engine. */
internal fun lectureEnvelope(context: Context): FluidAudioEnvelope =
  context.resources.openRawResource(R.raw.novita_lezione_onda).use { FluidAudioEnvelope.read(it) }

/**
 * Il lettore della lezione: quello delle prove se c'e', se no la riproduzione di sistema (null finche' si collega). La
 * lezione si carica una volta, ferma.
 */
@Composable
private fun rememberLecturePlayer(): FluidMediaSession? {
  LocalF8p13Player.current?.let { return it }
  val context = LocalContext.current
  val controller = rememberFluidPlaybackController()
  LaunchedEffect(controller) {
    if (controller != null && !controller.isCurrent("novita-lezione")) {
      controller.setItems(listOf(lectureItem(context)), play = false)
    }
  }
  return controller
}

// --- I fondi -------------------------------------------------------------------------------------------------------

/** Il fondo di una scena. */
internal enum class F8p13Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

@Composable
private fun GroundPicker(ground: F8p13Ground, onSelect: (F8p13Ground) -> Unit) {
  FluidSegmentedControl(
    options = F8p13Ground.entries,
    selected = ground,
    onSelect = onSelect,
    modifier = Modifier.fillMaxWidth().testTag(F8p13Tags.Ground),
    label = { it.label },
  )
}

/** La pagina, o una foto con sopra un pannello di vetro che porta l'elemento. */
@Composable
private fun Stage(ground: F8p13Ground, content: @Composable ColumnScope.() -> Unit) {
  if (ground == F8p13Ground.Page) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    return
  }
  val photo = rememberGlassBackdrop()
  Box(Modifier.padding(top = 4.dp).fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Group))) {
    Image(
      painter = painterResource(if (ground == F8p13Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.matchParentSize().glassBackdropSource(photo),
    )
    Column(
      Modifier
        .padding(horizontal = 12.dp, vertical = 28.dp)
        .fillMaxWidth()
        .fluidGlassPanel(photo, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Group), GlassRole.Modal)
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
private fun Caption(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
  )
}

/** Play e pausa per un lettore, un vetro tondo. */
@Composable
private fun PlayPause(player: FluidMediaSession?, size: androidx.compose.ui.unit.Dp = 44.dp, onPlay: (() -> Unit)? = null) {
  val playing = player?.playing == true
  FluidGlassIconButton(
    onClick = {
      when {
        player == null -> Unit
        playing -> player.pause()
        onPlay != null -> onPlay()
        else -> player.play()
      }
    },
    size = size,
    enabled = player != null,
    modifier = Modifier.testTag(F8p13Tags.Play).semantics { contentDescription = if (playing) "Pausa" else "Riproduci" },
  ) {
    FluidIcon(if (playing) FluidIcons.Filled.Pause else FluidIcons.Filled.PlayArrow, null)
  }
}

// --- I memo vocali -------------------------------------------------------------------------------------------------

/** Un memo registrato nella galleria. */
private class Memo(val file: File, val envelope: FluidAudioEnvelope) {
  val name: String get() = file.nameWithoutExtension
  val title: String get() = name.replace('-', ' ').replaceFirstChar { it.uppercase() }
  val durationMillis: Long get() = envelope.durationMillis
}

@Composable
private fun MemoScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val recorder = remember { F8p13Audio.recorderFactory(context.applicationContext) }
  val status by recorder.status.collectAsState()
  val clipping by recorder.clipping.collectAsState()
  val memos = remember { mutableStateListOf<Memo>() }
  var open by remember { mutableStateOf<String?>(null) }
  val player = rememberLecturePlayer()
  val folder = remember { F8p13Audio.memoFolder(context) }
  val microphone = rememberFluidPermission(
    FluidPermission.Microphone,
    reason = "Per registrare un memo con la tua voce. L'audio resta sul telefono.",
  )
  var startedAt by remember { mutableStateOf(0L) }
  // F8.13b: la qualità la sceglie l'utente (la riga di vetro qui sotto); di serie la Standard, AAC a 96 kbit/s.
  var qualityId by rememberSaveable { mutableStateOf(FluidRecordingQuality.Standard.id) }
  val quality = FluidRecordingQuality.byId(qualityId) ?: FluidRecordingQuality.Standard

  LaunchedEffect(folder) {
    val found = withContext(Dispatchers.IO) {
      folder.listFiles { f -> f.extension == "m4a" || f.extension == "wav" }.orEmpty()
        .sortedByDescending { it.lastModified() }
        .mapNotNull { file -> FluidAudioEnvelopes.load(file)?.let { Memo(file, it) } }
    }
    memos.clear()
    memos.addAll(found)
  }
  DisposableEffect(recorder) {
    onDispose {
      // Uscendo a meta' registrazione il memo si butta: la galleria non ha un servizio per registrare in sottofondo.
      if (recorder.status.value is FluidRecorderStatus.Recording || recorder.status.value is FluidRecorderStatus.Paused) recorder.discard()
    }
  }

  val phase = when (status) {
    is FluidRecorderStatus.Recording -> FluidRecorderPhase.Recording
    is FluidRecorderStatus.Paused -> FluidRecorderPhase.Paused
    else -> FluidRecorderPhase.Idle
  }

  fun record() {
    microphone.request {
      val index = (memos.size + 1)
      val target = File(folder, "memo-$index-${System.currentTimeMillis() % 100_000}.${FluidRecordingFormat.of(quality).extension}")
      player?.pause()
      startedAt = System.currentTimeMillis()
      recorder.start(target, quality)
    }
  }

  FluidScreen(
    title = "Memo vocali",
    subtitle = "F8.13 · memo-vocali",
    onBack = onBack,
    extraBottomPadding = 96.dp,
    overlay = { pageGlass ->
      FluidVoiceRecorder(
        phase = phase,
        onRecord = ::record,
        onPause = { recorder.pause() },
        onResume = { recorder.resume() },
        onDone = {
          scope.launch {
            val done = recorder.stop()
            if (done != null) {
              memos.add(0, Memo(done.file, done.envelope))
              open = done.file.nameWithoutExtension
            }
            recorder.reset()
          }
        },
        envelope = recorder.envelope,
        elapsedMillis = recorder::elapsedMillis,
        title = "Nuova registrazione",
        subtitle = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(startedAt.takeIf { it > 0L } ?: System.currentTimeMillis())),
        backdrop = pageGlass,
        clipping = clipping,
        interrupted = (status as? FluidRecorderStatus.Paused)?.interrupted == true,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(bottom = 12.dp)
          .fluidPermissionSource(microphone)
          .testTag(F8p13Tags.Recorder),
      )
    },
  ) {
    item(key = "spiegazione") {
      Caption(
        "Il tasto rosso registra col microfono del telefono (engine-audio: AAC in un file solo, anche con le pause). " +
          "I memo restano nella galleria; il riascolto passa dalla riproduzione di sistema.",
      )
    }
    item(key = "qualita") {
      FluidListGroup(glass = true, modifier = Modifier.testTag(F8p13Tags.Quality)) {
        FluidRecordingQualityRow(selected = quality, onSelected = { qualityId = it.id })
      }
    }
    if (microphone.status == FluidPermissionStatus.Denied) {
      item(key = "negato") { Caption("Il microfono è negato: si concede dalle impostazioni dell'app.") }
    }
    item(key = "elenco") {
      Column {
        FluidSectionHeader(title = "Tutte le registrazioni", detail = if (memos.isEmpty()) "Ancora nessuna" else "${memos.size}")
        if (memos.isNotEmpty()) {
          FluidListGroup(modifier = Modifier.testTag(F8p13Tags.Memos)) {
            memos.forEachIndexed { index, memo ->
              if (index > 0) FluidListDivider()
              MemoRow(memo, expanded = open == memo.name, player = player, onToggle = {
                open = if (open == memo.name) null else memo.name
              })
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MemoRow(memo: Memo, expanded: Boolean, player: FluidMediaSession?, onToggle: () -> Unit) {
  val date = remember(memo) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(memo.file.lastModified())) }
  Column(Modifier.testTag(F8p13Tags.memo(memo.name))) {
    FluidListRow(
      title = memo.title,
      subtitle = date,
      meta = FluidScrubbing.clock(memo.durationMillis),
      onClick = onToggle,
      disclosure = false,
    )
    if (expanded) {
      val controller = player as? FluidPlaybackController
      // `item` e' uno stato del controllo: la riga si rifa' quando cambia quel che suona. Con `isCurrent` (che legge il
      // lettore e non uno stato) il tasto restava «Riproduci» mentre il memo suonava, e il tocco lo faceva ripartire
      // (visto sul Tab S9 il 25/11/2026).
      val current = controller?.item?.id == memo.file.name || (controller == null && player != null)
      Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        FluidAudioWaveform(
          envelope = memo.envelope,
          position = { if (current) player?.positionMillis ?: 0L else 0L },
          onSeek = { target -> if (current) player?.seekTo(target) },
          durationMillis = memo.durationMillis,
          enabled = current,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            FluidScrubbing.clock(if (current) player?.positionMillis ?: 0L else 0L),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          PlayPause(if (current) player else player?.let { idle(it) }, onPlay = {
            if (controller != null && !current) {
              controller.setItems(
                listOf(FluidPlaybackItem(memo.file.name, Uri.fromFile(memo.file), memo.title, "Memo vocali · Fluid Glass", durationMillis = memo.durationMillis)),
                play = true,
              )
            } else {
              player?.play()
            }
          })
          Text(
            "−" + FluidScrubbing.clock(memo.durationMillis - (if (current) player?.positionMillis ?: 0L else 0L)),
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
          )
        }
      }
    }
  }
}

/** Un lettore visto «fermo»: per il tasto di un memo che non e' quello in ascolto. */
private fun idle(player: FluidMediaSession): FluidMediaSession = object : FluidMediaSession by player {
  override val playing: Boolean get() = false
}

// --- L'onda da scorrere --------------------------------------------------------------------------------------------

@Composable
private fun WaveScene(onBack: () -> Unit) {
  val context = LocalContext.current
  var ground by rememberSaveable { mutableStateOf(F8p13Ground.Page) }
  val player = rememberLecturePlayer()
  val stored = remember { lectureEnvelope(context) }
  // L'onda dal file: decodificata dall'audio sul telefono (la via di un file qualunque); senza decodificatori (le prove)
  // resta quella salvata accanto.
  var envelope by remember { mutableStateOf(stored) }
  LaunchedEffect(Unit) {
    val uri = lectureItem(context).uri
    FluidAudioEnvelopes.decode(context, uri)?.takeIf { it.size > 0 }?.let { envelope = it }
  }
  val timeline = rememberFluidAudioTimelineState()
  FluidScreen(title = "L'onda da scorrere", subtitle = "F8.13 · onda-audio", onBack = onBack) {
    item(key = "fondo") { GroundPicker(ground) { ground = it } }
    item(key = "bolla") {
      Column {
        FluidSectionHeader(title = "Il messaggio audio", detail = "Scorri sulle barre per andare avanti e indietro")
        Stage(ground) {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PlayPause(player, size = 40.dp)
            FluidAudioWaveform(
              envelope = envelope,
              position = { player?.positionMillis ?: 0L },
              onSeek = { player?.seekTo(it) },
              durationMillis = LectureDurationMillis,
              enabled = player != null,
              modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag(F8p13Tags.Bubble),
            )
            Text(
              FluidScrubbing.clock(player?.positionMillis ?: 0L),
              style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.width(40.dp),
            )
          }
        }
      }
    }
    item(key = "onda") {
      Column {
        FluidSectionHeader(title = "L'onda di Memo Vocali", detail = "Trascina, lancia, pizzica per lo zoom")
        Stage(ground) {
          FluidAudioTimeline(
            envelope = envelope,
            position = { player?.positionMillis ?: 0L },
            onSeek = { player?.seekTo(it) },
            durationMillis = { LectureDurationMillis },
            state = timeline,
            enabled = player != null,
            modifier = Modifier.testTag(F8p13Tags.Timeline),
          )
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PlayPause(player)
          }
          Caption("Zoom: ${timeline.dpPerSecond.toInt()} dp al secondo (Memo Vocali: 100). La testina resta ferma al centro.")
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Tutto ciò che muove il dito resta nitido: niente sfocatura nel trascinamento, nel lancio e quando l'onda scorre da sola.",
      )
    }
  }
}

// --- La riproduzione di sistema -------------------------------------------------------------------------------------

private enum class SleepChoice(val label: String, val minutes: Int, val endOfItem: Boolean = false) {
  Off("Spento", 0), One("1 min", 1), Fifteen("15 min", 15), Thirty("30 min", 30), End("Fine lezione", 0, endOfItem = true),
}

private val Speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun PlaybackScene(onBack: () -> Unit) {
  val player = rememberLecturePlayer()
  val controller = player as? FluidPlaybackController
  FluidScreen(title = "La lezione in sottofondo", subtitle = "F8.13 · riproduzione-sistema", onBack = onBack) {
    item(key = "lettore") {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FluidSectionHeader(title = controller?.item?.title ?: "Il ciclo dell'acqua", detail = controller?.item?.subtitle ?: "Lezione di scienze")
        if (player == null) {
          Caption("Collegamento alla riproduzione di sistema…")
          return@Column
        }
        FluidScrubber(
          position = { player.positionMillis.toFloat() },
          onSeek = { player.seekTo(it.toLong()) },
          valueRange = 0f..(player.durationMillis ?: LectureDurationMillis).toFloat(),
          enabled = player.canSeek,
          startLabel = FluidScrubbing.Elapsed,
          endLabel = FluidScrubbing.Remaining,
          keyStep = 15_000f,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
          SkipText("−15") { controller?.skipBy(-15_000L) ?: player.seekTo((player.positionMillis - 15_000L).coerceAtLeast(0L)) }
          PlayPause(player, size = 62.dp)
          SkipText("+15") { controller?.skipBy(15_000L) ?: player.seekTo(player.positionMillis + 15_000L) }
        }
      }
    }
    if (controller != null) {
      item(key = "velocita") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          FluidSectionHeader(title = "Velocità")
          FluidSegmentedControl(
            options = Speeds,
            selected = Speeds.minByOrNull { kotlin.math.abs(it - controller.playbackSpeed) } ?: 1f,
            onSelect = { controller.changePlaybackSpeed(it) },
            modifier = Modifier.fillMaxWidth().testTag(F8p13Tags.Speed),
            label = { java.text.NumberFormat.getNumberInstance().format(it) + "×" },
          )
        }
      }
      item(key = "silenzi") {
        FluidListGroup {
          FluidListRow(
            title = "Salta i silenzi",
            subtitle = "Le pause lunghe passano da sole, come in Memo Vocali",
            disclosure = false,
            badge = {
              FluidSwitch(
                checked = controller.skipSilence,
                onCheckedChange = { controller.changeSkipSilence(it) },
                modifier = Modifier.testTag(F8p13Tags.SkipSilence),
              )
            },
          )
        }
      }
      item(key = "timer") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          val at = controller.sleepAtMillis
          FluidSectionHeader(
            title = "Timer di spegnimento",
            detail = when {
              controller.sleepAtEndOfItem -> "Si ferma a fine lezione"
              at != null -> "Si ferma alle " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(at))
              else -> "Spento"
            },
          )
          val selected = when {
            controller.sleepAtEndOfItem -> SleepChoice.End
            at == null -> SleepChoice.Off
            else -> SleepChoice.entries.firstOrNull { it.minutes > 0 && kotlin.math.abs(at - System.currentTimeMillis() - it.minutes * 60_000L) < 60_000L } ?: SleepChoice.One
          }
          FluidSegmentedControl(
            options = SleepChoice.entries,
            selected = selected,
            onSelect = { choice -> if (choice.endOfItem) controller.sleepAtEndOfItem() else controller.setSleepTimer(choice.minutes) },
            modifier = Modifier.fillMaxWidth().testTag(F8p13Tags.Sleep),
            label = { it.label },
          )
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        "Blocca il telefono o abbassa la tendina: i comandi sono quelli di Android (Media3), con meno e più 15 secondi. " +
          "Una chiamata, un'altra app che suona o le cuffie che si staccano mettono in pausa. Chiudendo l'app la lezione " +
          "continua; riaprendola riparte dal punto giusto, ferma.",
      )
    }
  }
}

@Composable
private fun SkipText(label: String, onClick: () -> Unit) {
  FluidGlassIconButton(onClick = onClick, size = 48.dp, modifier = Modifier.semantics { contentDescription = if (label.startsWith("−")) "Indietro di 15 secondi" else "Avanti di 15 secondi" }) {
    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
  }
}

// --- L'ascolto dal paragrafo ---------------------------------------------------------------------------------------

private enum class Timing(val label: String) { Words("Per parola"), Paragraphs("Solo paragrafi") }

@Composable
private fun TranscriptScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val player = rememberLecturePlayer()
  var timing by rememberSaveable { mutableStateOf(Timing.Words) }
  val transcript = remember(timing) { lectureTranscript(context, words = timing == Timing.Words) }
  val state = rememberFluidTranscriptState()
  FluidScreen(title = "L'ascolto dal paragrafo", subtitle = "F8.13 · ascolto-paragrafo", onBack = onBack) {
    item(key = "lettore") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          PlayPause(player)
          Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Il ciclo dell'acqua", style = MaterialTheme.typography.titleMedium)
            Text(
              FluidScrubbing.clock(player?.positionMillis ?: 0L) + " / " + FluidScrubbing.clock(LectureDurationMillis),
              style = FluidTextStyles.numeric.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        FluidSegmentedControl(
          options = Timing.entries,
          selected = timing,
          onSelect = { timing = it },
          modifier = Modifier.fillMaxWidth().testTag(F8p13Tags.Timing),
          label = { it.label },
        )
        Caption(
          if (timing == Timing.Words) {
            "Tocca una parola: si sente da lì. Tocca l'ora: dall'inizio del paragrafo."
          } else {
            "Senza i tempi per parola il tocco va all'inizio del paragrafo, e la parola non si accende."
          },
        )
      }
    }
    item(key = "testo") {
      Box(
        Modifier
          .fillMaxWidth()
          .height(520.dp)
          .clip(ContinuousCornerShape(FluidRadius.Group)),
      ) {
        FluidTranscriptView(
          transcript = transcript,
          position = { player?.positionMillis ?: 0L },
          // Come Podcast: il tocco porta la voce li'; se era in pausa resta in pausa («tocca Play se compare»).
          onSeek = { target -> player?.seekTo(target) },
          playing = player?.playing == true,
          state = state,
          modifier = Modifier.fillMaxWidth().height(520.dp).testTag(F8p13Tags.Transcript),
        )
      }
    }
  }
}
