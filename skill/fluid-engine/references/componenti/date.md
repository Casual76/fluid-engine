# Date: calendario, capsula, durata, «Ripeti»

Le date della 4.0 (F8.8): il **calendario in pagina** (`FluidCalendar`) con i segni sotto i giorni, la scelta di un
giorno, di giorni sparsi o di un intervallo, il mese che cambia col dito e la striscia della settimana; la **capsula**
della data in una riga (`FluidDateCapsule`) e quella della durata (`FluidDurationCapsule`), che diventano un popover; il
pannello **«Ripeti»** (`FluidRecurrenceSheet`). Stanno in `engine-ui`, pacchetto `dev.antigravity.fluidengine.ui.fluid`.
La regola della ricorrenza, la sua frase, l'intervallo di giorni e il primo giorno della settimana sono dati puri di
`engine-foundation` ([../moduli/foundation.md](../moduli/foundation.md), «Date e ricorrenze»).

Il foglio data e ora (`FluidDateTimeSheet`, [menu-modali.md](menu-modali.md)) usa lo stesso calendario per la sua griglia.
La ruota della durata e dell'ora sono quelle dei controlli a gesto ([controlli-gesto.md](controlli-gesto.md)). Catalogo
delle firme: [../api/controlli.md](../api/controlli.md) e [../api/foundation.md](../api/foundation.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il mese nella pagina, con un giorno scelto | `FluidCalendar(FluidDateSelection.Single(giorno), onSelectionChange = { … })` |
| più giorni, anche in mesi diversi (i giorni da giustificare) | `FluidCalendar(FluidDateSelection.Multiple(giorni), …)` |
| un intervallo «dal… al…» con la fascia | `FluidCalendar(FluidDateSelection.Range(inizio, fine), …)`; `selection.range` è un `FluidDateRange` |
| i pallini o il numero sotto i giorni, che arrivano dalla rete un mese alla volta | `val marks = remember { FluidCalendarMarks() }`, poi `marks.setMonth(mese, mappa)` e `FluidCalendar(…, marks = marks)` |
| sapere quale mese si vede, per chiederne i segni | `val state = rememberFluidCalendarState()`, `LaunchedEffect(state.month) { … }` |
| saltare a un mese lontano | il titolo: lo tocca l'utente e sceglie mese e anno con le ruote; da codice `state.showMonth(mese)` o `state.showMonthYearPicker(true)` |
| la striscia della settimana quando la lista sotto scorre | `LazyColumn(Modifier.nestedScroll(state.nestedScrollConnection))` sotto il calendario |
| limiti, giorni spenti | `minDate`, `maxDate`, `isDateEnabled = { festivo(it).not() }` |
| la data in una riga, che apre il calendario | `FluidDateCapsule(FluidDateSelection.Single(giorno), onSelectionChange = { … }, label = "Scadenza")` |
| un periodo in una riga («23–30 nov 2026») | `FluidDateCapsule(FluidDateSelection.Range(a, b), …)` |
| il popover che si chiude appena il giorno è scelto (di serie resta aperto, come il `.compact` di iOS) | `FluidDateCapsule(…, closeOnSelection = true)` |
| una durata in una riga («45 min»), con la ruota del timer | `FluidDurationCapsule(seconds, onSecondsChange = { … }, minuteInterval = 5)` |
| far scegliere una ricorrenza | `FluidRecurrenceSheet(visible, onDismissRequest, onConfirm = { regola -> … }, start = giorno)` |
| espandere una regola in date, o dirla in una frase | `regola.occurrences(inizio, limit = 5)`, `FluidRecurrenceText.describe(regola, inizio, locale)` |
| salvare o leggere una regola come RFC 5545 | `regola.toRRule()`, `FluidRecurrenceRule.parseRRule("FREQ=MONTHLY;BYDAY=-1FR")` |
| una data **e un'ora** in un foglio, con proposte rapide | `FluidDateTimeSheet` ([menu-modali.md](menu-modali.md)) |
| **non così**: un `DatePicker` di Material | non ha il gesto, la striscia, i segni né il vetro dell'engine |

## Il calendario in pagina: `FluidCalendar`

**Cos'è.** `DatePicker` con lo stile `.graphical`, `MultiDatePicker` e `UICalendarView` con le decorazioni, più la
striscia della settimana dell'app Calendario. Una testata col mese («novembre 2026») e le frecce nell'accento, le lettere
dei giorni dal primo della lingua, sei righe sempre (cambiare mese non cambia l'altezza).

**Firma essenziale.**
```kotlin
FluidCalendar(
  selection: FluidDateSelection,                  // Single(date) | Multiple(dates) | Range(start, end)
  onSelectionChange: (FluidDateSelection) -> Unit,
  modifier: Modifier = Modifier,
  state: FluidCalendarState = rememberFluidCalendarState(…),   // il mese mostrato, la striscia, il collegamento con la lista
  marks: FluidCalendarMarks? = null,              // i segni sotto i giorni
  minDate: LocalDate? = null, maxDate: LocalDate? = null,
  isDateEnabled: ((LocalDate) -> Boolean)? = null,
  today: LocalDate = remember { LocalDate.now() },
  locale: Locale = LocalConfiguration.current.locales[0],
  labels: FluidCalendarLabels = FluidCalendarLabels(),   // FluidCalendarLabels.english()
)
```

**La scelta.** Il calendario non tiene la scelta: chiama `onSelectionChange` e mostra quella che riceve.
- `Single`: il tocco sceglie il giorno.
- `Multiple`: il tocco accende o spegne il giorno; i giorni di un altro mese restano.
- `Range`: il primo tocco fissa l'inizio; il secondo la fine se viene dopo (lo stesso giorno: un intervallo di un giorno);
  un secondo tocco **prima** dell'inizio ricomincia da lì, e un tocco a intervallo completo ricomincia da capo. I giorni
  in mezzo sono una fascia nell'accento al 12%, arrotondata ai bordi delle righe e del mese; i due estremi sono pieni.

**L'aspetto** (misure di `DatePicker` e delle HIG, `FluidCalendarDefaults`):
- la colonna è la larghezza diviso sette, al più 64 dp; cerchio e riga sono 0,82 della colonna, mai sotto i 44 dp;
- il giorno scelto ha il cerchio nell'accento al 12% e il numero nell'accento, semibold e più grande del 7%;
- oggi ha il numero nell'accento senza cerchio; oggi scelto, il cerchio pieno col numero bianco.

**Il mese cambia col dito.** La pagina segue il dito, nitida (niente sfocatura: la muove il dito), e si posa con
`FluidDetentInertia` sul mese giusto, al più uno più in là, come una pagina di `UIScrollView`. Oltre `minDate` e
`maxDate` resiste come una lista di iOS. Il dito che arriva mentre la pagina si posa la prende al volo. Le frecce fanno lo
stesso moto. I tre mesi (prima, questo, dopo) sono già composti; quelli vicini sono muti per TalkBack.

**Le ruote del mese e dell'anno.** Il titolo («novembre 2026 ›») è un tasto, come in `DatePicker(.graphical)`: toccato
passa all'accento, la freccia gira verso il basso e al posto della griglia ci sono due ruote, i mesi e gli anni (la ruota
di [controlli-gesto.md](controlli-gesto.md), nell'ordine della lingua: l'anno prima in giapponese, cinese, coreano e
ungherese). Girando cambiano il mese mostrato, e la testata lo dice subito; un altro tocco sul titolo torna alla griglia di
quel mese. La scelta non cambia. Le ruote stanno nell'altezza della griglia, quindi niente sotto si sposta; le frecce del
mese spariscono ma tengono il loro posto. I mesi fuori da `minDate` e `maxDate` sono spenti; gli anni vanno dai limiti, o
da cento anni prima a cento anni dopo oggi.
- Lo scambio fra griglia e ruote lo fa il tocco, cioè l'app: entra sfocato (`FluidGlassContentTransition`). Le ruote, che
  muove il dito, restano nitide. La freccia gira con la molla dei tasti (0,30 s, `FluidMotion.snappy`), a occhio sul moto
  di iOS.
- TalkBack: il titolo si espande e si comprime, col doppio tocco «Scegli mese e anno» o «Torna ai giorni»; le ruote si
  regolano con «Aumenta» e «Diminuisci».
- Con le ruote aperte la lista sotto non stringe la griglia; nella striscia il titolo è solo un titolo.
- Riduci movimento: le ruote compaiono e se ne vanno di colpo, la freccia è già girata.
- `state.isMonthYearPickerShown` dice se sono aperte, `state.showMonthYearPicker(vero o falso)` le apre e le chiude.

**La striscia della settimana.** Date alla lista sotto il calendario `state.nestedScrollConnection`: la lista che scorre in
su stringe prima la griglia, fino alla settimana del giorno di riferimento (il giorno scelto, o oggi, o il primo del
mese); in cima, tirata giù, la riapre. A dito lasciato la striscia si posa aperta o chiusa, mai a metà. Nella striscia lo
scorrimento di lato cambia **settimana**, e la testata dice il mese di quella settimana; i giorni degli altri mesi della
riga entrano mentre la griglia si stringe. `state.collapse()` e `state.expand()` la muovono da codice.

**I segni.** `FluidCalendarMark(count, style = Dots | Count, color, description)`: da uno a tre pallini (5 dp) o un numero
piccolo, dentro l'altezza della riga. `FluidCalendarMarks` tiene uno stato per giorno: un mese che arriva (`setMonth`)
scrive solo i giorni che cambiano e **ricompone solo quei giorni**; la griglia non salta. Il segno che arriva entra
sfocato e si mette a fuoco (260 ms, `FluidTextMotion.WordArrivalMillis`): lo muove l'app, non il dito.

**Testo grande.** Se sette celle da 48 dp per la scala del carattere non ci stanno, la griglia scorre di lato (la regola
delle sette colonne del foglio) e il mese cambia con le frecce. Vale anche nel popover.

**TalkBack.** Ogni giorno è un nodo: «25 novembre, 3 impegni» (con l'anno se non è quello di oggi, con «oggi»), selezionato
se scelto, con «inizio dell'intervallo», «fine dell'intervallo» o «nell'intervallo». Le frecce cambiano mese o settimana.

**Riduci movimento.** La pagina e la striscia vanno subito al loro posto; i segni compaiono fermi.

**Il costo.** Il dito e la molla scrivono stati letti solo nel disegno (`translationX`) e nella misura (l'altezza della
striscia): durante il cambio di mese nessun giorno si ricompone, e all'arrivo si compone un mese solo, quello nuovo. Il moto
non alloca per fotogramma. Fermo, il calendario non scrive niente.

## La capsula: `FluidDateCapsule` e `FluidDurationCapsule`

**Cos'è.** `DatePicker` con lo stile `.compact`: la data in una riga («23 nov 2026», la data media della lingua) in una
capsula grigia (34 dp, 13 dp di margine, `tertiarySystemFill` al 12%, testo a 17 sp). Toccata, **diventa il popover** del
calendario: è il popover dalla sorgente dell'engine (F5B.1), che prende in prestito la capsula per tutta la corsa e la
restituisce al ritorno; anche sul telefono resta un popover (`FluidCompactAdaptation.Popover`).

- Di serie il popover **resta aperto finché si tocca fuori**, come `DatePicker(.compact)` di iOS (F8.8b, scelta di
  Alessio): la capsula scrive subito il valore scelto, «23 nov 2026», «23–30 nov 2026», «23 nov – 5 dic 2026» o «3 giorni».
  Con `closeOnSelection = true` si chiude appena la scelta è fatta: `Single` al tocco del giorno, `Range` alla fine;
  `Multiple` resta aperto comunque.
- `label` è quello che legge TalkBack prima del valore; `text` sostituisce il testo di serie.
- `FluidDurationCapsule` è la stessa capsula per una durata («45 min», «1 h 30 min»): il popover ha la ruota del timer
  (`FluidDurationWheel`, la `UIDatePicker` in modo `countDownTimer`) con l'aptica a scatti, e si chiude toccando fuori.
  La capsula scrive la durata dentro i limiti della ruota (R2): -90 s dicono «1 min», il minimo che la ruota mostra.

## Il pannello «Ripeti»: `FluidRecurrenceSheet`

**Cos'è.** La schermata Ripetizione di Calendario e Promemoria, già pronta, in un `FluidGlassSheet` (a metà e in cima, che
nasce dalla `source` se c'è). In cima le scelte pronte (mai, ogni giorno, ogni settimana, ogni due settimane, ogni mese,
ogni anno, «Personalizza»). Personalizza apre la ruota «Ogni [N] [giorni | settimane | mesi | anni]» e, secondo la
frequenza, i giorni della settimana in capsule (l'ultimo acceso non si spegne) o il giorno del mese («il 23», «il quarto
martedì», «l'ultimo martedì», presi dall'inizio). Poi la fine (mai, in una data con la capsula, dopo N volte), la **frase**
(«Ogni mar e gio fino al 20 dicembre») e le **prossime date esatte**. Frase e date le cambia la regola, non il dito: cambiano
sfocate (`FluidGlassContentTransition`).

**Aperto e chiuso con «Fine» senza toccare niente, restituisce la regola com'era** (R2): la bozza sa dire solo una parte
di una regola ricca (giorni del mese, mesi, posizioni), e prima «ogni mese il 15» tornava «ogni mese il giorno
dell'inizio». **La bozza resiste alla rotazione e al processo ucciso** (`rememberSaveable`), come il calendario accanto.

```kotlin
FluidRecurrenceSheet(
  visible: Boolean, onDismissRequest: () -> Unit,
  onConfirm: (FluidRecurrenceRule?) -> Unit,      // null = «Mai»
  start: LocalDate, initialRule: FluidRecurrenceRule? = null,
  locale: Locale = …, labels: FluidRecurrenceLabels = FluidRecurrenceLabels(),   // .english()
  source: FluidTransitionSource? = null, previewCount: Int = 5,
)
```

**La regola** è un dato puro (`FluidRecurrenceRule`, come `EKRecurrenceRule` e la `RRULE` di RFC 5545) che l'app salva e
l'engine espande: i casi limite (il 31 del mese, il 29 febbraio, «l'ultimo venerdì», il quinto lunedì, l'ora legale) sono
provati una volta in `engine-foundation`. L'espansione lavora su date locali, mai su millisecondi sommati.

**Giorni del mese e giorni della settimana insieme si intersecano**, come vuole RFC 5545 (§ 3.3.10, nota 1: in una regola
mensile `BYDAY` con `BYMONTHDAY` *limita*): `FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13` è «ogni venerdì 13», l'esempio della
RFC. Il numero del giorno conta anche lì (R2): `BYDAY=2FR;BYMONTHDAY=1,…,14` è solo il secondo venerdì, e
`BYMONTHDAY=15;BYDAY=-1FR` non cade mai (prima l'engine dava i mesi in cui il 15 è un venerdì). **La frase dice i
limiti** (R2): «Ogni mese il 13, se è venerdì», «Ogni lunedì» per una regola giornaliera solo il lunedì (prima «Ogni
giorno»), «Ogni giorno (gennaio)», «Ogni lunedì (luglio e agosto)». Un intervallo di giorni che finisce a
`LocalDate.MAX` si scorre fino in fondo.

## Token, costanti, Defaults

| nome | cosa | fonte |
|---|---|---|
| `FluidCalendarDefaults.MaxColumnWidth` | 64 dp | a occhio (24 novembre 2026) |
| `FluidCalendarDefaults.CircleFraction` | 0,82 della colonna | render di `graphicalStyle` (0,815) e HIG (0,86) |
| `FluidCalendarDefaults.DayFontSp`, `SelectedDayScale` | 20 sp, ×1,07 | `DatePicker` (media) |
| `FluidCalendarDefaults.SelectedTintAlpha`, `RangeTintAlpha` | 12% | render di SwiftUI e HIG (alta) |
| `FluidCalendarDefaults.PageResponseSeconds` | 0,38 s, critica | `FluidMotion.ResponseStandard` |
| `FluidCalendarDefaults.CollapseResponseSeconds` | 0,30 s, critica | come l'assestamento della ruota |
| `FluidDateCapsuleDefaults.Height`, `HorizontalPadding`, `Fill` | 34 dp, 13 dp, (118,118,128) al 12% | HIG e Calendario di iOS 26/27 |

## Cosa è verificato dai test

- `engine-foundation/…/FluidRecurrenceTest`: il 31, l'ultimo giorno del mese, il 29 febbraio (e il 2100), l'ultimo venerdì,
  il quinto lunedì, l'ultimo giorno feriale, il giorno del Ringraziamento, le settimane alterne col loro `WKST`, l'inizio che
  non rispetta la regola, la fine per data e per conteggio, l'ora legale (le 9 restano le 9, l'ora che manca, quella che si
  ripete), la `RRULE` avanti e indietro, la frase in italiano e in inglese, il primo giorno della settimana per lingua,
  l'intervallo che attraversa l'ora legale (71 e 73 ore, tre giorni). `R2F5aFoundationTest`: venerdì 13 come la RFC, il
  numero del giorno che limita, la frase coi limiti, l'intervallo fino a `LocalDate.MAX`.
- `engine-ui/…/F8p8CalendarTest`: i tocchi delle tre scelte, gli stili della fascia, dove si posa una pagina, la resistenza,
  il moto senza allocazioni per fotogramma, i segni che scrivono solo i giorni che cambiano, i testi della capsula.
- In `F8p8DatesUiTest` anche le ruote del mese e dell'anno: il titolo toccato, il mese col dito, l'anno con TalkBack, il
  ritorno alla griglia sul mese scelto, «Riduci movimento», e il popover che di serie resta aperto col giorno scelto e si
  chiude al tocco fuori, e con `closeOnSelection = true` si chiude col giorno.
- `sample/…/F8p8DatesUiTest` (Robolectric, dito vero): il mese trascinato e lanciato, nessun giorno ricomposto durante il
  gesto, le frecce, TalkBack sui giorni coi segni, i segni che ricompongono solo i giorni toccati, le tre scelte, la
  striscia e il ritorno, «Riduci movimento», la capsula che apre il popover e scrive subito il valore, la ruota della durata, il
  pannello «Ripeti» personalizzato. `R2F5aFieldsUiTest`: «Ripeti» aperto e chiuso restituisce la regola com'era, la bozza
  dopo la ricreazione, la capsula di una durata negativa.

## Dove guardare

- `engine-ui/…/fluid/FluidCalendar.kt` (disegno, gesto, stato), `FluidCalendarModel.kt` (scelta, segni, parole, misure),
  `FluidDateCapsule.kt`, `FluidRecurrenceSheet.kt`.
- `engine-foundation/…/foundation/FluidRecurrence.kt`, `FluidRecurrenceText.kt`, `FluidDates.kt`.
- Le scene: `sample/…/novita/F8p8Scenes.kt` (`calendario-agenda`, `calendario-giorni`, `calendario-intervallo`,
  `capsula-data`, `ripeti`).
