# R1 · Inventario di iOS 26: tutto ciò che un'app ottiene dal nuovo design

Ricerca del 2026-11-10 per il progetto «iOS in Android». Fonti: documentazione Apple (letta dal JSON che
alimenta le pagine), Human Interface Guidelines, sessioni WWDC25 con trascrizione (219, 356, 323, 284, 256,
243) e WWDC26 (102, 269, 278, 292, 251), più poche fonti secondarie marcate come tali. Non guarda il codice
dell'engine: la distanza dall'engine sta in `docs/liquid-glass/riferimenti.md` e nell'audit R2.

**Avviso di versione.** Oggi è in giro iOS 27 (WWDC26, giugno 2026). La documentazione Apple online descrive
già il 2027: dove iOS 27 cambia qualcosa di iOS 26 lo scrivo in una riga «iOS 27». La base dell'inventario
resta iOS 26, perché i frame di riferimento (sessione «Meet Liquid Glass») sono di iOS 26.

**Gradi delle fonti.** **D** = documentato (pagina di documentazione o HIG); **W** = detto da Apple in una
sessione WWDC (descrittivo, senza numeri); **O** = osservato (video Apple, recensioni, articoli di
sviluppatori: fonte secondaria); **De** = dedotto da più indizi, non verificato.

---

## 1. Cosa conta per il piano

1. Il modello di Apple è **dichiarativo**: lo sviluppatore dichiara intenti (posizione semantica, ruolo,
   gruppo, sorgente) e il sistema decide vetro, raggruppamento, forma e animazione. Per «scrivere come per
   Apple» l'engine deve offrire questo strato semantico (barra, scheda, ricerca, presentazione con sorgente),
   non solo componenti di vetro.
2. **Un solo meccanismo di trasformazione** regge quasi tutto: contenitore + identità + spaziatura. Da lì
   vengono la barra che cambia contesto, il menu che esce dal tasto, foglio/avviso/popover/action sheet che
   nascono dalla sorgente, la scheda di ricerca che diventa campo, la tab bar che si minimizza, il vetro che
   si divide e si fonde. Due motori separati non possono fare questo.
3. Il materiale ha **due varianti** (regular adattiva; clear senza adattività, con oscuramento al 35% sotto),
   **tinta** che genera una gamma di toni secondo la luminosità di ciò che sta dietro, **spessore che cresce
   con la taglia**, e i piccoli che girano chiaro/scuro mentre i grandi no.
4. **Le barre spariscono come fasce**: elementi flottanti più bordo dello scorrimento (soft di serie su iPhone,
   hard per intestazioni fissate, scurimento su contenuto scuro). In basso il bordo sfuma senza sfocare (O).
5. **Tutto nasce dalla sorgente**: menu, popover, fogli (con zoom), avvisi, dialoghi, action sheet in linea
   senza tasto Annulla. Il popover dalla barra **sostituisce** il tasto finché resta aperto.
6. I controlli **salgono nel vetro al tocco**: interruttore, cursore (con inerzia e allungamento), controllo
   segmentato, bolla di selezione della tab bar (trascinabile), lente del testo.
7. **Concentricità** con tre forme (fissa, capsula, concentrica = raggio del genitore − margine), capsula come
   forma di serie, forme che ereditano il contenitore.
8. **Numeri quasi mai documentati** (altezze, raggi, durate, molle): li deve dare R4 dai frame. Qui sono
   indicati solo quelli scritti da Apple o misurati da altri.
9. Due punti da decidere: il menu contestuale di iOS 26 **non sfoca più** lo sfondo (O), mentre Alessio lo
   vuole sfocato e scurito; e iOS 27 ha **cambiato l'aspetto** del bordo dello scorrimento automatico.

---

## 2. Tabella riassuntiva

Priorità: **alta** = lo vede chiunque usi un'app iOS ogni giorno; **media** = frequente ma non ovunque, o
solo su tablet; **bassa** = raro, di sistema o fuori dal telefono.

| ID | voce | cosa fa | API Apple | doc. | priorità |
|---|---|---|---|---|---|
| M1 | Vetro su una vista | sagoma di vetro dietro la vista, contenuto reso vibrante | `glassEffect(_:in:)`, `UIGlassEffect` | D | alta |
| M2 | Varianti regular/clear/identity | adattiva; trasparente con oscuramento; spenta | `Glass.regular/.clear/.identity` | D | alta (clear media) |
| M3 | Lente e materializzazione | definisce la sagoma piegando lo sfondo; compare modulando la lente | implicito | W | alta |
| M4 | Luce (riflessi) | sorgenti che illuminano il bordo; si muovono a blocco/sblocco e col movimento | implicito | W | alta |
| M5 | Ombra adattiva | più forte sul testo, più leggera su fondo chiaro piatto | implicito | W | alta |
| M6 | Chiaro/scuro adattivo | i piccoli girano secondo lo sfondo, con le icone; i grandi no | implicito | W | alta |
| M7 | Spessore con la taglia | più grande = più spesso, opaco, ombra più ricca | implicito | W | alta |
| M8 | Tinta | colore mappato sulla luminosità di ciò che sta dietro; solo azione principale | `Glass.tint(_:)`, `tintColor` | D+W | alta |
| M9 | Luce del contenuto sui grandi | il colore vicino si riversa su superficie e ombra (barre laterali) | implicito | W | bassa |
| M10 | Contenuto sul vetro | testo e simboli vibranti; niente vetro su vetro | implicito | D+W | alta |
| M11 | Accessibilità e preferenza utente | trasparenza ridotta, contrasto, movimento ridotto; Chiaro/Colorato (26.1), cursore (27) | automatico | W+O | media |
| M12 | Stato inattivo | il vetro recede quando la finestra perde il fuoco | implicito, `appearsActive` | W | bassa |
| C1 | Contenitore del vetro | vetri vicini resi insieme, campionamento condiviso, fusione per vicinanza | `GlassEffectContainer(spacing:)`, `UIGlassContainerEffect` | D | alta |
| C2 | Identità e morph | stessi ID animano forme che si trasformano fra stati | `glassEffectID(_:in:)` | D | alta |
| C3 | Transizioni del vetro | matchedGeometry (dalla forma vicina), materialize, identity | `glassEffectTransition(_:)` | D | alta |
| C4 | Unione | più viste in un'unica sagoma anche a riposo | `glassEffectUnion(id:namespace:)` | D | media |
| C5 | Dividere e fondere | due gocce si uniscono avvicinandosi; una si divide in due | `UIGlassContainerEffect.spacing` | W | alta |
| C6 | Barra che cambia contesto | i gruppi cambiano larghezza e icone fra un contesto e l'altro | automatico, `UIBarButtonItem.identifier` | W+O | alta |
| B1 | Barra flottante e gruppi | elementi su vetro flottante, raggruppati in automatico | `toolbar`, `ToolbarItem(Group)` | D+W | alta |
| B2 | Spaziatori | fisso separa gruppi; flessibile spinge e separa | `ToolbarSpacer(.fixed/.flexible)` | D | alta |
| B3 | Sfondo condiviso | togliere un elemento dal gruppo, senza vetro | `sharedBackgroundVisibility`, `hidesSharedBackground` | D | media |
| B4 | Azione principale | Fatto = segno di spunta tinto, separato, a destra | `.prominent`, `ButtonRole.confirm/.close` | D+W | alta |
| B5 | Tinta degli elementi | di serie monocromi; tinta solo al simbolo o allo sfondo | `tint`, `tintColor`, `.prominent` | W | media |
| B6 | Badge | indicatore su un elemento della barra | `badge(_:)` | W | media |
| B7 | Titolo, sottotitolo, titolo grande | grande che scorre col contenuto, sottotitolo, viste personalizzate | `navigationTitle/Subtitle`, `.largeTitle` | D+W | alta |
| B8 | Posizioni semantiche e menu «Altro» | primaria, conferma, annulla, barra in basso; trabocco | `ToolbarItemPlacement` | D | alta |
| B9 | Tasto indietro e Chiudi | chevron e X in un tondo di vetro, senza testo | automatico | W+O | alta |
| B10 | Simboli invece di testo | icone monocrome; non mescolare testo e simboli in un gruppo | regola | D+W | alta |
| B11 | Barre a spazio limitato (27) | priorità di visibilità, menu di trabocco, elemento fissato | `visibilityPriority`, `ToolbarOverflowMenu` | D | media |
| T1 | Tab bar flottante | capsula staccata dai bordi, larga quanto le schede | `TabView`, `Tab` | D+W | alta |
| T2 | Bolla di selezione | pressione lunga: la selezione diventa lente trascinabile | automatico | O | alta |
| T3 | Minimizzazione | allo scorrimento resta solo la scheda corrente | `tabBarMinimizeBehavior(_:)` | D | media |
| T4 | Accessorio in basso | vista sopra la barra, in linea quando è minimizzata | `tabViewBottomAccessory` | D | media |
| T5 | Scheda di ricerca | tondo separato a destra che diventa campo | `Tab(role: .search)`, `UISearchTab` | D+W | alta |
| T6 | Scheda prominente (27) | qualsiasi scheda come tondo separato, sempre visibile | `TabRole.prominent` | D | media |
| T7 | Tablet: barra in alto e barra laterale | tab bar in alto che diventa barra laterale di vetro | `.sidebarAdaptable` | D | media |
| R1 | Ricerca automatica | campo posizionato dal sistema (in basso su iPhone) | `searchable(text:placement:prompt:)` | D+W | alta |
| R2 | Ricerca nella barra in basso | campo che si adatta agli elementi vicini, sale sopra la tastiera | automatico, `DefaultToolbarItem` | W | alta |
| R3 | Ricerca minimizzata | tasto che diventa campo a tutta larghezza | `searchToolbarBehavior(.minimize)` | D+W | alta |
| R4 | Ricerca in alto o in linea | nella barra in alto o sotto il titolo, nel contenuto | `.navigationBarDrawer`, `.toolbar` | D+W | media |
| R5 | Anatomia del campo | lente, segnaposto, cancella, Annulla; ambiti, token, suggerimenti | `searchable`, `searchScopes` | D+W | media |
| N1 | Menu dal suo tasto | la bolla si apre e mostra il contenuto, più spessa | `Menu`, `UIMenu` | W+O | alta |
| N2 | Tasto della barra → menu | l'intero gruppo della barra si trasforma nel menu | automatico | O | alta |
| N3 | Struttura del menu | icone a sinistra, sezioni, sottomenu, dimensioni piccolo/medio/grande | `Menu`, `preferredElementSize` | D | alta |
| N4 | Ordine e chiusura | voci vicine al dito, chiusura dopo l'azione o no | `menuOrder`, `menuActionDismissBehavior` | D | media |
| N5 | Azione principale del menu | tocco = azione, pressione lunga = menu | `Menu(primaryAction:)` | D | media |
| N6 | Selezione trascinando il dito | premere, scorrere sulla voce, rilasciare | automatico | De | media |
| N7 | Menu di modifica del testo | voci principali + tasto che apre un menu completo | `UIEditMenuInteraction` | O | media |
| X1 | Menu contestuale | pressione lunga: l'elemento si solleva, anteprima, menu accanto | `contextMenu(menuItems:preview:)` | D | alta |
| X2 | Sfondo del menu contestuale | scurito; in iOS 26 non più sfocato | automatico | D+O | alta |
| X3 | Anteprima personalizzata | anteprima diversa, forma del ritaglio | `contentShape(.contextMenuPreview,_)` | D | media |
| X4 | Regole del contenuto | voci frequenti vicine al dito, distruttive in fondo in rosso | regole | D | media |
| P1 | Foglio di vetro | rientrato a mezza altezza, opaco e ancorato a tutta altezza | `sheet`, `UISheetPresentationController` | W | alta |
| P2 | Detent e maniglia | altezze di riposo, trascinamento, scorrimento che espande | `presentationDetents`, `detents` | D | alta |
| P3 | Foglio che nasce dal tasto | zoom dal tasto della barra al foglio | `navigationTransition(.zoom)` + `matchedTransitionSource` | D+W | alta |
| P4 | Avviso | dialogo modale che esce dal tasto | `alert`, `UIAlertController` | D+W | alta |
| P5 | Dialogo di conferma / action sheet | esce dall'elemento, senza Annulla, si chiude toccando fuori | `confirmationDialog`, `sourceItem` | D+W | alta |
| P6 | Popover | dal tasto della barra lo sostituisce finché è aperto; su iPhone diventa foglio | `popover`, `sourceItem` | D | media |
| P7 | Modalità e oscuramento | oscuramento se interrompe, nessuno se parallelo | `largestUndimmedDetentIdentifier` | W+D | alta |
| K1 | Tasti di vetro | stili glass, glassProminent, clear | `.glass`, `.glassProminent`, `.glass(_:)` | D | alta |
| K2 | Forma e taglie dei controlli | capsula di serie, taglia extra large, larghezza flessibile | `controlSize`, `buttonSizing`, `buttonBorderShape` | D+W | alta |
| K3 | Interruttore | pomello a pillola che diventa vetro al tocco | `Toggle`, `UISwitch` | D+W | alta |
| K4 | Cursore | pomello che diventa bolla, inerzia, allungamento, tacche, valore neutro | `Slider(…ticks:)`, `UISlider` | D+W+O | alta |
| K5 | Controllo segmentato | selezione che diventa vetro al tocco | `.pickerStyle(.segmented)` | W | alta |
| K6 | Stepper, selettori, data | ridisegnati, nessun comportamento nuovo documentato | `Stepper`, `Picker`, `DatePicker` | D | media |
| K7 | Campi di testo | nel contenuto senza vetro; il campo di ricerca nella barra sì | `TextField` | W | alta |
| L1 | Contenuto senza vetro | il vetro resta alla navigazione; materiali standard nel contenuto | `Material` | D+W | alta |
| L2 | Liste e moduli | righe più alte, sezioni più arrotondate, intestazioni non maiuscole | `List`, `Form`, `listSectionMargins` | D+W | alta |
| L3 | Azioni a scorrimento | azioni sulla riga, coerenti col menu contestuale | `swipeActions` | D | alta |
| E1 | Bordo dello scorrimento | dissolve il contenuto sotto gli elementi flottanti | `scrollEdgeEffectStyle(_:for:)` | D+W | alta |
| E2 | Soft, hard, scurimento | sfumato di serie; netto con intestazioni fissate; scurisce su contenuto scuro | `.soft`, `.hard`, `.automatic` | D+W | alta |
| E3 | Barre personalizzate | registrare una barra propria per avere il bordo | `safeAreaBar`, `UIScrollEdgeElementContainerInteraction` | D | media |
| E4 | Estensione dello sfondo | specchia e sfoca l'immagine sotto barra laterale e ispettore | `backgroundExtensionEffect()` | D | media (tablet) |
| G1 | Concentricità | raggi che condividono il centro col contenitore | `ConcentricRectangle`, `containerShape` | D+W | alta |
| G2 | Capsula e margine dai bordi | sul telefono capsula con margine extra vicino ai bordi | regola | W | alta |
| G3 | Misure note | 44 pt di bersaglio, 35% di oscuramento, limiti numerici | — | D/O | alta |
| H1 | Flessione e luce al tocco | si flette come un gel e si accende di luce | implicito | W | alta |
| H2 | Bagliore che si propaga | dal dito a tutto l'elemento e ai vetri vicini | implicito | W | alta |
| H3 | Vetro interattivo | scala, rimbalzo, luccichio | `Glass.interactive()`, `isInteractive` | D+W | alta |
| H4 | Salire nel vetro | elemento che diventa vetro solo mentre lo si tocca | implicito | W+D | alta |
| H5 | Molle e durate | non pubblicate; animazione di serie | `withAnimation` | — | alta |
| H6 | Aptica | nessuna aptica documentata legata al vetro | `sensoryFeedback` | — | media |
| V1 | Push/pop interrompibile | la transizione si può interrompere e invertire | automatico | W | alta |
| V2 | Ritorno da tutto il contenuto | scorrere a destra in qualunque punto per tornare | `interactiveContentPopGestureRecognizer` | D+W | alta |
| V3 | Elementi della barra che si trasformano nel push | i tasti si trasformano fra pagina e pagina | automatico, `UIBarButtonItem.identifier` | W+D | alta |
| V4 | Transizione zoom | la pagina si apre ingrandendo l'elemento sorgente | `navigationTransition(.zoom(sourceID:in:))` | D | media |
| V5 | Barra di navigazione che si ritira (27) | la barra in alto scivola via allo scorrimento | `barMinimizationBehavior` | W | bassa |
| V6 | Split view e ispettore | colonne con barra laterale di vetro, ispettore | `NavigationSplitView`, `inspector` | D | media (tablet) |
| O1 | Tastiera | campo che sale con la tastiera; tastiera in stile nuovo | — | D+O | media |
| O2 | Lente del testo | la lente di selezione è una bolla di vetro | automatico | O | bassa |
| O3 | Foglio di condivisione | esempio di foglio da vetro a opaco | `ShareLink` | O | media |
| O4 | Schermata di blocco | orologio con la tinta del vetro, luce che corre allo sblocco | — | W | bassa |
| O5 | Icone a strati | icone con riflessi e varianti chiara/scura/clear/colorata | Icon Composer | D | bassa |
| O6 | Player video | controlli in tasti di vetro sopra il video | AVKit | O | bassa |

---

## 3. Le famiglie

### 3.1 Il materiale

#### M1 · Vetro su una vista (`glassEffect`)
- **Cos'è.** Il modo standard di dare il materiale a una vista personalizzata; i componenti di sistema lo
  hanno già.
- **Comportamento.** Il sistema rende una forma di vetro **dietro** la vista, ancorata ai suoi limiti
  (padding compreso), e applica gli «effetti di primo piano» del vetro al contenuto: il testo dentro prende
  un colore **vibrante** che si adatta per restare leggibile sugli sfondi colorati (323). Il modificatore
  cattura il contenuto da mandare al contenitore: va messo **dopo** gli altri modificatori che ne cambiano
  l'aspetto.
- **API.** `func glassEffect(_ glass: Glass = .regular, in shape: some Shape = DefaultGlassEffectShape()) -> some View`.
  Forma di serie: **capsula** (`DefaultGlassEffectShape`); qualsiasi `Shape` (es. `.rect(cornerRadius: 16)`,
  `Circle`). UIKit: `UIVisualEffectView` con `effect = UIGlassEffect(style: .regular)`, contenuto in
  `contentView`, forma con `cornerConfiguration` (di serie capsula).
- **Misure.** Non documentate.
- **Regole.** Solo sullo strato di navigazione; con parsimonia sui controlli personalizzati; più vetri vicini
  sempre in un contenitore (C1). Per i controlli personalizzati il materiale va sul controllo, non sulle sue
  viste interne (356).
- **Fonte.** [glassEffect](https://developer.apple.com/documentation/swiftui/view/glasseffect(_:in:)),
  [Applying Liquid Glass to custom views](https://developer.apple.com/documentation/swiftui/applying-liquid-glass-to-custom-views),
  [WWDC25 323 @18:30](https://developer.apple.com/videos/play/wwdc2025/323/?time=1110),
  [WWDC25 284 @20:28](https://developer.apple.com/videos/play/wwdc2025/284/?time=1228). **D**.

#### M2 · Varianti: regular, clear, identity
- **Cos'è.** `Glass` è la configurazione del materiale. Due varianti visibili, da **non mescolare** (219).
- **Comportamento.** *Regular*: tutti gli effetti adattivi, leggibile su qualunque sfondo, qualunque taglia,
  ci si può mettere sopra qualunque cosa; sfoca e regola la luminosità dello sfondo. La usano quasi tutti i
  componenti di sistema. *Clear*: **nessun comportamento adattivo**, permanentemente più trasparente; per
  la leggibilità serve uno **strato di oscuramento** sotto (o oscuramento localizzato se gli elementi sono
  piccoli). *Identity*: il contenuto resta come senza vetro (serve ad accendere/spegnere il vetro senza
  cambiare albero).
- **API.** `Glass.regular`, `Glass.clear`, `Glass.identity`; UIKit `UIGlassEffect.Style.regular/.clear`;
  tasti `UIButton.Configuration.glass()/.clearGlass()/.prominentGlass()/.prominentClearGlass()`,
  SwiftUI `.buttonStyle(.glass(.clear))` (`GlassButtonStyle.init(_:)` da iOS 26.1).
- **Misure.** Oscuramento per clear su contenuto chiaro: **nero al 35%** (HIG); l'esempio di codice usa
  `.black.opacity(0.3)`. Su contenuto già scuro o con i controlli AVKit (che hanno il loro oscuramento) non
  serve.
- **Regole.** Clear solo se tutte e tre: sopra contenuto ricco (foto, video); lo strato di contenuto regge
  l'oscuramento; il contenuto sopra il vetro è chiaro e grassetto. Regular per testo abbondante (avvisi,
  barre laterali, popover).
- **Fonte.** [Glass](https://developer.apple.com/documentation/swiftui/glass),
  [Glass.clear](https://developer.apple.com/documentation/swiftui/glass/clear),
  [HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials),
  [WWDC25 219 @13:48](https://developer.apple.com/videos/play/wwdc2025/219/?time=828). **D**.

#### M3 · Lente e materializzazione
- **Cos'è.** Il modo principale in cui il vetro si definisce: piega e concentra la luce (i materiali di prima
  la disperdevano).
- **Comportamento.** A riposo la sagoma si legge per la deformazione dello sfondo vicino al bordo. In entrata e
  uscita il vetro **si materializza modulando gradualmente piegatura e lente**, non l'opacità (219). In UIKit
  impostare `effect` dentro un blocco di animazione produce l'animazione «materialize»; metterlo a `nil`
  produce la «dematerializzazione». Apple chiede di animare l'effetto, **non `alpha`** (284).
- **API.** Implicita; `GlassEffectTransition.materialize` (C3); `UIVisualEffectView.effect = nil` animato.
- **Misure.** Non documentate (R4: frame 2:33–2:48 di 219).
- **Fonte.** [WWDC25 219 @2:55](https://developer.apple.com/videos/play/wwdc2025/219/?time=175),
  [WWDC25 284 @23:20](https://developer.apple.com/videos/play/wwdc2025/284/?time=1400). **W**.

#### M4 · Luce: lo strato dei riflessi
- **Comportamento.** Sorgenti di luce nell'ambiente illuminano il materiale producendo riflessi che
  rispondono alla geometria. In certe interazioni (blocco/sblocco) le luci si muovono e la luce **corre
  intorno al bordo** disegnandone la sagoma; in alcuni casi la luce segue il **movimento del dispositivo**.
- **API.** Nessuna.
- **Misure.** Non documentate. Lato osservato: bordo sottile e netto, più forte su due lati opposti
  (riferimenti.md, frame 11:09–11:36).
- **Fonte.** [WWDC25 219 @11:04](https://developer.apple.com/videos/play/wwdc2025/219/?time=664). **W**.

#### M5 · Ombra adattiva
- **Comportamento.** L'elemento sa cosa ha dietro: **aumenta l'opacità dell'ombra sopra il testo**, la
  **diminuisce sopra un fondo chiaro uniforme**. Serve a separarlo sempre dal contenuto.
- **API.** Nessuna. **Misure.** Non documentate.
- **Fonte.** [WWDC25 219 @11:38](https://developer.apple.com/videos/play/wwdc2025/219/?time=698). **W**.

#### M6 · Chiaro/scuro adattivo
- **Comportamento.** Ogni strato si adatta di continuo a ciò che sta dietro: più ombra quando scorre testo
  sotto, quantità di tinta e gamma dinamica regolate per tenere leggibili i tasti. **Gli elementi piccoli**
  (barre di navigazione, tab bar, tasti) **girano da chiaro a scuro** secondo lo sfondo; **gli elementi
  grandi** (menu, barre laterali) si adattano ma **non girano** (troppa superficie, il cambio distrarrebbe).
  Simboli e glifi girano insieme al vetro per massimizzare il contrasto; su regular avviene in automatico.
  UIKit: il vetro ha un aspetto chiaro e uno scuro, segue `userInterfaceStyle`; vetro e contenuto girano da
  soli **se il contenuto usa colori dinamici**; aggiunto dentro un contenitore di vetro esistente si adatta
  da solo.
- **API.** Nessuna; colori dinamici/vibranti.
- **Misure.** Soglie e isteresi non documentate.
- **Fonte.** [WWDC25 219 @6:00 e @15:16](https://developer.apple.com/videos/play/wwdc2025/219/?time=916),
  [WWDC25 323 @0:25](https://developer.apple.com/videos/play/wwdc2025/323/),
  [WWDC25 284 @20:54](https://developer.apple.com/videos/play/wwdc2025/284/?time=1254). **W**.

#### M7 · Spessore con la taglia
- **Comportamento.** Quando il vetro si flette e cresce (es. menu da un tasto della barra) simula un materiale
  **più spesso**: ombre più profonde e ricche, lente e rifrazione più marcate, diffusione della luce più
  morbida. UIKit: «una taglia più grande è **più opaca**; una più piccola è più chiara e passa da chiaro a
  scuro da sola».
- **API.** Nessuna. **Misure.** Non documentate (frame 7:15–7:27 di 219).
- **Fonte.** [WWDC25 219 @7:02](https://developer.apple.com/videos/play/wwdc2025/219/?time=422),
  [WWDC25 284 @21:30](https://developer.apple.com/videos/play/wwdc2025/284/?time=1290). **W**.

#### M8 · Tinta
- **Cos'è.** Il modo nuovo di colorare un elemento di vetro: etichette, testo, tasti interamente tinti, fino
  all'orologio della schermata di blocco.
- **Comportamento.** Scegliere un colore **genera una gamma di toni mappati sulla luminosità del contenuto
  dietro**: tinta, luminosità e saturazione cambiano come nel vetro colorato vero, senza allontanarsi troppo
  dal colore voluto. Resta trasparente e compatibile con tutti i comportamenti del vetro; un riempimento pieno
  invece è opaco e «rompe» il materiale. In UIKit la tinta si anima insieme alle altre proprietà e diventa da
  sola una versione vibrante.
- **API.** `Glass.tint(_ color: Color?) -> Glass`; `UIGlassEffect.tintColor`; tasti `glassProminent`,
  `UIBarButtonItem.style = .prominent`.
- **Misure.** Funzione colore → gamma non documentata.
- **Regole.** Solo per elementi e azioni **principali**; non tingere tutto (niente risalta più); il colore
  dell'app va nello strato di contenuto. Evitare colori simili fra etichette della barra e sfondi del
  contenuto. Con colori propri: variante chiara, scura e a contrasto aumentato.
- **Fonte.** [WWDC25 219 @16:09–17:50](https://developer.apple.com/videos/play/wwdc2025/219/?time=969),
  [Glass.tint](https://developer.apple.com/documentation/swiftui/glass/tint(_:)),
  [WWDC25 284 @22:15](https://developer.apple.com/videos/play/wwdc2025/284/?time=1335). **D+W**.

#### M9 · Luce del contenuto sui grandi elementi
- **Comportamento.** Sugli elementi grandi (barre laterali) l'aspetto è informato dall'ambiente dell'app: la
  luce del contenuto colorato vicino **si riversa** sulla superficie e anche **nell'ombra**.
- **API.** Nessuna. **Fonte.** [WWDC25 219 @8:01](https://developer.apple.com/videos/play/wwdc2025/219/?time=481). **W**.

#### M10 · Contenuto sul vetro, niente vetro su vetro
- **Regole.** Il vetro va **solo nello strato di navigazione** che galleggia sopra il contenuto; una tabella
  di vetro confonderebbe la gerarchia. **Mai vetro su vetro**: per gli elementi sopra il vetro usare
  riempimenti, trasparenza e vibrancy, come una pellicola sottile che fa parte del materiale. Eccezione nel
  contenuto: controlli con un elemento interattivo transitorio (cursori, interruttori) che diventa vetro solo
  durante l'interazione (HIG). Maps rimuove i suoi tasti di vetro quando il foglio si espande, per non
  sovrapporre vetro a vetro (284).
- **Fonte.** [WWDC25 219 @13:04](https://developer.apple.com/videos/play/wwdc2025/219/?time=784),
  [HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials). **D+W**.

#### M11 · Accessibilità e preferenza dell'utente
- **Comportamento.** Modificatori del materiale applicati in automatico quando l'impostazione di sistema è
  attiva: **trasparenza ridotta** → vetro più smerigliato, copre di più; **contrasto aumentato** → elementi
  prevalentemente bianchi o neri con un **bordo di contrasto**; **movimento ridotto** → intensità di alcuni
  effetti ridotta, **proprietà elastiche disattivate**.
- Preferenza dell'utente: iOS 26.1 aggiunge in Impostazioni › Schermo › Liquid Glass la scelta **Trasparente
  / Colorato** (O, MacRumors). **iOS 27**: un **cursore** da «ultra trasparente» a «del tutto colorato», a cui
  il vetro delle app risponde senza ricompilare (W, WWDC26 102 e 269).
- **API.** Automatico; SwiftUI `accessibilityReduceTransparency`, `accessibilityReduceMotion`,
  `colorSchemeContrast` nell'environment.
- **Fonte.** [WWDC25 219 @18:06](https://developer.apple.com/videos/play/wwdc2025/219/?time=1086),
  [MacRumors, 26.1](https://www.macrumors.com/2025/10/20/ios-26-1-liquid-glass-toggle/),
  [WWDC26 269](https://developer.apple.com/videos/play/wwdc2026/269/). **W+O**. Alessio: l'accessibilità non è
  priorità ora.

#### M12 · Stato inattivo
- **Comportamento.** Quando una finestra perde il fuoco (Mac, iPad) il vetro cambia aspetto e recede. iOS 27:
  su iPad icone e testo si attenuano nella finestra non attiva.
- **API.** `@Environment(\.appearsActive)`. **Fonte.** [WWDC25 219 @12:30](https://developer.apple.com/videos/play/wwdc2025/219/?time=750),
  [WWDC26 269](https://developer.apple.com/videos/play/wwdc2026/269/). **W**. Bassa: su Android vale solo per
  finestre multiple.

---

### 3.2 Contenitori e trasformazioni

#### C1 · Contenitore del vetro (`GlassEffectContainer`)
- **Cos'è.** Il gruppo di forme di vetro rese **insieme**. Ogni vista con `glassEffect` dentro contribuisce una
  forma.
- **Comportamento.** Tre funzioni. (1) **Prestazioni**: un solo rendering per tutte le forme. (2)
  **Correttezza**: il vetro campiona un'area **più grande di sé** per rifrangere i colori vicini, ma **il
  vetro non può campionare altro vetro**; vetri vicini in contenitori diversi danno risultati incoerenti, nello
  stesso contenitore condividono la regione di campionamento. (3) **Fusione**: quando le forme si
  avvicinano i contorni **cominciano a fondersi**; più alto `spacing`, prima comincia. Se lo `spacing` del
  contenitore è maggiore della spaziatura del layout interno, i vetri sono **fusi già a riposo**; animando
  comparse e scomparse le forme si separano o si uniscono. UIKit: `UIGlassContainerEffect` impone anche un
  **adattamento uniforme** (tutti i vetri del gruppo si adattano allo sfondo in modo coerente).
- **API.** `GlassEffectContainer(spacing: CGFloat? = nil) { content }`; UIKit `UIGlassContainerEffect` con
  `spacing` («la distanza fra elementi alla quale cominciano a fondersi»), vetri figli come
  `UIVisualEffectView` nel `contentView`.
- **Misure.** Spaziatura di serie non documentata (`nil`). Esempi Apple: 40 e 20 pt.
- **Regole.** Usarlo sempre con più vetri; non creare troppi contenitori né troppi vetri a schermo.
- **Fonte.** [GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer),
  [WWDC25 323 @19:51](https://developer.apple.com/videos/play/wwdc2025/323/?time=1191),
  [UIGlassContainerEffect](https://developer.apple.com/documentation/uikit/uiglasscontainereffect). **D**.

#### C2 · Identità e morph (`glassEffectID`)
- **Comportamento.** Ogni vetro riceve un identificatore unico in un namespace. Quando la gerarchia cambia,
  SwiftUI usa ID, spaziatura del contenitore e geometria delle forme per decidere **quali forme trasformare
  in quali**. Esempio Apple: con contenitore e `HStack` a 40, la gomma esce dalla matita e vi rientra quando il
  bordo più vicino è entro la spaziatura. Il modificatore agisce solo durante transizioni e animazioni
  (serve `withAnimation`). Landmarks: il tasto e i badge partono come una vista unita, si separano «come un
  liquido» e si riuniscono nel tasto.
- **API.** `func glassEffectID(_ id: (some Hashable & Sendable)?, in namespace: Namespace.ID) -> some View`
  con `@Namespace`.
- **Misure.** Curva di serie non documentata.
- **Fonte.** [glassEffectID](https://developer.apple.com/documentation/swiftui/view/glasseffectid(_:in:)),
  [Landmarks: badge](https://developer.apple.com/documentation/swiftui/landmarks-displaying-custom-activity-badges). **D**.

#### C3 · Transizioni del vetro (`glassEffectTransition`)
- **Comportamento.** Per forme aggiunte o tolte **entro la spaziatura** del contenitore la transizione di serie
  è **matchedGeometry**: la forma che compare **deriva la sua geometria da una forma vicina** e ne esce (e vi
  rientra). Con l'animazione di serie aggiunge anche **scala e spostamento al contenuto** quando l'identità
  resta e cambia solo il contenuto; per toglierli si passa un'animazione esplicita (es. `.spring`).
  **materialize**: dissolve il contenuto e fa entrare/uscire il materiale, senza legarsi ad altre forme; per
  forme più lontane della spaziatura. **identity**: nessun cambiamento. Il sistema applica «più che cambi di
  opacità».
- **API.** `func glassEffectTransition(_ transition: GlassEffectTransition) -> some View`;
  `.matchedGeometry`, `.materialize`, `.identity`.
- **Regole.** Usare solo queste due per coerenza fra app.
- **Fonte.** [GlassEffectTransition](https://developer.apple.com/documentation/swiftui/glasseffecttransition),
  [matchedGeometry](https://developer.apple.com/documentation/swiftui/glasseffecttransition/matchedgeometry). **D**.

#### C4 · Unione (`glassEffectUnion`)
- **Comportamento.** Più viste contribuiscono a **un'unica sagoma** anche a riposo; si combinano solo vetri con
  **stessa forma, stessa variante e stesso ID**. Utile per viste create dinamicamente o fuori da uno stack.
- **API.** `func glassEffectUnion(id: (some Hashable & Sendable)?, namespace: Namespace.ID) -> some View`.
- **Fonte.** [glassEffectUnion](https://developer.apple.com/documentation/swiftui/view/glasseffectunion(id:namespace:)). **D**.

#### C5 · Dividere e fondere
- **Comportamento.** Finché c'è spazio fra loro i vetri sono separati; avvicinandosi **cominciano a fondersi
  come gocce d'acqua**; animati fino a sovrapporsi diventano **una sola forma**. Per dividere: aggiungere le
  viste **nella stessa posizione senza animazione**, poi animarle via insieme.
- **API.** UIKit `UIGlassContainerEffect`; SwiftUI C1–C3.
- **Fonte.** [WWDC25 284 @24:12–24:33](https://developer.apple.com/videos/play/wwdc2025/284/?time=1452),
  frame 5:33–5:51 di 219 (vista obliqua della fusione). **W**.

#### C6 · La barra che cambia contesto
- **Comportamento.** Passando fra stati dell'app il vetro **si trasforma fra i controlli di ciascun contesto**:
  resta l'idea di un **unico piano flottante** su cui vivono i controlli, che cambiano forma di continuo.
  Osservato nei frame (riferimenti.md, `161838→162000`): i gruppi cambiano **larghezza in continuo**, le icone
  escono ed entrano **sfocandosi** (non in dissolvenza), una coppia si chiude in un tasto tondo, la larghezza
  finale oscilla appena. Anche nelle transizioni di navigazione gli elementi della barra si trasformano
  (256). UIKit abbina gli elementi fra due configurazioni con euristiche su posizione e contenuto; con
  `UIBarButtonItem.identifier` uguale due elementi diversi sono trattati come lo stesso.
- **API.** Automatico; `UIBarButtonItem.identifier` (iOS 26). In SwiftUI nessuna API esplicita documentata
  per l'abbinamento (De: identità dei `ToolbarItem`).
- **Fonte.** [WWDC25 219 @4:41](https://developer.apple.com/videos/play/wwdc2025/219/?time=281),
  [WWDC25 256 @1:22](https://developer.apple.com/videos/play/wwdc2025/256/?time=82),
  [UIBarButtonItem.identifier](https://developer.apple.com/documentation/uikit/uibarbuttonitem/identifier). **W+O**.

---

### 3.3 Barre degli strumenti e di navigazione

#### B1 · Barra flottante e raggruppamento automatico
- **Cos'è.** Barre di navigazione e degli strumenti **trasparenti**, con gli elementi su superfici di vetro
  flottanti che si adattano a ciò che sta sotto.
- **Comportamento.** Il sistema separa gli elementi in **gruppi visivi; ogni gruppo condivide un vetro**.
  Regole UIKit: i tasti con immagine condividono lo sfondo con gli altri tasti con immagine; i gruppi con più
  elementi condividono lo sfondo; **tasti di testo, «Fatto» e «Chiudi» di sistema e tasti prominenti hanno
  ciascuno il proprio vetro**. Il tasto indietro di sistema sta in un gruppo a sé (323). Lo sfondo della barra
  è trasparente di serie: `UIBarAppearance` o `backgroundColor` interferiscono col vetro.
- **API.** SwiftUI `.toolbar { ToolbarItem(placement:) {…}; ToolbarItemGroup {…} }`; UIKit
  `navigationItem.rightBarButtonItems`, `toolbarItems`, `UIBarButtonItemGroup`.
- **Misure.** Altezze, raggi e spaziature non documentati.
- **Regole.** Togliere sfondi e bordi personalizzati; la gerarchia viene da disposizione e raggruppamento;
  raggruppare per funzione e frequenza; **al massimo tre gruppi**; navigazione e azioni critiche (Fatto,
  Chiudi, Salva) in gruppi dedicati; spostare il secondario in un menu «Altro».
- **Fonte.** [WWDC25 284 @7:19–8:50](https://developer.apple.com/videos/play/wwdc2025/284/?time=439),
  [HIG Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars),
  [WWDC25 356, capitolo «Structure»](https://developer.apple.com/videos/play/wwdc2025/356/?time=376). **D+W**.

#### B2 · Spaziatori
- **Comportamento.** Uno spaziatore **fisso** spezza un gruppo in due vetri vicini (es. «preferito» e
  «aggiungi» insieme, «condividi» a parte). Uno **flessibile** si espande e spinge gli elementi agli estremi
  (Mail: filtro a sinistra, ricerca e componi a destra). UIKit: ogni `flexibleSpace` **separa** lo sfondo; per
  distribuire gli elementi in un solo vetro si usano spazi flessibili con `hidesSharedBackground = false`.
- **API.** `ToolbarSpacer(_ sizing: SpacerSizing, placement:)` con `.fixed`/`.flexible`;
  `UIBarButtonItem.fixedSpace(_:)`, `.flexibleSpace()`.
- **Misure.** La larghezza dello spaziatore fisso è «predefinita dal sistema e dal contesto»: non pubblicata.
- **Fonte.** [ToolbarSpacer](https://developer.apple.com/documentation/swiftui/toolbarspacer),
  [SpacerSizing](https://developer.apple.com/documentation/swiftui/spacersizing),
  [WWDC25 323 @8:26](https://developer.apple.com/videos/play/wwdc2025/323/?time=506). **D**.

#### B3 · Sfondo condiviso visibile o nascosto
- **Comportamento.** Un elemento con lo sfondo nascosto **va in un gruppo suo, senza vetro** (es. l'avatar in
  Books). In UIKit `hidesSharedBackground = true` toglie il vetro; `sharesBackground = false` lo tiene ma non
  lo condivide. Entrambi ignorati dentro un `UIBarButtonItemGroup` con più elementi.
- **API.** `.sharedBackgroundVisibility(_ visibility: Visibility) -> some ToolbarContent`;
  `UIBarButtonItem.hidesSharedBackground`, `.sharesBackground`.
- **Fonte.** [sharedBackgroundVisibility](https://developer.apple.com/documentation/swiftui/toolbarcontent/sharedbackgroundvisibility(_:)),
  [hidesSharedBackground](https://developer.apple.com/documentation/uikit/uibarbuttonitem/hidessharedbackground). **D**.

#### B4 · Azione principale (prominente)
- **Comportamento.** L'azione principale come **Fatto** sta da sola e appare **tinta**: su iOS spesso un
  **segno di spunta blu** in un tondo; Chiudi diventa una **X**. Un elemento prominente non si raggruppa con
  gli altri e prende uno stile adatto al contesto.
- **API.** `UIBarButtonItem.style = .prominent`; SwiftUI `ButtonRole.confirm` e `ButtonRole.close` (iOS 26:
  mostrano il tasto di conferma o chiusura nella barra), `.buttonStyle(.borderedProminent)` + `.tint` (256),
  `ToolbarItemPlacement.confirmationAction/.cancellationAction`.
- **Regole.** **Una sola** azione principale, sul lato **destro** (trailing).
- **Fonte.** [HIG Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars),
  [UIBarButtonItem.Style.prominent](https://developer.apple.com/documentation/uikit/uibarbuttonitem/style-swift.enum/prominent),
  [ButtonRole](https://developer.apple.com/documentation/swiftui/buttonrole),
  [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/) (O). **D+W**.

#### B5 · Tinta degli elementi della barra
- **Comportamento.** Le icone sono **monocrome** in più punti, barre comprese (`labelColor`). Una tinta sul
  tasto colora **solo il simbolo**; lo stile prominente **tinge lo sfondo**.
- **API.** `.tint(_:)`, `UIBarButtonItem.tintColor`, `.style = .prominent`.
- **Regole.** Colore solo per dare significato (invito all'azione, passo successivo), non per decorare.
- **Fonte.** [WWDC25 323 @7:49](https://developer.apple.com/videos/play/wwdc2025/323/?time=469),
  [WWDC25 284 @8:53](https://developer.apple.com/videos/play/wwdc2025/284/?time=533). **W**.

#### B6 · Badge
- **Comportamento.** Indicatore sull'elemento della barra (es. una reazione nuova). Nella tab bar: ovale rosso
  con testo bianco, numero o punto esclamativo, solo per informazioni critiche.
- **API.** `.badge(_:)` sul contenuto del `ToolbarItem`; `Tab(…).badge(_:)`; UIKit `UITabBarItem.badgeValue`
  (badge degli elementi della barra in UIKit: non verificato).
- **Misure.** Non documentate.
- **Fonte.** [WWDC25 323 @9:40](https://developer.apple.com/videos/play/wwdc2025/323/?time=580),
  [HIG Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars). **W+D**.

#### B7 · Titolo, sottotitolo, titolo grande
- **Comportamento.** Il **titolo grande** ora sta in cima alla vista scorrevole e **scorre col contenuto**
  sotto la barra; diventa titolo piccolo appena si scorre e torna grande in cima. Nuovo **sottotitolo** sotto
  il titolo (Mail: numero di non letti). Viste personalizzate nell'area del titolo grande e del suo
  sottotitolo (Mail: il filtro attivo in un tasto sotto il titolo grande). Su iOS 26 molte viste di Apple non
  fissano più la barra del titolo: il contenuto scorre sotto, col bordo dello scorrimento (O).
- **API.** `navigationTitle(_:)`, `navigationSubtitle(_:)` (iOS 26), `toolbarTitleDisplayMode(.automatic |
  .inline | .inlineLarge | .large)`, `ToolbarItemPlacement.title/.largeTitle/.subtitle/.largeSubtitle`
  (iOS 26); UIKit `prefersLargeTitles`, `navigationItem.subtitle`, `attributedSubtitle`, `largeSubtitleView`.
- **Misure.** Titolo grande 34 pt grassetto, corpo 17 pt (O, Learn UI Design); altre non documentate.
- **Regole.** Titolo sotto i 15 caratteri; non usare il nome dell'app; per tenere visibile il titolo grande la
  vista scorrevole deve estendersi sotto la barra.
- **Fonte.** [WWDC25 284 @10:15](https://developer.apple.com/videos/play/wwdc2025/284/?time=615),
  [largeSubtitle](https://developer.apple.com/documentation/swiftui/toolbaritemplacement/largesubtitle),
  [HIG Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars). **D+W**.

#### B8 · Posizioni semantiche, barra in basso, menu «Altro»
- **Comportamento.** Posizioni **semantiche** (il sistema decide dove: `automatic`, `principal`, `status`,
  `primaryAction`, `secondaryAction`, `confirmationAction`, `cancellationAction`, `destructiveAction`,
  `navigation`) ed **esplicite** (`topBarLeading`, `topBarTrailing`, `bottomBar`, `keyboard`). Se gli
  elementi non ci stanno il sistema crea un **menu di trabocco**. Sinistra: indietro e titolo; centro: comandi
  comuni; destra: elementi importanti, ricerca, menu Altro, azione principale.
- **API.** `ToolbarItemPlacement`, `toolbarRole(_:)`, `toolbar(removing:)`, `ToolbarItem.hidden(_:)`
  (iOS 26.4: nascondere l'elemento, non la vista dentro).
- **Regole.** Pochi elementi; un menu Altro solo se serve; nascondere l'**elemento** intero, altrimenti resta
  un vetro vuoto.
- **Fonte.** [ToolbarItemPlacement](https://developer.apple.com/documentation/swiftui/toolbaritemplacement),
  [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass). **D**.

#### B9 · Tasto indietro e Chiudi
- **Comportamento.** Il tasto indietro standard appare come **chevron in un tondo di vetro**, senza il
  titolo della pagina precedente (O: riportato da sviluppatori, non da Apple; da verificare sui frame);
  Chiudi è una X, Fatto un segno di spunta (MacStories). La pressione lunga sul tasto indietro che mostra la
  cronologia è un comportamento storico di iOS: non verificato su iOS 26.
- **API.** Automatico nella navigazione; `ButtonRole.close`.
- **Regole.** Usare i simboli standard, mai l'etichetta «Indietro» o «Chiudi».
- **Fonte.** [HIG Toolbars › Navigation](https://developer.apple.com/design/human-interface-guidelines/toolbars),
  [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/). **D+O**.

#### B10 · Simboli invece di testo
- **Regole.** Preferire simboli di sistema **senza bordo** (il gruppo fa già da contenitore); testo solo per
  azioni senza un simbolo chiaro (Seleziona, Modifica); **non mettere un tasto di testo nello stesso gruppo
  di un simbolo** (sembra un tasto solo): i tasti di testo stanno nel loro vetro. Ogni icona ha la sua
  etichetta di accessibilità.
- **Fonte.** [WWDC25 356, capitolo «Structure»](https://developer.apple.com/videos/play/wwdc2025/356/?time=376),
  [HIG Toolbars](https://developer.apple.com/design/human-interface-guidelines/toolbars). **D+W**.

#### B11 · Barre con poco spazio (iOS 27)
- **Comportamento.** Ridimensionando la finestra il sistema sposta nel trabocco gli elementi che non stanno;
  su iPhone anche azioni importanti possono finire nel menu. iOS 27 permette di decidere cosa resta.
- **API.** `.visibilityPriority(.high)`, `ToolbarOverflowMenu { … }` (sempre nel menu), placement
  `.topBarPinnedTrailing` (fissato a destra, va nel trabocco solo con la ricerca attiva).
- **Fonte.** [visibilityPriority](https://developer.apple.com/documentation/swiftui/toolbarcontent/visibilitypriority(_:)),
  [ToolbarOverflowMenu](https://developer.apple.com/documentation/swiftui/toolbaroverflowmenu),
  [WWDC26 269](https://developer.apple.com/videos/play/wwdc2026/269/?time=375). **D**.

---

### 3.4 Tab bar

#### T1 · Tab bar flottante
- **Cos'è.** Navigazione fra le sezioni principali (non azioni). Su iPhone **galleggia in basso**, sopra il
  contenuto, su vetro.
- **Comportamento.** Capsula centrata, staccata dai bordi, **lunga quanto serve** per il numero di schede
  (anche due); aspetto «più compatto» di prima. Scheda attiva: glifo colorato **più una forma evidenziata**.
  Le icone stanno sopra le etichette in compatto, a fianco in regolare. Ogni scheda ricorda il suo stato.
- **API.** `TabView { Tab("Titolo", systemImage: "…") { … } }`; UIKit `UITabBarController` con `UITab`.
- **Misure.** Etichette 11 pt; staccata **21 pt** da sinistra, destra e fondo (O, Learn UI Design). Altezza e
  raggio non documentati. tvOS: 68 pt di altezza (irrilevante qui).
- **Regole.** Non nascondere la tab bar (salvo modali); non disattivare schede; poche schede; simboli pieni;
  badge solo per informazioni critiche.
- **Fonte.** [HIG Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars),
  [WWDC25 256 @1:22](https://developer.apple.com/videos/play/wwdc2025/256/?time=82),
  [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/) (O). **D+W**.

#### T2 · Bolla di selezione
- **Comportamento.** In qualunque momento una **pressione lunga** sulla tab bar trasforma l'evidenziazione
  in una **bolla di vetro** con lente e **aberrazione cromatica**; tenendo premuto si **trascina il dito**
  lungo la barra e si vede il contenuto deformato dentro la bolla; al rilascio si cambia scheda. Sulla barra
  minimizzata, pressione lunga e trascinamento cambiano scheda senza espanderla.
- **API.** Nessuna: automatico nella tab bar di sistema (perso se si ricostruisce la barra a mano).
- **Misure.** Non documentate (serve video).
- **Fonte.** [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/). **O**.

#### T3 · Minimizzazione allo scorrimento
- **Comportamento.** Scorrendo verso il basso la barra **si rimpicciolisce alla sola scheda corrente** (un
  tondo); l'accessorio (T4) scivola **in linea** accanto; il tasto di ricerca resta separato. Si riespande
  scorrendo nel verso opposto, tornando in cima o toccando la scheda. Solo iPhone.
- **API.** `.tabBarMinimizeBehavior(_:)` con `.automatic`, `.never`, `.onScrollDown`, `.onScrollUp`; UIKit
  `tabBarMinimizeBehavior`.
- **Misure.** Non documentate.
- **Fonte.** [TabBarMinimizeBehavior](https://developer.apple.com/documentation/swiftui/tabbarminimizebehavior),
  [HIG Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars),
  [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/) (O). **D**.

#### T4 · Accessorio in basso
- **Comportamento.** Vista persistente (il mini player di Musica) **sopra la tab bar**, con lo stesso aspetto;
  quando la barra si minimizza l'accessorio **scende e si mette in linea** con essa, con meno spazio
  (Musica nasconde alcuni controlli). Posizioni: `expanded` (sopra la barra, o in fondo se non c'è barra) e
  `inline`.
- **API.** `.tabViewBottomAccessory { … }`, `.tabViewBottomAccessory(isEnabled:content:)` (iOS 26.1),
  `@Environment(\.tabViewBottomAccessoryPlacement)` (`.inline`/`.expanded`, `nil` se indefinito); UIKit
  `bottomAccessory = UITabAccessory(contentView:)`, tratto `tabAccessoryEnvironment`.
- **Regole.** Solo funzioni persistenti; niente azioni di una schermata (un tasto «paga» va col contenuto).
- **Fonte.** [tabViewBottomAccessory](https://developer.apple.com/documentation/swiftui/view/tabviewbottomaccessory(content:)),
  [TabViewBottomAccessoryPlacement](https://developer.apple.com/documentation/swiftui/tabviewbottomaccessoryplacement),
  [WWDC25 284 @3:08](https://developer.apple.com/videos/play/wwdc2025/284/?time=188). **D**.

#### T5 · Scheda di ricerca
- **Comportamento.** La scheda col ruolo di ricerca è **separata dalle altre, in fondo a destra**, come tondo
  di vetro a sé. Toccandola **si espande in un campo di ricerca e le altre schede collassano**; il campo
  **prende il posto della tab bar** e si vede il contenuto della scheda (suggerimenti). Due stili (HIG
  giugno 2026): **scheda standard** (pagina di ricerca con il campo in alto, per esplorare) e **aspetto a
  tasto** (fuoco immediato sul campo e tastiera, uscendo si torna alla scheda precedente). Su iPad il campo
  appare centrato sopra i suggerimenti.
- **API.** `Tab(role: .search) { … }` + `.searchable(text:)` sulla `TabView`;
  `.tabViewSearchActivation(.searchTabSelection)` (selezione = attivazione); UIKit `UISearchTab`,
  `automaticallyActivatesSearch` (attiva il campo e al termine torna alla scheda precedente).
- **Fonte.** [TabRole.search](https://developer.apple.com/documentation/swiftui/tabrole/search),
  [WWDC25 323 @13:17](https://developer.apple.com/videos/play/wwdc2025/323/?time=797),
  [WWDC25 284 @17:03](https://developer.apple.com/videos/play/wwdc2025/284/?time=1023),
  [HIG Search fields](https://developer.apple.com/design/human-interface-guidelines/search-fields),
  [Ryan Ashcraft](https://ryanashcraft.com/ios-26-tab-bar-beef) (O: in Apple News il tondo si trasforma nel
  campo e le altre schede collassano in un solo tasto). **D+W**.

#### T6 · Scheda prominente (iOS 27)
- **Comportamento.** Una scheda qualsiasi (es. il carrello) può avere il trattamento separato in basso a
  destra; resta **sempre visibile** anche con la barra minimizzata. Se nessuna è prominente lo diventa la
  ricerca.
- **API.** `TabRole.prominent`; UIKit `prominentTabIdentifier`.
- **Fonte.** [TabRole.prominent](https://developer.apple.com/documentation/swiftui/tabrole/prominent),
  [WWDC26 278](https://developer.apple.com/videos/play/wwdc2026/278/). **D**.

#### T7 · Tablet: tab bar in alto e barra laterale
- **Comportamento.** Su iPad la tab bar sta **in alto**; con lo stile adattivo un tasto la converte in una
  **barra laterale di vetro flottante** (rientrata dai bordi, il contenuto le scorre sotto). Barra laterale e
  tab bar sono «un unico elemento di navigazione che scala col canvas». iOS 27: anche le app iPhone possono
  scegliere la barra laterale quando lo spazio è regolare.
- **API.** `.tabViewStyle(.sidebarAdaptable)`, `TabSection`, `tabViewSidebarHeader`; UIKit
  `UITabBarController.Mode.tabSidebar`.
- **Fonte.** [sidebarAdaptable](https://developer.apple.com/documentation/swiftui/tabviewstyle/sidebaradaptable),
  [WWDC25 219 @8:35](https://developer.apple.com/videos/play/wwdc2025/219/?time=515). **D**. Media per il Tab S9.

---

### 3.5 Ricerca

#### R1 · Ricerca automatica
- **Comportamento.** Il modificatore sulla navigazione mette il campo nella barra; su iPhone il sistema lo
  porta **in basso** senza cambiare codice; su iPad in alto a destra. Collocato sulla `NavigationSplitView`
  indica che la ricerca riguarda tutta l'app, su una colonna solo quella.
- **API.** `searchable(text:placement:prompt:)` e `searchable(text:isPresented:placement:prompt:)`;
  `SearchFieldPlacement.automatic`, `.toolbar`, `.toolbarPrincipal`, `.sidebar`,
  `.navigationBarDrawer(displayMode:)`; `DefaultToolbarItem(kind: .search, placement: .bottomBar)` per
  decidere dove va fra gli altri elementi; UIKit `searchBarPlacementBarButtonItem`,
  `searchBarPlacementAllowsToolbarIntegration`.
- **Fonte.** [Adding a search interface](https://developer.apple.com/documentation/swiftui/adding-a-search-interface-to-your-app),
  [WWDC25 256 @3:30](https://developer.apple.com/videos/play/wwdc2025/256/?time=210). **D+W**.

#### R2 · Ricerca nella barra in basso
- **Comportamento.** Campo su vetro proprio, a portata di pollice. **La larghezza si adatta al numero di
  elementi vicini** (uno a sinistra e uno a destra); con più di due elementi parte come tasto. Al tocco **sale
  animato sopra la tastiera** e appare un tasto Annulla. Alternativa: barra con la sola ricerca (Impostazioni).
- **API.** Automatico; `DefaultToolbarItem`; UIKit `.integrated`.
- **Regole.** In basso se c'è spazio e la ricerca conta; in alto se in basso c'è contenuto da non coprire
  (Wallet, Borsa con il foglio).
- **Fonte.** [WWDC26 292](https://developer.apple.com/videos/play/wwdc2026/292/),
  [HIG Search fields](https://developer.apple.com/design/human-interface-guidelines/search-fields). **W+D**.

#### R3 · Ricerca minimizzata
- **Comportamento.** Secondo dimensioni del dispositivo e numero di tasti il sistema può ridurre il campo a un
  **tasto** della barra (Mail); al tocco appare un **campo a tutta larghezza sopra la tastiera**. Si può
  chiederlo esplicitamente se la ricerca non è centrale.
- **API.** `.searchToolbarBehavior(.minimize)` (la pagina di esempio scrive `.minimized`: nome da verificare);
  UIKit `.integratedButton`.
- **Fonte.** [searchToolbarBehavior](https://developer.apple.com/documentation/swiftui/view/searchtoolbarbehavior(_:)),
  [WWDC25 323 @12:51](https://developer.apple.com/videos/play/wwdc2025/323/?time=771). **D+W**.

#### R4 · Ricerca in alto e in linea
- **Comportamento.** Nella barra in alto appare come tasto e al tocco diventa un campo **sopra la tastiera**,
  o in alto se in basso non c'è spazio. **In linea** sotto il titolo, nel contenuto: stile da contenuto (non
  vetro) e resta in alto da attivo; utile per cercare solo in quella vista (libreria di Musica). Il campo
  sceglie da solo lo stile: vetro nella barra, contenuto nello scorrimento.
- **API.** `.navigationBarDrawer(displayMode:)` (può nascondersi allo scorrimento).
- **Fonte.** [WWDC26 292](https://developer.apple.com/videos/play/wwdc2026/292/),
  [navigationBarDrawer](https://developer.apple.com/documentation/swiftui/searchfieldplacement/navigationbardrawer(displaymode:)). **W+D**.

#### R5 · Anatomia del campo, ambiti, token, suggerimenti
- **Comportamento.** Icona della lente a sinistra, segnaposto, tasto cancella quando c'è testo, **Annulla**
  quando ha il fuoco (esce e chiude la tastiera). Barra degli ambiti per filtri chiari; token come testo
  evidenziato nel campo; ricerche recenti in linea al fuoco; suggerimenti predittivi con la parte predetta
  distinta. iPad: in barra il campo si allarga per scrivere e sposta in un menu gli elementi che traboccano.
- **API.** `searchable`, `searchSuggestions(_:)`, `searchScopes(_:)`, token di `searchable`.
- **Fonte.** [WWDC26 292](https://developer.apple.com/videos/play/wwdc2026/292/),
  [HIG Search fields](https://developer.apple.com/design/human-interface-guidelines/search-fields). **W+D**.

---

### 3.6 Menu

#### N1 · Il menu che esce dal suo tasto
- **Comportamento.** «La bolla **si apre** e rivela il contenuto»: transizione in linea che tiene tutto dove
  si è toccato e mostra il legame fra tasto e contenuto. Crescendo il vetro diventa **più spesso** (M7). Da un
  tasto di vetro il tasto **si trasforma nella presentazione** (menu o popover), con continuità visiva per
  tutta l'animazione; i menu lo fanno in automatico. Fasi osservate nei frame (riferimenti.md, `161313→161526`):
  le altre icone del gruppo si sfocano e spariscono, la capsula si stringe in una goccia, la goccia sale e
  cresce con un **contorno irregolare**, le voci compaiono già dentro **deformate dalla lente** e sfocate, la
  sagoma si assesta in un rettangolo dagli angoli continui, il materiale diventa più smerigliato. In chiusura
  il percorso inverso (De).
- **API.** `Menu { … } label: { … }`; UIKit `UIButton.menu`, `UIBarButtonItem.menu`.
- **Misure.** Non documentate (R4).
- **Fonte.** [WWDC25 219 @5:05](https://developer.apple.com/videos/play/wwdc2025/219/?time=305),
  [WWDC25 284 @13:55](https://developer.apple.com/videos/play/wwdc2025/284/?time=835). **W+O**.

#### N2 · Il tasto di una barra che trasforma l'intera barra nel menu
- **Comportamento.** Quando il tasto sta in un gruppo con sfondo condiviso, è **tutto il gruppo** a
  trasformarsi nel menu (le altre icone spariscono sfocandosi), non il solo tasto. Il popover ancorato a un
  elemento della barra **anima da quell'elemento e lo sostituisce** finché non si sceglie o si chiude.
- **API.** Automatico; `UIPopoverPresentationController.sourceItem`.
- **Fonte.** frame `161313→161526` (O, in riferimenti.md);
  [sourceItem](https://developer.apple.com/documentation/uikit/uipopoverpresentationcontroller/sourceitem) (D).
  **O+D**.

#### N3 · Struttura del menu
- **Comportamento.** Aspetto di vetro, più arrotondato e spaziato; **icone sempre sul bordo iniziale**
  (sinistra), stesse API su tutte le piattaforme. Tre layout: **piccolo** (fila di 4 simboli senza etichetta
  in cima), **medio** (fila di 3 con etichetta corta), **grande** (lista, di serie). Sezioni con separatori,
  sottomenu indicati da un chevron, voci con sottotitolo (secondo `Text`), interruttori e selettori in menu,
  voci distruttive in rosso.
- **API.** `Menu`, `Section`, `Button(role: .destructive)`, `Picker` in menu, `Toggle`;
  `UIMenu.preferredElementSize = .small/.medium/.large`; `menuIndicator(_:)`. iOS 27:
  `preferredImageVisibility` (le immagini delle voci non sempre visibili).
- **Regole.** Icone per tutte le voci di un gruppo o per nessuna; un livello di sottomenu; sottomenu oltre ~5
  voci → menu a parte; voci frequenti in alto; etichette con verbo, maiuscole da titolo in inglese; ellissi se
  serve altro input.
- **Misure.** Non documentate.
- **Fonte.** [HIG Menus](https://developer.apple.com/design/human-interface-guidelines/menus),
  [Menu](https://developer.apple.com/documentation/swiftui/menu),
  [WWDC25 323 @16:32](https://developer.apple.com/videos/play/wwdc2025/323/?time=992). **D**.

#### N4 · Ordine delle voci e chiusura
- **Comportamento.** Ordine **priority**: le prime voci stanno **più vicine al punto di interazione** (il
  menu si capovolge se si apre verso l'alto); **fixed**: sempre dall'alto in basso. Su iOS `automatic` = fixed
  dentro contenuto scorrevole e nei picker a menu, priority altrove. Il menu di serie si chiude dopo
  un'azione; si può tenerlo aperto (es. +/− della dimensione del carattere).
- **API.** `.menuOrder(.automatic/.priority/.fixed)`, `.menuActionDismissBehavior(.disabled)`.
- **Fonte.** [menuOrder](https://developer.apple.com/documentation/swiftui/view/menuorder(_:)),
  [menuActionDismissBehavior](https://developer.apple.com/documentation/swiftui/view/menuactiondismissbehavior(_:)). **D**.

#### N5 · Azione principale del menu
- **Comportamento.** Toccando il corpo del controllo si esegue l'azione principale; il menu appare con un
  gesto secondario (**pressione lunga**).
- **API.** `Menu(content:label:primaryAction:)`; UIKit `UIButton` con `primaryAction` e `menu`.
- **Fonte.** [Menu › Primary action](https://developer.apple.com/documentation/swiftui/menu). **D**.

#### N6 · Selezione trascinando il dito
- **Comportamento.** Premere il tasto, **scorrere sulla voce senza staccare** e rilasciare per sceglierla.
  È un comportamento di UIKit da iOS 14; nessuna fonte trovata lo descrive per iOS 26 né dice se la voce sotto
  il dito diventa una lente.
- **Fonte.** **De** (non verificato).

#### N7 · Menu di modifica del testo
- **Comportamento.** Cinque voci in cima (Taglia/Copia/Incolla, Sostituisci, Writing Tools, AutoFill); la
  freccia apre un **menu completo** invece di sfogliare pagine.
- **API.** `UIEditMenuInteraction`. **Fonte.** [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/). **O**.

---

### 3.7 Menu contestuali da pressione lunga

#### X1 · Il menu contestuale
- **Cos'è.** Accesso alle funzioni legate a un elemento, nascosto finché non si tiene premuto.
- **Comportamento.** Toccando e tenendo premuto, il sistema **anima l'anteprima che emerge dal contenuto**
  (l'elemento si solleva), **scurisce lo schermo dietro anteprima e menu** e mostra il menu accanto al
  contenuto. Si sceglie una voce, oppure si **tocca l'anteprima per aprirla** o la si **trascina** altrove. Si
  chiude con una scelta o toccando fuori. Il menu ha l'aspetto di vetro e icone a sinistra (N3).
- **API.** `contextMenu(menuItems:)`, `contextMenu(menuItems:preview:)`,
  `contextMenu(forSelectionType:menu:primaryAction:)` per liste e tabelle; UIKit `UIContextMenuInteraction`,
  `UIContextMenuConfiguration`, `UITargetedPreview`, `UIPreviewParameters` (`visiblePath`, `shadowPath`,
  `backgroundColor`).
- **Misure.** Ritardo di attivazione, scala del sollevamento e durate non documentati.
- **Fonte.** [contextMenu](https://developer.apple.com/documentation/swiftui/view/contextmenu(menuitems:preview:)),
  [HIG Context menus](https://developer.apple.com/design/human-interface-guidelines/context-menus). **D**.

#### X2 · Lo sfondo: scurito, non più sfocato
- **Comportamento.** L'HIG dice che lo schermo dietro viene **scurito**. In iOS 26 **lo sfondo intorno al
  menu contestuale non è più sfocato** (MacStories: «tap and hold to invoke any kind of context menu…»);
  Mac Observer e il forum Apple lo riportano per Messaggi, dove anche lo scurimento sembra sparito. Ipotesi
  dei recensori: menu più grandi e materiale bastano a staccarlo.
- **Decisione per Alessio**: lui ha chiesto pagina **sfocata e scurita** (iOS 18). Apple oggi fa solo
  scurito, o nemmeno quello in Messaggi.
- **Fonte.** [HIG Context menus](https://developer.apple.com/design/human-interface-guidelines/context-menus) (D),
  [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/),
  [Mac Observer](https://www.macobserver.com/news/ios-26-removes-blurry-background-in-messages-here-are-the-best-alternatives/) (O). **D+O**.

#### X3 · Anteprima personalizzata
- **Comportamento.** Un'anteprima diversa dall'elemento (es. l'immagine di un luogo), dimensionata sul suo
  contenuto; durante la transizione si vede la «lift preview». Il **percorso di ritaglio deve coincidere**
  con la forma, così gli angoli non cambiano durante l'animazione.
- **API.** `preview:` di `contextMenu`; `contentShape(.contextMenuPreview, shape)` (forma della preview),
  `.interaction` per la zona di attivazione; UIKit `previewForHighlightingMenu…`.
- **Fonte.** [contentShape kinds](https://developer.apple.com/documentation/swiftui/contentshapekinds/contextmenupreview),
  [HIG Context menus](https://developer.apple.com/design/human-interface-guidelines/context-menus). **D**.

#### X4 · Regole del contenuto
- **Regole.** Poche voci, al massimo ~3 gruppi; **un livello** di sottomenu; voci non disponibili
  **nascoste**, non attenuate; le più usate dove il dito le incontra prima (ordine invertito se il menu si
  apre sopra); distruttive **in fondo** e in rosso; titolo solo se chiarisce (es. numero di elementi
  selezionati); stesse azioni anche nell'interfaccia principale; le prime voci uguali alle azioni a
  scorrimento; menu contestuale **o** menu di modifica, non entrambi.
- **Fonte.** [HIG Context menus](https://developer.apple.com/design/human-interface-guidelines/context-menus),
  [Adopting Liquid Glass › Menus](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass). **D**.

---

### 3.8 Fogli, avvisi, conferme, popover

#### P1 · Il foglio di vetro
- **Comportamento.** A **altezza parziale** il foglio è **rientrato dai bordi** con sfondo di vetro; il
  contenuto sotto si intravede fra foglio e bordo. **A piccole altezze gli angoli inferiori rientrano**
  annidandosi nelle curve dello schermo (concentrici). Passando a **tutta altezza** lo sfondo **diventa
  gradualmente opaco** e si **ancora ai bordi**. Trascinandolo in su il vetro «recede», diventa più opaco e
  **cresce leggermente** (segnale di impegno più profondo). Raggio degli angoli aumentato. Esempio
  osservabile: il foglio di condivisione.
- **API.** `sheet(isPresented:onDismiss:content:)`, `sheet(item:)`; UIKit `UISheetPresentationController`
  (`preferredCornerRadius`, `prefersEdgeAttachedInCompactHeight`, `widthFollowsPreferredContentSizeWhenEdgeAttached`),
  `Detent.backgroundEffect` (iOS 26.1).
- **Misure.** Raggio, rientro e soglia dell'opacità non documentati.
- **Regole.** Togliere `presentationBackground` personalizzati; controllare il contenuto vicino agli angoli
  più tondi e quello che si vede ai lati; un foglio alla volta.
- **Fonte.** [WWDC25 323 @6:20](https://developer.apple.com/videos/play/wwdc2025/323/?time=380),
  [WWDC25 356, capitolo «Structure»](https://developer.apple.com/videos/play/wwdc2025/356/?time=376),
  [Adopting Liquid Glass › Windows and modals](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass). **W**.

#### P2 · Detent, maniglia, chiusura
- **Comportamento.** I **detent** sono altezze di riposo: `large` (tutta altezza, di serie) e `medium` (circa
  metà; inattivo in altezza compatta); detent personalizzati per frazione o altezza. Il foglio si espande
  trascinando la **maniglia** o scorrendo il contenuto (disattivabile); toccare la maniglia scorre i detent.
  Si chiude **trascinando giù**; con modifiche non salvate si chiede conferma con un action sheet. Fogli non
  modali: si lavora sotto (Note, formattazione).
- **API.** `presentationDetents(_:)` con `.medium`, `.large`, `.fraction(_)`, `.height(_)`, `.custom(_)`;
  `presentationDragIndicator(_:)`; `presentationBackgroundInteraction(.enabled(upThrough:))`;
  `presentationCompactAdaptation(_:)`; `presentationSizing(_:)`; UIKit `detents`, `selectedDetentIdentifier`,
  `largestUndimmedDetentIdentifier`, `prefersScrollingExpandsWhenScrolledToEdge`, `prefersGrabberVisible`,
  `animateChanges(_:)`.
- **Regole.** Medium per la rivelazione progressiva (condividi); solo tutta altezza per comporre (Mail);
  Annulla a sinistra e Fatto a destra; non mostrare insieme Annulla, Fatto e Indietro.
- **Fonte.** [presentationDetents](https://developer.apple.com/documentation/swiftui/view/presentationdetents(_:)),
  [UISheetPresentationController](https://developer.apple.com/documentation/uikit/uisheetpresentationcontroller),
  [HIG Sheets](https://developer.apple.com/design/human-interface-guidelines/sheets). **D**.

#### P3 · Il foglio che nasce dal tasto
- **Comportamento.** Il contenuto del foglio **si trasforma uscendo dal tasto** che lo presenta (zoom);
  stesso continuo visivo dei menu. Va dichiarato: il tasto è la sorgente, il contenuto la destinazione.
- **API.** `ToolbarItem { … }.matchedTransitionSource(id:in:)` (iOS 26) + nel contenuto
  `.navigationTransition(.zoom(sourceID:in:))`; UIKit `preferredTransition = .zoom { _ in barButtonItem }`.
- **Fonte.** [matchedTransitionSource (ToolbarContent)](https://developer.apple.com/documentation/swiftui/toolbarcontent/matchedtransitionsource(id:in:)),
  [WWDC25 323 @7:10](https://developer.apple.com/videos/play/wwdc2025/323/?time=430),
  [WWDC25 284 @14:07](https://developer.apple.com/videos/play/wwdc2025/284/?time=847). **D+W**.

#### P4 · Avviso
- **Comportamento.** Menu, avvisi e popover «escono fluidamente dai controlli di vetro»; i dialoghi **si
  trasformano uscendo dal tasto** che li presenta, in automatico. Tipografia più grassetto e **allineata a
  sinistra** negli avvisi. Titolo, testo facoltativo, **fino a tre tasti**, campo di testo facoltativo. Avvisi
  di vetro (UIKit li elenca fra i componenti rinnovati).
- **API.** `alert(_:isPresented:actions:message:)` (iOS 27: anche con binding a un elemento); UIKit
  `UIAlertController(.alert)`.
- **Regole.** Pochi avvisi, mai solo informativi né all'avvio; tasto predefinito a destra o in alto, Annulla a
  sinistra o in basso; stile distruttivo solo per azioni non volute.
- **Fonte.** [WWDC25 323 @7:27](https://developer.apple.com/videos/play/wwdc2025/323/?time=447),
  [WWDC25 356, capitolo «Design Language»](https://developer.apple.com/videos/play/wwdc2025/356/?time=126),
  [HIG Alerts](https://developer.apple.com/design/human-interface-guidelines/alerts). **D+W**.

#### P5 · Dialogo di conferma e action sheet dalla sorgente
- **Comportamento.** Prima compariva in fondo allo schermo; ora **scaturisce dall'azione stessa** e appare
  **direttamente sopra l'elemento** che l'ha aperto, anche su iPhone. In linea **non ha il tasto Annulla**:
  si annulla toccando altrove. Mentre è attivo si può interagire col resto dell'interfaccia. Senza sorgente
  appare **centrato** e con Annulla. In larghezza regolare diventa un popover. Il sistema può riordinare i
  tasti per ruolo e prominenza; distruttive in alto.
- **API.** `confirmationDialog(_:isPresented:titleVisibility:actions:message:)` (iOS 27: binding a un
  elemento); UIKit `UIAlertController(.actionSheet)` con `popoverPresentationController.sourceItem` o
  `sourceView` (impostarli anche su iPhone).
- **Regole.** Action sheet, non menu, per scelte legate a un'azione voluta; niente scorrimento.
- **Fonte.** [WWDC25 284 @14:46](https://developer.apple.com/videos/play/wwdc2025/284/?time=886),
  [WWDC25 356, capitolo «Structure»](https://developer.apple.com/videos/play/wwdc2025/356/?time=376),
  [Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass),
  [confirmationDialog](https://developer.apple.com/documentation/swiftui/view/confirmationdialog(_:ispresented:titlevisibility:actions:)). **D+W**.

#### P6 · Popover
- **Comportamento.** Dal tasto della barra **anima da quell'elemento e lo sostituisce** fino a scelta o
  chiusura (in iOS 18 indicava l'elemento con la freccia). Su iPhone si adatta in foglio (in orizzontale a
  tutto schermo) salvo diversa richiesta.
- **API.** `popover(isPresented:attachmentAnchor:arrowEdge:content:)`,
  `presentationCompactAdaptation(.popover)`; UIKit `sourceItem`.
- **Regole.** Uno alla volta; niente popover sopra popover; evitarli in compatto; animare i cambi di misura.
- **Fonte.** [sourceItem](https://developer.apple.com/documentation/uikit/uipopoverpresentationcontroller/sourceitem),
  [HIG Popovers](https://developer.apple.com/design/human-interface-guidelines/popovers). **D**.

#### P7 · Modalità e oscuramento
- **Comportamento.** Quando un compito **interrompe** il flusso, vetro **più strato di oscuramento** per
  centrare l'attenzione; quando avviene **in parallelo**, il vetro separa senza oscurare. Il controllo fino a
  quale detent non oscurare è esplicito.
- **API.** `largestUndimmedDetentIdentifier`, `presentationBackgroundInteraction`.
- **Fonte.** [WWDC25 356, capitolo «Structure»](https://developer.apple.com/videos/play/wwdc2025/356/?time=376). **W+D**.

---

### 3.9 Controlli

#### K1 · Tasti di vetro
- **Comportamento.** Stile `glass`: vetro adattato al contesto; `glassProminent`: vetro **tinto** col colore
  dell'app (simile a `borderedProminent`); `glass(_:)`: variante o tinta a scelta. Reagiscono al tocco come i
  tasti della barra (H3).
- **API.** `.buttonStyle(.glass)`, `.buttonStyle(.glassProminent)`, `.buttonStyle(.glass(.clear))`;
  `UIButton.Configuration.glass()`, `.prominentGlass()`, `.clearGlass()`, `.prominentClearGlass()`.
- **Regole.** Preferirli a vetro fatto a mano sui tasti; uno o due tasti prominenti per vista; distinguere la
  scelta preferita con lo stile, non con la taglia.
- **Fonte.** [glass](https://developer.apple.com/documentation/swiftui/primitivebuttonstyle/glass),
  [glassProminent](https://developer.apple.com/documentation/swiftui/primitivebuttonstyle/glassprominent),
  [glass()](https://developer.apple.com/documentation/uikit/uibutton/configuration-swift.struct/glass()). **D**.

#### K2 · Forma, taglie e larghezza dei controlli
- **Comportamento.** I tasti con bordo sono **capsule** di serie. Altezze dei controlli aggiornate. Nuova
  taglia **extra large** per le azioni più importanti (la pagina di `ControlSize.extraLarge` dice ancora che
  fuori da visionOS vale `large`: contraddizione aperta). Larghezza: `fitted` o `flexible` (riempie lo spazio).
- **API.** `buttonBorderShape(_:)`, `controlSize(.mini/.small/.regular/.large/.extraLarge)`,
  `buttonSizing(.automatic/.fitted/.flexible)`, `labelIconToTitleSpacing(_:)` (iOS 26).
- **Misure.** Bersaglio minimo **44×44 pt**. Altezze nuove non pubblicate.
- **Fonte.** [WWDC25 323 @14:25–15:33](https://developer.apple.com/videos/play/wwdc2025/323/?time=865),
  [buttonSizing](https://developer.apple.com/documentation/swiftui/view/buttonsizing(_:)),
  [HIG Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons). **D+W**.

#### K3 · Interruttore
- **Comportamento.** Pomello **a pillola** (schiacciato e allungato, O). Durante l'interazione il pomello **si
  trasforma in vetro** (sale nel vetro), così si vede attraverso. Dimensioni leggermente cambiate.
- **API.** `Toggle`, `UISwitch`.
- **Misure.** Non documentate (frame 4:09–4:30 di 219).
- **Regole.** Interruttore solo nelle righe di lista; fuori da una lista un tasto che fa da interruttore;
  verde di serie, colore dell'app solo se serve.
- **Fonte.** [Adopting Liquid Glass › Controls](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass),
  [WWDC25 284 @17:24](https://developer.apple.com/videos/play/wwdc2025/284/?time=1044),
  [HIG Toggles](https://developer.apple.com/design/human-interface-guidelines/toggles). **D+W**.

#### K4 · Cursore
- **Comportamento.** Pomello a pillola; afferrato **diventa una bolla di vetro che cresce sotto il dito** e
  rifrange la traccia. **Conserva l'inerzia** (lanciato accelera e si ferma secondo la velocità) e **si
  allunga** quando si muove. **Tacche**: automatiche con `step`, o poste a mano; solo valori sulle tacche
  possibile. **Valore neutro**: il riempimento parte da un punto qualsiasi (velocità più lenta/più veloce).
  **Senza pomello**: sembra una barra di avanzamento quando non lo si tocca (media).
- **API.** `Slider(value:in:step:neutralValue:enabledBounds:label:currentValueLabel:minimumValueLabel:maximumValueLabel:ticks:onEditingChanged:)`,
  `SliderTick`, `SliderTickContentForEach`; UIKit `UISlider` con track configuration (`allowsTickValuesOnly`,
  numero di tacche), valore neutro, stile senza pomello (nomi dalla WWDC, firma esatta non verificata).
- **Misure.** Non documentate.
- **Regole.** Minimo a sinistra; non per il volume.
- **Fonte.** [WWDC25 284 @18:16–19:00](https://developer.apple.com/videos/play/wwdc2025/284/?time=1096),
  [WWDC25 323 @14:03](https://developer.apple.com/videos/play/wwdc2025/323/?time=843),
  [SliderTick](https://developer.apple.com/documentation/swiftui/slidertick),
  [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/) (O). **D+W+O**.

#### K5 · Controllo segmentato
- **Comportamento.** La selezione (il «pomello») diventa vetro durante l'interazione (video nella guida
  all'adozione). Trascinarla da un segmento all'altro: non documentato (De).
- **API.** `Picker(…).pickerStyle(.segmented)`, `UISegmentedControl`.
- **Regole.** Al massimo ~5 segmenti su iPhone; testo o immagini, non entrambi; per sottoviste correlate, non
  per sezioni dell'app.
- **Fonte.** [WWDC25 284 @17:24](https://developer.apple.com/videos/play/wwdc2025/284/?time=1044),
  [HIG Segmented controls](https://developer.apple.com/design/human-interface-guidelines/segmented-controls). **W**.

#### K6 · Stepper, selettori, data
- **Comportamento.** Nella lista dei controlli da rivedere per forma e misure; nessun comportamento nuovo
  documentato. Il selettore a menu apre un menu (N1).
- **API.** `Stepper`, `Picker` (`.menu`, `.wheel`, `.inline`), `DatePicker`.
- **Fonte.** [Adopting Liquid Glass › Controls](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass). **D**.

#### K7 · Campi di testo
- **Comportamento.** Nel contenuto restano senza vetro; il campo di ricerca nella barra prende il vetro.
  Tasto con indicatore di attività al posto dell'immagine per azioni lente.
- **Fonte.** [WWDC26 292](https://developer.apple.com/videos/play/wwdc2026/292/),
  [HIG Buttons](https://developer.apple.com/design/human-interface-guidelines/buttons). **W+D**.

---

### 3.10 Liste, moduli e contenuto

#### L1 · Lo strato del contenuto resta senza vetro
- **Regole.** Il contenuto (liste, sfondi) non usa il vetro: usa i **materiali standard** (ultra sottile,
  sottile, regolare, spesso) e i colori vibranti per etichette, riempimenti e separatori. A riposo (es.
  all'avvio) **evitare intersezioni** fra contenuto e vetro: spostare o scalare il contenuto. Il colore
  dell'app va nel contenuto; le immagini a tutto schermo vanno estese sotto barre e tab bar.
- **Fonte.** [HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials),
  [WWDC25 219 @17:54](https://developer.apple.com/videos/play/wwdc2025/219/?time=1074),
  [HIG Layout](https://developer.apple.com/design/human-interface-guidelines/layout). **D+W**.

#### L2 · Liste e moduli
- **Comportamento.** Righe **più alte** e più padding; sezioni con **raggio maggiore** (come i controlli);
  gruppi un po' più distanziati e rientrati; intestazioni di sezione **non più tutte maiuscole** (maiuscole da
  titolo). Icone a sinistra nelle righe.
- **API.** `List`, `Form` con `.grouped`, `listSectionMargins(_:_:)` (iOS 26), `listSectionSpacing(_:)`,
  `sectionActions { }`, `listRowSeparator`.
- **Misure.** Non documentate. Testi: principale 17 pt, secondario 15 pt (O, Learn UI Design).
- **Fonte.** [Adopting Liquid Glass › Organization and layout](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass),
  [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/) (O). **D+O**.

#### L3 · Azioni a scorrimento
- **Comportamento.** Azioni sulla riga scorrendola; le prime voci del menu contestuale devono coincidere. iOS
  27: su qualsiasi vista, con un contenitore che ne apre una alla volta e le chiude allo scorrimento.
- **API.** `swipeActions(edge:allowsFullSwipe:content:)`; iOS 27 `swipeActionsContainer()`.
- **Fonte.** [swipeActionsContainer](https://developer.apple.com/documentation/swiftui/view/swipeactionscontainer()). **D**.

---

### 3.11 Bordo dello scorrimento

#### E1 · L'effetto
- **Cos'è.** Separazione fra elementi flottanti e contenuto che scorre sotto, al posto delle barre con sfondo.
- **Comportamento.** Appena il contenuto comincia a scorrere sotto il vetro, l'effetto **dissolve
  delicatamente il contenuto nello sfondo**, sollevando il vetro e tenendo chiari i titoli flottanti. È
  adattivo come il vetro: quando sotto scorre **contenuto scuro** e il vetro passa allo stile scuro, l'effetto
  applica invece **un leggero scurimento**. Appare **solo quando** un elemento fissato si sovrappone al
  contenuto scorrevole, ai due capi nella direzione di scorrimento. «Non è decorativo: non blocca e non
  scurisce come un overlay.»
- **API.** `scrollEdgeEffectStyle(_ style: ScrollEdgeEffectStyle?, for edges: Edge.Set)`,
  `scrollEdgeEffectHidden(_:for:)`; UIKit `scrollView.topEdgeEffect` / `bottomEdgeEffect` /
  `leftEdgeEffect` / `rightEdgeEffect` con `style` e `isHidden`.
- **Misure.** Altezza della zona e curva della sfumatura non documentate. In alto **sfuma e sfoca**, in basso
  sotto la tab bar **sfuma senza sfocare** (O, Learn UI Design); l'effetto arriva fino al fondo della barra
  di navigazione.
- **Fonte.** [WWDC25 219 @8:52](https://developer.apple.com/videos/play/wwdc2025/219/?time=532),
  [ScrollEdgeEffectStyle](https://developer.apple.com/documentation/swiftui/scrolledgeeffectstyle),
  [HIG Scroll views](https://developer.apple.com/design/human-interface-guidelines/scroll-views),
  [Learn UI Design](https://learnui.design/blog/ios-design-guidelines-templates.html) (O). **D+W**.

#### E2 · Soft, hard, automatico
- **Comportamento.** **Soft**: confine sottile e sfocato, quello di serie su iOS, per elementi interattivi di
  vetro. **Hard**: confine **lineare, quasi opaco**, applicato **uniformemente** su tutta l'altezza della barra
  e della vista accessoria fissata (intestazioni di colonna, tabelle con intestazione fissa, testo fuori dal
  vetro, UI dense come Calendario); aspetto simile alle barre di iOS 18. **Automatic**: lo sceglie il sistema.
- **iOS 27.** `.automatic` **non alterna più** soft e hard: ha un aspetto suo, più chiaro (Apple: una
  «barra uniforme» in alto quando il contenuto scorre sotto le barre flottanti). Chi aveva forzato `.soft`
  deve rivalutare: non coincide più con l'aspetto di sistema.
- **Regole.** Un solo effetto per vista; non mescolarli né sovrapporli; nelle viste divise altezze coerenti;
  togliere sfondi e scurimenti dietro gli elementi della barra.
- **Fonte.** [WWDC25 356, capitolo «Structure»](https://developer.apple.com/videos/play/wwdc2025/356/?time=376),
  [hard](https://developer.apple.com/documentation/swiftui/scrolledgeeffectstyle/hard),
  [WWDC26 278](https://developer.apple.com/videos/play/wwdc2026/278/),
  [WWDC26 102](https://developer.apple.com/videos/play/wwdc2026/102/). **D+W**.

#### E3 · Barre personalizzate
- **Comportamento.** Una barra propria sopra un bordo della vista scorrevole si registra e ottiene effetto,
  area sicura e inset; ogni discendente (etichette, immagini, vetri, controlli) dà forma all'effetto.
- **API.** `safeAreaBar(edge:alignment:spacing:content:)` (anche per i bordi laterali; inserisce l'area
  sicura ed estende l'effetto); UIKit `UIScrollEdgeElementContainerInteraction` (`scrollView`, `edge`).
- **Fonte.** [safeAreaBar](https://developer.apple.com/documentation/swiftui/view/safeareabar(edge:alignment:spacing:content:)),
  [UIScrollEdgeElementContainerInteraction](https://developer.apple.com/documentation/uikit/uiscrolledgeelementcontainerinteraction). **D**.

#### E4 · Estensione dello sfondo
- **Comportamento.** La vista viene **duplicata in copie specchiate** intorno, su ogni bordo con area sicura
  disponibile, e coperta da una **sfocatura**: l'immagine sembra estendersi sotto barra laterale e ispettore
  restando centrata e intera. La vista viene ritagliata. Il testo sopra va aggiunto dopo il modificatore (o
  come fratello, in UIKit) per non estenderlo. Anche le viste a scorrimento orizzontale scorrono sotto la
  barra laterale.
- **API.** `backgroundExtensionEffect()`, `backgroundExtensionEffect(isEnabled:)`; UIKit
  `UIBackgroundExtensionView` (`contentView`, `automaticallyPlacesContentView`).
- **Regole.** Con discrezione, di solito una sola istanza.
- **Fonte.** [backgroundExtensionEffect](https://developer.apple.com/documentation/swiftui/view/backgroundextensioneffect()),
  [WWDC25 284 @5:51](https://developer.apple.com/videos/play/wwdc2025/284/?time=351). **D**.

---

### 3.12 Geometria

#### G1 · Concentricità
- **Comportamento.** Forme che si annidano allineando raggi e margini intorno a un centro comune, fino agli
  angoli del dispositivo e delle finestre. Tre tipi: **fissa** (raggio costante), **capsula** (raggio = metà
  dell'altezza), **concentrica** (raggio = raggio del genitore − distanza/margine). Se il calcolo dà zero
  l'angolo è squadrato; per evitarlo un **minimo** (anche «raggio di ripiego»: concentrico quando annidato,
  fisso da solo). Con `isUniform` si usa il raggio più grande su tutti gli angoli. Bottoni, campi, intestazioni
  e piè di barra standard sono già concentrici con gli angoli della barra.
- **API.** `ConcentricRectangle()` e varianti per angolo o lato; `.rect(corners: .concentric(minimum:), isUniform:)`;
  `Edge.Corner.Style.concentric / .concentric(minimum:) / .fixed(_)`; `containerShape(_ shape: some
  RoundedRectangularShape)`; protocollo `RoundedRectangularShape`; UIKit `cornerConfiguration` con
  `UICornerConfiguration.corners(radius: .containerConcentric(minimum:))`, `.capsule(maximumRadius:)`,
  `uniformEdges`, `uniformCorners`.
- **Regole.** Attenzione agli angoli «strozzati» o «svasati» (contenuti annidati come immagini in una card).
- **Fonte.** [ConcentricRectangle](https://developer.apple.com/documentation/swiftui/concentricrectangle),
  [concentric(minimum:)](https://developer.apple.com/documentation/swiftui/edge/corner/style/concentric(minimum:)),
  [UICornerConfiguration](https://developer.apple.com/documentation/uikit/uicornerconfiguration-swift.struct),
  [WWDC25 356, capitolo «Design Language»](https://developer.apple.com/videos/play/wwdc2025/356/?time=126). **D+W**.

#### G2 · Capsula e margine dai bordi
- **Regole.** Sul telefono, vicino al bordo dello schermo, **capsula con margine extra**; su tablet e Mac forma
  concentrica allineata al bordo della finestra. Le capsule portano focus e chiarezza nei layout touch. Le
  viste sono centrate matematicamente quando ha senso, spostate otticamente quando no.
- **Fonte.** [WWDC25 356, capitolo «Design Language»](https://developer.apple.com/videos/play/wwdc2025/356/?time=126). **W**.

#### G3 · Misure note
| misura | valore | grado |
|---|---|---|
| bersaglio minimo | 44×44 pt | D (HIG Buttons) |
| oscuramento sotto clear | nero 35% | D (HIG Materials) |
| gruppi nella barra | al massimo 3 | D (HIG Toolbars) |
| titolo della barra | sotto i 15 caratteri | D |
| segmenti su iPhone | al massimo ~5 | D |
| tasti in un avviso | fino a 3 | D |
| layout dei menu | 4 simboli (piccolo), 3 con etichetta (medio) | D |
| tab bar: distanza dai bordi | 21 pt | O (Learn UI Design) |
| etichette della tab bar | 11 pt | O |
| titolo grande / corpo | 34 pt grassetto / 17 pt | O |
| zona dell'indicatore Home | 21 pt di altezza | O |
| altezze di barre e controlli, raggi di fogli e sezioni, spaziature di serie, durate, molle | — | **non documentato** |

---

### 3.13 Tocco e movimento

#### H1 · Flessione e luce immediate
- **Comportamento.** Il vetro risponde all'interazione **flettendosi e accendendosi di luce all'istante**;
  ha una **flessibilità da gel** che si muove insieme all'interazione (capsula che cede sotto il dito, frame
  4:00–4:06). Al tocco col dito il movimento è più enfatico che col trackpad.
- **Fonte.** [WWDC25 219 @3:37](https://developer.apple.com/videos/play/wwdc2025/219/?time=217),
  [HIG Motion](https://developer.apple.com/design/human-interface-guidelines/motion). **W+D**.

#### H2 · Bagliore che si propaga
- **Comportamento.** Il materiale **si illumina da dentro**: il bagliore parte **sotto il polpastrello**, si
  diffonde nell'elemento e **sugli elementi di vetro vicini**, interagendo con la flessibilità.
- **Fonte.** [WWDC25 219 @12:02](https://developer.apple.com/videos/play/wwdc2025/219/?time=722). **W**.

#### H3 · Vetro interattivo
- **Comportamento.** Con `interactive` un elemento personalizzato reagisce come i tasti della barra e i
  cursori: **scala, rimbalza e luccica** (shimmer). Su iOS per controlli personalizzati o contenitori con
  elementi interattivi.
- **API.** `Glass.interactive(_ isEnabled: Bool = true)`, `UIGlassEffect.isInteractive`.
- **Misure.** Fattore di scala e molla non documentati (R4: frame 3:39–3:54).
- **Fonte.** [WWDC25 323 @19:21](https://developer.apple.com/videos/play/wwdc2025/323/?time=1161),
  [WWDC25 284 @23:03](https://developer.apple.com/videos/play/wwdc2025/284/?time=1383). **D+W**.

#### H4 · Salire nel vetro
- **Comportamento.** Elementi che **diventano vetro solo mentre li si tocca**: a riposo l'interfaccia resta
  quieta, al tocco prende vita; la lente trasparente permette di vedere il valore sotto (interruttore,
  cursore, segmentato, bolla della tab bar, lente del testo).
- **Fonte.** [WWDC25 219 @4:07](https://developer.apple.com/videos/play/wwdc2025/219/?time=247),
  [HIG Materials](https://developer.apple.com/design/human-interface-guidelines/materials). **W+D**.

#### H5 · Molle e durate
- **Comportamento.** Apple non pubblica curve per il vetro. In SwiftUI le trasformazioni usano l'animazione
  passata a `withAnimation` (di serie: la molla di sistema); con l'animazione di serie matchedGeometry
  aggiunge scala e spostamento al contenuto. Le transizioni si possono interrompere (V1).
- **Fonte.** [matchedGeometry](https://developer.apple.com/documentation/swiftui/glasseffecttransition/matchedgeometry). **D** per il
  meccanismo; valori **non documentati**.

#### H6 · Aptica
- **Comportamento.** Nessuna fonte collega un'aptica al vetro (tocco, morph, menu, bolla della tab bar).
  Esiste l'API generica.
- **API.** `sensoryFeedback(_:trigger:)`, `UIFeedbackGenerator`.
- **Fonte.** [sensoryFeedback](https://developer.apple.com/documentation/swiftui/view/sensoryfeedback(_:trigger:)). **non documentato**.

---

### 3.14 Navigazione

#### V1 · Push e pop interrompibili
- **Comportamento.** Anche la transizione a scorrimento standard (non solo lo zoom di iOS 18) è **fluida e
  interrompibile**: si può tornare subito indietro, scorrere mentre si assesta, toccare indietro più volte di
  fila.
- **Fonte.** [WWDC25 284, capitolo «Navigation and toolbars»](https://developer.apple.com/videos/play/wwdc2025/284/?time=439),
  [WWDC25 243](https://developer.apple.com/videos/play/wwdc2025/243/). **W**.

#### V2 · Ritorno da tutto il contenuto
- **Comportamento.** Si torna indietro **scorrendo da qualsiasi punto del contenuto**, non solo dal bordo; il
  gesto cede alle interazioni concorrenti (le azioni a scorrimento lo bloccano, le zone non interattive no).
- **API.** `UINavigationController.interactiveContentPopGestureRecognizer` (iOS 26).
- **Fonte.** [interactiveContentPopGestureRecognizer](https://developer.apple.com/documentation/uikit/uinavigationcontroller/interactivecontentpopgesturerecognizer). **D**.

#### V3 · Elementi della barra che si trasformano nel push
- Vedi C6. **Fonte.** [WWDC25 256 @1:22](https://developer.apple.com/videos/play/wwdc2025/256/?time=82). **W**.

#### V4 · Transizione zoom
- **Comportamento.** La pagina o il foglio si apre **ingrandendo l'elemento sorgente**; sempre interattiva e
  interrompibile (iOS 18); in iOS 26 anche da un elemento della barra.
- **API.** `.matchedTransitionSource(id:in:)` sulla sorgente, `.navigationTransition(.zoom(sourceID:in:))`
  sulla destinazione.
- **Fonte.** [zoom(sourceID:in:)](https://developer.apple.com/documentation/swiftui/navigationtransition/zoom(sourceid:in:)). **D**.

#### V5 · Barra di navigazione che si ritira (iOS 27)
- **Comportamento.** La barra in alto scivola via scorrendo; il sistema decide quando, forzabile.
- **API.** `toolbarMinimizeBehavior(.onScrollDown, for: .navigationBar)` (nome dalla WWDC26, pagina non
  trovata); UIKit `barMinimizationBehavior` (`.always/.never`), `barMinimizationSafeAreaAdjustment`.
- **Fonte.** [WWDC26 278](https://developer.apple.com/videos/play/wwdc2026/278/),
  [WWDC26 269](https://developer.apple.com/videos/play/wwdc2026/269/?time=457). **W**.

#### V6 · Split view, barra laterale, ispettore
- **Comportamento.** Barra laterale di vetro flottante, rientrata, con il contenuto che le scorre sotto;
  ispettore con stratificazione più leggera; colonne che si mostrano e nascondono secondo lo spazio, con
  separatori trascinabili.
- **API.** `NavigationSplitView`, `inspector(isPresented:content:)`, `UISplitViewController` (colonna
  `.inspector`).
- **Fonte.** [WWDC25 323 @3:07](https://developer.apple.com/videos/play/wwdc2025/323/?time=187). **D+W**. Media (tablet).

---

### 3.15 Altro

- **O1 · Tastiera.** Toccato il campo di ricerca, il campo **scivola in su** con la tastiera (da verificare
  che si muova come nelle app di sistema); la tab bar sparisce con la tastiera (O). Il vetro non può
  rifrangere la tastiera, che è un'altra finestra (limite già noto su Android). Fonte:
  [Adopting Liquid Glass › Search](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass). **D+O**.
- **O2 · Lente del testo.** La lente di selezione è una **bolla di vetro** che entra rimbalzando e ingrandisce
  e deforma il testo ai bordi. [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/). **O**.
- **O3 · Foglio di condivisione.** Foglio di vetro rientrato a metà altezza, opaco a tutta altezza; azioni
  preferite in fila, le altre in lista con icone a sinistra. [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/). **O**.
- **O4 · Schermata di blocco.** L'orologio usa la **stessa tecnica di tinta** del vetro; a blocco e sblocco
  le luci si muovono e la luce corre sul bordo. Insegna: la tinta è un materiale, non un colore pieno; la luce
  può essere animata. [WWDC25 219](https://developer.apple.com/videos/play/wwdc2025/219/?time=976). **W**.
- **O5 · Icone a strati.** Strati con riflesso, rifrazione, ombra, sfocatura e luci speculari che seguono il
  movimento; varianti chiara, scura, clear, colorata. iOS 27: icone più nitide, più strati di vetro.
  [Adopting Liquid Glass › App icons](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass). **D**. Fuori
  dall'engine.
- **O6 · Player video.** Controlli in tasti di vetro che rifrangono il video. [MacStories](https://www.macstories.net/stories/ios-and-ipados-26-the-macstories-review/3/). **O**.
- **Notifiche, Centro di controllo:** nessuna fonte con comportamenti utili al materiale oltre a quanto sopra.

---

## 4. Mappa delle API

Solo la forma che una libreria Compose dovrebbe offrire perché chi scrive abbia la stessa esperienza. Nomi
indicativi, in stile Compose; nessuna scelta di implementazione.

| API Apple | cosa dovrebbe offrire la libreria Compose |
|---|---|
| `glassEffect(_:in:)`, `Glass` (`.regular/.clear/.identity`, `.tint`, `.interactive`) | `Modifier.glassEffect(glass = Glass.Regular, shape = CapsuleShape)`; valore `Glass` immutabile con `.tint(color)` e `.interactive()`; contenuto reso vibrante in automatico |
| `GlassEffectContainer(spacing:)` | `GlassEffectContainer(spacing: Dp? = null) { … }`: tutti i vetri dentro resi insieme, fusione per vicinanza |
| `glassEffectID(_:in:)`, `@Namespace` | `rememberGlassNamespace()` + `Modifier.glassEffectId(id, namespace)`; trasformazione automatica quando lo stato cambia dentro un'animazione |
| `glassEffectTransition(.matchedGeometry/.materialize/.identity)` | `Modifier.glassEffectTransition(GlassTransition.MatchedGeometry…)`, con matchedGeometry di serie entro la spaziatura |
| `glassEffectUnion(id:namespace:)` | `Modifier.glassEffectUnion(id, namespace)` |
| `.buttonStyle(.glass / .glassProminent / .glass(_:))` | `Button(style = ButtonStyle.Glass / GlassProminent / Glass(glass))` o un `LocalButtonStyle` impostabile su un sottoalbero |
| `controlSize`, `buttonSizing`, `buttonBorderShape` | valori di ambiente (`CompositionLocal`) impostabili su un sottoalbero, letti da tutti i controlli |
| `.toolbar { ToolbarItem(placement:); ToolbarItemGroup; ToolbarSpacer(.fixed/.flexible) }` | uno scope dichiarativo `toolbar { item(placement) { }; group { }; spacer(Fixed) }` per pagina; il sistema raggruppa, dà il vetro e trasforma fra pagine e stati |
| `sharedBackgroundVisibility`, `badge`, `matchedTransitionSource` su `ToolbarContent` | modificatori sugli elementi dello scope della barra |
| `ToolbarItemPlacement` semantici (`primaryAction`, `confirmationAction`, `bottomBar`…) | posizioni semantiche che il sistema risolve (sinistra, destra, basso, menu di trabocco) |
| `ButtonRole.confirm/.close/.cancel/.destructive` | ruoli sui tasti, con la resa standard (spunta tinta, X, rosso) |
| `UIBarButtonItem.identifier` | un `id` stabile sugli elementi della barra per abbinarli fra pagine |
| `navigationTitle`, `navigationSubtitle`, `toolbarTitleDisplayMode` | titolo, sottotitolo e modo del titolo dichiarati dalla pagina; titolo grande che scorre col contenuto |
| `visibilityPriority`, `ToolbarOverflowMenu`, `.topBarPinnedTrailing` (27) | priorità degli elementi e menu di trabocco dichiarativo |
| `TabView { Tab(…) ; Tab(role: .search) }` | `TabView { tab(title, icon) { }; tab(role = TabRole.Search) { } }` con ruoli `Search`/`Prominent` |
| `tabBarMinimizeBehavior(_:)` | parametro o modificatore `minimizeBehavior = OnScrollDown/OnScrollUp/Never/Automatic`, legato in automatico allo scorrimento della scheda |
| `tabViewBottomAccessory { }` + `tabViewBottomAccessoryPlacement` | slot `bottomAccessory { placement -> }` e un `Local…Placement` (`Inline`/`Expanded`) |
| `tabViewSearchActivation`, `UISearchTab.automaticallyActivatesSearch` | opzione sulla scheda di ricerca: attivazione al tocco e ritorno alla scheda precedente |
| `.sidebarAdaptable` | stile della tab view che su tablet diventa barra laterale |
| `searchable(text:placement:prompt:)`, `searchToolbarBehavior`, `DefaultToolbarItem(kind: .search)` | `Modifier.searchable(state, placement = Automatic, prompt)` sulla pagina o sull'host; comportamento `Minimize`; elemento «ricerca» collocabile nella barra |
| `searchSuggestions`, `searchScopes`, token | slot per suggerimenti, ambiti e token del campo |
| `Menu { } label: { } primaryAction: { }` | `Menu(label, primaryAction?) { item(); section { }; submenu { } }` che si apre dal suo tasto |
| `menuOrder`, `menuActionDismissBehavior`, `preferredElementSize` | parametri del menu: ordine priority/fixed, chiusura dopo l'azione, layout piccolo/medio/grande |
| `contextMenu(menuItems:preview:)`, `contentShape(.contextMenuPreview,_)` | `Modifier.contextMenu(preview = null) { … }` con forma dell'anteprima |
| `sheet(isPresented:)`, `presentationDetents`, `presentationDragIndicator`, `presentationBackgroundInteraction` | `Sheet(visible, detents = setOf(Medium, Large), dragIndicator, undimmedUpTo)`: vetro rientrato ai detent parziali, opaco a tutta altezza |
| `navigationTransition(.zoom(sourceID:in:))` + `matchedTransitionSource(id:in:)` | sorgente e destinazione dichiarate con un id: fogli e pagine che nascono dall'elemento |
| `alert`, `confirmationDialog` (con sorgente) | dialoghi che escono dal tasto che li apre; il dialogo di conferma ancorato alla sorgente, senza Annulla in linea |
| `popover(isPresented:attachmentAnchor:)`, `presentationCompactAdaptation` | popover ancorato che sostituisce il tasto; adattamento in foglio sul telefono |
| `Toggle`, `Slider(…neutralValue:ticks:)`, `Picker(.segmented)`, `Stepper` | controlli con la «salita nel vetro» di serie; cursore con tacche, valore neutro, stile senza pomello |
| `List`, `Form`, `listSectionMargins`, `sectionActions`, `swipeActions` | lista con metriche nuove, azioni di sezione e a scorrimento |
| `scrollEdgeEffectStyle(_:for:)`, `scrollEdgeEffectHidden` | `Modifier.scrollEdgeEffectStyle(Soft/Hard/Automatic, edges)`, applicato in automatico sotto barre e tab bar |
| `safeAreaBar(edge:)`, `UIScrollEdgeElementContainerInteraction` | slot per barre personalizzate che ricevono area sicura e bordo dello scorrimento |
| `backgroundExtensionEffect()` | `Modifier.backgroundExtensionEffect()` |
| `ConcentricRectangle`, `.rect(corners: .concentric(minimum:))`, `containerShape(_:)` | forme `ConcentricRectangle(corners…)` che leggono la forma del contenitore da un `Local`, e `Modifier.containerShape(shape)` |
| `withAnimation` | un'animazione di sistema di serie per le trasformazioni del vetro, sostituibile |
| `accessibilityReduceTransparency/Motion`, contrasto | modificatori del materiale automatici, letti dalle impostazioni di sistema |
| `sensoryFeedback(_:trigger:)` | aptica dichiarativa legata a un valore |

---

## 5. Cosa non ho trovato

- **Numeri**: altezze di barre, tab bar e controlli; raggi di fogli, sezioni, menu; spaziatura di serie del
  contenitore e dello spaziatore fisso; soglie di cambio chiaro/scuro e di opacità del foglio; durate e
  molle di morph, materializzazione, rimbalzo; scala del tocco. Apple non li pubblica; restano a R4 (frame) o
  all'UI kit Figma di Apple (non consultato: richiede Figma).
- **Aptica** legata al vetro, alla bolla della tab bar, ai menu: nessuna fonte.
- **Menu contestuale in iOS 26**: confermata (da fonti secondarie) la sparizione della sfocatura; non chiaro
  se lo scurimento resti ovunque (in Messaggi pare di no). Serve un dispositivo iOS o un video.
- **Selezione col dito trascinato nei menu** e **trascinamento fra segmenti** del controllo segmentato in
  iOS 26: non documentati.
- **Bolla della tab bar**: descritta solo da recensioni; nessuna API, nessun numero.
- **Sottomenu** in iOS 26 (come si aprono dentro il menu): non documentato.
- **Abbinamento degli elementi della barra in SwiftUI** fra pagine: documentato solo per UIKit
  (`identifier`).
- Pagine non leggibili come JSON (404): `UISlider` track configuration e stile senza pomello,
  `UIBarButtonItem.badge`, `toolbarMinimizeBehavior` di SwiftUI (iOS 27), `barMinimizationBehavior`.
  I nomi vengono dalle trascrizioni WWDC.
- **Contraddizioni aperte**: `ControlSize.extraLarge` (doc: vale `large` fuori da visionOS; WWDC25 323:
  nuova taglia extra large); `searchToolbarBehavior` (`.minimize` nella pagina del tipo, `.minimized` nel
  codice d'esempio).
- La sessione «Elevate the design of your iPad app» (WWDC25 208) è stata scaricata ma non analizzata: copre
  finestre e barra dei menu dell'iPad, fuori dalle priorità.
