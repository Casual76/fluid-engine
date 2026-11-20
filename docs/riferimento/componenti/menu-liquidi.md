<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/menu-liquidi.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Menu liquidi

Un menu dell'engine è una lista di voci (`FluidContextAction`) che **nasce da quello che l'ha aperto** e gli torna dentro al
congedo: un tasto, un gruppo di tasti della barra, una riga, una tessera. Gli ingressi sono tanti, il menu è uno: lo stesso
pannello, lo stesso viaggio, lo stesso materiale, la stessa aptica. Il modello mentale è una frase: **un menu si sceglie col dito
che l'ha aperto, e un rilascio altrove lo lascia aperto.** Tieni premuto, il menu si apre e prende il dito; trascina su una voce
e rilascia: scelta. Rilascia fermo, o fuori dalle voci: il menu resta e si sceglie con un tocco. Il perché di ogni scelta sta in
[regole.md](../regole.md) («Un menu si sceglie col dito che l'ha aperto»); qui c'è come si usa e cosa fa il codice.

Col vetro Liquid il menu dal tasto si muove come quello di iOS 26, in tre tempi: il tasto (o il gruppo intero) si raccoglie, sale
come una goccia verso il centro del menu e da lì si apre; le voci sono dentro dal primo fotogramma, piccole, sfocate e piegate
dalla lente, e si mettono a fuoco mentre il vetro si posa. Il menu contestuale solleva l'elemento tenuto premuto, scurisce la
pagina con una sfocatura leggera e fa nascere il menu accanto. Un menu sa avere una fila di simboli in cima, un sottomenu che si
apre nello stesso vetro, voci che lo lasciano aperto e un ordine che si capovolge quando si apre verso l'alto.

Il portale, l'host dei modali, l'avviso e i fogli stanno in [menu-modali.md](menu-modali.md). La superficie che viaggia
(`FluidPhysicsState`, la goccia, `driveExternally`) è di [fluid-physics.md](fluid-physics.md): qui si usa, non si spiega.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un tasto che apre un menu **e basta** («Ordina», tre puntini) | `FluidMorphMenuButton(opensOnTap = true)`; nella barra `FluidBarAction(actions = …, opensOn = FluidMenuOpensOn.Tap)` |
| un tasto con un'azione sua (il tocco) e un menu di varianti (il tenuto premuto) | `FluidMorphMenuButton` o `FluidBarAction(actions = …)` di serie |
| un menu che usa il vetro di **tutto il gruppo** di una barra dichiarata | `toolbar { group(…) { action(id, icon, label) { voci } } }` |
| un menu su una riga di lista | `FluidListRow(contextActions = { … })` (anche `FluidVividCard`, `FluidSidebarRow`) |
| un menu su una tessera, una card, una vista che non ha click propri | `Modifier.fluidContextMenu(actions = { … })` |
| un menu contestuale che solleva un'**anteprima** più grande, o con una **forma** sua | `Modifier.fluidContextMenu(…, preview = { … }, previewShape = …)` |
| un menu su un elemento che gestisce già i suoi click | `rememberFluidContextMenu` + `Modifier.fluidContextMenuAnchor`, e `open()` nel suo click lungo |
| una voce che dice la scelta di adesso («Ordina per…») | `FluidContextAction(selected = true)` |
| un gruppo di voci staccato dal resto («Elimina») | `FluidContextAction(startsSection = true)` (e `destructive = true`) |
| una voce che apre altre voci («Ordina ›») | `FluidContextAction(children = listOf(…))`: il sottomenu si apre dentro lo stesso vetro |
| una seconda riga sotto l'etichetta («Ordina» / «Per data») | `FluidContextAction(subtitle = …)` |
| un interruttore, o «+»/«−», che non chiude il menu | `FluidContextAction(keepsMenuOpen = true)` dentro `FluidMenuActions { … }` |
| la fila di simboli in cima («Condividi, Preferiti, Modifica») | `FluidMenuActions(elementSize = FluidMenuElementSize.Small)` (o `Medium`, con l'etichetta) |
| le prime voci vicino al dito quando il menu si apre verso l'alto | `FluidMenuActions(order = FluidMenuOrder.Priority)` |
| aprire il menu da una scorciatoia, senza un tasto | `FluidMorphMenuState.open(bounds, text, icon, items)` (si sceglie con un tocco) |
| spegnere il punto di luce della goccia | `CompositionLocalProvider(LocalFluidMenuPointOfLight provides false) { … }` |
| **non così**: `FluidGlassMenuButton` | deprecato: `FluidMorphMenuButton` + `FluidMorphMenuHost` |
| **non così**: un pannello di contenuto fatto con `FluidGlassModalPortal(presentation = ContextMenu)` per «fare un menu» | non ha il viaggio, la goccia né la scelta col dito |
| **non così**: molte voci | il menu non scorre: una riga è 48 dp e il pannello è alto quanto tutte le sue righe. Per una lista lunga, un foglio o `FluidPickerRow` |

## Gli ingressi

| ingresso | come | si apre | host | scrim |
|---|---|---|---|---|
| il tasto che si trasforma | `FluidMorphMenuButton(state, actions, onClick, …, opensOnTap)` | tenuto premuto; con `opensOnTap` al tocco (e `onClick` non viene chiamato) | `FluidMorphMenuHost` | nessuno: la pagina resta a colori |
| l'azione della barra | `FluidBarAction(icon, contentDescription, onClick, …, actions, opensOn)` | tenuto premuto; con `opensOn = Tap` al tocco | `FluidGlassModalHost` | quello dei modali |
| l'azione di una barra dichiarata | `toolbar { group { action(id, icon, label, menu = { … }) } }` | al tocco e tenendo premuto | `FluidGlassModalHost` | quello dei modali |
| la riga | `FluidListRow(contextActions = { … })` | tenuto premuto: la riga si solleva, il menu cresce accanto | `FluidGlassModalHost` | quello del contestuale, più scuro |
| un elemento qualunque | `Modifier.fluidContextMenu(actions = { … })` | tenuto premuto | `FluidGlassModalHost` | quello del contestuale |

Il **tenuto premuto apre sempre**, anche con `Tap`: è lui che porta il dito sul menu e permette di scegliere trascinando. `Tap` è
per il tasto il cui *unico* mestiere è il menu; un tasto che ha un'azione sua la tiene al tocco, e il menu si scopre tenendo
premuto. Ogni ingresso accetta una `List<FluidContextAction>` restituita da una lambda: la lambda si valuta **all'apertura**, quindi
legge lo stato del momento, e una lista vuota vuol dire nessun menu (`open()` ritorna `false`, nessun `Open`, e il chiamante può
ripiegare sul suo click lungo).

## Le voci: `FluidContextAction`

**Cos'è.** Una voce: etichetta, icona facoltativa, e cosa fare. È la stessa classe per tutti gli ingressi.

```kotlin
data class FluidContextAction(
  val label: String,
  val icon: ImageVector? = null,
  val destructive: Boolean = false,    // colore d'errore, al più una per menu
  val enabled: Boolean = true,         // spenta: niente tick, non si sceglie
  val startsSection: Boolean = false,  // banda sopra di lei (sulla prima voce non conta)
  val selected: Boolean = false,       // spunta a destra, «selezionato» per TalkBack
  val subtitle: String? = null,        // seconda riga, più piccola e più tenue
  val children: List<FluidContextAction> = emptyList(), // sottomenu, un livello
  val keepsMenuOpen: Boolean = false,  // la scelta non chiude il menu
  val onClick: () -> Unit,
) {
  // La firma della 3.x, sette argomenti: la chiamata per posizione resta valida.
  constructor(label: String, icon: ImageVector?, destructive: Boolean, enabled: Boolean,
    startsSection: Boolean, selected: Boolean, onClick: () -> Unit)
}
```

**Uso** (da `MenusDemo`, le voci di prova della galleria):

```kotlin
private fun demoActions(pick: (String) -> Unit): List<FluidContextAction> = listOf(
  FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { pick("Condividi") },
  FluidContextAction(label = "Aggiungi ai preferiti", icon = FluidIcons.Filled.Star) { pick("Aggiungi ai preferiti") },
  FluidContextAction(label = "Modifica (spenta)", icon = FluidIcons.Edit, enabled = false) { pick("Modifica") },
  FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {
    pick("Elimina")
  },
)
```

**Comportamento.**

- **La scelta**: `Tap`, poi la richiesta di congedo e `onClick`, nello stesso istante. Se `onClick` apre un altro modale, questo sta
  **sopra** il menu che sta uscendo. Fanno eccezione la voce coi `children` (apre il sottomenu e non chiama il suo `onClick`) e la
  voce `keepsMenuOpen` (chiama `onClick` e il menu resta).
- **`selected`** è per i menu che scelgono fra alternative («Ordina per», «Vista»): la spunta va a destra, nel colore della voce, e
  TalkBack dice «selezionato». Il pannello si allarga per tenerla.
- **`subtitle`** è la seconda riga, `bodyMedium` al 60% dell'inchiostro della voce; la riga passa da 48 a 60 dp (segue il font).
  L'etichetta non si tronca per fargli posto: il pannello si allarga per il più largo dei due, fino al tetto di sempre.
- **`children`** fa della voce un sottomenu: il gallone a destra e, scelta, il menu **diventa** il sottomenu dentro lo stesso vetro
  (vedi «La struttura»). Un livello solo: i figli dei figli si mostrano come righe semplici.
- **`keepsMenuOpen`**: la scelta fa `Tap` e chiama `onClick`, e il menu resta com'è. Perché la voce mostri lo stato nuovo (la spunta
  di un interruttore) il menu va scritto con `FluidMenuActions { … }`, il cui contenuto si rilegge a ogni ridisegno: una
  `listOf(…)` resta la fotografia dell'apertura.
- **`destructive`** è un colore, non una conferma: l'azione parte alla scelta. Per una cosa che non si disfa, la voce apre un
  `FluidConfirmSheet` o un `FluidAlert`.
- L'etichetta sta su una riga sola, con i puntini oltre il tetto della larghezza (vedi «Il pannello»).

**La firma e la compatibilità.** Tutti i campi con un valore di serie stanno **prima** di `onClick`, perché la lambda in coda resti
l'azione. La chiamata della 3.x a sette argomenti per posizione (`FluidContextAction("X", null, false, true, false, false, {})`)
risolve sul costruttore secondario, e così quella con tutti e sette i nomi e quella con sei argomenti e la lambda in coda (Kotlin
preferisce il candidato che non usa valori di serie). Della data class cambiano tre cose rispetto alla 3.x: `copy` **per nome**
compila come prima e porta i campi nuovi (per posizione il settimo è `subtitle`); la destrutturazione a sette non è più l'azione
(`component7` è `subtitle`, `onClick` è `component10`); `equals` e `hashCode` contano i campi nuovi. Per la JVM cambia il
costruttore con i valori di serie: un modulo già compilato va ricompilato. Lo fissa `GlassCompatSignaturesTest`.

## La struttura: `FluidMenuActions`, `FluidMenuOrder`, `FluidMenuElementSize`

**Cos'è.** Le regole di disposizione di un menu, da SwiftUI e UIKit: `menuOrder`, `preferredElementSize`,
`menuActionDismissBehavior`, i sottomenu. La struttura viaggia **dentro la lista**: `FluidMenuActions` *è* una lista di voci, quindi
va in ogni ingresso senza parametri nuovi, e chi non la conosce la legge come una lista.

```kotlin
class FluidMenuActions(
  val order: FluidMenuOrder = FluidMenuOrder.Automatic,
  val elementSize: FluidMenuElementSize = FluidMenuElementSize.Large,
  content: () -> List<FluidContextAction>,          // riletto a ogni ridisegno: può leggere stato
) : AbstractList<FluidContextAction>() {
  constructor(actions: List<FluidContextAction>, order: FluidMenuOrder = …, elementSize: FluidMenuElementSize = …)
  fun current(): List<FluidContextAction>
}
enum class FluidMenuOrder { Automatic, Priority, Fixed }
enum class FluidMenuElementSize { Small, Medium, Large }
```

**Uso** (da `MenusDemo`, la riga «Cartella Progetti»: la fila di simboli, un sottomenu col sottotitolo vivo, un interruttore che
lascia aperto):

```kotlin
var sort by remember { mutableStateOf("Per data") }
var previews by remember { mutableStateOf(true) }
FluidListRow(
  title = "Cartella Progetti",
  subtitle = "Ordine: $sort",
  onClick = {},
  contextActions = {
    FluidMenuActions(elementSize = FluidMenuElementSize.Small) {
      listOf(
        FluidContextAction(label = "Condividi", icon = FluidIcons.Share) {},
        FluidContextAction(label = "Preferiti", icon = FluidIcons.Filled.Star) {},
        FluidContextAction(label = "Modifica", icon = FluidIcons.Edit) {},
        FluidContextAction(
          label = "Ordina",
          subtitle = sort,
          startsSection = true,
          children = listOf("Per data", "Per nome", "Per dimensione").map { label ->
            FluidContextAction(label = label, selected = label == sort) { sort = label }
          },
        ) {},
        FluidContextAction(label = "Mostra anteprime", selected = previews, keepsMenuOpen = true) { previews = !previews },
        FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {},
      )
    }
  },
)
```

L'ordine, su un tasto in basso (da `MenusDemo`, il palco «Priority e Fixed»):

```kotlin
FluidBarAction(
  icon = FluidIcons.MoreVert,
  contentDescription = "Priority",
  onClick = {},
  actions = { FluidMenuActions(actions = voci, order = FluidMenuOrder.Priority) },
  opensOn = FluidMenuOpensOn.Tap,
)
```

**Comportamento.**

| cosa | come |
|---|---|
| **ordine** | `Priority`: se il menu si apre verso l'alto (il pannello sta sopra l'ancora: un tasto in basso, una riga vicina al fondo) le sezioni vanno in ordine inverso e le voci di ogni sezione pure, così la prima voce scritta sta in fondo, vicino al dito; le bande seguono le sezioni. `Fixed`: sempre come scritto. **`Automatic`** usa `Priority` dal tasto (anche `FluidMorphMenuHost`) e `Fixed` dal contestuale. Per un tasto in un contenuto scorrevole che deve conservare l'ordine dichiarato si può scegliere `Fixed` |
| **fila di simboli** | `Small` e `Medium` mettono le prime voci della **prima sezione** su una fila in cima: `Small` fino a quattro simboli senza etichetta (che resta per TalkBack), alta quanto una riga; `Medium` fino a tre con l'etichetta corta sotto (`bodySmall`), alta 68 dp. Le tessere si dividono la larghezza del pannello e non la allargano; fra due tessere un filo verticale, sotto la fila una banda. Le voci della prima sezione che non ci stanno continuano nella lista. Capovolto, la fila va in fondo, con le tessere nell'ordine scritto |
| **sottomenu** | la voce coi `children` ha il gallone; scelta (col dito, anche quello che ha aperto, o con TalkBack) fa `Tap` e il menu **diventa** il sottomenu: in cima la voce madre in grassetto col gallone girato in giù, sotto i figli con le loro sezioni. Il vetro cambia forma restando dov'era (stesso centro orizzontale, stesso bordo da cui il menu era cresciuto) con la molla d'andata del menu (0,72 / 500); le voci di prima se ne vanno sfocandosi e quelle nuove arrivano sfocate e si mettono a fuoco nel tempo di un cambio di contenuto (T = 300 ms: due strati che si incrociano a ≈0,47 T con l'opacità complessiva ≈0,7, chi esce sparito a 0,66 T, chi entra nitido a T e pieno a 1,15 T): il vetro non resta mai vuoto. Sotto API 31, senza sfocatura, i due strati si passano il posto senza sovrapporsi. L'intestazione, o il **back**, torna indietro allo stesso modo; il secondo back congeda. Uno scrim toccato congeda tutto, e un congedo dentro il sottomenu riporta a casa la sagoma del sottomenu |
| **chiusura** | `keepsMenuOpen`: `Tap`, `onClick`, il menu resta; la goccia si posa e se ne va come dopo ogni rilascio |

**Dove vale.** Nei menu dell'host dei modali (azione della barra e contestuale) e in `FluidMorphMenuHost`.
La stessa dichiarazione porta la fila, l'ordine e il sottomenu anche al tasto che si trasforma.

**Non così**: un sottomenu dentro un sottomenu (Apple ne vuole uno: oltre, un menu a parte o un foglio); la fila per voci senza
simbolo (si vedrebbe l'etichetta stretta nella tessera); `keepsMenuOpen` su una `listOf(…)` fotografata, se la voce deve cambiare
aspetto.

## Il menu dal tasto e dal gruppo: la nascita di Apple

**Cosa si vede** (vetro Liquid, campo del vetro disponibile: dall'API 33, con gli shader in salute; altrimenti vedi «Senza il
campo»). Dalla sessione «contestuale» (fase tasto) i due menu nascono **come quelli di Apple**, ognuno sul suo riferimento: il tasto
singolo come il menu dal tasto del telefono (WWDC25 219 a 5:06, «B1 a»), il gruppo come la graffetta sulla foto (7:15, «B1 b»), la
stessa nascita del contestuale. Il tasto singolo è più rapido perché Apple lo fa così.

**Dal tasto singolo** (B1 a, `FluidButtonBirthProfile`):

1. **Una goccia sul tasto.** Il tasto premuto diventa una goccia quasi quadrata, con gli angoli molto arrotondati (al 31% del lato
   corto a 33 ms, al 20% a 100 ms, poi verso il raggio del pannello): cresce dall'angolo del tasto, e il lato del tasto sul margine
   resta fermo. A 33 ms è al 58% del menu, a 67 ms all'80%.
2. **Pieno a ~133 ms, poi rilascia.** Il corpo arriva alla misura del pannello verso 110–133 ms e **sale** sopra la posa (il bordo
   lontano fino al 7–8%, il bordo vicino resta un 5% sopra il tasto) allargandosi del 3–4% a 167–200 ms; poi scende e si posa, fermo
   a ~367 ms. Nessun uovo: la sagoma è un rettangolo arrotondato che cresce.
3. **Le voci subito, ingrandite.** Entrano nei primi 33 ms (a 33 ms in Apple si legge già «Wis»), piegate e ingrandite dalla lente
   fino a 1,42×, e si mettono a fuoco in 270 ms (Apple: a fuoco a ~267 ms).

**I primi fotogrammi.** Il velo non va mai avanti alla goccia: nella nascita dal tasto e dal gruppo è la sua dissolvenza letta
sull'orologio della nascita, se questa è più indietro (`fluidMenuBirthFade`); l'animazione del velo parte infatti un fotogramma prima
dell'orologio della nascita. Velo e nascita corrono poi su un orologio costante (`FluidSteadyFrameClock`): un fotogramma lungo avanza
al più di 1/30 s, come la goccia («uno stallo non è un moto»). Fino a S4f il velo andava un fotogramma avanti e i primi fotogrammi
di un menu, i più cari e a 30 Hz a schermo fermo, facevano correre la nascita di nascosto. Sul Tab S9 «Altro» passava così dal quadrato stondato al pannello quasi pieno in 33,7 ms
(f0004 → f0005), dove B1 (a) cresce per ~133 ms.

**L'anticipo e la goccia in ritardo.** La sagoma chiesta alla goccia è quella di Apple qualche ms dopo (`FarLead`, `WidthLead`,
`NearLead`), perché la goccia la segue in ritardo; finché la goccia non ha un campione il vetro disegnato è proprio la sagoma
chiesta. L'anticipo è tarato al banco, dove la goccia campiona al secondo fotogramma a 60 Hz (`FluidMenuDropOnTimeMillis`, 33,4 ms):
sul dispositivo può partire più tardi (i primi fotogrammi lenti, uno stallo al suo primo disegno), e con l'anticipo letto
sull'orologio la sagoma disegnata nell'attesa, e quella su cui la goccia si posa, correvano avanti ad Apple, poi la goccia partiva
ferma dietro di lei. Il ritardo oltre quello del banco si sconta (`FluidMenuJourney.birthLeadAge`, `leadAge`,
`LiquidDrop.sampling`): nell'attesa l'anticipo resta quello del banco, dal primo campione conta dall'orologio meno il ritardo in
più. Al banco nulla cambia; con la goccia due fotogrammi in ritardo lo scarto da Apple da 67 ms scende da 0,12 a 0,06 (simulato
coi due bordi della goccia). Del superamento di Apple se ne chiede metà (`OvershootShare`): la goccia, anche tesa, ne aggiunge un
altro 2–3%, e chiesto tutto la larghezza arrivava a 1,06 contro 1,036 di B1 (a), al banco e sul Tab S9.

**Dal gruppo** (un'azione con `menu` dentro un gruppo con lo sfondo condiviso di una barra dichiarata; B1 b, la graffetta):

1. **Le icone si sfocano ed escono** (sfocate al massimo a ~41 ms, via a ~54 ms, con le curve del cambio di contenuto della barra),
   mentre **la capsula si stringe in una goccia sul tasto premuto** (al 63–67% della sua larghezza a 33 ms, la goccia a 67 ms).
2. **La goccia sale con la lente**: limpida, ingrandisce ciò che ha sotto (67–117 ms); le voci minuscole entrano fra 67 e 117 ms.
3. **Fagiolo, poi uovo storto con la coda verso il tasto** (133–233 ms), che si stacca dalla barra (fino al 15% dell'altezza a ~234
   ms) e torna.
4. **Il corpo si allarga** a botte e **si posa nel rettangolo** col superamento di Apple (3% a 367–467 ms): pieno verso 334 ms,
   fermo verso 770–868 ms; voci a fuoco a ~334 ms.

È la nascita del contestuale ([sotto](#la-nascita-del-contestuale-fluidcontextbirth-in-fluidcontextmenukt)) col seme sul tasto
premuto: `fluidGroupBirthSlab` raccoglie la capsula nella goccia e poi chiama `fluidContextBirthSlab`; il contorno è
`fluidContextBirthWarp` con la coda sul tasto.

**Al ritorno capsula e icone tornano insieme.** La bolla del ritorno avvolge la capsula: è la sorgente gonfiata dello stesso
margine su ogni lato (`fluidMenuReturnSlab(wrapSource = true)`), e il vetro ci si posa sopra come la goccia di B1 (a) sul tasto
(f9191–f9194). Per un tasto quadrato è il tondo di sempre; per la capsula del gruppo una pillola più larga di lei. Fino a S4g era un
tondo sul lato corto, più stretto delle icone: il ritorno passava da una pillola stretta e vuota alla capsula larga coi glifi in un
passo (scuro f0131 → f0132, chiaro f0058 → f0059). Le icone del gruppo restano spente mentre il vetro torna verso la capsula e si
riaccendono nell'ultimo tratto (`fluidMenuGroupFaceReturn`), e solo quando il
vetro le tiene già tutte e tre e non è più il menu: la copertura (`fluidMenuGroupFaceCover`) è zero finché il vetro non contiene
le icone, cresce col margine che ha attorno a loro ed è piena a 5 dp, due fotogrammi a 120 Hz dopo; è zero anche se il vetro
è più di 1,5–2 volte la capsula. Le icone si accendono intere e tutte e tre insieme (al ritorno la barra le registra senza il
ritaglio della goccia): mai a pezzi, mai la capsula vuota. Una prima regola di S4f, la capsula a 0,8 di intersezione su unione,
le teneva spente ~66 ms col vetro già attorno a loro nell'annullo. **Icone e voci non stanno mai insieme**: le voci sfumano mentre
il vetro comincia ad avvolgere la capsula, finché la fase delle icone scende da 1 a `FluidMenuGroupFaceVisiblePhase`
(`fluidMenuGroupReturnInk`, `fluidMenuGroupItemsGate`), e sono zero da lì, dove la curva d'uscita della barra comincia a rendere
visibili le icone. Da fermo a quel punto sono già quasi spente; nell'annullo sfumano in uno o due fotogrammi e le icone arrivano
dopo. La fase la scrive **un punto solo**, la superficie del menu che ha appena disegnato il vetro (`FluidMenuJourney.noteGroupGlass`,
nel suo disegno, prima del vetro): la barra, l'host e le voci la leggono e basta. La barra la vede al più un fotogramma dopo, le
voci in quello stesso, quindi le icone non sono mai avanti alle voci. E l'opacità delle icone la mette **l'host**, disegnando le
facce registrate dalla barra (`fluidMenuGroupFacesAlpha`, la curva d'uscita della barra sulla stessa fase); durante il ritorno la
barra le lascia piene. Sul Tab S9, coi marcatori `menu.group`, `menu.ink`, `menu.faces` e `toolbar.face`, lo strato delle icone della
barra non si ricalcola durante il ritorno (solo all'apertura, a fase 0 e opacità 1), e fino a S4g-ter l'host le disegnava così
com'erano appena la fase scendeva sotto lo span: icone piene sopra voci ancora vive (S4g-ter, chiaro f0129, scuro f0119), che al
banco, dove la curva della barra si ricalcolava a ogni fotogramma, non c'era. A gruppo consegnato (la capsula tolta all'ancora) le
voci sono zero, non l'inchiostro grezzo.
In S4g le icone entravano col quadrato dell'inchiostro uscito, ma la curva d'uscita le dava già quasi piene a metà, e sul Tab S9
restavano voci sfocate dietro le tre icone piene (S4g-bis, chiaro f0361 e f0391–f0392). Fino a S4f tornavano col
progresso, ritagliate dalla bolla che si allargava (in scuro f0236 la stella sola, i glifi ai lati a metà), e nell'annullo a metà
nascita, col progresso già basso, comparivano sopra l'uovo e le righe (f0193–f0195). Le disegna l'host
**sopra** il suo vetro (`FluidMenuAnchor.groupFaces`, `groupFacesAbove`), e alla consegna sono già quelle della barra, che le legge
dallo stato vivo nel disegno. Fino a S4b le disegnava la barra, sotto il vetro del menu che era già capsula: 2–3 fotogrammi di
capsula vuota sul Tab S9. Una volta tornate, le icone non si spengono più in quel ritorno; nemmeno il glifo del tasto
(`FluidMenuJourney.faceAlpha`): nell'annullo la goccia arriva con la velocità della nascita e oscilla attorno al tasto, e i
puntini comparivano per un campione, sparivano sotto la goccia più piccola del tasto e tornavano (S4g, chiaro f0023–f0028). A
ritorno consegnato (`FluidMenuReturnFaceDelivered`) il glifo c'è anche se la goccia oscilla ancora, scalato su di lei.

**Le voci stanno nella lente**, sfocate (fino a 5 dp) e piegate dallo stesso campo che disegna la sagoma (la lente del contenuto,
`GlassContentLensShader.kt`: un `RenderEffect` sul solo layer delle voci); lo stesso campo ritaglia l'inchiostro anche sui fianchi
concavi. Quello che la lente ingrandisce oltre il vetro **entra schiacciato nel suo bordo**, mai tagliato: il tiro di ogni lato è
quanto le voci ne escono (`glassMenuLensOverflow`, l'uniform `lensOverflow`), così il bordo delle voci cade sul bordo del vetro, come
«Recents» e «Tracking» in B1 (a) a f9151–f9152. Fino a S4f il ritaglio della goccia tagliava a metà «Condividi» ed «Elimina» nella
nascita di «Altro» (f0007) e le icone a destra (f0009). A tiro zero la lente è quella di prima, al bit. **Le voci le ritaglia lo
shader** (`clipInk`), non un ritaglio sopra il loro strato: sotto un `clipPath` Skia rende per un `RenderEffect` solo la parte dello
strato dentro il ritaglio (uno shader della lente non dichiara quanto lontano legge), e la lente che prende le voci da fuori il vetro
trovava il bordo dello strato ripetuto o niente. Sul Tab S9 «Condividi» diventava un codice a barre al bordo alto ed «Elimina»
restava tagliata da una linea netta (S4g, chiaro f0008), mentre al banco lo strato era intero. La sagoma ritaglia per conto suo la
goccia della scelta e il livello che se ne va (`drawInMenuSilhouette`); le voci solo dove lo shader non c'è
(`GlassContentLensEffect.clips`). Per lo stesso motivo **lo strato delle voci sta sempre nella finestra**: ingrandite fino a 1,42× e
portate verso il tasto, le voci di «Altro» vicino al bordo destro dello schermo ne uscivano, e Skia dava alla lente solo la parte
dentro lo schermo: le icone di condivisione e della stella entravano tagliate (S4g-bis, chiaro f0010–f0012, scuro f0085–f0086; in
RTL, dove lo spostamento dell'inchiostro va verso l'interno, entravano intere). Il nodo delle voci porta la mappa del viaggio, per il
dito e la semantica; le celle hanno il loro layer, e l'opacità e la lente stanno su uno strato esplicito (`inkLens`) che si sposta
dentro la finestra (`fluidMenuInkOnScreen`, `fluidMenuInkFrame`) e che lo shader riporta al suo posto (l'uniform `inkShift`, zero
quando lo strato ci sta). **Lo strato della lente ha un limite** (`fluidMenuInkLimit`): le voci intere, spostate nella finestra, più
la sfocatura (`fluidMenuInkHaloPx`). Lo shader della lente può colorare anche il trasparente, e senza limite Skia alloca per il filtro
un intermedio grande quanto la finestra divisa per la scala delle voci: sul Tab S9 la prima apertura del gruppo arrivava a ~1 s
(S4g contro S4f, bisezione F7.3k; il costo era l'allocazione nel driver Adreno). La lente tira le voci verso l'esterno, quindi
quello che disegna sta dentro lo strato delle voci: il limite non toglie niente, e l'intermedio resta della misura del pannello. Il layout e le celle sono quelli del pannello a riposo, ma **il dito sceglie la voce che vede**: mentre il menu nasce le
voci stanno col corpo (staccate dalla sorgente, rimpicciolite nella goccia: nel gruppo e nel contestuale fino a ~23 dp dal loro
riquadro a 300 ms, ~45–50 dp a 200 ms, sotto i 5 dp da ~500 ms), e la selezione riporta il punto del dito attraverso la stessa
mappa con cui si disegnano (`FluidMenuSelection.pointerMap`, `fluidMenuPointerAtRest`: spostamento e scala, non la piega della
lente). Così il dito che ha aperto può scegliere durante il viaggio. Pannello, voci, faccia del tasto e card sollevata si posano
in coordinate della finestra come il vetro (`absoluteOffset`), dentro un contenitore che posa in alto a sinistra anche in RTL
(`AbsoluteAlignment.TopLeft`, nel livello dei menu e nell'host del menu che si trasforma): in RTL le voci restano nel vetro. Fino a
S4b `offset` le specchiava dall'altra parte dello schermo; fino a S4c il contenitore allineava all'inizio, che in RTL è a destra, e
`absoluteOffset` partiva da lì (x = larghezza dell'host − quella del pannello + la sua sinistra): «Altro» con le voci a destra e
tagliate, il gruppo e il contestuale col pannello vuoto (ripresa 24).

**Il congedo** da fermo: il bordo lontano cede nei primi 67 ms, poi il corpo si raccoglie in una bolla sulla colonna del tasto e si
posa in circa 233 ms. Le voci si sfocano subito (curva di 100 ms) ma **restano dentro il vetro fino a ~67 ms e se ne vanno entro
~167** (l'inchiostro di Apple, `fluidContextExitInkSpec`, lo stesso del contestuale): mai un vetro vuoto che si stringe; fino a
lì sono sfocate ma piene (`FluidMenuJourney.lensInkAlpha`). Dal tasto singolo la lente le ingrandisce anche mentre se ne vanno,
come in B1 (a): alla loro misura a 33 ms, ~1,35× a 100 ms, 1,42× a ~117 ms (`fluidMenuLensMap(returning = true)`). Quando la
bolla tocca casa, la capsula del gruppo e le sue icone tornano insieme. Le tracce del banco sono in `tools/glassbench/traces/tasto/`
e `f5a-corr/`.

**Un congedo a metà apertura torna indietro dalla sagoma che c'era**: la nascita si ferma nell'istante in cui si decide
(`retarget`) e il ritorno parte da lì, con la molla dei menu liquidi; nel gruppo l'uovo si spegne mentre il ritorno lascia la sagoma
fermata. Riaperto a metà chiusura, il menu riprende la nascita dall'istante del suo progresso.

**Il punto di luce** è acceso di serie (`LocalFluidMenuPointOfLight`, vero); dall'API 33. Un'app che non lo vuole lo spegne una volta
sola, attorno all'host:

```kotlin
CompositionLocalProvider(LocalFluidMenuPointOfLight provides false) {
  FluidGlassModalHost(state = modals, backdrop = backdrop)
}
```

**La sorgente.** Il viaggio parte dalla sagoma vera dell'elemento: il tasto come il dito lo sta gonfiando (`fluidPressedBounds`, mai
il rettangolo a riposo, che sarebbe un sussulto all'indietro proprio dove l'occhio guarda), o la capsula del gruppo letta dal
registro del piano della barra (`rememberFluidToolbarTransitionSource`, vedi [toolbar.md](toolbar.md)). Se il pezzo non c'è (una
barra scritta con `actions`, un tasto fuori da un contenitore) la sorgente è il tasto. Un'azione finita in «Altro» porta le sue voci
in quel menu.

**Senza il campo** (vetro Classic, API sotto la 33, shader del campo non sani) il menu dal tasto fa il viaggio di prima: la sagoma
cresce dal tasto con la molla d'andata (0,72 / 500), le voci arrivano fra il 40% e il 75% del viaggio ritagliate nel corpo, nessuna
lente né punto di luce, e il ritorno usa la molla lenta (rigidezza 158, ≈0,5 s).

## Il tasto che si trasforma: `FluidMorphMenuButton`, `FluidMorphMenuHost`, `FluidMorphMenuState`

**Cos'è.** Il tasto che *si espande* nel proprio menu: la capsula del tasto viaggia fino a essere il pannello e al congedo torna
capsula. Il viaggio è quello in tre tempi, con le voci nella lente e il punto di luce. Il predecessore (`FluidGlassMenuButton`) faceva
comparire il pannello *sopra* il tasto: due superfici e una staffetta. Qui la superficie è una.

```kotlin
@Composable
fun FluidMorphMenuButton(
  state: FluidMorphMenuState = LocalFluidMorphMenuState.current ?: rememberFluidMorphMenuState(),
  actions: () -> List<FluidContextAction>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  icon: ImageVector? = null,
  backdrop: GlassBackdropState,           // obbligatorio: il fondale del tasto
  enabled: Boolean = true,
  opensOnTap: Boolean = false,
)

@Composable
fun FluidMorphMenuHost(state: FluidMorphMenuState, backdrop: GlassBackdropState, modifier: Modifier = Modifier)

val LocalFluidMorphMenuState: ProvidableCompositionLocal<FluidMorphMenuState?>
```

Tutti i parametri: [api/fluid-physics.md](../api/fluid-physics.md).

**Uso** (da `MenusDemo`, il palco in cima alla pagina: «Azione» si tiene premuto, «Ordina» si apre al tocco):

```kotlin
val menu = rememberFluidMorphMenuState()
var order by remember { mutableStateOf("Per data") }
CompositionLocalProvider(LocalFluidMorphMenuState provides menu) {
  FluidMorphMenuButton(
    actions = {
      listOf("Per data", "Per nome", "Per dimensione").map { label ->
        FluidContextAction(label = label, selected = label == order) { order = label }
      }
    },
    onClick = {},
    text = "Ordina",
    backdrop = backdrop,
    opensOnTap = true,
  )
  FluidMorphMenuHost(state = menu, backdrop = backdrop)   // alla radice, o nel palco: il menu cresce dentro l'host
}
```

**Comportamento.**

- **L'ancora** è una capsula di vetro con i gesti dei tasti (`FluidPressFeedback.Expand`: cresce e si illumina sotto il dito, 46 dp
  di altezza, 20 dp di margine laterale). Tocco = `onClick` (un `PressClick` come ogni tasto), tenuto premuto = menu (un solo `Open`,
  e il dito passa al menu), `opensOnTap` = il tocco apre (con un `Open`, non un `Tap`).
- **Mentre il menu è in scena l'ancora non disegna niente**, e al suo posto, allo stesso pixel, sta la superficie dell'host: un
  cambio di proprietario, mai un alpha di un vetro sopra un altro vetro. Lo spazio resta riservato.
- **Il pannello sta sopra il tasto se ci sta, altrimenti sotto**, col centro il più vicino possibile al tasto e sempre dentro i
  margini dell'host (16 dp): un menu che nasce da un tasto in basso cresce verso l'alto.
- **L'apertura è sincrona.** `openFrom` risolve il pannello con le misure che l'host ha scritto al suo ultimo layout, installa il
  viaggio e nasconde il tasto *nello stesso istante del gesto*: il primo fotogramma dell'host è già la sagoma sul tasto.
- **Un tocco fuori, ovunque**, chiude (in silenzio): se l'host è in un palco, le quattro fasce attorno le disegna l'host dei modali,
  l'unico che vede tutto lo schermo. Il **back** chiude prima di qualunque pagina: l'host compone il suo `BackHandler` solo mentre il
  menu è aperto, quindi è il gestore registrato per ultimo anche sopra un `FluidPageHost` annidato. Il congedo lo toglie.
- **Riaprire dallo stesso tasto mentre si chiude è un'inversione**: il viaggio torna verso il pannello da dove si trova.
- Il colore del testo dentro l'host è `onSurface`.

**`FluidMorphMenuState`.** `isOpen` (dal gesto alla richiesta di congedo), `isOnScreen` (finché c'è qualcosa in scena, ritorno
compreso), `dismiss()`, e `open(bounds, text, icon, items, content)`: apre dalla sagoma di `bounds` (pixel della radice) **senza il
dito**, per un tasto tuo. Chi lo usa nasconde la propria ancora finché `isOnScreen` è vero. `LocalFluidMorphMenuState` evita di
passare lo stato livello per livello: l'app lo fornisce una volta e il tasto lo prende da solo.

**Trappole.**

- *Il tasto non apre niente e non vibra*: manca il `FluidMorphMenuHost` composto (l'apertura legge le sue misure). Con `opensOnTap`
  il tocco è morto (non chiama `onClick`).
- *Il tasto ha il proprio stato*: senza un `LocalFluidMorphMenuState` e senza `state`, il default è `rememberFluidMorphMenuState()`,
  uno stato che nessun host pilota. Il tasto fa `onClick` e il menu non si apre.
- *Un solo menu per stato*: se un menu è in scena e un secondo tasto prova ad aprire, la richiesta è ignorata (`openFrom` ritorna
  falso e `Open` non suona). Per due menu simultanei servono due stati.
- *Il tasto non riappare appena il menu si chiude*: è voluto. Il congedo aspetta che anche la coda della goccia
  (`physics.liquid.quiet`) si sia posata sulla sagoma del tasto, o il tasto vero comparirebbe sopra una goccia ancora in moto.

**Non usarlo quando** il tasto non è una capsula di vetro (nella barra c'è `FluidBarAction(actions)`) o non hai un punto in cui
montare l'host.

## L'azione della barra: `FluidBarAction(actions, opensOn)` e `action(menu = …)`

**Cos'è.** L'azione in una barra (una lente di vetro da 46 dp sul fondale condiviso della barra) con un menu facoltativo. Con
`actions` non nullo l'azione diventa l'**ancora di un menu** dell'host dei modali, con lo stesso viaggio in tre tempi del tasto che
si trasforma. Nella barra dichiarata (`toolbar { … }`) la stessa cosa è `action(id, icon, label, menu = { … })`, e dentro un gruppo
con lo sfondo condiviso il vetro che diventa il menu è quello del gruppo intero.

```kotlin
@Composable
fun FluidBarAction(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  actions: (() -> List<FluidContextAction>)? = null,
  opensOn: FluidMenuOpensOn = FluidMenuOpensOn.LongPress,
  tint: Color = Color.Unspecified,
)
// con un contenuto tuo (un avatar) al posto dell'icona: stesso menu, stessa regola
@Composable
fun FluidBarAction(contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
  actions: (() -> List<FluidContextAction>)? = null, opensOn: FluidMenuOpensOn = FluidMenuOpensOn.LongPress,
  content: @Composable () -> Unit)

enum class FluidMenuOpensOn { LongPress, Tap }
```

**Uso** (da `MenusDemo`): la stella si tiene premuta, i tre puntini si aprono al tocco; poi il gruppo di una barra dichiarata.

```kotlin
FluidBarAction(icon = FluidIcons.Filled.Star, contentDescription = "Preferiti", onClick = {}, actions = { demoActions(pick) })
FluidBarAction(
  icon = FluidIcons.MoreVert,
  contentDescription = "Altro",
  onClick = {},
  actions = { demoActions(pick) },
  opensOn = FluidMenuOpensOn.Tap,
)

FluidScreen(
  title = "Foto",
  toolbar = {
    group(id = "photo-tools", placement = FluidToolbarPlacement.BottomBar) {
      button("share-photo", FluidIcons.Share, "Condividi foto", { pick("Condividi foto") })
      button("star-photo", FluidIcons.Filled.Star, "Preferiti foto", { pick("Preferiti foto") })
      action("photo-menu", FluidIcons.MoreVert, "Menu del gruppo") { demoActions(pick) }
    }
  },
) { /* contenuto */ }
```

**Comportamento.** Il pannello si posa **sull'ancora** (`OverAnchor`: il bordo da cui si allontana resta fermo, così il tasto non
salta nel fotogramma in cui si apre), verso il basso se ci sta, altrimenti verso l'alto dal bordo basso dell'ancora. La sagoma parte
dal tasto *premuto* (`FluidPressFeedback.ExpandRound`), con la pelle, la lente e l'inchiostro del tasto com'è adesso, anche a metà di
un giro sul nero; la faccia (l'icona) se ne va entro il 35% del viaggio. Con `Tap` il tocco apre senza il dito (il menu si sceglie
toccando) e suona `Open`; con `LongPress` il click lungo apre e passa il dito, suonando `Open` solo se il menu si apre.
`FluidMenuOpensOn` vale solo per questo ingresso: il tasto che si trasforma ha il booleano `opensOnTap`, l'azione della barra
dichiarata apre al tocco e tenendo premuto.

**Trappole.** *Senza `FluidGlassModalHost` il menu non si apre*; con `opensOn = Tap` e senza host il tasto è **morto** (il tocco
chiama l'apertura, non `onClick`). Con `actions = null` è il vecchio tasto della barra e nient'altro.

## Il menu contestuale: `Modifier.fluidContextMenu`, `rememberFluidContextMenu`, `fluidContextMenuAnchor`

**Cos'è.** Tieni premuta una riga o una tessera: l'elemento si **solleva** (diventa una card di vetro sorella della sua immagine, o
l'anteprima che gli dai), la pagina va **dietro uno scrim più scuro di quello dei menu, con una sfocatura leggera**, e il pannello
cresce accanto, da una goccia appesa al bordo della card, sotto il dito. Due forme e non una, e la divisione non è ordine: un
`detectTapGestures { onLongPress }` accanto al `combinedClickable` di una riga **non scatta mai** (il click consuma il gesto e il click
lungo viene mangiato in silenzio). Chi gestisce già i click apre il menu dal **proprio** click lungo con
`FluidContextMenuController.open()`; `Modifier.fluidContextMenu` è la forma comoda per gli elementi che *non* gestiscono click.

```kotlin
@Composable
fun Modifier.fluidContextMenu(
  actions: () -> List<FluidContextAction>,
  enabled: Boolean = true,
  previewShape: Shape? = null,                  // la forma del sollevamento: contentShape(.contextMenuPreview, _)
  preview: (@Composable () -> Unit)? = null,    // un'anteprima diversa dall'elemento: contextMenu(menuItems:preview:)
): Modifier

@Composable
fun rememberFluidContextMenu(
  actions: () -> List<FluidContextAction>,
  tint: Color? = null,
  previewShape: Shape? = null,
  preview: (@Composable () -> Unit)? = null,
): FluidContextMenuController

@Composable
fun Modifier.fluidContextMenuAnchor(controller: FluidContextMenuController): Modifier

class FluidContextMenuController { val isOpen: Boolean; fun open(): Boolean; fun dismiss() }
```

**Uso.** Una tessera con la foto che, tenuta premuta, diventa la sua anteprima più grande (da `MenusDemo`, «Anteprima e forma»):

```kotlin
val photo = remember { generateMeasurePhoto() }       // nella tua app, la tua immagine
Box(
  modifier = Modifier
    .size(96.dp)
    .clip(ContinuousCornerShape(FluidRadius.Card))
    .fluidContextMenu(
      actions = {
        listOf(
          FluidContextAction(label = "Condividi", icon = FluidIcons.Share) {},
          FluidContextAction(label = "Mi piace", icon = FluidIcons.Filled.Favorite) {},
          FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) {},
        )
      },
      previewShape = ContinuousCornerShape(FluidRadius.Group),
      preview = {
        Box(modifier = Modifier.width(280.dp).height(180.dp)) {
          Image(bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
      },
    ),
) {
  Image(bitmap = photo, contentDescription = "Foto", contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
}
```

Un tondo che si solleva tondo (la sagoma del vetro e il ritaglio dell'immagine sono la stessa forma), e una card che non ha click
suoi, col sollevamento di serie:

```kotlin
Box(
  modifier = Modifier
    .size(64.dp)
    .clip(CircleShape)
    .fluidContextMenu(actions = { voci }, previewShape = CircleShape),
)
Box(
  modifier = Modifier
    .fillMaxWidth()
    .height(96.dp)
    .clip(ContinuousCornerShape(FluidRadius.Card))
    .fluidContextMenu(actions = { voci }),
)
```

Per un elemento con click propri (è quello che fa `FluidListRow` dietro `contextActions`):

```kotlin
val menu = rememberFluidContextMenu(actions = { voci })
Box(
  modifier = Modifier
    .fluidContextMenuAnchor(menu)
    .combinedClickable(onClick = { apri() }, onLongClick = { menu.open() }),
)
```

**Comportamento.**

- **L'ancora registra solo mentre un dito è giù.** `fluidContextMenuAnchor` osserva il tocco nel passaggio *Initial* senza consumare
  niente, e dipinge l'elemento in un `GraphicsLayer` fra il tocco e il rilascio: a riposo una lista di ancore non costa nulla. Mentre
  il dito è giù l'elemento si gonfia del 2% e accende la luce dei controlli di vetro (con il movimento ridotto non si gonfia).
- **Il sollevamento è continuo.** Al click lungo la card parte dalla scala che l'elemento ha **sotto il dito** (`fluidLiftStartFraction`:
  la riga gonfiata e la card sono lo stesso rettangolo) e cresce al 3% **attorno al centro** (`fluidLiftedFrame`) con la molla del
  menu, oltrepasso compreso. La partenza resta ferma per tutta l'andata e torna viva al ritorno: a dito alzato la card finisce
  esattamente sull'elemento a riposo, che ricompare nella pagina nello stesso fotogramma. L'immagine **segue la sagoma del vetro**
  (`fluidLiftContentMap`: centrata, una scala sola, il minore dei due rapporti dei lati). Aperto, l'elemento non si disegna più nel suo
  posto (lo spazio resta riservato) e torna alla pagina **solo a uscita finita**.
- **Lo sfondo** (scelta di Alessio: «scurito con una sfocatura leggera») ha un livello suo, più scuro di quello dei menu dal tasto. Tre
  candidati: **A** 18% / 30% di nero (chiaro / scuro), sfocatura 3,75 dp; **B** 24% / 38%, 5 dp; **C** 32% / 48%, 6,5 dp. Di serie
  **C**, scelto sulle catture S10a. Nessuno passa i 7 dp di sfocatura: oltre ~8 dp uno scrim a tutto schermo
  trasmette la media della pagina. La pellicola della finestra del menu porta lo stesso nero, perché campiona la pagina cruda: un
  livello cambiato in un posto solo farebbe un menu più chiaro o più scuro della pagina attorno. iOS 26 scurisce soltanto: la
  sfocatura è la deroga voluta. Nelle build di debug e di misura il livello si cambia senza ricompilare, con la manopola
  `ctxscrim=a|b|c` della diagnostica (`--es gallery.tune ctxscrim=a`, o la riga «Sfondo del contestuale» della galleria): vale dal
  contestuale successivo.
- **L'anteprima personalizzata** (`preview`) è composta dall'host alla sua taglia (al più larga quanto lo schermo meno i margini e alta
  il 55% dello schermo), centrata sull'elemento e dentro i margini. La card va dall'elemento all'anteprima e i due contenuti si
  scambiano **attraverso la sfocatura** (l'elemento se ne va fra 0,05 e 0,45 del sollevamento, l'anteprima arriva fra 0,15 e 0,6,
  sfocati fino a 8 dp per la parte che manca, coi bordi tenuti: `Clamp`, non `Decal`). Il vetro della card, con un'anteprima
  personalizzata, è la piattaforma dell'anteprima: arriva e se ne va con lei (al cubo della sua opacità), così nello scambio, quando i
  contenuti sono in parte trasparenti, dietro non traspare la pellicola chiara né il filo di luce del vetro — il bordo bianco attorno
  alla foto che rientrava (S10a, ritrovato da S4 sul tablet). **Il contorno dello scambio è uno solo, quello del corpo**: le due
  immagini (l'elemento che se ne va e l'anteprima che arriva) lo riempiono con una scala sola, la maggiore dei due lati
  (`fluidLiftContentMap(fill = true)`), e un ritaglio comune sulla sagoma di adesso taglia quello che sporge; il vetro sta sotto di
  loro sulla stessa sagoma (`fluidLiftMaterialFrame`). Fino a S4c ogni immagine aveva la sua cornice, scalata per stare dentro: con
  proporzioni diverse (una tessera quadrata, una foto larga) nello scambio si vedevano due contorni, il filo chiaro attorno alla
  tessera al ritorno e, nel tema scuro, i suoi bordi colorati come secondo profilo (ripresa 22). Nel gonfiore dell'arrivo l'anteprima
  perde qualche punto ai fianchi invece di lasciare il vetro scoperto. Una riga senza anteprima non cambia: è lei stessa il vetro, e
  la sua immagine sta dentro la card. La taglia si misura al primo fotogramma, in cui card e goccia sono ancora spente.
- **La forma** (`previewShape`) è la sagoma del vetro e il ritaglio dell'immagine insieme. Una forma ad angoli (`RoundedCornerShape`,
  `CircleShape`, `ContinuousCornerShape`) dà i suoi raggi; un rettangolo, angoli vivi; una forma libera ritaglia l'immagine com'è, ma il
  vetro resta il rettangolo arrotondato dei pannelli (il campo di Fluid-physics è fatto di lastre). Senza forma: il raggio dei
  pannelli (22 dp).
- **Il menu nasce da una goccia** di 28 dp appesa al bordo della card che guarda il pannello, sotto il dito (o al centro), che entra
  nella card di 4 dp; il punto d'origine è fermato all'apertura. Sotto il dito anche fuori dalla colonna del pannello (dentro la card:
  fino a S4b era tenuta sopra il pannello, e col dito sul bordo della riga nasceva spostata): il corpo va verso il pannello
  inclinandosi, con la coda verso il dito. Il pannello sta **accanto, mai sopra** (`BesideAnchor`): sotto la card
  se ci sta o se sotto c'è più spazio, altrimenti sopra, a 12 dp, centrato e dentro i margini.
- **La nascita è quella di Apple sulla foto** (WWDC25 219 a 7:15, il tasto graffetta che diventa menu: la clip che Alessio ha scelto
  come modello, letta fotogramma per fotogramma), con il campo Liquid. Un orologio in millisecondi suo (`FluidContextBirth`) guida
  la sagoma con i profili misurati (`FluidContextBirthProfile`, interpolati senza rimbalzi inventati): la goccia esce dalla card in 50
  ms; a 67 ms è una goccia piccola, a 100 ms un fagiolo con le voci minuscole dentro; poi cresce verso il lato lontano **staccandosi
  dalla card** (il bordo vicino si allontana fino al 15% dell'altezza a ~234 ms e poi torna), è larga quanto il pannello a ~334 ms,
  supera una volta sola del 3% (prima il bordo lontano, a 367–467 ms, poi la larghezza, a 400–534 ms) ed è ferma a ~770 ms. Gli angoli
  restano quelli della goccia fino a ~200 ms e arrivano al raggio del pannello a ~367 ms (quelli verso la card più tardi: in viaggio è
  un uovo con la coda verso la card; mai oltre il 78% di mezzo lato, perché il ritaglio delle voci segua la goccia). Ogni lato va dal bordo del seme a quello del pannello: un seme sul bordo cresce dalla parte
  opposta, come la graffetta. La sagoma chiesta alla goccia è quella di Apple **qualche ms dopo** (l'anticipo misurato sul banco,
  18–51 ms), perché il corpo liquido insegue in ritardo: è il corpo che si vede a stare su Apple. **Il contorno non è un rettangolo
  che cambia misura**: la goccia insegue un contorno deformato (`FluidDropWarp`, dato a `driveExternally(travelWarp = …)`) lungo
  l'asse dal seme sotto il dito al lato lontano del pannello, e finché è piccola ha le **proporzioni della goccia di Apple** (tonda a
  67 ms, alta a 100–133 ms, larga 0,64 della sua altezza a 100 ms) e non quelle del pannello, che per un menu largo darebbero una
  pillola; fra 133 e 300 ms si allarga fino alla frazione del pannello. A 67 ms è una goccia grassa sulla card e più stretta in
  avanti, tutta lente (limpida, ingrandisce ciò che ha sotto, velo diradato); a 100 ms un fagiolo; a 133–200 ms un uovo con la testa lontana e la coda verso la
  card (un dito sul bordo della riga inclina l'asse, e l'uovo si piega verso il dito); a 234–300 ms un corpo a botte; da 400 ms
  nessuna deformazione. La stessa deformazione vale per la molla della goccia, l'anello disegnato, il ritaglio delle voci (che
  lascia fuori coda e punta) e la lente; la lente della goccia torna al vetro del profilo a ~200 ms. Le voci entrano nella goccia
  fra 67 e 117 ms (prima è solo lente), alla sua scala, piegate dalla lente e sfocate; nitide a 334 ms, con una sfocatura che cala
  dritta nel tempo. La card sollevata **non** rallenta col menu: ha la sua molla, quella di prima. Senza campo (o col movimento ridotto) il
  menu fa il viaggio dei menu di sempre.
- **Il congedo** è il ritorno del menu dal tasto (sul contorno, 233 ms: il corpo che crolla nella goccia e la goccia che rientra
  nella card) con l'inchiostro di Apple (B1 a, scelta di Alessio del 2026-11-19): le voci si sfocano subito ma restano piene dentro
  il vetro fino a ~67 ms e se ne vanno entro ~167 ms (`fluidContextExitInkSpec`), senza mai un vetro vuoto che si stringe. I menu
  dal tasto e dal gruppo tengono i ~90 ms. Un congedo a nascita in corso parte dalla sagoma che c'era, senza salti.
  Al ritorno il materiale dell'anteprima cede il bordo all'elemento registrato durante lo scambio dei contenuti.
- **Il colore** di chi ha alzato il menu (`tint`, che `FluidVividCard` passa da sola) va sulla card sollevata, **non** sul pannello: il
  menu è chrome, e la chrome non prende il colore di ciò che l'ha chiamata.
- `open()` ritorna `false` senza host, con un menu già aperto o con zero voci: chi chiama suona `Open` **solo se ritorna vero**
  (`FluidListRow` altrimenti ripiega sul suo `onLongClick`). L'ancora usa `boundsInRoot` (ritagliati dagli antenati): una riga mezza
  fuori dal bordo di una lista alza la parte visibile. Il relè del dito consuma già nell'Initial: la `LazyColumn` attorno resta ferma
  mentre il dito scorre sul menu.

**Trappole.**

- *Il menu non parte mai da una riga cliccabile*: hai usato `Modifier.fluidContextMenu` su un elemento che consuma i click.
- *Non si apre e non vibra*: manca `FluidGlassModalHost` (`rememberFluidContextMenu` prende `LocalFluidGlassModalHostState`).
- *L'ancora esce dalla composizione a dito giù* (una riga di `LazyColumn` rimossa): il gesto si annulla, il menu **resta aperto** e
  nessuno sceglie.
- *Un `GraphicsLayer` rilasciato*: il pannello lo ridisegna dentro un `runCatching`; se la riga è sparita il pannello si apre comunque,
  senza immagine e con le sue azioni.

## `FluidGlassMenuButton` (deprecato)

Il tasto che fa comparire il pannello *sopra* il tasto (due superfici). Passa comunque dal pannello, dal viaggio e dal dito dei menu
nuovi, ma ha un'apertura sua. `shape` e `contentDescription` non hanno corrispondente nel nuovo. Migrazione: alla radice, accanto a
`FluidGlassModalHost`, `rememberFluidMorphMenuState()` + `FluidMorphMenuHost(state, backdrop)` sopra la tab bar; dove c'era
`FluidGlassMenuButton(actions, onClick, text, icon)`, ora `FluidMorphMenuButton(state, actions, onClick, text, icon, backdrop)`. Le
azioni della barra con `FluidBarAction(actions)` **non passano di qui** e non cambiano.

## Come funziona dentro

### Il viaggio (`FluidMenuJourney`)

Un progresso geometrico per i due versi (0 = l'ancora, 1 = il pannello, può superare 1) che guida la finestra di Fluid-physics con
`driveExternally`. Col campo del vetro i menu dal tasto e dal gruppo nascono come Apple (`prepare(appleBirth = true)`, che
`FluidMenuLayer` e `FluidMorphMenuHost` chiedono per i menu liquidi con un'ancora): un orologio in millisecondi
(`FluidMenuJourney.birthClock`) guida la sagoma (`fluidButtonBirthSlab`, `fluidGroupBirthSlab`) e il suo contorno (nel gruppo),
e il progresso del viaggio corre nello stesso fotogramma sul bordo lontano di Apple (`fluidMenuBirthProgress`): è il numero che
leggono gli altri pezzi (materiale, lente delle voci, punto di luce, verso della chiusura). Senza la nascita (le tracce storiche) il
menu dal tasto segue `fluidMenuButtonTravel` e quello dal gruppo `fluidMenuJourneySeed`. Il contestuale Liquid ha una nascita sua
(`FluidContextBirth`, sotto). Il viaggio senza campo interpola fra seme e pannello.

| la nascita dal tasto e dal gruppo | valore |
|---|---|
| modelli | il tasto: B1 (a), f9148 → f9160, contorni letti sul video HD (±6 px, la posa sui bordi di f9156–f9170); il gruppo: B1 (b), la graffetta, i profili del contestuale |
| orologio | lineare, 0 → 400,4 ms dal tasto (`FluidButtonBirthProfile.EndMillis`), 0 → 867,5 ms dal gruppo; 1 esatto alla fine |
| tasto: profili | bordo vicino, lontano, larghezza (`Near`, `Far`, `Width`); dal nostro tasto ad Apple in 67 ms (`BlendMillis`); angoli dal 31% al 12% del lato corto (`Corner`), al raggio del pannello fra 200 e 334 ms |
| tasto: la goccia | più tesa: l'ancora arriva a 300 dp invece di 110 (`DropReachDp`, `FluidPhysicsState.dropAnchorReachDp`), perché B1 (a) cresce del 22% del menu in un fotogramma e con la goccia di serie l'angolo lontano restava indietro di 60–70 ms; anticipo del lontano 11–30 ms, della larghezza 12–30 ms, del vicino 12–15 ms (`FarLead`, `WidthLead`, `NearLead`), tarato con la goccia che campiona a 33,4 ms (`FluidMenuDropOnTimeMillis`): il ritardo in più si sconta (`leadAge`); superamento chiesto a metà (`OvershootShare`) |
| gruppo: raccolta | la capsula nella goccia sul tasto premuto in 67 ms (`FluidGroupGatherMillis`, `fluidGroupBirthDrop`), poi `fluidContextBirthSlab` col seme lì |
| gruppo: contorno | `fluidGroupBirthWarp`: quello del contestuale con la coda sul tasto; mentre la capsula si raccoglie solo la lente |
| facce | via col tempo della nascita (`fluidMenuBirthFaceProgress`, 83 ms): la faccia del tasto a ~29 ms, le icone del gruppo a ~54 ms; al ritorno le icone del gruppo tornano nell'ultimo 36% del progresso (`fluidMenuGroupFaceReturn`), sopra il vetro |
| voci in entrata | il tasto nei primi 33 ms, il gruppo da 67 a 117 ms (`fluidMenuBirthInk`) |
| messa a fuoco | il tasto lineare in 270 ms; il gruppo in 334 ms con l'andamento del contestuale |
| congedo | sempre sul contorno, con la goccia spenta: dal pannello fermo `fluidMenuReturnSlab` in 233 ms; a metà nascita dalla sagoma disegnata al congedo con la molla 900; inchiostro di B1 a (`fluidContextExitInkSpec`) |
| coppia lastra–contorno | la goccia disegna lastra **e** contorno del passo in cui ha integrato (`useSampledDropFrame`, S4) |

| cosa | valore |
|---|---|
| andata senza la nascita | molla `FluidMotion.dropOpen()`: smorzamento 0,72, rigidezza 500 |
| ritorno col campo da fermo | `fluidMenuReturnSlab`: bordo lontano, bolla sulla sorgente (nei menu dal tasto e dal gruppo la sorgente gonfiata, `wrapSource`), posa in 233 ms |
| ritorno senza campo | `FluidMotion.dropClose()`, smorzamento 0,72, rigidezza 158, ≈0,5 s |
| raccolta del gruppo | al 60% della larghezza (`MenuGroupGatherWidth`) |
| voci nella lente | alfa iniziale 0,28, sfocatura fino a 5 dp, messa a fuoco in 270 ms, curva di sfocatura d'uscita in 100 ms, ingrandimento dal tasto fino a 1,42×; inchiostro d'uscita di Apple con la nascita (pieno fino a 67 ms, via entro 167), in 90 ms senza |
| voci senza lente | fra 0,4 e 0,75 del viaggio (`LiquidContentOpenFrom/To`), all'uscita fra 0,94 e 0,62; scala `0,86 + 0,14·opacità` |
| la faccia del tasto | piena alla partenza, spenta entro 0,35 (`MenuFaceTo`) |
| il materiale | dal tasto al pannello con uno smoothstep fino al 60%: niente scalino di luminosità quando il viaggio si posa |
| oltrepasso Liquid | accento al bordo verticale lontano, larghezza limitata a 6 dp dai fianchi |
| scrim del layer | entra in 110 ms, esce in 280 ms (`MenuFadeOutMillis`) |
| movimento ridotto | `snapTo`: arriva senza viaggiare |

**Il ritaglio delle voci dura quanto la goccia.** `travelClipIn` fornisce il rettangolo interno usato per posare le voci.
Nel menu Liquid dal tasto o dal gruppo il campo dell’anello ritaglia l’inchiostro anche sui fianchi concavi. La molla
d'apertura supera l'1 verso 150 ms e ci resta fino a ≈355 ms mentre la goccia, che arriva dopo la molla, è ancora più stretta del
pannello: un ritaglio che si spegnesse all'1 lascerebbe testo, glifi e capi dei separatori sopra la pagina. Vale per tutti quelli che
ritagliano sulla goccia: il contestuale, il menu dal tasto, il menu che si trasforma, il pop-up ancorato.

### Il pannello (`FluidGlassMenuPanel`)

Uno per tutti, con le voci, i separatori e **una** goccia. Il pannello non è il vetro: il vetro è la superficie di Fluid-physics che
l'host fa viaggiare; qui c'è quello che sta sopra, in due fratelli, la goccia (che campiona la pagina con dentro il materiale del
pannello: vetro su vetro) e la colonna delle voci.

| misura | valore |
|---|---|
| altezza di una riga | `max(48 dp, lineHeight + 24 dp)`: segue il font; con il sottotitolo 60 dp |
| tessere della fila | `Small` alta quanto una riga, `Medium` 68 dp |
| padding del pannello / banda di sezione | 6 dp / 8 dp |
| larghezza | 252 dp, o quanto serve alla voce più lunga (etichetta + icona 22 dp + spunta 22 dp + 12 dp fra i segni + 16 dp per lato) fino a **340 dp**, mai oltre lo schermo meno 16 dp per lato; oltre, i puntini |
| icona e spunta di una riga | `FluidIcon` da 22 dp, riga da 48 dp e testo da 17 sp |
| distanza dall'ancora / margine | 12 dp / 16 dp |
| raggio | `FluidRadius.Group` (22 dp) |
| separatori | linea da 0,5 dp al 12% di `onSurface`, rientrata di 16 dp; fra due sezioni una **banda** (5% sul chiaro, 8% sullo scuro) |
| materiale | `GlassDefaults.panelOptics(compact = true)` + `panelTint` |

I separatori sono **disegnati, non scatole**: una scatola da mezzo dp aggiungerebbe altezza che l'aritmetica delle celle non conta, e
il dito sceglierebbe la riga sopra quella che vede. Le **celle** (la geometria del hit-test) sono solo le voci: padding, banda e
separatori non sono mai sotto il dito; sono semiaperte (a un confine vince la riga sotto).

**La goccia della selezione** è la ricetta di `glassDrop` coi numeri del pomello dello slider: bianca a riposo, lente 10/14 dp sotto il
dito, capsula rientrata 4×2 dp dalla cella, crescita 4%. **Nasce sotto il dito**, segue la voce attraversata (fra una tessera e una
riga cambia misura scivolando), e al rilascio si posa bianca per un attimo (al più 140 ms) prima di andarsene: è la conferma della
scelta. La voce sotto la goccia si fa avanti del 5% dal bordo d'attacco e i due separatori accanto si spengono. Ha tutte le sue
trasformazioni nel proprio `layerBlock`, mai dentro il layer delle voci: un vetro scalato da un antenato rifrange quello che non c'è
più. Senza fondale (API sotto la 31) non c'è goccia: restano la scala della voce e i separatori che si spengono.

### La selezione col dito (`FluidMenuSelection` e il relè)

Interni, ma sono la regola dei menu: un proprietario del dito alla volta.

| chi ha il dito | come | cosa succede al rilascio |
|---|---|---|
| **HandOff**: il dito che ha aperto | l'ancora glielo passa (`FluidMenuHandOff.attach`); il pannello è comparso a dito giù e non ha mai visto quel down | sceglie **solo se «armato»**: il dito si è mosso oltre lo slop (`ViewConfiguration.touchSlop`) dal punto in cui il menu l'ha preso, e **resta armato** anche se torna dov'era |
| **Panel**: un dito sceso sul pannello | il pannello consuma il down nell'Initial | sceglie se si solleva su una voce abilitata |
| nessuno | | il menu resta com'è |

**Il dito fermo mentre il menu nasce** (S4f, NO 4). Le voci stanno col corpo che cresce e scivolano sotto un dito fermo: la voce
accesa resta quella che il dito ha acceso finché non si sposta lui oltre lo slop (`fluidMenuHoverHolds`), e al rilascio si sceglie
lei. Se sotto il dito non si vede nessuna voce (il menu è ancora piccolo), si sceglie la voce della posa che sta arrivando lì
(`fluidMenuReleaseCell`). A menu fermo non cambia niente. Fino a S4f la voce restava accesa e il rilascio cercava la cella con la mappa
nuova: il menu restava aperto (sul Tab S9 la riga in scuro, f0214–f0224) o sceglieva la voce accanto.

Un rilascio **fuori dalle voci, nel padding, su un separatore o su una voce spenta** lascia il menu aperto. Un rilascio veloce, lontano
dall'ultimo movimento, conta come mosso. Un puntatore che sparisce dall'evento senza un sollevamento è un annullo. Un secondo
proprietario non scavalca né annulla il primo. Se il dito si muove prima che il pannello abbia scritto la sua geometria, l'ultimo
`hover` resta in attesa e viene rigiocato quando la geometria arriva. Il dito che tiene su una voce madre non apre il sottomenu senza
staccare: si apre al rilascio o al tocco.

**Il relè** (`Modifier.fluidMenuPointerRelay`, in testa all'ancora) esiste perché Compose sceglie i nodi di un puntatore *al down*: un
pannello che compare a dito giù non vede mai quel dito, il percorso dell'ancora sì. Legge **ignorando il consumato**, consuma i cambi di
quel dito **già nel passaggio Initial** (o il tasto nascosto continuerebbe a seguire il dito sul menu e a suonare la sua grana) e consuma
anche il sollevamento che chiude l'hand-off, o `clickable` lo leggerebbe come un click sull'ancora.

### L'aptica

Un momento, un colpo: `Open` all'apertura (**uno solo, e solo se il menu si apre**: lo suona il gesto che apre, mai il layer), `Tick`
per ogni voce **abilitata** attraversata, `Tap` alla scelta (anche quella che apre un sottomenu o lascia il menu aperto), **niente al
congedo**. Sui tasti resta il `PressDown` al contatto: è del tasto, non del menu.

### Accessibilità

Ogni voce è un `clickable(role = Button)` per TalkBack e tastiera (dal dito il click non scatta mai: il pannello consuma il down prima,
e la scelta la fa la selezione, una volta sola); la voce `selected` dice «selezionato», la voce spenta lo dice, una tessera della fila
dice la sua etichetta. Il pannello è un `isTraversalGroup` con `paneTitle` = il tuo, o `LocalFluidLabels.current.actions` («Azioni»).
Il **back** dentro un sottomenu torna al menu di prima, al primo livello congeda (col gesto predittivo). Durante il gesto **il vetro
segue il dito** verso la sua sorgente (`FluidMenuJourney.backPull`, `fluidMenuBackPullSlab`): la sagoma del primo tratto del congedo,
proporzionale al gesto addolcito, col bordo lontano che cede di `FluidMenuBackPullFarEdge` (0,13) dell'altezza ma almeno di
`FluidMenuBackPullMinFarDp` (24 dp) e al più di `FluidMenuBackPullMaxFar` (0,3), il bordo vicino fermo sulla sorgente e i fianchi
che stringono verso di lei; al rilascio il congedo parte dalla sagoma tirata (`backHome`), a gesto annullato il vetro torna al
pannello con la molla del menu. Fino a S4f si muoveva solo lo scrim (sul Tab S9 «Altro» fermo da f0084 a f0156). Fino a S4g-ter
il tiro era il ritorno stesso fino a 0,32 del cammino, che parte col quadrato del tempo e cede in proporzione all'altezza: al
gesto del Tab S9 (~0,4) il bordo lontano saliva del 5–6%, e sui pannelli corti del tondo e della foto di 10–16 px, a vista fermi. Con il movimento ridotto viaggio
e goccia sono `snapTo` e la riga tenuta premuta non si gonfia.

### Costi

A riposo niente: l'host del tasto che si trasforma è un nodo vuoto che tiene le misure, quello dei modali non compone voci chiuse,
l'ancora del contestuale non registra nulla senza un dito. In viaggio: una superficie di Fluid-physics (contorno di 48 punti integrati a
1/240 s) più la goccia; col campo, sul solo layer delle voci, una sfocatura e la lente (una lettura della texture),
finché il menu non è a fuoco. Lo scrim è la superficie più grande del sistema: la sua forza è un alpha sul pannello finito, mai
l'`intensity` del materiale (vedi [menu-modali.md](menu-modali.md)).

## Token, costanti, Defaults

Quasi tutto è interno: **non c'è un `object …Defaults`** per i menu. Le costanti che un'app può leggere sono i token di `FluidMotion`
(chi le legge si lega a numeri che cambiano).

| token | valore |
|---|---|
| `FluidMotion.DropOpenStiffness` / `DropCloseStiffness` / `DropJourneyDamping` | 500 / 158 / 0,72 |
| `FluidMotion.LiquidContentOpenFrom/To`, `LiquidContentCloseFrom/To` | 0,4–0,75 / 0,94–0,62 |
| `FluidMotion.LiquidContentMinScale` | 0,86 |
| `FluidMotion.DropInflationGain`, `DropPeakOvershoot` | 0,55, 0,0384 |
| `FluidMotion.MenuFaceTo`, `MenuFadeOutMillis` | 0,35, 280 ms |
| `FluidMotion.DropPoints`, `DropStiffnessNear/Far`, `DropDamping`, `DropEdgeTensionDefault` | 48, 1500 / 320, 0,72, 0,2 (la goccia della sagoma: di Fluid-physics) |
| `FluidMotion.LiftedSwellX` / `LiftedSwellY` | 0,25 / 0,6 (il gonfiore della card sollevata all'oltrepasso) |
| `FluidMotion.MenuOpenDamping`, `MenuOpenStiffness`, `menuOpen()`, `menuClose()` | 0,62, risposta 0,36 s: **non** la molla dei menu (sono i token di serie di `FluidPhysicsJourney`) |
| `FluidMotion.MenuContentOpenFrom/To`, `MenuContentCloseFrom/To` | `@Deprecated`: usa i `LiquidContent…` |

I numeri del viaggio in tre tempi (raccolta, risalita, espansione, lente, punto di luce, ritorno rapido) sono interni a `FluidMotion`
(«I menu Liquid»): si tarano guardando le tavole contro Apple, non dalle app.

### La nascita del contestuale (`FluidContextBirth`, in `FluidContextMenu.kt`)

| cosa | valore |
|---|---|
| modello | WWDC25 219 a 7:15 (f13046 → f13072), il menu sulla foto: contorni letti a mano sul 4K, ±0,4% dell'altezza |
| orologio | un `Animatable` in millisecondi, lineare, 0 → 867,5 ms (`FluidContextBirthProfile.EndMillis`); il progresso del viaggio corre nello stesso fotogramma sul bordo lontano di Apple (`fluidContextBirthProgress`), 1 esatto alla fine |
| profili | bordo vicino, bordo lontano, larghezza in frazioni del pannello (`Near`, `Far`, `Width`), cubica monotona fra i punti (`FluidMeasuredCurve`) |
| dal seme ad Apple | nei primi 100 ms (`BlendMillis`); la goccia si vede tutta a 50 ms (`fluidContextBirthPresence`) |
| anticipo per la goccia | il bordo lontano chiede Apple 18–51 ms dopo, la larghezza 10–41 ms, il vicino 4–12 ms (`FarLead`, `WidthLead`, `NearLead`); oltrepasso chiesto all'85% (`OvershootShare`): il resto lo aggiunge la goccia |
| angoli | della goccia (al più il 78% di mezzo lato, `RoundnessInTravel`) fino a 200 ms, al raggio del pannello a 367 ms i lontani e a 434 ms i vicini |
| contorno | `FluidDropWarp` da `fluidContextBirthWarp`: punta 0,35 a 67 ms, vita 0,15 a 100 ms, coda 0,50–0,55 a 133–167 ms (su 3/4–2/3 dell'asse) che si accorcia fino a 300 ms, pancia fino a 0,06 a 267 ms; niente da 400 ms (`TailTaper`, `TailReach`, `HeadTaper`, `Waist`, `Belly`, `WarpEndMillis`) |
| proporzioni | fino a 133 ms quelle della goccia di Apple (larghezza / altezza 1,10 a 67 ms, 0,64 a 100 ms, 0,68 a 133 ms, `Aspect`), poi verso la frazione del pannello fino a 300 ms (`AspectFrom`, `AspectTo`) |
| lente della goccia | soglia della lente in moto 1 a 33–100 ms, ingrandimento fino a 0,25 a 67 ms, limpidezza fino a 0,7 (velo diradato quanto la limpidezza; la pellicola resta, perché nel contestuale porta anche il nero dello sfondo); al profilo (0,035 e 0,25 in viaggio) a ~200 ms (`Lens`, `Magnify`, `Clarity`, `physicsDropLensRecipe`) |
| voci in entrata | da 67 a 117 ms (`fluidContextBirthInk`) |
| voci | messa a fuoco in 334 ms (`FluidContextFocusMillis`) con l'andamento 1 − √(1 − f): la sfocatura cala dritta nel tempo |
| card sollevata | la sua molla, `dropOpen` all'andata e le molle del viaggio al ritorno (le stesse di prima) |
| congedo | `FluidMenuJourney.run` senza la sua lente, **sempre sul contorno** con la goccia spenta (`retarget(0f, onContour = true)`): in 233 ms da fermo, con la molla 900 a metà nascita. Fino a S4c a metà nascita il ritorno era liquido, e la goccia oscillava mentre si stringeva: la sagoma ricresceva di un decimo del pannello (sul Tab S9 la goccia piccola e poi il pannello all'80% con le voci sfocate). Parte dalla sagoma **disegnata** al congedo, letta dall'host prima di cambiare verso (`FluidContextBirth.noteDrawnAtDismiss`), già al primo fotogramma in chiusura (`FluidContextBirth.freeze`; fino a S4b un fotogramma dopo, dal pannello pieno; fino a S4c dalla sagoma calcolata della nascita, più grande della goccia che la seguiva); fuoco in 100 ms come i menu, inchiostro di B1 a (pieno fino a 67 ms, via entro 167 ms, `fluidContextExitInkSpec`) |

## Cosa è verificato

Puri, in `engine-ui` (JVM):

- `FluidMenuSelectionTest`: il dito che decide, lo slop, l'armo che resta, la voce spenta, il rilascio che lascia aperto, l'hover che
  arriva prima della geometria, un proprietario alla volta.
- `FluidMenuGeometryTest`: il hit-test (padding e separatore non sono celle, i confini vanno alla riga sotto), il centro della goccia.
- `FluidMenuRelayTest`: il consumo già nell'Initial, il click lungo che scatta durante un evento, il sollevamento consumato, il puntatore
  che sparisce = annullo.
- `FluidMenuPanelTest`, `FluidMenuPanelWidthTest`: la riga che segue il font, le sezioni, il pannello sopra/sotto/sull'ancora/accanto,
  il seme, la larghezza (252 → 340, mai oltre lo schermo) con l'icona da 22 dp.
- `FluidMenuJourneyTest` (orologio finto): l'apertura oltrepassa e si posa, la partenza dal tasto premuto, il gonfiore, l'inversione a
  100 ms, riaprire mentre si chiude; i tre tempi (raccolta, risalita verso il centro, espansione), la messa a fuoco delle voci. Con
  la nascita di Apple: il tasto sull'orologio di B1 (a) (quasi pieno a ~96 ms, il superamento, 1 esatto alla fine) e il congedo con
  l'inchiostro di Apple; il gruppo dalla capsula alla goccia sul tasto, le icone via entro 55 ms, l'uovo con la coda sul tasto; il
  congedo a metà nascita dalla sagoma che c'era; il movimento ridotto; senza la nascita il viaggio di prima.
- `FluidMenuBirthTest`: le funzioni pure della nascita dal tasto e dal gruppo: esatta sul tasto e sul pannello, la sagoma di Apple
  senza anticipo entro lo 0,2%, il lato sul margine fermo, gli angoli della goccia, il progresso e la sua inversa, le voci in
  entrata, la capsula che si raccoglie (63% a 33 ms), la coda sul tasto, l'uovo che si spegne al congedo, le facce; il dito
  riportato attraverso la mappa delle voci sceglie la riga che si vede, non quella che a riposo sta sotto il punto.
- `FluidMenuMotionTest`: le finestre di opacità e il loro verso, la mappa del contenuto, il gonfiore, i token.
- `FluidMenuStructureTest`: un menu di sempre ha le stesse celle al bit; `Priority` capovolge solo verso l'alto, e `Automatic` lo fa per i menu dei tasti; la
  fila `Small`/`Medium`; la goccia che cambia misura fra tessera e riga; la riga col sottotitolo; il sottomenu e il suo pannello dallo
  stesso bordo; le curve del cambio di livello; `FluidMenuActions` come lista e il contenuto vivo.
- `GlassCompatSignaturesTest`: la `FluidContextAction` a sette argomenti per posizione e per nome, le forme nuove con la lambda in coda,
  `copy`, `component7`/`component10`, `equals`.
- `FluidContextLiftTest`: al click lungo la card è la riga gonfiata, alla fine del ritorno è la riga a riposo, l'oltrepasso,
  l'anteprima centrata e dentro i margini, il tetto della sua taglia, i raggi di ogni forma (destra-sinistra compreso), lo scambio (e
  il vetro che dietro un'anteprima personalizzata non traspare: sotto l'11% nello scambio, sotto il 5% con la tessera davanti), i
  tre livelli dello sfondo (più scuri dei menu, in ordine, sotto il tetto della sfocatura), la manopola `ctxscrim`.
- `DropPlanTest`: con la molla vera del menu, nei fotogrammi oltre l'1 a goccia non posata il ritaglio c'è e sta dentro la goccia.
- Le correzioni dopo S4f: `AltroNascitaPassoLungoTest` (la nascita vera di «Altro» sui fotogrammi del Tab S9, col passo lungo
  iniziale: prima e dopo l'orologio costante), `GruppoRitornoIconeTest` (le icone del gruppo al ritorno da fermo e nell'annullo:
  mai fuori dal vetro), `IndietroPredittivoMenuTest` (il vetro che segue il gesto, il congedo dalla sagoma tirata, il gesto
  annullato, il contestuale), `FluidMenuS4fRulesTest` (la voce accesa che tiene, la cella del rilascio, il tiro della lente).
- Le correzioni dopo S4g: `AltroGocciaInRitardoTest` (la nascita di «Altro» con la goccia che parte tardi, come sul Tab S9:
  fino al primo campione l'anticipo del banco, poi nessun salto oltre quello di Apple, il picco vicino al suo), `AnnulloAltroGlifoTest` (il glifo del tasto nel congedo anticipato: torna
  una volta sola e c'è sulla goccia consegnata), `GruppoRitornoIconeTest` (mai la pillola vuota più stretta delle icone),
  `FluidMenuEarlyPickS4fTest` (la scelta durante la crescita sul tondo e sulla foto, l'Indietro col dito che torna verso il bordo) e,
  al banco, `test_menu_lens_clip_input.py` (lo strato delle voci intero contro quello limitato al ritaglio, come lo dava Skia sul
  dispositivo).
- Le correzioni dopo S4g-bis: `VociDentroLaFinestraTest` (la nascita vera di «Altro» vicino al bordo destro del Tab S9: lo strato
  delle voci esce dalla finestra, spostato ci sta e copre ancora il vetro; in RTL esce meno), `GruppoRitornoIconeTest` (icone e voci
  mai insieme, da fermo e nell'annullo), `NascitaDopoLoStalloTest` (dopo un fotogramma da 93 ms la nascita riprende con la stessa
  curva) e, al banco, `test_menu_lens_window_input.py` (lo strato limitato alla finestra perde il ramo destro dell'icona; spostato e
  riportato dallo shader è come quello intero).
- Le correzioni S4g-ter: `VociDentroLaFinestraTest` (il limite della lente tiene tutte le voci e resta della misura del pannello
  nello spazio dello strato, in «Altro» e nel gruppo), `LenteVociLimiteTest` (PixelCopy: col limite gli stessi pixel che col limite
  allargato, a ogni scala da 0,04 a 1,35, e che senza limite da 0,72; il pannello delle voci vero, a metà nascita, disegna la
  lente nel limite, della misura del nodo).
- Le correzioni S4g-quater: `GruppoRitornoIconeTest` (con la barra un fotogramma indietro, come sul Tab S9, icone e voci mai
  insieme e la capsula vuota al più un fotogramma; nell'annullo le voci sfumano prima delle icone; l'opacità delle facce e quella
  delle voci non sono mai sopra zero alla stessa fase), `IndietroPredittivoMenuTest`
  (con le sorgenti e i pannelli misurati sul Tab S9, al gesto del tablet il tondo e la foto cedono come la riga, e seguono il dito
  da subito), `FluidMenuEarlyPickS4fTest` (l'Indietro vero dal dispatcher sul tondo e sulla foto: il vetro si accorcia col gesto).
- `FluidContextBirthTest` (anche su un orologio finto a 120 Hz): la nascita parte esatta sul seme e finisce esatta sul pannello; da
  100 ms la sagoma di Apple e' quella letta fotogramma per fotogramma (bordo vicino, lontano, larghezza entro lo 0,2%); il distacco
  dalla card (15% a ~234 ms) e il ritorno; un solo superamento per asse; il seme sul bordo che cresce dall'altra parte; il pannello
  sopra la card, specchiato; gli angoli; l'orologio (a meta' fra 134 e 167 ms, al 90% fra 234 e 300) e la ripresa a meta'; la
  sfocatura delle voci; il layer che apre con l'orologio suo e la card con la sua molla, e chiude col ritorno dei menu e l'inchiostro
  di B1 (a); un back a meta' che parte dalla sagoma che c'era; il contorno (goccia, fagiolo, uovo con la coda verso la card, botte,
  nessuna deformazione da 400 ms; un pannello sopra tiene la coda verso la card), la lente della goccia che torna al profilo entro
  234 ms senza scatti, e la deformazione che stringe soltanto verso l'asse e non esce mai dal rettangolo a riposo.

Nel `sample` (Robolectric, aptica registrata):

- `FluidMenuHandOffTest`: tieni-trascina-rilascia dà `Open` `Tick` `Tap` e nessuna grana; rilascio fermo, fuori dalle voci o su una voce
  spenta lascia aperto; una riga in `LazyColumn` resta ferma; `fluidContextMenu`; il back a metà apertura chiude in silenzio; l'azione
  della barra.
- `FluidMorphMenuBackOrderTest`: il back chiude prima il menu, poi la sottopagina di un `FluidPageHost` annidato, poi la pagina.
- `FluidMenuStructureBehaviourTest`: il sottomenu che si apre senza chiudere, il figlio che sceglie e chiude, l'intestazione e il back
  che tornano indietro, `keepsMenuOpen` con la spunta che si aggiorna, `Priority` su un tasto in basso, la fila `Small`.
- `FluidTransitionSourceTest`: la capsula del gruppo come sorgente, il gruppo che si consuma e ritorna, la scelta di una voce.
- `FluidMenuBirthPickTest`: il dito che tiene premuto e rilascia a metà nascita (~200 e ~300 ms) sopra una voce visibile sceglie
  quella voce, nel menu del gruppo e nel contestuale.
- `FluidMenuEarlyPickS4fTest` (S4f): il dito che arriva su «Condividi» durante la nascita e resta fermo la sceglie al rilascio, nel
  gruppo e nel contestuale, arrivando sulla voce che si vede, sul suo posto a riposo o con eventi ripetuti nello stesso punto; il
  velo e la nascita di «Altro» partono nello stesso fotogramma; l'Indietro predittivo vero (dal dispatcher dell'attività) tira il
  vetro col dito, chiude dalla sagoma tirata e, annullato, torna al pannello.
- `FluidContextMenuS4cTest`: i tre menu della pagina «Menù liquidi» montati come nella galleria (chrome, schermata, host che
  rifrange il fondale eletto). In RTL le voci di «Altro», del gruppo e del contestuale stanno dentro la sagoma che il menu
  disegna e sullo schermo, e il dito le sceglie. Dopo «Condividi», a menu fermo e durante la nascita (da 304 a 864 ms), il
  ritorno parte dalla sagoma che si vedeva, non ricresce e non cambia fondale. La sagoma la legge
  `FluidMenuLayerProbe`, una sonda solo per i test: fuori dai test è nulla e non compone niente.
- `FluidApiAdditionsTest`: `selected` per TalkBack, `opensOnTap` apre, una voce lunga si legge intera, il tasto prende lo stato dal
  fornitore. `FluidButtonDragTest`: il tocco sul tasto che si trasforma fa i tre colpi di un tasto e non apre.

Sul banco del movimento (`tools/glassbench/traces/`, scritte dal codice dei test `F5a*`):

- `f5a-5/`: le prove di chiusura della fase, accoppiate ad Apple sul `t0` di R4 (`F5a5MenuTraceTest`): `menu-apertura`,
  `menu-apertura-gruppo`, `menu-chiusura`, `menu-chiusura-gruppo`, `menu-interrotto` (il congedo a 133 ms), `contestuale-apre`.
- `f5a-2/scelta-*`: le scelte di Alessio sul viaggio (tasto, gruppo, chiusura, interruzione, punto di luce).
- `f5a-3/`: il sottomenu che si apre e torna, `Priority` e `Fixed` verso l'alto.
- `f5a-4/`: il contestuale coi tre livelli dello sfondo, chiaro e scuro, apertura e ritorno, con l'inchiostro misurato dentro il vetro
  (il viaggio senza campo: non cambia con la nascita nuova).
- `contestuale/` (`ContestualeTraceTest`): la nascita nuova dalla riga, chiaro e scuro (`apre-riga`, `apre-riga-scuro`, accoppiate al
  menu sulla foto di Apple, f13046), e il congedo con un tocco fuori (`chiude-tocco`, `chiude-tocco-scuro`, accoppiate al ritorno del
  menu dal tasto, f9187). Il test confronta la sagoma vera (l'anello della goccia) coi contorni di Apple fotogramma per fotogramma
  (entro il 5% sui bordi, il 4% sul bordo vicino), misura le voci dentro il vetro con la mappa della lente che si disegna, e tiene il
  costo: al più tre superfici, tre catture e una sola a tutto schermo per fotogramma. Le `f5a-corr/contestuale-*` seguono la stessa
  nascita.
- `tasto/` (`TastoTraceTest`): la nascita del menu dal tasto (`apre-tasto`, contro B1 a, f9148) e dal gruppo (`apre-gruppo`, e
  `apre-graffetta` col tasto a sinistra come la graffetta, contro B1 b, f13046), il congedo da fermo (`chiude-tasto`, `chiude-gruppo`,
  contro f9187) e a metà nascita (`interrotto-gruppo`), in chiaro e in scuro. Il test stampa la sagoma vera contro i contorni di
  Apple fotogramma per fotogramma (il tasto entro il 6% sui bordi, il 5% sul vicino) e tiene il costo: un solo elemento per
  fotogramma. Le `f5a-corr/menu-*` seguono la stessa nascita (il congedo a menu fermo ora a 900 ms).

**Non è provato** al PC: il vetro e la goccia sul vetro dentro i test Robolectric (gli host dei test non hanno fondale), il cambio di
proprietario fra tasto e finestra, il menu dal vero a 120 Hz, il costo della lente sul layer delle voci: sono del tablet.

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/…`) | cosa c'è dentro |
|---|---|
| `fluid/FluidMenu.kt` | `FluidMenuOpensOn`; la selezione (`FluidMenuSelection`, `FluidMenuHandOff`, il relè); la struttura: `FluidMenuOrder`, `FluidMenuElementSize`, `FluidMenuActions`, le celle (`fluidMenuEntries`, `fluidMenuSubmenuEntries`, `FluidMenuStructure`), il pannello del sottomenu, le curve del cambio di livello |
| `fluid/FluidMenuJourney.kt` | il viaggio (`FluidMenuJourney`, `FluidMenuAnchor`), i tre tempi (`fluidMenuButtonTravel`, `fluidMenuJourneySeed`), le voci nella lente (`fluidMenuLensMap`, `fluidMenuLensAlpha`, `fluidMenuLensBlurDp`), il punto di luce (`LocalFluidMenuPointOfLight`), le finestre di opacità, il posto del pannello |
| `fluid/FluidMenuLayer.kt` | il layer dell'host per i menu dal tasto e contestuale (`FluidMenuLayer`), i livelli del sottomenu (`FluidMenuLevels`), e l'avviso |
| `fluid/FluidGlassMenuPanel.kt` | il pannello, le voci (righe, tessere della fila, intestazione del sottomenu), i separatori, la goccia, l'area dei gesti |
| `glass/field/GlassContentLensShader.kt` | la lente delle voci |
| `fluid/FluidGlassModalPortal.kt` | `FluidContextAction`, `FluidGlassModalPresentation` |
| `fluid/FluidContextMenu.kt` | `FluidContextMenuController`, `rememberFluidContextMenu`, `fluidContextMenuAnchor`, `fluidContextMenu`; il sollevamento e la sua geometria |
| `fluid/FluidLiftedPreview.kt` | la card sollevata e l'anteprima personalizzata |
| `fluid/FluidGlassModalScrim.kt` | lo scrim e i livelli dello sfondo del contestuale |
| `fluid/FluidScreen.kt` | `FluidBarAction(actions, opensOn)` |
| `fluid/toolbar/FluidToolbar.kt` | `action(menu = …)` e il gruppo che diventa il menu |
| `fluid/FluidGlassMenuButton.kt` | il tasto deprecato |
| `fluidphysics/FluidMorphMenu.kt` | `FluidMorphMenuState`, `FluidMorphMenuButton`, `FluidMorphMenuHost`, `LocalFluidMorphMenuState` |
| `fluid/FluidMotion.kt` | i token (sezioni «Menus (3.0)», «La goccia», «I menu Liquid») |

**Galleria**: `sample/…/catalog/MenusDemo.kt`, pagina «Menù liquidi» (Chrome › Catalogo della 3.0), in quattro capitoli: il menu dal
tasto e dal gruppo (il tasto che si trasforma col punto di luce, le azioni della barra, il gruppo di una barra dichiarata sulla foto);
la struttura (la riga «Cartella Progetti», il palco «Priority e Fixed»); il contestuale (le righe, la tessera con la foto e la sua
anteprima, il tondo, la card); nelle build di debug la riga dello sfondo del contestuale. In fondo l'avviso.
