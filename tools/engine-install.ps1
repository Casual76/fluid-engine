<#
.SYNOPSIS
  Collega il Fluid Engine a un'app Android.

.DESCRIPTION
  Fa tre cose, e le dice tutte:
    1. inserisce nel settings.gradle dell'app il blocco che include i moduli dell'engine;
    2. scrive engine.properties con la versione agganciata;
    3. stampa le righe di dipendenza da incollare nei moduli che useranno l'engine.

  E' idempotente: rieseguirlo aggiorna il blocco esistente invece di aggiungerne un altro.

.PARAMETER AppRoot
  La cartella del progetto Gradle dell'app (quella con settings.gradle).

.PARAMETER EnginePath
  Dove sta l'engine, relativo ad AppRoot. Default: engine

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot .
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$AppRoot,
  [string]$EnginePath = "engine",
  [ValidateSet("stable", "beta")][string]$Channel = "stable"
)

$ErrorActionPreference = "Stop"

# Set-Content -Encoding utf8 in PowerShell 5.1 scrive un BOM, e un BOM in testa a un settings.gradle
# che non ce l'aveva e' una modifica gratuita a un file dell'app (che Groovy, a volte, non digerisce).
function Write-TextFile($path, $text) {
  $encoding = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($path, $text, $encoding)
}


$BeginMarker = "// --- fluid-engine (inizio) ---"
$EndMarker = "// --- fluid-engine (fine) ---"
$Modules = @(
  "engine-foundation",
  "engine-ui",
  "engine-storage",
  "engine-net",
  "engine-config",
  "engine-update",
  "engine-widget"
)

function Fail($message) {
  Write-Host "ERRORE: $message" -ForegroundColor Red
  exit 1
}

$appRootFull = (Resolve-Path -LiteralPath $AppRoot).Path
$engineFull = Join-Path $appRootFull $EnginePath
if (-not (Test-Path -LiteralPath $engineFull)) {
  Fail "l'engine non e' in $engineFull. Aggiungilo prima (git submodule add ... $EnginePath)."
}

$versionFile = Join-Path $engineFull "ENGINE_VERSION"
if (-not (Test-Path -LiteralPath $versionFile)) {
  Fail "$engineFull non sembra un Fluid Engine: manca ENGINE_VERSION."
}
$engineVersion = (Get-Content -LiteralPath $versionFile -Raw).Trim()

$settingsPath = Join-Path $appRootFull "settings.gradle"
$settingsKts = Join-Path $appRootFull "settings.gradle.kts"
$isKotlinDsl = $false
if (-not (Test-Path -LiteralPath $settingsPath)) {
  if (Test-Path -LiteralPath $settingsKts) {
    $settingsPath = $settingsKts
    $isKotlinDsl = $true
  } else {
    Fail "in $appRootFull non c'e' ne' settings.gradle ne' settings.gradle.kts."
  }
}

# Il blocco elenca i moduli per nome invece di scandire la cartella: cosi' il file dell'app dice
# esattamente cosa entra nel build, e aggiungere un modulo all'engine non lo cambia di nascosto.
if ($isKotlinDsl) {
  $moduleLines = ($Modules | ForEach-Object { "  `"$_`"" }) -join ",`r`n"
  $block = @"
$BeginMarker
val engineDir = file("$EnginePath")
if (engineDir.exists()) {
  listOf(
$moduleLines
  ).forEach { name ->
    include(":`$name")
    project(":`$name").projectDir = engineDir.resolve(name)
  }
}
$EndMarker
"@
} else {
  $moduleLines = ($Modules | ForEach-Object { "   '$_'" }) -join ",`r`n"
  $block = @"
$BeginMarker
def engineDir = file('$EnginePath')
if (engineDir.exists()) {
  [
$moduleLines
  ].each { name ->
    include ":`$name"
    project(":`$name").projectDir = new File(engineDir, name)
  }
}
$EndMarker
"@
}

$settings = Get-Content -LiteralPath $settingsPath -Raw
if ($settings -match [regex]::Escape($BeginMarker)) {
  $pattern = [regex]::Escape($BeginMarker) + "(?s).*?" + [regex]::Escape($EndMarker)
  $settings = [regex]::Replace($settings, $pattern, [System.Text.RegularExpressions.MatchEvaluator] { param($m) $block })
  Write-Host "settings.gradle: blocco engine aggiornato." -ForegroundColor Green
} else {
  $settings = $settings.TrimEnd() + "`r`n`r`n" + $block + "`r`n"
  Write-Host "settings.gradle: blocco engine aggiunto." -ForegroundColor Green
}
Write-TextFile $settingsPath $settings

$propertiesPath = Join-Path $appRootFull "engine.properties"
$today = Get-Date -Format "yyyy-MM-dd"
$properties = @"
# Quale Fluid Engine usa questa app. Aggiornato da engine-update.ps1, verificato da engine-doctor.ps1.
engine.version=$engineVersion
engine.channel=$Channel
engine.path=$EnginePath
engine.updatedAt=$today
"@
Write-TextFile $propertiesPath $properties
Write-Host "engine.properties: agganciato a $engineVersion (canale $Channel)." -ForegroundColor Green

Write-Host ""
Write-Host "Da incollare nel build.gradle dei moduli che useranno l'engine:" -ForegroundColor Cyan
Write-Host ""
Write-Host "  implementation project(':engine-ui')        // design system (porta con se' Compose e engine-foundation)"
Write-Host "  implementation project(':engine-storage')   // impostazioni su DataStore"
Write-Host "  implementation project(':engine-config')    // feature flag remoti"
Write-Host "  implementation project(':engine-update')    // aggiornamento in-app"
Write-Host "  implementation project(':engine-widget')    // widget Glance"
Write-Host ""
Write-Host "Poi: docs/01-integrazione.md per il tema e il collegamento della DI." -ForegroundColor Cyan
