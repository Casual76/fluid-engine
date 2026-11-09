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
