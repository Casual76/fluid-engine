package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
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
  val shape = ContinuousCornerShape(FluidRadius.Card)
  val step = strings.skipSeconds.coerceAtLeast(1) * 1000L
  val chapters = info?.chapters.orEmpty()
  val parentMenu = LocalFluidMorphMenuState.current
  val menu = parentMenu ?: rememberFluidMorphMenuState()
  val more = LocalFluidLabels.current.more
  var actionFailed by remember(content.session) { mutableStateOf(false) }
  var showingChapters by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
  if (chapters.isNotEmpty()) {
    ViewerPresetToolbar {
      text("viewer.audio.chapters", strings.chapters,
        { showingChapters = true }, placement = dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement.BottomBar)
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
    ViewerMixColumn(Modifier.fillMaxSize().padding(top = FluidScreenDefaults.topBarHeight(),
      bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottom)) { width ->
      val breath = ViewerMixProfile.verticalBreath(width)
      val coverSize = minOf(width * ViewerMixProfile.CoverFraction, ViewerMixProfile.CoverLimit)
      // A scroll container clips its own drawing. Include the whole upper feather in its measured content.
      val headroom = maxOf(12.dp + breath, ViewerMixProfile.haloSpread(coverSize) - ViewerMixProfile.HaloDrop)
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = headroom, bottom = 12.dp + breath),
        verticalArrangement = Arrangement.spacedBy(12.dp + breath / 2),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(coverSize).viewerHalo(accent, FluidRadius.Card)
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
          Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(metadata.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
              val subtitle = metadata.subtitle ?: metadata.facts.take(2).joinToString(" · ")
              if (subtitle.isNotBlank()) Text(subtitle, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            if (metadata.actions.isNotEmpty()) {
              FluidMorphMenuButton(state = menu, actions = {
                metadata.actions.map { action -> FluidContextAction(label = action.label) {
                  actionFailed = false
                  fluidRunFileAction(action.onClick) { actionFailed = true }
                } }
              }, onClick = {}, opensOnTap = true, icon = FluidIcons.MoreHoriz, backdrop = backdrop,
                modifier = Modifier.semantics { contentDescription = more })
            }
          }
          if (actionFailed) Text(FluidViewerStrings().actionFailed, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
        // Keep the cover in place when a tap hides the controls.
        Column(Modifier.fillMaxWidth().layout { measurable, constraints ->
          val controls = measurable.measure(constraints)
          layout(controls.width, controls.height) { if (chromeVisible) controls.placeRelative(0, 0) }
        }.viewerGlass(backdrop, ContinuousCornerShape(FluidRadius.Sheet)).padding(horizontal = 12.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          info?.audioWaveform?.invoke()
          PlaybackTimeline(session, duration, chromeVisible && canSeek, backdrop, video = false, strings = strings)
          Row(horizontalArrangement = Arrangement.spacedBy(FluidScreenDefaults.HorizontalPadding), verticalAlignment = Alignment.CenterVertically) {
            if (canSeek) SkipButton(strings.skipBack, -strings.skipSeconds, backdrop, chromeVisible) { session.skipBy(-step) }
            PlaybackButton(session, chromeVisible && session.error == null, backdrop, strings)
            if (canSeek) SkipButton(strings.skipForward, strings.skipSeconds, backdrop, chromeVisible) { session.skipBy(step) }
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
) {
  val playing = session.playing
  val label = if (playing) strings.pause else strings.play
  val icon = if (playing) FluidIcons.Filled.Pause else FluidIcons.Filled.PlayArrow
  val click = { if (session.playing) session.pause() else session.play() }
  if (appearanceNamespace == null) {
    FluidGlassIconButton(click, backdrop = backdrop, enabled = enabled, size = FluidGlassSize.Large) {
      FluidIcon(icon, label)
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
private fun SkipButton(label: String, seconds: Int, backdrop: GlassBackdropState, enabled: Boolean, click: () -> Unit) {
  val amount = if (seconds > 0) "+$seconds" else "−${-seconds}"
  val style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
  val measure = androidx.compose.ui.text.rememberTextMeasurer()
  val text = measure.measure(amount, style, softWrap = false).size
  val lens = with(androidx.compose.ui.platform.LocalDensity.current) {
    maxOf(LocalFluidGlassSize.current.lens, maxOf(text.width, text.height).toDp() + 10.dp)
  }
  FluidGlassIconButton(click, size = lens, backdrop = backdrop, enabled = enabled) {
    Text(amount, Modifier.clearAndSetSemantics { contentDescription = label }, style = style,
      maxLines = 1, softWrap = false)
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
