# Material Symbols

`engine-ui` include, **come vettori generati**, una parte delle icone Material Symbols di Google: il set
`FluidIcons` (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/icons/`).

- Progetto: **Material Symbols** (Material Design icons), di **Google**
- Repository: https://github.com/google/material-design-icons
- Commit da cui generiamo: `10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7` («Update Symbols», 2026-10-23)
- Stile e asse: **Rounded**, peso (`wght`) **500**, `FILL` 0 e, per le icone segnate nell'elenco, 1;
  `GRAD` 0, taglia ottica 24
- Licenza: **Apache License, Version 2.0** — testo: https://www.apache.org/licenses/LICENSE-2.0
- Copyright Google LLC

## Cosa abbiamo preso

| Dove sta da noi | Cosa è | Da dove viene |
|---|---|---|
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/icons/FluidIconsOutline.kt`, `FluidIconsFilled.kt` | i tracciati delle icone, come `ImageVector` in un riquadro da 24 | `symbols/android/<nome>/materialsymbolsrounded/<nome>_wght500[fill1]_24px.xml` |
| `tools/icons/sorgenti/material-symbols-rounded-500.json` | la cache delle stesse sorgenti, per rigenerare senza rete | idem |
| `tools/glassbench/icons/fluid-icons.json` | gli stessi tracciati per il banco di render sul PC | idem |

Quali icone, lo dice l'elenco `tools/icons/fluid-icons.txt`; il generatore è `tools/icons/gen_fluid_icons.py`.

## Le modifiche

La licenza chiede di dichiarare i file modificati (sezione 4). Le sagome sono quelle di Google; il
generatore cambia solo la forma in cui sono scritte:

1. **Riquadro**: dal riquadro della sorgente (960 unità, qualche icona ancora 24) a 24 unità,
   coordinate assolute a due decimali.
2. **Pulizia**: tolti i segmenti di lunghezza zero e i sottotracciati ridotti a un punto, che
   l'esportazione dal font variabile lascia e che non disegnano niente.
3. **Nomi**: ogni icona prende il nome di Material Symbols in forma Kotlin (`arrow_back_ios_new` →
   `FluidIcons.ArrowBackIosNew`); `autoMirrored` resta quello della sorgente.

Ogni file generato porta in testa la fonte, il commit e il rimando a questo file.

**Cosa fare in pratica:** tieni questo file nel repo e riporta Material Symbols (Google, Apache-2.0) fra
le licenze di terze parti nella schermata «informazioni» dell'app, se la app usa `FluidIcons`. Se cambi
il commit nel generatore, aggiorna anche la riga qui sopra.
