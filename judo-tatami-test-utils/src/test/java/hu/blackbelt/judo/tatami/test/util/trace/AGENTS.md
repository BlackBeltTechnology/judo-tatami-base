# AGENTS.md — `judo-tatami-test-utils/src/test/java/hu/blackbelt/judo/tatami/test/util/trace`

Unit tests for model-independent ETL vs Zeta transformation trace comparison.

| File | Purpose |
| --- | --- |
| `DualTraceComparatorTest.java` | Tests `DualTraceComparator.compare` with dynamic EMF `EPackage`/`EClass` instances and XMLResource XMI IDs. Verifies empty/identical traces produce no discrepancies, detects `MISSING_IN_ZETA` and `MISSING_IN_ETL`, and flags `TARGET_MISMATCH` when target types differ. |
