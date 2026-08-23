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
  [ValidateSet("stable", "beta")][string]$Channel = "stable",
  [ValidateSet("submodule", "copy")][string]$Mode = "submodule",
  [string]$Source
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

if ($Mode -eq "copy") {
  if ([string]::IsNullOrWhiteSpace($Source)) { Fail "-Mode copy vuole anche -Source." }
  $sourceFull = (Resolve-Path -LiteralPath $Source).Path
  if (-not (Test-Path -LiteralPath (Join-Path $sourceFull "ENGINE_VERSION"))) {
    Fail "$sourceFull non sembra un Fluid Engine: manca ENGINE_VERSION."
  }
  Write-Host "Copio l'engine da $sourceFull ..." -ForegroundColor Cyan
  if (Test-Path -LiteralPath $engineFull) { Remove-Item -Recurse -Force $engineFull }
  New-Item -ItemType Directory -Path $engineFull | Out-Null
  # .git, build e .gradle restano fuori: la copia e' una copia dei sorgenti, non del repo ne' dei
  # suoi prodotti di compilazione.
  Get-ChildItem -LiteralPath $sourceFull -Force |
    Where-Object { $_.Name -notin @(".git", "build", ".gradle", ".kotlin", "local.properties") } |
    ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $engineFull -Recurse -Force }
} elseif (-not (Test-Path -LiteralPath $engineFull)) {
  Fail @"
l'engine non e' in $engineFull. Aggiungilo prima:

  git submodule add <url> $EnginePath

Se il repo dell'engine e' ancora una cartella locale, git rifiuta il trasporto 'file' e serve:

  git -c protocol.file.allow=always submodule add "C:\percorsoluid-engine" $EnginePath

In alternativa, senza git: -Mode copy -Source <cartella dell'engine>
"@
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
$buildFileName = if ($isKotlinDsl) { "build.gradle.kts" } else { "build.gradle" }
Write-Host "Da incollare nel $buildFileName dei moduli che useranno l'engine:" -ForegroundColor Cyan
Write-Host ""
# La sintassi segue il DSL dell'app: nessuno vuole tradurre a mano cinque righe che lo script
# conosce gia'.
$notes = @(
  @("engine-ui", "design system (porta con se' Compose e engine-foundation)"),
  @("engine-storage", "impostazioni su DataStore"),
  @("engine-config", "feature flag remoti"),
  @("engine-update", "aggiornamento in-app"),
  @("engine-widget", "widget Glance")
)
foreach ($note in $notes) {
  $module = $note[0]
  $comment = $note[1]
  if ($isKotlinDsl) {
    $line = "  implementation(project(`":$module`"))"
  } else {
    $line = "  implementation project(':$module')"
  }
  Write-Host ("{0,-46}// {1}" -f $line, $comment)
}
Write-Host ""
Write-Host "Poi: docs/01-integrazione.md per il tema e il collegamento della DI." -ForegroundColor Cyan
