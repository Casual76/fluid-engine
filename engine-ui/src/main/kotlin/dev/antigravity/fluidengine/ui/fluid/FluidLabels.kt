package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Le parole che l'engine dice da se', quelle che nessun parametro passava (3.0.1): i nomi per TalkBack di
 * tasti e pannelli che i componenti disegnano senza chiedere niente all'app — la X di una pagina intera,
 * l'occhio di un campo segreto, il tasto indietro della barra, il titolo di un menu di azioni, le azioni di
 * TalkBack di una notifica e dell'indice di sezione.
 *
 * **In italiano di serie**, come `FluidSearchLabels`, `FluidUpdateStrings` e `FluidAppearanceStrings`: un'app
 * in una lingua sola non scrive niente, una tradotta le passa una volta alla radice e valgono per tutto
 * l'albero, menu e pop-up compresi (gli host le leggono dove sono composti):
 *
 * ```kotlin
 * CompositionLocalProvider(LocalFluidLabels provides FluidLabels(
 *   close = stringResource(R.string.close),
 *   back = stringResource(R.string.back),
 *   …
 * )) { App() }
 * ```
 *
 * Un componente non legge le risorse da se': non sa in che lingua e' l'app, e un engine che indovinasse
 * dalla lingua del telefono metterebbe frasi inglesi in un'app italiana su un telefono inglese. Le passa
 * l'app, e le passa **tutte**: una sola lasciata di serie e' una frase italiana in una pagina inglese.
 *
 * Dove un componente ha gia' un parametro per la stessa parola (`FluidPromptAlert(cancelLabel)`,
 * `FluidSlider(contentDescription)`, `FluidSearchLabels`) vince il parametro: queste sono solo le parole
 * che prima erano scritte dentro il componente.
 */
@Immutable
data class FluidLabels(
  /** La X che chiude una pagina intera (`FluidGlassModalPresentation.FullScreen`). */
  val close: String = "Chiudi",
  /** L'occhio di `FluidTextField(secret = true)` col testo nascosto: dice cosa fara' toccandolo. */
  val showSecret: String = "Mostra il testo",
  /** L'occhio di `FluidTextField(secret = true)` col testo in chiaro. */
  val hideSecret: String = "Nascondi il testo",
  /** Il titolo per TalkBack di un menu di azioni (contestuale, del tasto, della barra) che non ne ha uno. */
  val actions: String = "Azioni",
  /** Il tasto indietro della barra (`FluidScreen`, `FluidPageChrome`, la barra dell'host, `FluidTopBar`). */
  val back: String = "Indietro",
  /** Il tocco sul titolo della barra che riporta la lista in cima. */
  val scrollToTop: String = "Torna all'inizio",
  /** Il pannello di una notifica in-app, per TalkBack. */
  val notification: String = "Notifica in-app",
  /** L'azione di TalkBack che apre una notifica (come tirarla giu'). */
  val notificationExpand: String = "Espandi",
  /** L'azione di TalkBack che congeda una notifica. */
  val notificationDismiss: String = "Chiudi",
  /** La X di una notifica aperta. */
  val notificationClose: String = "Chiudi notifica",
  /** L'indice di sezione (`FluidSectionIndex`), per TalkBack. */
  val sectionIndex: String = "Indice sezioni",
  val previousSection: String = "Sezione precedente",
  val nextSection: String = "Sezione successiva",
  /** Dove si e' nell'indice: la sezione, la sua posizione da 1 e quante sono («Marzo, 3 di 12»). */
  val sectionPosition: (label: String, position: Int, total: Int) -> String = { label, position, total ->
    "$label, $position di $total"
  },
  /** La X che nasconde un messaggio (`FluidInlineMessage(onDismiss)`). */
  val hide: String = "Nascondi",
  /** Il tasto che svuota un campo di testo (`FluidTextField`, su una riga): per TalkBack. */
  val clear: String = "Cancella",
  /** Il titolo di un `FluidInlineMessage` che non ne riceve uno (3.0.4). */
  val message: String = "Messaggio",
  /** Le frasi dell'indicatore di sincronizzazione, dell'avviso e del suo dettaglio (3.0.4). */
  val sync: FluidSyncStrings = FluidSyncStrings(),
  // --- pm-f1/gb ---
  /** Lo stato di un avatar o di un segno di selezione che e' scelto, per TalkBack (`FluidAvatar(selected = true)`). */
  val selected: String = "Selezionato",
  /** Lo stato di un avatar che puo' essere scelto e non lo e'. */
  val notSelected: String = "Non selezionato",
  /**
   * Il resto di un mosaico di avatar oltre le celle che si vedono, con `%1$d` per quante persone mancano:
   * «e altri 3» (`fluidAvatarStackDescription`). Una stringa con un segnaposto e non una funzione, cosi'
   * un'app la traduce come tutte le altre risorse.
   */
  val moreAvatars: String = "e altri %1\$d",
  // --- fine pm-f1/gb ---
  // --- pm-f1/ge ---
  /** Il titolo per TalkBack di un pannello laterale (`FluidDrawer`) che non ne riceve uno: l'annuncio all'apertura. */
  val drawer: String = "Pannello laterale",
  /** L'azione di TalkBack che chiude il pannello laterale (lo stesso del tocco fuori e del back). */
  val closeDrawer: String = "Chiudi il pannello",
  /** Lo stato di una zona della barra col pallino (`FluidTabBadge.Dot`): «Chat, novita'». */
  val tabBadgeNew: String = "Novità",
  /**
   * Lo stato di una zona della barra con un numero, con `%1$d` per la quantita' vera: «Posta, 3 novita'».
   * «Novita'» e' invariabile, quindi la stessa frase va bene per uno e per cento.
   */
  val tabBadgeCount: String = "%1\$d novità",
  // --- fine pm-f1/ge ---
  // --- pm-f1/gf1 ---
  /** Stato accessibile di un gettone rifiutato dal validatore dell'app. */
  val tokenInvalid: String = "non valido",
  /** Azione accessibile sul nodo del gettone. */
  val tokenRemove: String = "Rimuovi",
  /** Modifica opzionale del gettone, implementata dal chiamante. */
  val tokenEdit: String = "Modifica",
  /** Conteggio sul nodo di input, anche quando il testo e' vuoto. */
  val tokenCount: String = "Gettoni",
  // --- fine pm-f1/gf1 ---
  // --- pm-f1/gf2 ---
  /** Calendar strings are flat so existing translation audits cover every added word. */
  val dateTimeTitle: String = FluidDateTimeLabels().title,
  val dateTimeChoose: String = FluidDateTimeLabels().choose,
  val dateTimeConfirm: String = FluidDateTimeLabels().confirm,
  val dateTimeCancel: String = FluidDateTimeLabels().cancel,
  val dateTimePreviousMonth: String = FluidDateTimeLabels().previousMonth,
  val dateTimeNextMonth: String = FluidDateTimeLabels().nextMonth,
  val dateTimeHour: String = FluidDateTimeLabels().hour,
  val dateTimeMinute: String = FluidDateTimeLabels().minute,
  val dateTimeIncrease: String = FluidDateTimeLabels().increase,
  val dateTimeDecrease: String = FluidDateTimeLabels().decrease,
  val dateTimeGap: String = FluidDateTimeLabels().gap,
  val dateTimeOverlap: String = FluidDateTimeLabels().overlap,
  val dateTimeInvalid: String = FluidDateTimeLabels().invalid,
  // --- fine pm-f1/gf2 ---
  // --- pm-f1/gg1 ---
  // I grafici (`FluidDonutChart`, `FluidBarChart`, `FluidLineChart`). Stringhe con segnaposto posizionali e non
  // funzioni, come `moreAvatars`: un'app le traduce come tutte le altre risorse, e i numeri e le percentuali che
  // ci finiscono dentro sono gia' scritti nella lingua del telefono.
  /** Lo stato vuoto di un grafico: nessuna voce, o tutte a zero. */
  val chartNoData: String = "Nessun dato nel periodo",
  /** Lo stato di un grafico i cui dati non si possono disegnare senza mentire (valore negativo, non numerico, oltre la scala). */
  val chartInvalidData: String = "Dati non validi: il grafico non viene disegnato",
  /** Lo stato di una ripartizione che non somma al totale dichiarato: `%1$s` e' la somma, `%2$s` il totale atteso. */
  val chartInvalidSum: String = "I valori sommano a %1\$s invece di %2\$s: il grafico non viene disegnato",
  /** La fetta in cui confluiscono le fette minuscole di una ciambella. */
  val chartOther: String = "Altro",
  /** Cio' che TalkBack dice di un grafico che sta arrivando (lo scheletro). */
  val chartLoading: String = "Caricamento del grafico",
  /** La descrizione di una ciambella: `%1$d` e' il numero di parti. Segue [chartLargest]. */
  val chartDonutSummary: String = "Ripartizione in %1\$d parti.",
  /** La parte piu' grande di una ripartizione: `%1$s` etichetta, `%2$s` percentuale. */
  val chartLargest: String = "La più grande: %1\$s, %2\$s.",
  /** La descrizione di un grafico a barre: `%1$d` e' il numero di voci. */
  val chartBarsSummary: String = "Grafico a barre, %1\$d voci.",
  /** La descrizione di un grafico a barre impilate: `%1$d` e' il numero di voci. */
  val chartStackedSummary: String = "Grafico a barre impilate, %1\$d voci.",
  /** Il numero di serie, aggiunto quando sono piu' d'una: `%1$d`. */
  val chartSeriesCount: String = "%1\$d serie.",
  /** La voce con il valore piu' alto: `%1$s` etichetta, `%2$s` valore. */
  val chartHighest: String = "Il valore più alto: %1\$s, %2\$s.",
  /** La descrizione di un andamento: `%1$d` punti, `%2$s` la prima etichetta, `%3$s` l'ultima. */
  val chartLineSummary: String = "Andamento, %1\$d punti, da %2\$s a %3\$s.",
  /** Il minimo e il massimo di un andamento: `%1$s` e `%2$s`. */
  val chartRange: String = "Minimo %1\$s, massimo %2\$s.",
  /** Il denominatore o la scala dichiarati da chi chiama: `%1$s`. */
  val chartScale: String = "Scala: %1\$s.",
  /** Una fetta, letta da TalkBack: `%1$s` etichetta, `%2$s` valore, `%3$s` percentuale. */
  val chartSliceItem: String = "%1\$s: %2\$s, %3\$s",
  /** Una barra o un punto, letto da TalkBack: `%1$s` etichetta, `%2$s` i valori delle serie. */
  val chartGroupItem: String = "%1\$s: %2\$s",
  /** Un punto senza dato (un buco nella serie), letto da TalkBack. */
  val chartNoValue: String = "nessun dato",
  // --- fine pm-f1/gg1 ---
  // --- pm-f1/gg2 ---
  /** Parole dei file: campi semplici per consentire traduzioni e controlli automatici. */
  val fileRemote: String = "Da scaricare",
  val fileTransferring: String = "In corso",
  val fileReady: String = "Scaricato",
  val fileFailed: String = "Errore",
  val fileCancel: String = "Annulla trasferimento",
  val fileRetry: String = "Riprova",
  val fileRemove: String = "Rimuovi file",
  val fileImage: String = "Immagine",
  val fileDocument: String = "Documento",
  val fileSpreadsheet: String = "Foglio di calcolo",
  val filePresentation: String = "Presentazione",
  val fileArchive: String = "Archivio",
  val fileAudio: String = "File audio",
  val fileVideo: String = "File video",
  val fileText: String = "Testo o codice",
  val fileExecutable: String = "Eseguibile o pacchetto, attenzione",
  val fileOther: String = "File",
  // --- fine pm-f1/gg2 ---
) {
  // --- pm-f1/gf2 ---
  /** A grouped view of the calendar vocabulary, without another stored translation object. */
  val dateTime: FluidDateTimeLabels get() = FluidDateTimeLabels(
    title = dateTimeTitle,
    choose = dateTimeChoose,
    confirm = dateTimeConfirm,
    cancel = dateTimeCancel,
    previousMonth = dateTimePreviousMonth,
    nextMonth = dateTimeNextMonth,
    hour = dateTimeHour,
    minute = dateTimeMinute,
    increase = dateTimeIncrease,
    decrease = dateTimeDecrease,
    gap = dateTimeGap,
    overlap = dateTimeOverlap,
    invalid = dateTimeInvalid,
  )
  // --- fine pm-f1/gf2 ---
  companion object {
    /**
     * Le stesse parole in inglese (3.0.4): un'app inglese passa `FluidLabels.english()` alla radice e non
     * scrive niente; una in un'altra lingua parte da questa con `copy(...)` o le scrive tutte.
     */
    fun english(): FluidLabels = FluidLabels(
      close = "Close",
      showSecret = "Show text",
      hideSecret = "Hide text",
      actions = "Actions",
      back = "Back",
      scrollToTop = "Back to top",
      notification = "In-app notification",
      notificationExpand = "Expand",
      notificationDismiss = "Dismiss",
      notificationClose = "Close notification",
      sectionIndex = "Section index",
      previousSection = "Previous section",
      nextSection = "Next section",
      sectionPosition = { label, position, total -> "$label, $position of $total" },
      hide = "Hide",
      clear = "Clear",
      message = "Message",
      sync = FluidSyncStrings.english(),
      // --- pm-f1/gb ---
      selected = "Selected",
      notSelected = "Not selected",
      moreAvatars = "and %1\$d more",
      // --- fine pm-f1/gb ---
      // --- pm-f1/ge ---
      drawer = "Side panel",
      closeDrawer = "Close panel",
      tabBadgeNew = "New",
      tabBadgeCount = "%1\$d new",
      // --- fine pm-f1/ge ---
      // --- pm-f1/gf1 ---
      tokenInvalid = "invalid",
      tokenRemove = "Remove",
      tokenEdit = "Edit",
      tokenCount = "Tokens",
      // --- fine pm-f1/gf1 ---
      // --- pm-f1/gf2 ---
      dateTimeTitle = FluidDateTimeLabels.english().title,
      dateTimeChoose = FluidDateTimeLabels.english().choose,
      dateTimeConfirm = FluidDateTimeLabels.english().confirm,
      dateTimeCancel = FluidDateTimeLabels.english().cancel,
      dateTimePreviousMonth = FluidDateTimeLabels.english().previousMonth,
      dateTimeNextMonth = FluidDateTimeLabels.english().nextMonth,
      dateTimeHour = FluidDateTimeLabels.english().hour,
      dateTimeMinute = FluidDateTimeLabels.english().minute,
      dateTimeIncrease = FluidDateTimeLabels.english().increase,
      dateTimeDecrease = FluidDateTimeLabels.english().decrease,
      dateTimeGap = FluidDateTimeLabels.english().gap,
      dateTimeOverlap = FluidDateTimeLabels.english().overlap,
      dateTimeInvalid = FluidDateTimeLabels.english().invalid,
      // --- fine pm-f1/gf2 ---
      // --- pm-f1/gg1 ---
      chartNoData = "No data for this period",
      chartInvalidData = "Invalid data: the chart is not drawn",
      chartInvalidSum = "The values add up to %1\$s instead of %2\$s: the chart is not drawn",
      chartOther = "Other",
      chartLoading = "Loading chart",
      chartDonutSummary = "Breakdown in %1\$d parts.",
      chartLargest = "The largest: %1\$s, %2\$s.",
      chartBarsSummary = "Bar chart, %1\$d items.",
      chartStackedSummary = "Stacked bar chart, %1\$d items.",
      chartSeriesCount = "%1\$d series.",
      chartHighest = "Highest value: %1\$s, %2\$s.",
      chartLineSummary = "Trend, %1\$d points, from %2\$s to %3\$s.",
      chartRange = "Minimum %1\$s, maximum %2\$s.",
      chartScale = "Scale: %1\$s.",
      chartSliceItem = "%1\$s: %2\$s (%3\$s)",
      chartGroupItem = "%1\$s – %2\$s",
      chartNoValue = "no data",
      // --- fine pm-f1/gg1 ---
      // --- pm-f1/gg2 ---
      fileRemote = "Not downloaded",
      fileTransferring = "Transferring",
      fileReady = "Downloaded",
      fileFailed = "Error",
      fileCancel = "Cancel transfer",
      fileRetry = "Retry",
      fileRemove = "Remove file",
      fileImage = "Image",
      fileDocument = "Document",
      fileSpreadsheet = "Spreadsheet",
      filePresentation = "Presentation",
      fileArchive = "Archive",
      fileAudio = "Audio file",
      fileVideo = "Video file",
      fileText = "Text or code",
      fileExecutable = "Executable or package, warning",
      fileOther = "Unknown file",
      // --- fine pm-f1/gg2 ---
    )
  }
}

/**
 * Le frasi della sincronizzazione che prima erano scritte dentro `FluidSyncIndicator`, `FluidSyncNotice`,
 * `FluidSyncAction` e `lastSyncLabel` (3.0.4): in italiano di serie, come il resto di [FluidLabels], che le
 * porta in `FluidLabels.sync`. Le due frasi che nominano «la scuola» sono quelle del dominio di
 * ClasseViva: un'app con un'altra fonte le riscrive.
 */
@Immutable
data class FluidSyncStrings(
  /** Il nome per TalkBack dell'indicatore: mai completata, completata, in corso, parziale, fallita. */
  val neverCompleted: String = "Sincronizzazione mai completata",
  val completed: String = "Sincronizzazione completata",
  val inProgress: String = "Aggiornamento in corso",
  val partial: String = "Sincronizzazione parziale",
  val failed: String = "Sincronizzazione non riuscita",
  /** Il titolo dell'avviso e del dettaglio quando non c'e' rete. */
  val offline: String = "Nessuna connessione",
  /** Il titolo dell'avviso per un aggiornamento fallito o parziale. */
  val updateFailed: String = "Aggiornamento non riuscito",
  /** Il titolo dell'avviso quando la fonte non pubblica ancora i dati. */
  val dataUnavailable: String = "Dati non disponibili",
  /** Cosa si puo' fare quando la fonte non ha ancora aperto l'anno (segue la frase della fonte). */
  val noticeGuidance: String = "Finché la scuola non lo apre, le sezioni legate all'anno restano vuote. " +
    "Puoi consultare l'anno precedente dal selettore in Impostazioni.",
  /** Il tasto dell'avviso. */
  val retry: String = "Riprova",
  /** Il tasto del dettaglio. */
  val retryNow: String = "Riprova adesso",
  /** Il titolo del foglio col dettaglio. */
  val detailsTitle: String = "Sincronizzazione",
  /** Il titolo del messaggio d'errore nel dettaglio (per i guasti diversi dall'assenza di rete). */
  val detail: String = "Dettaglio",
  /** Le sezioni che non sono arrivate, gia' unite da una virgola. */
  val notUpdated: (sections: String) -> String = { "Non aggiornate: $it" },
  /** La frase del dettaglio quando l'anno scelto non e' ancora aperto dalla fonte. */
  val yearNotOpen: String = "L'anno selezionato non è ancora stato aperto dalla scuola: i dati mostrati sono quelli dell'anno precedente.",
  val allUpToDate: String = "Tutti i dati risultano aggiornati.",
  // `lastSyncLabel`.
  val never: String = "Mai aggiornato",
  val justNow: String = "Aggiornato adesso",
  val minutesAgo: (Int) -> String = { "Aggiornato $it min fa" },
  val hoursAgo: (Int) -> String = { "Aggiornato $it h fa" },
  /** Dopo il primo giorno: la data e l'ora, gia' formattate. */
  val updatedOn: (date: String) -> String = { "Aggiornato il $it" },
) {
  companion object {
    /** Le stesse frasi in inglese (3.0.4). */
    fun english(): FluidSyncStrings = FluidSyncStrings(
      neverCompleted = "Sync never completed",
      completed = "Sync completed",
      inProgress = "Updating",
      partial = "Sync partially done",
      failed = "Sync failed",
      offline = "No connection",
      updateFailed = "Update failed",
      dataUnavailable = "Data not available",
      noticeGuidance = "Until the school opens it, the sections tied to the year stay empty. " +
        "You can look at the previous year from the picker in Settings.",
      retry = "Retry",
      retryNow = "Retry now",
      detailsTitle = "Sync",
      detail = "Detail",
      notUpdated = { "Not updated: $it" },
      yearNotOpen = "The selected year has not been opened by the school yet: the data shown is from the previous year.",
      allUpToDate = "All data is up to date.",
      never = "Never updated",
      justNow = "Updated just now",
      minutesAgo = { "Updated $it min ago" },
      hoursAgo = { "Updated $it h ago" },
      updatedOn = { "Updated on $it" },
    )
  }
}

/** Le parole dell'engine in scope: di serie quelle italiane. Vedi [FluidLabels]. */
val LocalFluidLabels: ProvidableCompositionLocal<FluidLabels> = staticCompositionLocalOf { FluidLabels() }

// --- pm-f1/gf2 ---
/** Translatable calendar controls, kept together so callers can supply other languages. */
@Immutable
data class FluidDateTimeLabels(
  val title: String = "Data e ora",
  val choose: String = "Scegli data e ora",
  val confirm: String = "Conferma",
  val cancel: String = "Annulla",
  val previousMonth: String = "Mese precedente",
  val nextMonth: String = "Mese successivo",
  val hour: String = "Ora",
  val minute: String = "Minuti",
  val increase: String = "Aumenta",
  val decrease: String = "Diminuisci",
  val gap: String = "Questo orario non esiste nel fuso scelto. Scegli un altro orario.",
  val overlap: String = "Questo orario ricorre due volte. Scegli l'offset UTC.",
  val invalid: String = "Scegli un orario futuro entro i limiti consentiti.",
) {
  companion object {
    /** Complete English vocabulary; dates themselves use the supplied locale. */
    fun english() = FluidDateTimeLabels(
      title = "Date and time", choose = "Choose date and time", confirm = "Confirm", cancel = "Cancel",
      previousMonth = "Previous month", nextMonth = "Next month", hour = "Hour", minute = "Minutes",
      increase = "Increase", decrease = "Decrease",
      gap = "This time does not exist in the chosen time zone. Choose another time.",
      overlap = "This time occurs twice. Choose the UTC offset.",
      invalid = "Choose a future time within the allowed limits.",
    )
  }
}
// --- fine pm-f1/gf2 ---
// --- pm-f1/gg2 ---
// PDF e un acronimo invariabile; le parole traducibili restano tutte nei campi di FluidLabels.
internal fun FluidLabels.fileKind(kind: FluidFileKind): String = when (kind) {
  FluidFileKind.Image -> fileImage
  FluidFileKind.Pdf -> "PDF"
  FluidFileKind.Document -> fileDocument
  FluidFileKind.Spreadsheet -> fileSpreadsheet
  FluidFileKind.Presentation -> filePresentation
  FluidFileKind.Archive -> fileArchive
  FluidFileKind.Audio -> fileAudio
  FluidFileKind.Video -> fileVideo
  FluidFileKind.Text -> fileText
  FluidFileKind.Executable -> fileExecutable
  FluidFileKind.Other -> fileOther
}
// --- fine pm-f1/gg2 ---
