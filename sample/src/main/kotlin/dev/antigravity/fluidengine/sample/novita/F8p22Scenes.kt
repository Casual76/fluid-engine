package dev.antigravity.fluidengine.sample.novita

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.antigravity.fluidengine.ai.bridge.AiToolClient
import dev.antigravity.fluidengine.ai.bridge.AiToolHostProvider
import dev.antigravity.fluidengine.ai.bridge.RemoteCall
import dev.antigravity.fluidengine.ai.bridge.RemoteHost
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.search.FluidPampaApp
import dev.antigravity.fluidengine.search.FluidPampaSearch
import dev.antigravity.fluidengine.search.FluidSearchBridge
import dev.antigravity.fluidengine.search.FluidSearchDocument
import dev.antigravity.fluidengine.search.FluidSearchExposure
import dev.antigravity.fluidengine.search.FluidSearchHit
import dev.antigravity.fluidengine.search.FluidSearchIndex
import dev.antigravity.fluidengine.search.FluidSearchIndexPolicy
import dev.antigravity.fluidengine.search.FluidSearchIndexer
import dev.antigravity.fluidengine.search.FluidSearchKindStyle
import dev.antigravity.fluidengine.search.FluidSearchSamples
import dev.antigravity.fluidengine.search.FluidSearchSource
import dev.antigravity.fluidengine.search.FluidSearchChanges
import dev.antigravity.fluidengine.search.FluidSemanticIndexer
import dev.antigravity.fluidengine.search.FluidTextEmbedder
import dev.antigravity.fluidengine.search.fluidPampaResults
import dev.antigravity.fluidengine.search.fluidSearchResults
import dev.antigravity.fluidengine.search.rememberFluidPampaSearchState
import dev.antigravity.fluidengine.search.rememberFluidSearchState
import dev.antigravity.fluidengine.search.semantic.FluidEmbeddingModels
import dev.antigravity.fluidengine.search.semantic.FluidLiteRtEmbedder
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.Campaign
import dev.antigravity.fluidengine.ui.fluid.icons.Description
import dev.antigravity.fluidengine.ui.fluid.icons.Event
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Forum
import dev.antigravity.fluidengine.ui.fluid.icons.Image
import dev.antigravity.fluidengine.ui.fluid.icons.Inbox
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.PictureAsPdf
import dev.antigravity.fluidengine.ui.fluid.icons.School
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// F8.22 · Ricerca: l'indice sul telefono come Core Spotlight (engine-search) e «Cerca in Pampa», la domanda a tutte le
// app col ponte firmato. Quattro scene, coi dati di prova inventati di `FluidSearchSamples` (note, mail, scuola, foto,
// assistente): niente dati veri. Il modello del significato (EmbeddingGemma 2 Text 270M, 157 MB) non sta nell'APK: la
// galleria lo legge da `Android/data/<pacchetto>/files/models/` se qualcuno ce l'ha copiato (adb push), se no la ricerca
// va per parole e la scena lo dice. Il log `F8p22` scrive le misure per la sessione sul tablet.

/** Le scene di F8.22. */
internal val F8p22Section = NewElementSection(
  pack = "F8.22",
  title = "Ricerca",
  scenes = listOf(
    NewElementScene(
      "ricerca-note", "Cerca nelle note",
      "Pampa Notes · errori di battitura, inizi di parola, accenti e plurali; i risultati per tipo, con le parole evidenziate",
    ) { onBack -> F8p22NotesScene(onBack) },
    NewElementScene(
      "ricerca-significato", "Per significato",
      "Pampa Mail · «la mail del dentista» trova lo Studio Odontoiatrico, col modello sul telefono",
    ) { onBack -> F8p22MeaningScene(onBack) },
    NewElementScene(
      "cerca-in-pampa", "Cerca in Pampa",
      "Una domanda a cinque app finte col ponte firmato di PampAI: i risultati per app",
    ) { onBack -> F8p22PampaScene(onBack) },
    NewElementScene(
      "ricerca-misure", "Le misure",
      "La prima indicizzazione di 5000 note, il tempo di una ricerca, il peso dell'indice; il modello: avvio e vettori",
    ) { onBack -> F8p22MeasureScene(onBack) },
  ),
  detail = "L'indice sul telefono come Spotlight, e «Cerca in Pampa» fra le app",
)

/** Le voci da provare a mano. */
internal val F8p22TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-22-note", TryArea.Pages, "Cerca nelle note",
    "Scrivi «riunone» (sbagliato): sopra i risultati c'è «Risultati per riunione · cercato anche «riunone»», divisi in " +
      "Note, Trascrizioni e Allegati, con la parola in grassetto; poi prova «perche», «citta», «verifich» e «l'ordine», e " +
      "tocca la pillola «Allegati» per tenere solo quelli.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Cerca nelle note"),
  ),
  TryItem(
    "f8-22-significato", TryArea.Pages, "Per significato",
    "Con il modello copiato sul telefono scrivi «la mail del dentista» e fermati: dopo un attimo arrivano, sfocate, le " +
      "mail dello Studio Odontoiatrico Rossi con «per significato»; con «Riduci movimento» arrivano ferme.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Per significato"),
  ),
  TryItem(
    "f8-22-pampa", TryArea.Pages, "Cerca in Pampa",
    "Scrivi «inglese» o «dentista»: «Cercato sul telefono, in 5 app», poi un gruppo per app (Mail, Notes, ClasseViva, " +
      "Gallery, PampAI); «Blocca Notes» la fa rispondere «è bloccata».",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Cerca in Pampa"),
  ),
  TryItem(
    "f8-22-misure", TryArea.Pages, "Le misure della ricerca",
    "Tocca «Indicizza 5000 note» e guarda i tempi (la prima indicizzazione, una ricerca, il peso del file); con il " +
      "modello, «Vettori di 200 note» dice l'avvio e i millisecondi per nota.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Le misure"),
  ),
)

/** I tag delle prove e delle riprese. */
internal object F8p22Tags {
  const val Notes = "f8p22:note"
  const val Meaning = "f8p22:significato"
  const val Pampa = "f8p22:pampa"
  const val Lock = "f8p22:blocca"
  const val Index5000 = "f8p22:indicizza"
  const val Vectors = "f8p22:vettori"
  const val Model = "f8p22:modello"
}

private const val Tag = "F8p22"

/** Il nome e il simbolo di ogni tipo dei dati di prova. */
internal val F8p22Kinds: Map<String, FluidSearchKindStyle> = mapOf(
  "nota" to FluidSearchKindStyle("Note", FluidIcons.Description, FluidTone.Warning),
  "trascrizione" to FluidSearchKindStyle("Trascrizioni", FluidIcons.Mic, FluidTone.Danger),
  "allegato" to FluidSearchKindStyle("Allegati", FluidIcons.PictureAsPdf, FluidTone.Info),
  "mail" to FluidSearchKindStyle("Mail", FluidIcons.Inbox, FluidTone.Primary),
  "comunicazione" to FluidSearchKindStyle("Comunicazioni", FluidIcons.Campaign, FluidTone.Success),
  "compito" to FluidSearchKindStyle("Agenda", FluidIcons.Event, FluidTone.Success),
  "voto" to FluidSearchKindStyle("Voti", FluidIcons.Star, FluidTone.Success),
  "foto" to FluidSearchKindStyle("Foto", FluidIcons.Image, FluidTone.Info),
  "conversazione" to FluidSearchKindStyle("Conversazioni", FluidIcons.Forum, FluidTone.Primary),
)

private fun f8p22KindsOf(app: String): Map<String, FluidSearchKindStyle> {
  val kinds = FluidSearchSamples.of(app).map { it.kind }.toSet()
  return F8p22Kinds.filterKeys { it in kinds }
}

/** Quando: «oggi», «ieri», «3 giorni fa», dall'adesso dei dati di prova. */
internal fun f8p22When(timestamp: Long?): String? {
  val t = timestamp ?: return null
  val days = ((FluidSearchSamples.Now - t) / 86_400_000L).toInt()
  return when {
    days <= 0 -> "oggi"
    days == 1 -> "ieri"
    days < 60 -> "$days giorni fa"
    else -> "${days / 30} mesi fa"
  }
}

/**
 * Il modello del significato della galleria: il file in `files/models/` dello spazio esterno dell'app (lo si copia con
 * adb), verificato una volta (dimensione e SHA-256). Null se non c'e'.
 */
internal object F8p22Model {
  private val lock = Mutex()
  @Volatile private var embedder: FluidLiteRtEmbedder? = null
  @Volatile private var checked = false

  /** Vero se questa build ha il motore (la libreria nativa sta solo nella debug: `sample/build.gradle`). */
  @Volatile var engineInBuild: Boolean? = null
    private set

  fun file(context: Context): File? =
    context.getExternalFilesDir("models")?.let { File(it, FluidEmbeddingModels.EmbeddingGemma2Text270M.fileName) }

  suspend fun get(context: Context): FluidLiteRtEmbedder? = lock.withLock {
    if (checked) return@withLock embedder
    withContext(Dispatchers.IO) {
      // La libreria del motore: nella bench e nella release della galleria non c'e', e senza il modello non si apre.
      val native = try {
        System.loadLibrary("litertlm_jni")
        true
      } catch (e: UnsatisfiedLinkError) {
        false
      }
      engineInBuild = native
      val f = file(context)
      val started = SystemClock.elapsedRealtime()
      val ok = native && f != null && FluidEmbeddingModels.verify(f)
      Log.i(Tag, "modello ${f?.path} motore=$native ok=$ok verifica=${SystemClock.elapsedRealtime() - started}ms")
      embedder = if (ok) FluidLiteRtEmbedder(f!!, File(context.noBackupFilesDir, "f8p22-model-cache")) else null
      checked = true
      embedder
    }
  }
}

// --- Cerca nelle note --------------------------------------------------------------------------------------------

@Composable
private fun F8p22NotesScene(onBack: () -> Unit) {
  val index by produceState<FluidSearchIndex?>(null) {
    val idx = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    idx.put(FluidSearchSamples.of("notes"))
    value = idx
    awaitDispose { idx.close() }
  }
  val state = rememberFluidSearchState(index)
  var opened by remember { mutableStateOf<String?>(null) }
  val kinds = remember { f8p22KindsOf("notes") }
  FluidScreen(
    title = "Note",
    subtitle = "Pampa Notes · l'indice sul telefono",
    onBack = onBack,
    modifier = Modifier.testTag(F8p22Tags.Notes),
    toolbar = { title(FluidToolbarTitleMode.Large) },
    search = state.spec(prompt = "Cerca nelle note", kinds = kinds),
  ) {
    if (state.query.isBlank()) {
      item(key = "tutte-titolo") { FluidSectionHeader(title = opened?.let { "Aperta: $it" } ?: "Tutte le note") }
      item(key = "tutte") { F8p22DocumentGroup(FluidSearchSamples.of("notes"), kinds) }
      item(key = "nota") {
        FluidSectionFootnote(
          "Prova «riunone» (sbagliato), «perche» senza accento, «verifich» a metà, «ordine» (sta dopo «l'»), «riunioni» al " +
            "plurale. Le pillole sotto il campo tengono solo un tipo.",
        )
      }
    } else {
      fluidSearchResults(state, kinds, onOpen = { opened = it.title?.text }, meta = { f8p22When(it.timestamp) })
    }
  }
}

@Composable
private fun F8p22DocumentGroup(docs: List<FluidSearchDocument>, kinds: Map<String, FluidSearchKindStyle>) {
  FluidListGroup {
    docs.forEachIndexed { i, doc ->
      if (i > 0) FluidListDivider()
      val style = kinds[doc.kind]
      FluidListRow(
        title = doc["title"],
        subtitle = doc["body"],
        meta = f8p22When(doc.timestamp),
        tone = style?.tone ?: FluidTone.Neutral,
        leading = style?.icon?.let { icon -> { FluidIcon(imageVector = icon, contentDescription = null) } },
        subtitleMaxLines = 2,
        titleMaxLines = 1,
      )
    }
  }
}

// --- Per significato ------------------------------------------------------------------------------------------------

/** Come sta il significato della scena: il modello, i vettori, i tempi. */
private data class F8p22MeaningStatus(
  val model: Boolean? = null,
  val vectors: Int = 0,
  val total: Int = 0,
  val initMillis: Long = -1,
  val perDocMillis: Double = -1.0,
)

@Composable
private fun F8p22MeaningScene(onBack: () -> Unit) {
  val context = LocalContext.current
  var status by remember { mutableStateOf(F8p22MeaningStatus()) }
  var embedder by remember { mutableStateOf<FluidTextEmbedder?>(null) }
  val index by produceState<FluidSearchIndex?>(null) {
    val idx = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    val mails = FluidSearchSamples.of("mail")
    idx.put(mails)
    value = idx
    val model = F8p22Model.get(context)
    status = F8p22MeaningStatus(model = model != null, total = mails.size)
    if (model != null) {
      val init = model.warmUp()
      val started = SystemClock.elapsedRealtime()
      val done = FluidSemanticIndexer(idx, model).update()
      val elapsed = SystemClock.elapsedRealtime() - started
      status = status.copy(vectors = done.indexed, initMillis = init, perDocMillis = elapsed.toDouble() / maxOf(1, done.indexed))
      Log.i(Tag, "significato avvio=${init}ms vettori=${done.indexed} in ${elapsed}ms")
      embedder = model
    }
    awaitDispose { idx.close() }
  }
  val state = rememberFluidSearchState(index, embedder)
  // I tempi di ogni risposta, per la sessione sul tablet.
  LaunchedEffect(state.results) {
    val r = state.results
    if (r.query.isNotBlank()) Log.i(Tag, "risposta «${r.query}» significato=${r.meaning} ${r.hits.size} risultati in ${r.tookMillis}ms")
  }
  val kinds = remember { f8p22KindsOf("mail") }
  var opened by remember { mutableStateOf<String?>(null) }
  FluidScreen(
    title = "Posta",
    subtitle = "Pampa Mail · per parole e per significato",
    onBack = onBack,
    modifier = Modifier.testTag(F8p22Tags.Meaning),
    toolbar = { title(FluidToolbarTitleMode.Large) },
    search = state.spec(prompt = "Cerca nella posta", kinds = kinds),
  ) {
    item(key = "modello") {
      FluidSectionFootnote(
        when (status.model) {
          null -> "Cerco il modello del significato…"
          false -> if (F8p22Model.engineInBuild == false) {
            "Il motore del significato c'è solo nella build di debug della galleria: qui la ricerca va per parole."
          } else {
            "Il modello del significato non c'è: copia ${FluidEmbeddingModels.EmbeddingGemma2Text270M.fileName} " +
              "(157 MB) in Android/data/${context.packageName}/files/models. Senza, la ricerca va per parole."
          }
          true -> if (status.vectors < status.total) "Preparo i vettori: ${status.vectors} di ${status.total}…" else
            "Modello sul telefono: avvio ${status.initMillis} ms, ${"%.0f".format(status.perDocMillis)} ms per mail. " +
              "Prova «la mail del dentista»: nessuna mail dice «dentista»."
        },
        modifier = Modifier.testTag(F8p22Tags.Model),
      )
    }
    if (state.query.isBlank()) {
      item(key = "posta-titolo") { FluidSectionHeader(title = opened?.let { "Aperta: $it" } ?: "In arrivo") }
      item(key = "posta") { F8p22DocumentGroup(FluidSearchSamples.of("mail"), kinds) }
    } else {
      fluidSearchResults(state, kinds, onOpen = { opened = it.title?.text }, meta = { hit -> listOfNotNull(hit.fields["people"], f8p22When(hit.timestamp)).joinToString(" · ") })
    }
  }
}

// --- Cerca in Pampa -------------------------------------------------------------------------------------------------

/** Le cinque app finte della scena: un ospite del ponte ciascuna, dichiarati nel manifesto della build di debug. */
internal object F8p22Demo {
  /** Le app, con l'autorita' (dopo il pacchetto) e il nome. */
  val apps: List<Pair<String, String>> = FluidSearchSamples.apps.map { (id, label) -> id to label }

  /** Le app sono bloccate? (il tasto «Blocca Notes» della scena). */
  @Volatile var notesLocked: Boolean = false

  private val lock = Mutex()
  private val indexes = HashMap<String, FluidSearchIndex>()
  @Volatile var model: FluidTextEmbedder? = null

  fun authority(context: Context, app: String): String = "${context.packageName}.f8p22.$app"

  /** Prepara gli indici delle cinque app (e i loro vettori, se c'e' il modello): la prima domanda non aspetta. */
  suspend fun prepare(context: Context) = lock.withLock {
    val embedder = F8p22Model.get(context)
    model = embedder
    for ((app, _) in apps) {
      if (app in indexes) continue
      val idx = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
      idx.put(FluidSearchSamples.of(app))
      if (embedder != null) FluidSemanticIndexer(idx, embedder).update()
      indexes[app] = idx
    }
  }

  fun index(app: String): FluidSearchIndex? = indexes[app]

  /** Le app della scena come le vede «Cerca in Pampa»: solo quelle che rispondono al catalogo (la build di debug). */
  suspend fun discovered(context: Context, client: AiToolClient): List<FluidPampaApp> = apps.mapNotNull { (app, label) ->
    val authority = authority(context, app)
    val catalog = runCatching { client.catalog(RemoteHost(context.packageName, authority, label)) }.getOrNull() ?: return@mapNotNull null
    if (catalog.tools.none { it.name == FluidSearchBridge.ToolName }) null else FluidPampaApp(context.packageName, authority, label)
  }
}

/** L'ospite del ponte di un'app finta: il tool della ricerca sul suo indice, come lo avrebbe la vera app. */
abstract class F8p22DemoHost : AiToolHostProvider<Unit>() {
  abstract val app: String

  override fun registry(): ToolRegistry<Unit> {
    val index = F8p22Demo.index(app) ?: return ToolRegistry(emptyList(), emptyList())
    val exposure = FluidSearchExposure(
      kindLabels = FluidSearchSamples.kinds,
      locked = { app == "notes" && F8p22Demo.notesLocked },
      embedder = F8p22Demo.model,
    )
    return ToolRegistry(listOf(FluidSearchBridge.searchTool(index, exposure)), listOf(FluidSearchBridge.Group))
  }

  override suspend fun context(call: RemoteCall) = Unit
  override fun domain(): String = app
  override fun appLabel(): String = FluidSearchSamples.apps[app] ?: app
}

class F8p22NotesHost : F8p22DemoHost() { override val app = "notes" }
class F8p22MailHost : F8p22DemoHost() { override val app = "mail" }
class F8p22SchoolHost : F8p22DemoHost() { override val app = "classeviva" }
class F8p22GalleryHost : F8p22DemoHost() { override val app = "gallery" }
class F8p22AssistantHost : F8p22DemoHost() { override val app = "pampai" }

private val F8p22AppIcons = mapOf(
  "notes" to (FluidIcons.Description to FluidTone.Warning),
  "mail" to (FluidIcons.Inbox to FluidTone.Primary),
  "classeviva" to (FluidIcons.School to FluidTone.Success),
  "gallery" to (FluidIcons.Image to FluidTone.Info),
  "pampai" to (FluidIcons.AutoAwesome to FluidTone.Primary),
)

@Composable
private fun F8p22PampaScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var locked by remember { mutableStateOf(F8p22Demo.notesLocked) }
  var opened by remember { mutableStateOf<String?>(null) }
  val search by produceState<FluidPampaSearch?>(null) {
    F8p22Demo.prepare(context)
    val client = AiToolClient(context.applicationContext)
    value = FluidPampaSearch(client, discover = { F8p22Demo.discovered(context, client) }, embedder = F8p22Demo.model)
  }
  val state = rememberFluidPampaSearchState(search)
  LaunchedEffect(state.results) {
    val r = state.results
    if (r.query.isNotBlank() && r.groups.isNotEmpty() && r.pending == 0) {
      Log.i(Tag, "cerca in pampa «${r.query}» significato=${r.meaning} ${r.groups.size} app, ${r.shown.size} con risultati, in ${r.tookMillis}ms")
    }
  }
  FluidScreen(
    title = "Cerca in Pampa",
    subtitle = "Una domanda a tutte le app, sul telefono",
    onBack = onBack,
    modifier = Modifier.testTag(F8p22Tags.Pampa),
    toolbar = { title(FluidToolbarTitleMode.Large) },
    search = state.spec(),
  ) {
    if (state.query.isBlank()) {
      if (search == null) item(key = "prepara") { FluidSectionFootnote("Preparo le cinque app di prova…") }
      item(key = "app-titolo") { FluidSectionHeader(title = opened?.let { "Aperto: $it" } ?: "Le app che rispondono") }
      item(key = "app") {
        FluidListGroup {
          F8p22Demo.apps.forEachIndexed { i, (app, label) ->
            if (i > 0) FluidListDivider()
            val (icon, tone) = F8p22AppIcons.getValue(app)
            FluidListRow(
              title = label,
              subtitle = FluidSearchSamples.of(app).size.let { "$it documenti di prova" },
              tone = tone,
              leading = { FluidIcon(imageVector = icon, contentDescription = null) },
            )
          }
        }
      }
      item(key = "blocca") {
        Column {
          FluidButton(
            text = if (locked) "Sblocca Notes" else "Blocca Notes",
            onClick = {
              locked = !locked
              F8p22Demo.notesLocked = locked
              scope.launch { search?.refresh() }
            },
            modifier = Modifier.testTag(F8p22Tags.Lock),
          )
          FluidSectionFootnote(
            "Prova «dentista», «28 novembre», «riunione», «inglese». Le app sono finte, nella stessa build di debug, ma " +
              "la domanda passa dal ponte firmato vero (come con PampAI); con il modello copiato, anche per significato.",
          )
        }
      }
    } else {
      fluidPampaResults(
        state,
        onOpen = { hit -> opened = "${hit.app.label} › ${hit.title.text}" },
        appIcon = { app ->
          val id = app.authority.substringAfterLast('.')
          F8p22AppIcons[id]?.let { (icon, _) -> { FluidIcon(imageVector = icon, contentDescription = null) } }
        },
      )
    }
  }
}

// --- Le misure ------------------------------------------------------------------------------------------------------

/** Le misure della scena, scritte anche nel log `F8p22`. */
private data class F8p22Measures(
  val running: String? = null,
  val lines: List<Pair<String, String>> = emptyList(),
)

/** 5000 note di prova, a pagine: la sorgente della prima indicizzazione. */
private class F8p22GeneratedSource(private val docs: List<FluidSearchDocument>) : FluidSearchSource {
  override val id: String = "misure"
  override suspend fun changes(cursor: String?, limit: Int): FluidSearchChanges {
    val from = cursor?.toInt() ?: 0
    val page = docs.drop(from).take(limit)
    val next = from + page.size
    return FluidSearchChanges(upserts = page, cursor = next.toString(), hasMore = next < docs.size)
  }
}

@Composable
private fun F8p22MeasureScene(onBack: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var measures by remember { mutableStateOf(F8p22Measures()) }

  fun add(label: String, value: String) {
    Log.i(Tag, "misura $label = $value")
    measures = measures.copy(lines = measures.lines + (label to value))
  }

  FluidScreen(
    title = "Le misure",
    subtitle = "La ricerca sul telefono",
    onBack = onBack,
    toolbar = { title(FluidToolbarTitleMode.Large) },
  ) {
    item(key = "tasti") {
      Column {
        FluidButton(
          text = "Indicizza 5000 note",
          enabled = measures.running == null,
          modifier = Modifier.testTag(F8p22Tags.Index5000),
          onClick = {
            scope.launch {
              measures = F8p22Measures(running = "Indicizzo…")
              FluidSearchIndex.open(context, FluidSearchSamples.schema, name = "f8p22-misure").destroy()
              val index = FluidSearchIndex.open(context, FluidSearchSamples.schema, name = "f8p22-misure")
              val docs = FluidSearchSamples.generated(5000)
              val started = SystemClock.elapsedRealtime()
              val done = FluidSearchIndexer(index, F8p22GeneratedSource(docs), FluidSearchIndexPolicy(chunk = 250)).update(heavy = true)
              add("prima indicizzazione", "${done.indexed} note in ${SystemClock.elapsedRealtime() - started} ms")
              val queries = listOf("riunione genitori ", "verifich", "biblioteca ", "prenotazone ", "dentista ")
              val times = queries.map { q -> index.search(q).tookMillis }
              index.search("riunione ") // la prima dopo l'apertura carica il vocabolario
              val warm = queries.map { q -> index.search(q).let { it.tookMillis to it.total } }
              add("ricerca (prima)", times.joinToString(" · ") { "$it ms" })
              add("ricerca", warm.joinToString(" · ") { "${it.first} ms (${it.second})" })
              val stats = index.stats()
              add("peso dell'indice", "${stats.bytes / 1024} KiB, ${stats.words} parole")
              index.close()
              measures = measures.copy(running = null)
            }
          },
        )
        FluidButton(
          text = "Vettori di 200 note",
          enabled = measures.running == null,
          modifier = Modifier.testTag(F8p22Tags.Vectors),
          onClick = {
            scope.launch {
              measures = measures.copy(running = "Preparo il modello…")
              val model = F8p22Model.get(context)
              if (model == null) {
                add("modello", "non c'è (va copiato in files/models)")
              } else {
                model.release()
                val init = model.warmUp()
                add("avvio del modello", "$init ms")
                val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
                index.put(FluidSearchSamples.generated(200))
                val started = SystemClock.elapsedRealtime()
                val done = FluidSemanticIndexer(index, model).update()
                val elapsed = SystemClock.elapsedRealtime() - started
                add("vettori", "${done.indexed} note in $elapsed ms (${"%.1f".format(elapsed.toDouble() / maxOf(1, done.indexed))} ms per nota)")
                val q0 = SystemClock.elapsedRealtime()
                val v = model.embed(listOf("la mail del dentista")).single()
                val q1 = SystemClock.elapsedRealtime()
                val r = index.search(dev.antigravity.fluidengine.search.FluidSearchQuery("la mail del dentista", meaning = v))
                add("una domanda per significato", "vettore ${q1 - q0} ms, ricerca ${r.tookMillis} ms")
                index.close()
              }
              measures = measures.copy(running = null)
            }
          },
        )
      }
    }
    item(key = "stato") {
      FluidSectionFootnote(measures.running ?: "I tempi si scrivono anche nel log «F8p22».")
    }
    if (measures.lines.isNotEmpty()) {
      item(key = "righe") {
        FluidListGroup {
          measures.lines.forEachIndexed { i, (label, value) ->
            if (i > 0) FluidListDivider()
            FluidListRow(title = label, subtitle = value)
          }
        }
      }
    }
    item(key = "testo") {
      Text(
        text = "Le note sono inventate (FluidSearchSamples.generated); il file dell'indice è nella cartella privata della galleria.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
