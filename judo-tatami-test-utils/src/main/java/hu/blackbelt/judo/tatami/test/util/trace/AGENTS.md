# AGENTS.md — `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/util/trace`

Model-independent transformation trace comparison tools for aligning ETL and Zeta execution traces and reporting mapping discrepancies.

| File | Purpose |
| --- | --- |
| `DualTraceComparator.java` | Compares ETL and Zeta source-to-target mapping traces keyed by source XMI ID and target EClass name. Exports `compare(Map<EObject, List<EObject>>, Map<EObject, List<EObject>>)`. Collects `TraceDiscrepancy` instances for missing mappings and target type mismatches. |
| `TraceDiscrepancy.java` | Immutable record representing differences between ETL and Zeta transformation traces. Exports `TraceDiscrepancy` record fields (`sourceId`, `etlTargetId`, `zetaTargetId`, `etlRuleName`, `zetaRuleName`, `type`), inner enum `Type` (`MISSING_IN_ZETA`, `MISSING_IN_ETL`, `TARGET_MISMATCH`), and formatted output method `describe()`. |
