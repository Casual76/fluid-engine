<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/feedback.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-feedback

**Segnala un problema** (4.0, F8.24, idea `segnala-problema` rivista da Alessio): il feedback di TestFlight
(screenshot → «Condividi feedback beta») fatto **senza disturbare**: si apre solo quando l'utente lo chiede, dalla
pagina «Informazioni» o dal Laboratorio, **mai dopo uno screenshot**. Lo screenshot di una pagina dell'app da segnare col
dito (il Markup di [`engine-ink`](ink.md)), il campo «Cosa non va?», **tutto quello che parte in vista**, e due strade:

- **un file da mandare su WhatsApp**: uno ZIP con tutto (`LEGGIMI.txt` da leggere per primo, il JSON, lo screenshot
  segnato, il registro pulito), col foglio Condividi dell'engine e WhatsApp in cima;
- **una issue pubblica su GitHub**, già scritta nel browser, che l'utente manda dal suo account: GitHub gli dà i crediti
  della segnalazione, e il corpo lo ringrazia per nome se vuole.

Pacchetto `dev.antigravity.fluidengine.feedback`. **Opt-in**; porta `engine-ui`, [`engine-diagnostics`](diagnostics.md)
(il registro circolare `FluidLog`, il filtro dei dati personali, gli ultimi arresti) ed `engine-ink` (circa 1,3 MB di
libreria nativa per ABI). Nessun permesso, nessuna libreria nuova, nessun servizio.

Catalogo completo delle firme: [`../api/feedback.md`](../api/feedback.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il flusso nell'app | `val feedback = rememberFluidFeedbackState(FluidFeedbackConfig("Pampa Notes", github = FluidFeedbackGitHub("Casual76", "pampa-notes")), lab = lab)` e `FluidFeedbackHost(feedback) { App() }` **una volta**, attorno al `FluidNavHost` |
| la riga in «Informazioni» | `FluidFeedbackRow()` in un `FluidListGroup` (legge `LocalFluidFeedback`) |
| aprirlo dal Laboratorio | `FluidLabPage(lab, onBack, onReportProblem = { feedback.open() })` |
| aprirlo da un tasto mio | `LocalFluidFeedback.current?.open()` |
| solo il file per WhatsApp | non offrire `github` (`FluidFeedbackConfig(appName)`) |
| le funzioni in prova nella segnalazione | passare `lab` a `rememberFluidFeedbackState` |
| gli ultimi arresti nella segnalazione | la diagnostica installata nel processo (di serie `FluidDiagnostics.get()`) |
| il testo o l'indirizzo della issue lontano dal foglio | `FluidFeedbackText.issueBody(draft, info)`, `FluidFeedbackText.issueUrl(draft, info, github)` |
| lo ZIP lontano dal foglio | `FluidFeedbackFile.writeToFile(context, draft, info)`; i file senza scrivere niente: `FluidFeedbackFile.entries(draft, info, screenshot)` |
| le parole in inglese | `FluidFeedbackLabels.english()` |
| **non così**: un `registerScreenCaptureCallback` che chiede «qualcosa non va?» | è la cosa che Alessio non vuole: un'app che si rompe a ogni screenshot |

## Il giro

1. **Il foglio** (di vetro): «Cosa non va?», lo screenshot, «Cosa parte», il nome per i ringraziamenti (facoltativo),
   e i due tasti. Senza una frase o uno screenshot i tasti sono spenti.
2. **«Cattura una pagina»**: il foglio esce e in basso resta una capsula di vetro («Vai alla pagina col problema, poi
   tocca qui», «Cattura» e la «x»). L'utente va dove vuole; al tocco su «Cattura» la capsula non si disegna in quel
   fotogramma e la finestra si copia con `PixelCopy` (`FluidFeedbackCapture`): niente permessi, niente altre app.
   Oppure **«Scegli dalle foto»** col selettore di Android. La sorgente del vetro della capsula si registra solo mentre
   la capsula c'è (F7: niente sorgenti a schermo intero sempre accese).
3. **La segnatura**: lo screenshot si apre nel `FluidInkEditor` (penna, evidenziatore, gomma, forme). Al ✓ il PNG segnato
   prende il posto della bozza e la miniatura **entra sfocata** (l'ha cambiata l'app). Sotto c'è l'avviso: lo
   screenshot può mostrare dati tuoi, coprili col pennarello.
4. **«Cosa parte»**: un interruttore per lo screenshot, l'app e il telefono, le funzioni in prova accese, gli ultimi
   arresti e gli ultimi eventi del registro **coi dati personali tolti**; «Mostra tutto quello che parte» mostra il
   testo esatto. Spento vuol dire che non c'è, né nel file né nella issue. Con «L'app e il telefono» spento resta solo
   il nome dell'app, che dice di che app è la segnalazione (è anche nel nome dello ZIP): via la versione, il pacchetto,
   il codice, il tipo di build e la versione dell'engine, in `LEGGIMI.txt`, in `segnalazione.json` (`app` ha solo
   `name`) e nel titolo della issue (R3).
5. **Le due strade**:
   - **«Manda un file»**: lo ZIP `Segnalazione-pampa-notes-20261126-1403.zip` (scelta di Alessio, F8.24b) e il foglio
     Condividi con l'azione «WhatsApp» in cima quando WhatsApp c'è; WhatsApp lo manda come documento. Dentro, nell'ordine:
     `LEGGIMI.txt` (il primo: la frase, l'app e il telefono, le funzioni in prova, gli arresti, i ringraziamenti, cosa c'è
     negli altri file, coi fine riga di Windows), `segnalazione.json` (gli stessi dati per i programmi, `schema` 1),
     `screenshot.png` (lo screenshot segnato) e `registro.txt` (gli ultimi eventi). Le parti spente non ci sono in nessun
     file. Lo stesso ZIP scritto due volte ha gli stessi byte (le voci hanno l'ora della segnalazione).
   - **«Apri una issue su GitHub»**: la pagina «nuova issue» con titolo e corpo già scritti (la frase, la tabella del
     telefono, le funzioni in prova, gli arresti, gli eventi in un blocco richiudibile, i ringraziamenti). Lo screenshot
     va nella cartella Download (MediaStore, Android 10+, senza permessi) e il corpo dice di allegarlo. Il browser lo
     apre Android; la issue la manda l'utente.
6. Partita, la bozza si dimentica. Chiuso il foglio senza mandare, la bozza resta per la prossima volta.
   Dimenticare la bozza (`discard`) cancella anche lo screenshot di lavoro (in `cacheDir/fluid-feedback`; un file dell'app
   messo nella bozza da lei resta), e alla riapertura del foglio si cancellano quelli rimasti da un processo ucciso
   prima che la bozza partisse (R3). Lo ZIP e la copia in Download sono già fatti, altrove.

## I dati personali

- Il registro (`FluidLog`) esce solo filtrato (`FluidPrivacyScrubber`: mail, telefoni, IBAN, carte, codici fiscali, IP,
  chiavi e token, id, percorsi e parametri degli indirizzi, file). La frase dell'utente e il nome passano dallo stesso
  filtro: **la issue è pubblica**.
- Gli arresti portano solo l'ora, il tipo e il primo passaggio del codice dell'app.
- Lo screenshot non si può filtrare: lo segna l'utente, e il foglio glielo dice.

## La issue su GitHub

`FluidFeedbackText.issueUrl` scrive `https://github.com/<owner>/<repo>/issues/new?title=…&body=…` (lo spazio come `%20`,
mai `+`). GitHub rifiuta gli indirizzi troppo lunghi: oltre `MaxIssueUrlLength` (7 500 caratteri) si tolgono prima gli
eventi più vecchi, poi gli arresti, poi la coda della frase. Le etichette nell'indirizzo (`labels`) valgono solo per chi
può già etichettare la repo; un modello della repo (`template = "segnalazione.md"`, in `.github/ISSUE_TEMPLATE/`) le mette
a tutti. **La repo dev'essere pubblica**, o chi segnala vede un 404.

## Trappole

- **`FluidFeedbackHost` una volta sola, alla radice** (dentro `FluidTheme`, attorno al `FluidNavHost`): la capsula deve
  restare sopra mentre l'utente cambia pagina. Il foglio e il foglio Condividi sono portali: serve il
  `FluidGlassModalHost`.
- **WhatsApp in cima** solo se è installato: il manifesto del modulo dichiara `com.whatsapp` e `com.whatsapp.w4b` in
  `<queries>` (da Android 11 un'app vede solo i pacchetti che dichiara). Senza, WhatsApp resta in «Altre app…».
- **Lo ZIP sta nella cartella del foglio Condividi** (`FluidShareFiles`, nella cache): Android la svuota quando serve, e
  `FluidShareFiles.clean` toglie le uscite di più di un giorno. Il tipo è `application/zip` (`FluidFeedbackFile.MimeType`).

## Prove

- `engine-feedback/src/test` (Robolectric): `FluidFeedbackTest` (cosa si raccoglie, col registro filtrato e le funzioni
  in prova; la issue precompilata coi ringraziamenti; quello che è spento non parte; il tetto dell'indirizzo che toglie
  prima gli eventi più vecchi; lo ZIP con `LEGGIMI.txt` per primo, il JSON, lo screenshot al byte e il registro pulito; le
  parti spente che non sono in nessun file; **i dati personali tolti anche dentro lo ZIP**; i nomi dei file).
  R3 (review `sol-ai`): `FluidFeedbackR3Test` (gli IPv6 compressi che non entrano nello ZIP, dal registro, dalla frase e
  negli indirizzi web; lo screenshot di lavoro che se ne va con la bozza, e il file dell'app che resta; «L'app e il
  telefono» spento senza i dati dell'app, acceso con tutti).
- `sample/src/testDebug/.../novita/F8p24FeedbackR3Test.kt` (R3): lo screenshot di un processo morto si cancella alla
  riapertura del foglio, quello di questo processo resta.
- `sample/src/testDebug/.../novita/F8p24DiagnosticsUiTest.kt`: il foglio chiuso finché non lo si chiede, i tasti spenti
  senza frase, l'anteprima di quello che parte coi dati tolti, l'interruttore che toglie gli eventi, la issue aperta nel
  browser già scritta, lo ZIP che arriva al foglio Condividi coi dati tolti, la capsula della cattura e «Annulla».
- Sul tablet (F8.24): la segnalazione con lo screenshot segnato e il foglio Condividi verso WhatsApp senza inviare.

## Dove guardare

`engine-feedback/src/main/kotlin/dev/antigravity/fluidengine/feedback/`: `FluidFeedback.kt` (la configurazione, la bozza,
cosa si raccoglie, il testo e l'indirizzo della issue), `FluidFeedbackFile.kt` (lo ZIP, Download, la cattura con
`PixelCopy`), `FluidFeedbackUi.kt` (lo stato, l'ospite, la capsula, la segnatura, il foglio, la riga),
`FluidFeedbackLabels.kt`.

Installazione: `engine-install.ps1 -AppRoot . -Modules engine-feedback` (opt-in; porta `engine-diagnostics`, `engine-ink`,
`engine-ui` e `engine-foundation`). Nel `build.gradle` dell'app: `implementation project(':engine-feedback')`.
