#!/usr/bin/env bash
#
# run-performance-tests.sh - Run ETL vs Zeta performance tests with JVM profiling
#
# Usage:
#   ./run-performance-tests.sh [options]
#
# Options:
#   --basedir /path/to/models          Path to external models directory
#   --module <name>                    Run single module (psm2asm|psm2measure|asm2rdbms|rdbms2liquibase|asm2keycloak)
#   --mode DUAL|ZETA|ETL               Transformation mode (default: DUAL)
#   --no-profiler                      Disable JVM profiler (profiler is ON by default)
#   --zeta-only                        Alias for --mode ZETA (clean profiling, no ETL overhead)
#   --json                             Output aggregated JSON to stdout after all tests
#   --help                             Show this help message
#
# Examples:
#   ./run-performance-tests.sh --basedir /path/to/models
#   ./run-performance-tests.sh --basedir /path/to/models --no-profiler
#   ./run-performance-tests.sh --basedir /path/to/models --zeta-only
#   ./run-performance-tests.sh --module psm2asm --basedir /path/to/models
#
set -euo pipefail

# --- Defaults ---
BASEDIR=""
SINGLE_MODULE=""
TRANSFORMATION_MODE="DUAL"
PROFILER_ENABLED=true
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
    --basedir)
      BASEDIR="$2"
      shift 2
      ;;
    --module)
      SINGLE_MODULE="$2"
      shift 2
      ;;
    --mode)
      TRANSFORMATION_MODE="$2"
      shift 2
      ;;
    --no-profiler)
      PROFILER_ENABLED=false
      shift
      ;;
    --zeta-only)
      TRANSFORMATION_MODE="ZETA"
      shift
      ;;
    --json)
      JSON_OUTPUT=true
      shift
      ;;
    --help|-h)
      head -22 "$0" | tail -20
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

# --- Filter modules ---
if [[ -n "$SINGLE_MODULE" ]]; then
  FILTERED_MODULES=()
  FOUND=false
  for entry in "${MODULES[@]}"; do
    IFS=':' read -r maven_module test_class display_name <<< "$entry"
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

# --- Build system properties ---
MAVEN_PROPS="-Djudo.test.discovery.basedir=${BASEDIR}"
MAVEN_PROPS+=" -Djudo.test.transformation.mode=${TRANSFORMATION_MODE}"
MAVEN_PROPS+=" -Djudo.test.profiler.enabled=${PROFILER_ENABLED}"
MAVEN_PROPS+=" -Djudo.test.profiler.format=collapsed"
MAVEN_PROPS+=" -Djudo.test.profiler.thresholdMs=0"

# --- Ensure test-utils is compiled and installed ---
echo "Compiling test utilities..."
./mvnw install -pl judo-tatami-test-utils -DskipTests -q
echo ""

# --- Print configuration ---
echo "============================================"
echo " ETL vs Zeta Performance Test Runner"
echo "============================================"
echo " Mode:      ${TRANSFORMATION_MODE}"
echo " Profiler:  ${PROFILER_ENABLED}"
echo " Basedir:   ${BASEDIR}"
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
  # shellcheck disable=SC2086
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

  # Check profiler output
  PROFILER_DIR="${SCRIPT_DIR}/${maven_module}/target/profiler-output"
  if [[ "${PROFILER_ENABLED}" == true ]]; then
    if [[ -d "$PROFILER_DIR" ]] && compgen -G "${PROFILER_DIR}/*.txt" > /dev/null 2>&1; then
      TXT_COUNT=$(find "$PROFILER_DIR" -name "*.txt" | wc -l | tr -d ' ')
      echo "[INFO] Profiler: ${TXT_COUNT} file(s) in ${PROFILER_DIR}"
    else
      echo "[WARN] Profiler enabled but no .txt output found in ${PROFILER_DIR}"
      echo "[WARN] Check: perf_event_paranoid on Linux, or SIP settings on macOS"
    fi
  fi
  echo ""
done

# ============================================================
# PHASE 1: Performance Comparison Table
# ============================================================

print_perf_table() {
  echo ""
  echo "════════════════════════════════════════════════════════════════════"
  echo " CROSS-MODULE PERFORMANCE SUMMARY"
  echo "════════════════════════════════════════════════════════════════════"
  printf " %-16s │ %6s │ %9s │ %9s │ %8s │ %s\n" \
    "Module" "Models" "ETL(ms)" "ZETA(ms)" "Speedup" "Pass"
  echo " ────────────────┼────────┼───────────┼───────────┼──────────┼──────"

  local total_etl=0
  local total_zeta=0
  local total_models=0
  local total_pass=0
  local total_total=0

  for entry in "${MODULES[@]}"; do
    IFS=':' read -r maven_module test_class display_name <<< "$entry"
    local json_file="${SCRIPT_DIR}/${maven_module}/target/comparison-results.json"

    if [[ ! -f "$json_file" ]]; then
      echo "[WARN] No results found for ${display_name} (${json_file})" >&2
      printf " %-16s │ %6s │ %9s │ %9s │ %8s │ %s\n" \
        "$display_name" "-" "-" "-" "-" "N/A"
      continue
    fi

    # Parse JSON with awk (no jq dependency)
    local etl_ms zeta_ms speedup passed total_m
    etl_ms=$(awk -F'"' '/"totalEtlTimeMs"/{gsub(/[^0-9]/,"",$3); print $3}' "$json_file" | head -1)
    zeta_ms=$(awk -F'"' '/"totalZetaTimeMs"/{gsub(/[^0-9]/,"",$3); print $3}' "$json_file" | head -1)
    speedup=$(awk '/"avgSpeedup"/{gsub(/[^0-9.]/,"",$2); printf "%.2f", $2}' "$json_file")
    passed=$(awk '/"passed"/{gsub(/[^0-9]/,"",$2); print $2}' "$json_file" | head -1)
    total_m=$(awk '/"totalModels"/{gsub(/[^0-9]/,"",$2); print $2}' "$json_file" | head -1)

    etl_ms="${etl_ms:-0}"
    zeta_ms="${zeta_ms:-0}"
    speedup="${speedup:-0.00}"
    passed="${passed:-0}"
    total_m="${total_m:-0}"

    total_etl=$((total_etl + etl_ms))
    total_zeta=$((total_zeta + zeta_ms))
    total_models=$((total_models + total_m))
    total_pass=$((total_pass + passed))
    total_total=$((total_total + total_m))

    local speedup_fmt="${speedup}x"
    if [[ "$TRANSFORMATION_MODE" == "ZETA" ]]; then
      speedup_fmt="N/A"
      etl_ms="-"
    fi

    printf " %-16s │ %6s │ %9s │ %9s │ %8s │ %d/%d\n" \
      "$display_name" "$total_m" "$etl_ms" "$zeta_ms" "$speedup_fmt" "$passed" "$total_m"
  done

  # Totals row
  echo " ────────────────┼────────┼───────────┼───────────┼──────────┼──────"
  local avg_speedup="N/A"
  if [[ $total_zeta -gt 0 && "$TRANSFORMATION_MODE" != "ZETA" ]]; then
    avg_speedup=$(awk "BEGIN { printf \"%.2fx\", ${total_etl}/${total_zeta} }")
  fi
  local total_etl_fmt="$total_etl"
  if [[ "$TRANSFORMATION_MODE" == "ZETA" ]]; then
    total_etl_fmt="-"
  fi
  printf " %-16s │ %6s │ %9s │ %9s │ %8s │ %d/%d\n" \
    "TOTAL" "$total_models" "$total_etl_fmt" "$total_zeta" "$avg_speedup" "$total_pass" "$total_total"
  echo "════════════════════════════════════════════════════════════════════"
}

print_perf_table

# ============================================================
# PHASE 2: Hotspot Analysis
# ============================================================

print_hotspot_analysis() {
  echo ""
  echo "════════════════════════════════════════════════════════════════════"
  echo " CPU HOTSPOT ANALYSIS (JUDO/Zeta packages)"
  echo "════════════════════════════════════════════════════════════════════"

  # Collect all collapsed profile .txt files
  local all_txt_files=()
  for entry in "${MODULES[@]}"; do
    IFS=':' read -r maven_module test_class display_name <<< "$entry"
    local profiler_dir="${SCRIPT_DIR}/${maven_module}/target/profiler-output"
    if [[ -d "$profiler_dir" ]]; then
      while IFS= read -r -d '' f; do
        all_txt_files+=("$f")
      done < <(find "$profiler_dir" -name "*.txt" -print0 2>/dev/null)
    fi
  done

  if [[ ${#all_txt_files[@]} -eq 0 ]]; then
    echo " [INFO] No profiler output found — run without --no-profiler"
    echo " [INFO] On Linux: echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid"
    echo "════════════════════════════════════════════════════════════════════"
    return
  fi

  echo " Analyzing ${#all_txt_files[@]} profile file(s)..."
  echo ""

  # Parse all files: aggregate leaf-method samples, filter to judo/blackbelt packages
  # Output: cpu_pct<TAB>method<TAB>samples
  local hotspot_data
  hotspot_data=$(
    cat "${all_txt_files[@]}" | \
    awk '
      NF >= 2 {
        n = split($1, stack, ";")
        leaf = stack[n]
        count = $NF + 0
        if (count > 0) {
          samples[leaf] += count
          total += count
        }
      }
      END {
        for (m in samples) {
          pct = (total > 0) ? (samples[m] * 100.0 / total) : 0
          printf "%.2f\t%s\t%d\n", pct, m, samples[m]
        }
      }
    ' | \
    grep -E "(hu\.blackbelt|judo)" | \
    sort -rn | \
    head -20
  )

  if [[ -z "$hotspot_data" ]]; then
    echo " [INFO] No JUDO/Zeta methods found in profiler output"
    echo " [INFO] This may indicate: profiler captured no samples, or all time is in framework code"
    echo "════════════════════════════════════════════════════════════════════"
    return
  fi

  # Print hotspot table
  printf " %-4s │ %-60s │ %6s │ %9s\n" "Rank" "Method" "CPU%" "Samples"
  echo " ─────┼──────────────────────────────────────────────────────────────┼────────┼───────────"

  local rank=0
  local high_items=()
  local medium_items=()

  while IFS=$'\t' read -r pct method samples; do
    rank=$((rank + 1))

    # Truncate method name to 60 chars
    local short_method="${method:0:60}"
    if [[ ${#method} -gt 60 ]]; then
      short_method="${method:0:57}..."
    fi

    printf " %-4d │ %-60s │ %6.2f │ %9d\n" "$rank" "$short_method" "$pct" "$samples"

    # Classify
    local pct_int
    pct_int=$(echo "$pct" | awk '{printf "%d", $1}')
    if [[ $pct_int -ge 15 ]]; then
      high_items+=("HIGH   | ${pct}% | ${method}")
    elif [[ $pct_int -ge 5 ]]; then
      medium_items+=("MEDIUM | ${pct}% | ${method}")
    fi
  done <<< "$hotspot_data"

  echo "════════════════════════════════════════════════════════════════════"

  # Print optimization candidates
  if [[ ${#high_items[@]} -gt 0 || ${#medium_items[@]} -gt 0 ]]; then
    echo ""
    echo "════════════════════════════════════════════════════════════════════"
    echo " OPTIMIZATION CANDIDATES"
    echo "════════════════════════════════════════════════════════════════════"
    printf " %-8s │ %6s │ %s\n" "Priority" "CPU%" "Method"
    echo " ─────────┼────────┼────────────────────────────────────────────────"

    for item in "${high_items[@]}"; do
      IFS='|' read -r priority pct_label method_label <<< "$item"
      printf " %-8s │ %6s │ %s\n" "$(echo "$priority" | xargs)" "$(echo "$pct_label" | xargs)" "$(echo "$method_label" | xargs)"
    done
    for item in "${medium_items[@]}"; do
      IFS='|' read -r priority pct_label method_label <<< "$item"
      printf " %-8s │ %6s │ %s\n" "$(echo "$priority" | xargs)" "$(echo "$pct_label" | xargs)" "$(echo "$method_label" | xargs)"
    done

    echo "════════════════════════════════════════════════════════════════════"
    echo " Priority guide: HIGH ≥15% CPU  |  MEDIUM ≥5% CPU  |  LOW ≥1% CPU"
    echo "════════════════════════════════════════════════════════════════════"
  fi

  echo ""
  echo " Profile files written to:"
  for f in "${all_txt_files[@]}"; do
    echo "   ${f}"
  done
  echo " Use ProfileAnalyzer or a flamegraph viewer for deeper analysis."
}

if [[ "${PROFILER_ENABLED}" == true ]]; then
  print_hotspot_analysis
fi

# --- Module run summary ---
echo ""
echo "============================================"
echo " Run Summary"
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
  echo "{"
  echo "  \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%S)\","
  echo "  \"transformationMode\": \"${TRANSFORMATION_MODE}\","
  echo "  \"profilerEnabled\": ${PROFILER_ENABLED},"
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
