<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/share-in.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-share-in

**Gli arrivi da altre app** (4.0, F8.10, `arrivi-da-altre-app`): il verso opposto del foglio di condivisione. Quando in
un'altra app si tocca «Condividi» e si sceglie un'app Pampa, compare un piccolo foglio sopra l'app di partenza:
l'anteprima di ciò che arriva, dove metterlo, «Salva». Come la Share Extension di iOS. Pacchetto
`dev.antigravity.fluidengine.sharein`.

È un **modulo opt-in** su `engine-ui`, senza librerie nuove (le scorciatoie di condivisione vengono da `androidx.core`,
che `engine-ui` porta già). **L'engine** legge in un modo solo tutte le forme in cui Android consegna, copia subito i
file prima che il permesso scada (con l'avanzamento e un tetto), mette i limiti di peso e di tipo e disegna il foglio.
**L'app** dichiara cosa accetta (i filtri nel manifest e la `FluidShareInPolicy`) e fa il salvataggio vero.

Catalogo delle firme: [`../api/share-in.md`](../api/share-in.md) (generato). Il foglio per mandare fuori è in
[`../componenti/condivisione.md`](../componenti/condivisione.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un'attività che riceve | tema `@style/Theme.FluidShareIn`, `FluidShareInWindow.install(this)` in `onCreate`, poi `FluidShareInSheet` |
| leggere cosa è arrivato | `FluidShareIn.read(context, intent, policy)`: `Text`, `Link`, `Files` o `Unsupported` col motivo |
| copiare i file subito, con l'avanzamento | `FluidShareIn.copy(context, files, policy) { progress -> … }` |
| tutto insieme, dentro Compose | `val state = rememberFluidShareInState(intent, policy)` |
| il foglio con l'anteprima, le destinazioni e «Salva» | `FluidShareInSheet(state, destinazioni, onSave = { dove, contenuto -> … }, onClose = { finish() }, title = "Notes")` |
| solo foto e PDF, fino a 50 MB | `FluidShareInPolicy(acceptedTypes = listOf("image/*", "application/pdf"), maxFileBytes = 50 * MB)` |
| le ultime destinazioni in alto nel foglio di Android | `FluidShareInShortcuts.publish(context, destinazione, MiaAttività::class.java, categoria)` dopo il salvataggio, e lo `<share-target>` nel manifest |
| sapere da quale scorciatoia si è arrivati | `FluidShareInShortcuts.destinationOf(intent)` → `initialDestination` |
| entrare nel menu del testo selezionato («Aggiungi a Notes») | un filtro `PROCESS_TEXT` sull'attività: arriva come `FluidIncoming.Text(selection = true)` |

## Le forme di Android

| azione | cosa arriva | `FluidIncoming` |
|---|---|---|
| `SEND` con `EXTRA_STREAM` (o solo nel `ClipData`) | un file | `Files` (uno), col testo che lo accompagna |
| `SEND` con `EXTRA_TEXT` | un testo, o un link («Titolo https://…» come lo manda Chrome) | `Text` o `Link` (il titolo dall'oggetto) |
| `SEND_MULTIPLE` | tanti file (dall'extra e dal `ClipData`, senza doppioni) | `Files` |
| `PROCESS_TEXT` | il testo selezionato in un'altra app, sola lettura o no | `Text(selection = true)` |
| `VIEW` (`content://`) | «apri con» | `Files(viewed = true)` |
| `VIEW` (`https://`) | un link | `Link` |
| altro, vuoto | niente | `Unsupported(NotAShare / Empty)` |

**Mai letti** (`Unsupported(UnsafeUri)`): un `file://` e un Uri servito dall'app stessa. Il secondo è il modo in cui
un'app cattiva fa copiare a un'altra app i suoi file privati; se l'app ha un'autorità sua che può ricevere davvero, la
dichiara in `trustedAuthorities`. «Servito dall'app stessa» lo dice il `PackageManager` (`resolveContentProvider`),
quindi vale anche per un provider dell'app con un'autorità che non comincia col nome del pacchetto; il prefisso del
pacchetto resta come ripiego (R2).

**Fra tanti.** In un `SEND_MULTIPLE` con file buoni e Uri insicuri, i buoni arrivano e gli insicuri **restano nella
lista**, mai aperti né chiesti al loro provider: la copia li rifiuta con `UnsafeUri` e la riga di stato li nomina («Non
arrivati: segreto.jpg (non sicuro)»), invece di farli sparire (R2). Tutti insicuri: `Unsupported(UnsafeUri)` come prima.

**Un tipo sbagliato.** Un intent che mette in `EXTRA_STREAM` un oggetto che non è un Uri (prima di Android 13 le letture
non controllano il tipo, e nemmeno quelle nuove gli elementi di una lista non ancora letta da un Parcel) non fa cadere
l'attività: si tengono solo gli Uri (R2).

## La copia

`FluidShareIn.copy` lavora su `Dispatchers.IO` (mai sul thread principale), un file alla volta, nella cartella
`cacheDir/fluid-share-in/<arrivo>/`:
- **prima** scarta ciò che la politica non accetta: il tipo, il numero (`TooMany`), il peso dichiarato oltre il tetto
  (`TooLarge`, senza nemmeno aprire il file);
- **durante** conta i byte: un file che dice di pesare poco, o non lo dice, e supera il tetto si ferma lì e la copia a
  metà si cancella; lo stesso per il totale;
- un file che non si legge (il permesso scaduto, l'app che non risponde, un provider guasto che lancia
  un'eccezione qualsiasi: `IllegalStateException`, `NullPointerException`, …) diventa `Unreadable`, e gli altri
  continuano (R2: prima un'eccezione «strana» faceva cadere tutta la ricezione);
- un Uri insicuro non si apre mai, anche se arriva da `copy` chiamata a mano: `UnsafeUri`;
- l'avanzamento arriva al più ogni 256 KB e alla fine di ogni file.

Il permesso di lettura vale finché l'attività che ha ricevuto vive: per questo la copia parte appena lo stato si compone
(`rememberFluidShareInState`), non al «Salva». La cartella dell'arrivo si salva con lo stato: dopo una rotazione l'arrivo
si ricopia **nella stessa cartella**, con gli stessi nomi, e non restano copie orfane (R2).

**La pulizia è dell'app.** L'engine non cancella mai da solo gli arrivi: `FluidShareInFiles.clean(context)` (fuori dal
thread principale, di serie quelli più vecchi di un giorno) va chiamata dall'app, per esempio all'avvio con
`FluidShareFiles.clean`. Senza, le copie restano nella cache finché Android non la svuota.

## Il foglio: `FluidShareInSheet`

In cima «Annulla», il titolo (il nome dell'app) e «Salva», come la Share Extension; sotto l'anteprima (il testo, il link
col dominio, i file con il loro stato), la riga di stato («Copio 2 di 3…», «Non arrivati: video.mp4 (troppo grande)»),
la barra dell'avanzamento, e le destinazioni (la prima, o quella della scorciatoia). «Salva» chiama `onSave` (il
salvataggio dell'app, sospeso): «Salvo…», la spunta, poi `onClose`. Un errore lascia il foglio aperto con «Non si è
salvato». **Mentre salva, niente chiude il foglio**: «Annulla» è spento, il velo e il dito non lo chiudono (il foglio
torna su), perché l'attività che si chiude cancellerebbe il salvataggio a metà senza dirlo (R2). La destinazione scelta
si salva per chiave e resta dopo una rotazione.

- **Vetro fermo.** Il foglio vive nella finestra trasparente di un'attività sopra un'altra app: lì il vetro non può
  piegare ciò che sta sotto, perché l'altra app non si cattura. Il foglio è il materiale del modale dell'engine sopra il
  velo dei fogli (23%, 31% sullo scuro), e dietro la finestra Android sfoca l'altra app (`FLAG_BLUR_BEHIND`, 24 dp) dove
  può (Android 12 e oltre, sfocatura fra finestre accesa). Dove non può, il film è pieno.
- **Sotto il bordo.** Il foglio continua 48 dp sotto il bordo dello schermo, come i fogli dell'engine: il bordo in basso
  del vetro, che campiona oltre se stesso, resta fuori vista, e la barra di navigazione sta dentro il materiale. Il
  contenuto resta sopra la barra coi suoi margini.
- **La barra di navigazione trasparente.** Come nella finestra principale delle app dell'engine: niente velo di contrasto
  (su una barra chiara Android stende un bianco al 90% sotto la maniglia dei gesti, sul tablet il 25 novembre una fascia
  sopra il foglio). Lo spengono il tema `Theme.FluidShareIn` (`enforceNavigationBarContrast`) e
  `FluidShareInWindow.install`, che costruisce la decorazione della finestra prima di spegnerlo: costruita dopo, da
  `setContent`, la rileggerebbe dal tema. Con un tema dell'app, chiama `install` prima di `setContent` e basta.
- **Il movimento.** Sale dal basso con la molla dei fogli (`FluidMotion.PopoverOpenY`) e scende con una molla critica; il
  dito lo porta giù e lo chiude oltre 110 dp o con un colpo oltre 900 dp/s, come `FluidGlassSheet`. Con «Riduci
  movimento» è fermo al suo posto dal primo fotogramma.
- **Accessibilità.** Il foglio ha il nome del pannello (il titolo); la riga di stato è una regione viva per TalkBack; i
  file hanno il loro nome.
- Lo stato e le anteprime li cambia l'app (la copia che avanza, il salvataggio): entrano sfocati. Le destinazioni le
  sceglie il dito.

## Nel manifest dell'app

```xml
<activity android:name=".RiceviActivity" android:exported="true" android:theme="@style/Theme.FluidShareIn"
    android:excludeFromRecents="true" android:taskAffinity="">
  <intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="text/plain" />
    <data android:mimeType="image/*" />
  </intent-filter>
  <intent-filter>
    <action android:name="android.intent.action.SEND_MULTIPLE" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="image/*" />
  </intent-filter>
</activity>
```

e, per le scorciatoie, `res/xml/shortcuts.xml` con `<share-target android:targetClass="…RiceviActivity">` (gli stessi
tipi, la `category` passata a `FluidShareInShortcuts.publish`) e il meta-data `android.app.shortcuts` sull'attività di
lancio. L'ordine dei suggerimenti lo decide Android, non l'app.

## Verifica

`sample/src/testDebug/…/novita/F8p10ShareInTest.kt`, Robolectric con un `ContentProvider` finto «altra app»: un file
col nome e il peso, copiato fuori dal thread principale con l'avanzamento; tanti file con uno negato
(`SecurityException`) che non ferma gli altri; testo selezionato, link col titolo, testo, «apri con»; i motivi del rifiuto
(azione sconosciuta, vuoto, `file://`, Uri dell'app stessa salvo fidato); il file dichiarato enorme mai aperto e quello
bugiardo fermato al tetto senza resti; tipi e numero dalla politica; il foglio col dito vero (la destinazione e «Salva»,
i file arrivati al salvataggio, lo stato per TalkBack, il trascinamento che lo chiude) e «Riduci movimento». Dopo la
review R2: un provider che lancia `IllegalStateException` perde solo il suo file; un `file://` fra tre arrivi resta nella
lista come `UnsafeUri` e non si apre; un oggetto che non è un Uri in `EXTRA_STREAM` non fa cadere niente; un provider
dell'app con un'autorità senza il nome del pacchetto è dell'app; durante il salvataggio né «Annulla» né il dito chiudono
il foglio; dopo una rotazione (`StateRestorationTester`) la destinazione resta e la copia non apre una cartella nuova.
Le regole pure (tipi, link, nomi) sulla JVM in `engine-share-in/src/test`.
