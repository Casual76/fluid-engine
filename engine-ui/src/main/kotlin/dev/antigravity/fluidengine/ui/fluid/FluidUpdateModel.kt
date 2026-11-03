package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.foundation.AppUpdateErrorKind
import dev.antigravity.fluidengine.foundation.AppUpdateInstallState
import dev.antigravity.fluidengine.foundation.AvailableAppUpdate
import java.util.Locale

// L'aggiornamento in-app, il lato che non disegna (3.0, fase 12c).
//
// Otto app avevano scritto da sole la schermata dell'aggiornamento — l'avviso all'avvio, le righe
// delle impostazioni, la scheda in cima alla home — e tutte avevano ricostruito lo stesso albero di
// stati in un loro `sealed interface` privato (`PromptState`, `UpdateStatus`, `UpdateUiState`...), con le
// stesse quattro-cinque frasi e gli stessi errori: un "Idle" che compariva a meta' (e che l'engine non
// ha mai avuto), un `Installed` che richiudeva l'avviso senza dire niente, un download senza dimensione
// che mostrava "0%" per tutto il tempo.
//
// **Dove sta questo file, e perche'.** Qui, in `engine-ui`, e non in `engine-update`: l'interfaccia non
// deve poter tirare dentro il modulo che parla di rete e di installatori, e un'app che fa gli
// aggiornamenti a modo suo (un Pampa Store diverso, un solo canale) deve poter usare lo stesso disegno.
// Senza un collegamento nuovo fra i moduli: i tipi che un `AppUpdater` produce (`AvailableAppUpdate`,
// `AppUpdateInstallState`, `UpdateChannel`) stanno in `engine-foundation`, da cui `engine-ui` dipende
// gia' (e con lui tutte le app). Quindi il modello qui sotto e' **neutro** — non sa cosa sia un
// `EngineAppUpdater` — e l'app lo riempie con le due funzioni di traduzione in fondo
// ([toFluidUpdateRelease], [toFluidUpdateState]), che parlano solo i tipi di foundation.

/** La versione che si offre: quanto basta a dire cosa e' e quanto pesa. */
@Immutable
data class FluidUpdateRelease(
  val version: String,
  /** Le note di rilascio, anche in Markdown: la vista le ripulisce ([FluidUpdate.plainNotes]). */
  val notes: String = "",
  /** Il peso del file, o 0 se il manifest non lo dice (e allora la vista non inventa una dimensione). */
  val sizeBytes: Long = 0L,
)

/** A che punto sta una versione che si sta installando e non scarica piu'. */
enum class FluidUpdateStage {
  /** Il file c'e', lo si sta controllando. Una barra senza fine. */
  Verifying,

  /** Lo sta installando il sistema. Una barra senza fine. */
  Installing,

  /** Android mostra la sua conferma: **niente si muove finche' non risponde**, e una barra che gira mentirebbe. */
  AwaitingUser,
}

/**
 * Dove sta un aggiornamento, dal punto di vista di chi lo guarda.
 *
 * **Stati, non percentuali**: quasi tutto un'installazione non e' una percentuale — le pause lunghe sono
 * la verifica e la finestra di conferma del sistema, e una barra ferma al 100% e' il modo piu' comune in cui
 * un aggiornamento fuori da uno store sembra rotto mentre funziona. Per lo stesso motivo **"nessun
 * aggiornamento" non e' un errore** ([UpToDate]) e il fallimento di un controllo ([CheckFailed]) non e' il
 * fallimento di un'installazione ([Failed]): il primo si ritenta con un tocco, il secondo ha una versione in
 * mano che si e' fermata a meta'.
 */
@Immutable
sealed interface FluidUpdateUiState {
  /** Non si sa niente: non si e' ancora controllato. */
  data object Idle : FluidUpdateUiState

  /** Si sta chiedendo al manifest. */
  data object Checking : FluidUpdateUiState

  /** Si e' controllato e non c'e' niente di piu' nuovo. */
  data object UpToDate : FluidUpdateUiState

  /** Il controllo non e' riuscito (rete, manifest). [message] e' il testo tecnico, se c'e'. */
  data class CheckFailed(val message: String? = null) : FluidUpdateUiState

  /** C'e' una versione nuova e non si e' ancora toccato niente. */
  data class Available(val release: FluidUpdateRelease) : FluidUpdateUiState

  /**
   * Si sta scaricando. [progress] e' 0..1, o **null se il peso non si conosce** (allora la barra e'
   * senza fine: "0%" per tutto il download e' peggio di non dire niente).
   */
  data class Downloading(
    val release: FluidUpdateRelease,
    val progress: Float?,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
  ) : FluidUpdateUiState

  /** Verifica, installazione, o l'attesa della conferma del sistema ([stage]). */
  data class Working(
    val release: FluidUpdateRelease,
    val stage: FluidUpdateStage,
    val message: String = "",
  ) : FluidUpdateUiState

  /** Installata. L'app, di norma, sta per essere sostituita. */
  data class Installed(val release: FluidUpdateRelease) : FluidUpdateUiState

  /** L'installazione si e' fermata ([message]: quello che il sistema ha detto). Si puo' riprovare. */
  data class Failed(val release: FluidUpdateRelease, val message: String) : FluidUpdateUiState

  /** Serve il permesso di installare da questa app: Android lo chiede una volta, e conviene dirlo prima. */
  data class NeedsPermission(val release: FluidUpdateRelease) : FluidUpdateUiState
}

/** La versione di cui parla lo stato, o null se non ce n'e' una (nessun controllo, nessuna novita'). */
val FluidUpdateUiState.release: FluidUpdateRelease?
  get() = when (this) {
    is FluidUpdateUiState.Available -> release
    is FluidUpdateUiState.Downloading -> release
    is FluidUpdateUiState.Working -> release
    is FluidUpdateUiState.Installed -> release
    is FluidUpdateUiState.Failed -> release
    is FluidUpdateUiState.NeedsPermission -> release
    FluidUpdateUiState.Idle,
    FluidUpdateUiState.Checking,
    FluidUpdateUiState.UpToDate,
    is FluidUpdateUiState.CheckFailed,
    -> null
  }

/**
 * Vero se c'e' qualcosa in corso che un secondo tocco non deve rilanciare: un controllo, un download, la
 * verifica, l'installazione — e anche l'attesa della conferma ([FluidUpdateStage.AwaitingUser]), perche'
 * finche' il sistema non ha avuto risposta ripartire vorrebbe dire due finestre di conferma.
 */
val FluidUpdateUiState.isBusy: Boolean
  get() = this is FluidUpdateUiState.Checking ||
    this is FluidUpdateUiState.Downloading ||
    this is FluidUpdateUiState.Working

/** Vero se il tasto principale e' "installa": c'e' una versione che si puo' (ri)avviare. */
val FluidUpdateUiState.canInstall: Boolean
  get() = this is FluidUpdateUiState.Available ||
    this is FluidUpdateUiState.Failed ||
    this is FluidUpdateUiState.NeedsPermission

// ---------------------------------------------------------------------------------------------
// Dai tipi di foundation
// ---------------------------------------------------------------------------------------------

/** La versione che `AppUpdater.check` ha trovato, nei termini della vista. */
fun AvailableAppUpdate.toFluidUpdateRelease(): FluidUpdateRelease =
  FluidUpdateRelease(version = version, notes = changelog, sizeBytes = sizeBytes)

/**
 * Un passo di `AppUpdater.install` come stato della vista, per la versione [release] che si sta installando.
 *
 * E' l'unico posto che sa come i sei passi dell'installatore diventano i cinque della vista, cosi' le
 * app non lo riscrivono ognuna con la sua variante (e il suo `else`):
 *
 *  * `Downloading` senza peso (`totalBytes <= 0`) non ha percentuale: [FluidUpdateUiState.Downloading.progress] e' null;
 *  * `Verifying`, `Installing` e `AwaitingUserAction` sono i tre [FluidUpdateStage], col messaggio dell'installatore;
 *  * `Installed` e `Error` sono `Installed` e `Failed`; un `Error` per il permesso d'installare
 *    ([AppUpdateErrorKind.InstallPermission]) e' [FluidUpdateUiState.NeedsPermission] (3.0.1).
 */
fun AppUpdateInstallState.toFluidUpdateState(release: FluidUpdateRelease): FluidUpdateUiState = when (this) {
  is AppUpdateInstallState.Downloading -> FluidUpdateUiState.Downloading(
    release = release,
    progress = if (totalBytes > 0L) progress.coerceIn(0f, 1f) else null,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
  )
  is AppUpdateInstallState.Verifying -> FluidUpdateUiState.Working(release, FluidUpdateStage.Verifying, message)
  is AppUpdateInstallState.Installing -> FluidUpdateUiState.Working(release, FluidUpdateStage.Installing, message)
  is AppUpdateInstallState.AwaitingUserAction ->
    FluidUpdateUiState.Working(release, FluidUpdateStage.AwaitingUser, message)
  is AppUpdateInstallState.Installed -> FluidUpdateUiState.Installed(release)
  // Il permesso d'installare non e' un fallimento (3.0.1): Android lo chiede una volta, e la vista lo dice.
  is AppUpdateInstallState.Error -> if (kind == AppUpdateErrorKind.InstallPermission) {
    FluidUpdateUiState.NeedsPermission(release)
  } else {
    FluidUpdateUiState.Failed(release, message)
  }
}

// ---------------------------------------------------------------------------------------------
// Le parole
// ---------------------------------------------------------------------------------------------

/**
 * Le parole dell'aggiornamento, **in italiano di serie** (come `FluidAppearanceStrings`): un'app a una
 * lingua non scrive niente, una tradotta le passa tutte, e nessuna frase italiana resta in mezzo a una
 * pagina inglese. Le frasi con un numero dentro sono funzioni, perche' "1 minuto" e "2 minuti" non sono
 * la stessa frase con un numero diverso.
 */
@Immutable
data class FluidUpdateStrings(
  // L'avviso.
  val promptTitle: (version: String) -> String = { "Aggiornamento $it" },
  val availableMessage: (size: String?) -> String = { size ->
    if (size != null) "C'è una nuova versione da installare ($size)." else "C'è una nuova versione da installare."
  },
  val whatsNew: String = "Novità",
  val install: String = "Aggiorna",
  val retry: String = "Riprova",
  val later: String = "Più tardi",
  val skip: String = "Salta questa versione",
  val close: String = "Chiudi",
  val updating: String = "Aggiornamento in corso…",
  val openSettings: String = "Apri impostazioni",
  val permission: String = "Android chiede di consentire a questa app di installare aggiornamenti. Succede una volta sola.",
  val installed: String = "Aggiornamento installato.",
  val failedTitle: String = "Aggiornamento non riuscito",
  val verifying: String = "Verifica del file…",
  val installing: String = "Installazione…",
  val awaitingUser: String = "Conferma l'installazione nella finestra di Android.",
  val downloading: (percent: Int?, downloaded: String?, total: String?) -> String = { percent, downloaded, total ->
    when {
      percent != null && downloaded != null && total != null -> "Scaricati $downloaded di $total · $percent%"
      percent != null -> "Download in corso · $percent%"
      else -> "Download in corso…"
    }
  },
  // Le impostazioni.
  val sectionTitle: String = "Aggiornamenti",
  val currentVersion: String = "Versione installata",
  val channelTitle: String = "Canale",
  val channelStable: String = "Stabile",
  val channelStableDetail: String = "Le versioni collaudate.",
  val channelBeta: String = "Beta",
  val channelBetaDetail: String = "Escono prima e si rompono più spesso.",
  val betaToggle: String = "Versioni di prova",
  val betaToggleDetail: String = "Ricevi anche le beta: escono prima e si rompono più spesso.",
  val checkNow: String = "Controlla ora",
  val checking: String = "Sto controllando…",
  val upToDate: String = "App aggiornata",
  val upToDateDetail: String = "Hai la versione più recente del canale scelto.",
  val checkFailed: String = "Controllo non riuscito",
  val checkFailedDetail: String = "Non riesco a raggiungere l'elenco delle versioni.",
  val available: (version: String) -> String = { "Versione $it disponibile" },
  val installVersion: (version: String) -> String = { "Installa la $it" },
  val newBadge: String = "Nuova",
  val autoDownload: String = "Scarica in automatico",
  val autoDownloadDetail: String = "Scarica l'aggiornamento appena lo trova, senza chiedere.",
  val unsupported: String = "Aggiornamenti non disponibili",
  val unsupportedDetail: String = "Questa versione dell'app non si aggiorna da sola.",
  val lastCheckedNever: String = "Mai controllato",
  val lastCheckedNow: String = "Controllato adesso",
  val lastCheckedMinutes: (Int) -> String = { if (it == 1) "Controllato 1 minuto fa" else "Controllato $it minuti fa" },
  val lastCheckedHours: (Int) -> String = { if (it == 1) "Controllato 1 ora fa" else "Controllato $it ore fa" },
  val lastCheckedDays: (Int) -> String = { if (it == 1) "Controllato ieri" else "Controllato $it giorni fa" },
  // Dalla 3.0.1.
  val autoCheck: String = "Controlla all'avvio",
  val autoCheckDetail: String = "Cerca una versione nuova ogni volta che apri l'app.",
  val unknownError: String = "Errore sconosciuto",
)

// ---------------------------------------------------------------------------------------------
// La parte pura
// ---------------------------------------------------------------------------------------------

/**
 * Le funzioni pure dell'aggiornamento: dimensioni, percentuali, note, "controllato N minuti fa". Provate
 * sul computer (`FluidUpdateModelTest`), perche' sono i conti che ogni app faceva a modo suo e che si
 * sbagliano in silenzio — una percentuale che arriva a "100%" a un byte dalla fine, una dimensione di
 * "0,0 MB" per un file che il manifest non misura.
 */
object FluidUpdate {

  private const val Kilo = 1024L

  /**
   * Il peso in parole ("812 KB", "12,4 MB"), o **null se non si conosce** (`<= 0`): la riga che lo dice
   * sparisce, non diventa "0 B". Base 1024, come il resto dell'app; MB e oltre con un decimale.
   */
  fun sizeLabel(bytes: Long, locale: Locale = Locale.getDefault()): String? {
    if (bytes <= 0L) return null
    if (bytes < Kilo) return "$bytes B"
    val kb = bytes.toDouble() / Kilo
    if (kb < Kilo) return String.format(locale, "%.0f KB", kb)
    val mb = kb / Kilo
    if (mb < Kilo) return String.format(locale, "%.1f MB", mb)
    return String.format(locale, "%.1f GB", mb / Kilo)
  }

  /**
   * La percentuale intera di un avanzamento 0..1: **per difetto** e mai oltre 100. A 0,996 e' 99, non
   * 100: arrivare al cento prima di aver finito e' lo stesso difetto della barra ferma in fondo.
   */
  fun percent(progress: Float): Int = (progress.coerceIn(0f, 1f) * 100f).toInt().coerceIn(0, 100)

  /**
   * Le note di rilascio senza il Markdown: via il grassetto e le intestazioni, i punti elenco come "•".
   * Nel changelog dello store il grassetto serve, in una riga da quattro righe sono solo asterischi.
   */
  fun plainNotes(notes: String): String =
    notes
      .replace("\r\n", "\n")
      .lineSequence()
      .map { line ->
        line.trim()
          .replace("**", "")
          .replace("__", "")
          .replace(Regex("^#{1,6}\\s*"), "")
          .replace(Regex("^[-*]\\s+"), "• ")
      }
      .joinToString("\n")
      .replace(Regex("\n{3,}"), "\n\n")
      .trim()

  /** Il primo paragrafo delle note, ripulito: quello che sta in una riga di elenco. Vuoto se non ci sono note. */
  fun notesSummary(notes: String): String =
    plainNotes(notes).split(Regex("\n\\s*\n")).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

  /**
   * "Controllato 5 minuti fa", dalla data dell'ultimo controllo e da adesso.
   *
   * Sotto il minuto e' "adesso", fino a un'ora in minuti, fino a un giorno in ore, poi in giorni; una data
   * nel futuro (l'orologio del telefono e' stato spostato) vale "adesso", non "-3 minuti". Null o <= 0 e'
   * "mai controllato".
   */
  fun lastCheckLabel(
    lastCheckedAtMillis: Long?,
    nowMillis: Long,
    strings: FluidUpdateStrings = FluidUpdateStrings(),
  ): String {
    if (lastCheckedAtMillis == null || lastCheckedAtMillis <= 0L) return strings.lastCheckedNever
    val elapsed = (nowMillis - lastCheckedAtMillis).coerceAtLeast(0L)
    val minutes = (elapsed / 60_000L).toInt()
    val hours = (elapsed / 3_600_000L).toInt()
    val days = (elapsed / 86_400_000L).toInt()
    return when {
      minutes < 1 -> strings.lastCheckedNow
      hours < 1 -> strings.lastCheckedMinutes(minutes)
      days < 1 -> strings.lastCheckedHours(hours)
      else -> strings.lastCheckedDays(days)
    }
  }

  /**
   * La frase di stato di [state] per l'avviso e per la riga delle impostazioni: **una sola**, dalla stessa
   * tabella, cosi' i due posti non possono dire cose diverse della stessa installazione.
   * Per `Available`, `Idle`, `Checking`, `UpToDate` e `CheckFailed` non c'e' un avanzamento da dire:
   * nulla, e ci pensa chi disegna (la dimensione, "nessuna novita'").
   */
  fun progressText(
    state: FluidUpdateUiState,
    strings: FluidUpdateStrings = FluidUpdateStrings(),
    locale: Locale = Locale.getDefault(),
  ): String? = when (state) {
    is FluidUpdateUiState.Downloading -> strings.downloading(
      state.progress?.let(::percent),
      sizeLabel(state.downloadedBytes, locale)?.takeIf { state.totalBytes > 0L },
      sizeLabel(state.totalBytes, locale),
    )
    is FluidUpdateUiState.Working -> state.message.ifBlank {
      when (state.stage) {
        FluidUpdateStage.Verifying -> strings.verifying
        FluidUpdateStage.Installing -> strings.installing
        FluidUpdateStage.AwaitingUser -> strings.awaitingUser
      }
    }
    is FluidUpdateUiState.Installed -> strings.installed
    is FluidUpdateUiState.NeedsPermission -> strings.permission
    is FluidUpdateUiState.Failed -> state.message.ifBlank { strings.failedTitle }
    else -> null
  }
}
