<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/notifiche.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Notifiche in-app

`FluidNotificationHost` è il messaggio transitorio dell'engine: una card di vetro che scende dalla cima dello schermo
(nello spirito dei banner di iOS), resta il tempo di leggerla e se ne va. Sta **fuori dalla navigazione**, alla radice,
accanto all'host dei modali ([menu-modali.md](menu-modali.md)): chiunque nell'app può pubblicare, nessuno deve
possedere la sua interfaccia. È per quello che informa un momento dopo che l'utente ha fatto qualcosa (un
salvataggio finito, una sincronizzazione, un errore passeggero) — non per quello che deve restare nella pagina
(`FluidInlineMessage`) né per quello che chiede una decisione (`FluidAlert`).

Modello mentale: **una coda FIFO con una card sola in scena, e la card è un oggetto che si tocca.** Un dito sopra
ferma il tempo di lettura; tirata in giù si apre (testo intero e azioni, e da aperta non scade più); spinta in su, o
lanciata, se ne va. Fino alla 2.11 il dito non cambiava niente: il conto alla rovescia correva sotto di lui e la card
spariva mentre la si leggeva.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| dire «fatto» / «errore» a chi sta guardando l'app, per qualche secondo | `FluidNotification` pubblicata con `FluidNotificationHostState.show` |
| che la notifica porti uno o due verbi («Annulla», «Apri») | `actions = listOf(FluidNotificationAction(label) { … })`: compaiono a card **aperta** |
| che la notifica non sparisca da sola | non c'è un «per sempre»: `durationMillis` lungo, o un messaggio nella pagina (`FluidInlineMessage`) |
| una domanda, un sì/no | **non così**: `FluidAlert` / `FluidConfirmSheet` |
| un messaggio che deve restare finché non si corregge la causa | **non così**: `FluidInlineMessage` |
| sapere che la notifica è stata *vista* prima di marcarla come consegnata | il valore di ritorno di `show`: `FluidNotificationDelivery.Presented` |
| pubblicare dallo stesso evento da più schermate senza duplicati | lo stesso `id`: la coda deduplica finché la notifica è in coda o a vista |

## Montare l'host

Dal `GalleryActivity` del sample, **alla radice, sopra il resto**:

```kotlin
val notificationHost = rememberFluidNotificationHostState()

CompositionLocalProvider(LocalFluidNotificationHostState provides notificationHost) {
  Box(Modifier.fillMaxSize()) {
    /* la pagina, la tab bar, gli altri host */
    FluidNotificationHost(
      state = notificationHost,
      backdrop = backdrop,                      // il fondale della chrome: senza, la card non è vetro
      modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}
```

Pubblicare, da una coroutine (`show` sospende):

```kotlin
val host = LocalFluidNotificationHostState.current
val scope = rememberCoroutineScope()

scope.launch {
  host?.show(
    FluidNotification(
      id = "sync-$n",
      title = "Sincronizzazione completata",
      message = "Sono arrivati quattro elementi nuovi. Chiusa, la notifica ne mostra due righe; tirata giù si apre.",
      tone = FluidNotificationTone.Success,
      actions = listOf(FluidNotificationAction("Apri") { apri() }),
    ),
  )
}
```

## `FluidNotification`, `FluidNotificationTone`, `FluidNotificationAction`

**Cos'è.** Il dato: un `id`, un titolo, un messaggio, un tono, quanto dura e, se serve, uno o due verbi.

```kotlin
data class FluidNotification(
  val id: String,
  val title: String,
  val message: String,
  val tone: FluidNotificationTone = FluidNotificationTone.Info,   // Info, Success, Warning, Error
  val durationMillis: Long = 6_000L,
  val actions: List<FluidNotificationAction> = emptyList(),
)

data class FluidNotificationAction(val label: String, val onClick: () -> Unit)
```

**Comportamento.**

- **I toni** colorano la piastrella dell'icona (38 dp, icona da 21): `Info` il primario (testo `onPrimary`), `Error` il colore d'errore
  (`onError`), `Success` un verde (`0xFF28A745`) e `Warning` un arancio (`0xFFFF9500`) il cui testo è scelto per **contrasto massimo**
  fra due inchiostri fissi (un nero e un bianco quasi puri), non per tema. L'icona è di serie per tono (info, spunta, triangolo, errore).
  Il tono non tinge la card di vetro; sul ripiego opaco la card prende una velatura dell'accento (9% sul tema scuro, 4,5% sul chiaro).
- **Il messaggio è di due righe a card chiusa**, tutto a card aperta (prima della 2.12 non c'era un limite): la prima riga serve a
  capire, il resto si legge tirando. Il titolo è uno solo, su `titleSmall` semibold.
- **`actions`**: «una o due, con etichetta corta»: stanno in una riga sotto il testo (allineate al testo, non all'icona: sono cose che si
  fanno *su* quel testo) e **una terza non avrebbe dove andare** — l'engine non la rifiuta, la card cresce. Compaiono **solo a card aperta**
  (tirata giù o con l'azione «Espandi» di TalkBack): chi non scopre il gesto non le vede mai, quindi la notifica non deve avere come
  unica via un'azione. Toccare un'azione **esegue `onClick` e poi chiude** la notifica.
- **`durationMillis`** è il tempo di lettura, non un timer d'orologio (vedi sotto): minimo 1,5 s (`NotificationResumeFloorMillis`), e
  se l'utente ha impostato il «tempo per agire» di accessibilità il sistema lo allunga
  (`AccessibilityManager.calculateRecommendedTimeoutMillis`, con icone, testo e controlli).

**Trappole.**

- *`id` o `message` vuoti → la notifica è rifiutata* (`FluidNotificationDelivery.Rejected`) senza entrare in coda; il titolo vuoto invece è
  ammesso.
- *Lo stesso `id` per due messaggi diversi*: finché il primo è in coda o a vista, il secondo **non crea una card**, attende la consegna del primo.
  Per eventi diversi un `id` diverso («sync-$n»); per lo stesso evento osservato da più schermate, lo stesso.

## `FluidNotificationHostState`

**Cos'è.** Una piccola FIFO per messaggi transitori dell'app intera. Una card alla volta; la seguente parte quando l'uscita della
precedente è davvero finita (`AnimatedVisibility` assestata), non a tempo: una costante non segue una molla né la scala delle animazioni del sistema.

```kotlin
class FluidNotificationHostState {
  suspend fun show(notification: FluidNotification): FluidNotificationDelivery   // Presented | Rejected
  fun dismissCurrent()
}
@Composable fun rememberFluidNotificationHostState(): FluidNotificationHostState
val LocalFluidNotificationHostState: ProvidableCompositionLocal<FluidNotificationHostState?>
```

**Comportamento di `show`.**

- **Sospende fino a quando l'`id` è stato davvero presentato da un host attivo** — con un layout reale, non solo accodato — e
  ritorna `Presented`. Non aspetta la chiusura. È fatto per chi ha un produttore *durevole* (un evento che va marcato come consegnato solo se
  è stato visto): «consegnato» vuol dire «a schermo».
- **Chiamate concorrenti per lo stesso `id` aspettano la stessa consegna** invece di duplicare il banner.
- **Un'interruzione dell'host** (la composizione se ne va, l'activity si ricrea) **ri-accoda in cima** la richiesta non ancora presentata: non
  scavalca quelle già in coda, e `show` continua ad aspettare.
- **Annullare la coroutine di `show` non ritira la notifica** già accodata: la protezione `try`/`catch` copre solo l'invio nel canale, non l'attesa.
- **Un solo consumatore alla volta**: se monti due `FluidNotificationHost` sullo stesso stato, il secondo aspetta che il primo se ne sia
  andato del tutto (un mutex), altrimenti due lettori scambierebbero l'ordine.
- `dismissCurrent()` chiude la card in scena (la stessa cosa che fanno il tasto di chiusura, un'azione e il gesto). Non fa niente se non c'è.

**Quando conta il tempo.** La notifica è «attiva» solo mentre **l'host è attivo**: ciclo di vita almeno `RESUMED` *e* finestra con il fuoco. Il
conto del tempo di lettura parte dopo la presentazione e scorre solo in quello stato (e **solo a card libera e chiusa**): app in secondo piano,
finestra senza fuoco o un dito sopra lo sospendono. Con un dito sopra il conto si ferma; al rilascio riparte da quello che restava ma **mai da meno di
1,5 s** (chi l'ha tenuta quattro secondi di sei non deve vederla sparire due secondi dopo averla mollata, e chi l'ha toccata all'ultimo istante
non deve vederla sparire sotto il dito: «dopo un tocco c'è sempre il tempo di accorgersi che è ancora lì»). Anche un tocco più breve di un giro del timer
conta (si contano i rilasci, non un booleano). Una card aperta **non scade più**: chi l'ha aperta ha deciso di leggerla, e un conto alla rovescia
su un testo lungo la porterebbe via a metà riga; resta finché qualcuno la chiude, tocca un'azione o la lancia via. Una nuova notifica parte sempre
da ferma e chiusa (il dito e l'apertura erano della card di prima). Un'uscita scaduta vale anche se la finestra ha perso il fuoco proprio ora.

**Trappole.**

- *Nessun host, nessun avviso*: con `LocalFluidNotificationHostState` nullo la chiamata `host?.show(…)` non fa niente. Con uno stato che **nessun host
  disegna**, `show` **non ritorna mai** (resta in attesa di un host attivo): lanciala da una coroutine che puoi abbandonare.
- *App in secondo piano*: la notifica resta in coda e viene presentata quando l'host è di nuovo attivo; il conto riparte da lì.
- *`show` va chiamata da una coroutine* (`scope.launch`), non in composizione.

## `FluidNotificationHost`

**Cos'è.** L'interfaccia: una colonna in cima (`fillMaxWidth`, `statusBarsPadding`, margini 12/8 dp, allineata in alto al centro) con la card
in scena. Va montato alla radice **sopra** le altre cose: è sopra le tab, e sta sopra anche i modali se è disegnato dopo.

```kotlin
@Composable
fun FluidNotificationHost(
  state: FluidNotificationHostState,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
)
```

**Comportamento.**

- **Il vetro.** Con `backdrop` la card è `glassSurface` (ruolo `Modal`, `GlassDefaults.panelTint()`, `panelOptics(overSharpPage = true)`: la
  sfocatura più alta, perché nessuno scrim ha sfocato prima la pagina e il testo che passa dietro nitido la card lo deve nascondere da sola),
  angoli da 20 dp, larga al più 560 dp. **Senza `backdrop` non è vetro**: una velatura opaca con un bordo da 0,5 dp dell'accento e l'ombra di
  piattaforma da 12 dp. (Con il vetro l'ombra la disegna il vetro: quella di piattaforma, su una superficie trasparente, trapela dentro la sagoma
  e si vedeva una fascia scura attorno a un rettangolo chiaro.)
- **Arrivo e uscita.** Scende con slide (un terzo della sua altezza), dissolvenza di 150 ms e scala 0,97 dall'alto; se ne va con slide di un quarto,
  dissolvenza di 180 ms e scala 0,985. Una card **lanciata via** non ripete l'uscita (`ExitTransition.None`): si è già dissolta, e un'uscita di
  ordinanza sarebbe una scena vuota che tiene in coda la notifica dopo.
- **Il vetro che si muove.** Spostamento e opacità stanno su un `graphicsLayer` *fuori* dal vetro: il vetro li vede come una pagina intera che scorre. La
  card si muove, il fondale che campiona no. **Mai scala** (scalare un vetro scala il fondale campionato, «la pagina dietro sembra muoversi»);
  l'allungamento del dito è un cambio di *layout*, non di layer. Il contenuto non si disegna sotto un'opacità di 0,004: il primo e l'ultimo
  fotogramma di vita di un nodo possono uscire con le proprietà di default, cioè opachi e al loro posto.
- **Il tasto di chiusura** (un `FluidGlassIconButton` che rifrange la card, non la pagina: la card pubblica il suo materiale) c'è sempre. Le azioni sono
  `FluidGlassButton`.
- **Il testo** ha colore `onSurface` anche fuori da una `Surface` (3.0.1).

### Il gesto

Tutte le decisioni sono funzioni pure provate a numeri, in dp e dp/s (il dito si muove in dp, e un numero sbagliato si vede subito in mano e mai in una revisione):

| numero | valore | cosa fa |
|---|---|---|
| tirare in giù | rilascio a ≥ **72 dp** del dito | apre la card (testo intero, azioni, niente scadenza); già aperta, torna |
| spingere in su | rilascio a ≤ **−40 dp** | lancia via: meno dell'apertura, «si butta con meno» |
| spinta | ≥ **1000 dp/s** | batte la distanza: giù apre, su lancia via |
| il ripensamento | passata la soglia da una parte e rilasciata con una spinta dall'altra | **torna al suo posto**: lanciare via una notifica perché si è tirata giù troppo e si è tornati su di scatto sarebbe la peggiore delle sorprese |
| curva elastica in giù | asintoto fisso **160 dp**: a 72 dp di dito la card ha fatto circa 32 dp | la stessa curva di `FluidScrollPhysics`; fisso e non ricavato dalla finestra perché a essere tirata è una card, non una pagina |
| in su | uno a uno | sta andando dove vuole andare |
| allungamento | 0,3 per px di spostamento, al più **14 dp**, di layout | un accenno di «c'è altro»; zero con il movimento ridotto |
| tick sulla soglia | isteresi 6 dp | un dito fermo sulla soglia trema di un paio di dp: senza, ogni tremito è un tick |
| lancio | velocità del dito, almeno **900 dp/s**, con dissolvenza di 200 ms | deve avere dove andare mentre sfuma; a fine dissolvenza la notifica si chiude |
| ritorno | molla `FluidMotion.pixels(DampingStandard, ResponseSnappy)`, quasi critica (0,9) | un contenitore che rimbalza dice «il layout è sciolto»: l'oltrepasso misurato resta sotto il dp |
| movimento ridotto | niente allungamento, lancio = dissolvenza di 100 ms | |

Altri dettagli che servono a chi tocca il codice: la molla di ritorno parte con la velocità **della card** e non quella del dito (in giù
differiscono per la resistenza, e una molla più veloce dell'oggetto che muove fa uno scatto), tenuta sotto `omega · distanza` verso il riposo
(altrimenti oltrepassa); una card ripresa mentre sta tornando riparte da dove è, non da un riposo che non ha ancora raggiunto; la card lanciata
non risponde più al dito e non torna. **Aptica**: un `Tick` a ogni attraversamento della soglia di apertura (con isteresi, in un senso e nell'altro) e un `Threshold` al
lancio. Il trascinamento sta *fuori* dal layer che muove la card, come nel foglio dei modali: il riferimento del dito non si muove con
l'oggetto che il dito sposta.

**Il tasto della card tiene il dito.** Un trascinamento che parte da un tasto (chiudi, un'azione) **non è anche un tocco** e non trascina la card;
un trascinamento che parte dal tasto e si solleva ancora sul tasto è il suo click. Un dito che tocca la card ferma il conto anche se il gesto lo
prende un tasto: l'ascolto è nel passaggio Initial e non consuma.

### Accessibilità

La card è un `paneTitle` («Notifica in-app», da `LocalFluidLabels.current.notification`), una `liveRegion` `Polite` — TalkBack la annuncia senza interrompere —
e ha due azioni custom, **«Espandi»** (solo se non è già aperta) e **«Chiudi»**: le stesse cose del gesto, senza trascinare («il trascinamento non è
per tutti»). Il tasto di chiusura si chiama «Chiudi notifica». Le parole sono tutte da `LocalFluidLabels` e un'app tradotta le fornisce alla radice
(le voci `notification`, `notificationExpand`, `notificationDismiss`, `notificationClose`). Il tempo di lettura rispetta il «tempo per agire» di sistema (vedi sopra).

**Trappole.**

- *La card non è vetro*: `backdrop` non passato. Il default è `null`.
- *La card non compare mai*: l'host non è composto, o l'app è in secondo piano / senza fuoco (non è «attivo»), o l'`id` è già in coda.
- *Un'azione non si vede*: la card è chiusa. Si apre tirando in giù o con «Espandi» di TalkBack.
- *Il dito era sulla card di prima*: il conto di una notifica ascolta solo i tocchi della propria card (`setTouched` confronta l'`id`), quindi un dito rimasto sulla precedente non ferma il tempo della nuova.
- *Il testo è tagliato*: il messaggio chiuso è di due righe, per costruzione.

**Non usarlo quando.** C'è bisogno di una risposta (→ `FluidAlert`, `FluidConfirmSheet`), il messaggio deve restare nella pagina (→ `FluidInlineMessage`) o
dura più di qualche decina di secondi.

**Versione.** `FluidNotificationHost` e la card: prima della 2.4 (la correzione della luminanza è in 2.4.0); il dito, `actions`, le azioni di TalkBack e la card di 2 righe
a card chiusa: **2.12.0**; le parole da `LocalFluidLabels` e il colore del testo: 3.0.1.

## Token, costanti, Defaults

Non c'è un `object …Defaults` pubblico: le costanti del gesto (`NotificationExpandDistanceDp`, `NotificationDismissDistanceDp`,
`NotificationFlingVelocityDpPerSecond`, `NotificationPullDimensionDp`, `NotificationResumeFloorMillis`, …) sono `internal`, e i numeri di sopra sono quelli del
sorgente. Quello che un'app può regolare è nel dato: `FluidNotification.durationMillis` (6 000 ms di serie, mai meno di 1 500 effettivi) e `tone`.

## Cosa è verificato dai test

- `FluidNotificationHostStateTest` (engine-ui): la coda è FIFO e deduplica gli `id` in coda o a vista; la scadenza chiude la card in scena; un host inattivo
  non presenta né avvia il conto; il budget di lettura si ferma mentre l'host è inattivo; un messaggio senza `id` o senza testo è rifiutato; il riavvio
  dell'host tiene la richiesta corrente non presentata davanti alle altre.
- `FluidNotificationGestureTest` (engine-ui): le decisioni al rilascio (oltre la soglia apre; la spinta batte la distanza; il ripensamento torna; una card aperta
  non si riapre), la curva elastica e l'allungamento (zero con il movimento ridotto), l'isteresi, il tempo minimo dopo il rilascio, il conto fermo sotto un dito,
  una card aperta che non scade mai ma si chiude, una chiusura che arriva anche a dito sceso, il dito sulla card sbagliata, **il ritorno che non oltrepassa
  visibilmente il riposo**.
- `FluidNotificationDragTest` (sample, Robolectric): trascinare in su oltre la soglia lancia via e lascia il posto alla seguente; uno corto torna; in giù apre con le azioni e
  non scade più; il tasto di chiusura chiude; un trascinamento che parte dal tasto non è un tocco; l'azione di una card aperta la esegue e chiude; le azioni di accessibilità
  aprono e chiudono; un dito fermo ferma il conto e al rilascio riparte; con il vetro si comporta come senza.
- `FluidHostContentColorTest`: il titolo ha il colore della superficie sul tema scuro.

**Non è provato**: l'aspetto del vetro, i fotogrammi dell'arrivo e dell'uscita, TalkBack su un dispositivo, e il comportamento reale a fotogrammi (è la ragione per cui i
numeri del gesto sono funzioni pure: il resto si guarda in mano).

## Dove guardare

`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/FluidNotification.kt` (un solo file): i tipi (`FluidNotification`, `FluidNotificationTone`,
`FluidNotificationAction`, `FluidNotificationDelivery`), la coda e il tempo di lettura (`FluidNotificationHostState`, `runQueue`, `awaitDismissalOrActiveTimeout`),
`FluidNotificationHost`, i colori dei toni, **le funzioni pure del gesto** (`fluidNotificationRelease`, la curva, l'allungamento, la velocità di ritorno), `FluidNotificationDragState`,
`FluidNotificationCard`. Sample che la usano: `ChromeTab.kt` («Notifica in-app», «Notifica da aprire»), `MaterialTab.kt` (una notifica con un'azione e 60 s di durata, per guardarla
sul vetro), `GalleryActivity.kt` (l'host alla radice). Catalogo delle firme: [api/menu-modali.md](../api/menu-modali.md).
