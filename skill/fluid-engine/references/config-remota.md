# Config remota: flag, versione minima, kill switch

Un JSON ospitato decide il comportamento di tutte le app costruite sull'engine. Si modifica il file e
al controllo successivo ogni app installata cambia comportamento, senza ricompilare.

## Il file

È lo stesso `manifest.json` del Pampa Store con una sezione `engine` in più.

```json
{
  "app": { "…": "la sezione release, invariata" },
  "engine": {
    "schema": 1,
    "minimumVersion": "1.0.0",
    "recommendedVersion": "1.1.0",
    "notice": null,
    "flags": { "nuovaAgenda": false },
    "killSwitch": { "enabled": false, "message": null },
    "overrides": {
      "dev.antigravity.unaApp": { "flags": { "nuovaAgenda": true } }
    }
  }
}
```

- Un **override** si fonde con i valori condivisi, non li sostituisce.
- I campi sconosciuti vengono ignorati: un manifest nuovo può essere servito a un'app vecchia.
- `schema` si alza **solo** se cambia il significato di un campo esistente.

## Aggiungere un flag

1. Dichiaralo dove viene usato, con il default che descrive **come si comporta la build di oggi**:

```kotlin
val NuovaAgenda = EngineFlag(key = "nuovaAgenda", default = false)
```

2. Leggilo:

```kotlin
val abilitato = remoteConfig.flag(NuovaAgenda)
  .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NuovaAgenda.default)
```

3. Aggiungi la voce al manifest solo quando serve accenderlo. Finché non c'è, vale il default.

Il default è la parte importante: un'app senza rete deve comportarsi esattamente come il giorno in
cui è uscita. Un flag senza un default sicuro non è un flag, è configurazione obbligatoria — e quella
va nella build.

## Il refresh

```kotlin
applicationScope.launch { remoteConfig.refreshIfStale() }
```

Scarica solo se la copia in cache ha più di sei ore. Non mettere il download davanti al primo frame:
la risposta in cache basta sempre per disegnare, e un file di controllo cambia poche volte l'anno.

Un download fallito **non** riporta ai default: resta l'ultima risposta valida. Perdere la rete non è
un motivo per cambiare comportamento.

## Il kill switch

```kotlin
val kill = remoteConfig.current().killSwitch
if (kill.enabled) fermaTutto(kill.message ?: "Questa versione non è più utilizzabile.")
```

Ultima risorsa, per quando una build installata sta facendo danno. Cosa farne è una scelta dell'app;
l'engine riporta il fatto e la frase.

## Igiene, e cosa dire quando qualcuno chiede di più

- Un flag ha una **scadenza**: quando la funzione è stabile, togli il ramo vecchio e togli il flag.
  Un flag dimenticato è un secondo percorso di codice che nessuno prova più.
- Il manifest è **pubblico**: niente chiavi, niente URL privati, niente nomi di funzioni non
  annunciate.
- Il manifest è **un file solo e può fermare tutto**: si modifica con un commit, non a mano.
- I flag scelgono fra percorsi che la build **contiene già**. Non possono aggiungere codice: vedi
  `limiti.md`.
