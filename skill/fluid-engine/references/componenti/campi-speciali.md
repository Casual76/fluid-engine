# Campi speciali

I campi che sanno cosa contengono (F8.3, dalla 4.0): un **numero con la sua unità** (`FluidUnitField`), un **codice** a
caselle (`FluidCodeField`), una **password nuova** con le regole e la forza (`FluidPasswordField`), un **valore
riservato** sotto un velo (`Modifier.fluidPrivate`), una **copia che si cancella** (`FluidCopyButton`,
`fluidCopySensitive`) e il **tasto incolla** che compare solo coi dati concordati (`FluidPasteButton`). Stanno in
`engine-ui`, pacchetto `dev.antigravity.fluidengine.ui.fluid`; l'aritmetica pura (unità, conversioni, formati, regole
della password, forza, generazione) sta in `engine-foundation` ([../moduli/foundation.md](../moduli/foundation.md)).

**Nessuno ha un aspetto suo.** Ogni campo è il campo di testo dell'engine ([controlli.md](controlli.md), `FluidTextField`):
la capsula da 40 dp, il pozzo velato sulla pagina e opaco sopra il vetro. Le caselle del codice sono lo stesso pozzo,
alte quanto il campo di vetro di F6B.15 (52 dp). I tasti (incolla, copia, «Usa password forte») sono i tasti di vetro
dell'engine. Catalogo delle firme: [../api/controlli.md](../api/controlli.md) e [../api/foundation.md](../api/foundation.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un importo, una misura, una percentuale, con l'unità accanto | `FluidUnitField(value, onValueChange, unit = FluidUnits.Kilometer)`: il numero è un `BigDecimal`, mai la stringa |
| la stessa misura in un'altra unità, scelta dalla capsula | `FluidUnitField(…, units = FluidUnits.of(FluidUnitDimension.Length), onUnitChange = { unit = it })` |
| la conversione sotto il campo («≈ 7,77 mi») | `FluidUnitField(…, convertTo = FluidUnits.Mile)` |
| un prezzo in euro con due cifre | `unit = FluidUnits.Euro` (le cifre sono quelle della valuta) |
| un'unità dell'app, o un cambio di valuta | `FluidUnit.linear(id, dimensione, simbolo, "fattore")`, `FluidUnit.currency("USD", rate = "0.92")` |
| scrivere o leggere un numero con l'unità fuori da un campo | `FluidUnitFormat.format(quantità, locale)`, `FluidUnitFormat.parse(testo, locale)` |
| il codice della verifica in due passaggi, il PIN, l'abbinamento | `FluidCodeField(rememberFluidCodeFieldState(6), onComplete = { verifica(it) })`; a codice sbagliato `state.fail()` |
| il PIN a pallini | `FluidCodeField(…, secret = true)` |
| la password nuova con le regole che si spuntano, la forza e la proposta | `FluidPasswordField(value, onValueChange, rules = FluidPasswordRules.Default)` |
| le regole del servizio scritte come quelle di Apple | `FluidPasswordRules.parse("minlength: 10; required: lower; required: upper; required: digit")` |
| la password di un accesso (niente regole) | `FluidPasswordField(…, rules = null)` |
| una chiave, un saldo, un voto coperto finché non lo tocchi | `Modifier.fluidPrivate(rememberFluidPrivateState())` |
| i voti velati tutti insieme in classe | `Modifier.fluidPrivate(state, discreetOnly = true)` e `LocalFluidDiscreetMode provides acceso` |
| un'anteprima nascosta (l'album privato) | `Modifier.fluidPrivate(state, veil = FluidPrivateVeil.Blur)` |
| l'impronta prima di scoprire | `LocalFluidPrivateGate provides FluidPrivateGate { chiediImpronta() }` |
| copiare un segreto che si cancella da solo | `FluidCopyButton(text = { chiave })`, o `context.fluidCopySensitive(chiave)` |
| il tasto incolla per un link o per uno screenshot | `FluidPasteButton(FluidPasteKind.Link, onPaste = { … })`, `FluidPasteKind.Image` |
| il tasto incolla per un codice | è già nel campo a codice (`pasteButton = true`); da solo `FluidPasteKind.code(6..6)` |

## Il campo con unità

**Cosa fa.** Un campo per numeri con significato, con la capsula dell'unità dove la lingua mette il simbolo (dopo in
italiano, prima per la valuta in inglese). Restituisce il numero, nell'unità che mostra; il valore nell'unità base è
`FluidQuantity(valore, unità).inBase()`. È la composizione che Apple fa a mano (`TextField` con le `FormatStyle` di
Measurement e Currency, Calcolatrice di iOS 18 con le conversioni), fatta una volta.

**La scrittura dal vivo**, la parte insidiosa dei campi numerici:
- il testo del campo è **grezzo**: cifre, il decimale della lingua, il meno; le migliaia si vedono mentre si scrive
  («1.234,5») ma non sono nel testo, quindi il cursore non salta;
- «1,» resta «1,» mentre si scrive; a fine scrittura (il campo perde il fuoco) il numero si scrive per bene («12,5» →
  «12,50» per l'euro);
- `.` e `,` sono entrambi il decimale della lingua: sulla tastiera italiana il punto diventa virgola;
- oltre le cifre dell'unità il tasto non entra; il meno solo se `allowNegative` (vero di serie per le temperature);
- un incolla si legge con `FluidUnitFormat.parse`, che accetta l'altra convenzione e il simbolo attaccato («1,234.56»
  in un campo italiano, «12,5 km»).

**Le unità.** `FluidUnits` ha le più usate di Foundation (lunghezza, massa, temperatura, volume, superficie, velocità,
durata, dati, energia, percentuale, tre valute), con i fattori delle definizioni (1 in = 2,54 cm, 1 lb = 0,45359237 kg):
le conversioni sono frazioni esatte, l'unica divisione è l'ultima, arrotondata al pari come le `FormatStyle`. Mai
`Double`. Una valuta si converte solo se l'app dà il cambio (`FluidUnit.currency(codice, rate)`): l'engine non sa quanto
vale un dollaro oggi.

**I formati** (`FluidUnitFormat`): lo spazio fra numero e simbolo non va a capo; «21 °C» in italiano, «21°C» in inglese;
«12,5%» in italiano e in inglese, «12 %» in tedesco e francese; «12,50 €» in italiano, «€12.50» in inglese. TalkBack legge
«12,5 chilometri» (`FluidUnitFormat.spoken`, parole in italiano e in inglese).

**La capsula** con più `units` si tocca e apre il menu contestuale dell'engine; scegliendo un'unità il valore si
converte (`convertOnUnitChange`).

## Il campo a codice

**Cosa fa.** Le caselle per un codice di 4–8 cifre, ma **un campo solo** per la tastiera: il fuoco avanza e torna da
solo, «cancella» toglie l'ultima cifra, a codice completo chiama `onComplete` una volta. Sei cifre si leggono in due
gruppi da tre, otto in due da quattro (lo stacco dei codici di Apple).

- **Il codice proposto.** Il campo dice al sistema che aspetta un codice monouso (`ContentType.SmsOtpCode`, come
  `textContentType(.oneTimeCode)`): il servizio di autocompilazione (Google, Samsung Pass) può proporre il codice
  dell'SMS sopra la tastiera, e la scelta riempie tutte le caselle. Nessuna libreria di Google Play services; la
  proposta dipende dal servizio del telefono e non è garantita.
- **L'incolla.** Un codice incollato col dito lungo riempie le caselle in un colpo, anche dentro una frase («Il tuo
  codice è 482913», «482-913»). Sotto le caselle il tasto incolla compare se negli appunti c'è un codice (vedi sotto).
- **L'errore.** `FluidCodeFieldState.fail()`: le caselle tremano (10 dp, 480 ms, a occhio), l'aptica d'errore, la tinta
  d'errore, poi le caselle si svuotano; riscrivendo l'errore va via. Con «Riduci movimento» niente scossa.
- **La verifica.** `verifying = true`: il campo non si scrive e le cifre si velano; il lavoro e l'esito li dice il tasto
  con esito accanto ([elementi-dinamici.md](elementi-dinamici.md)).
- **TalkBack** legge il nome, «cifra 3 di 6», «codice completo», e l'errore.
- **Lo stato non si salva**: un codice o un PIN non finiscono nello stato salvato dell'attività.

Col testo grande le caselle crescono con la cifra e vanno a capo a gruppi, mai sotto i 48 dp.

## Il campo password

**Cosa fa.** Il campo segreto di sempre, con l'occhio, e sotto: **le regole** che si spuntano mentre si scrive, **la
forza stimata** a quattro segmenti con la parola, e a campo vuoto e col fuoco **la password forte proposta** con «Usa
password forte» e «Scegli la mia password», come le schermate di registrazione di iOS.

- **Le regole** sono dati dell'app: `FluidPasswordRules(minLength, maxLength, required, allowed, maxConsecutive)` o la
  riga delle Password Rules di Apple con `FluidPasswordRules.parse`. Una riga per regola, per TalkBack «Almeno 8
  caratteri, fatto».
- **La forza** (`FluidPasswordStrength.estimate`) è una stima semplice e dichiarata: l'alfabeto usato per la lunghezza
  efficace (le ripetizioni e le sequenze contano poco), e le password più comuni restano deboli. Niente zxcvbn. Sotto la
  parola c'è «Stima indicativa».
- **La proposta** (`FluidPasswordRules.generate`, da `SecureRandom`) ha il formato di Apple quando le regole lo ammettono
  (20 caratteri, tre gruppi da sei, una maiuscola e una cifra: «dixtuf-nezKun-zigvy6»), altrimenti è casuale dentro le
  regole. Usata, si scrive nel campo e si mostra in chiaro, come iOS.
- **L'autocompilazione**: `ContentType.NewPassword` per una password nuova, `ContentType.Password` per un accesso.

## Il valore riservato

**Cosa fa.** `Modifier.fluidPrivate(state)` copre un dato sensibile finché la persona non lo tocca, come
`redacted(reason: .privacy)` di SwiftUI e il velo dei contenuti sensibili di Messaggi.

- **Coperto, il valore non c'è**: non si disegna e TalkBack non lo legge («Valore nascosto, Tocca per vedere», con
  l'azione «Mostra»). Il velo è un segnaposto a barre (`FluidPrivateVeil.Placeholder`: un testo corto sfocato si
  indovina) o, per un'immagine, la sfocatura (`FluidPrivateVeil.Blur`, da Android 12).
- **Si scopre** col tocco, o col cancello (`LocalFluidPrivateGate`): il blocco dell'engine, quando ci sarà, o
  l'impronta dell'app.
- **Si ricopre da solo** dopo `recoverAfter` (30 s di serie, a occhio), in secondo piano e quando esce dalla composizione.
- **Mai nelle schermate recenti**: mentre è scoperto la finestra non lascia la sua immagine alle recenti (da Android 13
  `setRecentsScreenshotEnabled(false)`, prima `FLAG_SECURE`); con `blockScreenshots` anche gli screenshot sono neri.
  `FLAG_SECURE` vale per tutta la finestra: Android non sa fare di meno.
- **La modalità discreta** (`LocalFluidDiscreetMode`) vela insieme i valori `discreetOnly`; l'interruttore è dell'app.
- Lo scambio fra velo e valore entra sfocato in 300 ms (R7); con «Riduci movimento» cambia e basta.

## La copia delicata

**Cosa fa.** `fluidCopySensitive(testo)` (o `FluidSensitiveClipboard.copy`) copia un segreto **segnato come riservato**
(`ClipDescription.EXTRA_IS_SENSITIVE`: da Android 13 non si vede nell'anteprima degli appunti) e lo **cancella dopo un
tempo** (60 s di serie), come `UIPasteboard` con `.expirationDate`. Sotto Android 13 resta la sola scadenza.

- **Si cancella solo se è ancora il nostro**: il clip porta un segno dell'engine negli extra della descrizione, che si
  legge senza il contenuto. Se la persona ha copiato altro, non si tocca niente.
- **In secondo piano** Android non lascia leggere gli appunti: nel dubbio non si cancella, e la cancellazione aspetta il
  ritorno dell'app.
- **Il tasto** `FluidCopyButton` è il tasto con esito di Copia: dopo il tocco dice «Copiato» e fa vedere il tempo che
  resta nello stesso vetro, col pieno del contatore «Annulla» (aggiornato a passi di 125 ms nelle finestre lunghe, non a
  ogni fotogramma); TalkBack legge «Copiato, si cancella fra 58 s». Toccato mentre conta, copia di nuovo.

## Il tasto incolla

**Cosa fa.** `FluidPasteButton(kind, onPaste)` è un tasto di vetro che **compare da solo solo se negli appunti c'è il
dato che il campo aspetta** (nota di Alessio: un codice per il campo del codice, un link per un campo link, un'immagine
per un campo che accetta uno screenshot); con gli appunti normali non c'è. Nasce e se ne va nel piano flottante.

**Come lo sa senza leggere.** Da Android 12 leggere il contenuto degli appunti fa comparire l'avviso «ha incollato dagli
appunti»; leggere la descrizione no. Il tasto legge solo la descrizione: il tipo (un'immagine, una lista di indirizzi) e
la classificazione che Android fa da sé del testo copiato (il link; il codice monouso solo da Android 16). Il contenuto
si legge al tocco, come `PasteButton` di SwiftUI.

| Android | codice | link | immagine |
|---|---|---|---|
| 16 e dopo | dalla classificazione | dalla classificazione o dal tipo | dal tipo |
| 12–15 | **non compare** (Android non dice se un testo è un codice) | dalla classificazione o dal tipo | dal tipo |
| prima di 12 | il testo corto si legge (nessun avviso): deve essere il codice intero | il testo deve essere il link | dal tipo |

Dove il tasto del codice non compare restano l'incolla col dito lungo e il codice proposto dalla tastiera. **Anche da
Android 16 dipende dal classificatore del telefono**: sul Tab S9 (Samsung, Android 16) il link si riconosce e il codice no
(né «482913» da solo né dentro una frase), quindi lì il tasto del codice non compare; le tastiere mostrano comunque
l'ultimo appunto nella loro barra, mascherato nei campi segreti.
`rememberFluidPasteOffer(kind)` dà la stessa decisione a chi vuole un tasto suo.

## Cosa fa l'engine e cosa resta all'app

| l'engine | l'app |
|---|---|
| i campi, la tastiera giusta, la scrittura dal vivo, le conversioni dichiarate, i formati | quali unità offre, i cambi delle valute, cosa fa col numero |
| le caselle, il fuoco, l'incolla, l'autocompilazione, la scossa, TalkBack | verifica il codice e chiama `fail()` |
| le spunte, la forza, la proposta, gli hint dell'autocompilazione | le regole del servizio, dove salva la password |
| il velo, il tocco, la ricopertura, la finestra fuori dalle recenti, la modalità discreta | cosa è riservato, l'interruttore, il cancello |
| il segno riservato, la scadenza, la cancellazione prudente, il tasto | quali dati sono delicati, il tempo |
| quando compare il tasto incolla, la lettura al tocco | quali dati accetta un campo, cosa farne |

## Trappole

- **La lingua del campo con unità cambia il testo**: il testo del campo si riscrive coi separatori nuovi (dal tablet di
  F8.3: prima restava «767,,08»).
- **La proposta della password sotto il campo** resta sotto la tastiera se la pagina non scorre: tienila in una pagina
  che scorre (su iOS il pannello prende il posto della tastiera).

- **Il tasto incolla del codice fra Android 12 e 15 non compare.** È voluto (nel dubbio, no): non leggere il contenuto
  per farlo comparire, o torna l'avviso di Android.
- **`FLAG_SECURE` è per tutta la finestra.** Con `blockScreenshots` uno screenshot della pagina intera è nero finché un
  valore è scoperto.
- **Un valore riservato in una lista** si ricopre quando la riga esce dallo schermo: lo stato non va tenuto in alto.
- **La forza non è sicurezza**: non bloccare un'iscrizione sulla forza, blocca sulle regole.
- **Il numero, non la stringa**: non leggere il testo del campo con unità, usa `onValueChange`.

## Come si prova

- I test puri: `FluidUnitsTest`, `FluidPasswordRulesTest` (engine-foundation), `FluidSpecialFieldsPureTest` (engine-ui).
- I test con l'albero Compose vero: `F8p3FieldsUiTest` della galleria (il campo con unità in italiano e in inglese, il
  codice incollato e proposto, l'errore, le regole, il velo che si ricopre con la finestra protetta, il clip riservato
  con la sua scadenza, il tasto incolla che compare solo coi dati concordati e non legge prima del tocco).
- In galleria: «Elementi nuovi» › «Campi speciali» (`--es gallery.page NewElements --es novita.scene unita`, `codice`,
  `password`, `riservato`, `copia`, `incolla`), su pagina, foto chiara e foto scura.

## Dove guardare

`engine-ui/…/ui/fluid/`: `FluidUnitField.kt`, `FluidCodeField.kt`, `FluidPasswordField.kt`, `FluidPrivateValue.kt`,
`FluidFieldClipboard.kt` (tasto incolla e copia delicata), `FluidSpecialFieldLabels.kt` (le parole, con l'inglese).
`engine-foundation/…/foundation/`: `FluidUnits.kt`, `FluidUnitFormat.kt`, `FluidPasswordRules.kt`. La galleria:
`sample/…/novita/F8p3Scenes.kt`.
