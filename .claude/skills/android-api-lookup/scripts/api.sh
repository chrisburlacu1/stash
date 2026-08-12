#!/usr/bin/env bash
# Answer "what is this library's API, in the version this project actually resolves?"
#
# Wraps inspect-artifact.sh with the one thing it cannot do on its own: work out which
# version is really on the classpath. The version in libs.versions.toml is a *request* —
# Gradle resolves transitively, so `material3:1.3.1 -> 1.5.0-alpha25` is normal, and the
# Gradle cache holds every version ever downloaded. Reading the wrong one produces an API
# that looks authoritative and is wrong.
#
# Usage:
#   scripts/api.sh <artifact>                       # list all public classes
#   scripts/api.sh <artifact> <class-filter>        # dump classes matching a substring
#   scripts/api.sh <artifact> --class <FQN>         # dump one fully-qualified class
#   scripts/api.sh --version <artifact>             # print the resolved version only
#
# Examples:
#   scripts/api.sh material3 AppBarWithSearch
#   scripts/api.sh genai-prompt --class com.google.mlkit.genai.common.FeatureStatus
#   scripts/api.sh --version material3
#
# Add DECOMPILE=1 to disassemble bytecode — the only way to read default parameter values.
#
# Env:
#   CONFIGURATION   Gradle configuration to resolve against (default debugRuntimeClasspath)
#   NO_RESOLVE=1    skip Gradle resolution, fall back to newest in cache (fast, less correct)
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Ask git rather than counting ../ up from the script: this lives under
# .claude/skills/<name>/scripts/, and in a worktree the repo root is somewhere else entirely.
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
[ -n "$ROOT" ] || ROOT="$(cd "$HERE/../../../.." && pwd)"
CONFIGURATION="${CONFIGURATION:-debugRuntimeClasspath}"

usage() { sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

MODE="api"
case "${1:-}" in
    ""|-h|--help) usage ;;
    --version) MODE="version"; shift ;;
esac

ARTIFACT="${1:-}"
[ -z "$ARTIFACT" ] && usage
shift || true

# ---------------------------------------------------------------------------
# Resolve the real version from Gradle's dependency graph.
#
# Lines look like:  +--- androidx.compose.material3:material3:1.3.1 -> 1.5.0-alpha25
# The arrow form is the one that matters: take what is AFTER the arrow, since that is what
# actually ends up on the classpath. Without an arrow, take the trailing version.
resolve_version() {
    local artifact="$1"
    [ -n "${NO_RESOLVE:-}" ] && return 1

    local deps
    deps="$(cd "$ROOT" && ./gradlew -q app:dependencies --configuration "$CONFIGURATION" 2>/dev/null)" || return 1

    printf '%s\n' "$deps" \
        | grep -E "[:/]${artifact}(-android)?:" \
        | sed -E 's/.*-> *//; s/ *\(\*\)$//; s/ *\(c\)$//' \
        | sed -E 's/.*:([0-9][^ :]*)$/\1/' \
        | grep -E '^[0-9]' \
        | sort -u \
        | tail -1
}

VERSION="$(resolve_version "$ARTIFACT" || true)"

if [ -n "$VERSION" ]; then
    echo "# resolved: $ARTIFACT $VERSION (via $CONFIGURATION)" >&2
else
    echo "# WARNING: could not resolve $ARTIFACT from Gradle; falling back to newest in cache." >&2
    echo "#          The newest cached version may NOT be what this project builds against." >&2
fi

if [ "$MODE" = "version" ]; then
    [ -n "$VERSION" ] && { echo "$VERSION"; exit 0; }
    exit 1
fi

[ -n "$VERSION" ] && export ARTIFACT_VERSION="$VERSION"
exec "$HERE/inspect-artifact.sh" "$ARTIFACT" "$@"
