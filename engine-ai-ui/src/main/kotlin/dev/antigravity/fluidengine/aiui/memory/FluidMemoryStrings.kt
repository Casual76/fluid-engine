package dev.antigravity.fluidengine.aiui.memory

import dev.antigravity.fluidengine.ai.memory.AiMemory
import dev.antigravity.fluidengine.ai.memory.AiMemoryExclusion
import dev.antigravity.fluidengine.ai.memory.AiMemoryNotice
import dev.antigravity.fluidengine.ai.memory.AiMemorySource
import java.time.Instant
import java.time.ZoneId

/**
 * Le parole della memoria (F9.3, F9.3b): la card «Vuoi che lo ricordi?» o «Ho ricordato…» e la pagina «Cosa ricordo di
 * te». Di serie in italiano;
 * [english] per un'app in inglese. Le frasi con `%s` ricevono il nome dell'app o il testo.
 *
 * I mesi sono scritti qui e non chiesti a `java.time`: su Android 16 il formato italiano di `java.time` mette la
 * maiuscola al mese («24 Nov»), e la data in una riga deve leggersi come il resto della frase.
 */
data class FluidMemoryStrings(
  val remembered: String = "Ho ricordato",
  val updated: String = "Ho aggiornato",
  val kept: String = "Tengo il più recente",
  val undone: String = "Non lo ricordo più",
  val undo: String = "Annulla",
  val previous: String = "prima: %s",
  val notEntered: String = "non entra: %s",
  val fromApp: String = "da %s",
  val pageTitle: String = "Cosa ricordo di te",
  val pageSubtitle: String = "Entra in una domanda solo quando c'entra. Resta sul telefono, cifrato.",
  val sectionYou: String = "Da te",
  val sectionApp: String = "Da %s",
  val sectionImported: String = "Dalla memoria di prima",
  val emptyTitle: String = "Niente per ora",
  val emptyMessage: String = "Quando dici qualcosa di te (la classe, la città, cosa non mangi) lo ricordo qui, e te lo dico.",
  val add: String = "Aggiungi",
  val edit: String = "Modifica",
  val pin: String = "Fissa: sempre nelle domande",
  val unpin: String = "Non fissare",
  val forget: String = "Dimentica",
  val forgetAll: String = "Dimentica tutto",
  val forgetAllTitle: String = "Dimenticare tutto?",
  val forgetAllMessage: String = "Non saprò più niente di quello che ricordo di te.",
  val forgetApp: String = "Dimentica quelli di %s",
  val forgetAppTitle: String = "Dimenticare quelli di %s?",
  val forgetAppMessage: String = "I ricordi nati in %s spariscono. Quelli che hai detto tu restano.",
  val blockApp: String = "Non ricordare più da %s",
  val allowApp: String = "Ricorda di nuovo da %s",
  val blockedApp: String = "%s non può più proporre ricordi.",
  val cancel: String = "Annulla",
  val save: String = "Salva",
  val newTitle: String = "Nuovo ricordo",
  val editTitle: String = "Modifica ricordo",
  val editHint: String = "Una frase breve, come la diresti a un amico.",
  val placeholder: String = "Es. sono in 4F al liceo Galilei",
  val sourceChat: String = "Detto in chat",
  val sourceManual: String = "Scritto da te",
  val sourceImported: String = "Dalla memoria di prima",
  val sourceApp: String = "Da %s",
  val sourceChatFromApp: String = "Detto in %s",
  val expires: String = "scade il %s",
  val expired: String = "scaduto il %s",
  val pinned: String = "fissato",
  val edited: String = "corretto",
  val secretLike: String = "sembra un codice: non entra nelle domande",
  val openPage: String = "Apri Cosa ricordo di te",
  val ask: String = "Vuoi che lo ricordi?",
  val askFromApp: String = "%s vuole che lo ricordi",
  val accept: String = "Ricorda",
  val decline: String = "No",
  val declined: String = "Va bene, non lo ricordo",
  val replacing: String = "al posto di: %s",
  val autoSave: String = "Ricorda da solo",
  val autoSaveOn: String = "Salvo quello che mi dici di te e te lo dico, con Annulla.",
  val autoSaveOff: String = "Prima di ricordare qualcosa ti chiedo se va bene.",
  val sectionPending: String = "Da confermare",
  val sectionExcluded: String = "Non imparo più",
  val include: String = "Impara di nuovo",
  val includeHint: String = "Tocca per impararlo di nuovo",
  val forgetExclude: String = "Dimentica e non imparare più",
  val sourceTool: String = "Chiesto in chat",
  val sourceToolFromApp: String = "Chiesto in chat, coi dati di %s",
  val sourceResearch: String = "Imparato dai dati di %s",
  val sourceResearchNoApp: String = "Imparato dalle tue app",
  val inferred: String = "dedotto",
  val inferredWith: String = "dedotto: %s",
  val excludedTopic: String = "l'argomento «%s»",
  val excludedApp: String = "da %s",
  val excludedSource: String = "una fonte",
  val excludedRecord: String = "un elemento",
  val months: List<String> = listOf(
    "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
    "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
  ),
) {
  companion object {
    /** Le stesse parole in inglese. */
    fun english(): FluidMemoryStrings = FluidMemoryStrings(
      remembered = "Remembered",
      updated = "Updated",
      kept = "Keeping the newer one",
      undone = "Forgotten",
      undo = "Undo",
      previous = "before: %s",
      notEntered = "not saved: %s",
      fromApp = "from %s",
      pageTitle = "What I remember about you",
      pageSubtitle = "It's used in a question only when relevant. It stays on this phone, encrypted.",
      sectionYou = "From you",
      sectionApp = "From %s",
      sectionImported = "From the earlier memory",
      emptyTitle = "Nothing yet",
      emptyMessage = "When you tell me something about yourself (your class, your city, what you don't eat) I'll keep it here, and tell you.",
      add = "Add",
      edit = "Edit",
      pin = "Pin: always in questions",
      unpin = "Unpin",
      forget = "Forget",
      forgetAll = "Forget everything",
      forgetAllTitle = "Forget everything?",
      forgetAllMessage = "I won't know anything I remember about you any more.",
      forgetApp = "Forget the ones from %s",
      forgetAppTitle = "Forget the ones from %s?",
      forgetAppMessage = "Memories that came from %s go away. The ones you said stay.",
      blockApp = "Stop remembering from %s",
      allowApp = "Remember from %s again",
      blockedApp = "%s can no longer suggest memories.",
      cancel = "Cancel",
      save = "Save",
      newTitle = "New memory",
      editTitle = "Edit memory",
      editHint = "A short sentence, the way you'd tell a friend.",
      placeholder = "E.g. I'm in class 4F at Galilei high school",
      sourceChat = "Said in chat",
      sourceManual = "Written by you",
      sourceImported = "From the earlier memory",
      sourceApp = "From %s",
      sourceChatFromApp = "Said in %s",
      expires = "expires %s",
      expired = "expired %s",
      pinned = "pinned",
      edited = "edited",
      secretLike = "looks like a code: never used in questions",
      openPage = "Open What I remember about you",
      ask = "Remember this?",
      askFromApp = "%s wants me to remember this",
      accept = "Remember",
      decline = "No",
      declined = "OK, I won't remember it",
      replacing = "instead of: %s",
      autoSave = "Remember on my own",
      autoSaveOn = "I save what you tell me about yourself and tell you, with Undo.",
      autoSaveOff = "Before remembering something I ask you first.",
      sectionPending = "To confirm",
      sectionExcluded = "No longer learning",
      include = "Learn again",
      includeHint = "Tap to learn it again",
      forgetExclude = "Forget and don't learn again",
      sourceTool = "Asked in chat",
      sourceToolFromApp = "Asked in chat, with data from %s",
      sourceResearch = "Learned from %s data",
      sourceResearchNoApp = "Learned from your apps",
      inferred = "inferred",
      inferredWith = "inferred: %s",
      excludedTopic = "the topic “%s”",
      excludedApp = "from %s",
      excludedSource = "a source",
      excludedRecord = "an item",
      months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"),
    )
  }
}

/** Un gruppo della pagina: «Da te», «Da ClasseViva», «Dalla memoria di prima». [app] e' il pacchetto per i gruppi di un'app. */
data class FluidMemoryGroup(val title: String, val app: String?, val appLabel: String?, val memories: List<AiMemory>)

/**
 * Le frasi della memoria, come funzioni pure (provate sul computer, `FluidMemoryTextsTest`): cosa dice la card, la riga
 * sotto ogni ricordo, l'etichetta per TalkBack, i gruppi della pagina.
 */
object FluidMemoryTexts {
  /**
   * La parola in testa alla card: «Vuoi che lo ricordi?», «ClasseViva vuole che lo ricordi», «Ho ricordato», «Ho
   * aggiornato · da ClasseViva», «Non lo ricordo più», «Va bene, non lo ricordo».
   */
  fun headline(notice: AiMemoryNotice, strings: FluidMemoryStrings): String {
    if (notice.undone) return strings.undone
    if (notice.kind == AiMemoryNotice.KindAsk) {
      if (notice.declined) return strings.declined
      val app = notice.source.appLabel?.takeIf { notice.source.fromApp }
      return if (app != null) strings.askFromApp.format(app) else strings.ask
    }
    val verb = when (notice.kind) {
      AiMemoryNotice.KindUpdated -> strings.updated
      AiMemoryNotice.KindKept -> strings.kept
      else -> strings.remembered
    }
    val app = notice.source.appLabel?.takeIf { notice.source.fromApp }
    return if (app != null) "$verb · ${strings.fromApp.format(app)}" else verb
  }

  /** La riga piccola sotto il fatto: «prima: è in 4F» per un aggiornamento, «non entra: …» per quello che resta fuori. */
  fun detail(notice: AiMemoryNotice, strings: FluidMemoryStrings): String? {
    val before = notice.previousText?.takeIf { it.isNotBlank() } ?: return null
    if (notice.undone) return null
    return when (notice.kind) {
      AiMemoryNotice.KindAsk -> if (notice.declined) null else strings.replacing.format(before)
      AiMemoryNotice.KindUpdated -> strings.previous.format(before)
      AiMemoryNotice.KindKept -> strings.notEntered.format(before)
      else -> null
    }
  }

  /** Quello che TalkBack legge della card, in una frase sola. */
  fun accessibilityLabel(notice: AiMemoryNotice, strings: FluidMemoryStrings): String {
    val head = headline(notice, strings)
    // Dopo una domanda niente due punti: «Vuoi che lo ricordi? è in 4F», come si leggerebbe ad alta voce.
    val sep = if (head.endsWith("?")) " " else ": "
    return listOfNotNull(head + sep + notice.text, detail(notice, strings)).joinToString(". ")
  }

  /** Il giorno, «26 novembre» (con l'anno se non e' quello di [nowMillis]). */
  fun day(millis: Long, nowMillis: Long, zone: ZoneId, strings: FluidMemoryStrings): String {
    val d = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val year = Instant.ofEpochMilli(nowMillis).atZone(zone).year
    val month = strings.months.getOrElse(d.monthValue - 1) { d.monthValue.toString() }
    return if (d.year == year) "${d.dayOfMonth} $month" else "${d.dayOfMonth} $month ${d.year}"
  }

  /**
   * Da dove viene: «Detto in chat», «Da ClasseViva», «Scritto da te», «Dalla memoria di prima», «Chiesto in chat, coi
   * dati di Pampa Notes», «Imparato dai dati di Fluidify».
   */
  fun source(source: AiMemorySource, strings: FluidMemoryStrings): String = when (source.kind) {
    AiMemorySource.KindApp -> strings.sourceApp.format(source.appLabel ?: source.app ?: "?")
    AiMemorySource.KindManual -> strings.sourceManual
    AiMemorySource.KindImported -> strings.sourceImported
    AiMemorySource.KindTool -> (source.appLabel ?: source.app)?.let { strings.sourceToolFromApp.format(it) } ?: strings.sourceTool
    AiMemorySource.KindResearch -> (source.appLabel ?: source.app)?.let { strings.sourceResearch.format(it) } ?: strings.sourceResearchNoApp
    else -> source.appLabel?.let { strings.sourceChatFromApp.format(it) } ?: strings.sourceChat
  }

  /**
   * La riga sotto un ricordo nella pagina: «Detto in chat · 26 novembre · prima: è in 4F», «Da ClasseViva · 24
   * novembre · scade il 28 novembre», «fissato · Scritto da te · 12 settembre».
   */
  fun subtitle(memory: AiMemory, nowMillis: Long, zone: ZoneId, strings: FluidMemoryStrings, secretLike: Boolean = false): String {
    val parts = ArrayList<String>(5)
    if (memory.pinned) parts += strings.pinned
    parts += source(memory.source, strings)
    parts += day(memory.observedAtMillis, nowMillis, zone, strings)
    if (memory.origin == AiMemory.OriginInference) {
      parts += memory.limitations?.takeIf { it.isNotBlank() }?.let { strings.inferredWith.format(it) } ?: strings.inferred
    }
    if (memory.edited) parts += strings.edited
    memory.expiresAtMillis?.let { exp ->
      // La scadenza e' la mezzanotte in cui smette di valere: il giorno da dire e' quello prima.
      val last = day(exp - 1, nowMillis, zone, strings)
      parts += if (memory.expired(nowMillis)) strings.expired.format(last) else strings.expires.format(last)
    }
    memory.previousText?.let { parts += strings.previous.format(it) }
    if (secretLike) parts += strings.secretLike
    return parts.joinToString(" · ")
  }

  /**
   * Un'esclusione in parole: «L'argomento «scuola › classe»», «Da MyCloud Gallery», «Un elemento da ClasseViva».
   * [labelFor] da' il nome di un'app dal pacchetto.
   */
  fun exclusion(exclusion: AiMemoryExclusion, strings: FluidMemoryStrings, labelFor: (String) -> String? = { null }): String {
    val parts = ArrayList<String>(3)
    exclusion.key?.let { parts += strings.excludedTopic.format(it.replace(".", " › ").replace('_', ' ')) }
    if (exclusion.key == null) {
      when {
        exclusion.record != null -> parts += strings.excludedRecord
        exclusion.grant != null -> parts += strings.excludedSource
      }
    }
    exclusion.app?.let { app -> parts += strings.excludedApp.format(labelFor(app) ?: app) }
    return parts.joinToString(" ").replaceFirstChar { it.uppercase() }
  }

  /**
   * I gruppi della pagina: «Da te» (chat e scritti a mano), uno per app (proposti dall'app o detti nella chat «Dalle
   * app» partita da lei), poi «Dalla memoria di prima». Dentro ogni gruppo l'ordine di [memories].
   */
  fun groups(memories: List<AiMemory>, strings: FluidMemoryStrings): List<FluidMemoryGroup> {
    val you = memories.filter { it.source.app == null && it.source.kind != AiMemorySource.KindImported }
    val imported = memories.filter { it.source.kind == AiMemorySource.KindImported }
    val byApp = memories.filter { it.source.app != null && it.source.kind != AiMemorySource.KindImported }.groupBy { it.source.app!! }
    val out = ArrayList<FluidMemoryGroup>()
    if (you.isNotEmpty()) out += FluidMemoryGroup(strings.sectionYou, null, null, you)
    byApp.entries.sortedBy { (_, list) -> list.first().source.appLabel ?: list.first().source.app }.forEach { (app, list) ->
      val label = list.firstNotNullOfOrNull { it.source.appLabel } ?: app
      out += FluidMemoryGroup(strings.sectionApp.format(label), app, label, list)
    }
    if (imported.isNotEmpty()) out += FluidMemoryGroup(strings.sectionImported, null, null, imported)
    return out
  }
}
