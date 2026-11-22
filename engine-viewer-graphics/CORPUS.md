# Campioni per il visore grafico

I file sotto `src/androidTest/assets/corpus` sono inclusi soltanto nell'APK di prova. Il generatore `tools/make-corpus.py` produce grafiche, font e contenitori per esercitare i lettori. I campioni generati sono originali del pacchetto e sono disponibili con [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).

`python tools/make-corpus.py --fonts-only` rigenera i soli caratteri, con le maiuscole, le minuscole, le cifre e i segni del provino.

| File | Formato o percorso da provare |
|---|---|
| `studio.heic`, `studio.avif` | Codec di Android |
| `studio-pages.tiff` | Due pagine RGB con LZW |
| `composito.psd`, `miniatura.psd` | Composito RGB e ripiego dalla risorsa JPEG 1036 |
| `tracce.svg`, `tracce.svgz` | Tre tratti sottili da ingrandire, versione compressa con gzip |
| `studio.ttf`, `studio.otf`, `studio.ttc` | TrueType, OpenType CFF e collezione con due facce |
| `studio-variable.ttf`, `studio.woff`, `studio.woff2` | Asse wght, decompressione WOFF1 e ripiego WOFF2 |

I RAW seguenti sono originali di fotocamera scaricati da [raw.pixls.us](https://raw.pixls.us/) con licenza CC0. Gli hash sono quelli pubblicati nell'indice.

| File | Campione | SHA-256 |
|---|---|---|
| `camera.dng` | [Blackmagic Micro Cinema Camera, 7317](https://raw.pixls.us/getfile.php/7317/nice/Blackmagic%20-%20Micro%20Cinema%20Camera%20-%2012bit%20%2816%3A9%29.dng) | `4c65b8cda205087cfb94d8931811e53e15eb4df538ad67c1b4a3e76c1185b277` |
| `camera-pixel.dng` | [Google Pixel 4 XL, 3502](https://raw.pixls.us/getfile.php/3502/nice/Google%20-%20Pixel%204%20XL%20-%2016bit%20%284%3A3%29.dng) | `0c03a6c93b4b8593cd0e4426166e6389710007238b82eb23861ef191322f0c54` |
| `camera.cr2` | [Canon EOS 40D, 2102](https://raw.pixls.us/getfile.php/2102/nice/Canon%20-%20EOS%2040D%20-%20sRAW2%20%28sRAW%29%20%283%3A2%29.CR2) | `ba644e7dd2abe74eca260e67f0206ff113bf0f62e710f8130611e964d6be5bf1` |
| `camera.cr3` | [Canon EOS R6, 4659](https://raw.pixls.us/getfile.php/4659/nice/Canon%20-%20EOS%20R6%20-%203%3A2.CR3) | `74abb0a113d075ad9887a058082f40dd2a938c4813a08474d82356f11a027778` |
| `camera.nef` | [Nikon D2H, 5227](https://raw.pixls.us/getfile.php/5227/nice/Nikon%20-%20D2H%20-%2012bit%2012bit%20compressed%20%28Lossy%20%28type%201%29%29%20%283%3A2%29.NEF) | `155edb938f884ea7372ce98d4ff5f965c3e413b43b95bc9923da6e92082cf914` |
| `camera.arw` | [Sony ILCE-7S, 1582](https://raw.pixls.us/getfile.php/1582/nice/Sony%20-%20ILCE-7S%20-%2014bit%2014bit%20compressed%20%283%3A2%29.ARW) | `a35ebb2fbec929daa5beb20d1ce5c15a8aac7b1a7a231455387f3df8a7442e07` |
