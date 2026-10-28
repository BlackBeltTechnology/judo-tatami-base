# AGENTS.md — `judo-tatami-rdbms2liquibase/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase/perf`

Performance and differential comparison test suites comparing ETL and Zeta transformation engines for RDBMS-to-Liquibase mappings.

| File | Purpose |
| --- | --- |
| `Rdbms2LiquibaseDiscoveryComparisonTest.java` | Extends `AbstractDualComparisonTest` to auto-discover external RDBMS models by dialect suffix and assert structural change-set parity between ETL and Zeta. |
| `Rdbms2LiquibasePerformanceTest.java` | Parameterized performance benchmark evaluating throughput, transformation timing, and speedup ratios of ETL versus Zeta across synthetic table collections. |
| `RealisticPerformanceTest.java` | Benchmarks ETL and Zeta transformations on synthetic RDBMS models generated via `RealisticRdbmsModelGenerator` at 30, 100, and 200 table scales. |
