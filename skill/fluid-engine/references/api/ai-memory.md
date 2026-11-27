# API · engine-ai-memory e i ricordi di engine-ai

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ai-memory/ai/memory/AiMemoryBook.kt`

- `data class AiMemoryLimits(val maxMemories: Int = 300, val maxAutomaticPerDay: Int = 12, val maxPerAppPerDay: Int = 5, val keepExpiredDays: Int = 7, val notices: Int = 20, val undo: Int = 50, val maxPending: Int = 30, val pendingDays: Int = 14)` — I tetti della memoria. A occhio il 26/11/2026, tranne dove e' scritto.
- `data class AiMemoryConfig(val limits: AiMemoryLimits = AiMemoryLimits(), val prompt: AiMemoryPromptBudget = AiMemoryPromptBudget(), val embedder: FluidTextEmbedder? = null, val zone: ZoneId = ZoneId.systemDefault(), val autoSave: Boolean = false, val apps: AiMemoryAppPolicy = AiMemoryAppPolicy(), val onDevice: AiMemoryOnDeviceModel? = null, val onDeviceTimeoutMillis: Long = AiMemoryQueryExpansion.TimeoutMillis)` — Come si comporta la memoria.
- `class AiMemoryOutcome(val result: String, val memory: AiMemory? = null, val notice: AiMemoryNotice? = null)` — Com'e' andata una proposta: [result] e' una costante `Outcome*` di [AiMemoryProposalResult], o [NeedsConfirmation].
  - `val remembered: Boolean get()` — Vero se adesso la memoria lo ricorda (nuovo o aggiornato).
  - `companion object`
    - `const val NeedsConfirmation` — «Ricordati che…» in un turno che ha letto testo scritto da altri, o dati privati: decide l'utente con un tasto (in PampAI la conferma di sicurezza che c'e' gia'), poi si richiama [AiMemoryBook.remember] con `confirmed = true`.
- `class AiMemoryAfterTurn(val extraction: AiMemoryExtraction, val outcomes: List<AiMemoryOutcome>)` — Com'e' andato il dopo-turno: l'estrazione (e il suo costo) e le proposte.
  - `val notices: List<AiMemoryNotice> get()` — Gli avvisi da mostrare sotto la risposta (di serie le domande «Vuoi che ricordi…?»).
- `data class AiMemoryLegacyRow(val id: Long, val text: String, val createdAtMillis: Long, val sourceConversationId: Long?, val pinned: Boolean)` — Una riga della memoria di prima di PampAI: la tabella `memories` di Room v7, colonna per colonna.
- `data class AiMemoryProfileRow(val id: String, val field: String, val value: String, val kind: String, val status: String, val limitations: String? = null, val derivedBy: String? = null, val evidence: List<AiMemoryEvidence> = emptyList(), val app: String? = null, val appLabel: String? = null, val observedAtMillis: Long? = null)` — Un fatto del profilo della ricerca di PampAI (Room v7: `aria_profile` con le sue prove da `aria_dependencies` e `aria_evidence`, e l'app della fonte da `aria_grants`), da fondere nella memoria unica (F9.3b, risposta 6 di Alessio).
- `data class AiMemoryImport(val imported: Int, val alreadyImported: Int, val duplicates: Int, val empty: Int, val secretLike: Int, val inactive: Int = 0, val exclusions: Int = 0, val excluded: Int = 0)` — Com'e' andato il passaggio della memoria di prima (o del profilo).
- `class AiMemoryBook private constructor(private val store: AiMemoryStore, private val index: AiMemoryIndex, val config: AiMemoryConfig, private val clock: () -> Long, initial: MemoryState)` — **La memoria di PampAI** (F9.3, F9.3b): i fatti che l'assistente ricorda dell'utente, con la fonte, la data e la scadenza, cifrati nella cassaforte, e la ricerca per pertinenza che decide quali entrano in una domanda.
  - `val memories: StateFlow<List<AiMemory>>` — Tutti i ricordi, i fissati prima e poi dal piu' recente: la pagina «Cosa ricordo di te».
  - `val notices: StateFlow<List<AiMemoryNotice>>` — Gli avvisi recenti, dal piu' nuovo: le card sotto le risposte.
  - `val pending: StateFlow<List<AiMemoryNotice>>` — Le domande «Vuoi che ricordi…?» che aspettano una risposta, dalla piu' nuova: la sezione «Da confermare».
  - `val blockedApps: StateFlow<Set<String>>` — Le app a cui l'utente ha tolto il permesso di proporre ricordi.
  - `val exclusions: StateFlow<List<AiMemoryExclusion>>` — Cio' che l'utente non vuole che la memoria impari piu'.
  - `val autoSave: StateFlow<Boolean>` — Il salvataggio automatico: vero, i fatti entrano subito con Annulla; falso (di serie), la memoria chiede. Uno solo per tutto: la chat, le app, lo strumento del modello e i fatti imparati dalla ricerca ([AiMemorySource.KindResearch]: spento chiedono uno per uno, acceso entrano da soli; risposte 2C e 4A di Alessio alle domande di F9.3b).
  - `suspend fun remember(proposal: AiMemoryProposal, source: AiMemorySource, turn: AiMemoryTurn? = null, confirmed: Boolean = false, automatic: Boolean = false): AiMemoryOutcome` — Propone [proposal], nato da [source].
  - `suspend fun answer(noticeId: String, accept: Boolean, exclude: Boolean = false, app: String? = null): AiMemoryOutcome` — La risposta a una domanda «Vuoi che ricordi…?»: [accept] vero (Ricorda) e il fatto entra, con «Ho ricordato…» e Annulla al posto della domanda; falso (No) e non entra, e con [exclude] la memoria non chiede piu' di quell'argomento. Con [app] risponde solo se la domanda e' nata da quell'app (il ponte). [AiMemoryProposalResult.OutcomeNotFound] se la domanda non c'e' (gia' risposta, scaduta, di un'altra app).
  - `suspend fun afterTurn(turn: AiMemoryTurn, extractor: AiMemoryExtractor): AiMemoryAfterTurn` — Il dopo-turno: le regole ([AiMemoryPolicy.automaticSkip]), il filtro locale, le app (una domanda arrivata da un'app che non puo' proporre ricordi non lascia ricordi automatici: risposta 3 di Alessio), il tetto del giorno, poi l'estrattore coi ricordi gia' noti che c'entrano, e le sue proposte. Se qualcosa ferma prima del modello, il costo e' zero. Un «ricordati che…» nel messaggio entra subito;
  - `suspend fun undo(noticeId: String, app: String? = null): Boolean` — Annulla l'avviso [noticeId]: un ricordo nuovo sparisce, uno aggiornato torna com'era. Vale finche' il ricordo non e' cambiato di nuovo (una correzione, un altro aggiornamento): allora torna falso. Con [app] annulla solo se l'avviso e' nato da quell'app (il ponte: un'app annulla solo le sue proposte).
  - `fun dismissNotice(noticeId: String)` — Toglie l'avviso [noticeId] dagli avvisi recenti (la card chiusa); il ricordo (o la domanda) resta.
  - `suspend fun setAutoSave(on: Boolean)` — Accende o spegne il salvataggio automatico (si salva con la memoria). Le domande gia' in attesa restano domande.
  - `suspend fun correct(id: String, text: String): AiMemory?` — Corregge il testo del ricordo [id] (dalla pagina): resta la fonte, cambia la data di modifica. Null se non c'e'.
  - `suspend fun setPinned(id: String, pinned: Boolean): Boolean` — Fissa (sempre nel prompt, dentro il tetto) o sfissa il ricordo [id].
  - `suspend fun setExpiry(id: String, expiresAtMillis: Long?): Boolean` — Cambia la scadenza del ricordo [id] (null: non scade). Come [setPinned], non cambia il fatto: Annulla resta possibile.
  - `suspend fun forget(id: String, exclude: Boolean = false): Boolean` — Dimentica il ricordo [id]. Le card che lo nominano diventano «Non lo ricordo più». Con [exclude] la memoria non impara piu' quell'argomento (la sua chiave) se non lo chiede l'utente.
  - `suspend fun forgetAll(): Int` — Dimentica tutto (i ricordi, le domande, gli annulla, i vettori). Restano le app bloccate, le esclusioni e l'interruttore.
  - `suspend fun forgetFromApp(app: String, onlyProposed: Boolean = false): Int` — Dimentica i ricordi nati dall'app [app]: quelli che ha proposto lei, quelli detti nella chat «Dalle app» partita da lei, quelli chiesti dal modello coi suoi dati e quelli imparati dalla ricerca sui suoi dati ([onlyProposed] vero: solo i primi, la revoca chiesta dall'app stessa col ponte). Per non riceverne altri: [setAppBlocked].
  - `suspend fun forgetFromGrant(grant: String, record: String? = null): Int` — Dimentica cio' che e' stato letto o dedotto dalla fonte autorizzata [grant] (e, con [record], solo da quel dato): la revoca di una fonte della ricerca. I fatti detti dall'utente restano, anche se una prova veniva da li'. Con loro se ne vanno le domande ancora aperte che vengono da li' e le versioni di prima che Annulla rimetterebbe (R3-S1).
  - `suspend fun exclude(exclusion: AiMemoryExclusion): Int` — Non imparare piu' [exclusion] (un argomento, un'app, una fonte, un dato), se non lo chiede l'utente. Come l'esclusione del profilo di oggi, toglie anche cio' che la memoria aveva **imparato** da li' (osservato o dedotto); le cose dette dall'utente restano, finche' non le dimentica lui. Torna quanti ricordi ha tolto.
  - `suspend fun include(exclusion: AiMemoryExclusion)` — Toglie l'esclusione [exclusion]: la memoria puo' di nuovo imparare quell'argomento.
  - `suspend fun setAppBlocked(app: String, blocked: Boolean)` — Toglie (o ridà) all'app [app] il permesso di proporre ricordi. I suoi ricordi restano ([forgetFromApp]); le sue proposte ancora da confermare no (R3-S1): senza permesso non entrano neanche con Ricorda.
  - `suspend fun relevant(question: String, limit: Int = 8, language: String = "it"): List<AiMemoryMatch>` — I ricordi che c'entrano con [question], dal piu' pertinente, senza gli scaduti. Col modello del telefono ([AiMemoryConfig.onDevice]) la domanda si allarga con le parole del suo argomento, entro il suo tempo massimo.
  - `suspend fun prompt(question: String, budget: AiMemoryPromptBudget = config.prompt, language: String = "it"): AiMemoryPrompt` — Il blocco dei ricordi per il prompt della domanda [question]: i fissati, quelli «per ogni domanda», poi i pertinenti, finche' stanno in [budget]. Vuoto se niente c'entra: la domanda non paga gettoni per la memoria. Gli scaduti e quelli che sembrano un segreto non entrano mai; le domande in attesa nemmeno.
  - `suspend fun maintain(): Int` — La manutenzione: toglie i ricordi scaduti da piu' di [AiMemoryLimits.keepExpiredDays] giorni e le domande senza risposta da piu' di [AiMemoryLimits.pendingDays]. Torna quanti ricordi.
  - `suspend fun importLegacy(rows: List<AiMemoryLegacyRow>): AiMemoryImport` — Porta dentro la memoria di prima (Room v7 di PampAI, [AiMemoryLegacyImport]) **senza perdere niente**: ogni riga entra con il suo testo intero, la sua data, la conversazione e la fissatura. Si puo' rifare: le righe gia' passate (per id) non si duplicano, e uno stesso testo che c'e' gia' resta uno. I tetti della memoria non valgono qui, e non si chiede niente: l'utente li aveva gia' chiesti.
  - `suspend fun importProfile(rows: List<AiMemoryProfileRow>, exclusions: List<AiMemoryExclusion> = emptyList(), label: (String) -> String = ::profileLabel): AiMemoryImport` — Fonde nella memoria il **profilo della ricerca** di PampAI (F9.3b, risposta 6 di Alessio: «deve essere una cosa unica»): ogni fatto attivo diventa un ricordo con la chiave del campo, il valore, l'origine (detto, letto o dedotto), le prove con le fonti autorizzate, i limiti e chi l'ha dedotto; le esclusioni diventano esclusioni. Il testo e' «[label] del campo: valore» (PampAI puo' passare le sue parole per i campi).
  - `fun close()` — Chiude l'indice in memoria. La memoria sul disco resta.
  - `companion object`
    - `const val VaultSectionName` — Il nome della sezione della cassaforte di serie.
    - `suspend fun open(context: Context, config: AiMemoryConfig = AiMemoryConfig()): AiMemoryBook` — La memoria dell'app [context], nella sezione [VaultSectionName] della sua cassaforte (`VaultAccess.Device()`: cifrata col Keystore, si apre da sola, fuori dal backup). Va aperta fuori dal thread principale.
    - `suspend fun open(store: AiMemoryStore, config: AiMemoryConfig = AiMemoryConfig(), clock: () -> Long = System::currentTimeMillis): AiMemoryBook` — La memoria nel deposito [store] (per le prove e la galleria: [AiMemoryStore.inMemory], o una cassaforte di prova). Un file che non si legge (una versione futura, byte rovinati) fa lanciare l'eccezione: la memoria non si azzera da sola. [clock] e' l'ora del muro, in millisecondi.
    - `fun profileLabel(field: String): String` — Le parole di un campo del profilo: «musica.artista_preferito» → «musica artista preferito».

## `engine-ai-memory/ai/memory/AiMemoryExtractor.kt`

- `data class AiMemoryExtractorConfig(val maxFacts: Int = 2, val maxOutputTokens: Int = 200, val userTextChars: Int = 1_200, val previousAnswerChars: Int = 280, val knownMemories: Int = 4, val knownKeys: Int = 24)` — Le regole dell'estrattore. I numeri sono a occhio (26/11/2026) e le misure sono nel rapporto di F9.3.
- `class AiMemoryExtraction(val proposals: List<AiMemoryProposal>, val skipped: String? = null, val usage: Usage? = null, val estimatedPromptTokens: Int = 0, val millis: Long = 0, val failed: Boolean = false, val discarded: Int = 0)` — Com'e' andata un'estrazione. [skipped] dice perche' il modello non e' stato chiamato (una costante `Skip*` di [AiMemoryPolicy]): allora [usage] e' null e il costo e' zero.
  - `val called: Boolean get()` — Vero se il modello e' stato chiamato.
- `class AiMemoryExtractor private constructor(private val provider: ChatProvider?, private val model: String, private val device: AiMemoryOnDeviceModel?, val config: AiMemoryExtractorConfig, private val usageSink: AiUsageSink?, private val zone: ZoneId)` — **L'estrattore dopo i turni** (F9.3): un modello piccolo legge il messaggio dell'utente e propone i fatti da ricordare, senza dipendere dal modello della chat che sceglie (o no) di chiamare `ricorda`. E' la ragione per cui la memoria di PampAI oggi non funziona: `ricorda` sta nel gruppo «aria», che il pre-router carica solo se la domanda dice «ricorda» o «memoria», e anche caricato lo chiama solo un modello che decid…
  - `val onDevice: Boolean get()` — Vero se i fatti li estrae il modello del telefono (AICore): niente rete, niente gettoni, niente consumi.
  - `suspend fun extract(turn: AiMemoryTurn, known: List<AiMemory> = emptyList(), keys: List<String> = emptyList()): AiMemoryExtraction` — I fatti di [turn]. [known] sono i ricordi gia' noti che c'entrano col messaggio (di solito `AiMemoryBook.relevant`): il modello ne riusa la chiave quando parla della stessa cosa, ed e' cosi' che «da lunedì sono in 5F» diventa un aggiornamento di «è in 4F» e non un secondo ricordo.
  - `fun request(turn: AiMemoryTurn, known: List<AiMemory> = emptyList(), keys: List<String> = emptyList()): ChatRequest` — La richiesta al modello per [turn]: pubblica per misurarla (gettoni, caratteri) senza chiamare nessuno. [keys] sono le chiavi gia' usate dalla memoria (al piu' [AiMemoryExtractorConfig.knownKeys]): bastano poche parole perche' «da lunedì sono in 5F» trovi `scuola.classe` anche quando nessuna parola del messaggio nomina il ricordo di prima.
  - `companion object`
    - `const val NoConversation` — La conversazione dei consumi quando il turno non ne ha una.
    - `fun onDevice(model: AiMemoryOnDeviceModel, config: AiMemoryExtractorConfig = AiMemoryExtractorConfig(), zone: ZoneId = ZoneId.systemDefault()): AiMemoryExtractor` — L'estrattore sul modello del telefono, **AICore** (F9.3b, risposta 5 di Alessio): le stesse istruzioni, gratis e senza rete. Quando AICore c'e', PampAI usa questo; quando non c'e', il modello piccolo del fornitore (risposta 1), o niente se l'utente spegne i ricordi automatici.
    - `val Schema: JsonObject` — Lo schema della risposta (rigido: ogni campo c'e', anche null).
    - `fun systemPrompt(language: String, maxFacts: Int, today: LocalDate): String` — Le istruzioni del modello piccolo.
    - `fun parse(text: String?, turn: AiMemoryTurn, zone: ZoneId = ZoneId.systemDefault()): List<AiMemoryProposal>?` — Le proposte nel JSON del modello, gia' ripulite (null se il JSON non si legge). Tollera i recinti di codice e il nome inglese del campo; la scadenza «AAAA-MM-GG» diventa la mezzanotte del giorno dopo in [zone].
    - `fun grounded(fact: String, userText: String): Boolean` — Vero se il fatto viene dalle parole dell'utente: almeno una sua parola piena (3 lettere o piu', o con una cifra, fuori dalle parole di servizio) comincia come una parola del messaggio (le prime 4 lettere: «vegetariana» e «vegetariano», «4F» e «4f»). Un fatto preso solo dalla risposta di prima, o inventato, non passa.

## `engine-ai-memory/ai/memory/AiMemoryInbox.kt`

- `data class AiMemoryCaller(val packageName: String, val label: String)` — Chi propone un ricordo dal ponte, **gia' verificato** dal cancello del provider dell'assistenza di PampAI (F9.1): la firma Pampa, il pacchetto detto dal sistema (`getPackagesForUid`, uno solo), la tabella. [label] e' il nome dell'app letto dal sistema, non scritto dall'app.
- `class AiMemoryAppPolicy(val apps: Map<String, Entry> = DEFAULT, val devBuild: Boolean = false)` — Quali app possono proporre ricordi, e su quali argomenti: una chiave che comincia con uno dei loro prefissi. Cosi' ClasseViva puo' dire «è in 4F» (`scuola.classe`) ma non «è vegetariana» (`cibo.dieta`). La tabella e' codice rivisto in git, come quella dell'assistenza di F9.1 e quella dell'Account Pampa; le varianti di sviluppo (`.debug`, `.dev`) contano solo se anche PampAI e' una build di sviluppo ([devBuild]).
  - `class Entry(val label: String, val keyPrefixes: Set<String>)` — Un'app della tabella: il nome e i prefissi delle chiavi che puo' proporre.
  - `fun entryFor(packageName: String): Entry?` — La voce di [packageName], o null se non puo' proporre ricordi.
  - `fun allows(packageName: String, key: String?): Boolean` — Vero se [packageName] puo' proporre un ricordo con la chiave [key]. Senza chiave no: un'app dice sempre l'argomento.
  - `companion object`
    - `val DevSuffixes`
    - `val DEFAULT: Map<String, Entry>` — La tabella di serie, **scelta di Alessio del 26/11/2026** (risposta 3): possono proporre ricordi da sole Pampa Mail (lo stile delle risposte, la firma), MyCloud Gallery (i luoghi, le persone, gli album) e ClasseViva (la classe, la scuola, le materie e le verifiche, che scadono).
- `class AiMemoryInbox(private val book: AiMemoryBook, private val policy: AiMemoryAppPolicy = book.config.apps)` — **I ricordi che nascono in un'altra app passano da PampAI** (F9.3): l'aggancio per il provider dell'assistenza di F9.1. `AiAssistHostProvider` fa i suoi controlli (chi chiama, la firma, il pacchetto dal sistema) e il «no» di F9.1 (un'app rifiutata o bloccata in «App che usano PampAI» riceve `not_allowed`), poi passa qui i metodi dei ricordi ([AiMemoryProtocol.Methods]) con la richiesta JSON e chi chiama;
  - `suspend fun handle(method: String, json: String, caller: AiMemoryCaller): String` — Risponde al metodo [method] con la richiesta [json] da [caller]. Mai un'eccezione: un errore e' un esito.

## `engine-ai-memory/ai/memory/AiMemoryIndex.kt`

- `data class AiMemoryMatch(val memory: AiMemory, val score: Float, val byMeaning: Boolean = false)` — Un ricordo trovato per una domanda: quanto c'entra ([score], piu' alto e' meglio) e se l'ha trovato il significato.

## `engine-ai-memory/ai/memory/AiMemoryLegacyImport.kt`

- `object AiMemoryLegacyImport` — La memoria di prima di PampAI: la tabella `memories` del database Room di PampAI alla versione 7 (`core-assistant/schemas/…/PampaiDatabase/7.json`):
  - `const val RoomVersion` — La versione dello schema di PampAI che questo lettore conosce.
  - `const val Table` — La tabella.
  - `val Columns: List<String>` — Le colonne che servono, come le scrive Room.
  - `fun readPampaiRoom(file: File): List<AiMemoryLegacyRow>` — Le righe di [file] (il database di PampAI, `pampai.db`), in ordine di id. Vuoto se la tabella non c'e'. Lancia un'eccezione se la tabella c'e' ma le colonne non sono quelle della versione 7 (uno schema che non si conosce non si indovina), o se [file] non e' un database.
  - `fun read(db: SQLiteDatabase): List<AiMemoryLegacyRow>` — Le righe dal database [db] gia' aperto (in sola lettura va bene).
  - `val ProfileColumns: Map<String, List<String>>` — Le tabelle del profilo della ricerca (F9.3b), alla versione 7, e le colonne che servono di ognuna.
  - `fun readPampaiProfile(file: File, labelFor: (String) -> String? = { null }): AiMemoryProfileExport` — [readPampaiProfile] con un file: aperto in sola lettura, come [readPampaiRoom].
  - `fun readProfile(db: SQLiteDatabase, labelFor: (String) -> String? = { null }): AiMemoryProfileExport` — **Il profilo della ricerca di oggi** (`aria_profile` con le sue prove, le autorizzazioni e le esclusioni) come righe per [AiMemoryBook.importProfile]: la risposta 6 di Alessio, una memoria sola. Come per i ricordi, la strada di serie e' che PampAI legga le righe coi suoi DAO; questo lettore e' per le prove e il recupero.
- `data class AiMemoryProfileExport(val rows: List<AiMemoryProfileRow>, val exclusions: List<AiMemoryExclusion>, val unmapped: Int)` — Il profilo della ricerca letto da PampAI: le voci, le esclusioni da passare e quante esclusioni sono da chiarire ([unmapped]: con una sola, il passaggio aspetta).

## `engine-ai-memory/ai/memory/AiMemoryOnDevice.kt`

- `fun interface AiMemoryOnDeviceModel` — **Il modello del telefono** per la memoria (F9.3b, risposta 5 di Alessio): il servizio di sistema **AICore** di Android, coi modelli che il sistema gia' offre (Gemini Nano), senza niente da scaricare o installare. Il collegamento vero (le API GenAI di ML Kit sopra AICore) e' di F9.5; qui c'e' solo la porta, una funzione: istruzioni e testo dentro, testo fuori.
  - `suspend fun generate(instructions: String, input: String, maxOutputTokens: Int): String?` — Il testo del modello per [instructions] e [input], al piu' [maxOutputTokens] gettoni; null se non risponde.
- `object AiMemoryQueryExpansion` — Allargare la domanda col modello del telefono: le parole dell'argomento che la domanda non dice («cosa cucino stasera?» → «cena, ricette, cibo, dieta»). Poche parole, al piu' [MaxWords], con un tempo massimo: se il modello e' lento o non risponde, la ricerca va per le sole parole della domanda e la risposta non aspetta.
  - `const val MaxWords: Int` — Quante parole al piu'.
  - `const val MaxOutputTokens: Int` — Il tetto della risposta del modello: 24 gettoni bastano per sei parole.
  - `const val TimeoutMillis: Long` — Quanto si aspetta al piu': **800 ms** (a occhio, 26/11/2026): un prompt di cento gettoni con una risposta di venti, sul modello del telefono, sta sotto il mezzo secondo sui telefoni che hanno AICore (fiducia bassa: da misurare in F9.5).
  - `fun instructions(language: String): String` — Le istruzioni del modello.
  - `suspend fun expand(model: AiMemoryOnDeviceModel, question: String, language: String = "it", timeoutMillis: Long = TimeoutMillis): List<String>` — Le parole per [question], o niente se il modello non risponde in [timeoutMillis].
  - `fun parse(text: String): List<String>` — Le parole di [text]: separate da virgole, punti o a capo, in minuscolo, di lettere, al piu' [MaxWords].

## `engine-ai-memory/ai/memory/AiMemoryPolicy.kt`

- `data class AiMemoryTurn(val conversationId: Long?, val userText: String, val previousAnswer: String? = null, val atMillis: Long, val temporary: Boolean = false, val locked: Boolean = false, val storedKnowledgeAllowed: Boolean = true, val privateProvenance: Boolean = false, val untrustedInput: Boolean = false, val readExternal: Boolean = false, val language: String = "it", val app: String? = null, val appLabel: String? = null)` — Un turno di chat visto dalla memoria: cosa ha scritto l'utente e in che condizioni. L'estrattore legge **solo** [userText] (e, come contesto, [previousAnswer]): mai i risultati dei tool, le pagine, le notifiche, lo schermo o gli allegati. Cosi' un testo scritto da altri non puo' diventare un ricordo nemmeno se dice «ricorda che…».
  - `val source: AiMemorySource get()` — La fonte dei ricordi nati in questo turno.
- `object AiMemoryPolicy` — Le regole della memoria, tutte in un posto: quando si ricorda da sola, quando chiede, quando no, e cosa non si ricorda mai. Funzioni pure, provate sul computer (`AiMemoryPolicyTest`).
  - `fun automaticSkip(turn: AiMemoryTurn): String?` — Perche' l'estrattore non parte (o null se parte): costa zero gettoni.
  - `fun explicit(turn: AiMemoryTurn): String` — Cosa fare di un «ricordati che…» chiesto dall'utente nel turno [turn] (dal tool `ricorda` o dalla domanda): [VerdictAllowed], [VerdictAskFirst] (il turno ha letto testo scritto da altri, o dati privati: decide l'utente con un tasto, come fa oggi PampAI con `confirmSafety`) o [VerdictRefused] (chat temporanea, telefono bloccato).
  - `fun looksSecret(text: String): Boolean` — Vero se [text] sembra un segreto, che non si ricorda mai, nemmeno se l'utente lo chiede: i ricordi entrano nelle domande e vanno al fornitore del modello. Password, PIN, codici di verifica, IBAN, numeri di carta, codice fiscale, chiavi dei fornitori. Meglio un fatto in meno che un segreto nel prompt di ogni domanda.
  - `const val SkipTemporary`
  - `const val SkipLocked`
  - `const val SkipRefused`
  - `const val SkipPrivate`
  - `const val SkipUntrusted`
  - `const val SkipEmpty`
  - `const val SkipNoSignal` — Il messaggio non dice niente di se' (il filtro locale, [AiMemorySignals]): nessuna chiamata.
  - `const val SkipDailyCap` — Il tetto dei ricordi automatici di oggi e' gia' pieno.
  - `const val SkipApp` — La domanda arrivava da un'app che non puo' proporre ricordi da sola (risposta 3 di Alessio: solo Pampa Mail, MyCloud Gallery e ClasseViva): per le altre un ricordo nasce solo se lo chiede il modello della chat di PampAI con lo strumento della memoria.
  - `const val VerdictAllowed`
  - `const val VerdictAskFirst`
  - `const val VerdictRefused`
- `object AiMemorySignals` — Il filtro locale prima dell'estrattore: la maggior parte dei messaggi («che tempo fa domani?», «metti un timer») non dice niente di chi scrive, e per loro il modello non si chiama. Parole in prima persona e richieste di ricordare, in italiano e in inglese. Sbaglia apposta per eccesso: un falso sì costa una chiamata piccola (il modello risponde «niente»), un falso no perde un fatto.
  - `fun mayCarryFacts(text: String): Boolean` — Vero se [text] potrebbe dire qualcosa di chi scrive.
  - `fun asksToRemember(text: String): Boolean` — Vero se [text] chiede esplicitamente di ricordare («ricordati che…», «remember that…»).

## `engine-ai-memory/ai/memory/AiMemoryPrompt.kt`

- `data class AiMemoryPromptBudget(val maxTokens: Int = 250, val maxItems: Int = 10, val maxAlways: Int = 4)` — Il tetto dei ricordi nel prompt di una domanda. Oggi PampAI mette **sempre** fino a 40 righe e 1 500 caratteri (circa 430 gettoni) in ogni domanda, che c'entrino o no (`MemoryRepository.promptBlock`): qui entrano solo quelli pertinenti, e al piu' [maxTokens].
- `data class AiMemoryPrompt(val text: String, val tokens: Int, val memories: List<AiMemory>, val leftOut: Int = 0)` — Il blocco dei ricordi per il prompt di una domanda: il testo, quanto pesa e chi c'e' dentro.
  - `val isEmpty: Boolean get()`
  - `companion object`
    - `val Empty`
    - `fun build(candidates: List<AiMemory>, budget: AiMemoryPromptBudget, language: String = "it", zone: ZoneId = ZoneId.systemDefault()): AiMemoryPrompt` — Il blocco con [candidates] gia' in ordine (fissati, «per ogni domanda», poi i pertinenti), finche' ci stanno in [budget]. [language] e' quella del prompt («it» o «en»), [zone] quella delle date.
    - `fun header(language: String): String` — L'intestazione: i ricordi sono dati, non istruzioni (un'app li ha proposti, l'utente li ha detti).
    - `fun line(m: AiMemory, language: String = "it", zone: ZoneId = ZoneId.systemDefault()): String` — Una riga: «- è in 5F al liceo Galilei (2026-11-26)», con l'app e la scadenza quando ci sono. Un fatto dedotto dal profilo ([AiMemory.OriginInference]) lo dice, coi suoi limiti: il modello non lo tratta come una cosa detta.
    - `const val MaxLimitationChars: Int` — I limiti di un fatto dedotto nel prompt, al piu': una riga, non il ragionamento intero.

## `engine-ai-memory/ai/memory/AiMemoryStore.kt`

- `interface AiMemoryStore` — Dove la memoria tiene i suoi byte: un blocco solo, scritto tutto o niente. Il formato lo decide la memoria ([AiMemoryBook]); il deposito sa solo leggere, scrivere e cancellare. Di serie e' la cassaforte ([vault]): il file e' cifrato con la chiave della sezione (AES-256-GCM a pezzi, `VaultFile`), sta in `noBackupFilesDir` e non esce dal telefono.
  - `suspend fun read(): ByteArray?` — I byte salvati, o null se non c'e' ancora niente.
  - `suspend fun write(bytes: ByteArray)` — Scrive [bytes] al posto di quelli di prima, tutto o niente.
  - `suspend fun delete()` — Cancella tutto.
  - `companion object`
    - `const val DefaultFile` — Il nome di serie del file della memoria nella sua sezione.
    - `fun vault(section: VaultSection, fileName: String = DefaultFile): AiMemoryStore` — Il file [fileName] della sezione [section] della cassaforte: cifrato, fuori dal backup. Una sezione `VaultAccess.Device()` (si apre da sola) e' quella giusta: la memoria serve anche a una domanda dalla sessione dell'assistente; dal telefono bloccato non entra comunque nelle domande (lo decide PampAI).
    - `fun inMemory(): AiMemoryStore` — In memoria, che sparisce col processo: per le prove e la galleria. Non cifra niente.

## `engine-ai/ai/memory/AiMemory.kt`

- `data class AiMemorySource(val kind: String, val conversationId: Long? = null, val app: String? = null, val appLabel: String? = null)` — Da dove viene un ricordo: la conversazione in cui l'utente l'ha detto, l'app Pampa che l'ha proposto, la pagina dove l'ha scritto a mano, o la memoria di prima di PampAI. La pagina «Cosa ricordo di te» lo mostra sotto ogni ricordo, e la revoca per app ([app]) lo usa per trovare «quelli nati da ClasseViva».
  - `val fromApp: Boolean get()` — Vero se l'ha proposto un'altra app col ponte.
  - `companion object`
    - `const val KindChat` — Detto dall'utente in chat: l'estrattore dopo il turno, o «ricordati che…».
    - `const val KindApp` — Proposto da un'altra app Pampa col ponte firmato (F9.1), passando da PampAI.
    - `const val KindManual` — Scritto a mano dall'utente nella pagina «Cosa ricordo di te».
    - `const val KindImported` — Portato dalla memoria di prima di PampAI (la tabella `memories` di Room v7).
    - `const val KindTool` — Chiesto dal modello della chat di PampAI con lo strumento della memoria (`ricorda`), anche con dati letti da un'altra app ([app]): e' l'unica strada per i ricordi delle app che non possono proporre da sole (F9.3b).
    - `const val KindResearch` — Imparato dalla ricerca autorizzata di PampAI sui dati di un'app collegata ([app]): il profilo della ricerca di oggi, fuso nella memoria unica (F9.3b). Porta le sue fonti in [AiMemory.evidence].
    - `fun chat(conversationId: Long?, app: String? = null, appLabel: String? = null): AiMemorySource` — Detto in chat, nella conversazione [conversationId]; [app] se la domanda arrivava da un'altra app.
    - `fun app(packageName: String, label: String): AiMemorySource` — Proposto dall'app [packageName] (il nome lo dice il sistema: [label]).
    - `val Manual: AiMemorySource` — Scritto a mano.
    - `fun imported(conversationId: Long?): AiMemorySource` — Dalla memoria di prima, nata nella conversazione [conversationId] (se si sa).
    - `fun tool(conversationId: Long?, app: String? = null, appLabel: String? = null): AiMemorySource` — Chiesto dal modello della chat [conversationId] con lo strumento della memoria; [app] se i dati erano di un'app.
    - `fun research(app: String?, appLabel: String?): AiMemorySource` — Imparato dalla ricerca autorizzata sui dati dell'app [app] (la fonte vera sta nelle prove del ricordo).
- `data class AiMemoryEvidence(val id: String, val grant: String? = null, val app: String? = null, val record: String? = null, val observedAtMillis: Long? = null, val field: String? = null)` — Una prova di un ricordo: da quale fonte autorizzata e' venuto (F9.3b, il profilo della ricerca fuso nella memoria). Revocare la fonte ([grant]) toglie i ricordi dedotti da lei; un dato tolto dall'app ([record]) toglie quelli che ne dipendono.
- `data class AiMemoryExclusion(val key: String? = null, val app: String? = null, val grant: String? = null, val record: String? = null)` — Un'esclusione: cio' che l'utente non vuole che la memoria impari piu' (F9.3b; le esclusioni del profilo della ricerca di oggi). Ogni campo non nullo deve combaciare: [key] e' l'argomento o il suo inizio («musica» esclude «musica.gusti»), oppure il tipo di dato di una prova ([AiMemoryEvidence.field]: «cv.grades» esclude cio' che e' dedotto dai voti); [app] l'app, [grant] e [record] la fonte autorizzata e il dato.
  - `fun matches(key: String?, app: String?, evidence: List<AiMemoryEvidence> = emptyList()): Boolean` — Vero se l'esclusione vale per un ricordo con [key], nato dall'app [app] con le prove [evidence].
- `data class AiMemory(val id: String, val text: String, val key: String? = null, val topics: List<String> = emptyList(), val source: AiMemorySource, val createdAtMillis: Long, val updatedAtMillis: Long = createdAtMillis, val observedAtMillis: Long = createdAtMillis, val expiresAtMillis: Long? = null, val pinned: Boolean = false, val always: Boolean = false, val previousText: String? = null, val previousSinceMillis: Long? = null, val edited: Boolean = false, val value: String? = null, val origin: String = OriginStatement, val evidence: List<AiMemoryEvidence> = emptyList(), val limitations: String? = null, val derivedBy: String? = null)` — Un fatto che l'assistente ricorda dell'utente: «è in 4F al liceo Galilei», «è vegetariana», «la verifica di matematica e' venerdi' 28». Un valore intero e immutabile: chi lo cambia ne fa una copia (regola F7.21).
  - `fun expired(nowMillis: Long): Boolean` — Vero se a [nowMillis] e' gia' scaduto.
  - `companion object`
    - `const val OriginStatement` — Detto o confermato dall'utente (anche scritto a mano), o un dato proposto da un'app.
    - `const val OriginObservation` — Letto da una fonte autorizzata, cosi' com'e' (la ricerca del profilo).
    - `const val OriginInference` — Dedotto da un modello dalle fonti (la ricerca del profilo): ha [limitations] e [derivedBy].
- `data class AiMemoryNotice(val id: String, val kind: String, val memoryId: String, val text: String, val previousText: String? = null, val source: AiMemorySource, val atMillis: Long, val undone: Boolean = false, val stale: Boolean = false, val declined: Boolean = false)` — L'avviso che accompagna ogni ricordo nuovo, perche' **niente si ricordi senza che l'utente lo veda**: di serie la card «Vuoi che ricordi…?» con Ricorda e No ([KindAsk], scelta di Alessio del 26/11/2026), o, col salvataggio automatico acceso, «Ho ricordato…» con Annulla; sotto la risposta o come notifica di vetro, di `engine-ai-ui`.
  - `val canUndo: Boolean get()` — Vero se la card deve offrire Annulla.
  - `val asking: Boolean get()` — Vero se la card deve chiedere «Vuoi che ricordi…?» con Ricorda e No.
  - `companion object`
    - `const val KindRemembered` — Un fatto nuovo: «Ho ricordato: è in 4F».
    - `const val KindUpdated` — Un fatto che ne sostituisce uno con la stessa chiave: «Ho aggiornato: è in 5F (prima: è in 4F)».
    - `const val KindKept` — Un fatto arrivato dopo ma detto prima di quello che c'e' (un'app che propone un dato vecchio): vince il piu' recente, che resta, e l'avviso lo dice («Tengo: è in 5F, è più recente»). Niente da annullare.
    - `const val KindAsk` — Un fatto proposto che aspetta il si' dell'utente: «Vuoi che ricordi: è in 4F?» con Ricorda e No. E' il modo di serie per i ricordi che l'utente non ha chiesto lui (l'estrattore, il modello, un'app, la ricerca): col salvataggio automatico acceso diventano subito [KindRemembered]. Se l'utente dice si', l'avviso diventa [KindRemembered] (o [KindUpdated]) con Annulla.

## `engine-ai/ai/memory/AiMemoryProposal.kt`

- `data class AiMemoryProposal(val text: String, val key: String? = null, val topics: List<String> = emptyList(), val expiresAtMillis: Long? = null, val always: Boolean = false, val observedAtMillis: Long? = null, val value: String? = null, val origin: String = AiMemory.OriginStatement, val evidence: List<AiMemoryEvidence> = emptyList(), val limitations: String? = null, val derivedBy: String? = null)` — Un fatto proposto alla memoria: dall'estrattore dopo un turno, da «ricordati che…», dalla pagina, o da un'altra app Pampa col ponte (ClasseViva propone «è in 4F»). Una proposta non e' ancora un ricordo: decide la memoria di PampAI (`AiMemoryBook` di `engine-ai-memory`), che la controlla, la confronta con quello che sa e ne fa un avviso.
  - `fun sanitized(): AiMemoryProposal?` — La stessa proposta nei limiti: i caratteri di controllo tolti, gli spazi ripiegati, il testo tagliato a [MaxTextChars] (a una parola), la chiave normalizzata ([normalizeKey]), al piu' [MaxTopics] parole di [MaxTopicChars], il valore a [MaxValueChars]. Null se non resta un fatto.
  - `fun toJson(): String` — Il JSON della proposta, come passa nel ponte: `{"text", "key"?, "topics"?, "expiresAt"?, "always"?, "observedAt"?, "value"?}`. L'origine, le fonti, i limiti e chi l'ha dedotto non passano: un'app propone un dato suo, non una prova.
  - `companion object`
    - `const val MaxTextChars` — Quanto e' lungo al piu' un fatto: 300 caratteri, come la memoria di oggi di PampAI (`MemoryRepository.MAX_CHARS`), cosi' i ricordi di prima passano senza tagli. L'estrattore ne scrive di molto piu' corti (una riga).
    - `const val MaxKeyChars` — Quanto e' lunga al piu' una chiave.
    - `const val MaxTopics` — Quante parole al piu' accompagnano un fatto.
    - `const val MaxTopicChars` — Quanto e' lunga al piu' una parola.
    - `const val MaxValueChars` — Quanto e' lungo al piu' un valore strutturato.
    - `fun cleanText(raw: String): String?` — Il testo di un fatto pulito (caratteri di controllo, spazi, le virgolette che lo chiudono tutto) e tagliato a [MaxTextChars]; null se vuoto. Le virgolette dentro il fatto restano: «firma con «Giulia»».
    - `fun normalizeKey(key: String?): String?` — La chiave in minuscolo, con gli spazi diventati punti e solo lettere, cifre, `.`, `-`, `_`; null se vuota.
    - `fun normalizeTopics(topics: List<String>): List<String>` — Le parole di un fatto ripulite: in minuscolo, di lettere e cifre, da 2 a [MaxTopicChars], al piu' [MaxTopics].
    - `fun fromJson(json: String): AiMemoryProposal?` — La proposta di [json] (il formato di [toJson]), o null se non e' una proposta. Non la ripulisce: [sanitized].
    - `fun fromJsonElement(e: JsonElement?): AiMemoryProposal?` — Come [fromJson], da un elemento gia' letto (la proposta dentro una richiesta del ponte).
- `object AiMemoryProtocol` — Il ricordo proposto da un'altra app, passando da PampAI (F9.3 sopra il contratto inverso di F9.1): i metodi e il formato. Il trasporto, l'identita' di chi chiama e la tabella delle app sono quelli di F9.1 (`ContentResolver.call` sul provider dell'assistenza di PampAI, la firma Pampa, il pacchetto detto dal sistema); qui c'e' solo cosa si scrive.
  - `const val Version` — La versione del formato dei ricordi.
  - `const val MethodPropose`
  - `const val MethodAnswer`
  - `const val MethodUndo`
  - `const val MethodForgetMine`
  - `val Methods: Set<String>` — I quattro metodi, per chi smista le chiamate.
  - `fun answerRequest(noticeId: String, accept: Boolean): String` — La richiesta di [MethodAnswer]: [accept] vero per Ricorda, falso per No.
  - `fun proposeRequest(proposal: AiMemoryProposal): String` — La richiesta di [MethodPropose].
  - `fun undoRequest(noticeId: String): String` — La richiesta di [MethodUndo].
  - `fun forgetMineRequest(): String` — La richiesta di [MethodForgetMine].
- `data class AiMemoryProposalResult(val outcome: String, val noticeId: String? = null, val text: String? = null, val previousText: String? = null, val count: Int? = null)` — Com'e' andata una proposta (o un annulla, o una revoca) arrivata col ponte. [outcome] e' una delle costanti `Outcome*`: un'app che riceve un esito che non conosce lo tratta come [OutcomeRefused].
  - `val remembered: Boolean get()` — Vero se adesso PampAI lo ricorda (nuovo o aggiornato).
  - `fun notice(source: AiMemorySource, atMillis: Long): AiMemoryNotice?` — L'avviso da mostrare nell'app che ha proposto (la card o la notifica di `engine-ai-ui`), o null se non c'e' niente da dire. [source] e' l'app stessa. Per [OutcomeKept] niente, come per [OutcomeDuplicate]: il fatto non e' entrato, e quello che resta lo vede l'utente in PampAI (R3-S1).
  - `fun toJson(): String`
  - `companion object`
    - `const val OutcomeRemembered` — Ricordato: e' nuovo.
    - `const val OutcomeUpdated` — Ricordato al posto di un fatto con la stessa chiave.
    - `const val OutcomeKept` — Non entrato: PampAI sa gia' un fatto piu' recente con la stessa chiave, che resta.
    - `const val OutcomeDuplicate` — Lo sapeva gia', uguale: niente di nuovo, nessun avviso.
    - `const val OutcomeRefused` — Non si ricorda: sembra un segreto (una password, un codice), o non e' un fatto.
    - `const val OutcomeNotAllowed` — L'app non puo' proporre ricordi, non di questo argomento, o l'utente gliel'ha tolto.
    - `const val OutcomeLimitReached` — Troppe proposte oggi da quest'app, o la memoria e' piena.
    - `const val OutcomeInvalid` — La richiesta non si legge.
    - `const val OutcomeUndone` — Annullato.
    - `const val OutcomeNotFound` — Niente da annullare: l'avviso non c'e', e' di un'altra app, o il ricordo e' cambiato nel frattempo.
    - `const val OutcomeForgotten` — Dimenticati ([count]).
    - `const val OutcomePending` — In attesa del si' dell'utente (il modo di serie): la card chiede «Vuoi che ricordi…?», e la risposta arriva con [AiMemoryProtocol.MethodAnswer] o dalla pagina di PampAI. [noticeId] e' la domanda.
    - `const val OutcomeDeclined` — L'utente ha detto No a una domanda: il fatto non entra.
    - `const val OutcomeExcluded` — L'utente ha chiesto di non imparare piu' questo argomento (o da questa fonte): non entra.
    - `fun fromJson(json: String): AiMemoryProposalResult` — L'esito di [json], o un [OutcomeInvalid] se non si legge.
