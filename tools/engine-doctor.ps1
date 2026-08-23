<#
.SYNOPSIS
  Verifica che l'engine dentro un'app sia quello che l'app dice di usare.

.DESCRIPTION
  Controlla le quattro cose che possono divergere in silenzio:
    - engine.properties, ENGINE_VERSION e EngineBuild.VERSION dicono la stessa versione;
    - la cartella dell'engine e' agganciata al tag corrispondente;
    - il settings.gradle include davvero i moduli;
    - nessuno ha modificato l'engine in locale senza committare (il modo tipico in cui una copia
      condivisa smette di essere condivisa).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File engine\tools\engine-doctor.ps1 -AppRoot .
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$AppRoot
)

$ErrorActionPreference = "Stop"
$problems = 0

function Ok($message) { Write-Host "  ok    $message" -ForegroundColor Green }
function Warn($message) { Write-Host "  nota  $message" -ForegroundColor Yellow }
function Bad($message) {
  Write-Host "  KO    $message" -ForegroundColor Red
  $script:problems++
}

# git scrive su stderr anche quando non e' successo niente di grave, e con ErrorActionPreference a
# Stop PowerShell 5.1 tratta quello come un errore terminante. Le chiamate passano tutte da qui, che
# guarda il codice di uscita e ignora il resto.
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

$appRootFull = (Resolve-Path -LiteralPath $AppRoot).Path
Write-Host "Fluid Engine - controllo di $appRootFull" -ForegroundColor Cyan
Write-Host ""

$propertiesPath = Join-Path $appRootFull "engine.properties"
if (-not (Test-Path -LiteralPath $propertiesPath)) {
  Bad "manca engine.properties: questa app non risulta collegata all'engine."
  exit 1
}

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesPath) {
  $trimmed = $line.Trim()
  if ($trimmed.Length -eq 0 -or $trimmed.StartsWith("#")) { continue }
  $index = $trimmed.IndexOf("=")
  if ($index -lt 1) { continue }
  $properties[$trimmed.Substring(0, $index).Trim()] = $trimmed.Substring($index + 1).Trim()
}

$pinned = $properties["engine.version"]
$enginePath = $properties["engine.path"]
if ([string]::IsNullOrWhiteSpace($enginePath)) { $enginePath = "engine" }
$engineFull = Join-Path $appRootFull $enginePath
Ok "engine.properties: versione $pinned, cartella $enginePath"

if (-not (Test-Path -LiteralPath $engineFull)) {
  Bad "la cartella $engineFull non esiste (submodule non inizializzato? git submodule update --init)"
  exit 1
}

$versionFilePath = Join-Path $engineFull "ENGINE_VERSION"
if (Test-Path -LiteralPath $versionFilePath) {
  $fileVersion = (Get-Content -LiteralPath $versionFilePath -Raw).Trim()
  if ($fileVersion -eq $pinned) {
    Ok "ENGINE_VERSION: $fileVersion"
  } else {
    Bad "ENGINE_VERSION dice $fileVersion ma engine.properties dice $pinned"
  }
} else {
  Bad "manca ENGINE_VERSION in $engineFull"
}

$buildKt = Join-Path $engineFull "engine-foundation\src\main\kotlin\dev\antigravity\fluidengine\foundation\EngineVersion.kt"
if (Test-Path -LiteralPath $buildKt) {
  $match = Select-String -LiteralPath $buildKt -Pattern 'const val VERSION: String = "([^"]+)"'
  if ($match) {
    $constant = $match.Matches.Groups[1].Value
    if ($constant -eq $pinned) {
      Ok "EngineBuild.VERSION: $constant"
    } else {
      Bad "EngineBuild.VERSION dice ${constant}: il manifest remoto giudichera' questa app sulla versione sbagliata"
    }
  } else {
    Warn "non riesco a leggere EngineBuild.VERSION da EngineVersion.kt"
  }
} else {
  Bad "manca engine-foundation: $engineFull non sembra un Fluid Engine completo"
}

# In un submodule .git e' un file, non una cartella: Test-Path va bene per entrambi.
if (Test-Path -LiteralPath (Join-Path $engineFull ".git")) {
  Push-Location $engineFull
  try {
    $status = Invoke-Git @("status", "--porcelain")
    if ($status.Code -ne 0) {
      Warn "git non risponde in ${engineFull}: salto i controlli sul repo."
    } else {
      $dirty = $status.Lines | Where-Object { $_.Trim().Length -gt 0 }
      if ($dirty) {
        Warn "l'engine ha modifiche locali non committate:"
        $dirty | ForEach-Object { Write-Host "        $_" -ForegroundColor Yellow }
        Warn "finche' restano qui, questa app non usa l'engine condiviso ma una sua variante."
      } else {
        Ok "nessuna modifica locale"
      }

      $describe = Invoke-Git @("describe", "--tags", "--exact-match")
      if ($describe.Code -eq 0) {
        $tag = ($describe.Lines | Select-Object -First 1).Trim()
        if ($tag -eq "engine-$pinned") {
          Ok "agganciato al tag $tag"
        } else {
          Bad "agganciato a $tag, ma engine.properties dice $pinned"
        }
      } else {
        Warn "non e' agganciato a un tag (branch di lavoro?). Va bene mentre sviluppi, non per una release."
      }
    }
  } finally {
    Pop-Location
  }
} else {
  Warn "l'engine non e' un repo git: installazione in modalita' copia, aggiornabile solo ricopiando."
}

$settingsPath = Join-Path $appRootFull "settings.gradle"
if (-not (Test-Path -LiteralPath $settingsPath)) {
  $settingsPath = Join-Path $appRootFull "settings.gradle.kts"
}
if (Test-Path -LiteralPath $settingsPath) {
  $settings = Get-Content -LiteralPath $settingsPath -Raw
  if ($settings -match "fluid-engine \(inizio\)") {
    Ok "settings.gradle include i moduli dell'engine"
  } else {
    Bad "settings.gradle non include l'engine: esegui engine-install.ps1"
  }
} else {
  Bad "nessun settings.gradle in $appRootFull"
}

Write-Host ""
if ($problems -eq 0) {
  Write-Host "Tutto a posto." -ForegroundColor Green
  exit 0
} else {
  Write-Host "$problems problemi da sistemare." -ForegroundColor Red
  exit 1
}
