# AGENTS.md — `judo-tatami-asm2keycloak/src/test/java/hu/blackbelt/judo/tatami/asm2keycloak/perf`

Performance and output-equivalence benchmarks comparing ETL versus Zeta execution for ASM to Keycloak transformations.

| File | Purpose |
| --- | --- |
| `Asm2KeycloakDiscoveryComparisonTest.java` | Extends `AbstractDualComparisonTest` to auto-discover external ASM models, transform via ETL and Zeta into Keycloak models, and assert semantic client parity. Module name evaluates to `asm2keycloak`. |
| `Asm2KeycloakPerformanceTest.java` | Benchmarks execution time and memory across parameterized model sizes (10, 50, 100 actors) comparing ETL against Zeta. Uses `ModelComparator` to assert structural identity between engine outputs. |
| `RealisticPerformanceTest.java` | Benchmarks ETL versus Zeta transformation times on synthetic RackInspect-scale ASM models generated via `RealisticAsmModelGenerator`. Verifies equivalence on small, medium, and large synthetic graphs. |
