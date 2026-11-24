package dev.antigravity.fluidengine.foundation

// F8.11 · Prima volta, permessi e avvio: la parte pura. Le decisioni stanno qui, senza Android e senza Compose, perché
// sono quelle che si sbagliano in silenzio (un benvenuto mostrato due volte, un «negato per sempre» scambiato per «mai
// chiesto») e si provano sul JVM in un millisecondo. Il foglio, il tasto e la memoria vera stanno in `engine-ui` e in
// `engine-storage`.

/**
 * Dove l'engine ricorda le cose della prima volta: quale versione ha già mostrato il suo benvenuto, quali permessi ha
 * già chiesto e quali spiegazioni ha già dato (F8.11).
 *
 * Un'interfaccia e non un `DataStore`, perché `engine-ui` non dipende da `engine-storage`: l'app passa
 * `EngineFirstUseStore(context)` di `engine-storage` (il file dell'engine, a parte da quello dell'app), oppure una sua
 * memoria. [inMemory] è il ripiego dei test e di un'app che non vuole niente su disco: dura quanto il processo.
 *
 * Le chiavi sono quelle di [FluidFirstUseKeys]; i valori sono testo, così la stessa memoria serve tutte e due le idee.
 */
interface FluidFirstUseMemory {
  /** Il valore della chiave, o nulla se non c'è. */
  suspend fun read(key: String): String?

  /** Scrive il valore della chiave; nulla la cancella. */
  suspend fun write(key: String, value: String?)

  companion object {
    /** Una memoria che dura quanto il processo: per i test e per le prove. */
    fun inMemory(): FluidFirstUseMemory = InMemoryFirstUseMemory()
  }
}

private class InMemoryFirstUseMemory : FluidFirstUseMemory {
  private val values = java.util.concurrent.ConcurrentHashMap<String, String>()
  override suspend fun read(key: String): String? = values[key]
  override suspend fun write(key: String, value: String?) {
    if (value == null) values.remove(key) else values[key] = value
  }
}

/** Le chiavi della memoria della prima volta. Un nome stabile: cambiarle fa rivedere a tutti il benvenuto. */
object FluidFirstUseKeys {
  /** L'ultima versione per cui il benvenuto (o le novità) di [scope] è stato mostrato o saltato apposta. */
  fun welcomeSeen(scope: String = "app"): String = "welcome.$scope.seen"

  /** Il permesso [id] è già stato chiesto almeno una volta col dialogo di Android («1»). */
  fun permissionAsked(id: String): String = "permission.$id.asked"

  /** La spiegazione del permesso [id] è già stata mostrata una volta («1»): da lì in poi, il dialogo diretto. */
  fun permissionExplained(id: String): String = "permission.$id.explained"
}

/** Che foglio mostrare all'apertura (F8.11, `benvenuto-e-novita`). */
enum class FluidWelcomeKind {
  /** Il primo avvio dopo l'installazione: «Benvenuto in…». */
  Welcome,

  /** Il primo avvio dopo un aggiornamento con delle novità: «Novità di…». */
  WhatsNew,
}

/**
 * Se all'apertura va mostrato il benvenuto, le novità o niente. Pura: la chiama lo stato del foglio in `engine-ui`.
 *
 * - **Installazione nuova** ([freshInstall], nessuna versione vista): il benvenuto, se l'app ne ha uno ([hasWelcome]).
 * - **Aggiornamento** (una versione vista più vecchia di [currentVersion], oppure nessuna versione vista su un'app che
 *   c'era già, cioè la prima versione col benvenuto dell'engine): le novità, se per questa versione ce ne sono
 *   ([hasNews]).
 * - **La stessa versione** (o una più vecchia: un ritorno indietro): niente. Le novità si vedono **una volta sola**.
 *
 * Le versioni si confrontano con [compareVersions]: `1.2.0-beta1` viene prima di `1.2.0`, quindi chi passa dalla beta
 * alla stabile rivede le novità della stabile se l'app ne scrive.
 */
fun fluidWelcomeKind(
  currentVersion: String,
  seenVersion: String?,
  freshInstall: Boolean,
  hasNews: Boolean,
  hasWelcome: Boolean = true,
): FluidWelcomeKind? = when {
  seenVersion == null && freshInstall -> if (hasWelcome) FluidWelcomeKind.Welcome else null
  seenVersion == null -> if (hasNews) FluidWelcomeKind.WhatsNew else null
  compareVersions(currentVersion, seenVersion) > 0 -> if (hasNews) FluidWelcomeKind.WhatsNew else null
  else -> null
}

/**
 * Se la versione corrente va segnata come vista anche senza mostrare niente: un aggiornamento senza novità, o una prima
 * apertura senza benvenuto. Così una nota aggiunta dopo nel manifest non compare a chi quella versione l'ha già aperta.
 */
fun fluidWelcomeMarksSilently(currentVersion: String, seenVersion: String?): Boolean =
  seenVersion == null || compareVersions(currentVersion, seenVersion) > 0

/**
 * Una novità di una versione: una riga del foglio «Novità di…», con il titolo, la frase e il nome di un simbolo
 * facoltativo (`star`, `bolt`, `lock`…: l'app o `engine-ui` lo traducono in un'icona).
 */
data class FluidReleaseNote(
  val title: String,
  val text: String = "",
  val symbol: String? = null,
)

/**
 * Le novità di questa release: le righe scritte apposta nel manifest (`highlights`) se ci sono, altrimenti il
 * `changelog` diviso in righe ([fluidReleaseNotesFromChangelog]).
 */
fun AppRelease.releaseNotes(): List<FluidReleaseNote> =
  highlights.ifEmpty { fluidReleaseNotesFromChangelog(changelog) }

/**
 * Le novità di [version] nel manifest dello store: della stabile o della beta, quella con la stessa versione ([beta]
 * dice quale guardare per prima). Nessuna release con quella versione: nessuna novità, perché le note di un'altra
 * versione non sono le novità di questa.
 */
fun EngineManifest.releaseNotesFor(version: String, beta: Boolean = false): List<FluidReleaseNote> {
  val releases = listOfNotNull(app?.stable, app?.beta).let { if (beta) it.reversed() else it }
  val release = releases.firstOrNull { compareVersions(it.version, version) == 0 && it.version == version }
    ?: releases.firstOrNull { compareVersions(it.version, version) == 0 }
    ?: return emptyList()
  return release.releaseNotes()
}

/**
 * Il `changelog` dello store diviso in righe di novità: una riga per voce (i trattini, i pallini e gli asterischi in
 * testa si tolgono, i titoli Markdown si saltano). Una voce «Titolo: frase» con un titolo corto (al più
 * [TitleMaxWords] parole e [TitleMaxChars] caratteri) diventa titolo e frase; le altre restano un titolo solo.
 */
fun fluidReleaseNotesFromChangelog(changelog: String): List<FluidReleaseNote> =
  changelog.lineSequence()
    .map { it.trim().trimStart('-', '•', '*', '–', '·').trim() }
    .filter { it.isNotEmpty() && !it.startsWith("#") }
    .map { line ->
      val colon = line.indexOf(": ")
      val head = if (colon > 0) line.substring(0, colon).trim() else ""
      val words = head.split(' ').count { it.isNotBlank() }
      if (head.isNotEmpty() && head.length <= TitleMaxChars && words <= TitleMaxWords) {
        FluidReleaseNote(title = head, text = line.substring(colon + 2).trim())
      } else {
        FluidReleaseNote(title = line)
      }
    }
    .toList()

private const val TitleMaxChars = 40
private const val TitleMaxWords = 6

/**
 * Lo stato di un permesso (F8.11, `permesso-nel-punto-giusto`). Android ne dice due cose (concesso o no, e se mostrare
 * una spiegazione), l'engine ricorda la terza (se l'ha già chiesto): insieme fanno questi cinque.
 */
enum class FluidPermissionStatus {
  /** Mai chiesto: il primo tocco spiega (una volta) e poi chiede. */
  NeverAsked,

  /** Concesso. */
  Granted,

  /** Concesso in parte: le foto «solo alcune» di Android 14. L'azione va, e l'app può offrire di sceglierne altre. */
  Partial,

  /** Negato una volta: si può chiedere ancora, col dialogo diretto. */
  Denied,

  /** Negato per sempre: Android non mostra più il dialogo, resta «Apri impostazioni». */
  DeniedForever,
}

/**
 * Lo stato di un permesso da quello che dice Android e da quello che ricorda l'engine.
 *
 * Android non distingue «mai chiesto» da «negato per sempre»: in tutti e due i casi il permesso non c'è e
 * `shouldShowRequestPermissionRationale` è falso. Lo distingue [asked], che l'engine scrive nella sua memoria dopo il
 * primo dialogo vero. Un [rationale] vero vince su [asked]: il permesso è stato negato una volta (anche da un codice
 * dell'app di prima dell'engine) e si può chiedere ancora.
 */
fun fluidPermissionStatus(
  granted: Boolean,
  partial: Boolean,
  asked: Boolean,
  rationale: Boolean,
): FluidPermissionStatus = when {
  granted -> FluidPermissionStatus.Granted
  partial -> FluidPermissionStatus.Partial
  rationale -> FluidPermissionStatus.Denied
  !asked -> FluidPermissionStatus.NeverAsked
  else -> FluidPermissionStatus.DeniedForever
}

/**
 * Lo stato dopo la risposta al dialogo di Android.
 *
 * Al primo «Non consentire» Android accende la spiegazione (`rationale` vero); al secondo (da Android 11) la spegne e
 * non mostra più il dialogo: negato per sempre. Un primo dialogo che torna senza permesso **e** senza spiegazione non è
 * stato negato: è stato chiuso senza scegliere (indietro, un tocco fuori), e resta [FluidPermissionStatus.NeverAsked].
 */
fun fluidPermissionAfterRequest(
  previous: FluidPermissionStatus,
  granted: Boolean,
  partial: Boolean,
  rationale: Boolean,
): FluidPermissionStatus = when {
  granted -> FluidPermissionStatus.Granted
  partial -> FluidPermissionStatus.Partial
  rationale -> FluidPermissionStatus.Denied
  previous == FluidPermissionStatus.NeverAsked -> FluidPermissionStatus.NeverAsked
  else -> FluidPermissionStatus.DeniedForever
}

/** Cosa fa il tasto che ha bisogno di un permesso, quando lo si tocca. */
enum class FluidPermissionStep {
  /** Il permesso c'è (anche in parte): l'azione parte subito. */
  Proceed,

  /** La spiegazione breve che nasce dal tasto, poi il dialogo di Android. Una volta sola per permesso. */
  Explain,

  /** Il dialogo di Android, direttamente. */
  AskSystem,

  /** Android non lo chiede più: la spiegazione con «Apri impostazioni». */
  OpenSettings,
}

/**
 * Il passo del tocco, dallo [status] e da quello che l'engine ricorda.
 *
 * La spiegazione non diventa un muro: si mostra una volta ([explained] falso), poi si chiede direttamente. Un
 * permesso che il sistema non sa chiedere ([requestable] falso: le notifiche prima di Android 13) e che non c'è porta
 * alle impostazioni.
 */
fun fluidPermissionStep(
  status: FluidPermissionStatus,
  explained: Boolean,
  hasExplanation: Boolean = true,
  requestable: Boolean = true,
): FluidPermissionStep = when {
  status == FluidPermissionStatus.Granted || status == FluidPermissionStatus.Partial -> FluidPermissionStep.Proceed
  !requestable || status == FluidPermissionStatus.DeniedForever -> FluidPermissionStep.OpenSettings
  hasExplanation && !explained -> FluidPermissionStep.Explain
  else -> FluidPermissionStep.AskSystem
}
