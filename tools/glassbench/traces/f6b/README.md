# F6B: le tracce del banco del movimento degli elementi dinamici

Le tracce della tabella «Il banco del movimento» del piano (`docs/liquid-glass/piano.md`, sezione F6B) per la selezione,
i tasti con esito, «Annulla» e l'avviso dal tasto, l'elemento che diventa pagina. Le ha raccolte F6B.12 (22 novembre
2026) dal commit `35bb23b`, che contiene F6B.1–F6B.9: ogni `trace.json` è quello che il test del pacchetto esporta
**sul codice integrato**, con la variabile d'ambiente del pacchetto. Nessuna è scritta a mano nel banco.

Il confronto con le tracce consegnate dai pacchetti (`artifacts/liquid-glass/sessioni/f6b-12/tracce-confronto.json`,
script `raccogli-tracce.py` e `differenze-tracce.py` nella stessa cartella):
- **uguali al byte**: le quattro della tab bar di F6B.2, le sei di F6B.4, `elemento-giu-chiude` e le sei curve dei tasti;
- **uguali nei numeri**: `selezione-senza-tabbar` cambia solo nei nomi che portano l'hash d'identità di
  `FluidToolbarOverflowId` (cambia a ogni corsa); le sei di F6B.5b ed `elemento-dalla-barra` di F6B.5c cambiano solo in
  `page` e negli `items` dei contenuti, cioè nei fondali che il preparatore del pacchetto aggiunge prima di rendere
  (sotto). Geometria, raggi, opacità e sfocature sono gli stessi in ogni fotogramma.

## Le tracce

| traccia | elemento | la scrive | variabile | collage di tutti i fotogrammi (in `artifacts/liquid-glass/sessioni/`) |
|---|---|---|---|---|
| `selezione-entra` | la tab bar diventa le azioni | `FluidFloatingTabBarSelectionTest` | `GLASS_F6B2_DIR` | `f6b-2/banco/selezione-entra/selezione-entra-collage.jpg` |
| `selezione-esce` | e torna com'era | `FluidFloatingTabBarSelectionTest` | `GLASS_F6B2_DIR` | `f6b-2/banco/selezione-esce/selezione-esce-collage.jpg` |
| `selezione-back-confermato` | il gesto indietro confermato | `FluidFloatingTabBarSelectionTest` | `GLASS_F6B2_DIR` | `f6b-2/banco/selezione-back-confermato/selezione-back-confermato-collage.jpg` |
| `selezione-back-annullato` | il gesto indietro annullato (D1) | `FluidFloatingTabBarSelectionTest` | `GLASS_F6B2_DIR` | `f6b-2/banco/selezione-back-annullato/selezione-back-annullato-collage.jpg` |
| `selezione-senza-tabbar` | la barra in basso della pagina cambia azioni | `FluidSelectionToolbarTest` | `GLASS_F6B1_DIR` | `f6b-1/banco/selezione-senza-tabbar/selezione-senza-tabbar-collage.jpg` |
| `tasto-invia-riuscito`, `tasto-invia-fallito` | Invia | `F6b3GlyphTraces` (curva) | sempre, `engine-ui/build/reports/f6b-3b/curve/` | `f6b-3b/banco/tasto-invia-riuscito.jpg`, `tasto-invia-fallito.jpg` |
| `tasto-salva` | Salva | `F6b3GlyphTraces` (curva) | sempre | `f6b-3b/banco/tasto-salva.jpg` |
| `tasto-copia` | Copia | `F6b3GlyphTraces` (curva) | sempre | `f6b-3b/banco/tasto-copia.jpg` |
| `tasto-scarica-percentuale` | Scarica | `F6b3GlyphTraces` (curva) | sempre | `f6b-3b/banco/tasto-scarica-percentuale.jpg` |
| `tasto-preferito-rifiutato` | Preferito | `F6b3GlyphTraces` (curva) | sempre | `f6b-3b/banco/tasto-preferito-rifiutato.jpg` |
| `tasto-lavoro-breve` | Salva sotto i 300 ms | `F6b12ShortWorkTrace` (curva, nuova in F6B.12) | sempre, `engine-ui/build/reports/f6b-12/curve/` | `f6b-12/banco/tasto-lavoro-breve.jpg` |
| `invia-composto` | Invia → «Nuova mail» diventa «Annulla» | `FluidUndoCountdownTraceTest` | `GLASS_UNDO_TRACE_DIR` | `f6b-4/banco-contatore/render/invia-composto/invia-composto-collage.jpg` |
| `annulla-contatore` | «Annulla» toccato durante il riempimento | `FluidUndoCountdownTraceTest` | `GLASS_UNDO_TRACE_DIR` | `f6b-4/banco-contatore/render/annulla-contatore/annulla-contatore-collage.jpg` |
| `annulla-scade` | il contatore che scade | `FluidUndoCountdownTraceTest` | `GLASS_UNDO_TRACE_DIR` | `f6b-4/banco-contatore/render/annulla-scade/annulla-scade-collage.jpg` |
| `avviso-dal-tasto` | l'avviso che nasce dal tasto (non di serie) | `FluidNotificationSourceTraceTest` | `GLASS_F6B4_TRACE_DIR` | `f6b-4/banco/render/avviso-dal-tasto/avviso-dal-tasto-collage.jpg` |
| `avviso-annulla-torna` | e torna nel tasto | `FluidNotificationSourceTraceTest` | `GLASS_F6B4_TRACE_DIR` | `f6b-4/banco/render/avviso-annulla-torna/avviso-annulla-torna-collage.jpg` |
| `avviso-sorgente-sparita` | col tasto che non c'è più | `FluidNotificationSourceTraceTest` | `GLASS_F6B4_TRACE_DIR` | `f6b-4/banco/render/avviso-sorgente-sparita/avviso-sorgente-sparita-collage.jpg` |
| `elemento-zoom` | Zoom, con la copertina condivisa | `FluidSharedElementTest` | `GLASS_F6B5B_DIR` | `f6b-5b/tracce/elemento-zoom/elemento-zoom-collage.jpg` |
| `elemento-sale-col-dito` | Sale, col dito | `FluidSharedElementTest` | `GLASS_F6B5B_DIR` | `f6b-5b/tracce/elemento-sale-col-dito/elemento-sale-col-dito-collage.jpg` |
| `elemento-cresce` | Cresce | `FluidSharedElementTest` | `GLASS_F6B5B_DIR` | `f6b-5b/tracce/elemento-cresce/elemento-cresce-collage.jpg` |
| `elemento-interrotto-25`, `-50`, `-75` | il dito che si ferma e inverte | `FluidSharedElementTest` | `GLASS_F6B5B_DIR` | `f6b-5b/tracce/elemento-interrotto-25/…-collage.jpg` (e 50, 75) |
| `elemento-dalla-barra` | Dalla barra: «Scrivi» → compositore | `FluidZoomPresetsTest` | `GLASS_F6B5C_DIR` | `f6b-5c/tracce/elemento-dalla-barra/elemento-dalla-barra-collage.jpg` |
| `elemento-giu-chiude` | la pagina chiusa trascinando in giù | `FluidZoomPresetsTest` | `GLASS_F6B5C_DIR` | `f6b-5c/tracce/elemento-giu-chiude/elemento-giu-chiude-collage.jpg` |

Le curve dei tasti sono `curve.csv` e non `trace.json`: i fotogrammi dei tasti con esito sono le riprese della finestra
vera (PixelCopy dei `FluidActionButton`, `F6b3GlyphFramesTest` e `FluidActionFramesTest` nel modulo sample), e le curve
dicono i canali del disegno dai profili Kotlin (`FluidActionGlyphProfile`, R7), campionati ogni 16 ms. Le versioni
scure, dove ci sono, restano nelle cartelle dei pacchetti.

## Come si rifanno

```powershell
$env:GLASS_F6B2_DIR = "<cartella>"     # e le altre variabili della tabella
# il lotto di engine-ui (le tracce le scrive il test, a fine prova)
$py = "artifacts\liquid-glass\venv\Scripts\python"
& $py tools\glassbench\glassmotion.py validate tools\glassbench\traces\f6b\elemento-zoom\trace.json
& $py tools\glassbench\glassmotion.py collage  tools\glassbench\traces\f6b\elemento-zoom\trace.json --step-ms 0 --cols 10
```

**Le tracce di «Cresce».** `elemento-cresce` ed `elemento-dalla-barra` hanno la pagina sotto che si sfoca e si scurisce:
il motore esporta per ogni fotogramma il sigma e il velo (`_sigmaSfondo`, `_veloSfondo`), e prima di rendere va
preparato il fondale con il preparatore del pacchetto (`artifacts/liquid-glass/sessioni/f6b-5b/prepara-tracce.py` e
`f6b-5c/prepara-tracce.py`), come per i collage consegnati. Le altre si rendono così come sono.

**Che cosa non dicono.** Il banco esegue i modelli e i profili Kotlin, non il disegno Compose né la GPU: tempi e sagome
contro Apple non sono una qualifica sul dispositivo. Il collaudo a 120 Hz sul Tab S9 è di F6B.14.
