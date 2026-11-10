#!/usr/bin/env bash
# Le prove del contenitore web (FluidWebContent) su un emulatore: socket, DNS, DOM vero di Chromium, processi renderer.
#
# Perche' esiste: queste prove non girano nella suite JVM. Lo script le lancia tutte, un processo nuovo per lancio, e
# raccoglie esiti e log. Fino a K1 le prove a ciclo andavano divise in lanci brevi (`-e webChunk i/n`), perche' ogni
# documento lasciava vivo un processo renderer e una trentina esaurivano un AVD da 2 GB: dalla 3.1.1 il contenitore
# termina il renderer alla chiusura, e censimento e varianti girano ciascuno in un lancio solo. L'argomento `webChunk`
# resta nelle prove per chi volesse dividerle a mano su un dispositivo ancora piu' piccolo.
#
# Uso (da Git Bash, dalla radice dell'engine, con UN emulatore acceso):
#   tools/qa/web-network-tests.sh                 installa i due APK di debug e lancia tutto
#   tools/qa/web-network-tests.sh --no-install    usa quelli gia' sull'emulatore
#   tools/qa/web-network-tests.sh --uninstall     alla fine disinstalla i due pacchetti
#   tools/qa/web-network-tests.sh -o cartella     dove mettere gli esiti (di serie artifacts/web-network/<ora>)
#   tools/qa/web-network-tests.sh -a app.apk -t test.apk   APK diversi da quelli appena compilati
#
# Compilare prima:  ./gradlew.bat --no-daemon --no-watch-fs :sample:assembleDebug :sample:assembleDebugAndroidTest
# (dentro il semaforo del PC: gradle-slot.ps1, se girano altri agenti.)
#
# Cosa lancia (11 lanci):
#   - FluidWebContentInstrumentedTest            le nove prove storiche (la prima conta i socket del corpus ostile)
#   - FluidWebContentNetworkCensusTest           il censimento dei canali
#   - FluidWebContentSealInstrumentedTest        varianti travestite, poi sentinelle, DNS, DOM, SVG, foglio di stile
#   - FluidWebContentRendererLifecycleTest       60 documenti in un processo e chiusure rapide (processi renderer),
#                                                poi gli avanzi dei profili al processo successivo
# Nella cartella degli esiti restano l'uscita di ogni lancio (`*.txt`) e le righe di log `FluidWebCensus`,
# `FluidWebSeal` e `FluidWebLifecycle` (`*.logcat.txt`): per ogni canale, quante connessioni native e che cosa e'
# arrivato a `onRequest`; per i documenti, quanti renderer vivi e le misure di memoria, thread, descrittori e disco.
#
# Esce 0 se ogni lancio finisce con «OK», 1 altrimenti. Se la memoria libera dell'emulatore e' sotto i 400 MB lo dice
# prima di cominciare: un «Timeout» del contenitore in quelle condizioni non e' un difetto del contenitore.

set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=qa-common.sh
source "$SCRIPT_DIR/qa-common.sh"
[ -x "$ADB" ] || ADB="adb"

PACKAGE="dev.antigravity.fluidglass.debug"
RUNNER="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
TESTS="dev.antigravity.fluidengine.sample"
APP="$ROOT/sample/build/outputs/apk/debug/sample-debug.apk"
TEST_APK="$ROOT/sample/build/outputs/apk/androidTest/debug/sample-debug-androidTest.apk"
OUT=""
INSTALL=1
UNINSTALL=0

while [ $# -gt 0 ]; do
  case "$1" in
    -o) OUT="$2"; shift 2 ;;
    -a) APP="$2"; shift 2 ;;
    -t) TEST_APK="$2"; shift 2 ;;
    --no-install) INSTALL=0; shift ;;
    --uninstall) UNINSTALL=1; shift ;;
    -h|--help) sed -n '2,31p' "$0"; exit 0 ;;
    *) echo "Opzione sconosciuta: $1" >&2; exit 2 ;;
  esac
done

[ -n "$OUT" ] || OUT="$ROOT/artifacts/web-network/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

if [ "$INSTALL" = 1 ]; then
  "$ADB" install -r -t "$(qa_winpath "$APP")" >/dev/null || { echo "Installazione dell'app fallita: $APP" >&2; exit 2; }
  "$ADB" install -r -t "$(qa_winpath "$TEST_APK")" >/dev/null || { echo "Installazione delle prove fallita: $TEST_APK" >&2; exit 2; }
fi

FREE_KB="$("$ADB" shell grep MemAvailable /proc/meminfo | tr -dc '0-9')"
echo "Provider: $("$ADB" shell dumpsys webviewupdate | grep -m1 'Current WebView package' | tr -d '\r' | sed 's/^ *//')"
echo "Memoria libera dell'emulatore: $((FREE_KB / 1024)) MB"
[ "$FREE_KB" -ge 409600 ] || echo "ATTENZIONE: meno di 400 MB liberi, il contenitore puo' finire in Timeout senza colpa."

FAILED=0
TOTAL=0

# launch ETICHETTA CLASSE[#metodo,...] [argomenti di am instrument...]
launch() {
  local label="$1" class="$2"
  shift 2
  "$ADB" shell am force-stop "$PACKAGE" >/dev/null 2>&1
  "$ADB" logcat -c >/dev/null 2>&1
  "$ADB" shell am instrument -w -r -e class "$class" "$@" "$RUNNER" 2>&1 | tr -d '\r' > "$OUT/$label.txt"
  "$ADB" logcat -d -v time -s FluidWebCensus:I FluidWebSeal:I FluidWebLifecycle:I 2>/dev/null | tr -d '\r' > "$OUT/$label.logcat.txt"
  local verdict
  verdict="$(grep -a -E '^OK \(|^Tests run:|Process crashed|INSTRUMENTATION_FAILED' "$OUT/$label.txt" | head -n 1)"
  TOTAL=$((TOTAL + 1))
  case "$verdict" in
    "OK ("*) echo "  ok       $label  $verdict" ;;
    *) FAILED=$((FAILED + 1)); echo "  FALLITO  $label  ${verdict:-nessun esito (processo interrotto?)}"
       grep -a -E '^Error in|AssertionError' "$OUT/$label.txt" | awk '!seen[$0]++' | cut -c1-400 | sed 's/^/           /' ;;
  esac
}

SEAL="$TESTS.FluidWebContentSealInstrumentedTest"
LIFE="$TESTS.FluidWebContentRendererLifecycleTest"
echo "Prove storiche"
launch storiche "$TESTS.FluidWebContentInstrumentedTest"
echo "Censimento dei canali"
launch censimento "$TESTS.FluidWebContentNetworkCensusTest"
echo "Varianti travestite"
launch varianti "$SEAL#disguisedHintsOpenNoNativeSocket"
echo "Sentinelle, DNS, DOM di Chromium, SVG, foglio di stile"
launch sentinelle "$SEAL#sentinelUnsealedPreconnectOpensNativeSockets,$SEAL#sentinelUnsealedDnsPrefetchIsCountedBySystem"
launch misura-senza-sigillo "$SEAL#unsealedDisguisesAreMeasuredOnThisProvider,$SEAL#unsealedNameResolutionIsMeasuredOnThisProvider"
launch dns-e-foglio "$SEAL#documentCannotCauseNameResolution,$SEAL#stylesheetServedByTheAppStillApplies"
launch dom "$SEAL#chromiumFindsNoHintElementInSealedDocuments,$SEAL#chromiumCreatesTheElementOnlyFromTheLiteralName,$SEAL#sealedDocumentKeepsTheTreeShapeAndItsUsefulHead,$SEAL#inlineSvgKeepsItsShapesAfterMetadata,$SEAL#inlineSvgWithMetadataIsStillDrawnByTheContainer"
echo "Ciclo di vita: processi renderer e profili"
launch renderer-60-documenti "$LIFE#manyDocumentsInOneProcessLeaveNoRendererBehind"
# Gli avanzi si provano nel processo successivo a uno che ha aperto molti documenti: l'ordine dei due lanci conta.
launch renderer-avanzi "$LIFE#leftoverProfilesArePrunedWithoutHoldingTheFirstDocument"
launch renderer-chiusure-rapide "$LIFE#documentsClosedWhileLoadingLeaveNoRendererBehind"
launch renderer-avanzi-tutti-insieme "$LIFE#leftoverProfilesAtProcessStartAreMeasured"

"$ADB" shell am force-stop "$PACKAGE" >/dev/null 2>&1
if [ "$UNINSTALL" = 1 ]; then
  "$ADB" uninstall "$PACKAGE.test" >/dev/null 2>&1
  "$ADB" uninstall "$PACKAGE" >/dev/null 2>&1
fi

echo "Esiti in $OUT"
if [ "$FAILED" -gt 0 ]; then
  echo "$FAILED lanci su $TOTAL falliti."
  exit 1
fi
echo "Tutti i $TOTAL lanci sono passati."
