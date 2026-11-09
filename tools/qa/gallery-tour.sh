#!/usr/bin/env bash
# Il giro della galleria (F1.6): installa Fluid Glass sull'emulatore, apre ogni pagina dei componenti nuovi in ogni
# combinazione della matrice degli stati che si chiede, e per ognuna scatta uno screenshot (e, se si vuole, un
# secondo dopo uno scorrimento) e controlla che l'app non sia caduta.
#
# Perche' esiste: i test Robolectric dicono che una pagina regge la matrice senza un'eccezione, ma Robolectric non
# disegna il vetro. Quello che il giro mostra — un testo tagliato al 200%, un nero che non si legge, un vetro che non
# rifrange — si vede solo qui, e va guardato (vedi tools/qa/qa-common.sh e strumenti-e-qa.md: «Una UI si giudica
# in mano»). Lo script produce le immagini; guardarle resta un lavoro vostro.
#
# Come apre le pagine: non tocca lo schermo. La galleria di debug legge degli extra di lancio (`gallery.page`,
# `gallery.theme`, `gallery.text`, `gallery.reduced`, `gallery.lang`, `gallery.data`; vedi GalleryLaunch.kt) e parte gia'
# dove si chiede, nella matrice che si chiede. Un tocco su una coordinata e' la prova piu' fragile che ci sia, e
# cambierebbe a ogni versione.
#
# Uso (da Git Bash, dalla radice dell'engine, con UN emulatore acceso e la galleria compilata):
#   tools/qa/gallery-tour.sh                       tutte le pagine, in chiaro, scuro, 200% e scuro+200%+ridotto
#   tools/qa/gallery-tour.sh -p "LongLists Charts" solo queste pagine (i nomi di GalleryPage)
#   tools/qa/gallery-tour.sh -c "light black english"   solo queste combinazioni
#   tools/qa/gallery-tour.sh --scroll              un secondo screenshot per pagina, dopo due scorrimenti
#   tools/qa/gallery-tour.sh --no-install          non reinstalla (usa quella gia' sull'emulatore)
#   tools/qa/gallery-tour.sh --list                elenca pagine e combinazioni
#   tools/qa/gallery-tour.sh -o cartella           dove mettere le immagini (di serie artifacts/gallery-tour/<ora>)
#
# Compilare prima:  ./gradlew.bat --no-daemon --no-watch-fs :sample:assembleDebug
# (dentro il semaforo del PC: gradle-slot.ps1, se girano altri agenti.)
#
# Esce 0 se nessuna combinazione ha fatto cadere l'app, 1 altrimenti. Lascia l'app fermata (force-stop) e, con
# --uninstall, la disinstalla: l'emulatore e' di tutti, si restituisce com'era.

set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
# shellcheck source=qa-common.sh
source "$SCRIPT_DIR/qa-common.sh"

PACKAGE="dev.antigravity.fluidglass.debug"
ACTIVITY="dev.antigravity.fluidengine.sample.GalleryActivity"

# I nomi sono quelli di GalleryPage (GalleryNav.kt), nell'ordine dei gruppi del catalogo.
ALL_PAGES="LongLists Identity DrawerAndTabs NotificationUndo TokenField DateTime RichText WebContent Charts FileTile"
# Le combinazioni di serie: chiaro, scuro, testo al 200%, e il caso peggiore (scuro, 200%, movimento ridotto).
DEFAULT_COMBOS="light dark text200 dark200"

# combo -> gli extra di lancio da passare ad `am start` (--es chiave valore)
combo_extras() {
  case "$1" in
    light)    echo "--es gallery.theme light --es gallery.text 1.0" ;;
    dark)     echo "--es gallery.theme dark --es gallery.text 1.0" ;;
    black)    echo "--es gallery.theme black --es gallery.text 1.0" ;;
    text130)  echo "--es gallery.theme light --es gallery.text 1.3" ;;
    text200)  echo "--es gallery.theme light --es gallery.text 2.0" ;;
    dark200)  echo "--es gallery.theme dark --es gallery.text 2.0 --es gallery.reduced true" ;;
    black200) echo "--es gallery.theme black --es gallery.text 2.0 --es gallery.reduced true" ;;
    reduced)  echo "--es gallery.theme light --es gallery.text 1.0 --es gallery.reduced true" ;;
    english)  echo "--es gallery.theme light --es gallery.text 1.0 --es gallery.lang en" ;;
    empty)    echo "--es gallery.theme light --es gallery.text 1.0 --es gallery.data empty" ;;
    loading)  echo "--es gallery.theme light --es gallery.text 1.0 --es gallery.data loading" ;;
    error)    echo "--es gallery.theme light --es gallery.text 1.0 --es gallery.data error" ;;
    *)        return 1 ;;
  esac
}
ALL_COMBOS="light dark black text130 text200 dark200 black200 reduced english empty loading error"

PAGES="$ALL_PAGES"
COMBOS="$DEFAULT_COMBOS"
OUT=""
APK=""
INSTALL=1
SCROLL=0
UNINSTALL=0
WAIT=3

while [ $# -gt 0 ]; do
  case "$1" in
    -p) PAGES="$2"; shift 2 ;;
    -c) COMBOS="$2"; shift 2 ;;
    -o) OUT="$2"; shift 2 ;;
    -a) APK="$2"; shift 2 ;;
    -w) WAIT="$2"; shift 2 ;;
    --scroll) SCROLL=1; shift ;;
    --no-install) INSTALL=0; shift ;;
    --uninstall) UNINSTALL=1; shift ;;
    --list)
      echo "Pagine:       $ALL_PAGES"
      echo "Combinazioni: $ALL_COMBOS"
      echo "Di serie:     $DEFAULT_COMBOS"
      exit 0
      ;;
    -h|--help) sed -n '2,32p' "$0"; exit 0 ;;
    *) echo "Argomento sconosciuto: $1 (--help)"; exit 2 ;;
  esac
done

for combo in $COMBOS; do
  combo_extras "$combo" >/dev/null || { echo "Combinazione sconosciuta: $combo (--list)"; exit 2; }
done

STAMP="$(date +%Y%m%d-%H%M%S)"
[ -z "$OUT" ] && OUT="$ROOT/artifacts/gallery-tour/$STAMP"
mkdir -p "$OUT"

# --- L'emulatore: uno solo, e deve esserci --------------------------------------------------------------
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "Nessun dispositivo raggiungibile con ANDROID_SERIAL=$ANDROID_SERIAL."
  echo "Accendi l'emulatore (AVD Medium_Phone) e riprova; lo script non ne avvia uno per non contendere quello di altri."
  exit 3
fi

# --- L'installazione -------------------------------------------------------------------------------------
if [ "$INSTALL" -eq 1 ]; then
  if [ -z "$APK" ]; then
    APK="$(ls "$ROOT"/sample/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1)"
  fi
  if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "Nessun APK di debug in sample/build/outputs/apk/debug: compila prima :sample:assembleDebug (o passa -a)."
    exit 3
  fi
  echo "Installo $APK"
  "$ADB" install -r -d "$(qa_winpath "$APK")" >/dev/null || { echo "Installazione fallita"; exit 3; }
fi

# Un'app che non c'e' non si fotografa.
if ! "$ADB" shell pm list packages "$PACKAGE" | grep -q "$PACKAGE"; then
  echo "$PACKAGE non e' installata sull'emulatore."
  exit 3
fi

# --- Il giro ----------------------------------------------------------------------------------------------
total=0
failed=0
INDEX="$OUT/indice.md"
{
  echo "# Giro della galleria — $STAMP"
  echo
  echo "Pagine: $PAGES"
  echo "Combinazioni: $COMBOS"
  echo
  echo "| pagina | combinazione | screenshot | esito |"
  echo "|---|---|---|---|"
} > "$INDEX"

for page in $PAGES; do
  for combo in $COMBOS; do
    total=$((total + 1))
    name="${page}_${combo}"
    extras="$(combo_extras "$combo")"

    "$ADB" logcat -c >/dev/null 2>&1
    # -S ferma l'app prima di avviarla (stato pulito), -W aspetta che la prima schermata sia davanti.
    # shellcheck disable=SC2086
    "$ADB" shell am start -S -W -n "$PACKAGE/$ACTIVITY" --es gallery.page "$page" $extras >/dev/null 2>&1
    sleep "$WAIT"

    outcome="ok"
    # L'app deve essere ancora viva, e non deve aver scritto una caduta.
    if ! "$ADB" shell pidof "$PACKAGE" >/dev/null 2>&1 || [ -z "$("$ADB" shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r')" ]; then
      outcome="CADUTA (processo assente)"
    elif "$ADB" logcat -d -b crash 2>/dev/null | grep -q "$PACKAGE"; then
      outcome="CADUTA (log dei crash)"
    fi

    qa_shot "$OUT" "$name"
    qa_dump "$OUT" "$name"
    "$ADB" logcat -d -b crash > "$OUT/${name}_crash.txt" 2>/dev/null
    [ -s "$OUT/${name}_crash.txt" ] || rm -f "$OUT/${name}_crash.txt"

    if [ "$SCROLL" -eq 1 ] && [ "$outcome" = "ok" ]; then
      # Due scorrimenti verso l'alto, dal centro: la lista va avanti e i testi sotto la prima schermata si vedono.
      size="$("$ADB" shell wm size | grep -o '[0-9]*x[0-9]*' | tail -n 1)"
      w="${size%x*}"; h="${size#*x}"
      for _ in 1 2; do
        "$ADB" shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 400
        sleep 1
      done
      qa_shot "$OUT" "${name}_scroll"
    fi

    [ "$outcome" = "ok" ] || failed=$((failed + 1))
    echo "[$total] $page · $combo → $outcome"
    echo "| $page | $combo | ${name}.png | $outcome |" >> "$INDEX"
  done
done

"$ADB" shell am force-stop "$PACKAGE"
if [ "$UNINSTALL" -eq 1 ]; then
  "$ADB" uninstall "$PACKAGE" >/dev/null 2>&1
fi

{
  echo
  echo "Totale: $total · cadute: $failed"
} >> "$INDEX"

echo
echo "Fatto: $total combinazioni, $failed cadute. Immagini e indice in $OUT"
[ "$failed" -eq 0 ]
