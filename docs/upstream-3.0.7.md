# Integrazione locale Fluid Engine 3.0.7

Base: `main` / `engine-3.0.6` = `33c69bd`. Worktree esclusivo: `pm-up-307`, ramo `upstream/3.0.7`. Nessun push.

## Inventario completo dei commit esclusivi

Recupero locale con `git fetch <repository-submodule> HEAD`, ref temporanee `refs/upstream-import/pampai` e `refs/upstream-import/fluidify`; inventario con `git log --reverse --format="%H %s" main..<ref>`.

| App | Commit originale | Contenuto | Integrazione |
|---|---|---|---|
| PampAI | ab113365f5c969f6d8cff2efe7887194bc4313ca | Hook risultati e allegati dinamici; ammissione/liquidazione tentativi provider, audit prima dell'invio, contesto ereditato, visione verificata; filtro delle sezioni impostazioni; includeva anche typed bridge e identita operazione | ce7c648, 0e5f4a2, f2d9059; mantenute le implementazioni 3.0.6 gia equivalenti di bridge e identita, senza duplicarle |
| PampAI | 4e8c1dce29f54272a49eae237a787b47e66813aa | Comandi barra della pagina corrente disponibili durante le transizioni, esclusione input e semantica delle azioni uscenti | ada4d62; nuova regressione sulla pila richiesta |
| Fluidify | 9c659890c8f7ef275d48e048069d1c2d9b5ae1a9 | Conserva l'ultimo target dello scrubber durante conferme intermedie del lettore, nei due versi | 455f51a, inclusi test puri e Compose |
| Fluidify | b29bbac97cde2f8958a7b2b8d9efe295c451773a | Disabilitazione o cambio intervallo cancellano trascinamento e punto tenuto, senza seek residui | a918af5, inclusi test Compose |
| Fluidify | d4ac035110bbc6f4580feaab10132661bf28f421 | Sincronizza guida scrubber esportata | d24dbdd |

I conflitti nei changelog Fluidify non sono stati retrodatati nella 3.0.6: le note appartengono alla nuova release. Cataloghi e copie documentali sono rigenerati dal sorgente finale. I conflitti di orchestratore mantengono una sola `prepareToolExecution` e conservano i test 3.0.6 insieme ai test del fork.

## Compatibilita e scelte

Nuove proprieta in coda con default: `AskInput.onToolOutput`, `prepareToolParts`, `strictToolExecution`; `ChatRequest.beforeDispatch`, `lifecycle`; `ModelInfo.visionVerified`; `GeminiProvider.baseUrl`; filtro `parts` delle impostazioni. Nuovi tipi provider separati, senza nuovi valori negli enum pubblici preesistenti. L'overload storico di `AiHttp.postJsonStream` conserva la lambda finale degli header 2xx; il nuovo overload osserva tutti gli status.

Il fork ampliava implicitamente il catalogo OpenRouter ai modelli senza tool: nella release serve `includeModelsWithoutTools = true`. Il filtro storico resta il default. La validazione preventiva dei tool locali, l'esito incerto del timeout e le tracce basate sull'esito typed richiedono `strictToolExecution = true`: i callback nuovi restano disponibili anche senza tale opzione. Le correzioni richieste di barra e scrubber modificano intenzionalmente i casi difettosi.

La compatibilita verificata e quella dei sorgenti ricompilati; non si promette compatibilita binaria di artefatti Kotlin precompilati. Nessun servizio remoto reale e nessun addebito sono coinvolti nei test dei provider.

## Adozione nelle app

Quando tag e ramo saranno pubblicati dal responsabile, aggiornare submodule e `engine.properties` insieme con gli strumenti dell'engine e ricompilare. PampAI puo usare direttamente i nuovi hook; per riprodurre anche la policy rigorosa del fork deve scegliere `strictToolExecution = true` e, dove necessario, il catalogo senza tool tramite opt-in. Fluidify riceve i fix scrubber aggiornando il submodule; nessuna nuova API obbligatoria. ClasseViva e le altre app possono conservare i parametri attuali.

Le verifiche delle app usano worktree temporanei esterni, copie dei file tracked e untracked non ignorati degli originali, engine locale al commit candidato e `local.properties` temporaneo con il solo percorso SDK. Nessun commit nei repository delle app.

## Verifiche eseguite

Tutte le invocazioni Gradle sono racchiuse da `powershell -NoProfile -File "C:\VibeCoded Projects\gradle-slot.ps1" -Acquire -Name sol-up-307` e `-Release -Name sol-up-307` in `finally`, con controllo dell'exit code dell'acquisizione. Attesa degli altri gruppi senza sottrarre i loro slot.

Comando finale engine (exit **0**):

```text
gradlew.bat :engine-ai:testDebugUnitTest :engine-ai-bridge:testDebugUnitTest :engine-ai-ui:testDebugUnitTest :engine-ui:testDebugUnitTest :sample:assembleDebug :sample:testDebugUnitTest --continue --no-daemon --no-watch-fs
```

| Modulo | Test | Fallimenti/errori/skipped |
|---|---:|---|
| engine-ai | 268 | 0/0/0 |
| engine-ai-bridge | 32 | 0/0/0 |
| engine-ai-ui | 116 | 0/0/0 |
| engine-ui | 1087 | 0/0/0 |
| sample | 411 | 0/0/0 |

Totale: **1914**. Build finale: 2m36s; 185 task (23 eseguiti, 3 da cache, 159 aggiornati). I conteggi XML sono archiviati in `validation/upstream-3.0.7/tests.json`.

Due tentativi preliminari hanno exit **1**: errore di compilazione nella prima modifica al filtro catalogo (corretto) e test bridge di cancellazione con tempo virtuale che anticipava il cleanup IO (portato al tempo reale). Non sono conteggiati come verifiche riuscite.

Nuove regressioni: lifecycle provider e audit importati dal fork; hook risultati integrali e parti dinamiche; filtro catalogo con default storico e opt-in; pagina di interazione durante push/pop; gruppi delle impostazioni senza duplicazioni e conservazione degli slot. Conservate le regressioni scrubber pure e Compose e quelle bridge 3.0.6.

`check_docs.py --check` inizialmente non era supportato (exit 1, interpretato come nome file): lo script ora lo accetta come alias esplicito del controllo senza scritture. Tutti i comandi documentali ordinari e con `--check` sono stati poi eseguiti con exit 0 prima del cambio versione.

La ricerca sui sorgenti delle app sotto `C:\VibeCoded Projects` ha trovato file con `when` e riferimenti agli enum engine in ClasseViva (5), Fluid-transit (1), FluidWeather (3), Pampa notes (14), PampAI (22); nessun valore e stato aggiunto agli enum preesistenti. Le altre app rilevate non mostravano tale combinazione. Questa ricerca e statica e non sostituisce la compilazione di tutte le app.

## Compilazioni applicative

Candidato engine compilato: `f2d9059` (i commit successivi fino alla release riguardano documentazione, evidenze e versione). Comando per ciascuna app: `gradlew.bat :app:compileDebugKotlin --no-daemon --no-watch-fs`.

- PampAI: **exit 0**, 1m36s, 177 task; include `core-assistant` e `feature-assistant`. Snapshot locale da HEAD `9a435ae5820eb0e20ec58e43a9bfe145b6c1a8cc`, piu modifiche locali copiate. Nessun errore residuo nei sette file segnalati; nessuna modifica ai sorgenti app necessaria per compilare.
- Fluidify: **exit 0**, 2m01s, 112 task; snapshot locale da HEAD `a9247286a8bb86d7b8a83c5c30ffea0a2fd1b2d3`, piu modifiche locali copiate.
- ClasseViva: **exit 0**, 1m44s, 375 task; build Android dello snapshot locale da HEAD `c603e0dce00eb8c75779ce5e9cde723fb0f100dc`, piu modifiche locali copiate.

## Limiti e rischi residui

Le compilazioni applicative verificano Kotlin e dipendenze del percorso Debug, non confezionamento APK, firma, native Rust/C++ di Fluidify, release minificata o esecuzione sui dispositivi. Le UI hanno prova Compose/JVM, non osservazione hardware delle animazioni o dell'aptica. Le liquidazioni dei provider hanno prova locale con server di test; non attestano prezzi o addebiti reali. Lo snapshot delle app include modifiche non committate: i soli hash HEAD non lo identificano integralmente.

Tutti i test sono precedenti al solo aggiornamento della versione e della documentazione della release. `main` rimane a `33c69bd5bc1d1264207314cc395c442c666fce58`: lo script consente la release dal ramo assegnato, quindi nessun merge e necessario. Nessun push eseguito.

## Identita della release

Commit di release e tag locale `engine-3.0.7`: `40540d8d136cf1318bfd1382b34cdd2eadcdf23f`. Nessun merge su main. Il ramo prosegue con il commit di allineamento documentale successivo al tag, come previsto dalla sequenza richiesta; il codice Kotlin del tag coincide con il candidato testato salvo la costante di versione.

Comando release eseguito con exit **0**: `powershell -NoProfile -ExecutionPolicy Bypass -File tools/engine-release.ps1 -Version 3.0.7 -Notes "Integrati gli hook runtime IA e i controlli pagina di PampAI; corretto lo scrubber di Fluidify. Nuove policy IA opzionali con default compatibili con 3.0.6." -Tag`. Worktree applicativi temporanei rimossi con exit **0**.

Dopo la release, `gen_api_catalog.py`, `check_docs.py`, `sync_docs.py` e tutti e tre con `--check`: exit **0**. Catalogo: 880 dichiarazioni; controllo: 52 file; copia documentale: 79 file. Lo stato finale del worktree e pulito dopo il commit documentale; le ref temporanee di import sono state eliminate.
