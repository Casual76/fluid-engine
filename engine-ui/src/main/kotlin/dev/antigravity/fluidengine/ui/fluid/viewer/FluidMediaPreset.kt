package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.Replay
import dev.antigravity.fluidengine.ui.fluid.icons.FormatListBulleted
import dev.antigravity.fluidengine.ui.fluid.icons.GraphicEq
import dev.antigravity.fluidengine.ui.fluidphysics.SizedMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.LocalFluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import androidx.compose.ui.text.font.FontWeight
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidBottomBarExtraInset
import kotlin.math.roundToLong

/** Labels for the viewer's single set of playback controls. The time arguments are already formatted. */
data class FluidMediaPresetStrings(
  val play: String = "Riproduci",
  val pause: String = "Pausa",
  val position: String = "Posizione di riproduzione",
  val skipBack: String = "Indietro di 10 secondi",
  val skipForward: String = "Avanti di 10 secondi",
  val unknownDuration: String = "Durata sconosciuta",
  val time: (elapsed: String, total: String) -> String = { elapsed, total -> "$elapsed di $total" },
  val skipSeconds: Int = 10,
  val chapters: String = "Capitoli",
  val currentChapter: (Int, Int) -> String = { index, count -> "Capitolo $index di $count" },
  val speed: String = "Velocità",
  val output: String = "Uscita audio",
)

/**
 * Video or audio controls for the [FluidFileViewer] renderer slot. Pass its `chromeVisible` unchanged so a tap
 * on the file remains the sole toggle. Play, pause and seek operate on the owner's session, which also controls
 * autoplay, errors, and release. The supplied video surface should have its own controls disabled.
 *
 * Set [sampleVideo] for video drawn into the Compose backdrop. External or protected surfaces use a black
 * backing for the controls, so the white labels remain legible independently of the frame underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluidMediaPreset(
  content: FluidViewerContent.Media,
  chromeVisible: Boolean,
  modifier: Modifier = Modifier,
  sampleVideo: Boolean = content.sampleVideo,
  strings: FluidMediaPresetStrings = FluidMediaPresetStrings(),
) {
  key(content.session) {
    val session = content.session
    val video = content.videoSurface
    val duration = session.durationMillis?.takeIf { it > 0L }
    val error = session.error
    val canSeek = session.canSeek && duration != null && error == null
    val backdrop = rememberGlassBackdrop()
    val bottom = fluidBottomBarExtraInset(LocalFluidBottomInset.current)
    val policy = LocalFluidMotionPolicy.current
    val active = currentFluidViewerPageActive()
    val page = LocalViewerPage.current
    val surfaceColor = if (video != null) Color.Black else MaterialTheme.colorScheme.surface
    Box(modifier.fillMaxSize().then(if (page == null) Modifier.background(surfaceColor) else Modifier)) {
      if (video != null) {
        if (!sampleVideo) Box(Modifier.matchParentSize().glassBackdropSource(backdrop)
          .viewerBackdrop(Color.Black, fullyArrivedOnly = true))
        Box(
          Modifier.matchParentSize().then(if (sampleVideo) Modifier.glassBackdropSource(backdrop) else Modifier),
          contentAlignment = Alignment.Center,
        ) {
          val ratio = (session as? FluidVideoGeometry)?.videoAspectRatio
          val shared = LocalVideoShared.current
          val surface = Modifier.viewerFit(ratio)
          Box(if (page != null && shared != null) surface.then(shared).fluidZoomLayer(page.zoom) else surface,
            contentAlignment = Alignment.Center) { video() }
        }
        // Route contacts on an AndroidView to the frame. The transport above has its own hit targets.
        if (LocalVideoShared.current != null) Box(Modifier.matchParentSize().pointerInput(Unit) {
          awaitPointerEventScope { while (true) awaitPointerEvent() }
        })
        dev.antigravity.fluidengine.ui.theme.BlackMediaAppearance(true) {
          val playbackNamespace = rememberFluidGlassNamespace()
          // Materialize is the existing R4 B3 preset. Removing this single keyed piece leaves its
          // recorded symbol with the plane for the 470 ms departure, without a second hit target.
          FluidGlassContainer(Modifier.align(Alignment.Center).size(FluidGlassSize.Large.touchTarget)) {
            if (chromeVisible) {
              PlaybackButton(session, error == null, backdrop, strings, playbackNamespace)
            }
          }
          AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
              .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility).padding(bottom = bottom),
            enter = fadeIn(tween(ViewerChromeMotion.duration(false, 1f, policy.durationScale))),
            exit = fadeOut(tween(ViewerChromeMotion.duration(true, 1f, policy.durationScale))),
          ) {
            val semantics = if (chromeVisible) Modifier else Modifier.clearAndSetSemantics { hideFromAccessibility() }
            PlaybackTimeline(session, duration, chromeVisible && canSeek, backdrop, video = true,
              strings = strings, modifier = semantics)
          }
        }
      } else {
        LaunchedEffect(session, active) {
          if (active && session.durationMillis == null) (session as? FluidMediaDurationLoader)?.requestDuration()
        }
        AudioPanel(content, duration, canSeek, chromeVisible, backdrop, bottom, strings)
      }
      if (error != null) {
        val colors = MaterialTheme.colorScheme
        Text(error, Modifier.align(Alignment.Center).padding(FluidScreenDefaults.HorizontalPadding)
          .background(colors.errorContainer, MaterialTheme.shapes.medium).padding(FluidScreenDefaults.HorizontalPadding)
          .semantics { liveRegion = LiveRegionMode.Polite }, color = colors.onErrorContainer)
      }
    }
  }
}

@Composable
private fun AudioPanel(content: FluidViewerContent.Media, duration: Long?, canSeek: Boolean, chromeVisible: Boolean,
  backdrop: GlassBackdropState, bottom: androidx.compose.ui.unit.Dp, strings: FluidMediaPresetStrings) {
  val session = content.session
  val info = LocalViewerInfo.current
  val colors = MaterialTheme.colorScheme
  val accent = viewerAccent(info, colors.primary)
  val shape = ContinuousCornerShape(ViewerMixProfile.CoverRadius)
  val playerShape = ContinuousCornerShape(ViewerMixProfile.PlayerRadius)
  val playerSurface = if (GlassDefaults.isDarkSurface()) ViewerMixProfile.PlayerDark else ViewerMixProfile.PlayerLight
  val playerBackdrop = rememberGlassBackdrop()
  val step = strings.skipSeconds.coerceAtLeast(1) * 1000L
  val chapters = info?.chapters.orEmpty()
  val parentMenu = LocalFluidMorphMenuState.current
  val menu = parentMenu ?: rememberFluidMorphMenuState()
  val more = LocalFluidLabels.current.more
  var actionFailed by remember(content.session) { mutableStateOf(false) }
  var showingChapters by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
  if (chapters.isNotEmpty()) {
    ViewerPresetToolbar {
      button("viewer.audio.chapters", FluidIcons.FormatListBulleted, strings.chapters, { showingChapters = true },
        placement = dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement.BottomBar)
    }
    FluidGlassSheet(showingChapters, { showingChapters = false }, paneTitle = strings.chapters,
      detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large)) {
      Column(Modifier.verticalScroll(rememberScrollState()).padding(FluidScreenDefaults.HorizontalPadding)) {
        FluidSectionHeader(strings.chapters)
        chapters.forEach { chapter ->
          FluidButton(FluidScrubbing.clock(chapter.startMillis) + " · " + chapter.title, {
            session.seekTo(chapter.startMillis.inMediaBounds(duration))
            showingChapters = false
          }, enabled = canSeek, style = FluidButtonStyle.Plain)
        }
      }
    }
  }
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.matchParentSize().glassBackdropSource(backdrop).viewerWash(accent, colors.secondary, colors.primary))
    val top = FluidScreenDefaults.topBarHeight()
    ViewerMixColumn(Modifier.fillMaxSize().padding(top = top,
      bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottom)) {
      val coverTop = viewerBoardTop(110.dp)
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = coverTop - top, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(ViewerMixProfile.Cover).viewerHalo(accent, ViewerMixProfile.CoverRadius)
          .clip(shape), contentAlignment = Alignment.Center) {
          val artwork = content.artwork
          val thumb = info?.thumbnail
          when {
            artwork != null -> artwork()
            thumb != null -> Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> {
              Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(accent, colors.tertiaryContainer)))
                val ink = colors.onPrimary
                for (n in 0 until 27) {
                  val x = size.width * (.12f + n * .76f / 26)
                  val wave = kotlin.math.abs(kotlin.math.sin(n * .6f) * kotlin.math.cos(n * .14f))
                  val half = size.height * (.025f + wave * .17f)
                  drawLine(ink, Offset(x, center.y - half), Offset(x, center.y + half), size.width / 65,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
              }
            }
          }
        }
        info?.let { metadata ->
          Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp),
            verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
              Text(metadata.title, style = MaterialTheme.typography.headlineSmall.viewerSize(22f, FontWeight.Bold))
              val subtitle = metadata.subtitle ?: metadata.facts.take(2).joinToString(" · ")
              if (subtitle.isNotBlank()) Text(subtitle, Modifier.padding(top = 3.dp), color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge.viewerSize(16f))
            }
            if (metadata.actions.isNotEmpty()) {
              SizedMorphMenuButton(state = menu, actions = {
                metadata.actions.map { action -> FluidContextAction(label = action.label) {
                  actionFailed = false
                  fluidRunFileAction(action.onClick) { actionFailed = true }
                } }
              }, onClick = {}, opensOnTap = true, icon = FluidIcons.MoreHoriz, backdrop = backdrop,
                modifier = Modifier.size(34.dp).semantics { contentDescription = more }, text = null,
                enabled = true, lens = 34.dp, horizontalPadding = 7.dp)
            }
          }
          if (actionFailed) Text(FluidViewerStrings().actionFailed, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
        // Keep the cover in place when a tap hides the controls.
        Box(Modifier.fillMaxWidth().padding(top = if (info == null) 24.dp else 22.dp).layout { measurable, constraints ->
          val controls = measurable.measure(constraints)
          layout(controls.width, controls.height) { if (chromeVisible) controls.placeRelative(0, 0) }
        }.shadow(6.dp, playerShape, clip = false).clip(playerShape)) {
          // The play lens reads the solid panel without capturing its own foreground.
          Box(Modifier.matchParentSize().glassBackdropSource(playerBackdrop).background(playerSurface))
          Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            AudioWave(session, duration, canSeek, info?.audioWaveform, strings)
            val timeStyle = MaterialTheme.typography.labelMedium.viewerSize(12f, FontWeight.SemiBold)
              .copy(fontFeatureSettings = "tnum")
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(FluidScrubbing.clock(session.positionMillis.inMediaBounds(duration)), style = timeStyle)
              Text(info?.audioFormat.orEmpty(), Modifier.weight(1f).padding(horizontal = 6.dp),
                style = timeStyle.copy(fontWeight = FontWeight.Medium), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
              Text(duration?.let { "−" + FluidScrubbing.clock(it - session.positionMillis.inMediaBounds(it)) } ?: "—",
                style = timeStyle)
            }
            Row(Modifier.padding(top = 6.dp, bottom = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
              if (canSeek) SkipButton(strings.skipBack, -strings.skipSeconds) { session.skipBy(-step) }
              PlaybackButton(session, chromeVisible && session.error == null, playerBackdrop, strings, audioSize = 68.dp)
              if (canSeek) SkipButton(strings.skipForward, strings.skipSeconds) { session.skipBy(step) }
            }
            val speed = session as? FluidAudioSpeed
            val chapterIndex = chapters.indexOfLast { it.startMillis <= session.positionMillis }.coerceAtLeast(0)
            if (speed != null || chapters.isNotEmpty() || info?.audioOutput != null) {
              Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                  if (speed != null) {
                    val value = speed.playbackSpeed
                    val rates = listOf(1f, 1.25f, 1.5f, 2f, .75f)
                    val shown = java.text.NumberFormat.getNumberInstance().format(value) + "×"
                    Text(shown, Modifier.fluidPressable(role = Role.Button, enabled = speed.canChangePlaybackSpeed,
                      onClick = { speed.changePlaybackSpeed(rates[(rates.indexOf(value) + 1) % rates.size]) })
                      .semantics { contentDescription = "${strings.speed}, $shown" }.padding(4.dp), style = timeStyle)
                  }
                }
                if (chapters.isNotEmpty()) Row(Modifier.fluidPressable(onClick = { showingChapters = true }, role = Role.Button),
                  verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  FluidIcon(FluidIcons.FormatListBulleted, null, size = 15.dp)
                  Text(strings.currentChapter(chapterIndex + 1, chapters.size), style = timeStyle)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                  info?.audioOutput?.let { output ->
                    Box(Modifier.size(48.dp, 32.dp).fluidPressable(onClick = output, role = Role.Button),
                      contentAlignment = Alignment.CenterEnd) {
                      FluidIcon(FluidIcons.GraphicEq, strings.output, size = 21.dp)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    if (parentMenu == null && !info?.actions.isNullOrEmpty()) FluidMorphMenuHost(menu, backdrop)
  }
}

@Composable
private fun PlaybackButton(
  session: FluidMediaSession, enabled: Boolean, backdrop: GlassBackdropState, strings: FluidMediaPresetStrings,
  appearanceNamespace: FluidGlassNamespace? = null,
  audioSize: Dp = 68.dp,
) {
  val playing = session.playing
  val label = if (playing) strings.pause else strings.play
  val icon = if (playing) FluidIcons.Filled.Pause else FluidIcons.Filled.PlayArrow
  val click = { if (session.playing) session.pause() else session.play() }
  if (appearanceNamespace == null) {
    FluidGlassIconButton(click, backdrop = backdrop, enabled = enabled, size = audioSize) {
      FluidIcon(icon, label, size = 30.dp)
    }
    return
  }
  Box(
    Modifier.size(FluidGlassSize.Large.touchTarget).fluidGlassId("playback", appearanceNamespace)
      .fluidGlassTransition(FluidGlassTransition.Materialize)
      .fluidGlass(FluidGlass.Clear, backdrop = backdrop)
      .fluidPressable(onClick = click, enabled = enabled, role = Role.Button, focusShape = FluidCapsuleShape),
    contentAlignment = Alignment.Center,
  ) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    FluidIcon(icon, label, tint = tint)
  }
}

@Composable
private fun SkipButton(label: String, seconds: Int, click: () -> Unit) {
  Box(Modifier.size(48.dp).fluidPressable(onClick = click, role = Role.Button)
    .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
    FluidIcon(FluidIcons.Replay, null, Modifier.scale(if (seconds > 0) -1f else 1f, 1f), size = 32.dp)
    Text(kotlin.math.abs(seconds).toString(), Modifier.padding(top = 4.dp).clearAndSetSemantics {},
      style = MaterialTheme.typography.labelSmall.viewerSize(9f, FontWeight.Bold))
  }
}

@Composable
private fun AudioWave(session: FluidMediaSession, duration: Long?, enabled: Boolean,
  waveform: (@Composable () -> Unit)?, strings: FluidMediaPresetStrings) {
  val limit = duration ?: 0L
  val keyStep = strings.skipSeconds.coerceAtLeast(1) * 1000L
  val shown = session.positionMillis.inMediaBounds(duration)
  var dragPosition by remember(session) { mutableStateOf<Float?>(null) }
  val fraction = dragPosition ?: if (limit > 0L) shown.toFloat() / limit else 0f
  val seek: (Float) -> Unit = { value ->
    if (enabled && value.isFinite() && session.error == null && session.canSeek) {
      session.seekTo((limit * value.coerceIn(0f, 1f)).roundToLong())
    }
  }
  val latestSeek by rememberUpdatedState(seek)
  val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
  val input = if (enabled) Modifier.pointerInput(session, forward) {
    detectTapGestures { position ->
      val at = (position.x / size.width).coerceIn(0f, 1f)
      latestSeek(if (forward > 0) at else 1f - at)
    }
  }.pointerInput(session, forward) {
    detectHorizontalDragGestures(
      onDragEnd = { dragPosition?.let(latestSeek); dragPosition = null },
      onDragCancel = { dragPosition = null },
      onHorizontalDrag = { change, _ ->
        val at = (change.position.x / size.width).coerceIn(0f, 1f)
        dragPosition = if (forward > 0) at else 1f - at
      },
    )
  }.onKeyEvent { event ->
    val direction = when (event.key) {
      Key.DirectionRight -> forward
      Key.DirectionLeft -> -forward
      else -> 0
    }
    if (direction != 0 && event.type == KeyEventType.KeyDown) {
      session.skipBy(direction * keyStep)
      true
    } else false
  }.focusable() else Modifier
  val ink = MaterialTheme.colorScheme.onSurface
  Box(Modifier.widthIn(max = 302.dp).fillMaxWidth().height(44.dp).then(input)
    .semantics {
      contentDescription = strings.position
      stateDescription = strings.time(FluidScrubbing.clock(shown), duration?.let(FluidScrubbing::clock) ?: strings.unknownDuration)
      progressBarRangeInfo = ProgressBarRangeInfo(fraction * limit, 0f..limit.coerceAtLeast(1L).toFloat())
      if (enabled) {
        setProgress { time -> time.isFinite().also { valid -> if (valid) seek(time / limit) } }
        customActions = listOf(
          CustomAccessibilityAction(strings.skipBack) { session.skipBy(-keyStep); true },
          CustomAccessibilityAction(strings.skipForward) { session.skipBy(keyStep); true },
        )
      } else disabled()
    }, contentAlignment = Alignment.Center) {
    if (waveform != null) waveform()
    else Canvas(Modifier.fillMaxSize()) {
      repeat(46) { index ->
        val pitch = size.width / 46
        val height = size.height * (.12f + .8f * kotlin.math.abs(kotlin.math.sin(index * .47f) *
          kotlin.math.cos(index * .16f)))
        val midpoint = (index + .5f) * pitch
        val x = if (forward > 0) midpoint else size.width - midpoint
        drawLine(ink.copy(alpha = if (index / 46f <= fraction) 1f else .32f),
          Offset(x, center.y - height / 2), Offset(x, center.y + height / 2), pitch * .55f,
          cap = androidx.compose.ui.graphics.StrokeCap.Round)
      }
    }
  }
}

@Composable
private fun PlaybackTimeline(
  session: FluidMediaSession, duration: Long?, enabled: Boolean, backdrop: GlassBackdropState,
  video: Boolean, strings: FluidMediaPresetStrings, modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val ink = if (video) Color.White else scheme.onSurface
  // The light ink also feeds FluidScrubber's static track and its two tabular labels.
  val colors = if (video) scheme.copy(onSurface = ink, onSurfaceVariant = ink,
    primary = ink, onPrimary = Color.Black) else scheme
  val surface = if (video) Modifier.fluidGlass(FluidGlass.Clear, backdrop = backdrop) else Modifier
  val total = duration?.let(FluidScrubbing::clock)
  CompositionLocalProvider(LocalContentColor provides ink, LocalClearScrubberThumb provides video) {
    MaterialTheme(colorScheme = colors) {
      FluidScrubber(
        position = { session.positionMillis.inMediaBounds(duration).toFloat() },
        onSeek = { target -> if (session.canSeek && session.error == null) {
          session.durationMillis?.takeIf { it > 0L }?.let { session.seekTo(target.roundToLong().inMediaBounds(it)) }
        } },
        modifier = if (video) modifier.widthIn(max = 560.dp).fillMaxWidth().padding(FluidScreenDefaults.HorizontalPadding)
          .then(surface).padding(horizontal = FluidScreenDefaults.HorizontalPadding)
        else modifier.fillMaxWidth().padding(horizontal = 8.dp),
        valueRange = 0f..(duration?.toFloat() ?: 0f),
        enabled = enabled,
        startLabel = FluidScrubbing.Elapsed,
        endLabel = { _, _ -> total ?: "—" },
        contentDescription = strings.position,
        valueDescription = { shown, _ -> strings.time(
          FluidScrubbing.clock(if (duration == null) session.positionMillis else shown.toLong()),
          total ?: strings.unknownDuration,
        ) },
        keyStep = strings.skipSeconds.coerceAtLeast(1) * 1000f,
        stepBackLabel = strings.skipBack,
        stepForwardLabel = strings.skipForward,
        backdrop = backdrop,
      )
    }
  }
}

internal const val MediaSkipMillis = 10_000L

internal fun Long.inMediaBounds(duration: Long?): Long = coerceIn(0L, duration?.coerceAtLeast(0L) ?: Long.MAX_VALUE)

internal fun FluidMediaSession.skipBy(delta: Long) {
  val end = durationMillis?.takeIf { it > 0L } ?: return
  if (!canSeek || error != null) return
  val at = positionMillis.inMediaBounds(end)
  seekTo(at + delta.coerceIn(-at, end - at))
}
