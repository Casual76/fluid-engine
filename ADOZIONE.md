# Portare l'engine su tutte le app

Ordine deciso da Alessio il 2026-08-23: **ClasseViva Expressive → Pampa Store → KeyVoice →
universal_converter**, con Pampa widgets in coda (vedi in fondo).

## La distinzione che conta

Un'app può stare sull'engine a tre profondità diverse, e confonderle è il modo più rapido per
promettere qualcosa che non arriva:

| profondità | cosa cambia | si vede? |
|---|---|---|
| **tema** — `FluidTheme` | colori, tipografia, angoli, curve di animazione | poco: l'app resta strutturata com'era |
| **componenti** — `FluidScreen`, `FluidListGroup`, `GlassMaterial`, la fisica dello scroll | com'è fatta ogni schermata | **sì**: è qui che arrivano il vetro e l'overscroll |
| **moduli senza UI** — `engine-update`, `engine-config` | aggiornamento in-app, feature flag | no, per definizione |

Al 2026-08-23 tutte le app sono ferme al **tema** (o meno). Nessuna chiama `FluidScreen`,
`GlassMaterial` o `FluidScrollPhysics`: la verifica è un `grep`, e sono zero occorrenze.

Questo documento è il piano per arrivare ai **componenti**.

---

## Fase 1 — ClasseViva Expressive ✅ fatta (2026-08-23, engine 1.2.0)

**8499 righe cancellate, 269 rimaste.** La copia locale del design system non esiste piu':
`core-designsystem` dipende da `:engine-ui` e tiene solo quello che e' di ClasseViva.

Cosa e' rimasto nell'app, e perche':

| file | righe | perche' non e' dell'engine |
|---|---|---|
| `AppTheme.kt` | 109 | il verde del marchio, il guscio su `FluidTheme`, e la mappa da `AppSettings` a `EngineSettings` |
| `FeatureHero.kt` | 71 | `FeatureIdentity`: sa cosa sono voti, agenda e assenze |
| `GradePill.kt` | 35 | sa cos'e' la sufficienza |
| `SyncStatusBridge.kt` | 54 | "anno non iniziato" e' un booleano nel dominio e una frase nell'engine |

**Deviazione dal piano, deliberata:** il piano diceva di cancellare `core-designsystem`. Il modulo
resta, ma come **strato dell'app** invece che come copia del design system: dichiara
`api project(':engine-ui')`, quindi i sette moduli feature continuano a dipendere da lui e nessun
loro `build.gradle` e' stato toccato. La copia e' sparita, il modulo no.

**Cosa e' andato nell'engine:** `FluidHero`. Era rimasta fuori dall'estrazione perche' sembrava
specifica, e invece il dominio stava solo nei nomi dell'enum. Ora l'engine ha `FluidHeroTone` (una
posizione sull'anello delle tre famiglie di accento) e `FluidHeroMotif` (la forma sul fondo), e
l'app mappa le sue sette sezioni su quella coppia.

**Cosa e' stato cancellato senza portarlo:** `Animations.kt`, cioe' `bouncyClickable`. Era un guscio
di compatibilita' su `fluidPressable` con **zero chiamanti**.

**Verificato:** build debug e release firmata, test verdi, installata sul dispositivo, home / voti /
altro guardate una per una. Si vede identica a prima — che era il risultato atteso e va detto cosi'.

### Le tre cose che hanno richiesto attenzione

1. **I nomi dei preset dell'accento.** Le impostazioni salvano la scelta per nome e la confrontano
   con `preset.name`. L'engine chiama "fluid" quello che ClasseViva chiamava "expressive": prendere
   la lista dall'engine avrebbe fatto apparire *nessun* preset come selezionato a chiunque avesse
   gia' scelto il blu. La lista resta nell'app, con i nomi storici e gli stessi colori.
2. **Il tipo dello stato di sincronizzazione.** L'app ne ha uno nel dominio, l'engine uno suo. Non
   vanno unificati: nel dominio `schoolYearNotStarted` e' un booleano perche' decide un
   comportamento, nell'engine e' una frase perche' li' serve solo da dire. Si converte al confine.
3. **Un nome pienamente qualificato** nel codice (`dev.antigravity...theme.QuickAction(`) che la
   riscrittura degli import non vede. Cercarli prima: `grep -rn "core.designsystem\." --include=*.kt`.

---

## Fase 2 — Pampa Store

**Cosa:** portare nel `FluidPort.kt` i componenti, non solo i token. Griglia del catalogo, schede
app, schermata dettaglio, impostazioni.

**Costo diverso dalle altre:** è Compose Multiplatform, quindi ogni componente va **riscritto a
mano** in `commonMain` e non si aggiorna con l'engine. Vale la pena solo per i componenti che
l'app usa davvero, e ognuno va aggiunto sapendo che è una copia in più da mantenere.

**Cosa non si può portare:** il motion scheme espressivo non esiste nel Material 3 di Compose
Multiplatform 1.7. Il vetro sì (è disegno, non API nuove); la fisica dello scroll va verificata.

Alzare `FLUID_PORT_OF` solo dopo aver riportato davvero, mai prima.

---

## Fase 3 — KeyVoice

**Cosa:** è tutta View e XML. Prendere i componenti significa **riscrivere l'interfaccia in
Compose**, non adottare una libreria.

Non è una migrazione, è una riscrittura, e va decisa come tale. Se la risposta è sì, si comincia
dalla `MainSetupActivity` (che è già una lista di sezioni: mappa quasi uno a uno su `FluidScreen` +
`FluidListGroup`) e si lasciano stare il servizio di accessibilità e la tastiera, che sono View per
buone ragioni.

Se la risposta è no, KeyVoice resta dov'è: ha già `engine-update`, che è il pezzo che le serviva.

---

## Fase 4 — universal_converter

**Cosa:** schermata principale, anteprima, progresso, impostazioni su `FluidScreen` e liste
raggruppate.

È la più semplice delle quattro: poche schermate, già sul tema, già ad angoli continui, nessun
vincolo multipiattaforma. Ha molte superfici piatte e scroll lunghi, quindi vetro e overscroll ci
si vedono bene.

---

## Fase 5 — Pampa widgets

Non era nell'ordine che Alessio ha dato — probabilmente perché ha già il tema. Ma ha due schermate
sole, quindi è la più economica di tutte, ed è anche l'unica dove ha senso far passare i widget
Glance a `engine-widget`.

---

## Regole che valgono per ogni fase

- **La firma di release è `C:\VibeCoded Projects\pampa.jks`, alias `pampa`**, per tutte le app —
  verificato confrontando l'impronta SHA-256 con quella degli APK installati. Ogni app la legge da
  un file ignorato da git (`local.properties`, o `keystore.properties` in universal_converter).
- **Installare sempre la build di release**, mai la debug: le app hanno
  `applicationIdSuffix = ".debug"`, quindi una debug si installa *accanto* a quella vera e non
  sostituisce niente. È il modo più facile di credere di aver consegnato qualcosa che nessuno vede.
- **Una fase è finita quando la si è guardata sul telefono**, non quando compila.
- Le cinque regole del design system sono in `skill/fluid-engine/references/regole.md`.
