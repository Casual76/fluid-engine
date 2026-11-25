package dev.antigravity.fluidengine.media

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Le parole del selettore e del pannello degli allegati (F8.14). Di serie in italiano; [english] e' la versione inglese
 * completa. Un'app in un'altra lingua ne da' una sua con [LocalFluidMediaLabels].
 */
@Immutable
data class FluidMediaLabels(
  val photosTab: String = "Foto",
  val albumsTab: String = "Album",
  val cancel: String = "Annulla",
  val back: String = "Indietro",
  val add: String = "Aggiungi",
  val search: String = "Cerca",
  val searchPlaceholder: String = "Cerca nelle foto",
  val semanticPlaceholder: String = "Cerca: «mare d'estate», «Anna al parco»",
  val searchIn: (sourceLabel: String) -> String = { "Cerca in $it" },
  val noResults: String = "Nessun risultato",
  val noResultsHint: String = "Prova con altre parole.",
  val empty: String = "Nessuna foto",
  val emptyHint: String = "Qui non ci sono foto o video.",
  /** Il titolo con la scelta: «1 foto selezionata», «3 elementi selezionati». */
  val selected: (photos: Int, videos: Int) -> String = { p, v ->
    val n = p + v
    when {
      v == 0 && p == 1 -> "1 foto selezionata"
      v == 0 -> "$p foto selezionate"
      p == 0 && v == 1 -> "1 video selezionato"
      p == 0 -> "$v video selezionati"
      else -> "$n elementi selezionati"
    }
  },
  /** Il tasto che conferma, con il numero: «Aggiungi (3)». */
  val addCount: (Int) -> String = { "Aggiungi ($it)" },
  val limitReached: (Int) -> String = { if (it == 1) "Puoi scegliere un elemento" else "Puoi sceglierne al massimo $it" },
  val video: String = "Video",
  val photo: String = "Foto",
  val backupOnly: String = "Solo nel backup",
  val favorite: String = "Preferito",
  /** La durata di un video per TalkBack: «Video, 1 minuto e 5 secondi». */
  val duration: (minutes: Long, seconds: Long) -> String = { m, s ->
    when {
      m == 0L -> if (s == 1L) "1 secondo" else "$s secondi"
      s == 0L -> if (m == 1L) "1 minuto" else "$m minuti"
      else -> "${if (m == 1L) "1 minuto" else "$m minuti"} e ${if (s == 1L) "1 secondo" else "$s secondi"}"
    }
  },
  val preview: String = "Anteprima",
  val previewHint: String = "Tieni premuto per l'anteprima",
  val select: String = "Seleziona",
  val deselect: String = "Togli dalla scelta",
  val position: (index: Int, count: Int) -> String = { i, c -> "$i di $c" },
  // L'accesso
  val limitedTitle: String = "Solo le foto che hai scelto",
  val limitedAction: String = "Gestisci",
  val notAllowedTitle: String = "Consenti l'accesso alle foto",
  val notAllowedDetail: String = "Per scegliere dalla libreria, consenti l'accesso alle foto e ai video. Puoi anche scegliere con il selettore di Android, senza permessi.",
  val allowAccess: String = "Consenti l'accesso",
  val openSettings: String = "Apri Impostazioni",
  val systemPicker: String = "Scegli con Android",
  val permissionReason: String = "Per mostrarti le tue foto e i tuoi video qui, e sceglierli senza uscire.",
  val lockedTitle: (sourceLabel: String) -> String = { "$it è bloccata" },
  val lockedDetail: String = "Sbloccala per vedere le sue foto qui.",
  val unlock: String = "Sblocca",
  val unavailableTitle: (sourceLabel: String) -> String = { "$it non è disponibile" },
  val unavailableDetail: String = "Riprova fra poco.",
  val retry: String = "Riprova",
  // La consegna
  val preparing: (Int) -> String = { if (it == 1) "Preparo 1 elemento…" else "Preparo $it elementi…" },
  val refusedTitle: String = "Non è stato possibile aggiungere",
  val refusedUnreachable: String = "Il backup non risponde: le foto che stanno solo nel backup non si possono avere adesso.",
  val refusedTooMany: (Int) -> String = { "Al massimo $it elementi alla volta." },
  val refusedNothing: String = "Gli elementi scelti non ci sono più.",
  val refusedLocation: String = "Non è stato possibile togliere la posizione da una foto: non è stato aggiunto niente.",
  val refusedFailed: String = "Qualcosa non è andato. Riprova.",
  val skipped: (Int) -> String = { if (it == 1) "1 elemento non c'era più." else "$it elementi non c'erano più." },
  val ok: String = "OK",
  // Il pannello degli allegati
  val panelTitle: String = "Allegati",
  val recents: String = "Recenti",
  val allPhotos: String = "Tutte le foto",
  val camera: String = "Fotocamera",
  val photos: String = "Foto",
  val files: String = "File",
  val location: String = "Posizione",
  val audio: String = "Audio",
  val attached: String = "Allegata",
  val attach: String = "Allega",
) {
  companion object {
    /** Le parole in inglese. */
    fun english(): FluidMediaLabels = FluidMediaLabels(
      photosTab = "Photos",
      albumsTab = "Collections",
      cancel = "Cancel",
      back = "Back",
      add = "Add",
      search = "Search",
      searchPlaceholder = "Search photos",
      semanticPlaceholder = "Search: “beach in summer”, “Anna at the park”",
      searchIn = { "Search in $it" },
      noResults = "No results",
      noResultsHint = "Try other words.",
      empty = "No photos",
      emptyHint = "There are no photos or videos here.",
      selected = { p, v ->
        val n = p + v
        when {
          v == 0 -> if (p == 1) "1 photo selected" else "$p photos selected"
          p == 0 -> if (v == 1) "1 video selected" else "$v videos selected"
          else -> "$n items selected"
        }
      },
      addCount = { "Add ($it)" },
      limitReached = { if (it == 1) "You can choose one item" else "You can choose up to $it" },
      video = "Video",
      photo = "Photo",
      backupOnly = "Only in backup",
      favorite = "Favorite",
      duration = { m, s ->
        when {
          m == 0L -> if (s == 1L) "1 second" else "$s seconds"
          s == 0L -> if (m == 1L) "1 minute" else "$m minutes"
          else -> "${if (m == 1L) "1 minute" else "$m minutes"} ${if (s == 1L) "1 second" else "$s seconds"}"
        }
      },
      preview = "Preview",
      previewHint = "Touch and hold to preview",
      select = "Select",
      deselect = "Remove from selection",
      position = { i, c -> "$i of $c" },
      limitedTitle = "Only the photos you chose",
      limitedAction = "Manage",
      notAllowedTitle = "Allow access to photos",
      notAllowedDetail = "To choose from your library, allow access to photos and videos. You can also choose with the Android picker, without permissions.",
      allowAccess = "Allow access",
      openSettings = "Open Settings",
      systemPicker = "Choose with Android",
      permissionReason = "To show your photos and videos here, and choose them without leaving.",
      lockedTitle = { "$it is locked" },
      lockedDetail = "Unlock it to see its photos here.",
      unlock = "Unlock",
      unavailableTitle = { "$it is not available" },
      unavailableDetail = "Try again in a moment.",
      retry = "Try again",
      preparing = { if (it == 1) "Preparing 1 item…" else "Preparing $it items…" },
      refusedTitle = "Couldn't add",
      refusedUnreachable = "The backup isn't responding: photos that are only in the backup can't be added right now.",
      refusedTooMany = { "Up to $it items at a time." },
      refusedNothing = "The chosen items are gone.",
      refusedLocation = "The location couldn't be removed from a photo: nothing was added.",
      refusedFailed = "Something went wrong. Try again.",
      skipped = { if (it == 1) "1 item was gone." else "$it items were gone." },
      ok = "OK",
      panelTitle = "Attachments",
      recents = "Recents",
      allPhotos = "All Photos",
      camera = "Camera",
      photos = "Photos",
      files = "Files",
      location = "Location",
      audio = "Audio",
      attached = "Attached",
      attach = "Attach",
    )
  }
}

/** Le parole del selettore e del pannello ([FluidMediaLabels]). */
val LocalFluidMediaLabels = staticCompositionLocalOf { FluidMediaLabels() }
