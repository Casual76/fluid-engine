# Campioni e ambiente del visore 3D

I modelli sotto `src/androidTest/assets/corpus` sono originali del pacchetto, disponibili con [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). Il generatore è `tools/make-corpus.py`.

| File | Caso |
|---|---|
| `anelli.glb` | Due materiali metallici PBR e un'animazione |
| `tessuto.gltf`, `tessuto.bin`, `tessuto.png` | Geometria e texture come file a fianco |
| `piramide-ascii.stl`, `piramide-binaria.stl` | La stessa piramide in STL ASCII e binario |
| `piramide.obj`, `materiali.mtl`, `tessuto.png` | OBJ con colori e texture da MTL |
| `250mila.glb`, `milione.glb` | Cinque e venti istanze da 50.000 triangoli con rotazione animata |

## Rigenerare l'ambiente e il piano d'ombra

Prendi `matc` e `cmgen` dal pacchetto della piattaforma host di [Filament v1.71.5](https://github.com/google/filament/releases/tag/v1.71.5). Usa lo stesso numero di versione delle librerie in `build.gradle`. Esegui dalla cartella del modulo con gli eseguibili nel PATH.

```powershell
$dest = 'src/main/assets/fluid-viewer-3d'
matc -p mobile -a all -o "$dest/ground.filamat" materials/ground.mat
$studioWork = Join-Path $env:TEMP 'f6c14-ibl'
New-Item -ItemType Directory -Force $studioWork | Out-Null
python materials/studio.py "$studioWork/studio.hdr"
cmgen -s 32 -f ktx -x "$studioWork/studio" "$studioWork/studio.hdr"
Copy-Item -LiteralPath "$studioWork/studio/studio_ibl.ktx" -Destination "$dest/studio.ktx"
```

Il KTX contiene le armoniche sferiche e i riflessi. `SceneDeviceTest.shadowMaterialAndStudioLoadOnThePackagedFilament` carica entrambi gli asset con l'engine dell'APK.
