---
name: android-api-lookup
description: Look up the real shape of any library API this project resolves, and navigate the project's own code, without guessing or hunting. Use this whenever you are about to call an AndroidX, Compose, Material 3, ML Kit GenAI or Navigation 3 API and are not certain of its exact signature, whether a symbol exists in the resolved version, or which version is even on the classpath - the docs for these alpha and beta artifacts are sparse and have contradicted the bytecode before. Also use it at the start of any task in a fresh git worktree (index CodeGraph first), before exploring unfamiliar code, and before any filesystem search - never run a bare find. Triggers on "what is the signature of", "does X exist in this version", "which version do we resolve", "where is X used", "how does X work", AppBarWithSearch, Generable, or any compile error about an unresolved reference in a library.
---

# Finding things: APIs and code

Two rules, both learned the expensive way in this project.

**Never guess an API shape.** The libraries here are alpha and beta — `material3:1.5.0-alpha25`,
ML Kit GenAI Beta with Alpha structured output. Public docs are sparse, lag the artifacts, and have
disagreed with the actual bytecode more than once. Inspect the artifact instead.

**Never run an unscoped filesystem search.** Two agents in this project ran `find /` looking for
JARs. On Windows Git Bash that walks the entire drive; both hung for 25+ minutes and were killed
having produced nothing, while the agents gave up and answered another way. If you type `find`,
you have already taken a wrong turn — use the tools below.

## Library APIs — `tools/api.sh`

```bash
tools/api.sh <artifact>                    # list all public classes
tools/api.sh <artifact> <filter>           # dump classes matching a substring
tools/api.sh <artifact> --class <FQN>      # dump one fully-qualified class
tools/api.sh --version <artifact>          # print just the resolved version
DECOMPILE=1 tools/api.sh <artifact> <FQN>  # disassemble — the only way to read default values
```

Examples:

```bash
tools/api.sh material3 AppBarWithSearch
tools/api.sh genai-prompt --class com.google.mlkit.genai.common.FeatureStatus
tools/api.sh navigation3-runtime NavDisplay
```

First run takes ~1-2 minutes (it asks Gradle to resolve the dependency graph); later runs are fast.
It prints the resolved version and artifact path to stderr, so you can always see what you actually
read.

**Why the wrapper exists — this is the part that matters.** The version in
`gradle/libs.versions.toml` is a *request*, not the answer. Gradle resolves transitively, so
`material3:1.3.1 -> 1.5.0-alpha25` is normal and expected. Meanwhile the Gradle cache holds **every
version ever downloaded** — 23 artifacts matched `material3` here. So both obvious approaches are
wrong: reading the TOML gives you a version that is not on the classpath, and grabbing the newest
JAR from the cache gives you a version this project does not build against. Either way you get an
API that looks authoritative and is not. `tools/api.sh` asks Gradle first, then inspects exactly
that artifact.

`tools/inspect-artifact.sh` is the underlying tool if you need to pin a version yourself
(`ARTIFACT_VERSION=1.4.0 tools/inspect-artifact.sh material3`). Prefer `api.sh`.

## Project code — CodeGraph

`codegraph explore "<question or symbols>"` answers "how does X work", "where is X used", and
"what breaks if I change X" in one call, returning verbatim line-numbered source plus the call
paths and blast radius. Reach for it before grep or read.

**In a git worktree the index is missing — build it first, as your opening move:**

```bash
codegraph index          # or: codegraph init .   if .codegraph/ does not exist
```

Do this at the start of the task, before exploring anything. The index is machine-local and
deliberately not committed (a multi-MB SQLite database with a live daemon), so every fresh worktree
starts without one.

**Why building it beats skipping it.** Indexing is fast, and its cost is wall-clock time in a shell
command whose output never lands in your context. The alternative — a grep-and-read hunt — routinely
runs to twenty-plus tool calls, and every file you read is tokens you cannot get back. Context is
the scarce resource here, not seconds. One `codegraph explore` returns the same understanding for a
fraction of it.

`Grep`/`Glob` scoped to `app/src/` remain fine for a single known string. The graph earns its keep
the moment the question is structural.

## Scoping any search

- Use the `Grep` and `Glob` tools, not shell `find`/`grep`. They are ripgrep-backed and scoped.
- If you must shell out, always give a root and a depth:
  `find app/src -maxdepth 4 -name '*.kt'` — never `find /` or `find ~`.
- Searching the Gradle cache for an artifact file is already solved: `tools/api.sh`.

## Checking whether a symbol exists at all

```bash
tools/api.sh material3 2>/dev/null | grep -i searchbar
```

An empty result means it is genuinely absent in the resolved version — that is a real answer, and
better than a plausible guess. Say so rather than inventing a signature.

## Verifying claims about the built app

The artifact is ground truth for what shipped, and R8 can change it:

```bash
unzip -l app/build/outputs/apk/release/app-release.apk | grep -i <name>
unzip -p app/build/outputs/apk/release/app-release.apk META-INF/services/<service-file>
grep <ClassName> app/build/outputs/mapping/release/mapping.txt
```

The last one matters because ML Kit finds the KSP-generated `OrganizedResponse_GeneratedProvider`
through a `META-INF/services` ServiceLoader entry. If R8 strips or renames it, structured output
falls back to prompt-JSON **silently** — the build stays green and the app keeps working. Checking
the mapping proves the class survived; only a real save on a real device proves the path runs.
