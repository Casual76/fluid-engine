# La pagina «Icone»

Pacchetto F1A.11 del piano «iOS in Android» (`docs/liquid-glass/piano.md`, sezione F1A). Esiste per giudicare in mano
le icone nuove dell'engine (`FluidIcons`, Material Symbols arrotondate a peso 500) e le misure che vengono con loro:
icone più grandi e nell'inchiostro del testo, tasti più lontani dal bordo, tab bar a 62 dp con l'icona piena su ogni
scheda. Non confronta le icone di prima con quelle nuove: quel confronto sta solo nelle tavole del banco
(`artifacts/liquid-glass/tavole/f1a/icone-prima-dopo-{chiaro,scuro}.png` e `12-tabbar-piene.png`). La pagina della
galleria che guarda il vetro è «Materiale» (`../material/LEGGIMI.md`, le voci B.1–B.7 rimandano qui).

Chi guarda le foto è Alessio, in chat: la sessione porta collage e ritagli, non giudizi a parole. Le
regole del tablet sono quelle di `../material/LEGGIMI.md`: mai il tasto di accensione, in secondo piano con `keyevent 3`,
`wm size reset` e `wm density reset` alla fine se si è passati al formato telefono.

## Come si lancia

Dal catalogo di Chrome (riga «Icone») o da `adb`, nella build di debug:

```bash
adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
  --es gallery.page icons --es icons.section sizes|chrome|rtl|set
```

Senza `icons.section`, o con un valore che non c'è, la pagina mostra tutte le sezioni; l'extra si legge solo nella build
di debug. Il tema scuro si chiede con `--es gallery.theme dark`, ma le sezioni `sizes` e `chrome` mostrano già i due
temi, uno sopra l'altro, ognuno su un palco con la pagina vera del tema. `-S` ferma l'app prima: senza, un secondo lancio
riporterebbe davanti la sezione di prima. Il pacchetto, la funzione `qa_shot` e il resto degli strumenti sono quelli di
«Materiale».

## Le sezioni

### `sizes` — Taglie sul vetro
Lo stesso tasto da 44 dp con l'icona a 20, 22, 24, 26 e 28 dp, nei due temi. 26 dp è la taglia di serie dalla 4.0
(0,6 del vetro; prima 22, nell'accento). **Atteso**: icone nitide, non tagliate, monocrome (nere sul chiaro, bianche
sullo scuro), la 26 che riempie il tasto senza toccare il filo. La terza riga del blocco scuro finisce sotto la tab bar
della galleria finché non si scorre.

### `chrome` — Barra, capsula, tab bar, menu
I componenti veri con le misure nuove, nei due temi: tasti a 16 dp dal bordo e a 8 fra due vetri, icone a 26 dp (la
freccia 24) nell'inchiostro del testo, la capsula flottante, la tab bar alta 62 dp con icone da 28 piene su ogni scheda
e l'etichetta a 11 sp; «Altro» apre il menu, con le icone a 22. **Atteso**: i vetri a 16 dp dal bordo dello schermo e
centri a 52 dp (44 + 8), la freccia centrata nel suo vetro, un titolo lungo che si tronca prima del tasto, la scheda
scelta nella pillola e nel colore, non nel riempimento, e nessun verde sulle icone di barra.

### `rtl` — Da destra a sinistra
La stessa barra specchiata dentro la pagina (`LocalLayoutDirection`): la freccia si gira da sola e i margini passano
dall'altra parte. Sotto, le icone del set che si girano (↔) accanto alla loro forma da sinistra a destra. **Atteso**: si
girano solo le frecce e le icone direzionali; i margini restano 16 dp da tutti e due i lati.

### `set` — Il set
Tutto il set (`FluidIcons`, 162 icone), tre per riga: al tratto a sinistra, la gemella piena a destra se c'è (31 icone
ce l'hanno; la tab bar le usa su ogni scheda, non solo sulla scelta); ↔ si gira da destra a sinistra. Le quattro icone
dei cambi di icona (`Dns`, `Shield`, `Language`, `Group`) non sono usate da nessun componente né da nessuna riga della
galleria: si vedono **solo qui**. Le righe sono una lista pigra e si costruiscono mentre compaiono: la prima costruzione
di un'icona si misura sul fotogramma in cui il set compare, a freddo (stima di F1A.11a: sotto 1 ms ciascuna; la pagina
non scrive questo tempo in logcat, serve una traccia Perfetto o `dumpsys gfxinfo`).

## Cosa si è visto sul tablet (S2-pre, voce B8, Tab S9)

Un solo dispositivo; il formato telefono è `wm size 1080x2340` e `wm density 440` sul tablet, quindi vale per margini e
titoli, non per costi o GPU.

| cosa | tablet (densità 2,125) | telefono (2,75) |
|---|---|---|
| bordo sinistro → vetro | 34,5 px = 16,2 dp | 45 px = 16,4 dp |
| ultimo vetro → bordo destro | 35,5 px = 16,7 dp | 44 px = 16,0 dp |
| centri dei due vetri a destra | 112 px = 52,7 dp (± 1,5 px) | 143 px = 52,0 dp |
| tab bar, dal filo in alto a quello in basso | 132 px = 61,9 dp | 171 px = 62,0 dp |

Sul filo il diametro del vetro misura 92 px (43,3 dp): per questo, presa fra i fili, la distanza fra i due vetri risulta
8,5–9 dp. La freccia (`ArrowBackIosNew`, 24 dp) è centrata nel vetro al pixel (inchiostro x 543–565, y 676–716, centro
554,0; 696,0 contro il vetro 554; 697), nei due temi. La sezione `sizes` mostra le cinque taglie nitide e monocrome nei
due temi. Il titolo lungo del telefono («Pannello laterale e sch…») si tronca prima del tasto; «Capsule e pezzi piccoli»
entra intero. Il rail (≥ 600 dp, «Pannello laterale e schede») è verticale, con l'azione in testa e le icone a
inchiostro pieno; il campo di ricerca chiuso ha la lente a inchiostro; la croce del foglio a schermo intero sta a
36 px = 16,7 dp dal bordo destro e a 22 dp da quello alto. La sezione `rtl` mostra la barra specchiata con i margini a
34 px = 16,0 dp dai due bordi.

## Cosa non è stato provato

- **La destra-sinistra di sistema.** Con `settings put global debug.force_rtl 1` e l'app riavviata la galleria non si
  gira: la chiave da sola non basta, serve anche l'aggiornamento di configurazione dell'opzione sviluppatore, che da
  `adb` non si è fatto per non toccare altre impostazioni. Si è misurata solo la barra specchiata della sezione `rtl`.
  Proprietario: S2.
- **La sezione `set`**: non si è aperta in nessuna sessione, e con lei la prima costruzione di un'icona. Proprietario: S2.
- **Il foglio a schermo intero in chiaro**: il tocco su «Pagina intera» è caduto sul bordo dello schermo; misurato solo
  in scuro. Proprietario: S2.
- **Un telefono vero** (il formato telefono è una simulazione sul tablet) e **l'S25**: S5.
- **Le icone di un'app che passa le sue** (`Icons.Rounded.*`, `Icon(…)` con una taglia scritta): restano com'erano, alla
  taglia che l'app dà; la pagina non le mostra.
- **L'inchiostro fuori da una `Surface`**: `FluidGlassIconButton` prende l'inchiostro da `LocalContentColor`, che fuori da
  una `Surface` è nero, anche sul vetro scuro (la pagina «Materiale» scura lo mostrava prima della correzione). Il vetro
  che dà l'inchiostro al suo contenuto è dell'inchiostro adattivo, F2.

## Il confronto con le icone di prima

La pagina non mostra le icone di prima accanto a quelle nuove. Aggiungerlo è codice (per ogni icona del set, la sua
corrispondente di Material, da tenere in pari con `tools/icons/corrispondenza.md`) e non è ancora stato fatto: è una
proposta per S2. Il confronto prima | dopo è nelle tavole `icone-prima-dopo-*` del banco, e la corrispondenza fra i nomi
nella tabella di `tools/icons/corrispondenza.md`.
