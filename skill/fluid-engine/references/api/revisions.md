# API · engine-revisions

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-revisions/revisions/FluidVersionChoiceSheet.kt`

- `data class FluidVersionLabels(val title: String = "Risolvi conflitto", val message: (String) -> String = { "«$it» è cambiata su più dispositivi senza ch…, val keepOne: String = "Tieni questa versione", val keepAll: (Int) -> String = { n -> if (n == 2) "Tieni tutte e due" else "Ti…, val keepSome: (Int) -> String = { n -> "Tieni queste $n" }, val chooseFirst: String = "Scegli una versione", val later: String = "Decidi dopo", val current: String = "Attuale", val updated: String = "È arrivata una versione nuova: guardale di nuo…, val excerptsOnly: String = "Le versioni sono troppo diverse per segnare i…, val selected: String = "Scelta", val notSelected: String = "Non scelta", val byAuthor: (String) -> String = { "di $it" }, val today: (String) -> String = { "Oggi, $it" }, val yesterday: (String) -> String = { "Ieri, $it" }, val onDay: (String, String) -> String = { day, time -> "$day, $time" })` — Le parole del foglio, nella lingua dell'app: di serie italiane, [english] per l'inglese. I titoli seguono il foglio di iOS per i documenti di iCloud in conflitto («Risolvi conflitto», fiducia media: la guida di Pages, letta il 24/11/2026); i tasti sono quelli del brief di F8.21 («tieni questa», «tieni tutte e due»).
  - `companion object`
    - `fun english()`
- `object FluidVersionChoiceTags` — I tag dei test.
  - `const val Pane`
  - `const val Keep`
  - `const val Later`
  - `const val Updated`
  - `fun card(id: String): String`
- `@Composable fun FluidVersionChoiceSheet(choice: FluidVersionChoice?, onDecision: (FluidVersionDecision) -> Unit, onDismissRequest: () -> Unit, source: FluidTransitionSource? = null, labels: FluidVersionLabels = FluidVersionLabels(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault())` — **La scelta tra versioni** (F8.21, `scelta-tra-versioni`): quando due copie di un elemento sono cambiate e non si possono fondere, un foglio di vetro le mette a confronto e chiede quale tenere. Il riferimento e' Apple: le versioni in conflitto di `NSFileVersion` e il foglio «Risolvi conflitto» dei documenti di iCloud, con una riga per versione (il dispositivo, l'ora, l'anteprima) da spuntare e il tasto «Mantieni».
- `@Composable fun FluidVersionChoicePane(choice: FluidVersionChoice, onDecision: (FluidVersionDecision) -> Unit, onDismissRequest: () -> Unit, labels: FluidVersionLabels = FluidVersionLabels(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault(), modifier: Modifier = Modifier)` — Il contenuto del foglio, per chi lo mette in una superficie sua.

## `engine-revisions/revisions/FluidVersions.kt`

- `class FluidVersion(val id: String, val source: String, val timeMillis: Long, val text: String, val author: String? = null, val current: Boolean = false)` — Una versione da mettere a confronto: una revisione di un elemento, come l'app la sa raccontare.
- `class FluidVersionChoice(val title: String, val versions: List<FluidVersion>, val key: Any = title)` — Il confronto da mostrare: l'elemento ([title], «Lista della gita») e le sue [versions], di solito due. [key] e' l'identita' del confronto (l'elemento e il campo): se le revisioni cambiano con la stessa chiave, il foglio dice che e' arrivata una versione nuova e chiede di scegliere di nuovo.
  - `val ids: List<String> get()` — Gli identificativi delle revisioni, nell'ordine.
- `class FluidVersionDecision(val keep: List<String>, val shown: List<String>)` — La scelta: le revisioni da tenere ([keep], una o piu'; con piu' d'una, «tieni tutte e due») e quelle che il foglio mostrava quando si e' scelto ([shown]). L'app salva l'esito in modo atomico e controlla che [shown] sia ancora il confronto attuale; con `engine-sync`: `space.resolve(record, field, keep, shown)`, che risponde «superata» se nel frattempo e' arrivata una revisione nuova.
  - `val keepsAll: Boolean get()` — Vero se si tengono tutte le versioni mostrate.
- `data class FluidVersionExcerpt(val text: String, val changed: List<IntRange>, val clippedStart: Boolean, val clippedEnd: Boolean, val compared: Boolean)` — Un estratto di una versione da mostrare nella card: il [text] (gia' tagliato attorno al primo passaggio diverso), i tratti diversi dalle altre versioni ([changed], indici in [text]), e se il testo continua prima o dopo. [compared] e' falso quando il confronto ha superato il tetto di lavoro: allora l'estratto e' l'inizio del testo, senza segni (il ripiego su due estratti).
- `fun fluidVersionExcerpts(versions: List<FluidVersion>, maxChars: Int = 280, maxEdits: Int = FluidTextDiff.DefaultMaxEdits): Map<String, FluidVersionExcerpt>` — I passaggi diversi: per ogni versione, un estratto con i tratti che le altre non hanno. Ogni versione si confronta con quella attuale (o la prima); quella attuale porta segnati i tratti che le altre hanno cambiato. Funzione pura, da chiamare fuori dal thread principale per i testi lunghi (il foglio lo fa da solo).
- `fun fluidVersionTime(millis: Long, zone: ZoneId, locale: Locale, today: LocalDate, labels: FluidVersionLabels = FluidVersionLabels()): String` — L'ora di una versione come la dice il foglio: «Oggi, 14:32», «Ieri, 9:10», «21 nov, 18:05», «21 nov 2025, 18:05». Pura, con il giorno di oggi dato ([today]).
