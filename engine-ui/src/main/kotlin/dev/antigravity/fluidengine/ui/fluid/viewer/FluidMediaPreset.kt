package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import dev.antigravity.fluidengine.ui.fluid.icons.MusicNote
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
    val art = content.artwork
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
        Column(
          Modifier.matchParentSize()
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.navigationBarsIgnoringVisibility))
            .padding(horizontal = FluidScreenDefaults.HorizontalPadding).padding(bottom = bottom),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(Modifier.weight(1f).fillMaxWidth().glassBackdropSource(backdrop), contentAlignment = Alignment.Center) {
            if (art != null) art() else FluidIcon(FluidIcons.MusicNote, contentDescription = null,
              modifier = Modifier.size(FluidGlassSize.Large.touchTarget), tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          // Reserve the transport's space so artwork or a waveform does not resize on a tap.
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
              Modifier.fillMaxWidth().layout { measurable, constraints ->
                val controls = measurable.measure(constraints)
                layout(controls.width, controls.height) {
                  if (chromeVisible) controls.placeRelative(0, 0)
                }
              },
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(FluidScreenDefaults.HorizontalPadding),
                verticalAlignment = Alignment.CenterVertically) {
                if (canSeek) SkipButton(strings.skipBack, false, backdrop, chromeVisible) { session.skipBy(-MediaSkipMillis) }
                PlaybackButton(session, chromeVisible && error == null, backdrop, strings)
                if (canSeek) SkipButton(strings.skipForward, true, backdrop, chromeVisible) { session.skipBy(MediaSkipMillis) }
              }
              PlaybackTimeline(session, duration, chromeVisible && canSeek, backdrop, video = false, strings = strings)
            }
          }
        }
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
private fun SkipButton(label: String, forward: Boolean, backdrop: GlassBackdropState, enabled: Boolean, click: () -> Unit) {
  FluidGlassIconButton(click, backdrop = backdrop, enabled = enabled) {
    Text(if (forward) "+10" else "−10", Modifier.clearAndSetSemantics { contentDescription = label },
      style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
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
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().padding(FluidScreenDefaults.HorizontalPadding)
          .then(surface).padding(horizontal = FluidScreenDefaults.HorizontalPadding),
        valueRange = 0f..(duration?.toFloat() ?: 0f),
        enabled = enabled,
        startLabel = FluidScrubbing.Elapsed,
        endLabel = { _, _ -> total ?: "—" },
        contentDescription = strings.position,
        valueDescription = { shown, _ -> strings.time(
          FluidScrubbing.clock(if (duration == null) session.positionMillis else shown.toLong()),
          total ?: strings.unknownDuration,
        ) },
        keyStep = MediaSkipMillis.toFloat(),
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
