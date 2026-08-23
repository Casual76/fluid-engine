<#
.SYNOPSIS
  Taglia una versione del Fluid Engine.

.DESCRIPTION
  Da eseguire dentro il repo dell'engine. Muove insieme le tre cose che devono restare d'accordo:
  il file ENGINE_VERSION, la costante EngineBuild.VERSION e il tag git. Sono la stessa versione
  scritta in tre posti perche' servono a tre lettori diversi (gli script, l'app in esecuzione, git),
  e sono la cosa che diverge per prima se le si tocca a mano.

.PARAMETER Version
  La nuova versione, per esempio 1.1.0.

.PARAMETER Notes
  Le voci del changelog, una per riga. Se manca, apre una sezione vuota da riempire.

.PARAMETER Tag
  Crea anche il tag git engine-<Version>. Senza, prepara soltanto i file.

.EXAMPLE
  Con una voce sola, -File va bene:
  powershell -ExecutionPolicy Bypass -File tools\engine-release.ps1 -Version 1.1.0 -Notes "FluidStepper" -Tag

.EXAMPLE
  Con piu' voci serve -Command: passando per -File, PowerShell non sa costruire un array.
  powershell -ExecutionPolicy Bypass -Command "& .	ools\engine-release.ps1 -Version 1.1.0 -Notes 'FluidStepper','BREAKING: FluidCard non accetta piu elevation' -Tag"
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Version,
  [string[]]$Notes = @(),
  [switch]$Tag
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

# git parla su stderr anche quando va tutto bene, e con ErrorActionPreference a Stop PowerShell 5.1
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

if ($Version -notmatch "^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$") {
  Fail "'$Version' non e' una versione semantica (1.2.3 oppure 1.2.3-beta1)."
}

$engineRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$versionFile = Join-Path $engineRoot "ENGINE_VERSION"
$changelog = Join-Path $engineRoot "CHANGELOG.md"
$versionKt = Join-Path $engineRoot "engine-foundation\src\main\kotlin\dev\antigravity\fluidengine\foundation\EngineVersion.kt"

foreach ($required in @($versionFile, $changelog, $versionKt)) {
  if (-not (Test-Path -LiteralPath $required)) { Fail "manca $required" }
}

$previous = (Get-Content -LiteralPath $versionFile -Raw).Trim()
if ($previous -eq $Version) { Fail "la versione e' gia' $Version." }

$isGit = Test-Path -LiteralPath (Join-Path $engineRoot ".git")
if ($Tag -and -not $isGit) { Fail "$engineRoot non e' un repo git: non posso taggare." }
if ($Tag) {
  Push-Location $engineRoot
  try {
    $status = Invoke-Git @("status", "--porcelain")
    $dirty = $status.Lines | Where-Object { $_.Trim().Length -gt 0 }
    if ($dirty) {
      Write-Host "Ci sono modifiche non committate:" -ForegroundColor Yellow
      $dirty | ForEach-Object { Write-Host "  $_" }
      Fail "committale prima di taggare, altrimenti il tag non contiene quello che pensi."
    }
  } finally {
    Pop-Location
  }
}

Write-TextFile $versionFile $Version

$kotlin = Get-Content -LiteralPath $versionKt -Raw
$updatedKotlin = [regex]::Replace(
  $kotlin,
  'const val VERSION: String = "[^"]*"',
  "const val VERSION: String = `"$Version`""
)
if ($updatedKotlin -eq $kotlin) { Fail "non ho trovato EngineBuild.VERSION in EngineVersion.kt" }
Write-TextFile $versionKt $updatedKotlin

$today = Get-Date -Format "yyyy-MM-dd"
if ($Notes.Count -gt 0) {
  $entries = ($Notes | ForEach-Object { "- $_" }) -join "`r`n"
} else {
  $entries = "- (da scrivere)"
}
$section = "## $Version - $today`r`n`r`n$entries`r`n"

$existing = Get-Content -LiteralPath $changelog -Raw
$marker = "<!-- nuove versioni qui sopra -->"
if ($existing -match [regex]::Escape($marker)) {
  $existing = $existing -replace [regex]::Escape($marker), "$section`r`n$marker"
} else {
  $existing = $existing.TrimEnd() + "`r`n`r`n" + $section
}
Write-TextFile $changelog $existing

Write-Host "ENGINE_VERSION, EngineBuild.VERSION e CHANGELOG portati a $Version (da $previous)." -ForegroundColor Green

if ($Tag) {
  Push-Location $engineRoot
  try {
    $add = Invoke-Git @("add", "ENGINE_VERSION", "CHANGELOG.md", $versionKt)
    if ($add.Code -ne 0) { Fail "git add non riuscito." }
    $commit = Invoke-Git @("commit", "-m", "release: engine $Version")
    if ($commit.Code -ne 0) { Fail "commit non riuscito: $($commit.Lines -join ' ')" }
    $tagResult = Invoke-Git @("tag", "engine-$Version")
    if ($tagResult.Code -ne 0) { Fail "tag non creato: $($tagResult.Lines -join ' ')" }
    Write-Host "Commit e tag engine-$Version creati." -ForegroundColor Green
    Write-Host "Da spingere: git push ; git push --tags" -ForegroundColor Cyan
  } finally {
    Pop-Location
  }
} else {
  Write-Host "File pronti. Rileggi il changelog, poi committa e tagga (o rilancia con -Tag)." -ForegroundColor Cyan
}
