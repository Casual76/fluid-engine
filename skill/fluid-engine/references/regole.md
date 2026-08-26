# Le regole, con il perché

Ognuna di queste risolve un modo concreto in cui l'interfaccia si rompe. Conoscere il perché serve a
sapere quando una regola non si applica, invece di aggirarla.

## Angoli continui, mai circolari

`ContinuousCornerShape`, non `RoundedCornerShape`.

Un angolo circolare unisce un lato dritto a un arco: la curvatura passa da zero a `1/r` in un punto
solo, e l'occhio legge quella discontinuità come una piega. Un angolo continuo fa salire e scendere
la curvatura lungo il lato, e non lascia giunzione. È metà del motivo per cui una card qui non
sembra una card Material — l'altra metà è la tipografia.

Conseguenza pratica: un angolo continuo si estende più a lungo lungo il lato, quindi a parità di
numero sembra più stretto. I raggi di `FluidRadius` sono già più grandi degli equivalenti Material
apposta. Non "correggerli" verso i valori Material.

```kotlin
Modifier.clip(ContinuousCornerShape(FluidRadius.Card))   // sì
Modifier.clip(RoundedCornerShape(16.dp))                 // no
```

`MaterialTheme.shapes` è già mappato sui raggi continui: usarlo va sempre bene.

## Tipografia: due tagli e una curva di tracking

`fluidTypography()` mappa la scala iOS (34/28/22/20/17/15/13/11) sugli slot di Material. Chi scrive
una schermata chiede `MaterialTheme.typography.titleMedium` e ottiene 17sp semibold, che è la riga
principale di ogni riga di lista.

Due cose la fanno leggere come San Francisco e non come "un grottesco carino":

- **Optical sizing**: sopra i 20sp si usa il taglio Display (spaziatura più stretta), sotto il taglio
  Text. È automatico dentro `fluidStyle`.
- **Tracking dipendente dalla dimensione**: negativo nei titoli, neutro a 13sp, positivo sotto. Inter
  a tracking zero sembra largo nei titoli e stretto nelle didascalie.

Non scrivere `fontSize` a mano. Se manca uno stile, aggiungilo a `FluidTextStyles` con la stessa
curva, non nella schermata.

Per numeri in colonna (voti, date, conteggi) usa `FluidTextStyles.numeric` o `largeNumeric`: hanno
le cifre tabulari, altrimenti la colonna balla mentre i numeri cambiano.

## Una palette da un colore solo

`FluidTheme(settings, brand)` genera l'intera scala di superfici dall'accento. Le superfici grandi
restano quasi neutre, i contenitori piccoli e alti prendono progressivamente più tinta.

I ruoli sono **coppie**: `primaryContainer`/`onPrimaryContainer`, mai un colore di sfondo separato da
quello di contenuto. Se ti serve un colore nuovo, cercalo prima fra i ruoli esistenti; se davvero non
c'è (warning e success non hanno un ruolo Material), aggiungi una coppia fissa con i due valori
chiaro/scuro, come già fanno `FluidTone.Warning` e `FluidTone.Success`.

Il contrasto è verificato da `FluidContrastTest`. Se aggiungi un ruolo, aggiungi il caso.

## Liste raggruppate

Righe dentro un unico contenitore arrotondato, separate da hairline da 0.5dp rientrate dove comincia
il testo.

Il tono della riga sta sulla **piastrella dell'icona**, mai sullo sfondo della riga. Colorare le
righe trasforma un gruppo ordinato in un patchwork di blocchi colorati: era così prima, ed è la cosa
che rendeva l'app "colorata" invece che leggibile.

Il feedback al tocco per una riga è `fluidRowPressable` (tinta), non `fluidPressable` (scala): una
riga che si rimpicciolisce rompe la sagoma del gruppo e fa sembrare la lista instabile.

## Il vetro va sul contenitore, mai sulla riga

Un `FluidListGroup` di dodici righe e' **un** nodo di vetro, non dodici. Ogni superficie costa una
registrazione di layer e una catena di `RenderEffect` sui propri limiti, quindi mettere il materiale
un livello troppo in basso porta una schermata da otto superfici a ottanta e fa cadere fotogrammi per
qualcosa che nessuno puo' vedere. Testo, icone e badge appoggiati sopra il vetro restano opachi al
100%: il materiale e' il contenitore, mai il contenuto.

Il vetro nel contenuto e' arrivato con la 1.5.0 e vuole due cose, non una:

1. **un canvas ambientale** (`FluidScreen(ambient = ...)`), perche' il vetro sopra il grigio e'
   invisibile per costruzione — una superficie che rifrange una pagina piatta produce una pagina
   piatta, e la sua assenza si legge come un bug;
2. **due registrazioni, non una.** Il canvas si disegna e si registra *prima* della lista, quindi non
   la contiene: una card dentro la lista rifrange solo la lavata sotto di se'. La chrome continua a
   rifrangere il corpo. Se una card campionasse la registrazione che la contiene, il risultato e' un
   feedback ottico — la cosa che nel vetro si vede subito e non si puo' nascondere.

`glass = true` e' una **richiesta**, non un obbligo: senza canvas in scope, o sotto API 31, il
componente disegna esattamente la superficie opaca di sempre.

## Un pannello di vetro e' una texture, e una texture ha un tetto

Ogni superficie di vetro registra un `GraphicsLayer` grande quanto se stessa, e un layer e' una
texture della GPU: qualche migliaio di pixel per lato, dipende dal dispositivo. Oltre quel limite la
registrazione non fallisce rumorosamente, torna **vuota** — e una registrazione vuota sotto una tinta
traslucida e' un rettangolo nero al posto del contenuto.

La superficie che ci arriva e' sempre la stessa: **un gruppo lista**, perche' un gruppo e' alto
quanto il numero di righe che l'utente si trova ad avere. Ottantaquattro voti in un pannello sono
sedicimila pixel, ed e' successo davvero.

Dalla 1.5.1 l'engine se ne accorge da solo e registra piu' in piccolo invece di annerire
(`fitToTexture`): quello che c'e' dietro un pannello di contenuto e' una lavata ambientale, morbida
per costruzione, che a un quarto della risoluzione non perde niente. E la cattura era solo meta' del
problema: il **contenuto** del pannello passava da un secondo layer offscreen grande quanto il
pannello stesso — un'altra texture con lo stesso tetto, e per giunta ri-rasterizzata a ogni
fotogramma di scorrimento, testo compreso. Dalla 1.5.3 quel layer non esiste piu' (la pila del vetro
e' tutta `SrcOver`, e comporre isolati o in place e' identico): sul tablet e' la differenza fra una
pagina Voti a 85 ms per frame e una a 27. Niente di tutto questo e' un permesso per fare gruppi
infiniti: e' la garanzia che una lista lunga resti guardabile.

## Il vetro si tara con la lente, non con la sfocatura

Il raggio di riferimento e' **2 dp**. E' passato da 8 a 16 e poi a 2, e il giro di mezzo e' l'errore
che vale la pena non rifare: sopra circa 8 dp un pannello smette di trasmettere un'immagine e
trasmette la sua *media*, e un pannello che tiene una media e' un riempimento. Tutto il lavoro che la
lente fa sul bordo sta allora piegando un colore piatto in un altro colore piatto.

Quello che identifica il materiale e' la **dislocazione** al perimetro e la riga speculare sopra, non
la brina. Quindi il raggio resta minuscolo e la lente sale (19 e 29 dp sulla capsula flottante, i
numeri della capsula di Kyant). Le superfici che devono davvero nascondere qualcosa — la barra
superiore, sotto cui scorre testo nitido — chiedono un multiplo con `GlassOptics.blurScale`, invece
di farlo pagare a tutte.

**E il film del vetro non e' `MaterialTheme.surface`.** Una barra dello stesso colore del fondo non e'
un materiale traslucido, e' niente: su un tema AMOLED spariva del tutto. Il Liquid Glass e' un
materiale *chiaro e riflettente*, che anche in tema scuro si legge come un pannello piu' chiaro del
fondo. `GlassDefaults.glassFilm()` parte da due grigi fissi e prende solo un terzo della palette,
quel tanto che basta perche' segua l'accento senza farsi trascinare a fondo.

## Un solo vocabolario di movimento

Tutto passa da `FluidMotion`. `FluidMotionScheme` lo passa anche ai componenti Material, così uno
switch e la chrome custom si muovono con le stesse molle.

Due invarianti:

- **Una pagina in arrivo non sfuma mai.** Le transizioni di rotta sono opache e laterali; due pagine
  leggibili sovrapposte, anche per 100ms, sono la cosa che fa sembrare l'app un prototipo.
- **`FluidMotionPolicy` va rispettata.** Quando la scala di animazione di sistema è a zero, il
  movimento decorativo sparisce. È un'impostazione di accessibilità, non un suggerimento.

Una `tween` scritta a mano dentro una schermata è il modo più rapido per far sembrare quella
schermata di un'altra app.

## L'engine non conosce il dominio

`engine-foundation` non importa niente di Compose né di Android UI. Nessun modulo dell'engine sa cosa
sia un voto, una materia, una lezione.

Quando un componente sembra generico ma ha un parametro che nomina un concetto dell'app, non lo è:
generalizza il parametro o lasciarlo nell'app. `SyncStatus.notice` è l'esempio: nell'app da cui
l'engine viene si chiamava `schoolYearNotStarted`, e generalizzarlo in "una frase che spiega perché
questo non è un errore" lo ha reso utile a qualsiasi app che sincronizzi qualcosa.

## Il codice dentro `engine/` non è codice dell'app

È un repo git a sé, agganciato a un tag. Modificarlo dentro un'app senza committarlo nel repo
dell'engine produce una variante che:

- sparisce al primo `engine-update.ps1` senza avviso;
- non arriva a nessun'altra app;
- fa sembrare che il bug sia stato risolto quando non lo è.

`engine-doctor.ps1` segnala le modifiche non committate proprio per questo.

## Il morphing vive in un posto solo

La regola storica — niente shape morphing, un raggio animato ri-clippa ogni fotogramma — resta
vera per le schermate. Dalla 1.9.0 esiste la deroga, ed è una sola: **Fluid-physics**
(`ui/fluidphysics`), che se la guadagna con una disciplina propria: il clip del layer non insegue
mai la sagoma (la silhouette la scolpisce l'alpha dello shader), il padding della cattura resta
fermo per tutto il viaggio, e a riposo la superficie tiene un'istanza di `Shape` stabile — il
morphing è transiente per contratto. Un raggio animato a mano in una schermata è ancora il bug di
prima; una forma che deve trasformarsi chiede un `FluidPhysicsState`.

Vale anche per gli shader: "nessuno shader di vetro nuovo accanto a GlassMaterial" resta la
regola, e la famiglia SDF di Fluid-physics è la deroga deliberata, nel suo package, annotata in
`LICENSES/AndroidLiquidGlass.md` (il preludio deriva da quello Apache-2.0 di Kyant).

## Tre trappole del vetro in movimento

Pagate una per una sui fotogrammi, migrando la card che si espande (1.9.7). Chi tocca una
superficie di vetro animata le incontra tutte e tre.

1. **Non scalare mai il layer di una superficie di vetro per farle fare un rimbalzo.** Un
   `graphicsLayer { scaleX = … }` scala anche il fondale campionato dentro il vetro: il testo della
   pagina *dietro* si muove, e non ha senso — quel testo sta fermo. L'oltrepasso va nella
   geometria della sagoma (`overshootInflation`), non nella trasformazione del nodo.
2. **Il primo e l'ultimo fotogramma di vita di un nodo possono disegnare con le proprietà di
   default.** Su una superficie il cui arrivo è un alpha (scrim, finestra dei modali) questo è un
   lampo a piena forza — misurato: un fotogramma a 190 di luminanza in mezzo a una serie a 214. Il
   cancello è `drawWithContent { if (amount > 0.004f) drawContent() }`: a materiale zero non si
   registra niente, e una displaylist vuota non ha lampi da rigiocare.
3. **Gli `exports` di uno scrim registrano il suo materiale a forza PIENA**, perché il suo arrivo
   è un alpha sul pannello finito. Chi campiona quel composito resta scuro mentre lo scrim vero
   sfuma. Meglio campionare la pagina cruda e dipingersi la propria quota di scurimento alla forza
   corrente (`tintFrom` + `tintBlend`).

E il metodo, perché nessuna delle tre si vede "ragionando": si vedono nei fotogrammi. Video con
`screenrecord`, estrazione con **`-fps_mode passthrough`** (`fps=30` su un video a frame rate
variabile riordina e duplica i fotogrammi, e una cronologia sbagliata porta a diagnosi sbagliate),
e per i difetti di un fotogramma solo la misura batte l'occhio: `signalstats,metadata=print` dà la
luminanza per frame, e un lampo è un picco isolato in una serie che dovrebbe essere monotona.
