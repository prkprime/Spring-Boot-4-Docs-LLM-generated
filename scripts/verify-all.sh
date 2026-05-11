#!/usr/bin/env bash
# Run the complete local verification suite.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

python3 scripts/check-render.py --build

for d in $(find code -mindepth 1 -maxdepth 1 -type d | sort -V); do
  slug="${d#code/}"
  scripts/verify-chapter.sh "${slug}"
done

echo "OK: docs and all chapter projects verified"
