# AGENTS.md — `judo-tatami-asm2rdbms/src/test/java/hu/blackbelt/judo/tatami/asm2rdbms/perf`

Performance and output-equivalence benchmarks comparing ETL versus Zeta execution for ASM to RDBMS transformations.

| File | Purpose |
| --- | --- |
| `Asm2RdbmsDiscoveryComparisonTest.java` | Extends `AbstractDualComparisonTest` to auto-discover external ASM models, transform via ETL and Zeta into RDBMS models, and compare generated tables. Configurable SQL dialect defaults to `hsqldb`. |
| `Asm2RdbmsPerformanceTest.java` | Benchmarks throughput and execution duration across parameterized synthetic model sizes (10, 50, 100 entities). Compares ETL against Zeta and asserts structural equality with `ModelComparator`. |
| `RealisticPerformanceTest.java` | Evaluates execution duration and output equivalence of ETL versus Zeta transformations on synthetic RackInspect-scale ASM models produced by `RealisticAsmModelGenerator`. |
