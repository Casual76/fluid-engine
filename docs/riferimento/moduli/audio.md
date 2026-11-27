<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/audio.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-audio

**La cattura del registratore** (F8.13, idea `registratore-vocale`): registra un memo in **un file solo**, con **pausa e
ripresa** (la ripresa scrive in coda, senza buchi), l'**onda dal vivo**, il **livello che non satura** e l'avviso quando il
suono taglia, le **interruzioni** del telefono (una chiamata, un'altra app che suona: va in pausa da sola), il **taglio dei
silenzi** in testa e in coda, e l'**onda di un file** già registrato. AAC in M4A col codificatore di Android, o WAV. Pacchetto
`dev.antigravity.fluidengine.audio`. **Opt-in**, senza librerie e senza Compose: il tasto rosso, il pannello e l'onda stanno
in `engine-ui` ([controlli-gesto](../componenti/controlli-gesto.md), «L'audio: l'onda e il registratore») e leggono
l'inviluppo puro di [foundation](foundation.md) (`FluidAudioEnvelope`, `FluidAudioLevel`, `FluidAudioSilence`).

**Apple.** Memo Vocali di iOS 26: registrare a pezzi con la pausa, «Se l'altra app comincia a suonare, Memo Vocali smette di
registrare», la qualità «Compressa» (AAC) o «Senza perdita». Non c'è un componente di SwiftUI.

**Chi lo usa.** Pampa Notes (le lezioni registrate, F12), PampAI (le domande a voce, oggi con la cattura di `engine-ai`),
Pampa Sounds (un suono proprio), Pampa Mail (un messaggio vocale). L'app dà **dove salvare**, **il permesso del microfono**
(chiesto al tasto, con `FluidPermission.Microphone`) e **il servizio in primo piano** per registrare a schermo spento.

Catalogo delle firme: [`../api/audio.md`](../api/audio.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| registrare un memo col microfono del telefono | `val recorder = FluidAudioRecorder(context)`, poi `recorder.start(File(…, "memo.m4a"))` |
| pausa, ripresa, fine, scarta | `recorder.pause()`, `resume()`, `stop()` (sospende: torna la `FluidRecording`), `discard()` |
| non aspettare il microfono sul thread principale | i comandi da un altro thread, in fila: `scope.launch(comandi) { recorder.start(file) }` con `val comandi = Dispatchers.Default.limitedParallelism(1)` |
| una lezione lunga da non perdere se l'app muore | il WAV (`FluidRecordingQuality.Lossless`): si ascolta fino all'ultimo secondo |
| l'interfaccia di Memo Vocali | `FluidVoiceRecorder(phase, onRecord, onPause, onResume, onDone, recorder.envelope, recorder::elapsedMillis)` (engine-ui) |
| sapere dov'è | `recorder.status` (`FluidRecorderStatus`: `Idle`, `Recording`, `Paused(interrupted)`, `Finished`, `Failed`) |
| l'avviso «troppo forte» | `recorder.clipping` (vero per un secondo dopo un campione tagliato) |
| la qualità (voce per l'IA, standard, alta, senza perdita) | `FluidRecorderConfig().with(FluidRecordingQuality.High)`, o per una registrazione sola `recorder.start(file, FluidRecordingQuality.Lossless)` |
| far scegliere la qualità all'utente | `FluidRecordingQualityRow(selected, onSelected)` di engine-ui in un `FluidListGroup(glass = true)` (sotto, «La qualità») |
| il taglio dei silenzi | `FluidRecorderConfig(trimSilence = true)` |
| il microfono di un assistente (soppressore, guadagno) | `FluidRecorderConfig(microphone = FluidMicrophoneConfig.speech)` |
| l'onda di un file già registrato | `FluidAudioEnvelopes.load(file)` (quella salvata accanto, o decodificata e salvata) |
| l'onda di un audio qualunque per `Uri` | `FluidAudioEnvelopes.decode(context, uri)` |
| tagliare un file fra due tempi | `FluidAudioTrim.trim(file, format, fromMillis, toMillis)` |
| provarlo senza microfono | `FluidAudioRecorder(source = mioMicrofonoFinto, config)` con un `FluidPcmSource` |

## Come funziona

**La cattura.** `FluidMicrophone` apre `AudioRecord` a 16 bit mono, 48 kHz di serie, sulla sorgente `MIC` (come il telefono
la tara per registrare), **senza** soppressore e **senza** controllo del guadagno: il controllo automatico «pompa» la stanza
nelle pause, e i memo di Memo Vocali non lo hanno. Il thread della cattura è del registratore, alla priorità audio, e legge
blocchi da 20 ms: ogni blocco va nel file e diventa un valore dell'onda (`FluidAudioLevel.fromRms`, la scala in decibel che
si piega sopra il ginocchio e non tocca il soffitto). Il picco dei campioni a fondo scala accende `clipping`.

**La pausa** spegne il microfono (e l'indicatore della privacy di Android) e lascia il file aperto; la ripresa riapre il
microfono e scrive in coda: i tempi dei pacchetti AAC vengono dal conto dei campioni, quindi il file non ha buchi. Il tempo
(`elapsedMillis()`) è un orologio liscio mentre registra, tenuto entro un quarto di secondo dai campioni, ed è esattamente
quello del file in pausa e alla fine.

**Le interruzioni.** Il registratore chiede il fuoco audio (la musica di un'altra app si ferma invece di finire nel memo);
se un'altra app se lo riprende (una chiamata, un video) o se Android zittisce il microfono (da Android 10,
`isClientSilenced`), va **in pausa** con `interrupted = true`. Un microfono perso a metà o preso da un'altra app fa lo stesso:
il file scritto fin lì resta, e si può riprendere o finire.

**Se qualcosa si rompe** (R1, 26 novembre 2026). Un errore vero (il file che non si scrive, il codificatore che si ferma)
dà `Failed`, e il file scritto fin lì resta finché l'app decide: `stop()` lo chiude e lo consegna; `discard()`, `reset()` o
un nuovo `start()` lo buttano (scelta di Alessio del 26 novembre 2026: un file a metà che nessuno ha chiesto non resta sul
disco). La cattura comincia solo dopo che lo stato dice `Recording`: un
microfono perso o un file che non si scrive già al primo blocco danno `Paused` o `Failed`, mai un `Recording` fermo. Se l'apertura dell'AAC fallisce a metà (il contenitore su una
destinazione non scrivibile, il codificatore che rifiuta il formato) il codificatore e il contenitore già nati si liberano
subito; se la chiusura fallisce (`MediaMuxer.stop()` sull'indice finale, un disco pieno) si liberano lo stesso e `stop()`
torna null con `Failed`, invece di lanciare.

**Se il processo muore registrando** (il sistema chiude l'app, un crash altrove, la batteria). Il **WAV** resta sul disco e
si ascolta fino all'ultimo secondo: le due misure dell'intestazione si riscrivono ogni secondo di audio (senza `fsync`, che
sul thread della cattura potrebbe fermarlo oltre il mezzo secondo di riserva del microfono: un telefono che si spegne di
colpo può perdere gli ultimi secondi non ancora sul disco). L'**AAC** resta sul disco ma non si apre: il contenitore MP4
scrive il suo indice solo alla fine. All'app resta decidere cosa fare dei file rimasti a metà al prossimo avvio.

**La fine.** `stop()` chiude il file (il WAV riscrive le misure nell'intestazione; l'AAC svuota il codificatore e chiude il
contenitore), toglie i silenzi se `trimSilence` (`FluidAudioSilence.trimBounds`: la soglia segue il rumore della stanza, e
restano 300 ms prima della prima parola e dopo l'ultima; l'AAC si taglia copiando i pacchetti, senza ricodificare) e salva
l'onda accanto al file (`<file>.fluidwave`, un byte per valore: due ore sono 360 KB). Riaprendo il memo l'onda c'è subito.

**L'onda di un file.** `FluidAudioEnvelopes.load` legge prima quella salvata accanto (se non è più vecchia del file); se no
il WAV si legge da sé (PCM 8–32 bit, mono o stereo mescolati) e gli altri formati si decodificano con `MediaExtractor` e
`MediaCodec`, fuori dal thread principale.

## La qualità (F8.13b)

Quattro preimpostazioni in `engine-foundation` (`FluidRecordingQuality`), tutte mono a 48 kHz. Scelta di Alessio del 25
novembre 2026: di serie la via di mezzo, modificabile dall'app e, se l'app vuole, dall'utente.

| preimpostazione | formato | un'ora | per chi |
|---|---|---|---|
| `Voice` («Voce per l'IA») | AAC 64 kbit/s | circa 29 MB | una domanda o una nota da dare all'IA: i riconoscitori lavorano sul parlato a 16 kHz |
| `Standard` (di serie) | AAC 96 kbit/s | circa 44 MB | i memo e le lezioni |
| `High` («Alta») | AAC 128 kbit/s | circa 58 MB | quanto un canale di iTunes Plus (AAC 256 kbit/s stereo) |
| `Lossless` («Senza perdita») | WAV PCM 16 bit | circa 346 MB | chi vuole l'originale (in Pampa Notes, per esempio); Memo Vocali usa ALAC, che Android non codifica |

La qualità vale per il registratore intero (`FluidRecorderConfig().with(quality)`, e `config.quality` dice qual è) o per una
registrazione sola (`start(file, quality)`: l'estensione del file la sceglie l'app, `FluidRecordingFormat.of(quality).extension`).
`recordingConfig` è la configurazione della registrazione in corso. La riga per l'utente è `FluidRecordingQualityRow` di
engine-ui: il titolo, la qualità di adesso a destra, e al tocco un pop-up di vetro con le quattro e lo spazio di un'ora
(«AAC 96 kbit/s · circa 43,6 MB all'ora»). L'app salva l'`id` e lo rilegge con `FluidRecordingQuality.byId`.

## Trappole

- **Il servizio in primo piano è dell'app.** Senza, Android zittisce il microfono di un'app in sottofondo (registra
  silenzio). Per registrare a schermo spento serve un servizio di tipo `microphone` (Android 14 vuole il tipo e il permesso
  `FOREGROUND_SERVICE_MICROPHONE`), avviato mentre l'app è in primo piano. La pillola di attività dentro l'app c'è già:
  `FluidActivity.recording` di engine-ui.
- **Il permesso** RECORD_AUDIO è nel manifesto del modulo; va chiesto al tasto (`FluidPermission.Microphone`), mai
  all'avvio. Senza, `start()` è falso e lo stato è `Failed(Unavailable)`.
- **`stop()` sospende**: chiamala da una coroutine (`scope.launch { recorder.stop() }`); i comandi e lo stato sono sicuri
  da qualunque thread.
- **I comandi non tornano tutti subito** (R1): `start()` e `resume()` aspettano che il microfono si apra (qualche decina di
  millisecondi, di più su alcuni telefoni), `pause()` e `discard()` la lettura in corso (al più 20 ms). Dal thread
  principale è un'attesa breve al tocco; chi non la vuole li chiama da un altro thread, **in fila**
  (`Dispatchers.Default.limitedParallelism(1)`: due comandi in parallelo arriverebbero in un ordine qualunque), e legge
  l'esito da `status`.
- **La cattura di `engine-ai`** (`SpeechCapture`) resta dov'è per ora: quella decide da sola quando fermarsi e scrive un WAV
  breve per un riconoscitore. Scelta di Alessio del 25 novembre 2026: in F9 PampAI passa dal microfono di questo modulo, e
  la trascrizione resta in due modi (una frase breve col riconoscimento del telefono, una lunga con l'IA, se c'è).
- **Registrare a schermo spento**: il servizio in primo piano `microphone` arriverà in questo modulo in F12 (scelta di
  Alessio del 25 novembre 2026); fino ad allora è dell'app.

## Cosa è verificato dai test

`engine-audio/src/test/.../FluidAudioRecorderTest.kt`: con un microfono finto (un copione di toni e silenzi, poi silenzio a
tempo vero) la registrazione con pausa e ripresa scrive **un file solo** con l'intestazione giusta e tutti i campioni; in
pausa il microfono è spento e il tempo è fermo; un'interruzione va in pausa e si riprende; un microfono che non parte è
`Failed(Busy)`; un microfono perso a metà lascia il file e la pausa; il taglio dei silenzi lascia 0,6 s di voce più 300 ms per
lato, con l'onda tagliata insieme; il WAV stereo si legge in un'onda mescolata; un WAV che si sta registrando dice sul disco
quanto audio contiene, al più un secondo indietro; dopo una cattura fallita `reset()` e `start()` buttano il file a metà, e `stop()` lo
consegna chiuso (R1). `FluidAacWriterTest.kt` (Robolectric, col codificatore e il contenitore contati): un contenitore che non si apre libera
il codificatore; uno che non si chiude libera tutti e due, e `stop()` torna null con `Failed` (R1). Nella galleria
(`sample/src/testDebug/.../F8p13AudioUiTest.kt`) il registratore vero dietro il pannello, coi tocchi veri. Sul tablet: un
memo vero, registrato e riascoltato.

## Dove guardare

- `engine-audio/src/main/kotlin/dev/antigravity/fluidengine/audio/FluidAudioRecorder.kt` — il registratore e la cattura.
- `FluidMicrophone.kt` — il microfono, il fuoco audio, il microfono zittito.
- `FluidAudioWriters.kt` — l'AAC con `MediaCodec`/`MediaMuxer` e il WAV scritto mentre si registra.
- `FluidAudioEnvelopes.kt` — l'onda dai file, salvata accanto; il taglio (`FluidAudioTrim`).
- `engine-foundation/.../foundation/audio/` — l'inviluppo, la scala che non satura, i silenzi.

## Dipendenze e configurazione minima

```groovy
implementation project(':engine-audio')   // porta engine-foundation; nessuna libreria
```

Nessuna dichiarazione nel manifesto dell'app (il permesso è del modulo). Licenze: nessuna opera di terzi.
