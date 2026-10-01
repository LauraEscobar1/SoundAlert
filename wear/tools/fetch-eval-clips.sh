#!/bin/bash
# Descarga clips de evaluación del dataset ESC-50 (Piczak, CC BY-NC 3.0) y los
# convierte a WAV 16 kHz mono PCM16 (formato del pipeline) con afconvert (macOS).
# Destino: app/src/androidTest/assets/clips/<grupo>__<categoria-esc50>__<archivo>.wav
# Esa carpeta está en .gitignore: los clips NO se suben al repositorio.
#
# Uso: tools/fetch-eval-clips.sh [clips_por_categoria]   (por defecto 5)
set -euo pipefail
PER=${1:-5}
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/app/src/androidTest/assets/clips"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
BASE=https://raw.githubusercontent.com/karolpiczak/ESC-50/master
mkdir -p "$OUT"
curl -fsSL "$BASE/meta/esc50.csv" -o "$TMP/esc50.csv"

# Positivos: categoría ESC-50 → debería producir un evento de SoundAlert.
POSITIVE="siren car_horn door_wood_knock glass_breaking crying_baby clock_alarm dog"
# Negativos: no deberían producir ningún evento.
NEGATIVE="breathing keyboard_typing snoring footsteps laughing coughing sneezing clapping mouse_click clock_tick church_bells vacuum_cleaner washing_machine"

fetch() {
  local group=$1 category=$2
  grep ",$category," "$TMP/esc50.csv" | head -n "$PER" | cut -d, -f1 | while read -r file; do
    curl -fsSL "$BASE/audio/$file" -o "$TMP/$file"
    afconvert -f WAVE -d LEI16@16000 -c 1 "$TMP/$file" "$OUT/${group}__${category}__${file}"
  done
  echo "$group $category: $(ls "$OUT" | grep -c "^${group}__${category}__")"
}
for c in $POSITIVE; do fetch pos "$c"; done
for c in $NEGATIVE; do fetch neg "$c"; done

# Voz sintética (TTS de macOS) como negativo adicional.
say -v Paulina -o "$TMP/speech.aiff" "Hola, esta es una conversación normal en casa. Mañana vamos a la universidad temprano y después al trabajo."
afconvert -f WAVE -d LEI16@16000 -c 1 "$TMP/speech.aiff" "$OUT/neg__speech-tts__paulina.wav"
echo "Clips en $OUT: $(ls "$OUT" | wc -l)"
