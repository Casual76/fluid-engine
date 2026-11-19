#!/usr/bin/env bash
# perf-glass.sh: le corse di costo del vetro su un dispositivo (piano «iOS in Android», F7.1).
#
# Per ogni giro, per ogni APK (in ordine che ruota a ogni giro) e per ogni scenario: aspetta il dispositivo freddo
# sulla Home, lancia lo scenario, azzera gfxinfo al segnale PRONTO dell'app, raccoglie i framestats nelle pause fra un
# viaggio e l'altro, scrive temperatura e stato termico prima e dopo, e scarta (e rifà) le corse calde. Il riassunto
# dei tempi lo fa tools/qa/perf-summary.py sulla cartella di uscita.
#
# Uso (da Git Bash, dalla radice del repository):
#   bash tools/qa/perf-glass.sh -s R52X50DKRBW -o <cartella> \
#     --apk rif=<3.1.2.apk>,gesti=tools/qa/perf-gesti-312.txt \
#     --apk prima=<nuovo-prima-bench.apk> --apk dopo=<nuovo-dopo-bench.apk> \
#     [--scenari a,b,c] [--giri 3] [--tema chiaro|scuro] [--perfetto] [--pelle 35] [--pelle-dopo 35]
#   python tools/qa/perf-summary.py sessione <cartella>
#
# Due piloti:
#   misure  (di serie) l'APK ha la pagina «Misure» con gli scenari fissi: si lanciano con gli extra
#           `gallery.page measure`, `measure.scenario <id>`, e l'app scrive in logcat (tag FluidMisure) le righe
#           `SCENARIO PRONTO|INIZIO|FINE|CHIUSO ... t_ns=<CLOCK_MONOTONIC>`.
#   gesti   l'APK non ha gli scenari (la 3.1.2 di riferimento): un file di copioni dice, per scenario, la pagina della
#           galleria e i passi di agent-device (vedi tools/qa/perf-gesti-312.txt). L'origine dei viaggi la danno i
#           fotogrammi con un InputEventId.
#
# Regole del tablet (artifacts/liquid-glass/sessioni/tablet-diretto.md), tutte rispettate qui:
#   - mai il tasto di accensione ne' altro che spenga o blocchi lo schermo; la Home e' KEYCODE_HOME;
#   - niente `pm clear`, niente `run-as ... rm`; installazioni solo con `install -r` (con -t -d, come in S1);
#   - il tocco solo dagli extra di lancio (pilota misure) o da agent-device (pilota gesti), mai `input tap/swipe`;
#   - le tracce Perfetto si tolgono dal dispositivo dopo averle scaricate;
#   - lo stato del dispositivo (dimensioni, densita', tema di sistema, scale delle animazioni, voci di /sdcard) si scrive
#     all'inizio e alla fine: lo script non lo cambia, e se alla fine e' diverso lo dice.
# Se lo schermo risulta bloccato o spento, lo script si ferma subito (uscita 3). Un file STOP nella cartella di uscita
# lo ferma alla corsa seguente (uscita 2).

set -u
export MSYS_NO_PATHCONV=1

ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if [ -n "${LOCALAPPDATA:-}" ] && [ -x "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" ]; then
    ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
  else
    ADB=adb
  fi
fi
AGENT_DEVICE="${AGENT_DEVICE:-C:/Users/casua/.t3/tools/agent-device/0.21.36/node_modules/agent-device/bin/agent-device.mjs}"
ACTIVITY="dev.antigravity.fluidengine.sample.GalleryActivity"
TRACE_DIR="/data/misc/perfetto-traces"

# Gli scenari fissi della pagina «Misure» (sample/.../measure/MeasureScenario.kt), nell'ordine del piano.
ALL_SCENARIOS="scorrimento-foto,scorrimento-testo,menu-tasto,menu-gruppo,contestuale,barra-contesto,capsula-premuta,foglio-sale,foglio-trascinato,otto-lastre,push-pop,zoom-card,f1b-c,f1b-f,f1b-h"

SERIAL="${ANDROID_SERIAL:-}"
OUT=""
APK_SPECS=()
SCENARIOS="$ALL_SCENARIOS"
ROUNDS=3
THEME=chiaro
PERFETTO=0
SKIN_MAX=35.0
SKIN_AFTER_MAX=35.0
COOL_WAIT_MAX=900
RUN_TIMEOUT=150

POLL=""
RETRIES=2
RESUME=0
SESSION_NAME="perf-glass"
CYCLES=""

usage() { sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

while [ $# -gt 0 ]; do
  case "$1" in
    -s) SERIAL="$2"; shift 2 ;;
    -o) OUT="$2"; shift 2 ;;
    --apk) APK_SPECS+=("$2"); shift 2 ;;
    --scenari) SCENARIOS="$2"; shift 2 ;;
    --giri) ROUNDS="$2"; shift 2 ;;
    --cicli) CYCLES="$2"; shift 2 ;;
    --tema) THEME="$2"; shift 2 ;;
    --perfetto) PERFETTO=1; shift ;;
    --pelle) SKIN_MAX="$2"; shift 2 ;;
    --pelle-dopo) SKIN_AFTER_MAX="$2"; shift 2 ;;
    --attesa-max) COOL_WAIT_MAX="$2"; shift 2 ;;
    --durata-max) RUN_TIMEOUT="$2"; shift 2 ;;
    --sondaggio) POLL="$2"; shift 2 ;;
    --tentativi) RETRIES="$2"; shift 2 ;;
    --riprendi) RESUME=1; shift ;;
    --sessione) SESSION_NAME="$2"; shift 2 ;;
    -h|--help) usage 0 ;;
    *) echo "opzione sconosciuta: $1" >&2; usage 1 ;;
  esac
done

[ -n "$SERIAL" ] || { echo "manca il seriale (-s)" >&2; exit 1; }
[ -n "$OUT" ] || { echo "manca la cartella di uscita (-o)" >&2; exit 1; }
[ "${#APK_SPECS[@]}" -gt 0 ] || { echo "manca almeno un --apk" >&2; exit 1; }
case "$THEME" in chiaro) GALLERY_THEME=light ;; scuro) GALLERY_THEME=dark ;; *) echo "tema: chiaro o scuro" >&2; exit 1 ;; esac
if [ "$ROUNDS" -lt 3 ]; then
  echo "AVVISO: $ROUNDS giri. Il metodo del piano (F7, debito di F2) vuole almeno tre corse alternate per APK." >&2
fi

mkdir -p "$OUT/corse"
OUT_ABS="$(cd "$OUT" && pwd)"
LOG="$OUT_ABS/perf-glass.log"

say() { echo "$(date +%H:%M:%S) $*" | tee -a "$LOG"; }
adb() { "$ADB" -s "$SERIAL" "$@"; }
# Un comando di shell sul dispositivo, senza i ritorni a capo di Windows.
sh_dev() { "$ADB" -s "$SERIAL" shell "$@" | tr -d '\r'; }

# ---------------------------------------------------------------------------------------------------------------------
# Gli APK: etichetta=percorso[,gesti=file][,pacchetto=nome]
# ---------------------------------------------------------------------------------------------------------------------
LABELS=(); PATHS=(); PACKAGES=(); DRIVERS=(); SCRIPTS=()
for spec in "${APK_SPECS[@]}"; do
  label="${spec%%=*}"; rest="${spec#*=}"
  path="${rest%%,*}"; options=""
  [ "$rest" != "$path" ] && options="${rest#*,}"
  driver=misure; script=""; package=""
  IFS=',' read -r -a opts <<< "$options"
  for opt in "${opts[@]:-}"; do
    case "$opt" in
      gesti=*) driver=gesti; script="${opt#gesti=}" ;;
      pacchetto=*) package="${opt#pacchetto=}" ;;
      "") ;;
      *) echo "opzione dell'APK sconosciuta: $opt" >&2; exit 1 ;;
    esac
  done
  [ -f "$path" ] || { echo "APK mancante: $path" >&2; exit 1; }
  if [ -z "$package" ]; then
    case "$path" in
      *-bench.apk) package=dev.antigravity.fluidglass.bench ;;
      *) package=dev.antigravity.fluidglass.debug ;;
    esac
  fi
  if [ "$driver" = gesti ] && [ ! -f "$script" ]; then echo "copioni mancanti: $script" >&2; exit 1; fi
  LABELS+=("$label"); PATHS+=("$path"); PACKAGES+=("$package"); DRIVERS+=("$driver"); SCRIPTS+=("$script")
done

# ---------------------------------------------------------------------------------------------------------------------
# Stato del dispositivo, schermo, temperatura
# ---------------------------------------------------------------------------------------------------------------------
device_state() {
  echo "wm_size=$(sh_dev wm size | tail -n 1 | sed 's/.*: //')"
  echo "wm_density=$(sh_dev wm density | tail -n 1 | sed 's/.*: //')"
  echo "tema_sistema=$(sh_dev cmd uimode night | sed 's/.*: //')"
  echo "animazioni=$(sh_dev settings get global window_animation_scale)/$(sh_dev settings get global transition_animation_scale)/$(sh_dev settings get global animator_duration_scale)"
  echo "sdcard_voci=$(sh_dev ls /sdcard | wc -l | tr -d ' ')"
  echo "resta_sveglio=$(sh_dev settings get global stay_on_while_plugged_in)"
}

# Fermo subito se lo schermo e' bloccato o spento: una corsa sulla schermata di sblocco ferma tutte le successive.
check_screen() {
  local policy
  policy="$(sh_dev dumpsys window policy)"
  if ! printf '%s\n' "$policy" | grep -Eq '^[[:space:]]*showing=false[[:space:]]*$' ||
     printf '%s\n' "$policy" | grep -Eq '^[[:space:]]*(showing=true|mIsShowing=true)|screenState=SCREEN_STATE_OFF'; then
    say "FERMO: schermo bloccato, spento o stato sconosciuto. Nessun tasto premuto: va sbloccato a mano."
    finish 3
  fi
}

# "stato pelle batteria": lo stato termico, la pelle piu' calda (SKIN di thermalservice), la batteria in decimi.
read_thermal() {
  local file="$1"
  sh_dev dumpsys thermalservice > "$file"
  local status skin battery
  status="$(grep -m1 '^Thermal Status:' "$file" | awk '{print $3}')"
  skin="$(grep -o 'mValue=[0-9.]*, mType=3, mName=SKIN' "$file" | sed 's/mValue=//; s/,.*//' | sort -n | tail -n 1)"
  battery="$(sh_dev dumpsys battery | grep -m1 -E '^ +temperature:' | tr -dc '0-9')"
  echo "${status:-?} ${skin:-?} ${battery:-?}"
}

is_cold() {  # is_cold stato pelle soglia
  [ "$1" = "0" ] && awk -v s="$2" -v m="$3" 'BEGIN { exit !(s + 0 > 0 && s <= m) }'
}

go_home() { sh_dev input keyevent KEYCODE_HOME > /dev/null; }  # la Home, non un tocco

# Aspetta sulla Home lo stato termico 0 e la pelle a SKIN_MAX o meno. Scrive WAITED (secondi) e THERMAL_BEFORE.
cool_down() {
  local dir="$1" start now
  go_home
  start=$(date +%s)
  while true; do
    check_screen
    THERMAL_BEFORE="$(read_thermal "$dir/prima.thermal")"
    # shellcheck disable=SC2086
    if is_cold $THERMAL_BEFORE "$SKIN_MAX"; then break; fi
    now=$(date +%s)
    if [ $((now - start)) -gt "$COOL_WAIT_MAX" ]; then WAITED=$((now - start)); return 1; fi
    sleep 15
  done
  WAITED=$(( $(date +%s) - start ))
  return 0
}

# ---------------------------------------------------------------------------------------------------------------------
# Installazione e riscaldamento
# ---------------------------------------------------------------------------------------------------------------------
declare -A INSTALLED=()   # pacchetto -> etichetta dell'APK installato adesso
install_apk() {
  local i="$1" package="${PACKAGES[$1]}"
  if [ "${INSTALLED[$package]:-}" = "${LABELS[$i]}" ]; then return 0; fi
  say "install -r ${LABELS[$i]} (${PATHS[$i]})"
  if ! adb install -r -t -d "${PATHS[$i]}" >> "$LOG" 2>&1; then
    say "installazione fallita: ${PATHS[$i]}"; finish 4
  fi
  INSTALLED[$package]="${LABELS[$i]}"
  # Una corsa di riscaldamento scartata dopo ogni installazione (come nella chiusura di F2): il primo avvio paga la
  # verifica del codice e la prima compilazione degli shader, e non e' una misura.
  local first="${SCENARIO_LIST[0]}"
  if [ "${DRIVERS[$i]}" = misure ]; then
    adb logcat -c
    sh_dev am start -S -W -n "$package/$ACTIVITY" --es gallery.page measure --es measure.scenario "$first" \
      --es measure.cycles 1 --es gallery.theme "$GALLERY_THEME" > /dev/null
    wait_log "$package" "SCENARIO CHIUSO\|SCENARIO ERRORE" 60 > /dev/null
  else
    sh_dev am start -S -W -n "$package/$ACTIVITY" --es gallery.theme "$GALLERY_THEME" > /dev/null
    sleep 8
  fi
  sh_dev am force-stop "$package"
}

# Aspetta in logcat (sul dispositivo) una riga del tag FluidMisure; stampa la riga. Solo per il riscaldamento.
wait_log() {
  local package="$1" pattern="$2" seconds="$3" t=0
  while [ "$t" -lt $((seconds * 2)) ]; do
    if sh_dev logcat -d -s FluidMisure:I | grep -q "$pattern"; then return 0; fi
    sleep 0.5; t=$((t + 1))
  done
  return 1
}

# ---------------------------------------------------------------------------------------------------------------------
# Perfetto, a richiesta
# ---------------------------------------------------------------------------------------------------------------------
TRACE_PID=""; TRACE_REMOTE=""
trace_start() {
  local dir="$1" package="$2" name="$3"
  TRACE_REMOTE="$TRACE_DIR/perf-glass-$name-$(date +%s).pftrace"
  cat > "$dir/perfetto.pbtxt" <<EOF
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
duration_ms: $(( (RUN_TIMEOUT + 30) * 1000 ))
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
data_sources { config { name: "linux.process_stats" process_stats_config { scan_all_processes_on_start: true } } }
data_sources { config { name: "android.log" android_log_config { filter_tags: "FluidMisure" } } }
data_sources { config { name: "linux.ftrace" ftrace_config {
  atrace_categories: "view" atrace_categories: "gfx" atrace_categories: "input" atrace_categories: "dalvik"
  atrace_apps: "$package" ftrace_events: "sched/sched_switch" ftrace_events: "power/cpu_frequency"
  compact_sched { enabled: true } } } }
EOF
  TRACE_PID="$(adb shell perfetto --txt --background-wait -c - -o "$TRACE_REMOTE" < "$dir/perfetto.pbtxt" | tr -dc '0-9')"
  [ -n "$TRACE_PID" ] || say "Perfetto non e' partito"
}
trace_stop() {
  local dir="$1"
  [ -n "$TRACE_PID" ] || return 0
  sh_dev kill -TERM "$TRACE_PID" > /dev/null 2>&1
  for _ in $(seq 1 60); do
    [ -z "$(sh_dev "if [ -d /proc/$TRACE_PID ]; then echo vivo; fi")" ] && break
    sleep 0.2
  done
  adb pull "$TRACE_REMOTE" "$dir/trace.pftrace" > /dev/null 2>&1
  sh_dev rm -f "$TRACE_REMOTE" > /dev/null
  TRACE_PID=""; TRACE_REMOTE=""
}
# Le tracce di questo script rimaste da una corsa interrotta.
trace_cleanup() { sh_dev "rm -f $TRACE_DIR/perf-glass-*.pftrace" > /dev/null 2>&1; }

# ---------------------------------------------------------------------------------------------------------------------
# Una corsa
# ---------------------------------------------------------------------------------------------------------------------
LOGCAT_PID=""; POLLER_PID=""
dump() { sh_dev dumpsys gfxinfo "$1" framestats > "$2"; }

# Ferma un processo in sottofondo e controlla che sia fermo davvero (in Git Bash un programma di Windows lanciato da una
# sottoshell sopravvive alla sottoshell: per questo il lettore di logcat parte senza funzioni in mezzo, vedi run_once).
stop_proc() {
  local pid="$1"
  [ -n "$pid" ] || return 0
  kill "$pid" 2> /dev/null
  for _ in 1 2 3 4 5; do
    kill -0 "$pid" 2> /dev/null || return 0
    sleep 0.2
  done
  kill -9 "$pid" 2> /dev/null
}

stop_background() {
  stop_proc "$POLLER_PID"; POLLER_PID=""
  stop_proc "$LOGCAT_PID"; LOGCAT_PID=""
}

# Pilota misure. Al PRONTO azzera gfxinfo (l'app aspetta 1,5 s prima del primo viaggio). Poi un dump a ogni SONDA (a
# meta' dei viaggi lunghi) e a ogni FINE (nella quiete che segue: il dump lavora sul RenderThread dell'app, e nella
# quiete non sporca nessun fotogramma misurato). Se PRONTO dice `sondaggi=no` (le prove di F1B) nessun dump nel mezzo:
# solo quello finale, come le prove di allora.
drive_measure() {
  local dir="$1" package="$2" scenario="$3" log="$1/log.txt" extras=()
  [ -n "$CYCLES" ] && extras=(--es measure.cycles "$CYCLES")
  sh_dev am start -S -W -n "$package/$ACTIVITY" --es gallery.page measure --es measure.scenario "$scenario" \
    --es gallery.theme "$GALLERY_THEME" "${extras[@]}" > "$dir/apertura.txt"
  local deadline=$(( $(date +%s) + RUN_TIMEOUT )) seen=0 n=0 ready=0 probes=1
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if [ "$ready" = 0 ] && grep -q "SCENARIO PRONTO" "$log" 2> /dev/null; then
      sh_dev dumpsys gfxinfo "$package" reset > /dev/null
      ready=1
      echo "reset_t=$(date +%s.%N)" >> "$dir/meta.txt"
      grep -q "SCENARIO PRONTO.*sondaggi=no" "$log" && probes=0
    fi
    if grep -q "SCENARIO ERRORE" "$log" 2> /dev/null; then return 2; fi
    if [ "$probes" = 1 ] && [ -z "$POLL" ]; then
      local events
      events="$(grep -c "SCENARIO SONDA\|SCENARIO FINE" "$log" 2> /dev/null)"
      while [ "$seen" -lt "${events:-0}" ]; do
        seen=$((seen + 1))
        dump "$package" "$dir/fs-$(printf '%03d' "$n").gfx"; n=$((n + 1))
      done
    fi
    if grep -q "SCENARIO CHIUSO" "$log" 2> /dev/null; then
      [ "$ready" = 1 ] && return 0 || return 2
    fi
    sleep 0.1
  done
  return 1
}

# Pilota gesti: la riga dello scenario nel file di copioni, `id | pagina | passo; passo; ...`.
drive_gestures() {
  local dir="$1" package="$2" scenario="$3" script="$4"
  local line page steps
  line="$(grep -v '^[[:space:]]*#' "$script" | grep -E "^[[:space:]]*$scenario[[:space:]]*\|" | head -n 1)"
  [ -n "$line" ] || return 3
  page="$(echo "$line" | cut -d'|' -f2 | tr -d ' ')"
  steps="$(echo "$line" | cut -d'|' -f3-)"
  local args=(open "$package" --relaunch --serial "$SERIAL" --session "$SESSION_NAME"
              "--launch-args=-e" "--launch-args=gallery.theme" "--launch-args=$GALLERY_THEME")
  if [ -n "$page" ] && [ "$page" != "-" ]; then
    args+=("--launch-args=-e" "--launch-args=gallery.page" "--launch-args=$page")
  fi
  ad "${args[@]}" > "$dir/apertura.txt" 2>&1 || return 2
  sleep 3
  sh_dev dumpsys gfxinfo "$package" reset > /dev/null
  echo "reset_t=$(date +%s.%N)" >> "$dir/meta.txt"
  # Un copione senza `dump` legge solo alla fine, come le prove di F1B: lo dice al riassunto, che allora non chiede
  # i primi 100 ms di ogni viaggio (il buffer ne tiene solo gli ultimi 120 fotogrammi).
  case "$steps" in *dump*) echo "sondaggi=si" >> "$dir/meta.txt" ;; *) echo "sondaggi=no" >> "$dir/meta.txt" ;; esac
  local n=0 step
  IFS=';' read -r -a list <<< "$steps"
  for step in "${list[@]}"; do
    step="$(echo "$step" | sed 's/^ *//; s/ *$//')"
    [ -n "$step" ] || continue
    case "$step" in
      "attendi "*) sleep "$(awk -v m="${step#attendi }" 'BEGIN { printf "%.3f", m / 1000 }')" ;;
      dump) dump "$package" "$dir/fs-$(printf '%03d' "$n").gfx"; n=$((n + 1)) ;;
      # La preparazione (una scheda da aprire, una pagina da scorrere) e' finita: la misura comincia qui.
      azzera)
        sh_dev dumpsys gfxinfo "$package" reset > /dev/null
        echo "reset_t=$(date +%s.%N)" >> "$dir/meta.txt" ;;
      *)
        # Lo schermo si controlla prima di ogni gesto, non prima delle attese e dei dump: allungherebbe le pause fra il
        # gesto e la lettura, e i copioni le hanno contate.
        check_screen
        # shellcheck disable=SC2086
        ad $step --serial "$SERIAL" --session "$SESSION_NAME" >> "$dir/passi.txt" 2>&1 || return 2 ;;
    esac
  done
  return 0
}

ad() {
  ANDROID_HOME="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}" node "$AGENT_DEVICE" "$@"
}

run_once() {  # run_once giro indice-apk scenario nome
  local round="$1" i="$2" scenario="$3" name="$4"
  local dir="$OUT_ABS/corse/$name" package="${PACKAGES[$i]}"
  rm -rf "$dir"; mkdir -p "$dir"
  {
    echo "corsa=$name"; echo "giro=$round"; echo "apk=${LABELS[$i]}"; echo "file_apk=${PATHS[$i]}"
    echo "pacchetto=$package"; echo "pilota=${DRIVERS[$i]}"; echo "scenario=$scenario"; echo "tema=$THEME"
    echo "inizio=$(date '+%Y-%m-%d %H:%M:%S')"
  } > "$dir/meta.txt"
  if ! cool_down "$dir"; then
    echo "esito=caldo-attesa"; echo "attesa_s=$WAITED"
    return 1
  fi
  local before_status before_skin before_battery
  read -r before_status before_skin before_battery <<< "$THERMAL_BEFORE"
  {
    echo "attesa_s=$WAITED"; echo "stato_prima=$before_status"; echo "pelle_prima=$before_skin"
    echo "batteria_prima=$before_battery"
  } >> "$dir/meta.txt"

  adb logcat -c
  # Direttamente, non con la funzione `adb`: una funzione in sottofondo e' una sottoshell, e fermarla lascerebbe vivo
  # adb.exe, che continuerebbe a scrivere le righe delle corse dopo nel registro di questa.
  "$ADB" -s "$SERIAL" logcat -v monotonic -s FluidMisure:I > "$dir/log.txt" 2>&1 &
  LOGCAT_PID=$!
  [ "$PERFETTO" = 1 ] && trace_start "$dir" "$package" "$name"
  if [ -n "$POLL" ]; then
    ( n=0; while true; do sleep "$POLL"; dump "$package" "$dir/fs-p$(printf '%04d' "$n").gfx"; n=$((n + 1)); done ) &
    POLLER_PID=$!
  fi

  local code=0
  if [ "${DRIVERS[$i]}" = misure ]; then
    drive_measure "$dir" "$package" "$scenario"; code=$?
  else
    drive_gestures "$dir" "$package" "$scenario" "${SCRIPTS[$i]}"; code=$?
  fi
  local pid_before
  pid_before="$(sh_dev pidof "$package")"
  sleep 0.3
  dump "$package" "$dir/fine.gfx"
  local pid_after
  pid_after="$(sh_dev pidof "$package")"
  stop_background
  [ "$PERFETTO" = 1 ] && trace_stop "$dir"
  local after after_status after_skin after_battery
  after="$(read_thermal "$dir/dopo.thermal")"
  read -r after_status after_skin after_battery <<< "$after"
  sh_dev am force-stop "$package"
  go_home

  local outcome=valida reason=""
  case "$code" in
    1) outcome=scaduta; reason="nessun CHIUSO entro ${RUN_TIMEOUT}s" ;;
    2) outcome=errore; reason="lo scenario non e' partito o ha scritto ERRORE" ;;
    3) outcome=senza-copione; reason="nessuna riga per $scenario in ${SCRIPTS[$i]}" ;;
  esac
  if [ "$outcome" = valida ] && [ -n "$pid_before" ] && [ "$pid_before" != "$pid_after" ]; then
    outcome=errore; reason="il processo e' cambiato durante la corsa"
  fi
  if [ "$outcome" = valida ] && ! is_cold "$after_status" "$after_skin" "$SKIN_AFTER_MAX"; then
    outcome=calda; reason="dopo: stato $after_status, pelle $after_skin (soglia $SKIN_AFTER_MAX)"
  fi
  {
    echo "stato_dopo=$after_status"; echo "pelle_dopo=$after_skin"; echo "batteria_dopo=$after_battery"
    echo "pid=$pid_before"; echo "esito=$outcome"; echo "motivo=$reason"; echo "fine=$(date '+%Y-%m-%d %H:%M:%S')"
  } >> "$dir/meta.txt"
  say "$name: $outcome ${reason:+($reason) }pelle $before_skin -> $after_skin, stato $before_status -> $after_status"
  [ "$outcome" = valida ] && return 0
  [ "$outcome" = calda ] && return 1
  return 5
}

finish() {
  stop_background
  [ -n "$TRACE_PID" ] && trace_stop "$OUT_ABS"
  trace_cleanup
  if [ -n "${STATE_BEFORE:-}" ]; then
    local now
    now="$(device_state)"
    printf '%s\n' "$now" | sed 's/^/stato_fine /' >> "$OUT_ABS/sessione.txt"
    if [ "$now" != "$STATE_BEFORE" ]; then
      say "ATTENZIONE: lo stato del dispositivo e' diverso da quello d'inizio (vedi sessione.txt)."
    fi
  fi
  if grep -q gesti <<< "${DRIVERS[*]}"; then ad close --serial "$SERIAL" --session "$SESSION_NAME" > /dev/null 2>&1; fi
  say "fine (uscita $1). Riassunto: python tools/qa/perf-summary.py sessione $OUT_ABS"
  exit "$1"
}
trap 'finish 130' INT TERM

# ---------------------------------------------------------------------------------------------------------------------
# La sessione
# ---------------------------------------------------------------------------------------------------------------------
IFS=',' read -r -a SCENARIO_LIST <<< "$SCENARIOS"
check_screen
STATE_BEFORE="$(device_state)"
{
  echo "# perf-glass.sh $(date '+%Y-%m-%d %H:%M:%S')"
  echo "seriale $SERIAL"; echo "modello $(sh_dev getprop ro.product.model)"; echo "giri $ROUNDS"; echo "tema $THEME"
  echo "scenari $SCENARIOS"; echo "pelle_max $SKIN_MAX"; echo "pelle_dopo_max $SKIN_AFTER_MAX"; echo "perfetto $PERFETTO"
  for i in "${!LABELS[@]}"; do
    echo "apk ${LABELS[$i]} ${PATHS[$i]} ${PACKAGES[$i]} ${DRIVERS[$i]} $(sha256sum "${PATHS[$i]}" | cut -c1-16)"
  done
  printf '%s\n' "$STATE_BEFORE" | sed 's/^/stato_inizio /'
} > "$OUT_ABS/sessione.txt"
case "$STATE_BEFORE" in *"resta_sveglio=0"*)
  say "AVVISO: «resta sveglio» e' spento: durante le attese al freddo lo schermo potrebbe spegnersi e lo script si fermerebbe." ;;
esac
trace_cleanup

COUNT=${#LABELS[@]}
for round in $(seq 1 "$ROUNDS"); do
  for k in $(seq 0 $((COUNT - 1))); do
    i=$(( (k + round - 1) % COUNT ))   # l'ordine ruota a ogni giro: nessun APK parte sempre per primo
    [ -f "$OUT_ABS/STOP" ] && { say "STOP trovato"; finish 2; }
    install_apk "$i"
    for scenario in "${SCENARIO_LIST[@]}"; do
      name="$round-$scenario-${LABELS[$i]}"
      if [ "$RESUME" = 1 ] && grep -q '^esito=valida' "$OUT_ABS/corse/$name/meta.txt" 2> /dev/null; then continue; fi
      attempt=0
      while true; do
        [ -f "$OUT_ABS/STOP" ] && { say "STOP trovato"; finish 2; }
        tag="$name"; [ "$attempt" -gt 0 ] && tag="$name-t$attempt"
        run_once "$round" "$i" "$scenario" "$tag"; result=$?
        [ "$result" = 0 ] && break
        # Una corsa calda si rifa' a freddo (fino a RETRIES volte); un errore no: resta scritto e si va avanti.
        [ "$result" = 1 ] && [ "$attempt" -lt "$RETRIES" ] || break
        attempt=$((attempt + 1))
      done
    done
  done
done
finish 0
