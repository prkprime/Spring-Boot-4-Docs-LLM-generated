#!/usr/bin/env bash
# Install this repository's local Git hooks.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

if [[ ! -d .git ]]; then
  echo "ERROR: initialize Git first with: git init" >&2
  exit 1
fi

chmod +x .githooks/pre-commit .githooks/pre-push scripts/*.sh
git config core.hooksPath .githooks

echo "Git hooks installed from .githooks"
