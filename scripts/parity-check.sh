#!/usr/bin/env bash
# Confirm Maven and Gradle samples for a chapter compile equivalent code:
# the set of *.java source files (relative paths) must match between maven/ and gradle/.
# We don't byte-compare files — Initializr emits identical sources, but we want a hard
# guard against drift if a future chapter edits one side without the other.
# Usage: scripts/parity-check.sh NN-slug
set -euo pipefail

SLUG="${1:?usage: scripts/parity-check.sh NN-slug}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
M="${ROOT}/code/${SLUG}/maven/src"
G="${ROOT}/code/${SLUG}/gradle/src"

if [[ ! -d "${G}" ]]; then
  echo "↷ ${SLUG}: gradle/ absent (Initializr Gradle outage). Skipping parity."
  exit 0
fi
[[ -d "${M}" ]] || { echo "missing src/ in maven"; exit 1; }

m_list="$(cd "${M}" && find . -name '*.java' -print | sort)"
g_list="$(cd "${G}" && find . -name '*.java' -print | sort)"

if [[ "${m_list}" != "${g_list}" ]]; then
  echo "✗ Maven/Gradle source tree mismatch for ${SLUG}"
  diff <(echo "${m_list}") <(echo "${g_list}") || true
  exit 1
fi

# Per file, byte-equal? (same source, two build configs)
fail=0
while IFS= read -r f; do
  if ! cmp -s "${M}/${f}" "${G}/${f}"; then
    echo "✗ source diverged: ${f}"
    fail=1
  fi
done <<< "${m_list}"

if [[ "${fail}" -ne 0 ]]; then
  exit 1
fi

echo "✓ ${SLUG}: maven and gradle source trees match"
