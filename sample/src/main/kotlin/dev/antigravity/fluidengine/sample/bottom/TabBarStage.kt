package dev.antigravity.fluidengine.sample.bottom

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidTabRole
import dev.antigravity.fluidengine.ui.fluid.FluidTabSearchActivation
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.CalendarMonth
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoLibrary
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop

/**
 * La tab bar di F4B su un palco largo come un telefono: tre schede nel gruppo, il tondo della ricerca, la scheda
 * prominente e il lettore.
 *
 * Si ripiega in due modi, perché sono due strade nel componente: col tasto («Piega barra», la piega comandata) e
 * scorrendo l'elenco del palco (la connessione di scorrimento, come nelle app). «Cerca» apre il campo nel tondo e la
 * pagina del palco sbianca con la curva della tab bar. Le stesse schede della sezione «Scheda prominente» di
 * «Pannello laterale e schede», che le sessioni S9 riprendono: qui stanno nel loro capitolo, accanto alle barre in
 * basso e alla ricerca.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TabBarStage() {
  var selected by rememberSaveable { mutableStateOf("Raccolta") }
  var query by rememberSaveable { mutableStateOf("") }
  var player by rememberSaveable { mutableStateOf(true) }
  val scroll = rememberFluidFloatingTabBarScrollConnection()
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      FluidButton(text = if (scroll.isInline) "Apri barra" else "Piega barra",
        onClick = { if (scroll.isInline) scroll.expand() else scroll.inline() })
      FluidButton(text = if (player) "Togli lettore" else "Mostra lettore", onClick = { player = !player })
    }
    val wash by animateFloatAsState(if (selected == "Cerca") 1f else 0f,
      if (LocalFluidMotionPolicy.current.reducedMotion) snap() else FluidMotion.FloatingTabSearchPage, label = "paginaRicerca")
    val surface = MaterialTheme.colorScheme.surface
    val backdrop = rememberGlassBackdrop()
    Box(
      Modifier.fillMaxWidth().height(320.dp).clip(ContinuousCornerShape(FluidRadius.Group))
        .consumeWindowInsets(WindowInsets.safeDrawing).testTag("bottom-tabbar-stage"),
    ) {
      Box(
        Modifier.fillMaxSize().glassBackdropSource(backdrop).drawBehind {
          drawRect(Brush.linearGradient(listOf(Color(0xFF3A7BD5), Color(0xFFE96443), Color(0xFFF5C451), Color(0xFF2E8B57)),
            Offset.Zero, Offset(900f, 900f)))
          drawRect(surface, alpha = wash)
        },
      ) {
        // L'elenco del palco porta la sua connessione: scorrendo verso il basso la barra si ripiega, risalendo si riapre.
        LazyColumn(
          Modifier.fillMaxSize().nestedScroll(scroll),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 140.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(30) { i ->
            Text("Foto ${i + 1} — scorri per ripiegare la barra", color = Color.White, maxLines = 1,
              overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
      FluidFloatingTabBar(
        selectedTabKey = selected, scrollConnection = scroll, backdrop = backdrop,
        modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 432.dp).padding(horizontal = 14.dp, vertical = 16.dp),
        inlineAccessory = if (player) { band, _ -> Box(band, contentAlignment = Alignment.Center) { Text("In ascolto") } } else null,
        expandedAccessory = if (player) { band, _ ->
          Box(band.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) { Text("In ascolto") }
        } else null,
        searchActivation = FluidTabSearchActivation.SearchTabSelection,
        searchBarContent = { field ->
          BasicTextField(value = query, onValueChange = { query = it }, modifier = field.padding(12.dp), singleLine = true,
            decorationBox = { input -> Box { if (query.isEmpty()) Text("Cerca nella raccolta"); input() } })
        },
      ) {
        tab("Oggi", { Text("Oggi") }, { FluidIcon(FluidIcons.CalendarMonth, null) }, { selected = "Oggi" })
        tab("Posta", { Text("Posta") }, { FluidIcon(FluidIcons.Filled.Inbox, null) }, { selected = "Posta" })
        tab("Raccolta", { Text("Raccolta") }, { FluidIcon(FluidIcons.Filled.PhotoLibrary, null) }, { selected = "Raccolta" })
        standaloneTab("Cerca", { FluidIcon(FluidIcons.Search, "Cerca") }, FluidTabRole.Search,
          onClick = { selected = "Cerca" }, title = { Text("Cerca") })
        standaloneTab("Preferiti", { FluidIcon(FluidIcons.Filled.Star, "Preferiti") }, FluidTabRole.Prominent,
          onClick = { selected = "Preferiti" })
      }
    }
    Text("Scheda attiva · $selected", style = MaterialTheme.typography.labelLarge)
  }
}
