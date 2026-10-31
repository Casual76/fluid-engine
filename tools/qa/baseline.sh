#!/usr/bin/env bash
# Baseline animazioni Fluid Glass su emulatore Medium_Phone (API 37, 1080x2400).
# Uso: bash tools/qa/baseline.sh [CARTELLA_OUTPUT]
#   SKIP_EMU=1  non avvia l'emulatore (gia' acceso con l'app installata)
cd "$(dirname "$0")/../.." || exit 1
source tools/qa/qa-common.sh

OUT="${1:-$PWD/artifacts/baseline-2.13.1}"
APK="$PWD/sample/build/outputs/apk/debug/sample-debug.apk"
PKG="dev.antigravity.fluidglass.debug"
ACT="dev.antigravity.fluidengine.sample.GalleryActivity"
mkdir -p "$OUT"

# ---- Coordinate (pixel su 1080x2400) ----
TAB_MATERIALE="146 2245"    # centro etichetta tab "Materiale" (qa_bounds)
TAB_FISICA="343 2245"       # centro "Fisica" (qa_bounds)
TAB_CONTROLLI="539 2245"    # centro "Controlli" (qa_bounds)
TAB_CHROME="736 2245"       # centro "Chrome" (qa_bounds)
AZIONE="592 2027"           # pulsante flottante "Azione" (qa_bounds, tab Materiale)
CHROME_CARD="540 680"       # riga "Card che si espande" (bounds 53,586 - 1027,775)
CHROME_MENU="540 1204"      # riga "Circolare di settembre" (bounds 53,1110 - 1299), menu contestuale
FISICA_CERCHIO="162 1552"   # chip "Cerchio" (forma iniziale del playground)
FISICA_QUADRATO="414 1552"  # chip "Quadrato" (cambio forma)
SLIDER_A="624 930"          # pollice "Volume" a 60%, dopo 1 scorrimento in Controlli
SLIDER_B="790 930"          # 80% della pista (pista x 120..958)
SWITCH_OFF_X=876            # pomello di "Interruttore spento", dopo 2 scorrimenti in Controlli
SWITCH_ON_X=912             # pomello di "Interruttore acceso" quando l'interruttore e' acceso
SWITCH_Y=1762
BUTTON_VETRO="203 1037"     # pulsante vetro "Vetro" (Controlli, in alto)
CLOSE_TAP="540 150"         # tap fuori dai menu, sotto la barra di stato

tap()  { "$ADB" shell input tap $1 $2; }
sw()   { "$ADB" shell input swipe "$@"; }
fin()  { qa_finish "$1_$2" "$OUT/$1"; }          # fin NOME RUN
reset_top() { for _ in 1 2 3; do sw 540 1400 540 2350 200; sleep 0.3; done; sleep 1; }
prep_controlli() { tap $TAB_CONTROLLI; sleep 2; reset_top; }
scroll1() { sw 540 1600 540 900 400; sleep 1; }
scroll2() { scroll1; sw 540 1600 540 1000 400; sleep 1; }

if [ -z "${SKIP_EMU:-}" ]; then
  # Su Windows l'emulatore va avviato staccato dalla shell, altrimenti esce da solo
  powershell.exe -NoProfile -Command 'Start-Process -FilePath (Join-Path $env:LOCALAPPDATA "Android\Sdk\emulator\emulator.exe") -ArgumentList "-avd Medium_Phone -no-snapshot-load -no-snapshot-save -no-boot-anim -gpu host -memory 4096" -WindowStyle Minimized'
  timeout 120 "$ADB" wait-for-device
  for i in $(seq 1 40); do
    [ "$(timeout 8 "$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
    sleep 5
  done
fi

# ---- Installazione e primo avvio ----
cp "$APK" "$OUT/sample-debug.apk"
"$ADB" install -r "$(qa_winpath "$APK")"
"$ADB" shell am start -n "$PKG/$ACT"
sleep 8
qa_shot "$OUT" home

# ---- Scenari (ogni scenario: RUN1 e RUN2 con gesti identici) ----

# 1. pill_drag: trascina la pillola dal tab Materiale al tab Chrome
for r in run1 run2; do
  tap $TAB_MATERIALE; sleep 2
  qa_record "pill_drag_$r" 6
  "$ADB" shell input draganddrop $TAB_MATERIALE $TAB_CHROME 1200
  fin pill_drag $r; sleep 2
done

# 2. slider_slow / slider_fast: dal pollice (60%) all'80% della pista
for spec in "slider_slow 1500" "slider_fast 150"; do
  set -- $spec; name=$1; dur=$2
  for r in run1 run2; do
    prep_controlli; scroll1
    sw $SLIDER_B $SLIDER_A 150; sleep 1.5      # riporta il pollice a 60% (stato iniziale identico)
    qa_record "${name}_$r" 6
    sw $SLIDER_A $SLIDER_B $dur
    fin $name $r; sleep 2
  done
done

# 3. switch_drag: 60 px a destra se spento, a sinistra se acceso
SW_ON=0
for r in run1 run2; do
  prep_controlli; scroll2; sleep 1
  if [ $SW_ON -eq 0 ]; then x1=$SWITCH_OFF_X; x2=$((SWITCH_OFF_X+60)); else x1=$SWITCH_ON_X; x2=$((SWITCH_ON_X-60)); fi
  qa_record "switch_drag_$r" 6
  sw $x1 $SWITCH_Y $x2 $SWITCH_Y 400
  fin switch_drag $r; SW_ON=$((1-SW_ON)); sleep 2
done

# 4. button_slow / button_fast: dal pulsante vetro "Vetro" 120 px a destra
for spec in "button_slow 1500" "button_fast 150"; do
  set -- $spec; name=$1; dur=$2
  for r in run1 run2; do
    prep_controlli; sleep 1
    qa_record "${name}_$r" 6
    sw 203 1037 323 1037 $dur
    fin $name $r; sleep 2
  done
done

# 5. morph_menu: pressione lunga su "Azione", poi tap fuori per chiudere
for r in run1 run2; do
  tap $TAB_MATERIALE; sleep 2
  qa_record "morph_menu_$r" 8
  sw $AZIONE $AZIONE 900
  sleep 2; tap $CLOSE_TAP
  fin morph_menu $r; sleep 2
done

# 6. context_menu: pressione lunga su "Circolare di settembre", poi tap fuori
for r in run1 run2; do
  tap $TAB_CHROME; sleep 2; reset_top
  qa_record "context_menu_$r" 8
  sw $CHROME_MENU $CHROME_MENU 900
  sleep 2; tap $CLOSE_TAP
  fin context_menu $r; sleep 2
done

# 7. expand_card: tap su "Card che si espande", poi BACK
for r in run1 run2; do
  tap $TAB_CHROME; sleep 2; reset_top
  qa_record "expand_card_$r" 8
  tap $CHROME_CARD; sleep 2.5
  "$ADB" shell input keyevent KEYCODE_BACK
  fin expand_card $r; sleep 2
done

# 8. back_gesture: tap su "Card che si espande", poi gesto predictive back dal bordo
for r in run1 run2; do
  tap $TAB_CHROME; sleep 2; reset_top
  qa_record "back_gesture_$r" 8
  tap $CHROME_CARD; sleep 2.5
  sw 3 1200 700 1200 450
  fin back_gesture $r; sleep 2
done

# 9. playground: tap sul controllo di cambio forma (Cerchio -> Quadrato)
for r in run1 run2; do
  tap $TAB_FISICA; sleep 2
  tap $FISICA_CERCHIO; sleep 1.5
  qa_record "playground_$r" 6
  tap $FISICA_QUADRATO
  fin playground $r; sleep 2
done

echo "baseline completata: $OUT"
