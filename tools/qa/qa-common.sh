#!/usr/bin/env bash
# Funzioni comuni per la baseline QA delle animazioni (registrazione, frame, luminanza, dump UI).
# Uso: source tools/qa/qa-common.sh

# Git Bash non deve riscrivere i percorsi che iniziano con /sdcard
export MSYS_NO_PATHCONV=1

# Strumenti Android e ffmpeg (non sono nel PATH)
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
EMULATOR="$LOCALAPPDATA/Android/Sdk/emulator/emulator.exe"
FFMPEG="$LOCALAPPDATA/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-9.0-full_build/bin/ffmpeg.exe"

# Con un solo emulatore attivo, adb punta sempre a questo seriale (non tocca i dispositivi fisici)
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"

# PID del processo adb che tiene aperto screenrecord (impostato da qa_record)
QA_PID=""

# Percorso Git Bash -> percorso Windows, per passarlo ai programmi nativi
qa_winpath() {
  cygpath -w "$1"
}

# Avvia la registrazione sul dispositivo: qa_record NOME SECONDI
qa_record() {
  local name="$1" secs="$2"
  "$ADB" shell screenrecord --bit-rate 20000000 --time-limit "$secs" "/sdcard/qa_${name}.mp4" >/dev/null 2>&1 &
  QA_PID=$!
  sleep 1
}

# Chiude la registrazione, scarica il video, estrae frame e luminanza, salva le vibrazioni: qa_finish NOME CARTELLA
qa_finish() {
  local name="$1" outdir="$2"
  if [ -n "$QA_PID" ]; then wait "$QA_PID" 2>/dev/null; fi
  sleep 1
  mkdir -p "$outdir/${name}_frames"
  "$ADB" pull "/sdcard/qa_${name}.mp4" "$(qa_winpath "$outdir/${name}.mp4")" >/dev/null
  "$ADB" shell rm -f "/sdcard/qa_${name}.mp4"
  # ffmpeg gira dalla cartella di output: così i filtri non hanno i due punti del percorso Windows
  ( cd "$outdir" && "$FFMPEG" -loglevel error -y -i "${name}.mp4" -fps_mode passthrough -vf scale=iw/2:-1 "${name}_frames/%04d.png" )
  ( cd "$outdir" && "$FFMPEG" -loglevel error -y -i "${name}.mp4" -vf "signalstats,metadata=print:file=${name}_luma.txt" -f null - )
  "$ADB" shell dumpsys vibrator_manager > "$outdir/${name}_vibrator.txt"
  QA_PID=""
}

# Centro "x y" del primo nodo UI con testo o content-desc uguale a TESTO (vuoto se non trovato)
qa_bounds() {
  local text="$1" tmp
  tmp="$(mktemp -d)"
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" pull /sdcard/ui.xml "$(qa_winpath "$tmp/ui.xml")" >/dev/null 2>&1
  grep -o '<node [^>]*>' "$tmp/ui.xml" 2>/dev/null \
    | grep -F -e "text=\"$text\"" -e "content-desc=\"$text\"" \
    | head -n 1 \
    | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
    | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' \
    | awk 'NF==4 { printf "%d %d\n", ($1+$3)/2, ($2+$4)/2 }'
  rm -rf "$tmp"
}

# Screenshot del dispositivo: qa_shot CARTELLA NOME
qa_shot() {
  "$ADB" shell screencap -p /sdcard/qa_shot.png
  "$ADB" pull /sdcard/qa_shot.png "$(qa_winpath "$1/$2.png")" >/dev/null
  "$ADB" shell rm -f /sdcard/qa_shot.png
}

# Dump della gerarchia UI: qa_dump CARTELLA NOME
qa_dump() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  "$ADB" pull /sdcard/ui.xml "$(qa_winpath "$1/$2.xml")" >/dev/null
}
