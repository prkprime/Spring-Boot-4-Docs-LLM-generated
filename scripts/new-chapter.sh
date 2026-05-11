#!/usr/bin/env bash
# Scaffold a new chapter's code/ directory with a Maven Spring Boot 4 project.
# Usage: scripts/new-chapter.sh NN-slug "extra,deps,csv"
#   NN-slug:  e.g. 06-rest-controllers
#   deps:     Initializr dependencies (defaults to "web")
set -euo pipefail

SLUG="${1:?usage: scripts/new-chapter.sh NN-slug [deps-csv]}"
DEPS="${2:-web}"
SB_VERSION="${SB_VERSION:-4.0.6}"
JAVA_VERSION="${JAVA_VERSION:-25}"
GROUP="dev.springboot4docs"
PKG="ch_$(echo "${SLUG}" | sed 's/[^a-zA-Z0-9]/_/g' | tr 'A-Z' 'a-z')"
ARTIFACT="${SLUG}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/code/${SLUG}"

if [[ -e "${OUT}" ]]; then
  echo "ERROR: ${OUT} already exists" >&2
  exit 1
fi

mkdir -p "${OUT}"

scaffold() {
  local TYPE="$1"   # maven-project | gradle-project-kotlin
  local DIR="$2"    # maven | gradle
  local URL="https://start.spring.io/starter.zip"
  echo "→ scaffolding ${DIR} (${TYPE})..."
  curl -sf -G "${URL}" --data-urlencode "type=${TYPE}" \
                       --data-urlencode "language=java" \
                       --data-urlencode "bootVersion=${SB_VERSION}.RELEASE" \
                       --data-urlencode "baseDir=${DIR}" \
                       --data-urlencode "groupId=${GROUP}" \
                       --data-urlencode "artifactId=${ARTIFACT}" \
                       --data-urlencode "name=${ARTIFACT}" \
                       --data-urlencode "packageName=${GROUP}.${PKG}" \
                       --data-urlencode "packaging=jar" \
                       --data-urlencode "javaVersion=${JAVA_VERSION}" \
                       --data-urlencode "dependencies=${DEPS}" \
                       -o /tmp/sb4-init-${DIR}.zip
  unzip -q /tmp/sb4-init-${DIR}.zip -d "${OUT}"
  rm -f /tmp/sb4-init-${DIR}.zip
}

scaffold "maven-project" "maven"

# Initializr writes the SB version with a `.RELEASE` suffix that is not the
# actual published artifact coordinate (observed 2026-05-09 for SB 4.0.x).
# Patch it so `./mvnw test` resolves the parent POM.
sed -i "s|<version>${SB_VERSION}.RELEASE</version>|<version>${SB_VERSION}</version>|" \
  "${OUT}/maven/pom.xml"

# Gradle scaffolding is disabled because start.spring.io currently returns
# HTTP 500 for all Gradle project types (upstream Initializr bug observed
# 2026-05-09). When that is resolved, set SB4_INIT_GRADLE=1 to opt back in
# and we'll regenerate gradle/ for every existing chapter.
if [[ "${SB4_INIT_GRADLE:-0}" == "1" ]]; then
  scaffold "gradle-project-kotlin" "gradle"
fi

echo
echo "✓ scaffolded ${OUT}/maven"
[[ -d "${OUT}/gradle" ]] && echo "  + ${OUT}/gradle"
echo "  next: scripts/verify-chapter.sh ${SLUG}"
