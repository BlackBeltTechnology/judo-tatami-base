## Context

`run-comparison.sh` already orchestrates ETL vs ZETA correctness tests using `DiscoveryComparisonTest` classes. The JVM profiler integration (`ProfilingExtension`, `ProfileAnalyzer`, `@Profile`) is implemented and wired into the `performance` Maven profile via system properties. The two workflows — multi-model discovery testing and profiling — exist independently and need to be connected for performance analysis.

**Current state:**
- `DiscoveryComparisonTest` classes produce `target/comparison-results.json` (timing + speedup data)
- `ProfilingExtension` writes `target/profiler-output/<TestClass>_<method>.txt` (collapsed stack format)
- `ProfileAnalyzer.parseHotspots()` can parse collapsed format in Java; same parsing can be done in awk in the shell script
- `AbstractExternalModelTest` already warns when profiling in DUAL mode

## Goals / Non-Goals

**Goals:**
- Single script that runs all (or selected) DiscoveryComparisonTest classes with profiling enabled
- Produce comprehensive performance comparison tables (ETL vs ZETA, per model, per module)
- Parse profiler output and surface CPU hotspots filtered to JUDO/Zeta packages
- Classify hotspots into optimization priorities (HIGH/MEDIUM/LOW)
- Zero new Java code required — shell + awk only

**Non-Goals:**
- Not a replacement for `run-comparison.sh` (correctness remains separate concern)
- Not LLM-integrated analysis (offline — profiler outputs are written to files for manual/LLM review)
- Not parallel module execution (sequential to avoid profiler metric pollution between JVMs)

## Decisions

### Decision 1: Default transformation mode = DUAL (not ZETA)
**Rationale:** DUAL mode gives ETL vs ZETA timing ratios in the same run, which is the primary output of the performance script. ETL JVM overhead does not affect Zeta profiling since each transformation runs sequentially and the profiler is started/stopped per test method.

**Considered: ZETA-only as default**
Rejected because we lose the speedup ratio, which is the most valuable performance metric. Users wanting pure profiling can pass `--zeta-only`.

### Decision 2: Profiler enabled by default, opt-out with `--no-profiler`
**Rationale:** The script is specifically for performance analysis — having to pass `--profiler` every time defeats the purpose.

**Considered: opt-in with `--profiler`**
Rejected because the script is already named "performance-tests" — profiling is its raison d'être.

### Decision 3: Hotspot analysis in awk, not Java
**Rationale:** The collapsed format is trivially parseable with awk. No classpath, no compilation, immediate availability. `ProfileAnalyzer.java` exists for programmatic use but is not needed here.

**awk algorithm:**
```bash
awk '{ n=split($1,s,";"); leaf=s[n]; samples[leaf]+=$2; total+=$2 }
     END { for(m in samples) printf "%.2f\t%s\t%d\n", samples[m]*100/total, m, samples[m] }' \
| sort -rn | head -30
```

### Decision 4: Filter hotspots to JUDO/Zeta packages
**Rationale:** JVM internals (GC, JIT, classloading) and EMF/Epsilon framework internals appear in profiler output but cannot be optimized by the JUDO team. Filtering to `hu.blackbelt` and `judo` namespaces reveals actionable bottlenecks.

**Considered: no filtering, show all**
Rejected because the top 10 are often dominated by JVM/Epsilon internals, obscuring actual Zeta code issues.

### Decision 5: Three priority tiers for optimization candidates
- **HIGH**: CPU% ≥ 15% — significant bottleneck, investigate immediately
- **MEDIUM**: CPU% ≥ 5% — measurable, worth investigating
- **LOW**: CPU% ≥ 1% — minor, track for future

## Risks / Trade-offs

- **[Risk] Native library not available** → profiler silently skips, script still produces timing tables. Warning is printed if profiler output directory is empty after tests.

- **[Risk] DiscoveryComparisonTest doesn't find models** → test is skipped (Assumption), script shows 0 models. Same behavior as `run-comparison.sh`.

- **[Risk] Hotspot aggregation across modules may be misleading** — a method appearing in multiple modules might have different semantics. Mitigated by showing per-module hotspot breakdown as well as aggregate.

- **[Trade-off] Sequential execution** — running 5 modules sequentially is slower than parallel, but avoids JVM sharing / profiler interference. Acceptable for a script run manually or in CI nightly.

## Open Questions

- Should timing mode (`DUAL` vs `ZETA`) be a `--mode` flag or two separate scripts? Leaning toward flag since it reuses the existing `judo.test.transformation.mode` property.
- Should cross-module profiler output be aggregated into a single table or per-module sections? Leaning toward per-module sections followed by a deduplicated aggregate.
