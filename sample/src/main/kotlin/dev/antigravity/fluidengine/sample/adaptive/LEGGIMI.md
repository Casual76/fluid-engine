# Adattività (F2.6, F2.3b)

La pagina dove si prova, e si riprende dal tablet, ciò che la fase F2 ha dato all'engine: il vetro che si adatta a ciò che
ha dietro (la sonda dello sfondo, il giro chiaro/scuro dei piccoli con l'inchiostro, il velo e l'ombra adattivi, il tasto
tinto, il cursore dell'utente), i grandi che scuriscono in continuo e il bordo dello scorrimento al posto della fascia in
cima alle schermate. Dal catalogo Chrome aprire **Adattività**, oppure da riga di comando.

Sette scene. Si cambia a mano col tasto «Scena», e ognuna si lancia da `adb` con `adaptive.scene`. In logcat
(`adb logcat -s FluidAdattivo`) la pagina scrive ciò che serve a chi misura (sotto, «Cosa scrive in logcat»).

Comandi da eseguire da chi possiede il tablet (provati sul Tab S9 in F2.3b, con le riprese in
`artifacts/liquid-glass/sessioni/f2-3b/`):

```powershell
$A = "dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity"
adb shell am start -S -W -n $A --es gallery.page adaptive
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene bianco-nero
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene bianco-nero --es adaptive.auto true
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene soglia --es adaptive.auto true
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene soglia --es adaptive.share 0.5
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene scacchiera --es adaptive.dark scuro --es adaptive.cell 32 --es adaptive.auto true
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene scorrimento --es adaptive.auto true
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene scorrimento --es adaptive.fullbar true --es adaptive.theme scuro
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene grandi --es adaptive.share 0.6
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene grandi --es adaptive.auto true --es adaptive.large popup
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene cursore --es adaptive.cursor 0
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene cursore --es adaptive.cursor 1
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene varianti
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene bianco-nero --es adaptive.model classic
```

(`$A` è una variabile di PowerShell; in `bash` va scritta `A=…` e `$A`.) Gli extra valgono solo nella build di debug, come
quelli delle altre pagine: chiudere l'attività prima di un nuovo lancio con extra diversi (`-S` la ferma e la riparte).

## Gli extra

| extra | valori | cosa fa |
|---|---|---|
| `gallery.page` | `adaptive` | apre la pagina (sopra Chrome: «Indietro» torna al catalogo) |
| `adaptive.scene` | `bianco-nero` (di serie), `soglia`, `scacchiera`, `scorrimento`, `grandi`, `cursore`, `varianti` | la scena |
| `adaptive.theme` | `chiaro`, `scuro` | il tema della pagina; di serie quello della galleria (`gallery.theme`) |
| `adaptive.model` | `classic`, `liquid` | il vetro che chiede la pagina; di serie quello della galleria (`gallery.model`) |
| `adaptive.auto` | `true` | la pagina si muove da sola (vedi ogni scena); senza, **non scrive niente** |
| `adaptive.tone` | `bianco`, `grigio`, `nero` | il fondo da cui parte la prima scena |
| `adaptive.share` | `0`–`1` | la quota di nero dal basso, per `soglia` e `grandi` |
| `adaptive.cursor` | `0`–`1` | il cursore dell'utente (`LocalFluidGlassPreference`), di serie `0.5` |
| `adaptive.dark` | `nero`, `scuro`, `grigio` | il tono dei riquadri scuri della scacchiera (nero puro, 25%, 40%) |
| `adaptive.cell` | `8`–`96` | il lato di una casella della scacchiera, in dp (di serie 32) |
| `adaptive.fullbar` | `true` | scorrimento: i riquadri scuri sono alti e riempiono la zona della barra |
| `adaptive.large` | `foglio` (di serie), `popup`, `pannello` | grandi: quale grande è aperto alla partenza, uno alla volta (F2.3b) |

Un valore che non si riconosce lascia quello di serie. Il tema e il modello cambiati dal tasto rifanno la scena da capo; il
fondo, la quota, il cursore, i riquadri e l'automatico no, perché la prova è proprio un vetro **fermo** sopra un fondo
che cambia: un vetro che rinascesse a ogni cambio ripartirebbe senza misura, e non proverebbe niente.

## Le scene

Tutte le scene sul palco (la prima, la seconda, la terza, la quinta, la sesta e la settima) mettono i vetri sopra un fondo
che la pagina disegna e registra da sé; lo scorrimento è una `FluidScreen` vera. In ogni scena i tasti in cima (Scena, Tema,
Vetro, Automatico e quelli propri della scena) sono anche loro vetro, e anche loro si adattano al fondo.

1. **`bianco-nero` · bianco → nero sotto un vetro fermo.** Un tasto tondo, una capsula con tre icone, un tasto tinto, un
   pannello fatto a mano dall'app (`vetro-app`: un `glassSurface` della taglia e del ruolo di un menu), la tab bar e il tasto
   che diventa menu (toccato, si apre), tutti fermi sopra un fondo che cambia colore senza muoversi: bianco, grigio medio,
   nero e ritorno. A mano col tasto «Fondo» (bianco → grigio → nero → bianco); con `adaptive.auto true` il fondo cambia ogni
   2 s nel giro bianco, grigio, nero, grigio, bianco. È la prova del giro chiaro/scuro e dell'inchiostro. **Girano i vetri
   il cui contenuto segue l'inchiostro** (il patto `GlassInk`): il tondo, la capsula, i tasti con l'etichetta, **la tab bar**
   (F2.3b: icone, etichette e pillola; l'accento della scheda scelta resta accento) e il tasto che diventa menu. Il tasto
   tinto e il vetro dell'app non girano: tengono il lato del tema, con il velo e l'ombra che seguono lo sfondo. Il tinto
   conserva la tonalità e muove la luminosità (piena su bianco, 58% su nero).
2. **`soglia` · la soglia.** Gli stessi vetri sopra un fondo bianco con una fascia nera che sale dal basso. Un cursore regola la
   quota (`adaptive.share`); con `adaptive.auto true` sale da 0 a 1 in 8 s e riscende in 8 s. Per vedere a che quota di
   scuro gira ogni vetro, e a quale torna: con le due soglie (0,45 per andare sullo scuro, 0,62 per tornare, sulla
   luminanza media) l'andata e il ritorno sono a quote diverse, e di proposito. Le righe `COPERTURA` dicono, per ogni
   vetro, quanto è sopra il nero.
3. **`scacchiera` · la scacchiera.** Un fondo che alterna riquadri chiari e scuri, vicino alla soglia: **il vetro non deve
   girare avanti e indietro**. Due tasti scelgono il tono dei riquadri scuri (nero, scuro 25%, grigio 40%: la media sotto il
   vetro sale da 0,5 verso la soglia di ritorno) e la casella (16, 32, 64 dp). Con `adaptive.auto true` il disegno si sposta
   ogni 600 ms di una frazione di casella (0,37), così la media sotto il vetro oscilla attorno alle soglie.
4. **`scorrimento` · scorrimento su foto e testo.** Una `FluidScreen` con barra coi tasti in alto e la tab bar della galleria in
   basso, e una lista lunga che alterna riquadri chiari, riquadri scuri, foto sature (arancio, blu) e testo. È la scena del
   **bordo dello scorrimento** (sotto `Liquid` e da Android 13 la fascia di vetro non c'è: il contenuto si scioglie nel colore
   del fondo sotto i tasti, in alto e in basso) e del giro mentre si scorre. Con `adaptive.fullbar true` i riquadri scuri sono
   alti 720 dp e riempiono tutta la zona della barra (il caso in cui la striscia scurisce e la barra gira); il tasto
   «Barra piena» della pagina lo accende e lo spegne. Con `adaptive.auto true` la lista scorre da sola 2400 dp in 8 s e
   torna in 8 s, a velocità costante. Provarla anche con `adaptive.model classic` (la fascia di sempre, in basso niente).
5. **`grandi` · i grandi.** I grandi veri dell'engine sopra un fondo bianco con una fascia nera che sale dal basso, con la
   quota regolabile (cursore, `adaptive.share`, o `adaptive.auto true` che la fa salire e scendere): un pop-up (il menu di
   `FluidGlassModalPortal`, che nasce dal suo tasto), un foglio (in fondo) e il pannello laterale (`FluidDrawer`), **uno
   alla volta**: ognuno porta il suo velo sulla pagina e il secondo coprirebbe il primo. Alla partenza è aperto quello di
   `adaptive.large` (di serie il foglio); i tasti «Foglio», «Pop-up» e «Pannello» passano dall'uno all'altro, e si chiudono
   toccando fuori. A destra il vetro dell'app (`vetro-app`, un `glassSurface`), visibile sotto il velo del grande aperto. **Cosa aspettarsi** (F2.3b): sul tema chiaro i
   grandi **scuriscono in continuo** con la quota di nero che hanno sotto, senza girare (R4 A6: da 143 a 48 su nero pieno in
   circa 1,5 s; a metà copertura il velo segue la quota), e il loro contenuto li segue: quando il lato continuo passa metà
   (0,56 salendo, 0,44 scendendo) il testo passa al chiaro, una volta, nel fotogramma in cui è trasparente, e il testo
   secondario e l'accento prendono il tema scuro. Sul tema scuro restano scuri. Il vetro dell'app resta del lato del tema:
   è il controesempio (vedi `vetro.md`, «Chi gira, chi scurisce e chi no»).
6. **`cursore` · il cursore dell'utente.** Una foto, i vetri di serie e, sotto, la riga di `fluidAppearanceSection`
   («Aspetto del vetro») collegata a `LocalFluidGlassPreference`: il cursore va da trasparente (0) a colorato (1), a metà è il
   vetro di serie al bit, e la riga si aggancia al centro. I due estremi e il centro sono anche tre tasti a portata di
   tocco in cima («Trasparente», «Di serie», «Colorato»).
7. **`varianti` · `Clear` e tasto tinto.** Il vetro di serie, il tasto tinto (`GlassTintMode.Colored`) e, accanto, il posto di
   `Clear`, sopra una foto chiara (in alto) e una scura (in basso). **`Clear` non si vede ancora**: il tipo `FluidGlass.Clear`
   e la ricetta con la variante limpida ci sono, ma nessun componente li posa (il modificatore `Modifier.fluidGlass`, che
   passa la variante al vetro, nasce in F3.2). Il riquadro tratteggiato dice dove andrà; la manopola di debug `cleardim`
   (`gallery.tune`) riguarda solo l'ombra che la ricetta darebbe a `Clear`, non la fa comparire.

## Le manopole di S3

Le scelte che restano ad Alessio (S3) si riprendono sul tablet con le manopole di debug di `gallery.tune` (l'elenco completo
è in `material/LEGGIMI.md`). Senza manopole tutto è di serie, al bit; valgono per i vetri e le sonde che nascono dopo, quindi
si danno al lancio insieme alla scena:

| manopola | valori | cosa cambia | dove si vede |
|---|---|---|---|
| `fliptime` | `ios26` (di serie) · `swift` · `slow` | i tempi del giro: corpo 300 / 200 / 450 ms, inchiostro pieno a 700 / 450 / 1000 ms | `bianco-nero` con `adaptive.auto true`; la barra in alto di `scorrimento` con `adaptive.fullbar true` |
| `flipwait` | `20`–`2000` ms (di serie 200) | quanto il filtro aspetta prima di decidere il lato (la costante di tempo della media) | come sopra |
| `flipon` · `flipoff` | `0`–`1` (di serie 0,45 e 0,62) | le due soglie del filtro: verso lo scuro, di ritorno al chiaro. `flipon` sotto `flipoff` | `soglia` con `adaptive.auto true`; la scheda Materiale sui riquadri colorati |
| `darkveilalpha` | `0`–`1` (di serie 0,5) | la profondità di un vetro piccolo girato sul nero (e del vetro scuro del tema scuro): sul tablet 0,5 / 0,39 / 0,29 danno 55 / 43 / 32. Sopra una foto scura il piccolo prende il velo dei grandi e la tab bar la sua pellicola: lì quasi non cambia (70–74) | `bianco-nero` sul nero, sui due temi |
| `darklargealpha` | `0`–`1` (di serie 0,5) | la profondità di un grande sopra il nero pieno (foglio, pop-up, pannello) | `grandi` con `adaptive.share 1` |
| `darkceiling` | `0`–`1` (di serie 0,40) | il soffitto del velo scuro dei grandi a copertura parziale | `grandi` con `adaptive.share` a metà |
| `edgedim` | `0`–`1` (di serie 0,2) | lo scurimento della striscia del bordo dello scorrimento sul lato scuro | `scorrimento` con `adaptive.fullbar true` |

```powershell
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene bianco-nero --es adaptive.auto true --es gallery.tune fliptime=swift,flipwait=100
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene soglia --es adaptive.auto true --es gallery.tune flipon=0.55,flipoff=0.7
adb shell am start -S -W -n $A --es gallery.page adaptive --es adaptive.scene grandi --es adaptive.share 1 --es gallery.tune darklargealpha=0.89
```

La riga `PROFILO` del tag `FluidMateriale` dice quali manopole l'engine ha preso e quali ha scartato.

## Cosa scrive in logcat

Tag `FluidAdattivo`. Una riga per cosa; i tempi sono `SystemClock.uptimeMillis()`, lo stesso orologio delle letture.

| riga | quando | cosa dice |
|---|---|---|
| `SCENA` | al lancio e a ogni cambio di scena, tema, vetro o variante | la scena, il tema, il vetro chiesto, l'API, se la sonda esiste, se l'automatico è acceso |
| `RIQUADRO nome x y w h` | 1,5 s dopo la scena | la posizione di ogni vetro nominato in pixel della finestra, e il lato in dp: chi ritaglia prende lo stesso punto a ogni fondo |
| `FONDO` | a ogni cambio del fondo (prima scena e scacchiera) | il tono (o i riquadri) e l'istante: da qui si conta il ritardo del giro |
| `COPERTURA quota=… nome=…` | a ogni passo del 5% della quota (soglia e grandi) | quanto di ogni vetro sta sopra la fascia nera, 0..1 |
| `LETTURA nome luminanza deviazione gradiente copertura lato lettura\|scadenza t` | a ogni lettura di una sonda | cosa ha letto la sonda sotto quel vetro e il lato che il filtro ha deciso; `lettura` è una lettura nuova, `scadenza` il giro del filtro dopo l'ultima lettura (stessi numeri) |
| `ASPETTO nome lato scurimento luminanza misurato` | a ogni cambio del lato del vetro, **visto da dentro** | il lato (`isDark`), quanto è scurito, la luminanza dello sfondo; «nessun vetro adattivo» dove il vetro non lo è (annidato, di prima, sotto Android 13) |
| `SONDA scena richieste letture fallite caselle rifiutate risvegli` | quando uno di questi cambia (controllo ogni 0,5 s) | i contatori di `FluidGlassProbeDiagnostics`: richieste al lettore, letture, fallimenti, picco di caselle occupate, caselle rifiutate, e perché le sonde si sono svegliate (revisione, riposizionamento, letture osservate, timer) |
| `SCORRIMENTO indice scarto blocco` | ogni 100 px di scorrimento (solo `scorrimento`) | dove sta la lista, e di che tipo è il riquadro in cima |
| `CURSORE valore` | a ogni passo di 0,05 | il valore del cursore (solo `cursore`) |

Due cose da sapere su `LETTURA` e `ASPETTO`:

- `LETTURA` viene da **una sonda in più**, che la pagina monta sul riquadro con lo stesso fondo (registrato una seconda volta
  come sorgente) e lo stesso filtro di quella del vetro. Dice il lato che il vetro **deciderebbe** a quel riquadro; non è
  lo stato interno del vetro. Lo stato interno lo dice `ASPETTO`, ma solo dove il contenuto del vetro lo può leggere: sul
  tondo, sul tasto tinto, sul vetro dell'app (`vetro-app`), sulla tab bar (`tabbar`, dalla prima icona), sul pop-up
  (`menu`), sul foglio (`foglio`) e sul pannello laterale (`pannello`). Sulla capsula c'è solo `LETTURA`. Per un grande
  `ASPETTO` esce quando l'inchiostro passa di lato (una volta per attraversamento della metà), con lo scurimento di quel
  momento; lo scurimento in continuo fra un passaggio e l'altro non scrive righe. Nello scorrimento non c'è nessuna delle
  due: la sonda della striscia sta dentro la barra.
- La seconda registrazione del fondo e le sonde in più sono **strumenti di misura**: costano un layer e qualche nodo in più.
  Questa pagina non è un esempio di come si monta il vetro in un'app.

## Cosa provare, cosa aspettarsi

I numeri sono ipotesi da tarare in S3 (la sessione sul tablet), non misure sul dispositivo.

1. Un vetro piccolo che segue l'inchiostro gira quando la media sotto di lui scende sotto 0,45 (e torna sopra 0,62): su
   bianco → nero a una quota si vede il corpo scendere in 300 ms circa e l'inchiostro attraversare il corpo, con **un
   fotogramma** a contrasto quasi zero. Tondo, capsula e tab bar girano; il tinto e il vetro dell'app no. I grandi (scena
   `grandi`) scuriscono senza girare e il loro testo passa al chiaro una volta sola, a metà strada.
2. Nessuna icona illeggibile per più di un fotogramma; nessuno scatto del corpo; nessuno sfarfallio sulla scacchiera.
3. Il tasto tinto resta del suo colore (la tonalità) su bianco e su nero: la luminosità segue lo sfondo (piena su bianco,
   58% su nero), con l'alone del colore del tasto e il filo chiaro.
4. Sullo scorrimento: in alto nessuna fascia e nessuna riga; il testo e le foto che passano sotto i tasti si sciolgono nel
   colore del fondo con un velo leggero e progressivo (tetto 0,36), che scurisce sopra un riquadro scuro che riempie la
   barra; in basso il contenuto sfuma senza sfocarsi sopra la tab bar. A riposo, in cima alla lista, la striscia non c'è.
5. Pagina ferma (senza `adaptive.auto`): in logcat dopo la prima misura non esce più niente di nuovo da `SONDA`; `dumpsys
   gfxinfo` non conta fotogrammi. Il test `GlassProbeQuietTest` lo prova sul computer (con Robolectric: l'immagine del
   lettore non arriva, quindi lì si prova solo la quiete degli stati).

## Da dove vengono i numeri

Il piano è `docs/liquid-glass/piano.md` (sezione F2); le misure e i rapporti: `artifacts/liquid-glass/bench/f2-1/` … `f2-4/` e
`artifacts/liquid-glass/sessioni/f2-5/`; la tab bar che gira e i grandi che scuriscono: `bench/f2-3b/contrasto.md` e
`artifacts/liquid-glass/sessioni/f2-3b/`. Le tre schede dell'engine che descrivono ciò che qui si guarda sono
`skill/fluid-engine/references/componenti/vetro.md` (sezione «Il vetro che si adatta»), `schermata.md` («Il bordo dello
scorrimento») e `fluid-physics.md`.
