#!/usr/bin/env bash
# Run mvn test and gradle test for a chapter's both projects.
# Usage: scripts/verify-chapter.sh NN-slug
set -euo pipefail

SLUG="${1:?usage: scripts/verify-chapter.sh NN-slug}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="${ROOT}/code/${SLUG}"

[[ -d "${DIR}/maven" ]] || { echo "missing ${DIR}/maven"; exit 1; }

echo "▶ mvn spotless:check (${SLUG})"
( cd "${DIR}/maven" && ./mvnw -ntp spotless:check )

echo "▶ mvn test  (${SLUG})"
( cd "${DIR}/maven" && ./mvnw -ntp -q -B -DskipTests=false -DargLine="-XX:+EnableDynamicAgentLoading" test )

if [[ -d "${DIR}/gradle" ]]; then
  echo "▶ gradle spotlessCheck (${SLUG})"
  ( cd "${DIR}/gradle" && ./gradlew spotlessCheck )

  echo "▶ gradle test (${SLUG})"
  ( cd "${DIR}/gradle" && ./gradlew --quiet --console=plain test )
  echo "✓ ${SLUG}: both build tools green"
else
  echo "✓ ${SLUG}: maven green (gradle configuration missing)"
fi
