# engine-revisions

La scelta tra versioni (F8.21, idea `scelta-tra-versioni`): quando due copie di un elemento sono cambiate e non si
possono fondere, **un foglio di vetro le mette a confronto** e chiede quale tenere. Pacchetto
`dev.antigravity.fluidengine.revisions`. **Opt-in**, sopra `engine-ui`; nessuna libreria (il confronto dei testi e'
`FluidTextDiff` di [foundation](foundation.md)).

Riferimento Apple: le versioni in conflitto di `NSFileVersion` e il foglio «Risolvi conflitto» dei documenti di iCloud
(una riga per versione con il dispositivo, l'ora e l'anteprima, da spuntare, e il tasto «Mantieni»; fiducia media).

**Perche' un modulo a se', e non dentro `engine-sync`.** Pampa Notes ha la sua sincronizzazione (resta com'e', progetto
dell'account § 5.3) e Pampa Mail confronta due bozze: tutte e due usano il foglio senza `engine-sync`. E `engine-sync`
resta senza Compose, usabile dai moduli dell'account e nelle prove sul JVM. Il ponte fra i due sono gli **id delle
revisioni**, stringhe: nessun tipo in comune.

Catalogo completo delle firme: [`../api/revisions.md`](../api/revisions.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il foglio del confronto, nato dal tasto che segnala il conflitto | `FluidVersionChoiceSheet(choice, onDecision, onDismissRequest, source)` |
| il contenuto del foglio in una superficie mia | `FluidVersionChoicePane(choice, onDecision, onDismissRequest)` |
| i passaggi diversi di due testi, per mostrarli altrove | `fluidVersionExcerpts(versions)` (pura; per i testi lunghi fuori dal thread principale) |
| l'ora di una versione come la dice il foglio | `fluidVersionTime(millis, zone, locale, today)` |
| le parole in inglese | `FluidVersionLabels.english()` |

## Il foglio

- **Una card per versione**: il dispositivo (o l'autore in uno spazio condiviso, «di Giulia»), l'ora («Oggi, 14:32»), il
  segno «Attuale» sulla versione che l'app mostra adesso, e l'anteprima **tagliata attorno al primo passaggio diverso**,
  con i passaggi diversi segnati dal fondo dell'accento. Oltre il tetto di lavoro del confronto, due estratti senza segni e
  una riga che lo dice.
- **Si sceglie toccando la card** (il segno di `FluidSelectionMark`); il tasto dice cosa fara': «Scegli una versione»
  (spento), «Tieni questa versione», «Tieni tutte e due». «Decidi dopo» chiude senza scegliere.
- **La scelta** (`FluidVersionDecision`) porta le revisioni da tenere (`keep`) e quelle mostrate (`shown`). Il foglio non
  si chiude da solo: lo chiude l'app quando ha salvato, mettendo `choice` a null.
- **Una revisione nuova a foglio aperto**: se `choice` cambia revisioni (stessa `key`), la card cambiata **entra
  sfocata** (la cambia l'app, non il dito), la scelta si azzera, e una riga dice che e' arrivata una versione nuova
  (TalkBack la annuncia: regione viva). Con `engine-sync`, `resolve` risponde `Stale` se le revisioni non sono piu' quelle.
- **Vetro e viaggio**: e' un modale di vetro dell'engine (`FluidGlassModalPortal`): nasce dalla `source` e ci torna; sul
  telefono e' un foglio dal basso, sul tablet un popover.
- **Accessibilita'**: ogni card e' una casella (ruolo, stato «Scelta» / «Non scelta», e il testo della versione); bersagli
  da almeno 44 dp.
- **«Riduci movimento»**: il segno e le card cambiano senza passaggi. **A riposo** il foglio non scrive niente.

## Con engine-sync

```kotlin
val conflict = space.state.value.conflicts.first()
val choice = FluidVersionChoice(
  title = note.text("titolo") ?: "Nota",
  key = "${conflict.recordId}/${conflict.field}",
  versions = conflict.versions.map { v ->
    FluidVersion(v.revision, deviceName(v.node), v.millis, v.text.orEmpty(), current = v.current)
  },
)
FluidVersionChoiceSheet(choice = choice.takeIf { open }, source = source, onDismissRequest = { open = false },
  onDecision = { d -> scope.launch {
    if (space.resolve(conflict.recordId, conflict.field, d.keep, d.shown) is SyncResolution.Resolved) open = false
  } })
```

«Tieni tutte e due» con `engine-sync`: la prima revisione scelta resta nell'elemento, ogni altra diventa un elemento
nuovo, copia di questo con quella versione (come «Mantieni entrambi» di File).

## File

- `engine-revisions/src/main/kotlin/dev/antigravity/fluidengine/revisions/FluidVersions.kt` — i modelli, i passaggi
  diversi, l'ora;
- `.../FluidVersionChoiceSheet.kt` — il foglio, il contenuto, le parole, i tag.
- Le prove: `engine-revisions/src/test` (passaggi, ripiego, ora) e `sample/src/testDebug/.../F8p21VersionSheetUiTest.kt`
  (il dito vero, TalkBack, «Riduci movimento», niente lavoro a riposo).

**Dipendenze del modulo.** `api` su `engine-ui`.
