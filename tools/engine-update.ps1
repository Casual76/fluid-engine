<#
.SYNOPSIS
  Sposta un'app su un'altra versione del Fluid Engine.

.DESCRIPTION
  Aggancia la cartella dell'engine al tag richiesto, riscrive engine.properties e stampa cosa e'
  cambiato fra le due versioni. Non compila e non committa: quello lo decidi dopo aver letto il
  changelog.

  Il rollback e' lo stesso comando con il numero di prima: il pin e' un tag, e i tag non si spostano.

.PARAMETER AppRoot
  La cartella del progetto Gradle dell'app (quella con engine.properties).

.PARAMETER Version
  La versione dell'engine, per esempio 1.1.0. In alternativa usa -Latest.

.PARAMETER Latest
  Prende la versione piu' alta disponibile sul canale indicato in engine.properties.

.PARAMETER AllowDirty
  Aggiorna anche se l'engine ha modifiche locali. Le perderai.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File engine\tools\engine-update.ps1 -AppRoot . -Version 1.1.0
#>
[CmdletBinding(DefaultParameterSetName = "Explicit")]
param(
  [Parameter(Mandatory = $true)][string]$AppRoot,
  [Parameter(Mandatory = $true, ParameterSetName = "Explicit")][string]$Version,
  [Parameter(Mandatory = $true, ParameterSetName = "Latest")][switch]$Latest,
  [switch]$AllowDirty
)

$ErrorActionPreference = "Stop"

# Set-Content -Encoding utf8 in PowerShell 5.1 scrive un BOM, e un BOM in testa a un settings.gradle
# che non ce l'aveva e' una modifica gratuita a un file dell'app (che Groovy, a volte, non digerisce).
function Write-TextFile($path, $text) {
  $encoding = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($path, $text, $encoding)
}


function Fail($message) {
  Write-Host "ERRORE: $message" -ForegroundColor Red
  exit 1
}

# git scrive su stderr anche quando va tutto bene, e con ErrorActionPreference a Stop PowerShell 5.1
# lo tratta come errore terminante. Qui conta solo il codice di uscita.
function Invoke-Git {
  param([string[]]$Arguments)
  $previous = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = & git @Arguments 2>&1 | ForEach-Object { $_.ToString() }
    return [pscustomobject]@{ Code = $LASTEXITCODE; Lines = @($output) }
  } finally {
    $ErrorActionPreference = $previous
  }
}

function Read-EngineProperties($path) {
  $values = @{}
  foreach ($line in Get-Content -LiteralPath $path) {
    $trimmed = $line.Trim()
    if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) { continue }
    $index = $trimmed.IndexOf("=")
    if ($index -lt 1) { continue }
    $values[$trimmed.Substring(0, $index).Trim()] = $trimmed.Substring($index + 1).Trim()
  }
  return $values
}

$appRootFull = (Resolve-Path -LiteralPath $AppRoot).Path
$propertiesPath = Join-Path $appRootFull "engine.properties"
if (-not (Test-Path -LiteralPath $propertiesPath)) {
  Fail "in $appRootFull non c'e' engine.properties. Usa prima engine-install.ps1."
}

$properties = Read-EngineProperties $propertiesPath
$enginePath = $properties["engine.path"]
if ([string]::IsNullOrWhiteSpace($enginePath)) { $enginePath = "engine" }
$channel = $properties["engine.channel"]
if ([string]::IsNullOrWhiteSpace($channel)) { $channel = "stable" }
$currentVersion = $properties["engine.version"]
$modules = $properties["engine.modules"]
$mode = $properties["engine.mode"]

# Un'app "porto" non ha una cartella dell'engine da spostare: ha una copia del look scritta nel suo
# codice, perche' i moduli non ci entrano (Compose Multiplatform contro androidx). Aggiornarla
# significa che qualcuno riporta le modifiche a mano, quindi qui si dice come stanno le cose e si
# esce senza toccare niente: alzare il numero da solo direbbe una bugia.
if ($mode -eq "port") {
  Write-Host ""
  Write-Host "$appRootFull e' un porto del look, non un'installazione dell'engine." -ForegroundColor Yellow
  Write-Host "  fermo alla: $currentVersion" -ForegroundColor Yellow
  $source = $properties["engine.portFile"]
  if (-not [string]::IsNullOrWhiteSpace($source)) {
    Write-Host "  il file da riallineare: $source" -ForegroundColor Yellow
  }
  Write-Host "Non tocco niente: un porto si aggiorna a mano, e poi si alza FLUID_PORT_OF." -ForegroundColor Yellow
  exit 0
}

$engineFull = Join-Path $appRootFull $enginePath
if (-not (Test-Path -LiteralPath (Join-Path $engineFull "ENGINE_VERSION"))) {
  Fail "$engineFull non sembra un Fluid Engine."
}
if (-not (Test-Path -LiteralPath (Join-Path $engineFull ".git"))) {
  Fail "$engineFull non e' un repo git: e' un'installazione in modalita' copia, si aggiorna ricopiando i file."
}

Push-Location $engineFull
try {
  $status = Invoke-Git @("status", "--porcelain")
  if ($status.Code -ne 0) { Fail "git non risponde in $engineFull." }
  $dirty = $status.Lines | Where-Object { $_.Trim().Length -gt 0 }
  if ($dirty -and -not $AllowDirty) {
    Write-Host "L'engine ha modifiche locali non committate:" -ForegroundColor Yellow
    $dirty | ForEach-Object { Write-Host "  $_" }
    Fail "committale o scartale prima di aggiornare (o passa -AllowDirty, e perdile)."
  }

  Write-Host "Scarico i tag..." -ForegroundColor Cyan
  $fetch = Invoke-Git @("fetch", "--tags", "--quiet")
  if ($fetch.Code -ne 0) {
    Write-Host "git fetch non riuscito: proseguo con i tag gia' presenti in locale." -ForegroundColor Yellow
  }

  if ($Latest) {
    $tagList = Invoke-Git @("tag", "--list", "engine-*")
    $tags = $tagList.Lines | Where-Object { $_.Trim().Length -gt 0 }
    if ($channel -eq "stable") {
      # Sul canale stable un tag con un suffisso (engine-1.2.0-beta1) non e' un candidato.
      $tags = $tags | Where-Object { $_ -match "^engine-[0-9]+\.[0-9]+\.[0-9]+$" }
    } else {
      $tags = $tags | Where-Object { $_ -match "^engine-[0-9]+\.[0-9]+\.[0-9]+" }
    }
    if (-not $tags) { Fail "nessun tag engine-* disponibile sul canale $channel." }
    $sorted = $tags | Sort-Object -Property @{ Expression = {
      $numbers = [regex]::Match($_, "^engine-([0-9]+)\.([0-9]+)\.([0-9]+)")
      [int]$numbers.Groups[1].Value * 1000000 + [int]$numbers.Groups[2].Value * 1000 + [int]$numbers.Groups[3].Value
    } }, @{ Expression = { $_ } }
    $targetTag = $sorted[-1]
    $Version = $targetTag -replace "^engine-", ""
  } else {
    $targetTag = "engine-$Version"
  }

  $verify = Invoke-Git @("rev-parse", "--verify", "--quiet", "refs/tags/$targetTag")
  if ($verify.Code -ne 0) {
    $available = (Invoke-Git @("tag", "--list", "engine-*")).Lines -join ", "
    Fail "il tag $targetTag non esiste. Disponibili: $available"
  }

  if ($currentVersion -eq $Version) {
    Write-Host "Gia' su ${Version}: riaggancio comunque il tag." -ForegroundColor Yellow
  }

  $checkout = Invoke-Git @("checkout", "--quiet", $targetTag)
  if ($checkout.Code -ne 0) {
    Fail "checkout di ${targetTag} non riuscito: $($checkout.Lines -join ' ')"
  }
  Write-Host "Engine agganciato a $targetTag." -ForegroundColor Green

  $changelogPath = Join-Path $engineFull "CHANGELOG.md"
  if ((Test-Path -LiteralPath $changelogPath) -and $currentVersion -and ($currentVersion -ne $Version)) {
    Write-Host ""
    Write-Host "Cosa e' cambiato ($currentVersion -> $Version)" -ForegroundColor Cyan
    $printing = $false
    foreach ($line in Get-Content -LiteralPath $changelogPath) {
      if ($line -match "^##\s") {
        if ($line -match [regex]::Escape($currentVersion)) { break }
        $printing = $true
      }
      if ($printing) {
        if ($line.TrimStart().StartsWith("<!--")) { continue }
        if ($line -match "BREAKING") {
          Write-Host $line -ForegroundColor Yellow
        } else {
          Write-Host $line
        }
      }
    }
  }
} finally {
  Pop-Location
}

$today = Get-Date -Format "yyyy-MM-dd"
# I moduli si riportano com'erano: sono una scelta dell'app (un'app senza Compose non puo' ospitare
# engine-ui), non qualcosa che un aggiornamento di versione ha il diritto di rimettere a default.
$moduleLine = ""
if (-not [string]::IsNullOrWhiteSpace($modules)) {
  $moduleLine = "engine.modules=$modules`r`n"
}
$updated = @"
# Quale Fluid Engine usa questa app. Aggiornato da engine-update.ps1, verificato da engine-doctor.ps1.
engine.version=$Version
engine.channel=$channel
engine.path=$enginePath
${moduleLine}engine.updatedAt=$today
"@
Write-TextFile $propertiesPath $updated

Write-Host ""
Write-Host "engine.properties aggiornato a $Version." -ForegroundColor Green
Write-Host "Ora tocca a te:" -ForegroundColor Cyan
Write-Host "  ./gradlew.bat --no-daemon :app:assembleDebug testDebugUnitTest"
Write-Host "  git add $enginePath engine.properties"
Write-Host "  git commit -m `"engine: $currentVersion -> $Version`""
