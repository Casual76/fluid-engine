# La pagina «Assistente» (F3.2b, F3.2e)

Le superfici di `engine-ai-ui` sopra un fondo che cambia, per le sessioni sul dispositivo. Sopra, i pezzi che **girano da
soli** (tasto tondo di serie e con l'accento, stop, due chip, la capsula dell'ascolto: ognuno un `FluidGlassContainer`
con un pezzo solo); sotto, **i pannelli** (F3.2e), anche loro un contenitore con un pezzo solo ciascuno: la pillola
ridotta, la barra di scrittura e la capsula del composer **girano**, la card e il dock **scuriscono** in continuo; i pezzi
che portano (stop, chip, tasti tondi, il tasto del dock) sono vetro annidato del pannello. Il perché:
`skill/fluid-engine/references/moduli/ai-ui-overlay.md`, «Il vetro e il lato».

    adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
      --es gallery.page assistant --es assistant.fund foto --es assistant.auto true
    adb logcat -s FluidAssistente

| extra | valori | di serie |
|---|---|---|
| `assistant.fund` | `chiaro`, `scuro`, `foto` | `chiaro` |
| `assistant.auto` | `true` (2500 ms), un numero di ms (mai sotto 500), `spento` | spento |
| `assistant.theme` | `chiaro`, `scuro` | quello della galleria |
| `assistant.model` | `liquid`, `classic` | quello della galleria |
| `assistant.card` | `risposta` (ferma), `lavoro` (stop annidato e pallino che respira) | `risposta` |

Righe di logcat: `PAGINA <configurazione> fondo=… contenitori=… sonde=…` un secondo dopo che la pagina (ri)nasce, e
`FONDO <fondo> passo=<n> t=<uptime>` a ogni cambio di fondo.

Il fondo si legge in disegno: cambiarlo non ricompone niente e non apre viaggi. Ferma, con la card «risposta» e
l'automatico spento, la pagina non chiede fotogrammi (`GlassProbeQuietTest` la conta con le altre). I dati sono finti:
nessuna chiave, nessuna rete, nessun servizio dell'assistente; il microfono del composer non chiede il permesso.

Le prove: `AssistantPageTest` (Robolectric: i pezzi da soli e i pannelli sono un contenitore ciascuno, i pezzi sui
pannelli nessuno, dentro un contenitore dell'app sono pezzi di quello; card e dock dichiarano di scurire, barra, pillola e
composer di girare; il `weight` e la misura dell'app arrivano al vetro; fondo, card, tema e vetro cambiano senza
eccezioni) e `AssistantPureTest` (gli extra e il giro dei fondi). Gli elenchi delle riprese:
`artifacts/liquid-glass/sessioni/f3-2b/riprese.md` e `artifacts/liquid-glass/sessioni/f3-2e/riprese.md`.
