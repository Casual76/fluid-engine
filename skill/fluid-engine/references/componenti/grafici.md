# Grafici: ciambella, barre, andamento, legenda

Quattro componenti per **mostrare quanto**: `FluidDonutChart` (di cosa è fatto un totale), `FluidBarChart` (confronto fra voci, anche impilato), `FluidLineChart` (come è andata nel tempo), `FluidChartLegend` (il testo che
spiega i colori), più due riquadri di stato (`FluidChartMessage`, `FluidChartSkeleton`). Famiglia nuova dalla 3.1.0 (Pampa Mail, F1.5, gruppo G-G1). File: `engine-ui/.../ui/fluid/FluidCharts.kt` (disegno) e `FluidChartMath.kt` (i
conti: scale, quote, assegnazione dei colori, tutto puro e provato sulla JVM). Catalogo delle firme: [`../api/grafici.md`](../api/grafici.md) (generato).

**Modello mentale in due frasi.** Un grafico dell'engine **non può mentire e non può essere solo di colore**: la scala parte da zero e finisce su un numero tondo, una barra tagliata o una ciambella che non somma sono «dati
non validi» (mai disegnati), un buco in una serie spezza la linea (mai un valore inventato), e ogni numero esiste anche come testo (legenda, descrizione per TalkBack). I colori sono quelli della palette d'identità, portati a
3:1 dalle superfici su cui poggiano.

## Scegliere in fretta

| la domanda è… | uso |
|---|---|
| «com'è fatto questo totale?» e le voci sono **disgiunte** (ogni elemento in una voce sola) | `FluidDonutChart(entries)` |
| una ripartizione di spesa **in valuta** (per fornitore, per modello) | `FluidDonutChart` con `valueText` che scrive l'importo nella valuta e `whole` col totale dichiarato: la somma deve tornare, e se non torna il grafico lo dice invece di disegnare |
| «quanto per ciascuna?» con voci che **si sovrappongono** (un messaggio ha due etichette) | `FluidBarChart` con `maxValue` e `scaleLabel`: **non** una ciambella, che mentirebbe |
| confronto fra voci, conteggi per periodo, spesa per fornitore | `FluidBarChart(groups, series)` (verticali, `horizontal = true` per etichette lunghe) |
| più serie affiancate | `FluidBarChart` con più `series`: un valore per serie in ogni `FluidBarGroup` |
| una serie che è somma di parti disgiunte (non negative) | `FluidBarChart(stacked = true)` |
| «com'è andata nelle ultime settimane?» | `FluidLineChart(series, points, xLabels)`; `fill = true` per un'area |
| il dato di un giorno manca | `null` nel punto: la linea si **interrompe**; non unire i vicini |
| una legenda in un layout tuo, o per grafici a più serie | `FluidChartLegend(series)` (in fila) o `FluidChartLegend(items, vertical = true)` (con dettagli) |
| «nessun dato», «dati non validi», un errore con «Riprova» al posto del grafico | `FluidChartMessage(text, detail, actionLabel, onAction)` |
| «sto calcolando» | `FluidChartSkeleton(height)` (stessa altezza del grafico: la pagina non salta) |
| una sola cifra con una variazione | **non un grafico**: `FluidMetricTile` ([contenuto-righe-liste.md](contenuto-righe-liste.md)) |
| decine di serie o un asse x continuo con migliaia di punti | **non questi**: campiona **prima** (50.000 record → 30 punti) e passa i punti; l'engine non campiona |

## Firma essenziale

```kotlin
FluidDonutChart(entries: List<FluidChartEntry>, modifier, centerValue = null, centerLabel = null,
  whole: Float? = null, sumPolicy = FluidChartSumPolicy.Strict, groupBelow = 0.03f, maxSlices = 8,
  valueText: ((FluidDonutSlice) -> String)? = null, showLegend = true,
  selectedIndex: Int? = null, onSelectedChange: ((Int?) -> Unit)? = null, contentDescription: String? = null, emptyText: String? = null)
FluidChartEntry(label, value: Float, color = Color.Unspecified)

FluidBarChart(groups: List<FluidBarGroup>, series: List<FluidChartSeries>, modifier, stacked = false, horizontal = false,
  valueText: ((Float) -> String)? = null, maxValue: Float? = null, scaleLabel: String? = null, showLegend = series.size > 1,
  height = FluidChartDefaults.PlotHeight, selectedIndex, onSelectedChange, contentDescription, emptyText)
FluidBarGroup(label, values: List<Float>)               // un valore per serie, nell'ordine di series

FluidLineChart(series, points: List<List<Float?>>, xLabels: List<String>, modifier, fill = false, startAtZero = true,
  valueText, scaleLabel, showLegend = series.size > 1, height, selectedIndex, onSelectedChange, contentDescription, emptyText)
FluidChartSeries(label, color = Color.Unspecified)      // Unspecified: dalla palette d'identità, per posizione
FluidChartLegend(items: List<FluidChartLegendItem>, modifier, vertical = false, selectedIndex, onItemClick, accessible = true)
FluidChartLegendItem(label, color, detail = null)       // + overload FluidChartLegend(series, …)
```
Altri nomi: `FluidChartDefaults` (soglie e misure), `FluidDonutSlice`, `FluidDonutLayout`/`fluidDonutLayout(…)` (le stesse fette del grafico, con `sourceIndices`), `FluidDonutProblem`, `FluidChartSumPolicy`, `LocalFluidChartLocale`.

## La ciambella è una promessa: le parti fanno il tutto

**Usala solo per voci disgiunte.** «Arrivi per categoria» (ogni elemento in **una** categoria) è una ciambella; «messaggi per etichetta» (un messaggio ne ha due) **non somma al 100%** e in una ciambella direbbe il falso:
vuole barre separate col denominatore dichiarato.

- **Dati non validi, non disegnati.** Un valore `NaN`/infinito o **negativo** → «dati non validi» (`FluidDonutProblem.NotFinite` / `Negative`, con la posizione colpevole). Nessuna voce o tutte a zero → «nessun dato nel
  periodo» (`Empty`: non è un errore, non si divide per zero).
- **`whole`: il totale dichiarato.** Con `whole` (100 se i valori sono percentuali, 1240 se sono mail) la somma deve tornare entro lo **0,5%**: altrimenti, di serie (`FluidChartSumPolicy.Strict`), il grafico **non si disegna**
  e scrive somma e totale (`SumMismatch`). `FluidChartSumPolicy.Normalize` la sceglie **chi sa che c'è un resto** (arrotondamenti a monte): le quote si calcolano sulla somma e il disegno è pieno al 100%. La scelta è esplicita,
  nel codice di chi chiama, mai una conseguenza silenziosa di un dato sbagliato.
- **Fette minuscole.** Sotto `groupBelow` (3%) le fette confluiscono in «Altro» **se sono almeno due**; oltre `maxSlices` (8: più di otto tinte non si distinguono) restano le maggiori. Con `onSelectedChange` l'indice è quello
  delle fette **disegnate**: `fluidDonutLayout` dà le stesse fette e `sourceIndices` per risalire alle voci di partenza. Il colore di «Altro» è un neutro, portato a 3:1 come gli altri.
- **Selezione.** Un tocco su una fetta (o sulla sua riga di legenda) la evidenzia e scrive nel foro la sua percentuale; un tocco sulla stessa, o fuori, la toglie. Gestita dal grafico e salvata con lo stato della pagina, **oppure
  controllata** da `selectedIndex` + `onSelectedChange` (con `onSelectedChange` la sorgente è `selectedIndex`). Un indice fuori dai dati (la lista è diminuita) vale nessuna selezione. Ogni scelta suona un colpo `Tap`, mai due.
- **Il foro** porta `centerValue` («1.240», il totale) e `centerLabel` sotto. Il valore **si riduce** per entrare nel foro (al 200% «1.240» non ci sta a 34 sp): mai a capo (una cifra spezzata è un numero sbagliato), mai tagliato;
  l'etichetta va su due righe.
- **Testo grande.** Al 200% l'anello si assottiglia (0,22 → 0,15 del diametro) e il foro cresce; la legenda va a capo. Tutti i numeri esistono anche come testo nella legenda.
- **Le percentuali scritte sommano esattamente 100** (arrotondate col metodo del resto più grande), e una quota minuscola si scrive «meno dell'1%», **mai 0%**. Numeri e percentuali usano i separatori della lingua
  (`LocalFluidChartLocale`), poche cifre, e dal milione in su la forma compatta.
- `valueText` scrive il valore di una fetta; **stringa vuota** lo nasconde e lascia la sola percentuale (quando il valore **è** già la percentuale).

## Barre: scala, denominatore, pile

- **La scala parte sempre da zero** (è una barra, non una linea) e finisce su un **numero tondo**. Se le barre sono quote di un totale, **dichiaralo**: `maxValue` fissa la fine della scala (1.240 mail: finisce a 1.240, non a
  1.500) e `scaleLabel` lo dice in parole sotto il grafico («% sulle 1.240 mail del periodo») e nella sintesi per TalkBack. **Un valore che supera `maxValue` è un dato sbagliato**: il grafico dice «dati non validi» e non taglia una
  barra («una barra tagliata mente»).
- **Pile** (`stacked`): **solo per serie disgiunte e non negative** (la barra intera è la somma). **Non** per etichette che si sovrappongono. Valori negativi nelle barre affiancate partono da zero verso il basso (o sinistra); in
  una pila sono dati non validi.
- **Etichette e testo grande.** Le etichette delle caselle vanno a capo su due righe e **si diradano** (una ogni `stride`) finché tutte quelle scritte stanno nello spazio che hanno, a capo senza spezzare una parola: al 200% ne restano
  meno, **mai sovrapposte**, e il numero di ogni barra è nella descrizione. Il valore si scrive sulla barra quando c'è posto (in cima, o dentro il segmento di una pila). Le barre orizzontali hanno l'etichetta a sinistra (anche su tre
  righe) e il valore alla fine.
- **Selezione.** Un tocco su una casella (o sulla sua riga, in orizzontale) la evidenzia e scrive sotto il suo testo completo; il bersaglio è tutta la casella e il tocco fuori la toglie. `selectedIndex` + `onSelectedChange` la
  controllano. La riga sotto il grafico (il testo della scelta, o la `scaleLabel`) è **sempre riservata**: la scelta non sposta la pagina.
- Spessore massimo di una barra verticale: una barra non diventa una lastra con poche voci. Lo spazio fra barre, fette e segmenti è **spazio**, non un bordo: la separazione non dipende dal colore.

## Andamento

- **Dati**: `points[serie][posizione]`, una lista per serie lunga come `xLabels`; **`null` è un buco** (un giorno senza dati). Un buco **spezza la linea**; un punto isolato fra due buchi si disegna come un punto; la descrizione
  dice «nessun dato». I valori devono essere finiti (negativi ammessi: la scala include lo zero e lo mostra). Tutti buchi o nessun punto: «nessun dato nel periodo». Liste di lunghezze diverse: «dati non validi».
  **Il campionamento (50.000 record in 30 punti) lo fa chi chiama, fuori dal disegno.**
- **Asse.** La scala include lo zero (`startAtZero = true`, di serie) e finisce su un numero tondo; con `fill` (area) lo zero è **sempre** incluso, perché l'area è la distanza dallo zero. Spegnere `startAtZero` è una scelta di chi sa
  che un asse che non parte da zero non inganna (un andamento di temperature, non di conteggi). Le etichette di `xLabels` si diradano e vanno a capo senza sovrapporsi, anche al 200%.
- **La curva non inventa picchi.** Fra due punti la linea è una curva **monotona** (`fluidMonotoneTangents`, interna): non supera mai i due valori misurati. Una spline qualunque «rimbalza» e disegna un massimo che i dati non hanno.
- **Selezione.** Un tocco su un punto (la sua colonna) lo evidenzia e scrive sotto «etichetta – valori»; con TalkBack si esplora punto per punto.

## Legenda

Un campione di colore, il nome e (se c'è) il `detail` (di solito valore e percentuale) per voce. **Ha anche il testo perché il colore da solo non basta a chi non distingue le tinte**: è la seconda via per capire quale voce è
quale, e con `onItemClick` è un bersaglio da 48 dp per le fette sottili che il dito non prenderebbe. `vertical = true`: una voce per riga e il dettaglio a destra (le ripartizioni); altrimenti in fila con a capo (le serie
di un andamento). **`accessible = false`** la nasconde a TalkBack, da usare quando il grafico espone già le stesse voci una per una (altrimenti ogni voce si ascolterebbe due volte); resta toccabile.

## Accessibilità: il testo è generato dai dati

Un grafico è un nodo con **una sintesi** che TalkBack legge per prima, e **una frase per voce** che si esplora una a una. Il testo è **generato dai dati** (non scritto dall'app) e usa i valori **scritti a schermo**, nella lingua
di `LocalFluidChartLocale` (di serie la lingua del telefono: un'app con una lingua sua diversa dal sistema la mette lì, insieme a `LocalFluidLabels`, così frasi e cifre sono della stessa lingua).

- Ciambella: «Arrivi per categoria. Ripartizione in 5 parti. La più grande: Principale, 62%.» e per fetta «Principale: 620 (62%)». Il `contentDescription` (di cosa parla il grafico) viene **prima** della sintesi.
- Barre: tipo, numero di voci, serie, valore più alto, scala dichiarata, e una frase per casella con i valori di **tutte** le serie.
- Andamento: punti, prima e ultima etichetta, serie, minimo e massimo, e una frase per punto con i valori di tutte le serie (un buco dice «nessun dato»).
- Stati: `FluidChartSkeleton` dice «caricamento» **una volta sola** (`FluidLabels.chartLoading`); il vuoto è `FluidLabels.chartNoData`. Le parole sono `FluidLabels` (italiano di serie, inglese in `FluidLabels.english()`).

## Colori: portati a 3:1

I colori delle serie sono quelli dati dall'app o, dove mancano, dalla **palette d'identità** (`FluidIdentityColors`, [contenuto-testo-e-media.md](contenuto-testo-e-media.md)) **per posizione**: la prima serie parte dal blu e le altre
si distribuiscono a passo uguale lungo il cerchio cromatico (due serie: blu e arancio; tre: blu, viola e verde); da dodici serie in poi la palette gira (raggruppa in «Altro»). **Non è un contratto di stabilità**, a differenza di
`FluidIdentityColors.indexFor`: dice solo «tinte lontane fra loro». Chi vuole che «Promozioni» sia sempre dello stesso colore **passa il colore**.

Qualunque colore (della palette o dell'app) è portato **del minimo che serve** a staccarsi a **3:1** da tutte le superfici su cui può stare: pagina, card a due livelli e pellicola del vetro (un grafico può stare su una card
di vetro). Per i colori della palette non cambia niente (provato su tutta la palette nei due temi); il ritocco serve ai colori dell'app. Il **testo secondario** (assi, didascalie) è `onSurfaceVariant` portato verso `onSurface`
fino a 4,5:1 su tutte le superfici (sul vetro scuro il grigio del tema fa meno di 4:1). **Il colore non è mai l'unica informazione**: legenda con testo, valori scritti, fette separate da spazio.

## Movimento

L'ingresso (le fette, le barre e la linea crescono da zero) accade **una volta sola per grafico, ed è salvato**: un grafico in una lista che esce e rientra non ricresce. **Col movimento ridotto il grafico nasce già intero**, senza
un solo fotogramma di crescita. L'evidenza di una voce scelta cresce e torna con `FluidMotion.chartSelect` (di colpo col movimento ridotto). Tutto passa da `LocalFluidMotionPolicy`.

## Trappole

- **Etichette sovrapposte in una ciambella.** È l'errore classico: sommano oltre il 100% e le fette sono false. Barre separate con `maxValue`, o `whole` con `Strict` (che lo segnala).
- **`Normalize` per far sparire un errore.** Non è il rimedio per dati sbagliati: nasconde che la somma non tornava. Va scelto solo da chi **sa** perché c'è un resto.
- **Un asse che non parte da zero** in un grafico a barre non esiste, in un andamento è una scelta dichiarata.
- **Campionare dentro il composable.** 50.000 record in un `points` bloccano il thread: riduci prima, il grafico disegna ciò che riceve.
- **`valueText` con il formato dell'app e non della lingua**: usa il `locale` dell'app (`LocalFluidChartLocale`) se vuoi cifre e frasi coerenti.
- **`accessible = false` sulla legenda senza che il grafico dica le voci**: nessuno le legge più.
- **Il grafico non sa cosa è «una mail»**: titolo e descrizione (`contentDescription`) sono dell'app.
- Dentro un `FluidScreen` il grafico prende la larghezza che riceve; la ciambella ha un lato massimo (`FluidChartDefaults`) e resta al centro: non crescere oltre.

## Cosa è verificato dai test

- **`FluidChartMathTest`** (`engine-ui`, JVM, 54): le scale tonde che partono da zero e coprono i dati (anche a zero, negativi, enormi, e cinquecento casi casuali), la scala fissata dal denominatore, il budget di tacche che si riduce col testo,
  il diradamento minimo delle etichette; le quote che sommano uno, vuoto e tutto zero (non un errore), negativi e non finiti con la posizione, il totale dichiarato e le due politiche, «Altro» con almeno due fette minuscole e il tetto di otto,
  una fetta sola e un valore enorme fra minuscoli; le percentuali che sommano 100, «meno dell'1%», i separatori per lingua e i numeri grandi; gli angoli che chiudono il cerchio con lo spazio fra le fette, il tocco su una fetta (gli spazi sono
  di un vicino, il foro e il fuori di nessuno), l'anello che si assottiglia col testo, l'ingresso intero con movimento ridotto; le barre (centrate, mai più spesse del massimo, le pile da zero, la scala di una pila, il denominatore e il valore
  sopra, i dati sbagliati), il buco che spezza la linea e la curva monotona; i colori di serie e **tutta la palette a 3:1 su ogni superficie nei due temi**, cinquecento colori casuali portati del minimo, il testo degli assi a 4,5:1.
- **`FluidChartsTest`** (`sample`, Robolectric, 30): ciambella letta come sintesi + un nodo per fetta (in italiano e inglese), percentuali che sommano cento, la selezione con TalkBack, col tocco e dalla legenda (riga da 48 dp), controllata o no,
  vuoto e tutto zero nelle due lingue, il valore negativo e il totale mancato (con `Normalize` si disegna), «Altro», barre orizzontali col denominatore, valore sopra il denominatore «non valido» e non tagliato, pile, negativi ed enormi,
  l'andamento con buchi e due serie, le etichette dell'asse che si diradano a 200%, ciambella e barre che **crescono in altezza** a 200% invece di tagliare, il movimento ridotto, il tema scuro, la demo.
- **Non provato da test**: l'aspetto vero (la curva, le ombre, il vetro sotto un grafico) e TalkBack su un telefono.

## Dove guardare

| file | cosa c'è |
|---|---|
| `engine-ui/.../ui/fluid/FluidCharts.kt` | i quattro componenti, i riquadri di stato, la legenda, le descrizioni generate, i colori e il movimento, `FluidChartDefaults`, `LocalFluidChartLocale` |
| `engine-ui/.../ui/fluid/FluidChartMath.kt` | i dati (`FluidChartEntry`, `FluidDonutSlice`, `FluidDonutLayout`, `FluidChartSumPolicy`), la validazione, le scale, il diradamento, le tangenti: tutto puro |
| `sample/.../catalog/ChartsDemo.kt` | il call-site vero: ciambella, barre (anche impilate e orizzontali), andamento, stati, chiaro/scuro, testo grande, movimento ridotto |
