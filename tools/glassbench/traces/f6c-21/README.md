# Apertura e ritorno dalla miniatura

Dodici tracce a 120 Hz di PDF, DOCX, ZIP, JSON, audio M4A e DNG, in chiaro e scuro.
Ogni collage contiene tutti i 211 fotogrammi della sua corsa. Il tocco è al fotogramma 6,
il ritorno al 132.

`F6cDocumentViewerTraceTest.thumbnailOpenAndReturnAt120Hz` esegue ogni corsa tre volte
e confronta ogni fotogramma. Sagoma, corsa e velo vengono da `FluidPageHostState`.
Per i documenti lo scambio delle facce usa `ViewerEntranceMotion` con prontezza
simulata. Per il DNG la tavola usa il percorso della stessa immagine nel livello condiviso.
Il contenuto delle pagine è illustrativo. Le miniature PDF e M4A vengono dagli asset della galleria,
quelle DOCX, ZIP e JSON dalle catture F6C.17 e la foto DNG dal suo JPEG incorporato.

Impostare `GLASS_F6C21_DIR` su questa cartella ed eseguire il test con la coda Gradle.
Per ogni traccia eseguire `glassmotion.py validate` e `glassmotion.py collage <trace.json> --step-ms 0 --cols 10 --cell-width 152`.
`python confronta.py` stampa i tempi ai quartili e gli scarti da F6C.10 e F6D.1.
