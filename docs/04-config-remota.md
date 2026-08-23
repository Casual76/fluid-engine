# 04 · Configurazione remota

Un file JSON ospitato decide come si comportano tutte le app costruite sull'engine. Si modifica il file, e al controllo successivo ogni app installata cambia comportamento — senza ricompilare, senza pubblicare, senza che nessuno installi niente.

Quello che **non** fa è cambiare il codice. Il confine esatto è in [`06-limiti.md`](06-limiti.md); leggilo prima di progettare qualcosa che dipenda da questo meccanismo.

## Il file

È lo stesso `manifest.json` che il Pampa Store già usa per le release, con una sezione `engine` in più. Le due parti non si conoscono: un manifest con la sola `app` resta un manifest di store valido, uno con la sola `engine` è un file di controllo.

```json
{
  "app": {
    "id": "classeviva-expressive",
    "packageName": "dev.antigravity.classevivaexpressive",
    "repository": { "repoOwner": "Casual76", "repoName": "classeviva-expressive" },
    "stable": {
      "version": "7.1.1",
      "changelog": "…",
      "releaseTag": "stable-classeviva-expressive-v7.1.1",
      "apkAsset": "classeviva-expressive-7.1.1.apk",
      "sizeBytes": 23755210
    },
    "beta": { "…": "…" }
  },

  "engine": {
    "schema": 1,
    "minimumVersion": "1.0.0",
    "recommendedVersion": "1.1.0",
    "notice": null,
    "flags": {
      "glassChrome": true,
      "newAgenda": false
    },
    "killSwitch": { "enabled": false, "message": null },
    "overrides": {
      "dev.antigravity.classevivaexpressive": {
        "flags": { "newAgenda": true }
      }
    }
  }
}
```

| campo | cosa fa |
|---|---|
| `minimumVersion` | sotto questa versione dell'engine, l'app riporta `UPDATE_REQUIRED` |
| `recommendedVersion` | sotto questa, `UPDATE_RECOMMENDED` |
| `notice` | una frase che l'app può mostrare (manutenzione, avviso, disservizio noto) |
| `flags` | interruttori booleani condivisi da tutte le app |
| `killSwitch` | l'istruzione di fermarsi, con il messaggio da mostrare |
| `overrides` | gli stessi campi, ma per una sola app, scelta per application id |

Un override **si fonde** con i valori condivisi, non li sostituisce: spegnere un flag per una app non fa perdere a quell'app gli altri flag. I campi sconosciuti vengono ignorati, quindi un manifest nuovo può essere servito a un'app vecchia senza romperla.

## Nel codice

Un flag si dichiara dove viene usato, con il valore con cui la build è stata testata:

```kotlin
val NewAgenda = EngineFlag(key = "newAgenda", default = false)
```

Il default è la cosa importante: un'app senza rete, o un manifest che di quel flag non ha mai parlato, si comporta esattamente come il giorno in cui è stata pubblicata. Un flag senza un default sicuro non è un flag — è un valore di configurazione obbligatorio, e quello va nella build.

Poi si legge:

```kotlin
class AgendaViewModel(config: EngineRemoteConfig) : ViewModel() {
  val useNewAgenda = config.flag(NewAgenda)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewAgenda.default)
}
```

E si aggiorna, tipicamente all'avvio:

```kotlin
applicationScope.launch { remoteConfig.refreshIfStale() }
```

`refreshIfStale()` scarica solo se la copia in cache ha più di sei ore (`EngineConfigSource.refreshIntervalMillis`). Un file di controllo non è un feed: metterne il download davanti al primo frame per un valore che cambia poche volte l'anno è un cattivo affare, e la risposta in cache basta sempre per disegnare.

Un download fallito **non** riporta l'app ai default: lascia in piedi l'ultima risposta valida. Perdere la rete non è un motivo per cambiare comportamento.

## Compatibilità e kill switch

```kotlin
when (remoteConfig.compatibility()) {
  EngineCompatibility.OK -> Unit
  EngineCompatibility.UPDATE_RECOMMENDED -> notification.show("È disponibile un aggiornamento.")
  EngineCompatibility.UPDATE_REQUIRED -> showBlockingUpdateScreen()
}

val kill = remoteConfig.current().killSwitch
if (kill.enabled) showStopScreen(kill.message ?: "Questa versione non è più utilizzabile.")
```

Il kill switch è l'ultima risorsa: serve quando una build già installata sta facendo danno — parla con un endpoint che ora costa, corrompe dati salvati, martella un server. Cosa farne è una scelta dell'app; l'engine si limita a riportarlo, con la frase da mostrare.

## Igiene

- **Un flag ha una scadenza.** Quando la funzione nuova è stabile ovunque, si toglie il ramo vecchio e si toglie il flag. Un flag lasciato lì è un secondo percorso di codice che nessuno prova più.
- **Il manifest è pubblico.** Chiunque può leggerlo: niente URL privati, niente chiavi, niente nomi di funzioni non annunciate che raccontino i piani.
- **Il manifest è un file solo, e può rompere tutto.** Un JSON malformato non viene applicato (il parse avviene prima della cache), ma un `killSwitch.enabled: true` copiato per sbaglio ferma ogni app che lo legge. Va trattato come codice in produzione: si modifica con un commit, non a mano nella UI di GitHub alle due di notte.
- **`schema` si alza solo se cambia il significato di un campo esistente.** Aggiungere campi opzionali non conta: è proprio la cosa che le app vecchie devono poter ignorare.
