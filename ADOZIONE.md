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

## Fase 1 — ClasseViva Expressive

**Cosa:** l'engine è nato da qui, quindi l'app ha una copia locale di tutto il design system.
Migrare significa cancellare `core-designsystem` e dipendere da `engine-ui`.

**Perché per prima:** finché quella copia esiste, ogni correzione va fatta due volte, e le due
inevitabilmente divergono — è esattamente il problema che l'engine esiste per risolvere.

**Onestà sul risultato: visivamente non cambierà quasi niente.** Quest'app già *è* il vocabolario.
Il guadagno è strutturale, non estetico, e non va raccontato come estetico.

Passi:
1. `git submodule add` + `engine-install.ps1 -AppRoot android` (tutti i moduli).
2. Confrontare file per file `core-designsystem` con `engine-ui`. Dove ClasseViva ha qualcosa in
   più, **portarlo nell'engine** prima di cancellare: l'engine è stato estratto, non copiato, e
   qualcosa è rimasto indietro.
3. Sostituire gli import `…core.designsystem.*` con `dev.antigravity.fluidengine.ui.*`.
4. `AppTheme` diventa un guscio su `FluidTheme` con l'accento di ClasseViva, firma invariata.
5. Cancellare `core-designsystem`. Se resta, qualcuno la userà.
6. `engine-doctor` + build + test + **guardarla sul telefono**.

**Prerequisito:** il lavoro sul widget della 7.1.x va committato prima, altrimenti si mescola con
la migrazione e il diff diventa illeggibile.

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
