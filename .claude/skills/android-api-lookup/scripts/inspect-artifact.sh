#!/usr/bin/env bash
# Dump public signatures / constant values from a Maven artifact in the Gradle cache.
#
# Exists because Google's ML Kit / Play Services artifacts ship NO sources jar and are
# obfuscated, so javap on the compiled classes is the only ground truth for "does this
# version actually have member X, and what is its value". Docs have disagreed with the
# artifact more than once in this project.
#
# Usage:
#   scripts/inspect-artifact.sh <artifact-substring> [class-name-filter]
#   scripts/inspect-artifact.sh <artifact-substring> --class <fully.qualified.Name> [more...]
#
# Examples:
#   scripts/inspect-artifact.sh genai-prompt                      # list public classes
#   scripts/inspect-artifact.sh genai-prompt ModelConfig          # dump classes matching filter
#   scripts/inspect-artifact.sh genai-common --class com.google.mlkit.genai.common.FeatureStatus
#   ARTIFACT_VERSION=1.0.0-beta2 scripts/inspect-artifact.sh genai-prompt GenerateContentRequest
#
# Env:
#   ARTIFACT_VERSION  pin a specific version (default: highest found)
#   GRADLE_CACHE      override cache root
#   DECOMPILE=1       also disassemble bytecode (javap -c) — needed to read default values
set -uo pipefail

ARTIFACT="${1:-}"
if [ -z "$ARTIFACT" ]; then
    sed -n '3,26p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi
shift

CACHE="${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}"
WORK="${TMPDIR:-/tmp}/inspect-artifact/$ARTIFACT"

JAVAP=""
for c in "$JAVA_HOME/bin/javap" "$JAVA_HOME/bin/javap.exe" \
         "/c/Program Files/Android/Android Studio/jbr/bin/javap.exe" \
         "$(command -v javap 2>/dev/null)"; do
    [ -n "$c" ] && [ -x "$c" ] && { JAVAP="$c"; break; }
done
[ -z "$JAVAP" ] && { echo "ERROR: javap not found. Set JAVA_HOME." >&2; exit 1; }

# --- locate the archive (.aar or .jar) ------------------------------------------------
mapfile -t FOUND < <(find "$CACHE" \( -name "*.aar" -o -name "*.jar" \) \
    -path "*${ARTIFACT}*" ! -name "*-sources.jar" ! -name "*-javadoc.jar" 2>/dev/null | sort)
[ "${#FOUND[@]}" -eq 0 ] && { echo "ERROR: no artifact matching '$ARTIFACT' in $CACHE" >&2; exit 1; }

if [ -n "${ARTIFACT_VERSION:-}" ]; then
    ARCHIVE=$(printf '%s\n' "${FOUND[@]}" | grep -- "$ARTIFACT_VERSION" | head -1)
    [ -z "$ARCHIVE" ] && { echo "ERROR: version '$ARTIFACT_VERSION' not found. Available:" >&2
                           printf '  %s\n' "${FOUND[@]}" >&2; exit 1; }
else
    ARCHIVE="${FOUND[-1]}"
    if [ "${#FOUND[@]}" -gt 1 ]; then
        echo "# NOTE: ${#FOUND[@]} versions found; using newest. Pin with ARTIFACT_VERSION=." >&2
        printf '#   %s\n' "${FOUND[@]}" >&2
    fi
fi
echo "# artifact: $ARCHIVE"

# --- unpack to classes -----------------------------------------------------------------
rm -rf "$WORK"; mkdir -p "$WORK/ext"
case "$ARCHIVE" in
    *.aar) unzip -o -q "$ARCHIVE" -d "$WORK/aar" || exit 1
           CLASSES="$WORK/aar/classes.jar"
           [ -f "$CLASSES" ] || { echo "ERROR: no classes.jar inside AAR" >&2; exit 1; } ;;
    *.jar) CLASSES="$ARCHIVE" ;;
esac
unzip -o -q "$CLASSES" -d "$WORK/ext" || exit 1

# --- explicit class list ---------------------------------------------------------------
if [ "${1:-}" = "--class" ]; then
    shift
    FLAGS=(-constants -p)
    [ -n "${DECOMPILE:-}" ] && FLAGS+=(-c)
    for FQCN in "$@"; do
        echo; echo "########## $FQCN ##########"
        "$JAVAP" "${FLAGS[@]}" -classpath "$WORK/ext" "$FQCN" 2>&1
    done
    exit 0
fi

# --- discover public classes (skip obfuscated + internal) ------------------------------
mapfile -t CLASSLIST < <(cd "$WORK/ext" && find . -name "*.class" \
    | sed 's|^\./||; s|\.class$||' \
    | grep -v '/zz[a-z]*$' \
    | grep -v '/internal/' \
    | tr '/' '.' | sort)

FILTER="${1:-}"
if [ -z "$FILTER" ]; then
    echo "# ${#CLASSLIST[@]} public classes (pass a filter, or --class <fqcn>, to dump signatures):"
    printf '%s\n' "${CLASSLIST[@]}" | grep -v '\$'
    exit 0
fi

MATCHES=$(printf '%s\n' "${CLASSLIST[@]}" | grep -- "$FILTER")
[ -z "$MATCHES" ] && { echo "ERROR: no class matching '$FILTER'." >&2
                       echo "Run without a filter to list all." >&2; exit 1; }

FLAGS=(-constants -p)
[ -n "${DECOMPILE:-}" ] && FLAGS+=(-c)
while IFS= read -r FQCN; do
    echo; echo "########## $FQCN ##########"
    "$JAVAP" "${FLAGS[@]}" -classpath "$WORK/ext" "$FQCN" 2>&1
done <<< "$MATCHES"
