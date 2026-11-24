package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable

/**
 * Le parole del testo che si muove (F8.1), **in italiano di serie** come il resto di [FluidLabels], che le porta in
 * `FluidLabels.liveText`. Le frasi con un segnaposto (`%1$s`) restano stringhe, così un'app le traduce come le sue
 * risorse.
 */
@Immutable
data class FluidLiveTextStrings(
  /** La parola in fondo a un paragrafo chiuso ([FluidExpandableText]), come l'App Store: «altro». */
  val more: String = "altro",
  /** L'azione di TalkBack che apre il paragrafo chiuso. */
  val expand: String = "Espandi",
  /** L'azione di TalkBack che lo richiude, se si può. */
  val collapse: String = "Riduci",
  /** Lo stato del paragrafo chiuso, per TalkBack. */
  val collapsed: String = "Testo ridotto",
  /** Lo stato del paragrafo aperto, per TalkBack. */
  val expanded: String = "Testo intero",
  /** Il tempo che manca di un conto alla rovescia, per TalkBack: `%1$s` è la durata in parole («Mancano 4 minuti»). */
  val remaining: String = "Mancano %1\$s",
  /** Il conto alla rovescia arrivato a zero, per TalkBack. */
  val expired: String = "Tempo scaduto",
  /** L'immagine che si sta caricando ([FluidAsyncImage]), per TalkBack, quando l'app non dà una descrizione. */
  val imageLoading: String = "Caricamento dell'immagine",
  /** La tessera di un'immagine che non è arrivata. */
  val imageFailed: String = "Immagine non caricata",
  /** Il tasto che riprova a caricarla. */
  val retry: String = "Riprova",
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidLiveTextStrings = FluidLiveTextStrings(
      more = "more",
      expand = "Expand",
      collapse = "Collapse",
      collapsed = "Text shortened",
      expanded = "Full text",
      remaining = "%1\$s left",
      expired = "Time's up",
      imageLoading = "Loading image",
      imageFailed = "Image not loaded",
      retry = "Retry",
    )
  }
}

/**
 * Le parole degli stati pronti di una pagina (F8.1, `stati-pronti`), **in italiano di serie**: `FluidLabels.contentStates`.
 * I titoli e le frasi sono quelli di `ContentUnavailableView` di Apple, detti come li direbbe una persona.
 */
@Immutable
data class FluidContentStateStrings(
  /** Il titolo di una ricerca senza risultati: `%1$s` è ciò che si è cercato («Nessun risultato per “cena”»). */
  val noResultsFor: String = "Nessun risultato per «%1\$s»",
  /** Lo stesso titolo senza parola cercata. */
  val noResults: String = "Nessun risultato",
  /** La frase sotto, come `ContentUnavailableView.search`: «Check the spelling or try a new search.» */
  val noResultsDetail: String = "Controlla come l'hai scritto o prova un'altra ricerca.",
  /** Il titolo senza rete. */
  val offline: String = "Sei offline",
  /** La frase sotto: la pagina riprova da sola quando torna la rete. */
  val offlineDetail: String = "Quando torna la connessione la pagina si aggiorna da sola.",
  /** Il titolo di un errore. */
  val error: String = "Qualcosa non ha funzionato",
  /** La frase sotto, quando l'app non ne dà una sua. */
  val errorDetail: String = "Non è colpa tua. Puoi riprovare fra un momento.",
  /** Il tasto che riprova. */
  val retry: String = "Riprova",
  /** Quello che dice TalkBack mentre riprova. */
  val retrying: String = "Nuovo tentativo in corso…",
  /** Quando il nuovo tentativo è riuscito. */
  val retried: String = "Fatto",
  /** Quando anche il nuovo tentativo non è riuscito. */
  val retryFailed: String = "Non è riuscito",
  /** Il titolo di un permesso che manca. */
  val permission: String = "Serve un permesso",
  /** La frase sotto, quando l'app non dice quale. */
  val permissionDetail: String = "Per mostrare questa pagina serve un permesso che ora è spento.",
  /** Il tasto che porta alle impostazioni. */
  val openSettings: String = "Apri le impostazioni",
  /** Il titolo di una pagina che non ha ancora niente. */
  val empty: String = "Ancora niente qui",
  /** La frase sotto. */
  val emptyDetail: String = "Quando ci sarà qualcosa, lo troverai qui.",
  /** Quello che dice TalkBack mentre la pagina carica ([FluidContentState]). */
  val loading: String = "Caricamento",
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidContentStateStrings = FluidContentStateStrings(
      noResultsFor = "No Results for “%1\$s”",
      noResults = "No Results",
      noResultsDetail = "Check the spelling or try a new search.",
      offline = "You're Offline",
      offlineDetail = "This page updates by itself when the connection is back.",
      error = "Something Went Wrong",
      errorDetail = "It's not your fault. You can try again in a moment.",
      retry = "Try Again",
      retrying = "Trying again…",
      retried = "Done",
      retryFailed = "It didn't work",
      permission = "Permission Needed",
      permissionDetail = "This page needs a permission that is turned off.",
      openSettings = "Open Settings",
      empty = "Nothing Here Yet",
      emptyDetail = "When there's something, you'll find it here.",
      loading = "Loading",
    )
  }
}
