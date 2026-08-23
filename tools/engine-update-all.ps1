<#
.SYNOPSIS
  Sposta tutte le app che usano il Fluid Engine sulla stessa versione.

.DESCRIPTION
  Cerca sotto -Root ogni progetto con un engine.properties e chiama engine-update.ps1 su ciascuno.
  Ogni app resta comunque padrona del proprio pin: quelle che non sono sotto -Root, o che falliscono,
  restano dove sono.

.PARAMETER Root
  La cartella che contiene i progetti, per esempio "C:\VibeCoded Projects".

.PARAMETER Version
  La versione dell'engine da agganciare ovunque.

.PARAMETER WhatIf
  Dice cosa farebbe, senza toccare niente.

.PARAMETER Build
  Dopo ogni aggiornamento compila l'app, e si ferma alla prima che non compila.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools\engine-update-all.ps1 -Root "C:\VibeCoded Projects" -Version 1.1.0 -WhatIf
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Root,
  [Parameter(Mandatory = $true)][string]$Version,
  [switch]$WhatIf,
  [switch]$Build,
  [int]$Depth = 4
)

$ErrorActionPreference = "Stop"

$updateScript = Join-Path $PSScriptRoot "engine-update.ps1"
if (-not (Test-Path -LiteralPath $updateScript)) {
  Write-Host "ERRORE: engine-update.ps1 non e' accanto a questo script." -ForegroundColor Red
  exit 1
}

$rootFull = (Resolve-Path -LiteralPath $Root).Path
Write-Host "Cerco app sotto $rootFull ..." -ForegroundColor Cyan

# -Depth tiene la ricerca fuori dalle cartelle build/ e dalle dipendenze, dove non c'e' niente da
# aggiornare e ci sono decine di migliaia di file.
$found = Get-ChildItem -LiteralPath $rootFull -Filter "engine.properties" -Recurse -Depth $Depth -File -ErrorAction SilentlyContinue |
  Where-Object { $_.FullName -notmatch "\\build\\" -and $_.FullName -notmatch "\\\.git\\" }

if (-not $found) {
  Write-Host "Nessuna app trovata (nessun engine.properties)." -ForegroundColor Yellow
  exit 0
}

Write-Host ""
foreach ($properties in $found) {
  $appRoot = $properties.Directory.FullName
  $current = (Select-String -LiteralPath $properties.FullName -Pattern "^engine\.version=(.*)$").Matches.Groups[1].Value
  $isPort = [bool](Select-String -LiteralPath $properties.FullName -Pattern "^engine\.mode=port$")
  Write-Host "-> $appRoot" -ForegroundColor White
  Write-Host "   attuale: $current  richiesta: $Version"

  # I porti si contano fra le app che usano l'engine, ma non si aggiornano con un comando: qui
  # esistono per essere *visti*, perche' una copia che nessuno nomina e' una copia che resta indietro.
  if ($isPort) {
    if ($current -eq $Version) {
      Write-Host "   porto del look, gia' allineato." -ForegroundColor DarkGray
    } else {
      Write-Host "   porto del look: fermo alla $current, va riportato a mano." -ForegroundColor Yellow
    }
    continue
  }

  if ($WhatIf) {
    Write-Host "   (WhatIf: non tocco niente)" -ForegroundColor Yellow
    continue
  }

  & powershell -NoProfile -ExecutionPolicy Bypass -File $updateScript -AppRoot $appRoot -Version $Version
  if ($LASTEXITCODE -ne 0) {
    Write-Host "   aggiornamento non riuscito: mi fermo qui, le app rimanenti restano come stanno." -ForegroundColor Red
    exit 1
  }

  if ($Build) {
    $gradlew = Join-Path $appRoot "gradlew.bat"
    if (Test-Path -LiteralPath $gradlew) {
      Write-Host "   compilo..." -ForegroundColor Cyan
      Push-Location $appRoot
      try {
        & $gradlew --no-daemon assembleDebug
        if ($LASTEXITCODE -ne 0) {
          Write-Host "   BUILD FALLITA. Mi fermo: risolvi qui prima di andare avanti." -ForegroundColor Red
          exit 1
        }
        Write-Host "   build ok." -ForegroundColor Green
      } finally {
        Pop-Location
      }
    } else {
      Write-Host "   nessun gradlew.bat in ${appRoot}: salto la build." -ForegroundColor Yellow
    }
  }
  Write-Host ""
}

Write-Host "Fatto. Ricordati di committare submodule + engine.properties in ogni app." -ForegroundColor Green
