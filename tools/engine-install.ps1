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

.PARAMETER Modules
  Quali moduli includere. Default: tutti. Le dipendenze fra moduli si aggiungono da sole, quindi
  chiedere engine-update porta con se' engine-net e engine-foundation.
  Serve alle app che non possono ospitarli tutti: senza il plugin Compose nel build root dell'app,
  engine-ui non riesce nemmeno a configurarsi, e un modulo che non configura ferma tutto il build.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot .

.EXAMPLE
  Un'app di sole View, che vuole solo l'aggiornamento in-app:

  powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot . -Modules engine-update
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$AppRoot,
  [string]$EnginePath = "engine",
  [ValidateSet("stable", "beta")][string]$Channel = "stable",
  [ValidateSet("submodule", "copy")][string]$Mode = "submodule",
  [string]$Source,
  # Quali moduli entrano nel build. Non e' un'ottimizzazione: un'app senza Compose non puo' nemmeno
  # *configurare* engine-ui, perche' quel modulo applica il plugin Compose che il build root dell'app
  # non dichiara. Includere tutto per default rendeva l'engine impossibile da ospitare per meta' delle
  # app. Le dipendenze fra moduli vengono aggiunte da sole: chiedere engine-update porta con se'
  # engine-net e engine-foundation.
  [string[]]$Modules
)

$ErrorActionPreference = "Stop"

# Set-Content -Encoding utf8 in PowerShell 5.1 scrive un BOM, e un BOM in testa a un settings.gradle
# che non ce l'aveva e' una modifica gratuita a un file dell'app (che Groovy, a volte, non digerisce).
# Get-Content -Raw in PowerShell 5.1 decodifica con la codepage ANSI del sistema, non UTF-8: leggere
# un file accentato e riscriverlo in UTF-8 lo corrompe un po' di piu' a ogni giro. E' successo
# davvero, al CHANGELOG, tagliando una release. Qui la decodifica e' esplicita.
function Read-TextFile($path) {
  return [System.IO.File]::ReadAllText($path, (New-Object System.Text.UTF8Encoding($false)))
}

function Write-TextFile($path, $text) {
  $encoding = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($path, $text, $encoding)
}


$BeginMarker = "// --- fluid-engine (inizio) ---"
$EndMarker = "// --- fluid-engine (fine) ---"
# L'ordine e' quello in cui i moduli entrano nel settings.gradle; le liste sono le dipendenze
# dirette, che lo script chiude da solo.
$EngineModules = [ordered]@{
  "engine-foundation" = @()
  "engine-ui"         = @("engine-foundation")
  "engine-storage"    = @("engine-foundation")
  "engine-net"        = @("engine-foundation")
  "engine-config"     = @("engine-foundation", "engine-net", "engine-storage")
  "engine-update"     = @("engine-foundation", "engine-net")
  "engine-widget"     = @("engine-foundation", "engine-ui")
  "engine-vault"      = @("engine-foundation")
  "engine-security"   = @("engine-foundation", "engine-ui", "engine-vault")
  "engine-ai"         = @("engine-foundation", "engine-vault")
  "engine-ai-bridge"  = @("engine-foundation", "engine-ai")
  "engine-ai-ui"      = @("engine-foundation", "engine-ui", "engine-ai")
  "engine-wear"       = @("engine-foundation", "engine-ui")
  "engine-richtext"   = @("engine-foundation", "engine-ui")
  "engine-viewer"     = @("engine-foundation", "engine-ui")
  "engine-sync"       = @("engine-foundation", "engine-storage")
  "engine-revisions"  = @("engine-foundation", "engine-ui")
  "engine-auth"       = @("engine-foundation")
  "engine-auth-google" = @("engine-foundation", "engine-auth")
  "engine-qr"         = @("engine-foundation", "engine-ui")
  "engine-share-in"   = @("engine-foundation", "engine-ui")
  "engine-notify"     = @("engine-foundation")
  "engine-account"    = @("engine-foundation", "engine-storage")
  "engine-account-host" = @("engine-foundation", "engine-storage", "engine-account", "engine-auth", "engine-vault", "engine-sync")
  "engine-account-ui" = @("engine-foundation", "engine-ui", "engine-storage", "engine-account")
  "engine-people"     = @("engine-foundation", "engine-storage", "engine-account", "engine-sync")
  "engine-push-fcm"   = @("engine-foundation")
  "engine-transfer"   = @("engine-foundation", "engine-net")
  "engine-files"      = @("engine-foundation", "engine-ui", "engine-viewer")
  "engine-media"      = @("engine-foundation", "engine-ui")
  "engine-audio"      = @("engine-foundation")
  "engine-playback"   = @("engine-foundation", "engine-ui")
  "engine-transcript" = @("engine-foundation", "engine-ui")
  "engine-ink"        = @("engine-foundation", "engine-ui")
  "engine-search"     = @("engine-foundation", "engine-ui", "engine-vault", "engine-ai", "engine-ai-bridge")
  "engine-search-semantic" = @("engine-foundation", "engine-ui", "engine-vault", "engine-ai", "engine-ai-bridge", "engine-search")
}

# Moduli che entrano solo se chiesti per nome. engine-wear porta con se' Material 3 per Wear, e
# un'app per telefono che lo ricevesse di default pagherebbe dipendenze e tempo di build per uno
# schermo che non ha. Un'app con una versione orologio lo chiede: -Modules ...,engine-wear.
# engine-ai-ui (l'interfaccia dell'assistente: aureola, card, impostazioni, kit della chat) e' lo
# stesso caso: un'app senza assistente non deve compilare un modulo che non usa, e chiederlo per
# nome porta con se' engine-ui, engine-ai e engine-foundation: -Modules ...,engine-ai-ui.
# engine-richtext (l'editor con formattazione: modello a intervalli, barra sopra la tastiera, lettori di
# HTML e Markdown) idem: lo chiede solo un'app che scrive testo formattato, e chiederlo per nome porta
# con se' engine-ui e engine-foundation: -Modules ...,engine-richtext.
# engine-viewer (i decodificatori dei visori di file: PDF, testo e colori di sintassi, GIF e WebP animate, il
# Player di Media3, il risolutore dal MIME al preset) idem: il telaio dei visori sta gia' in engine-ui e non
# decodifica niente; il modulo lo chiede solo un'app che apre file dal disco. Porta con se' engine-ui e
# engine-foundation, e non porta Media3: chi usa video e audio aggiunge il suo media3-exoplayer alla sua
# versione (il modulo lo compila come compileOnly): -Modules ...,engine-viewer.
# engine-sync (F8.21: il registro locale, l'invio una volta sola, l'unione, le pietre tombali; porta WorkManager) e
# engine-revisions (il foglio della scelta tra versioni) idem: li chiede solo un'app che sincronizza o che mostra
# versioni in conflitto, e sono indipendenti fra loro: -Modules ...,engine-sync,engine-revisions.
# engine-auth (F10.2: passkey e gestore delle password con Credential Manager, senza rete) idem: lo chiede solo chi
# entra con una passkey (Pampa Store). Porta engine-foundation e androidx.credentials, nient'altro.
# engine-auth-google (F10.2: "Continua con Google" e le concessioni Google, spente finche' non servono) idem, e in piu'
# porta le sole librerie Google non libere dell'engine (Play services, googleid): un'app che lo chiede lo fa sapendo
# che l'engine le permette solo col permesso della GPL-3.0 sezione 7 (LICENSES/Permesso-librerie-Google.md). Porta
# engine-auth ed engine-foundation: -Modules ...,engine-auth-google.
# engine-vault (F8.23, la cassaforte: valori, file e DataStore cifrati col Keystore) e engine-security (il blocco
# dell'app con l'impronta, lo schermo di vetro, il PIN dell'app) idem. engine-vault entra comunque con engine-ai, che
# cifra con lei le chiavi dei provider; engine-security porta con se' engine-ui e engine-vault:
# -Modules ...,engine-security.
# engine-qr (F8.10: il QR di "Mostra come QR", con ZXing core) e engine-share-in (F8.10: gli arrivi da altre app) idem:
# il foglio di condivisione sta gia' in engine-ui, e un'app che non vuole il QR o non riceve niente non li paga.
# engine-notify (F8.19: notifiche vere, Live Update, promemoria; nessuna libreria nuova) lo chiede un'app che avvisa fuori
# dall'app: -Modules ...,engine-notify. Le pagine e il ponte con la pillola sono gia' in engine-ui.
# engine-account, engine-account-host ed engine-account-ui (F10.4: l'Account Pampa) idem: engine-account per un'app che usa
# l'account (porta Bouncy Castle), engine-account-ui per la riga nelle impostazioni, engine-account-host solo per Pampa
# Store: -Modules ...,engine-account-ui. Con engine-account (F10.8) EngineSettingsStore prende da solo il tema comune
# di Pampa Store (ServiceLoader), e l'icona segue la famiglia comune se l'app ha gli activity-alias col meta-data
# dev.pampa.account.ICON_SET. engine-account-host porta anche engine-sync (i luoghi fra i dispositivi).
# Store: -Modules ...,engine-account-ui.
# engine-people (F10.6: le persone, la crittografia di Codex, la casella, gli spazi; porta Bouncy Castle ed engine-sync) lo
# chiedono solo Pampa Store e Pampa Mail; engine-push-fcm (F10.6: il campanello, Firebase Messaging, permesso GPL-3.0 par. 7)
# solo Pampa Store: -Modules ...,engine-people,engine-push-fcm.
# engine-transfer (F8.20: la coda persistita degli scaricamenti e dei caricamenti lunghi; porta WorkManager e dichiara
# il servizio in primo piano di tipo dataSync) idem: lo chiede solo un'app che trasferisce file grandi
# (-Modules ...,engine-transfer); la pagina "Trasferimenti" e la pillola stanno gia' in engine-ui.
# engine-files (F8.18: lo sfoglia-file, sopra engine-viewer) idem: lo chiede un'app che fa sfogliare o scegliere file
# (-Modules ...,engine-files). Nessun permesso: le cartelle arrivano dal selettore di sistema.
$OptInModules = @("engine-wear", "engine-ai-ui", "engine-richtext", "engine-viewer", "engine-sync", "engine-revisions", "engine-auth", "engine-auth-google", "engine-vault", "engine-security", "engine-qr", "engine-share-in", "engine-notify", "engine-account", "engine-account-host", "engine-account-ui", "engine-people", "engine-push-fcm", "engine-transfer", "engine-files", "engine-media", "engine-audio", "engine-playback", "engine-transcript", "engine-ink", "engine-search", "engine-search-semantic")
# engine-media (F8.14: il selettore di foto e video, il contratto delle sorgenti fra app, il pannello degli allegati;
# nessuna libreria nuova e nessun permesso dichiarato) idem: -Modules ...,engine-media. I permessi delle foto li scrive
# l'app nel suo manifest solo se usa la griglia del telefono (moduli/media.md della skill).
# engine-audio (F8.13: la cattura del registratore, pausa e ripresa, AAC o WAV, l'onda dal vivo, il taglio dei silenzi;
# nessuna libreria, dichiara RECORD_AUDIO), engine-playback (F8.13: la riproduzione di sistema con Media3, che porta
# l'app: notifica, schermata di blocco, cuffie, timer) ed engine-transcript (F8.13: l'ascolto dal paragrafo, i tempi per
# parola e il salto) idem: -Modules ...,engine-audio,engine-playback,engine-transcript. Il tasto rosso, il pannello e
# l'onda stanno gia' in engine-ui.
# engine-media (F8.14: il selettore di foto e video, il contratto delle sorgenti fra app, il pannello degli allegati;
# nessuna libreria nuova e nessun permesso dichiarato) idem: -Modules ...,engine-media. I permessi delle foto li scrive
# l'app nel suo manifest solo se usa la griglia del telefono (moduli/media.md della skill).
# engine-ink (F8.16: il disegno e le annotazioni, sopra androidx.ink, Apache-2.0; porta la libreria nativa libink.so per
# le ABI di Android, circa 1,3 MB per ABI) idem: lo chiede solo un'app che disegna o annota (-Modules ...,engine-ink).
# Nessun permesso. I crediti: FluidInkLicenses in fluidLicensesSection.
# engine-media (F8.14: il selettore di foto e video, il contratto delle sorgenti fra app, il pannello degli allegati;
# nessuna libreria nuova e nessun permesso dichiarato) idem: -Modules ...,engine-media. I permessi delle foto li scrive
# l'app nel suo manifest solo se usa la griglia del telefono (moduli/media.md della skill).
# engine-search (F8.22: l'indice sul telefono in SQLite FTS4, la ricerca con gli errori di battitura e i pezzi
# evidenziati, "Cerca in Pampa" col ponte firmato; porta engine-ui, engine-ai-bridge, engine-vault e WorkManager) idem:
# -Modules ...,engine-search. engine-search-semantic (il modello del significato con LiteRT-LM: 22 MB di libreria
# nativa in arm64-v8a; il modello di 157 MiB lo porta l'app) si chiede a parte: -Modules ...,engine-search-semantic.

# F6C.8: i moduli di famiglia dei visori (Office, archivi, testi ricchi e dati, immagini speciali e font, 3D) e il
# visore universale engine-viewer-all. La lista, con il peso e le licenze, e' quella dell'engine stesso
# (engine-viewer/families.json, la stessa che controlla la build con checkViewerFamilies): qui non si ricopia.
# Ogni famiglia porta con se' engine-viewer; engine-viewer-all porta tutte le famiglie che esistono in questo
# engine. Sono tutte opt-in: un'app che non apre un tipo non ne paga il peso, e il tipo resta sconosciuto con
# "Apri con...". Una famiglia che l'engine non ha ancora (le scrivono F6C.11-F6C.14) si elenca, ma non si chiede.
$ViewerFamilies = @()
$familiesManifest = Join-Path $PSScriptRoot "..\engine-viewer\families.json"
if (Test-Path -LiteralPath $familiesManifest) {
  $parsedFamilies = (Read-TextFile $familiesManifest) | ConvertFrom-Json
  foreach ($family in $parsedFamilies.families) {
    if ($family.test -eq $true) { continue }
    $licenses = @(@($family.libraries | ForEach-Object { $_.license }) + @($family.assets | ForEach-Object { $_.license }) |
      Where-Object { $_ } | Select-Object -Unique) -join "; "
    $ViewerFamilies += [pscustomobject]@{
      Module   = [string]$family.module
      Label    = [string]$family.label
      Weight   = [string]$family.weight
      Licenses = $(if ($licenses) { $licenses } else { "nessuna libreria" })
      Requires = @($family.requires | Where-Object { $_ })
      Present  = (Test-Path -LiteralPath (Join-Path $PSScriptRoot "..\$($family.module)"))
    }
  }
}
$PresentViewerFamilies = @($ViewerFamilies | Where-Object { $_.Present } | ForEach-Object { $_.Module })
foreach ($family in ($ViewerFamilies | Where-Object { $_.Present })) {
  $EngineModules[$family.Module] = @("engine-foundation", "engine-ui", "engine-viewer") + $family.Requires
  $OptInModules += $family.Module
}
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot "..\engine-viewer-all")) {
  $EngineModules["engine-viewer-all"] = @("engine-foundation", "engine-ui", "engine-viewer") + $PresentViewerFamilies
  $OptInModules += "engine-viewer-all"
}

function Fail($message) {
  Write-Host "ERRORE: $message" -ForegroundColor Red
  exit 1
}

# Chiude la selezione sulle dipendenze: un modulo incluso il cui `api project(...)` punta a un
# progetto assente non e' un errore di runtime, e' un settings.gradle che non configura.
function Resolve-Modules {
  param([string[]]$Requested)
  $selected = New-Object System.Collections.Generic.HashSet[string]
  $queue = New-Object System.Collections.Queue
  foreach ($name in $Requested) { $queue.Enqueue($name) }
  while ($queue.Count -gt 0) {
    $name = $queue.Dequeue()
    if (-not $EngineModules.Contains($name)) {
      if (@($ViewerFamilies | Where-Object { $_.Module -eq $name }).Count -gt 0) {
        Fail "il modulo di famiglia $name non c'e' ancora in questo engine (engine-viewer/families.json lo annuncia). Quelli presenti: $(if ($PresentViewerFamilies.Count -gt 0) { $PresentViewerFamilies -join ', ' } else { 'nessuno' })"
      }
      Fail "modulo sconosciuto: $name. Quelli veri sono: $($EngineModules.Keys -join ', ')"
    }
    if (-not $selected.Add($name)) { continue }
    foreach ($dependency in $EngineModules[$name]) { $queue.Enqueue($dependency) }
  }
  # Riordinati come in $EngineModules, cosi' il blocco nel settings.gradle e' stabile fra due
  # esecuzioni e un rilancio non produce un diff fatto di sole righe spostate.
  return @($EngineModules.Keys | Where-Object { $selected.Contains($_) })
}

if (-not $Modules -or $Modules.Count -eq 0) {
  $Modules = @($EngineModules.Keys | Where-Object { $OptInModules -notcontains $_ })
}
$Modules = Resolve-Modules -Requested $Modules

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
  # robocopy invece di Copy-Item perche' /XD esclude le cartelle a *ogni* profondita': un filtro sui
  # soli figli di primo livello si porterebbe dietro engine-ui/build, che sono centinaia di MB di
  # prodotti di compilazione e percorsi abbastanza lunghi da far fallire la copia stessa.
  $excluded = @("build", ".gradle", ".kotlin", ".git", ".idea")
  $robocopyArgs = @($sourceFull, $engineFull, "/MIR", "/NFL", "/NDL", "/NJH", "/NJS", "/NP",
    "/XF", "local.properties", "/XD") + $excluded
  & robocopy @robocopyArgs | Out-Null
  # robocopy usa i codici di uscita come bitmask: sotto 8 ha copiato, da 8 in su e' un errore.
  if ($LASTEXITCODE -ge 8) { Fail "copia non riuscita (robocopy $LASTEXITCODE)." }
  $global:LASTEXITCODE = 0
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
$engineVersion = (Read-TextFile $versionFile).Trim()

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

$settings = Read-TextFile $settingsPath
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
engine.modules=$($Modules -join ",")
engine.updatedAt=$today
"@
Write-TextFile $propertiesPath $properties
Write-Host "engine.properties: agganciato a $engineVersion (canale $Channel)." -ForegroundColor Green
Write-Host "moduli inclusi: $($Modules -join ', ')" -ForegroundColor Green
$excludedModules = @($EngineModules.Keys | Where-Object { $Modules -notcontains $_ })
if ($excludedModules.Count -gt 0) {
  Write-Host "moduli esclusi: $($excludedModules -join ', ')" -ForegroundColor DarkGray
}

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
  @("engine-widget", "widget Glance"),
  @("engine-vault", "cassaforte: valori, file e DataStore cifrati col Keystore, sezioni all'impronta (opt-in; entra con engine-ai)"),
  @("engine-security", "blocco dell'app con l'impronta, contenuto protetto, PIN dell'app (opt-in)"),
  @("engine-ai", "assistente IA: provider, chiavi, orchestratore"),
  @("engine-ai-bridge", "tool federati: esporre i tool a un assistente esterno, o chiamarli"),
  @("engine-ai-ui", "interfaccia dell'assistente: aureola, card, impostazioni, kit della chat (opt-in)"),
  @("engine-wear", "design system su Wear OS (solo nel modulo dell'orologio)"),
  @("engine-richtext", "editor con formattazione: grassetto, elenchi, link, HTML/Markdown (opt-in)"),
  @("engine-viewer", "decodificatori dei visori: PDF, testo, GIF/WebP animate, Media3 (opt-in)"),
  @("engine-sync", "sincronizzazione: registro locale, invio una volta sola, unione, verso il relay dell'account (opt-in)"),
  @("engine-revisions", "il foglio della scelta tra versioni in conflitto (opt-in)"),
  @("engine-auth", "passkey e gestore delle password, senza rete; il PRF lo scrive il modulo (opt-in)"),
  @("engine-auth-google", "Continua con Google e concessioni Google spente; librerie Google non libere (opt-in)"),
  @("engine-qr", "il QR di 'Mostra come QR' nel foglio di condivisione, ZXing core (opt-in)"),
  @("engine-share-in", "arrivi da altre app: lettura, copia con tetto, foglio sopra l'app di partenza (opt-in)"),
  @("engine-notify", "notifiche vere, Live Update di Android 16, promemoria che arrivano davvero (opt-in)"),
  @("engine-account", "l'Account Pampa visto da un'app, attraverso Pampa Store e mai la rete (opt-in)"),
  @("engine-account-host", "il motore dell'account dentro Pampa Store: custodia delle chiavi, sessione (opt-in, solo lo Store)"),
  @("engine-account-ui", "la riga Account Pampa, l'avatar e i componenti delle persone (opt-in)"),
  @("engine-people", "le persone: crittografia di Codex, persona, casella, coda in uscita, spazi (opt-in, Store e Pampa Mail)"),
  @("engine-push-fcm", "il campanello di FCM, spento fino all'accesso (opt-in, solo lo Store)"),
  @("engine-transfer", "centro trasferimenti: coda persistita, pausa e ripresa con Range, WorkManager e dataSync (opt-in)"),
  @("engine-files", "sfoglia-file: posizioni, cartelle in griglia o elenco, ricerca, recenti, apertura nei visori (opt-in)"),
  @("engine-media", "foto e allegati: selettore di foto e video, sorgenti di altre app (MyCloud Gallery), pannello del + (opt-in)"),
  @("engine-audio", "registratore: microfono, pausa e ripresa, AAC o WAV, onda dal vivo, taglio dei silenzi (opt-in)"),
  @("engine-playback", "riproduzione di sistema con Media3: notifica, schermata di blocco, cuffie, timer (opt-in)"),
  @("engine-transcript", "ascolto dal paragrafo: tempi per parola e paragrafo, salto, la pagina che segue la voce (opt-in)"),
  @("engine-ink", "disegno e annotazioni: foglio, tavolozza di vetro, penna dello stilo, forme, foto e PDF annotati (opt-in)"),
  @("engine-search", "ricerca: indice sul telefono (FTS4, errori di battitura, pezzi evidenziati, cassaforte), Cerca in Pampa col ponte (opt-in)"),
  @("engine-search-semantic", "ricerca per significato: EmbeddingGemma 2 con LiteRT-LM, il modello lo porta l'app (opt-in; peso: 22 MB nativi arm64)")
)
foreach ($family in $ViewerFamilies) {
  if ($family.Present) { $notes += , @($family.Module, "$($family.Label) (opt-in; peso: $($family.Weight))") }
}
$notes += , @("engine-viewer-all", "il visore universale: engine-viewer e tutte le famiglie di questo engine (opt-in; peso: la somma)")
foreach ($note in $notes) {
  $module = $note[0]
  $comment = $note[1]
  if ($Modules -notcontains $module) { continue }
  if ($isKotlinDsl) {
    $line = "  implementation(project(`":$module`"))"
  } else {
    $line = "  implementation project(':$module')"
  }
  Write-Host ("{0,-46}// {1}" -f $line, $comment)
}
if ($Modules -contains "engine-viewer") {
  # Media3 non arriva col modulo (compileOnly): senza, video e audio vanno allo sconosciuto con "Apri con...".
  if ($isKotlinDsl) {
    $media3 = "  implementation(`"androidx.media3:media3-exoplayer:<la versione dell'app>`")"
  } else {
    $media3 = "  implementation `"androidx.media3:media3-exoplayer:<la versione dell'app>`""
  }
  Write-Host ("{0,-46}// {1}" -f $media3, "solo per video e audio nel visore (engine-viewer)")
}
if ($Modules -contains "engine-playback") {
  # F8.13: come engine-viewer, Media3 e' dell'app (compileOnly, almeno la 1.5.1): senza session ed exoplayer il servizio
  # di riproduzione non parte.
  foreach ($artifact in @("media3-session", "media3-exoplayer")) {
    if ($isKotlinDsl) {
      $media3 = "  implementation(`"androidx.media3:${artifact}:<la versione dell'app>`")"
    } else {
      $media3 = "  implementation `"androidx.media3:${artifact}:<la versione dell'app>`""
    }
    Write-Host ("{0,-46}// {1}" -f $media3, "per engine-playback (la stessa versione per tutti e due)")
  }
}
if ($Modules -contains "engine-viewer" -and $ViewerFamilies.Count -gt 0) {
  # F6C.8: le famiglie dei visori, una per tipo di file. Si includono con -Modules; chi vuole il visore universale
  # (come Universal Converter) chiede engine-viewer-all, che le porta tutte.
  Write-Host ""
  Write-Host "Visori, i moduli di famiglia (opt-in; tutti insieme: -Modules ...,engine-viewer-all):" -ForegroundColor Cyan
  foreach ($family in $ViewerFamilies) {
    if ($Modules -contains $family.Module) {
      $state = "incluso"
    } elseif ($family.Present) {
      $state = "da includere con -Modules ...,$($family.Module)"
    } else {
      $state = "non ancora in questo engine"
    }
    if ($isKotlinDsl) {
      $line = "  implementation(project(`":$($family.Module)`"))"
    } else {
      $line = "  implementation project(':$($family.Module)')"
    }
    Write-Host ("  {0,-24}{1}" -f $family.Module, $family.Label)
    Write-Host ("  {0,-24}peso: {1}; licenze: {2}" -f "", $family.Weight, $family.Licenses) -ForegroundColor DarkGray
    Write-Host ("  {0,-24}{1}" -f "", $state) -ForegroundColor DarkGray
    if ($family.Present) { Write-Host ("  {0,-24}riga: {1}" -f "", $line.Trim()) -ForegroundColor DarkGray }
  }
}
Write-Host ""
Write-Host "Poi: docs/01-integrazione.md per il tema e il collegamento della DI." -ForegroundColor Cyan

# Esplicito: senza, il codice di uscita dello script resta quello dell'ultimo comando nativo
# eseguito (robocopy ne usa uno diverso da zero anche quando ha copiato tutto), e chi automatizza
# l'installazione lo leggerebbe come un fallimento.
exit 0
