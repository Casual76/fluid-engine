package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.editor.FluidCropAspect
import dev.antigravity.fluidengine.editor.FluidEditorMode
import dev.antigravity.fluidengine.editor.FluidEditorSource
import dev.antigravity.fluidengine.editor.FluidImageEdit
import dev.antigravity.fluidengine.editor.FluidImageEditor
import dev.antigravity.fluidengine.editor.FluidImageExport
import dev.antigravity.fluidengine.editor.fluidImageEditTool
import dev.antigravity.fluidengine.editor.rememberFluidImageEditTool
import dev.antigravity.fluidengine.editor.rememberFluidImageEditorState
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfter
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfterLabels
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerThumbnail
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidFileViewerRoute
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidViewerThumbnail
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// F8.15 · Modifica delle immagini (`ritaglia-e-regola`, `confronto-prima-dopo`), le scene della pagina «Elementi nuovi».
// Le foto sono quelle di prova della galleria (le stesse dei visori); il salvataggio scrive nella cache della galleria, mai
// nella libreria del telefono.

/** La sezione della pagina. */
internal val F8p15Section: NewElementSection
  get() = NewElementSection(
    pack = "F8.15",
    title = "Modifica delle immagini",
    scenes = F8p15Scenes,
    detail = "Ritaglia e regola come Foto di iOS 26, l'originale sempre recuperabile, e il confronto prima e dopo",
  )

internal val F8p15Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "modifica-foto", "Modifica una foto",
    "MyCloud Gallery · regolazioni con la ruota, Auto, ritaglio e raddrizza; tieni premuta la foto per l'originale",
  ) { onBack -> F8p15Host(onBack, F8p15Start.Album) },
  NewElementScene(
    "modifica-subito", "L'editor aperto",
    "L'editor sulla foto chiara, per le riprese e le prove: Fine salva una copia, Annulla chiede conferma",
  ) { onBack -> F8p15Host(onBack, F8p15Start.Editor) },
  NewElementScene(
    "foto-profilo-tonda", "La foto dell'account",
    "Account Pampa · il ritaglio tondo, solo le proporzioni dell'avatar",
  ) { onBack -> F8p15Host(onBack, F8p15Start.Avatar) },
  NewElementScene(
    "visore-modifica", "Modifica dal visore",
    "Il visore dei file con lo strumento «Modifica»: Fine salva, indietro lascia com'era",
  ) { onBack -> F8p15Host(onBack, F8p15Start.Viewer) },
  NewElementScene(
    "confronto-tendina", "Confronto prima e dopo",
    "Universal Converter · la tendina sugli artefatti della compressione, con lo zoom del visore",
  ) { onBack -> F8p15Host(onBack, F8p15Start.Compare) },
)

/** Da dove parte una scena. */
internal enum class F8p15Start(val route: String) {
  Album("album"), Editor("modifica/0"), Avatar("avatar"), Viewer("file/0"), Compare("confronto")
}

/** I tag delle prove e delle riprese. */
internal object F8p15Tags {
  const val Result = "f8p15:risultato"
  const val Reopen = "f8p15:riapri"
  const val Compare = "f8p15:confronta"
  const val Revert = "f8p15:ripristina"
  fun photo(index: Int) = "f8p15:foto:$index"
}

/** Le foto di prova: chiara, scura, il razzo (un orizzonte da raddrizzare). */
internal object F8p15Photos {
  val ids = listOf(R.drawable.viewer_flower, R.drawable.viewer_astronaut, R.drawable.viewer_rocket)
  val names = listOf("Fiori", "Astronauta", "Razzo")
  /** Le proporzioni delle tre foto (960 × 540, 512 × 512, 640 × 427). */
  val ratios = listOf(960f / 540f, 1f, 640f / 427f)

  fun uri(context: Context, index: Int): Uri =
    Uri.parse("android.resource://${context.packageName}/${ids[index % ids.size]}")
}

/** L'ultimo salvataggio della scena: il file nuovo, la ricetta, le misure. */
internal class F8p15Saved(val index: Int, val file: File, val edit: FluidImageEdit, val result: FluidImageExport.Result)

@Composable
private fun F8p15Host(onBack: () -> Unit, start: F8p15Start) {
  val context = LocalContext.current
  val nav = rememberFluidNavController()
  val ns = rememberFluidGlassNamespace()
  val scope = rememberCoroutineScope()
  var saved by remember { mutableStateOf<F8p15Saved?>(null) }
  var reopen by remember { mutableStateOf(false) }
  val editTool = rememberFluidImageEditTool()

  fun save(index: Int, edit: FluidImageEdit, then: () -> Unit) {
    scope.launch {
      val file = File(context.cacheDir, "f8p15/modifica-$index.jpg").apply { parentFile?.mkdirs() }
      val result = FluidImageExport.export(context, FluidEditorSource.uri(F8p15Photos.uri(context, index)), edit, file)
      saved = F8p15Saved(index, file, edit, result)
      then()
    }
  }

  FluidNavHost(nav, start.route) {
    composable("album") {
      F8p15Album(
        saved = saved,
        onEdit = { i -> reopen = false; nav.navigate("modifica/$i") },
        onReopen = { reopen = true; saved?.let { nav.navigate("modifica/${it.index}") } },
        onCompare = { nav.navigate("confronto") },
        onViewer = { nav.navigate("file/0") },
        onBack = onBack,
        thumbnail = { i -> FluidViewerThumbnail("f8p15:$i", ns) },
      )
    }
    composable("modifica/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType })) { entry ->
      val index = entry.arguments?.getInt("index") ?: 0
      val initial = if (reopen) saved?.takeIf { it.index == index }?.edit ?: FluidImageEdit.Original else FluidImageEdit.Original
      val state = rememberFluidImageEditorState(FluidEditorSource.uri(F8p15Photos.uri(context, index)), initial)
      FluidImageEditor(
        state = state,
        onCancel = { if (start == F8p15Start.Editor) onBack() else nav.popBackStack() },
        onDone = { edit -> save(index, edit) { if (start == F8p15Start.Editor) onBack() else nav.popBackStack() } },
      )
    }
    composable("avatar") {
      val state = rememberFluidImageEditorState(
        FluidEditorSource.uri(F8p15Photos.uri(context, 1)),
        FluidImageEdit.Original,
      )
      androidx.compose.runtime.LaunchedEffect(state) {
        state.mode = FluidEditorMode.Crop
        androidx.compose.runtime.snapshotFlow { state.isReady }.collect { if (it) state.setAspect(FluidCropAspect.Circle) }
      }
      FluidImageEditor(
        state = state,
        onCancel = onBack,
        onDone = { edit -> save(1, edit) { onBack() } },
        aspects = listOf(FluidCropAspect.Circle, FluidCropAspect.Square),
      )
    }
    fluidFileViewerRoute("file/{index}", arguments = listOf(navArgument("index") { type = NavType.IntType }),
      initial = { it.arguments?.getInt("index") ?: 0 }, thumbnail = { _, i -> FluidViewerThumbnail("f8p15:$i", ns) }) {
      // Un contenuto per foto, ricordato: il visore chiama `content` spesso (le trappole dei visori).
      val pages = remember {
        F8p15Photos.ids.mapIndexed { i, id ->
          FluidViewerContent.Image(F8p15Photos.ratios[i]) {
            Image(painterResource(id), F8p15Photos.names[i], Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
          }
        }
      }
      FluidFileViewer(
        count = F8p15Photos.ids.size,
        content = { i -> pages[i] },
        tools = {
          fluidImageEditTool(editTool, source = { i -> FluidEditorSource.uri(F8p15Photos.uri(context, i)) }) { i, edit ->
            save(i, edit) {}
          }
        },
        onBack = { if (start == F8p15Start.Viewer) onBack() else nav.popBackStack() },
      )
    }
    composable("confronto") {
      F8p15CompareViewer(saved, onBack = { if (start == F8p15Start.Compare) onBack() else nav.popBackStack() })
    }
  }
}

@Composable
private fun F8p15Album(
  saved: F8p15Saved?,
  onEdit: (Int) -> Unit,
  onReopen: () -> Unit,
  onCompare: () -> Unit,
  onViewer: () -> Unit,
  onBack: () -> Unit,
  thumbnail: (Int) -> FluidViewerThumbnail,
) {
  FluidScreen(title = "Modifica delle immagini", subtitle = "Ritaglia e regola, come Foto", onBack = onBack) {
    item { FluidSectionHeader("Le foto") }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        F8p15Photos.ids.forEachIndexed { i, id ->
          Image(
            painterResource(id), F8p15Photos.names[i],
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .weight(1f)
              .aspectRatio(1f)
              .fluidViewerThumbnail(thumbnail(i), ContinuousCornerShape(FluidRadius.Control))
              .clip(ContinuousCornerShape(FluidRadius.Control))
              .testTag(F8p15Tags.photo(i))
              .fluidPressable(onClick = { onEdit(i) }),
          )
        }
      }
    }
    item { FluidSectionFootnote("Tocca una foto per modificarla. Il file di partenza non cambia mai: «Fine» salva una copia.") }
    item { FluidSectionHeader("Nel visore") }
    item {
      FluidListGroup {
        FluidListRow("Apri nel visore dei file", "Lo strumento «Modifica» nella capsula in basso", onClick = onViewer)
        FluidListDivider()
        FluidListRow("Confronto prima e dopo", "La tendina, con lo zoom del visore", onClick = onCompare,
          modifier = Modifier.testTag(F8p15Tags.Compare))
      }
    }
    if (saved != null) {
      item { FluidSectionHeader("Ultimo salvataggio") }
      item {
        val bitmap by produceState<Bitmap?>(null, saved.file, saved.result) {
          value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(saved.file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
          }
        }
        FluidListGroup(Modifier.testTag(F8p15Tags.Result)) {
          FluidListRow(
            title = "${F8p15Photos.names[saved.index]} modificata",
            subtitle = "${saved.result.width} × ${saved.result.height} · ${saved.result.bytes / 1024} kB" +
              if (saved.result.reduced) " · ridotta per la memoria" else "",
            leading = {
              bitmap?.let {
                Image(it.asImageBitmap(), null, Modifier.size(44.dp).clip(ContinuousCornerShape(FluidRadius.Small)),
                  contentScale = ContentScale.Crop)
              }
            },
            leadingTile = false,
            disclosure = false,
          )
          FluidListDivider()
          FluidListRow("Riapri la modifica", "La ricetta salvata: ${saved.edit.encode().take(48)}…", onClick = onReopen,
            modifier = Modifier.testTag(F8p15Tags.Reopen))
          FluidListDivider()
          FluidListRow("Confronta con l'originale", "La tendina fra la foto di partenza e la copia", onClick = onCompare)
        }
      }
    }
  }
}

/**
 * Il confronto nel visore: con un salvataggio, la foto di partenza contro la copia modificata; senza, la foto contro la
 * stessa foto compressa in JPEG a qualita' 12 (il caso di Universal Converter). Lo zoom del visore muove le due insieme.
 */
@Composable
private fun F8p15CompareViewer(saved: F8p15Saved?, onBack: () -> Unit) {
  val context = LocalContext.current
  val index = saved?.index ?: 2
  val before by produceState<Bitmap?>(null, index) {
    value = withContext(Dispatchers.IO) { BitmapFactory.decodeResource(context.resources, F8p15Photos.ids[index]) }
  }
  val after by produceState<Bitmap?>(null, saved, before) {
    val b = before ?: return@produceState
    value = withContext(Dispatchers.IO) {
      if (saved != null) {
        BitmapFactory.decodeFile(saved.file.absolutePath)
      } else {
        val bytes = java.io.ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 12, it) }.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      }
    }
  }
  val b = before
  val a = after
  val ratio = if (saved != null && a != null) a.width.toFloat() / a.height else b?.let { it.width.toFloat() / it.height }
  // Un contenuto ricordato: il visore chiama `content` spesso.
  val page = remember(b, a, ratio) {
    FluidViewerContent.Image(ratio) {
      if (b != null && a != null) {
        FluidBeforeAfter(
          before = { Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
          after = { Image(a.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
          labels = if (saved != null) FluidBeforeAfterLabels(before = "Originale", after = "Modificata")
            else FluidBeforeAfterLabels(before = "Originale", after = "JPEG 12"),
        )
      } else {
        Box(Modifier.fillMaxSize())
      }
    }
  }
  FluidFileViewer(
    count = 1,
    content = { _ -> page },
    onBack = onBack,
  )
}

/** Le voci da provare a mano (in coda a «Da provare»). */
internal val F8p15TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-15-regola", TryArea.ContentData, "Regola come in Foto",
    "Apri «Modifica una foto», tocca i Fiori e gira la ruota delle regolazioni: giudica se la foto cambia subito mentre " +
      "il dito muove il righello, senza scatti, e se il nome sopra la foto cambia sfocato.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Modifica una foto"),
  ),
  TryItem(
    "f8-15-originale", TryArea.ContentData, "Tieni premuto per l'originale",
    "Nell'editor alza l'esposizione e tieni premuta la foto, poi prova «Auto», «Ripristina originale» in «•••» e le frecce: " +
      "giudica se si vede subito l'originale, se lasciando torna la modifica e se ogni passo si annulla e si ripristina.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Modifica una foto"),
  ),
  TryItem(
    "f8-15-ritaglia", TryArea.ContentData, "Ritaglia e raddrizza",
    "In «Ritaglia» tira un angolo, sposta e pizzica la foto, gira il righello di «Raddrizza» e prova le proporzioni: " +
      "giudica se la cornice non esce mai dalla foto, se torna al centro quando lasci e se la rotazione di 90° gira giusta.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Modifica una foto"),
  ),
  TryItem(
    "f8-15-tendina", TryArea.ContentData, "Il confronto a tendina",
    "Apri «Confronto prima e dopo», sposta la maniglia, ingrandisci con due dita e tocca «Originale» e «JPEG 12»: giudica " +
      "se le due versioni restano allineate nello zoom e se la maniglia non fa sfogliare il visore.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Confronto prima e dopo"),
  ),
)
