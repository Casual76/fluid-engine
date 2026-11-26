<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/people.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-people

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-people/people/PeopleClient.kt`

- `object PeoplePolicy` — Le regole delle persone che non sono numeri del server. Ognuna è una costante sola, facile da cambiare.
  - `const val RENEWAL_DROPS_WORDS_VERIFICATION` — Dopo un rinnovo delle chiavi di un contatto, la verifica **a distanza** (quattro parole) è da rifare, come il numero di sicurezza di Signal (brief di F10.6). La verifica di persona (QR, vicinanza) resta: ha visto la radice. Con `false` resterebbe anche quella a distanza, perché le chiavi nuove sono firmate dalla radice già verificata.
  - `const val AUTO_ACCEPT_QR` — Un QR personale con la prova giusta fa entrare subito, come il `pairing_auto` dell'alpha: l'altro era lì.
  - `const val GREETING_MAX` — Il saluto di una richiesta: 280 caratteri al massimo (`progetto.md` § 3.6).
  - `const val PER_CALL` — Buste per chiamata verso una casella, e caselle per chiamata di gruppo (contratto `limits.people`).
  - `const val GROUP_PER_CALL`
  - `const val QR_TTL_MS` — Il QR personale vale 24 ore e una volta (contratto `people.inviteQr`).
- `class PeopleConfig(val storeVersion: String = OutEnvelope.MIN_STORE, val minStore: String = OutEnvelope.MIN_STORE, val renewalDropsWordsVerification: Boolean = PeoplePolicy.RENEWAL_DROPS_WORDS_VERIFICATION, val autoAcceptQr: Boolean = PeoplePolicy.AUTO_ACCEPT_QR)`
- `fun interface OutboxScheduler` — Quando riprovare la coda in uscita: lo Store la collega a WorkManager (`PeopleWork`), le prove a niente.
  - `fun requestFlush(delayMillis: Long)`
  - `companion object`
    - `val None`
- `sealed interface PeopleEvent` — Quello che il ritiro ha trovato, busta per busta. [n] è il numero della busta nella casella.
  - `val n: Long`
  - `data class Request(override val n: Long, val request: IncomingRequest) : PeopleEvent`
  - `data class Accepted(override val n: Long, val code: String) : PeopleEvent`
  - `data class Message(override val n: Long, val code: String, val thread: String, val payload: JsonObject, val messageId: String, val group: String? = null) : PeopleEvent`
  - `data class ChatKey(override val n: Long, val code: String, val epoch: Int) : PeopleEvent`
  - `data class WordsReady(override val n: Long, val code: String) : PeopleEvent`
  - `data class WordsChanged(override val n: Long, val code: String, val byThem: Boolean) : PeopleEvent` — Le parole con [code] sono cambiate: [byThem] se ha rinnovato lui, altrimenti ho rinnovato io.
  - `data class Join(override val n: Long, val group: String, val member: GroupMember, val card: PersonCard) : PeopleEvent`
  - `data class GroupUpdated(override val n: Long, val group: String, val epoch: Int) : PeopleEvent`
  - `data class RemovedFromGroup(override val n: Long, val group: String) : PeopleEvent`
  - `data class SpaceKeys(override val n: Long, val space: String, val owner: String) : PeopleEvent`
  - `data class FromMyDevices(override val n: Long, val payload: JsonObject) : PeopleEvent`
  - `data class RubricaSynced(override val n: Long, val code: String) : PeopleEvent`
  - `data class Detected(override val n: Long, val what: String) : PeopleEvent`
  - `data class TooNew(override val n: Long, val minStore: String) : PeopleEvent` — La busta chiede uno Store più nuovo (critica M2): la card «Aggiorna Pampa Store per vederlo».
- `data class FlushReport(val sent: Int, val kept: Int, val undeliverable: List<String>, val offline: Boolean)` — Com'è andata la coda: quante buste sono partite, quante restano, e quelle che non arriveranno mai.
- `data class WordsView(val words: List<String>, val gem: ByteArray, val verified: Boolean, val notice: ContactNotice?)` — Le parole di una coppia, come le mostra la conversazione.
- `data class GroupInvite(val group: String, val token: String, val admin: PersonCard)` — Il QR di un gruppo, da proiettare in classe: 24 ore, 100 ingressi, un amministratore approva.
- `class PeopleClient(val persona: PersonaStore, private val stateStore: PeopleStateStore, private val api: PeopleApi, val config: PeopleConfig = PeopleConfig(), private val clock: () -> Long = System::currentTimeMillis, private val scheduler: OutboxScheduler = OutboxScheduler.None)` — Le persone sul telefono (`d\server-e-chiavi.md` § 4–5 e § 10; `progetto.md` § 3.6–3.10). È lo Store che lo usa: tiene la persona, la rubrica e la casella, e parla col server con [api].
  - `val state: PeopleState get()` — Lo stato di adesso (rubrica, richieste, posta). Per leggere; si cambia solo dai metodi del client.
  - `fun identity(): PersonIdentity`
  - `fun contact(code: String): Contact?`
  - `fun isVerified(code: String): Boolean` — Il contatto è verificato **adesso**? Di persona sì sempre; a distanza solo con le parole delle chiavi di oggi.
  - `suspend fun publish(findable: Boolean? = null)` — Pubblica (o aggiorna) la scheda. La prima volta apre la casella.
  - `suspend fun lookup(code: String, invite: String? = null): PersonCard` — La scheda di [code], controllata sul telefono: resta «da verificare» (critica S5).
  - `suspend fun request(card: PersonCard, greeting: String, via: ContactVia = ContactVia.CODE, invite: String? = null, qrProof: String? = null): Contact` — Manda una richiesta a [card]: la mia scheda, un saluto, il gettone per scrivermi, la chiave della conversazione e l'**impegno** delle parole. Il contatto resta in attesa finché non accetta.
  - `suspend fun newQr(): PersonQr` — Il mio QR personale: la scheda intera, un invito da 24 ore e un uso, e un segreto che resta qui.
  - `suspend fun requestFromQr(qr: PersonQr, greeting: String = ""): Contact` — Ho inquadrato il QR di qualcuno: la scheda è arrivata senza intermediari, quindi **verificata subito**. La richiesta porta il suo invito e la prova di aver visto il QR, così anche lui sa che sono io (critica S5).
  - `suspend fun accept(req: IncomingRequest): Contact` — Accetta una richiesta: il mio gettone per lui, e il mio nonce delle parole.
  - `suspend fun ignore(req: IncomingRequest)` — Ignora una richiesta: sparisce e basta, chi l'ha mandata non lo sa.
  - `suspend fun block(mailbox: String)` — Blocca: come togliere, più l'impronta della sua casella fra i bloccati. Lo scarto è silenzioso. Vale **subito** sul telefono, anche senza rete (R2, regola R6): le sue buste si scartano qui, e le impronte partono con la coda.
  - `suspend fun unblock(mailbox: String)`
  - `suspend fun remove(code: String)` — Togli: la mia casella dimentica il gettone che gli avevo dato. Lui tiene quello che ha già. Vale subito (R2).
  - `fun words(code: String): WordsView?` — Le parole con [code], se lo scambio con l'impegno è finito; altrimenti `null` («le parole arrivano fra poco»).
  - `suspend fun markWordsVerified(code: String)` — Le parole tornavano al telefono: [code] è verificato a distanza, per le chiavi di oggi.
  - `suspend fun dismissNotice(code: String)` — L'utente ha visto l'avviso delle parole cambiate.
  - `suspend fun sendMessage(code: String, payload: JsonObject, priority: EnvelopePriority = EnvelopePriority.HIGH): String` — Un messaggio (o una cosa condivisa) nella conversazione con [code]: una PMX1, chiusa adesso, in coda.
  - `suspend fun sendToMyDevices(payload: JsonObject)` — «I tuoi dispositivi» (scelta Q1): una CDXB a me stesso, che apre ogni mio dispositivo. Il server non tiene cronologie.
  - `suspend fun flush(): FlushReport` — Riprova le buste in coda. Senza rete restano tutte; un rifiuto definitivo (`gone`, `not_found`) le toglie.
  - `suspend fun onBell(): List<PeopleEvent>` — Il campanello (o il canale aperto) ha suonato: ritira, apre, e riprova la coda.
  - `suspend fun pickup(): List<PeopleEvent>` — Il ritiro di questo dispositivo: prende tutto dopo il suo punto, lo apre e lo controlla, poi conferma fin dove è arrivato, così la casella può cancellare ciò che tutti i dispositivi hanno preso. Ogni cosa che non torna è un [PeopleEvent.Detected]: il telefono non la usa.
  - `suspend fun rotateWork(): PersonIdentity` — «Perso o rubato», o i sei mesi (`d\server-e-chiavi.md` § 3): chiavi di lavoro nuove firmate dalla stessa radice, scheda nuova, e per ogni contatto una chiave nuova della conversazione con un impegno nuovo delle parole. Il telefono rubato, già fatto uscire, non apre niente di ciò che arriva dopo. Serve la radice: a telefono sbloccato.
  - `suspend fun createGroup(name: String): PeopleGroup` — Un gruppo nuovo, di cui sono l'amministratore. Il nome viaggia solo cifrato, dentro le buste.
  - `suspend fun groupInvite(groupId: String): GroupInvite` — Il QR del gruppo: 24 ore, 100 ingressi. Chi lo inquadra chiede di entrare, e io approvo.
  - `suspend fun joinGroup(invite: GroupInvite)` — Ho inquadrato il QR di un gruppo: chiedo di entrare all'amministratore, col mio gettone per il gruppo.
  - `suspend fun approve(groupId: String, joins: List<PeopleEvent.Join>)` — Approvo chi ha chiesto di entrare, e mando a tutti lo stato del gruppo: chiavi, membri, gettoni.
  - `suspend fun groupMessage(groupId: String, payload: JsonObject): String` — Un messaggio al gruppo: una PMX1 per ogni membro, ognuna con le due caselle nei dati associati.
  - `suspend fun removeFromGroup(groupId: String, codes: List<String>)` — Tolgo dei membri (P6: chi esce tiene il passato e perde il futuro): epoca nuova, chiave nuova, gettoni nuovi per chi resta. Lo stato nuovo parte coi gettoni **vecchi**, gli unici che le caselle accettano ancora; chi esce riceve «fuori».
  - `fun exportRubrica(accountId: String, epoch: Int, kRubrica: ByteArray, version: Long): String` — La rubrica chiusa per il server sotto `K_com(rubrica)` all'epoca [epoch] (`b\chiavi.md` § 12): i contatti coi gettoni e le conversazioni, i bloccati, i gruppi e gli spazi. Un telefono nuovo, o un altro dispositivo dopo un «perso o rubato», la riprende con [importRubrica] insieme alla persona. Dati associati `accountId|com.pampa.store|rubrica|contatti|<versione>`.
  - `suspend fun importRubrica(accountId: String, field: String, version: Long, keyFor: (Int) -> ByteArray?)` — Riprende la rubrica dalla copia del server. Quello che c'è già sul telefono e non è nella copia resta.
  - `companion object`
    - `fun newer(a: String, b: String): Boolean` — [a] è una versione più nuova di [b]? `1.2.0` contro `1.10.0`, numero per numero.

## `engine-people/people/PeopleState.kt`

- `enum class ContactVia(val wire: String)` — Come una persona è entrata in rubrica (`progetto.md` § 3.6): di persona è verificata subito, a distanza no.
- `enum class VerifyMethod(val wire: String)` — Come è stata verificata: di persona (QR, vicinanza: la chiave intera) o con le quattro parole al telefono.
- `data class Verification(val method: VerifyMethod, val myEpoch: Int, val theirEpoch: Int, val at: Long)` — La verifica, con le epoche di lavoro dei due quando è stata fatta.
- `data class WordsExchange(val committer: Boolean, val myEpoch: Int, val theirEpoch: Int, val myNonce: ByteArray, val theirCommit: ByteArray? = null, val theirNonce: ByteArray? = null, val revealed: Boolean = false)` — Lo scambio delle quattro parole con l'impegno (rapporto F10.6, il «compleanno»). Chi comincia ([committer]) tiene il suo nonce e manda solo l'impegno; chi risponde manda il suo nonce; poi chi ha cominciato rivela il suo. Le parole si possono mostrare solo quando ci sono i due nonce ([ready]). Vale per la coppia di epoche [myEpoch] e [theirEpoch]: a un rinnovo ne comincia uno nuovo.
  - `val ready: Boolean get()`
- `enum class ContactNotice(val wire: String)` — Quello che la conversazione deve dire, una volta, finché l'utente non l'ha visto.
- `data class Contact(val card: PersonCard, val theirToken: String?, val myToken: String, val chatId: String, val chatKeys: Map<Int, ByteArray>, val chatEpoch: Int, val via: ContactVia, val verification: Verification? = null, val words: WordsExchange? = null, val notice: ContactNotice? = null, val nickname: String? = null, val addedAt: Long = 0, val lastActivity: Long = 0)` — Un contatto della rubrica (`d\persone-e-sintonia.md` § 4.1): la sua scheda, i due gettoni di consegna, la conversazione a epoche, come è entrato, se e come è verificato. La rubrica sta in classe consegna; la copia per gli altri tuoi dispositivi viaggia in una CDXB a te stesso.
  - `val code: String get()`
  - `val name: String get()`
  - `val accepted: Boolean get()`
- `data class IncomingRequest(val n: Long, val card: PersonCard, val greeting: String, val token: String, val chatId: String, val chatKey: ByteArray, val via: String?, val invite: String?, val wordsCommit: ByteArray?, val qrProof: String?, val at: Long)` — Una richiesta arrivata, da accettare, ignorare o bloccare. Porta solo la scheda e un saluto.
- `data class GroupMember(val name: String, val code: String, val mailbox: String, val root: String, val enc: String, val sign: String, val workEpoch: Int, val token: String)` — Un membro di un gruppo come lo conosce l'amministratore: niente codice da mostrare agli altri (P3).
- `data class PeopleGroup(val id: String, val name: String, val admin: String, val epoch: Int, val keys: Map<Int, ByteArray>, val members: Map<String, GroupMember>, val myToken: String)`
- `data class GivenInvite(val token: String, val kind: String, val group: String? = null, val qrSecret: ByteArray? = null, val expiresAt: Long = 0)` — Un invito che ho dato io (QR, link, gruppo): solo io so che è mio. Il segreto del QR non va mai al server.
- `data class OutboxItem(val id: String, val envelope: OutEnvelope, val group: Boolean, val attempts: Int, val notBefore: Long, val createdAt: Long, val label: String)` — Una busta già chiusa e firmata nella coda in uscita (critica M3): si riprova da sola, senza chiavi.
- `data class InboxItem(val id: String, val app: String, val from: String, val kind: String, val title: String, val payload: JsonObject, val at: Long, val thread: String, val notified: Boolean = false, val hidden: Boolean = false, val replies: List<JsonObject> = emptyList(), val inReplyTo: String? = null)` — Una cosa arrivata per un'app (`share.received`): già aperta dallo Store, con chi la manda e quando. [app] è il pacchetto che la deve mostrare; [payload] è quello che l'app ha preparato (link, copia, oggetto vivo).
- `class PeopleState` — Tutto quello che lo Store sa delle persone, tranne la persona (che sta in [PersonaStore]): la rubrica, le richieste, i bloccati, i gruppi, gli inviti, la coda in uscita, il punto di ritiro di questo dispositivo, le cose arrivate per le app. Un file solo, chiuso in classe consegna.
  - `val contacts`
  - `val requests`
  - `val blocked`
  - `val groups`
  - `val pendingGroupTokens`
  - `val invites`
  - `val outbox`
  - `val inbox`
  - `val seenMessages` — Gli id dei messaggi già presi (PMX1): lo stesso messaggio non entra due volte.
  - `val mutedInApps` — I dispositivi che non mostrano più «Da X» in un'app: `app|codice`.
  - `val undispatched` — Le consegne del ritiro ancora da smistare alle app (R1): le cose condivise, «I tuoi dispositivi», le richieste e le buste per uno Store più nuovo ([forApps]). Il ritiro le scrive nello stesso salvataggio del cursore e degli id visti, e `PeopleDispatcher` le toglie nello stesso salvataggio della posta: un errore alla pagina dopo, la scadenza del campanello o un riavvio a metà non le perdono.
  - `val spaces` — Gli spazi condivisi di cui ho la chiave (`SharedSpaces`), così come arrivano: id → stato.
  - `var cursor: Long`
  - `var rubricaVersion: Long`
  - `var deviceTag: String` — Un nome casuale di questo dispositivo: le buste a me stesso che ho mandato io non le riapplico.
  - `var appSalt: String` — Il sale degli id della fila delle persone: ogni app vede id suoi, che non valgono in un'altra app.
  - `fun toJson(): JsonObject`
  - `companion object`
    - `fun fromJson(o: JsonObject): PeopleState`
    - `fun contactJson(c: Contact): JsonObject`
    - `fun contact(o: JsonObject): Contact`
    - `fun memberJson(m: GroupMember): JsonObject`
    - `fun member(o: JsonObject)`
- `class PeopleStateStore(private val file: File, private val vault: PersonVault)` — Il file dello stato delle persone, chiuso in classe consegna ([PersonKeyRole.WORK]).
  - `fun load(): PeopleState`
  - `fun save(state: PeopleState)`
  - `fun reload(): PeopleState` — Rilegge dal disco: un altro processo (il campanello in sottofondo) può averlo cambiato.
  - `fun wipe()`

## `engine-people/people/PeopleWork.kt`

- `interface PeopleWorkHost` — Lo Store che tiene le persone: di solito la sua `Application`. Il lavoro di WorkManager parte anche a Store chiuso, in un processo nuovo, e chiede qui il client (`null` se la persona non c'è ancora o nessuno è entrato).
  - `fun peopleClient(): PeopleClient?`
- `class PeopleOutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params)` — La coda in uscita in sottofondo (critica M3): manda le buste già chiuse e firmate. Senza rete chiede di riprovare (WorkManager riparte quando la rete torna, con pause crescenti); quello che resta per un tetto o una casella piena lo riprende il lavoro successivo, che la coda stessa chiede all'ora giusta. Non apre mai la radice: col telefono in tasca va come a telefono sbloccato (Q7 A).
- `object PeopleWork` — L'[OutboxScheduler] con WorkManager: un lavoro unico ([UNIQUE]) con la rete come vincolo.
  - `const val UNIQUE`
  - `val constraints: Constraints`
  - `fun request(context: Context, delayMillis: Long)` — Chiede di riprovare la coda fra [delayMillis]; l'ultima richiesta sostituisce quella in attesa.
  - `fun scheduler(context: Context): OutboxScheduler` — Lo scheduler da passare a [PeopleClient].

## `engine-people/people/Persona.kt`

- `enum class PersonKeyRole` — Le due classi della custodia per la persona (`b\chiavi.md` § 3.3 e § 12; Q7, risposta A di Alessio): - [ROOT], la radice Ed25519: classe **madre**, aperta solo a telefono sbloccato. Serve solo per le cose rare: firmare la scheda e le chiavi di lavoro nuove; - [WORK], le chiavi di lavoro, la rubrica e la posta: classe **consegna**, aperta dal primo sblocco.
- `interface PersonVault` — La cassaforte della persona. Lo Store la collega alla sua custodia (`CustodyVault` di `engine-account-host`), con [PersonKeyRole.ROOT] nella classe di `CustodyKeyKind.PERSON_ROOT` e [PersonKeyRole.WORK] in quella di `CustodyKeyKind.WORK_KEYS`: la classe di ogni chiave resta una costante sola, `KeyClassPolicy`. Pampa Mail non la usa: non ha la persona.
  - `fun seal(role: PersonKeyRole, plaintext: ByteArray, aad: ByteArray): ByteArray`
  - `fun open(role: PersonKeyRole, sealed: ByteArray, aad: ByteArray): ByteArray` — Apre ciò che ha chiuso [seal]; [PersonLockedException] se la classe è chiusa (la madre a telefono bloccato).
- `class PersonLockedException(message: String = "la custodia della persona è chiusa") : Exception(message)` — La classe che serve è chiusa: la radice a telefono bloccato, o tutto prima del primo sblocco. Si riprova dopo.
- `class PersonIdentity(val card: PersonCard, val works: Map<Int, WorkKeys>)` — Chi sono, per le persone: la scheda e le chiavi di lavoro di ogni epoca. Niente radice: si legge anche in tasca.
  - `val work: WorkKeys get()`
  - `val code: String get()`
  - `val mailbox: String get()`
- `class PersonaStore(private val dir: File, private val vault: PersonVault, private val clock: () -> Long = System::currentTimeMillis)` — La persona sul telefono (`d\server-e-chiavi.md` § 3; `b\chiavi.md` § 12). Nasce la prima volta che apri «Persone» o condividi con qualcuno ([create], `progetto.md` § 3.5): prima di allora non esiste e non si pubblica niente (R3).
  - `val exists: Boolean get()` — C'è una persona su questo telefono? Non apre niente.
  - `fun create(name: String, mineral: ByteArray = PampaCrypto.random(16)): PersonIdentity` — La persona nuova: radice, chiavi di lavoro dell'epoca 1, scheda firmata. Serve la classe madre.
  - `fun identity(): PersonIdentity?` — La persona di questo telefono; [PersonLockedException] prima del primo sblocco, `null` se non c'è.
  - `fun root(): Ed25519Key` — La radice; [PersonLockedException] a telefono bloccato. Solo per firmare.
  - `fun rename(name: String): PersonIdentity` — Il nome nuovo (scelto nello Store): scheda nuova, stessa radice.
  - `fun rotateWork(): PersonIdentity` — «Perso o rubato», o i sei mesi: chiavi di lavoro nuove firmate dalla radice, all'epoca dopo. Le vecchie si tengono per aprire le buste già arrivate; quelle che arrivano dopo sono per le nuove, che il telefono rubato non ha.
  - `fun wipe()` — Uscita, uscita da remoto o eliminazione: via la persona da questo telefono. Il server la tiene sotto `K_com`.
  - `fun exportSealed(accountId: String, epoch: Int, kPersona: ByteArray, version: Long): String` — La persona chiusa per il server sotto `K_com(persona)` all'epoca [epoch] della chiave dei dati: così un telefono nuovo, che apre la chiave dei dati con una porta, ritrova anche la persona (`b\chiavi.md` § 12: le private sono protette dalle stesse porte). È la busta del contratto `{ v, e, alg, n, c }`, coi dati associati `accountId|com.pampa.store|persona|chiavi|<versione>`.
  - `fun importSealed(accountId: String, field: String, version: Long, keyFor: (Int) -> ByteArray?): PersonIdentity` — Ritrova la persona dalla copia del server (telefono nuovo, o un altro dispositivo dopo un «perso o rubato»).
  - `companion object`
    - `const val STORE_PACKAGE` — Il pacchetto dello Store nei dati associati delle copie della persona e della rubrica.

## `engine-people/people/client/PeopleApi.kt`

- `class PeopleRequest(val method: String, val path: String, val body: JsonObject? = null, val token: String? = null)` — Una richiesta verso le rotte delle persone (`b\api.md` § 4.11 ter). Niente segreti nell'indirizzo (critica S14).
- `class PeopleResponse(val status: Int, val json: JsonObject?)` — La risposta: lo stato HTTP e il JSON, se c'è.
- `fun interface PeopleHttp` — Il trasporto verso `account.pampa-apps.com`, sostituibile nelle prove (il server finto, MockWebServer). Un errore di rete è [IOException]: per la coda in uscita vuol dire «offline, riprova», non «errore».
  - `suspend fun execute(request: PeopleRequest): PeopleResponse`
- `fun interface PeopleAuth` — Il token con cui si chiama: la sessione dello Store, o il token del pubblico `persone` (Store e Pampa Mail).
  - `suspend fun token(): String`
- `class UrlPeopleHttp(private val baseUrl: String, private val storeVersion: String) : PeopleHttp` — Il trasporto di serie, con `HttpURLConnection` come il resto dell'account: niente librerie in più nello Store.
- `class PeopleServerException(val status: Int, val code: String, override val message: String, val reason: String? = null, val retryAfterSeconds: Long? = null, val accepted: Int? = null) : Exception(message)` — Un rifiuto del server, col codice del contratto (`errors.json`): `rate_limited` (429, con [retryAfterSeconds]), `mailbox_full` (507), `gone` (410, la persona ha lasciato Pampa), `not_found`, `conflict`, `forbidden`… [accepted] è quante buste della chiamata sono passate prima del primo rifiuto: sono sempre le prime.
  - `val retryable: Boolean get()` — Riprovare più tardi ha senso (troppe richieste, casella piena, servizio giù).
- `enum class EnvelopePriority(val wire: String)` — Alta solo per ciò che diventa una notifica, normale per ciò che aggiorna in silenzio (`d\server-e-chiavi.md` § 6).
- `class OutEnvelope(val to: String, val data: String, val token: String? = null, val invite: String? = null, val minStore: String = MIN_STORE, val priority: EnvelopePriority = EnvelopePriority.HIGH)` — Una busta già chiusa e firmata, pronta per la casella [to]. Il gettone o l'invito viaggiano accanto, in chiaro.
  - `fun toJson(withTo: Boolean = false): JsonObject`
  - `companion object`
    - `const val MIN_STORE` — La versione minima dello Store per aprire le buste di F10.6 (critica M2).
    - `fun fromJson(o: JsonObject): OutEnvelope`
- `class PickedEnvelope(val n: Long, val kind: String, val from: String, val data: String, val via: String?, val minStore: String, val priority: String, val at: Long)` — Una busta ritirata dalla casella: il numero, se è una richiesta, chi la manda (lo dice il server), i byte.
- `class PickupPage(val envelopes: List<PickedEnvelope>, val more: Boolean, val last: Long)`
- `class SpaceRow(val recordId: String, val version: Long, val epoch: Int, val ciphertext: String, val deleted: Boolean, val seq: Long)` — Un record di uno spazio come lo rende il server: chi l'ha scritto non c'è, sta dentro, firmato.
- `class SpacePage(val records: List<SpaceRow>, val last: Long, val more: Boolean)`
- `class PeopleApi(private val http: PeopleHttp, private val auth: PeopleAuth)` — Le rotte delle persone (`b\api.md` § 4.11 ter; `Pampa-account\src\routes\people.ts`, `casella.ts`, `spazi.ts`), con le forme del contratto. Il codice cercato viaggia nel corpo (`personCode`), mai nell'indirizzo.
  - `suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject`
  - `suspend fun putCard(card: PersonCard, findable: Boolean? = null): JsonObject`
  - `suspend fun setFindable(findable: Boolean): JsonObject`
  - `suspend fun lookup(code: String, invite: String? = null): PersonCard` — La scheda di [code], **controllata sul telefono**: codice e casella dalla radice, firme, e che sia proprio quel codice. Un server che rende la scheda di un altro è [PeopleDetected]. Ogni codice nuovo conta nei 20 del giorno.
  - `suspend fun send(to: String, envelopes: List<OutEnvelope>): Int` — Buste per una casella (al massimo 45; una richiesta viaggia da sola). Restituisce quante ne ha accettate.
  - `suspend fun sendMany(envelopes: List<OutEnvelope>): List<String>` — Un gruppo: fino a 100 caselle, che il server consegna a pezzi da 45. [List] dei risultati, uno per busta.
  - `suspend fun pickup(after: Long, limit: Int? = null): PickupPage` — Il ritiro, per questo dispositivo: [after] conferma fin dove ha già preso.
  - `suspend fun addPrint(kind: String, print: String): JsonObject` — L'impronta di un gettone di consegna, di un bloccato o di un invito (`delivery`, `blocked`, `invite.qr|link|group`).
  - `suspend fun removePrint(print: String)`
  - `suspend fun createSpace(spaceId: String, kind: String, writePrint: String): JsonObject`
  - `suspend fun writeRecord(spaceId: String, writeToken: String, recordId: String, baseVersion: Long, epoch: Int, ciphertext: String, deleted: Boolean = false): Pair<Long, Long>` — Scrive un record sulla versione [baseVersion] (0 per uno nuovo); `conflict` se nel frattempo è cambiato.
  - `suspend fun readSpace(spaceId: String, since: Long): SpacePage`
  - `suspend fun rotateSpace(spaceId: String, writeToken: String, epoch: Int, writePrint: String): JsonObject`
  - `suspend fun deleteSpace(spaceId: String): JsonObject`

## `engine-people/people/client/PeopleSocket.kt`

- `data class ChannelClosed(val code: Int, val reason: String)` — Perché il canale si è chiuso: dal server (col codice, per esempio 4003 «dispositivo uscito»), o per la rete.
- `class PeopleSocket(private val url: String, private val auth: PeopleAuth, private val storeVersion: String, private val pingEveryMillis: Long = 30_000, private val connect: (host: String, port: Int, tls: Boolean) -> Socket = ::defaultSocket)` — Il canale aperto con la casella (`GET /v1/casella/ws`, `d\server-e-chiavi.md` § 5): un WebSocket con ibernazione, **solo mentre lo Store o Pampa Mail sono aperti**. Porta solo «c'è posta» (`{"t":"posta","n":…}`); il telefono manda al massimo «ping», a cui il server risponde da sé senza svegliare la casella. Si chiude con il token (15 minuti).
  - `suspend fun run(onMail: suspend (Long) -> Unit): ChannelClosed` — Apre il canale e resta in ascolto finché si chiude; [onMail] riceve il numero dell'ultima busta.
  - `fun close()` — Chiude il canale (l'app va in sottofondo): da lì in poi suona il campanello.
  - `companion object`
    - `fun defaultSocket(host: String, port: Int, tls: Boolean): Socket`

## `engine-people/people/crypto/Cdxm.kt`

- `object Cdxm` — Foto, voci e file cifrati **a blocchi**: il formato `CDXM` di Codex (`MediaCipher`), tenuto **così com'è**, al byte (`d\server-e-chiavi.md` § 2). La memoria occupata è quella di un blocco da 64 KiB anche con una foto da 20 MB.
  - `const val CHUNK`
  - `class Malformed(message: String) : Exception(message)`
  - `fun newKey(): ByteArray` — Una chiave nuova per un file.
  - `fun keyFor(chatKey: ByteArray, mediaId: String): ByteArray` — La chiave di un media dell'alpha: `HKDF(chiave della chat, "codex-media-v1:<id>")`.
  - `fun encryptedSize(plainLength: Long): Long` — Quanto occuperà un file cifrato: serve a dire «non ci sta» prima di cominciare (25 MB a volta).
  - `fun encrypt(key: ByteArray, input: InputStream, output: OutputStream, plainLength: Long)`
  - `fun decrypt(key: ByteArray, input: InputStream, output: OutputStream)`
- `class EpochKeyring private constructor(private val keys: java.util.TreeMap<Int, ByteArray>)` — Le chiavi a epoche di un gruppo o di uno spazio condiviso (`GroupSecret` di Codex; `d\server-e-chiavi.md` § 11): 32 byte casuali per epoca, consegnati a ogni membro con una CDXB. Quando qualcuno esce si apre un'epoca nuova, data solo a chi resta: **chi esce tiene il passato e perde il futuro** (P6). Per questo si tengono tutte le epoche.
  - `val current: Int get()`
  - `val currentKey: ByteArray get()`
  - `val epochs: Set<Int> get()`
  - `fun key(epoch: Int): ByteArray?`
  - `fun rotate(): Int` — Un'epoca nuova, con una chiave nuova.
  - `fun merge(other: Map<Int, ByteArray>)` — Le chiavi arrivate in una busta: si aggiungono, non si sostituiscono quelle che si hanno già.
  - `fun snapshot(): Map<Int, ByteArray>`
  - `companion object`
    - `fun create(): EpochKeyring`
    - `fun of(keys: Map<Int, ByteArray>): EpochKeyring`

## `engine-people/people/crypto/ItalianWords.kt`

- `object ItalianWords` — L'elenco italiano di 2.048 parole delle quattro parole della verifica a distanza (`d\server-e-chiavi.md` § 4; contratto `e2e.json` → `people.formulas.words`): ogni parola porta 11 bit, quattro parole 44 bit.
  - `val all: List<String> by lazy` — Le 2.048 parole, in ordine: l'indice è il valore degli 11 bit.
  - `fun word(index: Int): String` — La parola dell'indice [index] (0–2047).
  - `fun indexOf(word: String): Int` — L'indice di [word], o −1 se non è nell'elenco. Bastano le prime quattro lettere, che nell'elenco sono uniche.

## `engine-people/people/crypto/Nearby.kt`

- `class SeededRandom(seed: Long)` — Il generatore pseudocasuale dei sigilli di Codex (`SeededRandom`, SplitMix64): quaranta righe di aritmetica definita fino all'ultimo bit, così due telefoni disegnano **la stessa** runa e la stessa gemma senza dipendere da una libreria che cambia fra versioni.
  - `fun nextLong(): Long`
  - `fun nextInt(bound: Int): Int` — In `[0, bound)`.
  - `fun nextFloat(): Float` — In `[0, 1)`, con 24 bit.
- `object PampaRune` — La runa della vicinanza: i glifi del blocco Runic di Unicode (`RuneAlphabet` di Codex, i 75 segni da U+16A0 a U+16EA, disegnati dal font incluso nell'app). Quattro rune sono circa 25 bit, come il confronto numerico del Bluetooth: chi si mette in mezzo nella stanza ha un tentativo solo, e una volta su 2²⁵ indovina.
  - `val POOL: List<Char>`
  - `const val LENGTH`
  - `fun of(seed: ByteArray, length: Int = LENGTH): String` — [length] rune dal seme [seed] (almeno 8 byte).
- `object NearbyHandshake` — Come due telefoni vicini si scambiano le schede **senza passare dal server** e si riconoscono (`NearbyHandshake` di Codex; `progetto.md` § 3.6, «Vicino»; critica S5): il NameDrop di Pampa.
  - `const val CHALLENGE_BYTES`
  - `class Refused(message: String) : Exception(message)`
  - `class Hello(val card: PersonCard, val challenge: ByteArray, val ephemeral: ByteArray)`
  - `fun advertisedName(): String` — Il nome con cui ci si annuncia in questa sessione: 8 byte casuali in Crockford minuscolo, mai la persona.
  - `fun newChallenge(): ByteArray`
  - `fun hello(card: PersonCard, challenge: ByteArray, ephemeral: ByteArray): ByteArray`
  - `fun readHello(bytes: ByteArray): Hello` — Legge un saluto e ne controlla la scheda; [Refused] se non è un saluto di Pampa o la scheda non torna.
  - `fun answer(peerChallenge: ByteArray, myCard: PersonCard, myEphemeral: ByteArray, peerEphemeral: ByteArray, work: WorkKeys): ByteArray` — La risposta alla sfida [peerChallenge]: firmo chi sono e le due effimere, con la mia chiave di lavoro.
  - `fun verify(myChallenge: ByteArray, peer: PersonCard, peerEphemeral: ByteArray, myEphemeral: ByteArray, signature: ByteArray): Boolean` — Controlla la risposta di [peer] alla mia sfida [myChallenge].
  - `fun rune(myEphemeral: X25519Key, peerEphemeral: ByteArray, myCard: PersonCard, peerCard: PersonCard): String` — La runa che i due telefoni mostrano, uguale da tutte e due le parti.
- `class PersonQr(val card: PersonCard, val invite: String, val secret: ByteArray)` — Il QR personale (critica S5): la **scheda intera**, quindi la chiave intera e niente da indovinare, più un gettone d'invito monouso (24 ore, registrato nella casella) e un **segreto** che non va mai al server. Chi inquadra manda una richiesta col gettone e, dentro la CDXB, la prova di aver visto il QR ([proof]): così è verificato **per tutti e due**, anche se il server conosce il gettone (che viaggia in chiaro accant…
  - `fun encode(): String`
  - `fun proof(requesterCardRaw: String): String` — La prova di chi ha inquadrato: `HMAC(HKDF(segreto, "pampa/v1/qr-prova"), scheda di chi chiede)`.
  - `fun checkProof(requesterCardRaw: String, proof: String): Boolean`
  - `companion object`
    - `const val PREFIX`
    - `const val SECRET_BYTES`
    - `const val QR_PROOF`
    - `fun create(card: PersonCard, invite: String): PersonQr`
    - `fun decode(text: String): PersonQr?` — Legge un QR; `null` se non è un QR personale di Pampa o la scheda non torna.
    - `fun looksLikePersonQr(text: String): Boolean`

## `engine-people/people/crypto/PairCode.kt`

- `object PairCode` — La chiave di coppia fra due persone, e da lei la gemma e le quattro parole della verifica a distanza (`d\server-e-chiavi.md` § 4; scelta D5; critica S5; contratto `people.formulas.pairKey`, `gem`, `words`).
  - `val EMPTY_SALT: ByteArray`
  - `const val NONCE_BYTES`
  - `fun pairKey(myEnc: X25519Key, theirEnc: ByteArray, myRoot: ByteArray, theirRoot: ByteArray): ByteArray`
  - `fun commit(nonce: ByteArray, committerRoot: ByteArray, committerEnc: ByteArray, peerEnc: ByteArray): ByteArray` — L'impegno di chi comincia: `SHA-256("pampa/v1/parole-impegno|" ‖ nonce ‖ sua radice ‖ sua enc ‖ enc dell'altro)`.
  - `fun opens(commitment: ByteArray, nonce: ByteArray, committerRoot: ByteArray, committerEnc: ByteArray, peerEnc: ByteArray): Boolean` — Il nonce rivelato torna con l'impegno ricevuto prima?
  - `fun salt(committerNonce: ByteArray, responderNonce: ByteArray): ByteArray` — Il sale delle parole e della gemma, dai due nonce: prima quello di chi ha cominciato.
  - `fun gem(pairKey: ByteArray, salt: ByteArray = EMPTY_SALT): ByteArray` — Il seme della gemma, 16 byte, uguale sui due telefoni.
  - `fun words(pairKey: ByteArray, salt: ByteArray = EMPTY_SALT): List<Int>` — I quattro indici da 11 bit (44 bit) nell'elenco di 2.048 parole ([ItalianWords]).
  - `fun wordsText(pairKey: ByteArray, salt: ByteArray = EMPTY_SALT): List<String>` — Le quattro parole da dirsi al telefono.

## `engine-people/people/crypto/PeopleBytes.kt`

- `object Crockford32` — Base32 di Crockford, l'alfabeto del codice Pampa e delle schede da ricopiare: il `Base32.kt` di Codex. Niente I, L, O, U: chi legge non si chiede mai «uno o elle?», e da byte casuali non nasce nessuna parola sgradevole. In lettura le lettere che si confondono tornano cifre.
  - `const val ALPHABET`
  - `fun encode(data: ByteArray): String` — Da byte a testo, senza riempimento: l'ultimo carattere è completato con zeri.
  - `fun decode(text: String): ByteArray?` — Da testo a byte, o `null` se non è base32: spazi, trattini e a capo non contano; I e L valgono 1, O vale 0, U vale V.
  - `fun grouped(text: String, group: Int = 5, perLine: Int = 8): String` — Lo stesso testo a gruppi, per chi lo deve ricopiare da uno schermo.

## `engine-people/people/crypto/PeopleEnvelopes.kt`

- `class CdxbRecipient(val mailbox: String, val enc: ByteArray, val workEpoch: Int)` — Chi riceve una CDXB: la sua casella, la sua chiave di cifratura e l'epoca, come stanno nella sua scheda.
  - `companion object`
    - `fun of(card: PersonCard)`
- `object Cdxb` — La busta per **una persona sola**: il formato `CDXB` di Codex (`InboxParcel`), rifatto con HPKE come dice il contratto (`people.formulas.cdxb`, F10.5). Porta richieste, accettazioni, chiavi delle conversazioni, dei gruppi e degli spazi.
  - `class Opened(val payload: JsonObject, val card: PersonCard, val senderEpoch: Int, val recipientEpoch: Int)`
  - `fun seal(work: WorkKeys, myMailbox: String, myCard: PersonCard, to: CdxbRecipient, payload: JsonObject): ByteArray` — Chiude [payload] per [to]. [work] sono le chiavi di lavoro di chi manda, [myMailbox] la sua casella e [myCard] la sua scheda (che viaggia dentro). Il risultato è già firmato: la coda in uscita lo può riprovare senza chiavi.
  - `fun isCdxb(data: ByteArray): Boolean` — È una CDXB? Lo dicono i primi quattro byte.
  - `fun open(myMailbox: String, mine: Map<Int, WorkKeys>, from: String, data: ByteArray, knownRoot: ByteArray? = null): Opened` — Apre una CDXB arrivata da [from] (quello che dice il server) nella casella [myMailbox], con le mie chiavi di lavoro di ogni epoca ([mine]). [knownRoot] è la radice che conosco già per quella casella (un contatto, un membro di un gruppo): deve restare quella. Ogni cosa che non torna è [PeopleDetected].
- `object Pmx1` — Il messaggio di una conversazione: il formato `CDX3` di Codex (`MessageEnvelope`), nella versione nuova `PMX1` del contratto (`people.formulas.pmx1`), con le due caselle e il filo nei dati associati.
  - `const val HEAD`
  - `fun isPmx1(data: ByteArray): Boolean`
  - `fun seal(chatKey: ByteArray, epoch: Int, from: String, to: String, thread: String, plaintext: ByteArray): ByteArray` — Chiude [plaintext] con [chatKey] all'[epoch], da [from] a [to] nel filo [thread].
  - `fun epochOf(data: ByteArray): Int` — L'epoca della conversazione con cui è chiusa, senza aprirla.
  - `fun messageIdOf(data: ByteArray): String` — L'id del messaggio, in base64url: serve a non prendere due volte lo stesso messaggio.
  - `fun open(chatKeys: Map<Int, ByteArray>, from: String, to: String, thread: String, data: ByteArray): ByteArray?` — Apre una PMX1 con la chiave dell'epoca giusta fra [chatKeys], o `null` se non è per questa conversazione.
- `object SpaceRecordCipher` — Un record di uno spazio condiviso (contratto `people.formulas.spaceRecord`): `nonce(12) ‖ AES-256-GCM`, con la chiave dello spazio all'epoca e i dati associati `"pampa/v1/spazio|<spazio>|<epoca>|<record>|<versione>"`.
  - `class Opened(val data: String, val writer: String, val writerEpoch: Int, val signature: ByteArray, val writerKeys: WorkPublic? = null)` — Un record aperto. [writerKeys] sono le chiavi di lavoro pubbliche di chi scrive, firmate dalla sua radice, se il record le porta (R2, il campo facoltativo `k`): servono a controllare la firma di chi ha rinnovato le chiavi dopo che il proprietario ha mandato lo spazio. Un record di un engine più vecchio non le ha.
  - `fun seal(key: ByteArray, spaceId: String, epoch: Int, recordId: String, version: Long, data: String, writerCode: String, writer: WorkKeys): ByteArray` — Chiude un record; dentro, accanto alla firma, ci sono anche le chiavi di lavoro pubbliche di chi scrive (`k`, R2).
  - `fun open(key: ByteArray, spaceId: String, epoch: Int, recordId: String, version: Long, ciphertext: ByteArray): Opened` — Apre un record; [PeopleDetected] se è stato spostato, rinominato, rinumerato o cambiato, o se dentro un campo è storto (R2: mai un'altra eccezione). Non controlla la firma.
  - `fun signedBy(opened: Opened, signPub: ByteArray, spaceId: String, epoch: Int, recordId: String, version: Long): Boolean` — La firma di [opened] è della chiave di lavoro [signPub]?
  - `fun aad(spaceId: String, epoch: Int, recordId: String, version: Long): ByteArray`

## `engine-people/people/crypto/PeopleKeys.kt`

- `class Ed25519Key private constructor(private val private: Ed25519PrivateKeyParameters)` — Una chiave Ed25519: la radice della persona o la chiave di lavoro che firma (`b\chiavi.md` § 12). È `IdentityKeys` di Codex, con Bouncy Castle e la sola API leggera. La privata non esce dal modulo: chi la custodisce la chiude subito nella sua classe ([dev.antigravity.fluidengine.people.PersonKeys]).
  - `val publicKey: ByteArray` — La pubblica, 32 byte.
  - `fun sign(message: ByteArray): ByteArray` — La firma di [message], 64 byte (RFC 8032, deterministica).
  - `companion object`
    - `const val KEY_BYTES`
    - `const val SIGNATURE_BYTES`
    - `fun fromPrivate(bytes: ByteArray): Ed25519Key` — La chiave dei 32 byte [bytes] (il seme RFC 8032).
    - `fun random(): Ed25519Key`
    - `fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean` — `false` invece di un'eccezione: una firma sbagliata è un caso normale, non un errore.
- `class X25519Key private constructor(private val private: X25519PrivateKeyParameters)` — Una chiave X25519: la chiave di lavoro che cifra (CDXB, HPKE) e da cui nasce la chiave di coppia.
  - `val publicKey: ByteArray`
  - `fun agree(peerPublic: ByteArray): ByteArray` — Il segreto in comune con [peerPublic] (RFC 7748). Non si usa mai così: passa sempre da HKDF, perché un accordo Diffie-Hellman non ha la distribuzione di una chiave.
  - `companion object`
    - `fun fromPrivate(bytes: ByteArray): X25519Key`
    - `fun random(): X25519Key`
- `class PeopleDetected(val what: String) : Exception(what)` — Il telefono ha scoperto qualcosa che non torna: una scheda col codice di un altro, una busta spostata, un record vecchio rimandato, una chiave non firmata dalla radice. È il `Detected` del banco del server: il telefono non usa la cosa e lo dice ([what] è la frase, senza segreti).

## `engine-people/people/crypto/PersonCard.kt`

- `class WorkPublic(val epoch: Int, val enc: ByteArray, val sign: ByteArray, val sig: ByteArray)` — Le chiavi di lavoro pubbliche di una persona a un'epoca (`b\chiavi.md` § 12): X25519 per cifrare ([enc]) ed Ed25519 per firmare ([sign]), con la firma della radice ([sig]) su `"pampa/v1/chiavi-di-lavoro|<epoch>|" ‖ enc ‖ sign`.
  - `fun signedBy(root: ByteArray): Boolean` — La radice [root] ha firmato queste chiavi?
  - `companion object`
    - `fun signatureInput(epoch: Int, enc: ByteArray, sign: ByteArray): ByteArray`
- `class WorkKeys(val epoch: Int, val enc: X25519Key, val sign: Ed25519Key, val sig: ByteArray)` — Le chiavi di lavoro con le private: stanno nella classe «consegna» della custodia (Q7 A), così le buste si aprono e si firmano anche col telefono in tasca. Cambiano solo con un «perso o rubato» o la rotazione dei sei mesi.
  - `val public: WorkPublic get()`
  - `companion object`
    - `fun create(root: Ed25519Key, epoch: Int): WorkKeys` — Chiavi nuove per l'epoca [epoch], firmate da [root]. Serve la radice: si fa a telefono sbloccato.
- `class PersonCard private constructor(val code: String, val name: String, val mineral: ByteArray, val root: ByteArray, val mailbox: String, val work: WorkPublic, val at: Long, val raw: String, val sig: String)` — La scheda pubblica di una persona (`d\server-e-chiavi.md` § 4; contratto `people.formulas.cardSig`), firmata dalla radice: codice, nome, seme del minerale, radice, casella, chiavi di lavoro, ora. Niente email, telefono, età o app.
  - `val rootB64: String get()` — La radice in base64url, come sta nella scheda.
  - `fun toJson(): JsonObject` — `{ "card": raw, "sig": sig }`, come la vuole `PUT /v1/people/card` e come viaggia nelle buste.
  - `companion object`
    - `const val MAX_NAME`
    - `const val MAX_BYTES`
    - `fun sign(root: Ed25519Key, name: String, mineral: ByteArray, work: WorkPublic, at: Long): PersonCard` — Scrive e firma la scheda di chi ha [root].
    - `fun verify(raw: String, sig: String, expectCode: String? = null, expectMailbox: String? = null, expectRoot: ByteArray? = null): PersonCard` — Legge e controlla una scheda: forma, codice e casella ricavati dalla radice, firma della scheda e delle chiavi di lavoro. Con [expectCode], [expectMailbox] o [expectRoot] controlla anche che sia **quella** persona: un server che rende la scheda di un altro, o che cambia la radice di un contatto, viene scoperto. Ogni problema è [PeopleDetected].
    - `fun fromJson(o: JsonObject, expectMailbox: String? = null, expectRoot: ByteArray? = null): PersonCard` — La scheda di un JSON `{ card, sig }`.
    - `fun validName(name: String): Boolean` — Le regole del nome del server: da 1 a 40 caratteri, niente caratteri di controllo.

## `engine-people/people/crypto/PersonCode.kt`

- `object PeopleLabels` — Le etichette delle persone, dal contratto del server (`Pampa-account\contract\v1\e2e.json` → `people.labels`, F10.5). Una prova le confronta con la copia del contratto, così un cambio da una parte sola non passa. `<…>` si sostituisce col valore ([fill]).
  - `const val CODE`
  - `const val MAILBOX`
  - `const val CARD`
  - `const val WORK_KEYS`
  - `const val DELIVERY_PRINT`
  - `const val INVITE_PRINT`
  - `const val WRITE_PRINT`
  - `const val BLOCKED_PRINT`
  - `const val CDXB_INFO`
  - `const val CDXB_SIG`
  - `const val PMX1_KEY`
  - `const val PMX1_AAD`
  - `const val PAIR`
  - `const val GEM`
  - `const val WORDS`
  - `const val SPACE_AAD`
  - `const val SPACE_SIG`
  - `const val WORDS_COMMIT` — F10.6 · L'impegno di chi comincia lo scambio delle parole: `SHA-256(etichetta ‖ nonce ‖ radice ‖ enc ‖ enc dell'altro)`.
  - `const val WORDS_SALT` — F10.6 · Il sale delle parole e della gemma: `SHA-256(etichetta ‖ nonce di chi comincia ‖ nonce di chi risponde)`.
  - `const val NEARBY_NAME` — F10.6 · Il saluto della vicinanza e la runa (`NearbyHandshake` di Codex, con l'etichetta di Pampa).
  - `const val NEARBY_AUTH`
  - `const val NEARBY_RUNE`
  - `fun fill(template: String, vararg values: Pair<String, Any>): String` — Sostituisce i segnaposto di [template]; un segnaposto senza valore è un errore.
- `object PersonCode` — Il codice Pampa e la casella, che nascono dalla radice Ed25519 (contratto `people.formulas.code` e `mailbox`): il telefono li sa prima di firmare la scheda, e il server non li sceglie. È il `CodexId` dell'alpha col prefisso `PMP-` (scelta D10, Q10: niente `CDX-`).
  - `const val PREFIX`
  - `fun of(rootPub: ByteArray): String` — `PMP-XXXX-XXXX` della radice [rootPub]: i primi 40 bit di `SHA-256("pampa/v1/codice|" ‖ radice)`.
  - `fun mailboxOf(rootPub: ByteArray): String` — L'id della casella: i primi 16 byte di `SHA-256("pampa/v1/casella|" ‖ radice)`, in base64url (22 caratteri).
  - `fun normalize(input: String): String?` — Un codice scritto a mano, come lo legge il server (`normalizeCode`): maiuscole, O→0, I e L→1. Accetta anche gli spazi al posto dei trattini e il codice senza `PMP-`; `null` se non è un codice.
  - `fun isValid(input: String): Boolean`
- `object PeoplePrints` — Le impronte dei gettoni, come le tiene la casella (contratto `people.formulas.print`).
  - `fun delivery(token: ByteArray): String`
  - `fun invite(token: ByteArray): String`
  - `fun write(token: ByteArray): String`
  - `fun blocked(mailbox: String): String` — L'impronta di una casella bloccata: `SHA-256(utf8("pampa/v1/bloccato|" + casella))`. Il server la sa ricalcolare.
  - `fun newToken(): String` — Un gettone nuovo: 32 byte casuali in base64url (43 caratteri).

## `engine-people/people/nearby/NearbyPairing.kt`

- `interface NearbyLink` — Il canale diretto fra due telefoni vicini, già aperto: Nearby Connections nello Store (F10.7, con Play services), o il filo finto delle prove ([FakeNearbyLink]). Porta byte e basta: chi è l'altro lo dice il saluto firmato.
  - `suspend fun send(bytes: ByteArray)`
  - `suspend fun receive(): ByteArray`
- `class FakeNearbyLink private constructor(private val out: Channel<ByteArray>, private val inbox: Channel<ByteArray>, private val tap: ((ByteArray) -> ByteArray)?) : NearbyLink` — Il filo finto delle prove: due capi collegati da due code. [tap] vede (e può cambiare) quello che passa.
  - `companion object`
    - `fun pair(tap: ((ByteArray) -> ByteArray)? = null): Pair<FakeNearbyLink, FakeNearbyLink>`
- `class NearbyRefused(message: String) : Exception(message)` — La vicinanza è stata rifiutata: un saluto non valido, una firma che non torna, o la runa non confermata.
- `class NearbyPairing(private val people: PeopleClient)` — Aggiungersi **di persona** avvicinando i telefoni (`progetto.md` § 3.6, «Vicino»; `NearbyHandshake` di Codex): le schede passano dirette, senza il server, e i due vedono la stessa runa. Dopo la conferma tutti e due sono **verificati subito**, perché la chiave intera è arrivata senza intermediari (critica S5).
  - `suspend fun handshake(link: NearbyLink): NearbySession` — Saluto, sfida e risposta. Torna la sessione con la runa da mostrare; [NearbyRefused] se l'altro non è chi dice.
- `class NearbySession internal constructor(private val people: PeopleClient, private val link: NearbyLink, private val eph: X25519Key, val peer: PersonCard, private val peerEphemeral: ByteArray, val rune: String)` — La vicinanza dopo il saluto: [peer] è la scheda dell'altro, [rune] la runa da confrontare. [confirm] quando l'utente ha visto la stessa runa sui due schermi; [cancel] se non tornava.
  - `suspend fun confirm(): Contact` — Confermo: lo dico all'altro e aspetto il suo sì; poi ci scambiamo, cifrati sul canale, i gettoni di consegna. La conversazione nasce dall'accordo delle effimere, uguale dai due lati e mai trasmessa.
  - `suspend fun cancel()` — La runa non era la stessa: si chiude senza aggiungere niente.

## `engine-people/people/spaces/SharedSpace.kt`

- `class SpaceMembership(val id: String, val kind: String, val epoch: Int, val keys: Map<Int, ByteArray>, val writeToken: String?, val owner: String, val writers: Map<String, PersonCard>, val seen: Map<String, Long>)` — Chi sono io in uno spazio condiviso (`d\server-e-chiavi.md` § 11): la chiave di ogni epoca, il gettone di scrittura (assente se posso solo vedere), il proprietario, le schede di chi può scrivere (per controllare le firme dei record) e la versione più alta vista di ogni record (un record vecchio rimandato dal server viene scoperto).
  - `val canWrite: Boolean get()`
  - `fun toJson(): JsonObject`
  - `companion object`
    - `fun fromJson(o: JsonObject): SpaceMembership`
    - `fun mergeIncoming(prev: JsonObject?, payload: JsonObject, owner: String): JsonObject` — Le chiavi arrivate dal proprietario in una CDXB (`t = "spazio"`), unite a quello che avevo: le epoche si aggiungono e non si tolgono (chi è rimasto legge il passato), la versione vista resta la più alta.
- `interface SharedSpaceCodec<T>` — Come un oggetto vivo diventa i campi di `engine-sync` (F8.21): per un campo vince l'ultimo, ogni voce di una lista ha il suo id, e una voce tolta vince su una modifica arrivata insieme («spunta e tolta insieme: vince la tolta», `progetto.md` § 3.10). L'app dice solo come scrivere e leggere il suo oggetto.
  - `fun write(value: T, editor: SyncEditor)`
  - `fun read(document: SyncDocument): T?`
- `class SharedSpaces(private val people: PeopleClient)` — Gli spazi condivisi della persona (`d\server-e-chiavi.md` § 11; `progetto.md` § 3.10): una lista, un invito, un viaggio da seguire. Ogni spazio ha una chiave casuale a epoche, sigillata per ogni membro con una CDXB, e un gettone di scrittura dato solo a chi modifica; il server ne tiene l'impronta, non l'elenco dei membri.
  - `fun membership(id: String): SpaceMembership?`
  - `suspend fun create(kind: String): SpaceMembership` — Uno spazio nuovo di cui sono il proprietario: id e chiave nati qui, l'impronta del gettone al server.
  - `suspend fun share(id: String, codes: List<String>, readOnly: Set<String> = emptySet())` — Do lo spazio a dei contatti: la chiave di ogni epoca e, a chi modifica, il gettone di scrittura; a chi in [readOnly] niente gettone. Viaggia in una CDXB per ognuno, a priorità normale.
  - `suspend fun removeMembers(id: String, codes: Collection<String>, keep: List<String>)` — Tolgo qualcuno (P6): epoca nuova, chiave nuova, gettone nuovo, consegnati solo a chi resta. Chi esce tiene ciò che ha già, e non legge né scrive più niente di nuovo.
  - `suspend fun delete(id: String)` — Cancella lo spazio: solo il proprietario.
  - `fun <T> open(id: String, sync: FluidSync, codec: SharedSpaceCodec<T>): SharedSpace<T>` — Lo spazio [id] come oggetto vivo di tipo [T], dentro il motore [sync] costruito con [relay] e [sealer].
  - `val relay: SyncRelay` — L'adattatore di `engine-sync` verso `/v1/spazi/…`.
  - `val sealer: SyncSealer` — Il sigillo dei record: chiave dell'epoca, firma di chi scrive dentro, versione mai più vecchia di quella vista.
- `class SharedSpace<T> internal constructor(val id: String, private val spaces: SharedSpaces, val sync: SyncSpace, private val codec: SharedSpaceCodec<T>)` — Uno spazio condiviso come oggetto vivo ([T] per ogni record), sopra uno `SyncSpace` di `engine-sync`: [items] cambia da sé, [put], [add] e [remove] scrivono subito sul telefono e partono con la sincronizzazione.
  - `val items: Flow<Map<String, T>>` — Gli elementi vivi, per id.
  - `val canWrite: Boolean get()` — Posso modificare? Senza gettone di scrittura lo spazio si vede soltanto.
  - `suspend fun put(recordId: String, value: T): Boolean` — Scrive [value] in [recordId]. Senza gettone di scrittura ([canWrite] falso) è [IllegalStateException]: una modifica locale che il relay rifiuterebbe per sempre resterebbe in coda e fermerebbe anche la lettura dello spazio.
  - `suspend fun add(value: T): String`
  - `suspend fun remove(recordId: String): Boolean`
  - `suspend fun sync()`
  - `suspend fun snapshot(): Map<String, T>`
- `data class SharedListItem(val text: String, val done: Boolean = false, val assignee: String? = null)` — Una voce di una lista condivisa (spesa, materiale per la gita): il testo, la spunta, a chi è assegnata («@mamma»).
  - `object Codec : SharedSpaceCodec<SharedListItem>`

## `engine-people/people/store/PeopleStore.kt`

- `data class StoreNotice(val title: String, val text: String, val address: String? = null, val app: String? = null)` — Una notifica semplice dello Store, quando l'app giusta non c'è o non è un'app (`progetto.md` § 3.7 e § 3.9, T21).
- `interface AppNotifier` — Come lo Store avvisa le app (`b\api.md` § 5.4): un broadcast **esplicito** a quel pacchetto, con dentro solo `what`; l'app non si fida del contenuto e rilegge dal provider firmato. Se l'app non c'è, lo Store mostra lui la notifica.
  - `fun notifyApp(packageName: String, what: String): Boolean` — Avvisa [packageName]; `false` se l'app non è installata (allora c'è [show]).
  - `fun show(notice: StoreNotice)`
- `class AndroidAppNotifier(private val context: Context) : AppNotifier` — L'avviso vero: `sendBroadcast` con `setPackage`, e il canale «Persone» dello Store per le notifiche sue.
  - `companion object`
    - `const val CHANNEL`
- `class PeopleDispatcher(private val people: PeopleClient, private val notifier: AppNotifier, private val clock: () -> Long = System::currentTimeMillis)` — Quello che il ritiro ha portato, passato alle app (`d\server-e-chiavi.md` § 6, punto 3): una cosa condivisa va nella posta in arrivo dell'app giusta, e l'app riceve l'avviso esplicito `CHANGED` con `share`; una risposta torna all'app che aveva mandato; «I tuoi dispositivi» e le richieste diventano notifiche dello Store. Rispetta blocchi (già fatti dal ritiro) e gli interruttori «Non mostrare più da X in questa app».
  - `suspend fun dispatch(events: List<PeopleEvent>): Set<String>` — Smista le consegne che il ritiro ha lasciato da smistare ([PeopleState.undispatched]), comprese quelle di un giro caduto a metà (un errore alla pagina dopo, la scadenza del campanello, un riavvio dello Store): la posta delle app e la coda si scrivono nello stesso salvataggio (R1).
- `class PeopleBell(private val people: PeopleClient, private val dispatcher: PeopleDispatcher)` — Il campanello (`d\server-e-chiavi.md` § 6): una notifica FCM **vuota**, data-only con il solo `k`, sveglia lo Store. Con `k = posta` lo Store ritira, apre e passa le cose alle app. Niente nome, niente tipo, niente anteprima: tutto quello che si vede lo scrive il telefono dopo aver aperto le buste.
  - `suspend fun ring(data: Map<String, String>): Set<String>` — [data] è il contenuto del messaggio FCM (o del campanello finto). Torna le app avvisate.
- `class PeopleProviderMethods(private val people: PeopleClient, private val hasOtherDevices: () -> Boolean = { true }, private val deviceName: () -> String = { "Telefono" }) : AccountPeopleMethods` — I metodi delle persone del provider dello Store (`b\api.md` § 5.3), per `AccountProtocolHandler`: la fila, mandare, ricevere, rispondere, nascondere e «I tuoi dispositivi». Ogni app vede **id suoi** (un HMAC col sale dello Store e il suo pacchetto): un id di Notes non vale in Transit, e nessuna app vede codici, caselle o chiavi.
  - `companion object`
    - `const val ROW_MAX` — La fila: fino a otto persone e gruppi recenti, più «Altri…» (`progetto.md` § 3.7).
    - `const val REPLY_MAX`

## `engine-people/people/testing/FakePeopleServer.kt`

- `class FakePeopleServer(var now: Long = 1_764_000_000_000)` — Il server delle persone finto del banco (`d\server-e-chiavi.md` § 13): le stesse regole del Worker vero di `Pampa-account` (F10.5, `src\routes\people.ts`, `casella.ts`, `spazi.ts`, `src\persone\casella.ts`), in memoria e dietro la stessa interfaccia del trasporto ([PeopleHttp]), così il client che lo usa è quello vero. Le prove HTTP lo mettono anche dietro MockWebServer.
  - `class Envelope(val n: Long, val kind: String, val from: String, val data: String, val size: Int, val via: String?, val minStore: String, val priority: String, val at: Long)`
  - `class Invite(val kind: String, var usesLeft: Int, val expiresAt: Long)`
  - `class Card(val account: Account, var raw: String, var sig: String, val code: String, val mailbox: String, var at: Long, var findable: Boolean)`
  - `class Space(val id: String, val kind: String, var epoch: Int, var writePrint: String, val owner: Account)`
    - `var seq`
    - `val records`
  - `class SpaceRecord(val recordId: String, var version: Long, var epoch: Int, var ciphertext: String, var deleted: Boolean, var seq: Long)`
  - `data class Bell(val device: String, val priority: String, val at: Long)` — Una sveglia del campanello finto: data-only e vuota, solo la priorità (`k = posta`).
  - `class Hostile` — Le manopole del server ostile: ognuna, se c'è, cambia quello che il server rende.
    - `var swapLookup: ((code: String) -> Pair<String, String>?)?` — Rende un'altra scheda (raw, sig) al posto di quella cercata.
    - `var tamperPickup: ((Envelope) -> Envelope)?` — Cambia una busta al ritiro: la sposta, ne cambia il mittente o i byte.
    - `var tamperSpace: ((spaceId: String, rows: List<SpaceRecord>) -> List<SpaceRecord>)?` — Cambia le righe di uno spazio alla lettura (rimanda un record vecchio, lo rinomina, lo sposta).
  - `val hostile`
  - `val accounts`
  - `val cards`
  - `val mailboxes`
  - `val spaces`
  - `val bells`
  - `val requestLog` — Tutte le richieste arrivate, per controllare che nessuna porti un segreto (critica S1, voce 9 del banco).
  - `fun newAccount(devices: Int = 1): Account` — Senza rete: ogni chiamata è [IOException], come un telefono in galleria.
  - `fun revoke(device: Device)` — «Fai uscire» un dispositivo: il suo token non vale più, e non trattiene più le buste.
  - `fun ringsFor(device: Device): List<Bell>`
  - `val http: PeopleHttp`

## `engine-people/people/testing/MemoryPersonVault.kt`

- `class MemoryPersonVault : PersonVault` — La cassaforte della persona in memoria, per le prove e per i dispositivi finti del banco: due chiavi AES casuali, una per classe, e due interruttori per fingere il telefono bloccato ([motherLocked]: la radice non si apre) o non ancora sbloccato dall'accensione ([allLocked]). Non va mai in un'app vera: lì c'è la custodia del Keystore.
