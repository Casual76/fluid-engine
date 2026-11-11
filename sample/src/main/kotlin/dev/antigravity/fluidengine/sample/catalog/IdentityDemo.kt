@file:OptIn(ExperimentalLayoutApi::class)

package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAvatar
import dev.antigravity.fluidengine.ui.fluid.FluidAvatarItem
import dev.antigravity.fluidengine.ui.fluid.FluidAvatarStack
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarActionDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidIdentityColors
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarRow
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarSection
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Folder
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.FormatUnderlined
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * Avatar, colori d'identita', badge, stato di selezione, barra laterale annidata, azione della barra con
 * avatar e capsula di formattazione: i pezzi di «identita' e selezione» (F1.2 parte B), tutti insieme.
 *
 * Sta nella galleria come pagina «Identità e selezione» (con la matrice degli stati). Ogni sezione mostra gli stati che contano:
 * senza immagine, con un'immagine che disegna l'app, selezionato, molti avatar (mosaico), badge a
 * 0/1/12/99/100+, e tre sezioni che **rifanno** le stesse cose col testo al 200%, in scuro e con il
 * movimento ridotto, perche' sono i tre modi in cui un cerchio da 40 dp si rompe.
 *
 * L'azione della barra con l'avatar apre il suo menu dentro un `FluidGlassModalHost`: la galleria lo
 * monta, un'anteprima isolata no (senza host il tasto resta un tasto e il menu non si apre).
 */
@Composable
fun IdentityDemo() {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("identity-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    AvatarSection()
    StackSection()
    PaletteSection()
    BadgeSection()
    SelectionSection()
    SidebarSection()
    BarActionSection()
    FormattingSection()
    VariantsSection()
  }
}

// --- Il palco (stesso schema delle altre demo di vetro) ----------------------------------------------

@Composable
private fun Stage(
  height: androidx.compose.ui.unit.Dp,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.(GlassBackdropState) -> Unit,
) {
  val backdrop = rememberGlassBackdrop()
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(ContinuousCornerShape(FluidRadius.Group)),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(backdrop)
        .drawBehind {
          drawRect(
            Brush.linearGradient(
              colors = listOf(Color(0xFF3A7BD5), Color(0xFFE96443), Color(0xFFF5C451), Color(0xFF2E8B57)),
              start = Offset.Zero,
              end = Offset(900f, 700f),
            ),
          )
        },
    ) {
      Column(Modifier.padding(12.dp)) {
        repeat(8) { i ->
          Text(
            text = "Testo nitido dietro il vetro, riga ${i + 1} — ABCDEFG 0123456789",
            style = MaterialTheme.typography.bodyMedium,
            color = if (i % 2 == 0) Color.Black else Color.White,
          )
        }
      }
    }
    content(backdrop)
  }
}

/** Una «foto» dell'app: un disegno, non un file. L'engine non carica niente dalla rete. */
@Composable
private fun FakePhoto(tint: Color) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Brush.linearGradient(listOf(tint, Color(0xFF1B1B2F)))),
  )
}

// --- Avatar --------------------------------------------------------------------------------------

@Composable
private fun AvatarSection() {
  var selected by remember { mutableStateOf<Boolean?>(null) }
  FluidSectionHeader(
    title = "Avatar",
    detail = "Monogramma a colore stabile, immagine dell'app, segno di selezione e angolo.",
  )
  FlowRow(
    modifier = Modifier.fillMaxWidth().testTag("identity-avatars"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Senza immagine: nomi che mettono alla prova le iniziali.
    listOf(
      "Anna Rossi",
      "Émile Bernard",
      "A",
      "🙂 Famiglia",
      "محمد علي",
      "田中太郎",
      "anna.rossi@example.com",
      "",
    ).forEach { name -> FluidAvatar(name = name) }
    // Con un'immagine: sta sopra il monogramma, che resta sotto finche' non arriva.
    FluidAvatar(name = "Foto Utente", image = { FakePhoto(Color(0xFF3A7BD5)) })
    // Con un angolo: il colore di un account, un badge con un numero.
    FluidAvatar(
      name = "Con angolo",
      corner = { FluidBadge(count = 3, contentDescription = null) },
      contentDescription = "Con angolo, 3 novita'",
    )
    FluidAvatar(
      name = "Account",
      corner = { Box(Modifier.size(12.dp).clip(ContinuousCornerShape(FluidRadius.Group)).background(Color(0xFF2E8B57))) },
    )
    // Colore deciso dall'app, anche di mezzo tono: il monogramma resta leggibile.
    FluidAvatar(name = "Grigio", color = Color(0xFF777777))
    // Selezionabile: il tocco entra in selezione e lo alterna.
    FluidAvatar(
      name = "Tocca",
      selected = selected,
      onClick = { selected = if (selected == null) true else !(selected ?: false) },
    )
    FluidAvatar(name = "Scelto", selected = true)
    FluidAvatar(name = "Non scelto", selected = false)
    // Taglie.
    FluidAvatar(name = "Piccolo", size = 28.dp)
    FluidAvatar(name = "Grande", size = 64.dp)
  }
  FluidButton(
    text = if (selected == null) "Entra in selezione" else "Esci dalla selezione",
    onClick = { selected = if (selected == null) false else null },
    style = FluidButtonStyle.Tinted,
  )
}

@Composable
private fun StackSection() {
  FluidSectionHeader(
    title = "Mosaico",
    detail = "Piu' persone in un cerchio: 2, 3, 4 e oltre il tetto («+3»).",
  )
  val people = listOf("Anna Rossi", "Bruno Neri", "Carla Verdi", "Dario Blu", "Elena Gialli", "Fabio Viola", "Gina Rosa")
  Row(
    modifier = Modifier.fillMaxWidth().testTag("identity-stacks"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidAvatarStack(items = people.take(2).map { FluidAvatarItem(it) })
    FluidAvatarStack(items = people.take(3).map { FluidAvatarItem(it) })
    FluidAvatarStack(items = people.take(4).map { FluidAvatarItem(it) })
    FluidAvatarStack(items = people.map { FluidAvatarItem(it) }, contentDescription = "Anna, Bruno, Carla e altri 4")
    FluidAvatarStack(
      items = listOf(
        FluidAvatarItem("Con foto", image = { FakePhoto(Color(0xFFE96443)) }),
        FluidAvatarItem("Anna Rossi"),
      ),
      size = 56.dp,
    )
  }
}

@Composable
private fun PaletteSection() {
  FluidSectionHeader(
    title = "Palette d'identita'",
    detail = "Dodici tinte, stabili: la stessa chiave ha sempre lo stesso colore. Chiave e colore sotto.",
  )
  FlowRow(
    modifier = Modifier.fillMaxWidth().testTag("identity-palette"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FluidIdentityColors.palette.indices.forEach { index ->
      // Una chiave che cade proprio su ogni indice: cerchiamo la prima che lo fa, cosi' la riga e' stabile.
      val key = remember(index) { (0 until 10_000).first { FluidIdentityColors.indexFor("k$it") == index }.let { "k$it" } }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FluidAvatar(name = key, key = key, size = 36.dp)
        Text(text = "$index", style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

// --- Badge ------------------------------------------------------------------------------------------

@Composable
private fun BadgeSection() {
  FluidSectionHeader(
    title = "Badge",
    detail = "Pallino, 0 (niente), 1, 12, 99, 100+; muto o con la frase per TalkBack; sopra il vetro.",
  )
  Row(
    modifier = Modifier.fillMaxWidth().testTag("identity-badges"),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidBadge()
    Box(Modifier.size(16.dp).testTag("identity-badge-zero")) { FluidBadge(count = 0) }
    FluidBadge(count = 1)
    FluidBadge(count = 12)
    FluidBadge(count = 99)
    FluidBadge(count = 100, contentDescription = "100 non lette")
    FluidBadge(count = 12_345)
    FluidBadge(count = 5, color = Color(0xFFE53935))
    FluidBadge(count = 7, color = Color(0xFFFFEB3B))
  }
  Stage(height = 96.dp) { _ ->
    Row(
      modifier = Modifier.align(Alignment.Center),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidBadge()
      FluidBadge(count = 3)
      FluidBadge(count = 99)
      FluidBadge(count = 100)
    }
  }
}

// --- Selezione --------------------------------------------------------------------------------------

private const val TotalRows = 50_000

@Composable
private fun SelectionSection() {
  val state: FluidSelectionState<Int> = rememberFluidSelectionState()
  val loaded = remember { (1..6).toList() }
  FluidSectionHeader(
    title = "Selezione",
    detail = "«Seleziona tutto» sceglie l'intero risultato (50.000 righe) senza elencare le chiavi.",
  )
  Text(
    text = if (state.isActive) "${state.countIn(TotalRows)} selezionate" else "Nessuna selezione",
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.testTag("identity-selection-count"),
  )
  FluidListGroup {
    loaded.forEach { id ->
      FluidListRow(
        title = "Conversazione $id",
        subtitle = "Una riga con l'avatar al posto della piastrella",
        leadingTile = false,
        disclosure = false,
        selected = state.isSelected(id),
        onClick = { if (state.isActive) state.toggle(id) },
        leading = {
          FluidAvatar(
            name = "Persona $id",
            selected = if (state.isActive) state.isSelected(id) else null,
            onClick = { state.toggle(id) },
          )
        },
      )
    }
  }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidButton(
      text = "Seleziona tutto",
      onClick = { state.selectAll(snapshot = "demo@50000") },
      style = FluidButtonStyle.Tinted,
    )
    FluidButton(text = "Fine", onClick = { state.clear() }, style = FluidButtonStyle.Tinted)
  }
}

// --- Barra laterale ----------------------------------------------------------------------------------

@Composable
private fun SidebarSection() {
  var current by remember { mutableStateOf("lavoro") }
  FluidSectionHeader(
    title = "Barra laterale",
    detail = "Righe annidate con rientro, contenuto iniziale (avatar), intestazione con avatar, 48 dp.",
  )
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("identity-sidebar")
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .padding(8.dp),
  ) {
    FluidSidebarSection(
      title = "anna.rossi@example.com",
      uppercase = false,
      leading = { FluidAvatar(name = "Anna Rossi", size = 24.dp) },
      detail = "12",
    )
    FluidSidebarRow(
      label = "Posta in arrivo",
      selected = current == "inbox",
      onClick = { current = "inbox" },
      icon = FluidIcons.Inbox,
      minHeight = FluidSidebarDefaults.ComfortableRowHeight,
      trailing = { FluidBadge(count = 12) },
    )
    FluidSidebarRow(
      label = "Lavoro",
      selected = current == "lavoro",
      onClick = { current = "lavoro" },
      icon = FluidIcons.Folder,
      minHeight = FluidSidebarDefaults.ComfortableRowHeight,
    )
    FluidSidebarRow(
      label = "Clienti",
      selected = current == "clienti",
      onClick = { current = "clienti" },
      icon = FluidIcons.Folder,
      level = 1,
      minHeight = FluidSidebarDefaults.ComfortableRowHeight,
    )
    FluidSidebarRow(
      label = "Contratti 2026",
      selected = current == "contratti",
      onClick = { current = "contratti" },
      level = 2,
      leading = { FluidBadge(color = Color(0xFF2E8B57)) },
      minHeight = FluidSidebarDefaults.ComfortableRowHeight,
    )
    FluidSidebarRow(
      label = "Una cartella dal nome molto lungo che con il testo grande va a capo o si accorcia",
      selected = false,
      onClick = {},
      level = 9,
      icon = FluidIcons.Folder,
    )
    FluidSidebarRow(
      label = "Impostazioni",
      selected = false,
      onClick = {},
      icon = FluidIcons.Settings,
    )
  }
}

// --- Azione della barra con un avatar --------------------------------------------------------------------

@Composable
private fun BarActionSection() {
  var picked by remember { mutableStateOf("niente") }
  FluidSectionHeader(
    title = "Azione della barra con avatar",
    detail = "Tenuto premuto (o toccato, qui) l'avatar diventa il menu degli account e il menu si richiude in lui.",
  )
  Stage(height = 140.dp) { _ ->
    Row(
      modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidBarAction(
        contentDescription = "Account: Anna Rossi",
        onClick = { picked = "toccato" },
        modifier = Modifier.testTag("identity-bar-avatar"),
        actions = {
          listOf(
            FluidContextAction(label = "Anna Rossi", selected = true, onClick = { picked = "Anna" }),
            FluidContextAction(label = "Bruno Neri", onClick = { picked = "Bruno" }),
            FluidContextAction(label = "Impostazioni", startsSection = true, onClick = { picked = "Impostazioni" }),
          )
        },
        opensOn = FluidMenuOpensOn.Tap,
      ) {
        FluidAvatar(name = "Anna Rossi", size = FluidBarActionDefaults.ContentSize)
      }
      FluidBarAction(
        contentDescription = "Tutti gli account",
        onClick = { picked = "mosaico" },
      ) {
        FluidAvatarStack(
          items = listOf(FluidAvatarItem("Anna Rossi"), FluidAvatarItem("Bruno Neri"), FluidAvatarItem("Carla Verdi")),
          size = FluidBarActionDefaults.ContentSize,
        )
      }
    }
  }
  // Fuori dal palco: dentro, al 200% il testo di stato si sovrapponeva al fondale testuale e non si leggeva.
  // Sulla pagina ha il colore del tema, come ogni altro testo di stato della galleria.
  Text(
    text = "Ultima scelta: $picked",
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.testTag("identity-bar-picked"),
  )
}

// --- Capsula di formattazione ----------------------------------------------------------------------------

@Composable
private fun FormattingSection() {
  var bold by remember { mutableStateOf(true) }
  var italic by remember { mutableStateOf(false) }
  var underline by remember { mutableStateOf(false) }
  FluidSectionHeader(
    title = "Formattazione",
    detail = "Azioni che si accendono e si spengono: interruttori per TalkBack, disco pieno quando sono accese.",
  )
  Stage(height = 110.dp) { stage ->
    FluidFloatingCapsule(
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).testTag("identity-formatting"),
      backdrop = stage,
      label = "Formattazione",
    ) {
      FluidCapsuleAction(
        icon = FluidIcons.FormatBold,
        contentDescription = "Grassetto",
        onClick = { bold = !bold },
        selected = bold,
      )
      FluidCapsuleAction(
        icon = FluidIcons.FormatItalic,
        contentDescription = "Corsivo",
        onClick = { italic = !italic },
        selected = italic,
      )
      FluidCapsuleAction(
        icon = FluidIcons.FormatUnderlined,
        contentDescription = "Sottolineato",
        onClick = { underline = !underline },
        selected = underline,
      )
    }
  }
}

// --- Gli stessi pezzi: testo 200%, scuro, movimento ridotto -------------------------------------------------

@Composable
private fun VariantsSection() {
  FluidSectionHeader(
    title = "Varianti",
    detail = "Testo al 200% (il cerchio non cresce, il numero si), tema scuro, movimento ridotto.",
  )
  val density = LocalDensity.current
  Column(
    modifier = Modifier.fillMaxWidth().testTag("identity-variant-large-text"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Testo al 200%", style = MaterialTheme.typography.labelLarge)
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
      VariantRow()
    }
  }
  Column(
    modifier = Modifier.fillMaxWidth().testTag("identity-variant-dark"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Tema scuro", style = MaterialTheme.typography.labelLarge)
    FluidTheme(settings = EngineSettings(themeMode = ThemeMode.DARK), systemBars = false) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(ContinuousCornerShape(FluidRadius.Group))
          .background(MaterialTheme.colorScheme.surface)
          .padding(12.dp),
      ) { VariantRow() }
    }
  }
  Column(
    modifier = Modifier.fillMaxWidth().testTag("identity-variant-reduced-motion"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Movimento ridotto", style = MaterialTheme.typography.labelLarge)
    CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f)) {
      var selected by remember { mutableStateOf<Boolean?>(false) }
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FluidAvatar(
          name = "Tocca",
          selected = selected,
          onClick = { selected = !(selected ?: false) },
        )
        Text("Il cambio e' secco, senza dissolvenza.", style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}

@Composable
private fun VariantRow() {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidAvatar(name = "Anna Rossi")
    FluidAvatar(name = "Selezionato", selected = true)
    FluidAvatarStack(items = listOf(FluidAvatarItem("Anna Rossi"), FluidAvatarItem("Bruno Neri"), FluidAvatarItem("Carla Verdi")))
    FluidBadge(count = 99)
    FluidBadge(count = 100)
    FluidBadge()
    Box(Modifier.width(1.dp))
  }
}
