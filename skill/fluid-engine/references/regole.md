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
