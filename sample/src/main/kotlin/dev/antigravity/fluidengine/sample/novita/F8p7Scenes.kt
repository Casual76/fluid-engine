package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidColorRow
import dev.antigravity.fluidengine.ui.fluid.FluidColorSwatches
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidColorName
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Shuffle
import dev.antigravity.fluidengine.ui.fluid.icons.SkipNext
import dev.antigravity.fluidengine.ui.fluid.rememberFluidRecentColors
import dev.antigravity.fluidengine.ui.theme.FluidContentSide
import dev.antigravity.fluidengine.ui.theme.FluidContentTheme
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.LocalFluidContentRoles
import dev.antigravity.fluidengine.ui.theme.rememberFluidContentColors
import java.util.Locale

// F8.7 · Colore, nei casi delle app di Alessio (le schede `selettore-colore` e `colore-dal-contenuto`): i colori di un
// widget di Pampa Widgets col pozzetto e il foglio di vetro (anche il contagocce sulla foto), il colore di una materia di
// ClasseViva con la fila corta dei campioni, e la pagina del brano di Fluidify tinta dalla copertina come in Apple Music.

/** Le scene di F8.7, nell'ordine delle idee. */
internal val F8p7Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "colore-widget", "I colori di un widget",
    "Pampa Widgets · il pozzetto in una riga, il foglio di vetro coi tre modi, l'opacita', i recenti e il contagocce",
  ) { onBack -> WidgetColorsScene(onBack) },
  NewElementScene(
    "colore-materia", "Il colore di una materia",
    "ClasseViva · la fila corta dei campioni dentro una riga, col pozzetto in fondo per un colore su misura",
  ) { onBack -> SubjectColorScene(onBack) },
  NewElementScene(
    "pagina-brano", "La pagina del brano",
    "Fluidify · la pagina presa dalla copertina, come Apple Music: tinta, accento e vetro della barra",
  ) { onBack -> TrackPageScene(onBack) },
)

/** Le voci da provare a mano di F8.7, in coda a quelle di «Da provare». */
internal val F8p7TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-7-foglio", TryArea.Rows, "Il foglio del colore",
    "Apri «I colori di un widget» e tocca il pozzetto di «Sfondo»: giudica se il foglio di vetro nasce dal pozzetto e " +
      "ci torna, e se griglia, spettro e cursori seguono il dito senza sfocarsi.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › I colori di un widget"),
  ),
  TryItem(
    "f8-7-contagocce", TryArea.Rows, "Il contagocce",
    "Nello stesso foglio tocca il contagocce in alto a sinistra, porta la lente su un petalo della foto e solleva il " +
      "dito: giudica se il colore preso e' quello sotto il centro della lente.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › I colori di un widget"),
  ),
  TryItem(
    "f8-7-pagina", TryArea.Pages, "La pagina tinta",
    "Apri «La pagina del brano» e cambia copertina, poi spegni «Colore dalla copertina»: giudica se la tinta passa " +
      "sfumando e se il testo si legge su ogni copertina, in chiaro e in scuro.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La pagina del brano"),
  ),
)

/** I tag delle prove. */
internal object F8p7Tags {
  const val Ground = "f8p7:fondo"
  const val Widget = "f8p7:widget"
  const val Contrast = "f8p7:contrasto"
  const val Swatches = "f8p7:campioni"
  const val Hero = "f8p7:copertina"
  const val Tint = "f8p7:tinta"
  const val Roles = "f8p7:ruoli"
  const val MiniPlayer = "f8p7:mini-player"
  const val Side = "f8p7:lato"
  const val Play = "f8p7:riproduci"
  const val Shuffle = "f8p7:casuale"
  const val Add = "f8p7:aggiungi"

  fun cover(index: Int): String = "f8p7:copertina-$index"

  fun subject(index: Int): String = "f8p7:materia-$index"
}

/** Il fondo del widget. */
internal enum class F8p7Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

private val Italian = Locale.ITALY

private fun ratio(a: Color, b: Color): Float {
  val la = a.luminance()
  val lb = b.luminance()
  return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private fun ratioText(v: Float): String = String.format(Italian, "%.1f:1", v)

// --- I colori di un widget (Pampa Widgets) --------------------------------------------------------------------------

/**
 * Il widget musica di Pampa Widgets: lo sfondo (con l'opacita') e il colore del testo si scelgono coi pozzetti delle
 * righe; l'anteprima sta sul fondo scelto (la pagina o una foto), e sotto si legge il contrasto del testo. Il contagocce
 * prende un colore dalla foto.
 */
@Composable
private fun WidgetColorsScene(onBack: () -> Unit) {
  var ground by rememberSaveable { mutableStateOf(F8p7Ground.LightPhoto) }
  var background by rememberSaveable { mutableIntStateOf(Color(0xCC2C1A3E).toArgb()) }
  var ink by rememberSaveable { mutableIntStateOf(Color.White.toArgb()) }
  val recents = rememberFluidRecentColors(initial = listOf(Color(0xFF007AFF), Color(0xFFFF9500), Color(0xFF34C759)))
  FluidScreen(title = "Widget Musica", subtitle = "F8.7 · colore-widget", onBack = onBack) {
    item(key = "fondo") {
      FluidSegmentedControl(
        options = F8p7Ground.entries,
        selected = ground,
        onSelect = { ground = it },
        modifier = Modifier.fillMaxWidth().testTag(F8p7Tags.Ground),
        label = { it.label },
      )
    }
    item(key = "anteprima") { WidgetPreview(ground, Color(background), Color(ink)) }
    item(key = "colori") {
      Column {
        FluidSectionHeader(title = "Colori del widget")
        FluidListGroup {
          FluidColorRow(
            title = "Sfondo",
            color = Color(background),
            onColorChange = { background = it.toArgb() },
            subtitle = fluidColorName(Color(background)),
            recents = recents,
          )
          FluidListDivider()
          FluidColorRow(
            title = "Testo e icone",
            color = Color(ink),
            onColorChange = { ink = it.toArgb() },
            subtitle = fluidColorName(Color(ink)),
            supportsOpacity = false,
            recents = recents,
          )
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "Tocca una riga: il foglio nasce dal pozzetto. Il contagocce in alto a sinistra prende un colore dalla " +
          "pagina, foto compresa. I recenti sono gli stessi per le due righe.",
      )
    }
  }
}

/** L'anteprima del widget sul fondo scelto, e sotto il contrasto del testo sullo sfondo (composto sul fondo). */
@Composable
private fun WidgetPreview(ground: F8p7Ground, background: Color, ink: Color) {
  val shape = ContinuousCornerShape(FluidRadius.Group)
  // Il fondo «medio» della foto, per il contrasto: chiaro e scuro presi dalle due foto della galleria.
  val under = when (ground) {
    F8p7Ground.Page -> MaterialTheme.colorScheme.background
    F8p7Ground.LightPhoto -> Color(0xFF9C8E86)
    F8p7Ground.DarkPhoto -> Color(0xFF1E2230)
  }
  val contrast = ratio(ink, background.compositeOver(under))
  Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(Modifier.fillMaxWidth().height(220.dp).clip(shape), contentAlignment = Alignment.Center) {
      if (ground != F8p7Ground.Page) {
        Image(
          painter = painterResource(if (ground == F8p7Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize(),
        )
      }
      Row(
        Modifier
          .padding(horizontal = 28.dp)
          .fillMaxWidth()
          .height(132.dp)
          .clip(shape)
          .background(background)
          .padding(16.dp)
          .testTag(F8p7Tags.Widget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Image(
          painter = painterResource(R.drawable.viewer_campanelle_cover),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(96.dp).clip(ContinuousCornerShape(FluidRadius.Small)),
        )
        Column(Modifier.weight(1f)) {
          Text("In riproduzione", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f))
          Text("Sabbia e luna", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
          Text("Campanelle", style = MaterialTheme.typography.bodyMedium, color = ink.copy(alpha = 0.8f), maxLines = 1)
          Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FluidIcon(FluidIcons.PlayArrow, "Riproduci", tint = ink)
            FluidIcon(FluidIcons.SkipNext, "Brano successivo", tint = ink)
          }
        }
      }
    }
    Text(
      text = "Contrasto del testo ${ratioText(contrast)} · " + if (contrast >= 4.5f) "si legge" else "non si legge bene",
      style = MaterialTheme.typography.bodySmall,
      color = if (contrast >= 4.5f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag(F8p7Tags.Contrast),
    )
  }
}

// --- Il colore di una materia (ClasseViva) --------------------------------------------------------------------------

/** I colori di sistema di iOS, quelli che un'app offre per primi. */
private val SubjectPalette = listOf(
  Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7BE), Color(0xFF30B0C7),
  Color(0xFF007AFF), Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFF2D55), Color(0xFFA2845E),
)

private val Subjects = listOf("Matematica", "Italiano", "Storia", "Inglese", "Fisica")

/**
 * Le materie di ClasseViva, ognuna col suo colore: tocca una materia, e sotto la fila corta dei campioni (con il pozzetto
 * in fondo per un colore su misura) ne cambia il colore. E' la forma corta del selettore, dentro una riga.
 */
@Composable
private fun SubjectColorScene(onBack: () -> Unit) {
  val colors = remember {
    mutableStateListOf(Color(0xFF007AFF), Color(0xFFFF9500), Color(0xFFA2845E), Color(0xFFAF52DE), Color(0xFF34C759))
  }
  var editing by rememberSaveable { mutableIntStateOf(0) }
  val recents = rememberFluidRecentColors()
  FluidScreen(title = "Materie", subtitle = "F8.7 · colore-materia", onBack = onBack) {
    item(key = "materie") {
      FluidListGroup {
        Subjects.forEachIndexed { index, name ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = name,
            subtitle = fluidColorName(colors[index]),
            modifier = Modifier.testTag(F8p7Tags.subject(index)),
            leading = { Box(Modifier.size(24.dp).clip(CircleShape).background(colors[index])) },
            leadingTile = false,
            selected = index == editing,
            disclosure = false,
            onClick = { editing = index },
          )
        }
      }
    }
    item(key = "colore") {
      Column {
        FluidSectionHeader(title = "Colore di ${Subjects[editing]}")
        FluidListGroup {
          FluidColorSwatches(
            colors = SubjectPalette,
            selected = colors[editing],
            onSelect = { colors[editing] = it.copy(alpha = 1f) },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).testTag(F8p7Tags.Swatches),
            recents = recents,
          )
        }
      }
    }
    item(key = "nota") {
      FluidSectionFootnote(
        text = "La fila corta usa gli stessi pallini dell'accento dell'app; il pozzetto in fondo apre il foglio per un " +
          "colore che non c'e'. Ogni campione ha il suo nome per TalkBack.",
      )
    }
  }
}

// --- La pagina del brano (Fluidify, come Apple Music) ---------------------------------------------------------------

private class Cover(val res: Int, val title: String, val artist: String)

private val Covers = listOf(
  Cover(R.drawable.viewer_campanelle_cover, "Sabbia e luna", "Campanelle"),
  Cover(R.drawable.viewer_rocket, "Lancio di notte", "Le Torri"),
  Cover(R.drawable.viewer_flower, "Fiore di maggio", "Giardino"),
  Cover(R.drawable.viewer_astronaut, "Tuta arancione", "Orbita"),
)

private val Tracks = listOf("Alba", "Le dune", "Vento di sale", "Notte chiara", "Ritorno")

/** La copertina decodificata piccola, come fanno Fluidify e Pampa Widgets (96 px bastano all'estrattore). */
private fun decodeSmall(context: Context, res: Int): Bitmap? {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeResource(context.resources, res, bounds)
  var sample = 1
  while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
  return BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * La pagina del brano: la copertina in grande, il titolo, la fila dei tasti (casuale, Riproduci, aggiungi), i brani; la pagina e l'accento presi dalla
 * copertina con `FluidContentTheme`, il mini player in basso con il vetro della barra dei ruoli. Sotto la copertina le
 * altre copertine (la tinta passa dall'una all'altra) e l'interruttore che la spegne (la pagina torna al tema normale).
 */
@Composable
private fun TrackPageScene(onBack: () -> Unit) {
  var index by rememberSaveable { mutableIntStateOf(0) }
  var tinted by rememberSaveable { mutableStateOf(true) }
  var side by rememberSaveable { mutableStateOf(FluidContentSide.Content) }
  val cover = Covers[index]
  val context = LocalContext.current
  val colors by rememberFluidContentColors(key = cover.res) { decodeSmall(context, cover.res) }
  FluidContentTheme(colors = colors, enabled = tinted, side = side) {
    FluidScreen(
      title = "Album",
      subtitle = "F8.7 · pagina-brano",
      onBack = onBack,
      extraBottomPadding = 88.dp,
      modifier = Modifier.testTag(F8p7Tags.Tint),
      overlay = { backdrop ->
        val roles = LocalFluidContentRoles.current
        Row(
          Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(56.dp)
            .fluidGlassPanel(backdrop, roles?.barTint() ?: GlassDefaults.floatingTint(), FluidCapsuleShape, GlassRole.Floating)
            .padding(horizontal = 8.dp)
            .testTag(F8p7Tags.MiniPlayer),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val ink = roles?.onBar ?: MaterialTheme.colorScheme.onSurface
          FluidGlassContentTransition(targetState = index, blurReference = 40.dp) { i ->
            Image(
              painter = painterResource(Covers[i].res),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(40.dp).clip(ContinuousCornerShape(FluidRadius.Small)),
            )
          }
          FluidGlassContentTransition(
            targetState = index,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
          ) { i ->
            Column {
              Text(Covers[i].title, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(Covers[i].artist, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f), maxLines = 1)
            }
          }
          FluidIcon(FluidIcons.PlayArrow, "Riproduci", tint = ink, modifier = Modifier.padding(horizontal = 8.dp))
          FluidIcon(FluidIcons.SkipNext, "Brano successivo", tint = ink, modifier = Modifier.padding(end = 8.dp))
        }
      },
    ) {
      item(key = "copertina") {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          FluidGlassContentTransition(targetState = index, blurReference = 240.dp, modifier = Modifier.testTag(F8p7Tags.Hero)) { i ->
            Image(
              painter = painterResource(Covers[i].res),
              contentDescription = "Copertina di ${Covers[i].title}",
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(240.dp).clip(ContinuousCornerShape(FluidRadius.Card)),
            )
          }
          Spacer(Modifier.height(16.dp))
          FluidGlassContentTransition(targetState = index, blurReference = 28.dp) { i ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                Covers[i].title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
              )
              Text(
                Covers[i].artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
              )
            }
          }
          Spacer(Modifier.height(16.dp))
          PlayRow()
        }
      }
      item(key = "copertine") {
        Column {
          FluidSectionHeader(title = "Copertine", detail = "La pagina prende il colore di quella scelta")
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Covers.forEachIndexed { i, c ->
              val chosen = i == index
              Image(
                painter = painterResource(c.res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(72.dp)
                  .clip(ContinuousCornerShape(FluidRadius.Small))
                  .testTag(F8p7Tags.cover(i))
                  .semantics {
                    contentDescription = c.title
                    selected = chosen
                    role = Role.RadioButton
                  }
                  .fluidPressable(onClick = { index = i }, role = Role.RadioButton),
              )
            }
          }
        }
      }
      item(key = "lato") {
        Column {
          FluidSectionHeader(title = "La pagina", detail = "Dalla copertina come Apple Music, o dalla parte del tema")
          FluidSegmentedControl(
            options = FluidContentSide.entries,
            selected = side,
            onSelect = { side = it },
            modifier = Modifier.fillMaxWidth().testTag(F8p7Tags.Side),
            label = { if (it == FluidContentSide.Content) "Copertina" else "Tema" },
          )
        }
      }
      item(key = "tinta") {
        FluidListGroup {
          FluidToggleRow(
            title = "Colore dalla copertina",
            checked = tinted,
            onCheckedChange = { tinted = it },
            subtitle = "Spento, la pagina torna al tema normale",
          )
        }
      }
      item(key = "brani") {
        FluidListGroup {
          Tracks.forEachIndexed { i, t ->
            if (i > 0) FluidListDivider()
            FluidListRow(title = "${i + 1}  $t", subtitle = cover.artist, disclosure = false, onClick = {})
          }
        }
      }
      item(key = "ruoli") { RolesCaption() }
    }
  }
}

/**
 * La fila dei tasti della pagina dell'album di Apple Music (iOS 26.4, F8.7b): al centro la capsula piena «Riproduci» col
 * testo del colore della pagina ([FluidGlassButtonStyle.Filled]), ai lati due tasti tondi di vetro, casuale a sinistra e
 * «aggiungi alla libreria» a destra. Su una pagina scura la capsula e' bianca; su una chiara (dalla parte del tema) e'
 * l'accento; senza tinta e' l'accento del tema.
 */
@Composable
private fun PlayRow() {
  val roles = LocalFluidContentRoles.current
  val scheme = MaterialTheme.colorScheme
  val fill = when {
    roles == null -> scheme.primary
    roles.isDark -> Color.White
    else -> roles.accent
  }
  val ink = roles?.pageTint ?: scheme.onPrimary
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
    FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag(F8p7Tags.Shuffle)) {
      FluidIcon(FluidIcons.Shuffle, "Casuale")
    }
    FluidGlassButton(
      text = "Riproduci",
      onClick = {},
      modifier = Modifier.width(168.dp).testTag(F8p7Tags.Play),
      style = FluidGlassButtonStyle.Filled(fill, ink = ink),
      leading = { FluidIcon(FluidIcons.Filled.PlayArrow, null) },
    )
    FluidGlassIconButton(onClick = {}, modifier = Modifier.testTag(F8p7Tags.Add)) {
      FluidIcon(FluidIcons.Add, "Aggiungi alla libreria")
    }
  }
}

/** I contrasti dei ruoli della pagina in cui ci si trova, per le riprese (e per chi vuole vedere i numeri). */
@Composable
private fun RolesCaption() {
  val roles = LocalFluidContentRoles.current
  val text = if (roles == null) {
    "Tema normale: niente tinta."
  } else {
    "Testo ${ratioText(ratio(roles.onPage, roles.pageTint))} · secondario ${ratioText(ratio(roles.onPageVariant, roles.pageTint))} · " +
      "accento ${ratioText(ratio(roles.accent, roles.pageTint))} · sul tasto ${ratioText(ratio(roles.onAccent, roles.accent))} · " +
      "barra ${ratioText(ratio(roles.onBar, roles.barGlass))}"
  }
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp).testTag(F8p7Tags.Roles),
  )
}
