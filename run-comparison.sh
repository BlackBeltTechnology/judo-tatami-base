#!/usr/bin/env bash
#
# run-comparison.sh - Run ETL vs Zeta comparison/performance tests
#
# Usage:
#   ./run-comparison.sh [options]
#
# Options:
#   --mode STRICT|STRUCTURAL|LENIENT   Comparison mode (default: STRICT)
#   --basedir /path/to/models          Path to external models directory
#   --perf-only                        Disable comparison, run performance only
#   --module <name>                    Run single module (psm2asm|psm2measure|asm2rdbms|rdbms2liquibase|asm2keycloak)
#   --json                             Output aggregated JSON to stdout after all tests
#   --help                             Show this help message
#
set -euo pipefail

# --- Defaults ---
COMPARISON_MODE="STRICT"
BASEDIR=""
PERF_ONLY=false
SINGLE_MODULE=""
JSON_OUTPUT=false
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- Module definitions ---
# Each entry: maven_module:test_class:display_name
MODULES=(
  "judo-tatami-psm2asm:Psm2AsmDiscoveryComparisonTest:PSM2ASM"
  "judo-tatami-psm2measure:Psm2MeasureDiscoveryComparisonTest:PSM2Measure"
  "judo-tatami-asm2rdbms:Asm2RdbmsDiscoveryComparisonTest:ASM2RDBMS"
  "judo-tatami-rdbms2liquibase:Rdbms2LiquibaseDiscoveryComparisonTest:RDBMS2Liquibase"
  "judo-tatami-asm2keycloak:Asm2KeycloakDiscoveryComparisonTest:ASM2Keycloak"
)

# --- Parse arguments ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      COMPARISON_MODE="$2"
      shift 2
      ;;
    --basedir)
      BASEDIR="$2"
      shift 2
      ;;
    --perf-only)
      PERF_ONLY=true
      shift
      ;;
    --module)
      SINGLE_MODULE="$2"
      shift 2
      ;;
    --json)
      JSON_OUTPUT=true
      shift
      ;;
    --help|-h)
      head -16 "$0" | tail -14
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Run with --help for usage." >&2
      exit 1
      ;;
  esac
done

# --- Auto-detect basedir ---
if [[ -z "$BASEDIR" ]]; then
  CANDIDATE="${SCRIPT_DIR}/../judo-tatami-tests/models"
  if [[ -d "$CANDIDATE" ]]; then
    BASEDIR="$(cd "$CANDIDATE" && pwd)"
    echo "Auto-detected basedir: $BASEDIR"
  else
    echo "ERROR: Could not auto-detect models directory." >&2
    echo "Expected: ${CANDIDATE}" >&2
    echo "Use --basedir /path/to/models to specify manually." >&2
    exit 1
  fi
fi

if [[ ! -d "$BASEDIR" ]]; then
  echo "ERROR: basedir does not exist: $BASEDIR" >&2
  exit 1
fi

# --- Build system properties ---
MAVEN_PROPS="-Djudo.test.external.models.dir=${BASEDIR}"
MAVEN_PROPS+=" -Djudo.test.comparison.mode=${COMPARISON_MODE}"

if [[ "$PERF_ONLY" == true ]]; then
  MAVEN_PROPS+=" -Djudo.test.comparison.enabled=false"
fi

# --- Filter modules ---
if [[ -n "$SINGLE_MODULE" ]]; then
  FILTERED_MODULES=()
  FOUND=false
  for entry in "${MODULES[@]}"; do
    IFS=':' read -r maven_module test_class display_name <<< "$entry"
    # Match by short name (e.g., "psm2asm") or full maven module name
    module_lower="$(echo "$display_name" | tr '[:upper:]' '[:lower:]')"
    filter_lower="$(echo "$SINGLE_MODULE" | tr '[:upper:]' '[:lower:]')"
    if [[ "$maven_module" == *"$SINGLE_MODULE"* ]] || [[ "$module_lower" == "$filter_lower" ]]; then
      FILTERED_MODULES+=("$entry")
      FOUND=true
    fi
  done
  if [[ "$FOUND" == false ]]; then
    echo "ERROR: Unknown module '$SINGLE_MODULE'." >&2
    echo "Available modules: psm2asm, psm2measure, asm2rdbms, rdbms2liquibase, asm2keycloak" >&2
    exit 1
  fi
  MODULES=("${FILTERED_MODULES[@]}")
fi

# --- Ensure test-utils is compiled and installed ---
echo "Compiling test utilities..."
./mvnw install -pl judo-tatami-test-utils -DskipTests -q
echo ""

# --- Print configuration ---
echo "============================================"
echo " ETL vs Zeta Comparison Test Runner"
echo "============================================"
echo " Mode:      ${COMPARISON_MODE}"
echo " Basedir:   ${BASEDIR}"
echo " Perf-only: ${PERF_ONLY}"
echo " Modules:   ${#MODULES[@]}"
if [[ -n "$SINGLE_MODULE" ]]; then
  echo " Filter:    ${SINGLE_MODULE}"
fi
echo "============================================"
echo ""

# --- Run tests ---
PASS_COUNT=0
FAIL_COUNT=0
FAILED_MODULES=()
MODULE_RESULTS=()

for entry in "${MODULES[@]}"; do
  IFS=':' read -r maven_module test_class display_name <<< "$entry"

  echo ">>> Running ${display_name} (${maven_module})..."
  echo ""

  # Run Maven test with performance profile
  set +e
  ./mvnw test \
      -pl "${maven_module}" \
      -Pperformance \
      -Dtest="${test_class}" \
      ${MAVEN_PROPS} \
      -Dsurefire.useFile=false
  MVN_EXIT=$?
  set -e

  if [[ $MVN_EXIT -eq 0 ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    MODULE_RESULTS+=("${display_name}:PASS")
    echo ""
    echo "<<< ${display_name}: PASS"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    FAILED_MODULES+=("$display_name")
    MODULE_RESULTS+=("${display_name}:FAIL")
    echo ""
    echo "<<< ${display_name}: FAIL"
  fi
  echo ""
done

# --- Cross-module summary ---
echo ""
echo "============================================"
echo " Cross-Module Summary"
echo "============================================"
printf "  %-20s %s\n" "Module" "Result"
printf "  %-20s %s\n" "--------------------" "------"
for result in "${MODULE_RESULTS[@]}"; do
  IFS=':' read -r name status <<< "$result"
  printf "  %-20s %s\n" "$name" "$status"
done
echo "  ----------------------------------------"
printf "  Passed: %d / %d\n" "$PASS_COUNT" "$((PASS_COUNT + FAIL_COUNT))"
if [[ ${#FAILED_MODULES[@]} -gt 0 ]]; then
  echo "  Failed: ${FAILED_MODULES[*]}"
fi
echo "============================================"

# --- JSON aggregation ---
if [[ "$JSON_OUTPUT" == true ]]; then
  echo ""
  echo "--- Aggregated JSON ---"
  # Collect all comparison-results.json files
  # Build aggregated JSON manually (no jq dependency)
  echo "{"
  echo "  \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%S)\","
  echo "  \"comparisonMode\": \"${COMPARISON_MODE}\","
  echo "  \"perfOnly\": ${PERF_ONLY},"
  echo "  \"modules\": ["

  FIRST=true
  for entry in "${MODULES[@]}"; do
    IFS=':' read -r maven_module test_class display_name <<< "$entry"
    JSON_FILE="${SCRIPT_DIR}/${maven_module}/target/comparison-results.json"
    if [[ -f "$JSON_FILE" ]]; then
      if [[ "$FIRST" == true ]]; then
        FIRST=false
      else
        echo "    ,"
      fi
      echo "    $(cat "$JSON_FILE")"
    fi
  done

  echo "  ],"
  echo "  \"crossModuleSummary\": {"
  echo "    \"totalModules\": $((PASS_COUNT + FAIL_COUNT)),"
  echo "    \"passed\": ${PASS_COUNT},"
  echo "    \"failed\": ${FAIL_COUNT}"
  echo "  }"
  echo "}"
fi

# --- Exit code ---
if [[ $FAIL_COUNT -gt 0 ]]; then
  exit 1
fi
exit 0
