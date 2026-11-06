# AGENTS.md — `judo-tatami-test-utils/src/test/java/hu/blackbelt/judo/tatami/test/util`

Unit tests verifying the abstract dual-comparison test framework and execution timing measurement utilities.

| File | Purpose |
| --- | --- |
| `AbstractDualComparisonTestTest.java` | Tests `AbstractDualComparisonTest` lifecycle and assertion behavior using concrete in-memory subclasses (`IdenticalDualTest`, `MismatchedDualTest`). Verifies comparison passes on equivalent EMF EPackages and throws assertion errors on discrepancy. |
| `PerformanceMeasurementTest.java` | Tests `PerformanceMeasurement.measure`, `measureAvg`, and `measureAvg` with warmup cycles. Verifies execution duration capture, exception propagation, iteration counts, and arithmetic mean calculation in `TimedResult`. |
