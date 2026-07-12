#!/usr/bin/env bash
# Gestiona la versión de QvaArbi SMS en app/build.gradle.
#
# versionCode: entero monotónico que la app compara para detectar updates.
# versionName: string semver cosmético (mayor.menor.parche).
#
# Uso:
#   scripts/bump-version.sh                 # muestra la versión actual (no cambia nada)
#   scripts/bump-version.sh show
#   scripts/bump-version.sh patch           # code +1, name x.y.(z+1)   (fix)
#   scripts/bump-version.sh minor           # code +1, name x.(y+1).0   (feature)
#   scripts/bump-version.sh major           # code +1, name (x+1).0.0   (rediseño)
#   scripts/bump-version.sh set 3 2.4.1     # manual: versionCode=3, versionName=2.4.1
#   scripts/bump-version.sh code            # solo code +1 (name intacto)
set -euo pipefail

# Raíz del repo = carpeta padre de este script.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE="$ROOT/app/build.gradle"
[ -f "$GRADLE" ] || { echo "No encuentro $GRADLE" >&2; exit 1; }

cur_code() { grep -Eo 'versionCode[[:space:]]+[0-9]+' "$GRADLE" | grep -Eo '[0-9]+'; }
cur_name() { grep -Eo 'versionName[[:space:]]+"[^"]*"' "$GRADLE" | sed -E 's/.*"([^"]*)".*/\1/'; }

show() { echo "versionCode : $(cur_code)"; echo "versionName : $(cur_name)"; }

# Reescribe ambos campos in-place.
write() {
  local code="$1" name="$2"
  perl -i -pe "s/(versionCode\s+)\d+/\${1}$code/" "$GRADLE"
  perl -i -pe "s/(versionName\s+)\"[^\"]*\"/\${1}\"$name\"/" "$GRADLE"
  echo "Actualizado →"
  show
}

CMD="${1:-show}"

case "$CMD" in
  show|"")
    show
    ;;
  code)
    write "$(( $(cur_code) + 1 ))" "$(cur_name)"
    ;;
  patch|minor|major)
    IFS='.' read -r MA MI PA <<< "$(cur_name)"
    # Rellena partes ausentes (p.ej. "2" → 2.0.0) para no romper el semver.
    MA="${MA:-0}"; MI="${MI:-0}"; PA="${PA:-0}"
    case "$CMD" in
      patch) PA=$(( PA + 1 )) ;;
      minor) MI=$(( MI + 1 )); PA=0 ;;
      major) MA=$(( MA + 1 )); MI=0; PA=0 ;;
    esac
    write "$(( $(cur_code) + 1 ))" "$MA.$MI.$PA"
    ;;
  set)
    NEW_CODE="${2:-}"; NEW_NAME="${3:-}"
    [[ "$NEW_CODE" =~ ^[0-9]+$ && -n "$NEW_NAME" ]] || {
      echo "uso: bump-version.sh set <versionCode:int> <versionName:x.y.z>" >&2; exit 1; }
    write "$NEW_CODE" "$NEW_NAME"
    ;;
  *)
    echo "comando desconocido: $CMD" >&2
    echo "usa: show | patch | minor | major | code | set <code> <name>" >&2
    exit 1
    ;;
esac
