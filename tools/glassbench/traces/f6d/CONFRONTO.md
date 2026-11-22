# Il velo sulle pagine

Ogni `trace.json` contiene una corsa a 120 Hz della macchina di `FluidPageHost`. Ci sono tre corse indipendenti per tema. Il test confronta ogni elemento di ogni fotogramma e salva una copia quando le tre corse coincidono. Il chiaro esce a destra, lo scuro a sinistra. La pagina inferiore misura 240/255 in chiaro e 96/255 in scuro.

| Traccia | Riferimento | Riscontro in chiaro | Soglia |
|---|---|---|---|
| `pagina-arriva` | Note 26, WWDC25 284, f22230–52 e f22611–23. Impostazioni 26.1 e 27, F06/F07. | Ai quartili osservabili lo scarto massimo è 0,49 punti in Note e 2,16 punti in Impostazioni. Il confronto grezzo più distante è F06 a circa l’81% (+2,91 punti). | ±2,5 punti ai quartili. |
| `pagina-col-dito` | Note 26, WWDC25 284, F04 f23463–23542. | Ai tre quartili lo scarto massimo è 0,72 punti. Le tre sonde restano ferme durante i 183 ms di sosta. | ±2,5 punti ai quartili, ≤1 punto durante la sosta e all’inversione. |
| `pagina-col-dito-annullata` | Manca un annullo Apple misurabile. | Il primo frame della molla segue la posa con errore inferiore a 0,4 punti in entrambi i temi. All’arrivo il velo visibile è zero. | ≤1 punto al rilascio e a riposo. |
| `zoom-scurisce` | WWDC24 10145, iPadOS 18, f958–966 e f3183–3190. | Apertura e ritorno entro 1,23 punti ai quartili interpolabili. Nei tempi il massimo scarto è 24 ms. | ±3 punti e ±33 ms equivalenti nel tratto osservabile. |

Le percentuali ai quartili derivano da un’interpolazione fra due pose misurate. Il CSV conserva entrambi i fotogrammi per lato. I punti privi di due campioni validi rimangono esclusi. L’alfa proviene dai pixel di tre zone della pagina inferiore. La posizione delle sonde e la coordinata del bordo sono nel CSV di ciascuna traccia. L’ombra di elevazione Android si misura separatamente sulla finestra Compose con PixelCopy. I PNG del banco isolano il velo. Per lo scuro e l’annullo, la parità Apple resta NV. [Ricerca F6D.0](../../../../docs/liquid-glass/ricerca/scurimento.md) · [Dati della sessione](../../../../artifacts/liquid-glass/sessioni/f6d-1/rapporto.md)

## Ripetere

1. Impostare `GLASS_F6D_DIR` su questa cartella e `GLASS_F6D_PIXELS` sulla cartella di sessione seguita da `pixelcopy/lg-f6d-1`. Eseguire con la coda Gradle i test `F6dPageDimTraceTest`, `FluidPageDimTest` e `F6dPageVeilPixelsTest`.
2. Con il Python del banco, eseguire `misura.py <cartella-sessione> <cartella-ricerca-F6D.0>`. Scrive ogni frame, i collage, le sonde e i CSV. Il secondo fondale di contrasto verifica ogni pixel interno al fronte su campiture e pagine di testo.
3. Eseguire `video.py` con gli stessi due percorsi e FFmpeg sul PATH. I video conservano tutti i fotogrammi Fluid e mostrano a fianco la clip di confronto quando disponibile. La demo dello zoom a metà velocità viene riportata al tempo reale. Le demo di Note conservano il ritmo pubblicato.
