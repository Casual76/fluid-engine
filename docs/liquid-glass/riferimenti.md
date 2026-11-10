# Riferimenti: il Liquid Glass di Apple e dove sta il Fluid Engine

Studio del 2026-11-10 sul materiale lasciato da Alessio, fatto sull'engine 3.1.2. Serve a non rifare
lo studio: dice dove guardare nei frame, cosa fa Apple, cosa fa l'engine oggi e dove sta la distanza.
Il piano di lavoro che ne segue si scrive qui sotto quando Alessio ha deciso.

## Il materiale

Cartella: `C:\Users\casua\Downloads\Fluid glass huge step up source material`

| cosa | dove | note |
|---|---|---|
| trascrizione | `WWDC25 Transcribed\WWDC25- Meet Liquid Glass - Apple.md` | sessione «Meet Liquid Glass», 19:22, con i tempi |
| frame del video | `WWDC all frames\frames_video\frame_tNNNNs.jpg` | 1173 frame, **uno al secondo**, 1280×720: buoni per l'aspetto, troppo radi per i tempi di un'animazione |
| scelti a mano | `WWDC25 Transcribed\Reference handpicked\*.png` | 45 screenshot 2559×1439, video fermo: **due animazioni passo per passo** (sotto) |
| `Frames extracted single button to 2` | vuota | probabilmente doveva contenere una terza sequenza |

Per i tempi veri di un'animazione serve il video: con `ffmpeg` (installato) si estraggono i 60 fps del
pezzo che interessa.

### Le due sequenze scelte a mano

Sono frame consecutivi del video (29,97 fps): la prima è `f13046`→`f13061`, cioè il menu sulla foto a
**7:15**; la seconda `f28857`→`f28879`, cioè la barra a **16:03**. Le misure fatte sul video intero stanno in
[ricerca/riferimenti-misure.md](ricerca/riferimenti-misure.md): per i numeri vale quel documento, non questo.

- **`161313` → `161526`: il tasto che diventa menu.** Capsula di tre tasti su una foto. Le altre due
  icone si sfocano e spariscono, la capsula si stringe in una goccia, la goccia sale e cresce con un
  contorno **irregolare** (mai un rettangolo che cambia misura), le voci del menu compaiono già dentro
  **deformate dalla lente** e sfocate, poi la sagoma si assesta in un rettangolo dagli angoli continui
  e il materiale diventa più spesso e più smerigliato (prende il colore sfocato della foto).
- **`161838` → `162000`: la barra che cambia contesto.** Due capsule (cartella, cestino) + (annulla,
  invia) diventano (graffetta, penna, più) + (freccia tinta). Le capsule **cambiano larghezza in
  continuo**, le icone escono ed entrano **sfocandosi** (non in dissolvenza), la coppia di destra si
  chiude in un tasto tondo solo, e la larghezza finale oscilla appena (molla che si assesta).

### Indice dei frame (minuto:secondo = `frame_t` in secondi)

| tempo | cosa mostra |
|---|---|
| 0:48–1:06 | la lente a riposo: capsula, tasto tondo, menu, su fondo grigio piatto |
| 1:39–1:54 | un tasto visto di lato: disco di vetro sollevato, luce concentrata sotto |
| 2:33–2:48 | confronto fra il materiale vecchio (sfocatura) e la lente |
| 2:51–2:55 | vetro sopra foto e video |
| 2:56–3:09 | comparsa: il vetro **si materializza** modulando la lente, non l'opacità |
| 3:39–3:54 | tocco: bagliore che parte dal dito, il tasto cresce e l'icona sotto si deforma |
| 4:00–4:06 | capsula che cede sotto il dito (flessione) |
| 4:09–4:30 | «lift into glass»: pomello dell'interruttore e del cursore che diventano lente |
| 4:45–5:03 | la barra che cambia tasti fra un contesto e l'altro (tre stati) |
| 5:06–5:24 | il menu che si apre dal suo tasto |
| 5:33–5:51 | i controlli in vista obliqua: spessori, bordi, la fusione di due forme (in basso) |
| 6:21–7:00 | adattività: ombra e tinta sopra disegno, testo, foto scura (qui il vetro e le icone girano in scuro) |
| 7:15–7:27 | il menu più spesso del tasto: ombra più profonda, più lente, più diffusione |
| 7:42–8:30 | barre laterali, luce del contenuto che si riversa sul vetro |
| 8:54–10:06 | scroll edge effect: morbido, scurimento su contenuto scuro, duro |
| 10:45–11:06 | gli strati esplosi; 11:09–11:36 la luce che corre sul bordo |
| 11:39–12:09 | l'ombra più forte sul testo, più leggera sul fondo piatto |
| 12:12–12:21 | il bagliore al tocco che si propaga |
| 13:06–13:48 | vetro solo sulla navigazione; mai vetro su vetro |
| 14:03–14:45 | varianti regular e clear |
| 15:15–15:57 | i piccoli girano chiaro/scuro, i grandi no; le icone girano insieme |
| 16:09–17:18 | la tinta che segue la luminosità dello sfondo; 17:21–18:03 tinta solo sull'azione principale |
| 18:15–18:42 | accessibilità: trasparenza ridotta, contrasto, movimento ridotto |

## Cosa fa Apple, in nove proprietà

1. **Lente.** La sagoma si legge perché piega ciò che ha dietro; compare e scompare modulando la lente.
2. **Luce.** Uno strato di riflessi: sorgenti che illuminano il bordo secondo la geometria; il bordo
   è una linea sottile e netta, più forte su due lati opposti; la luce può muoversi.
3. **Ombra adattiva.** Più forte sopra il testo, più leggera sopra un fondo piatto.
4. **Tinta e gamma adattive.** I piccoli (barre, tasti) girano da chiaro a scuro secondo lo sfondo, e
   le icone con loro; i grandi (menu, barre laterali) si adattano senza girare.
5. **Spessore con la taglia.** Più grande = più spesso: ombra più ricca, più lente, luce più diffusa.
6. **Tocco.** Si flette come un gel e si accende di luce dal punto del dito, fino agli elementi vicini;
   i controlli «salgono nel vetro» solo mentre li si tocca.
7. **Trasformazione.** I controlli stanno su un unico piano flottante e cambiano forma da un contesto
   all'altro; il menu esce dal suo tasto.
8. **Bordo dello scorrimento.** Il contenuto si dissolve sotto i controlli che galleggiano (morbido,
   duro, scurimento su contenuto scuro): nessuna fascia di vetro.
9. **Tinta e varianti.** Il colore si mappa sulla luminosità dello sfondo; regular e clear;
   accessibilità come modificatori del materiale.

Regole d'uso dette nella sessione: vetro solo sullo strato di navigazione, mai vetro su vetro, tinta
solo sull'azione principale, forme concentriche agli angoli dello schermo.

## Dove sta l'engine 3.1.2

| proprietà | oggi | distanza |
|---|---|---|
| lente | c'è: shader di Kyant, campo di distanza del rettangolo arrotondato, spostamento al bordo (`glass/backdrop/internal/Shaders.kt`) | piccola. Ma sugli sfondi morbidi delle app (la lavata) non c'è niente da piegare, e la lente non si vede |
| luce | un solo filo speculare, simmetrico (`pow(abs(dot(grad, luce)))`), bianco al 50–60%, disegnato in un layer a parte; nessuna ombreggiatura del corpo, nessuna seconda sorgente | **grande**: è il motivo per cui un tasto su fondo piatto sembra un cerchio tinto |
| ombra | fissa: nero 10–16%, un solo strato, scostamento `raggio/6`; `Content` non ne ha | grande |
| adattività | nessuna: decide il tema; sopra le foto le tinte `…OnPhoto` si scelgono a mano | grande; rischio medio (serve leggere lo sfondo) |
| spessore con la taglia | c'è dalla 2.12 per sfocatura e lente (`glassSizedOptics`) | piccola: manca su luce e ombra |
| tocco | pressione, crescita ×1,5 dei tondi, bagliore (`GlassTouchHighlight`), goccia di cursore e interruttore | media: manca la flessione a gel e il bagliore che passa ai vicini |
| trasformazione | Fluid-physics (gruppo fino a 6 pezzi fusi, goccia, guida esterna), barra ancorata fra pagine, `FluidMorphMenu` | media-grande: non è generale né automatica; nella stessa pagina i tasti non si trasformano; le icone si incrociano in dissolvenza |
| bordo dello scorrimento | fascia di vetro `Bar` con `FadeDown` | media |
| tinta | pellicola fissa con il 20% dell'accento | media |

Due famiglie di shader convivono: quella di Kyant (una sagoma, `glassSurface` e i controlli) e quella di
Fluid-physics (unione di sagome). È la ragione tecnica per cui «qualsiasi elemento che si trasforma»
oggi richiede di scegliere prima un motore.

## Limiti di Android (da dire prima)

- Tutta l'ottica per pixel richiede AGSL: **Android 13+**. Sotto resta il ripiego di oggi.
- Niente di ciò che sta in un'altra finestra (barra di stato, tastiera, altre app) si può rifrangere.
- Ogni passata in più si paga: sull'S25 il RenderThread era già saturo con molti vetri (misure del
  2026-10-13). Il materiale nuovo deve sostituire passate, non aggiungerne.
- Leggere lo sfondo per adattarsi costa: va fatto su una miniatura, poche volte al secondo, con isteresi.
- Identico ad Apple no: stessa grammatica sì.
