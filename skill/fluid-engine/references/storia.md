# Come siamo arrivati qui

Le tappe dell'engine, dalla 1.0.0 (23 agosto 2026) alla 3.1.0. Serve a capire **perché una cosa è com'è** e a
datare una voce («da quando c'è `FluidHero`?»). Il testo intero è in `CHANGELOG.md`; una riga per versione e le
voci BREAKING sono in `api/versioni.md` (generato). Date e numeri di versione qui sono quelli del changelog.

## Le tappe

| quando | versione | cosa cambia davvero |
|---|---|---|
| 2026-08-23 | **1.0.0** | Prima versione, estratta da ClasseViva Expressive 7.1.1 e ripulita di tutto ciò che sapeva cos'è un voto: tema, tipografia (Inter), motion, componenti, `engine-update`, `engine-config`, `engine-widget` |
| 2026-08-23 | 1.2.0 | `FluidHero`; ClasseViva smette di avere una copia locale del design system (8 499 righe cancellate) |
| 2026-08-24 | **1.3.0 → 1.4.0** | Il materiale: prima `GlassMaterial` a gradienti dipinti in un bordo di 2 dp (con tinta fino a 0,86 per compensare), poi la **libreria backdrop di Kyant0** copiata come sorgente: lente vera, campo di distanza, dispersione cromatica, bordo speculare. Tinta a un terzo, sfocatura raddoppiata |
| 2026-08-25 | 1.5.0 | Il vetro diventa anche contenuto, **ma solo dove c'è qualcosa da guardare attraverso**: `FluidScreen(ambient = FluidAmbient(...))`, il fondale che dà al vetro qualcosa da rifrangere |
| 2026-08-25 | **1.9.0** | **Fluid-physics**: qualsiasi forma di vetro diventa qualsiasi altra (`FluidForm`, `FluidPhysicsState`). Dalla 1.9.7 i preset lo usano: la riga toccata *diventa* il pop-up e ci ritorna |
| 2026-09-03 | 1.19.0 / 1.20.0 | `FluidHaptics` (vocabolario aptico) e i suggerimenti al primo uso (`FluidCoachMark`, `FluidTutorialHost`) |
| 2026-09-06 | 1.26 / 1.27 | `engine-ai` (assistente), allegati; nasce `engine-ai-bridge` (tool federati) |
| 2026-09-18 | 1.36.0 | Schermi larghi: due pannelli al massimo, e uno è la barra laterale |
| 2026-09-19 | **2.0.0** | La barra dell'app Fluidify portata nell'engine: `FluidFloatingTabBar` (rifrazione sotto il puck, schede nascoste e tinte) — «un livello sopra quello che l'engine sapeva fare da solo». 2.2.0: `FluidSwitch` è il LiquidToggle di Kyant0 (**BREAKING**) |
| 2026-09-23 | 2.5 – 2.7 | Lista-dettaglio adattiva, fuoco da tastiera, hover da mouse: l'engine impara i tablet |
| 2026-10-03 | 2.10.0 | `engine-wear`: il design system su Wear OS (opt-in) |
| 2026-10-12/13 | 2.11 – 2.13 | **I pulsanti crescono sotto il dito** (2.11), un pannello grande non è un pannello piccolo ingrandito (2.12), **vetro su vetro da solo** e materiale che costa solo se qualcuno lo guarda (2.13) |
| 2026-11-02 | **3.0.0** | Il grande step up «alla qualità della pillola della tab bar». **BREAKING**: licenza **GPL-3.0** (codice di LibrePods: pulsanti, pressione, gesto, aptica), pagine (`FluidNavHost`) e ponte dell'assistente. Uscita dopo quattro beta di Fluid Glass e due release candidate |
| 2026-11-03 | 3.0.1 – 3.0.4 | Correzioni trovate migrando le app (`FluidLabels`, versioni inglesi, `FluidPickerRow`…), back dal bordo (3.0.3), stato delle pagine che sopravvive alla ricreazione (3.0.2), interruttori di riga trascinabili e menu veloci (3.0.4), componenti di `engine-wear` dal ramo sweet-ride |
| 2026-11-05 – 09 | 3.0.5 – 3.0.7 | Icone della barra che seguono il cambio pagina (3.0.5), Back dei menu sopra pagine annidate e orchestratore dell'assistente (3.0.6), hook runtime IA e controlli pagina di PampAI (3.0.7) |
| 2026-11-09 | **3.1.0** | **La Fase 1 di Pampa Mail**: l'engine impara a fare una casella di posta senza che il dominio entri. Liste lunghe e paginate (`fluidGroupedItems`, `fluidSwipeActions`, `FluidJumpPill`), identità e selezione (`FluidAvatar`, `FluidIdentityColors`, `FluidBadge`, `FluidSelectionState`), pannello laterale e segni sulle zone della barra (`FluidDrawer`, `FluidTabBadge`), notifiche con azioni in linea, campi (`FluidTokenField`, `FluidDateTimeSheet`), il modulo opt-in `engine-richtext` (editor con elenchi e citazioni annidati), il contenitore web chiuso (`FluidWebContent`), i grafici e i file. Tutto **additivo per costruzione** (funzioni e tipi nuovi, parametri nuovi in coda con default che lasciano il comportamento identico): un'app che non cambia niente compila e si comporta come prima; l'unico effetto di build è la dipendenza `androidx.webkit` in `engine-ui` (una cautela di sorgente sulle lambda finali di `FluidTabRail`, `FluidSidebarRow` e `tab(…)` è nel changelog, voce «compat») |

## Le decisioni che spiegano il resto

- **L'engine è nato da un'app, non da un progetto.** Per questo il dominio non entra mai: ciò che sapeva cos'è un voto
  è rimasto in ClasseViva (`FeatureHero`, `GradePill`…), e `FluidHero` ci è entrato quando si è visto che il dominio
  stava solo nei nomi dell'enum.
- **Il vetro era «solo chrome» fino alla 1.4.0, non per estetica**: il vetro sopra il grigio è invisibile per
  costruzione. La risposta non è stata più vetro, ma un fondale che valga la pena guardare (1.5.0).
- **Si porta, non si reinventa.** Slider, interruttore, backdrop sono port dichiarati di Kyant0 (Apache-2.0); pulsanti,
  pagine e aptica sono port di LibrePods (GPL-3.0) perché *devono* comportarsi esattamente come loro. Ogni file portato
  ha in testa provenienza, licenza ed elenco di ciò che è cambiato.
- **Il codice non si aggiorna da remoto.** Il manifest cambia comportamento, mai codice (`limiti.md`).
- **Un rename pubblico è major.** Per questo il changelog distingue ciò che rompe la compilazione da ciò che **cambia a
  vista** o **al tatto** e compila lo stesso.

## L'adozione nelle app (`ADOZIONE.md`)

Ordine deciso da Alessio il 2026-08-23: ClasseViva Expressive → Pampa Store → KeyVoice → universal_converter,
Pampa widgets in coda. Stato: ClasseViva, universal_converter, Pampa widgets e Pampa Store hanno tema **e** componenti;
KeyVoice usa solo `engine-update` (la dashboard è in Compose dalla 2.7.1, l'IME resta a View). Altre app (l'app dell'orologio di Fluidify, PampAI,
MyCloud: i loro nomi stanno nel changelog accanto alle correzioni che hanno fatto trovare) l'hanno adottata migrando alla 3.0. Il piano e le decisioni aperte vivono in
`ADOZIONE.md`: leggilo prima di una migrazione, aggiornalo quando una fase si chiude.

## Dove sono i dettagli di una versione

| cosa | dove |
|---|---|
| il testo di ogni versione | `CHANGELOG.md` |
| una riga per versione e i BREAKING | `api/versioni.md` |
| il perché della 3.0 (decisioni, stato, analisi di LibrePods) | `artifacts/3.0-decisioni.md`, `artifacts/3.0-stato.md`, `docs/riferimenti-3.0.md` |
| il confronto componente per componente con LibrePods | `docs/10-coerenza.md` (matrice) |
| la migrazione delle app alla 3.0 | `ADOZIONE.md`, «Migrare alla 3.0»; `artifacts/design/migrazione-3.0.md` |
