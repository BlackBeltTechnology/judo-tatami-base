# AGENTS.md — `judo-tatami-asm2rdbms/src/test/java/hu/blackbelt/judo/tatami/asm2rdbms`

Core (Epsilon-free, Zeta) tests: abbreviation rules, workflow execution, ETL-missing failure. ETL, dual-engine, mapping and trace tests live in `judo-tatami-asm2rdbms-etl`.

| File | Purpose |
| --- | --- |
| `AbbreviateUtilsTest.java` | Tests SQL identifier shortening rules via `AbbreviateUtils.abbreviate()`. Asserts target character length boundaries, word separator handling, and consonant compression for issue JNG-1303. |
| `Asm2RdbmsWorkEtlMissingTest.java` | ETL mode without add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-asm2rdbms-etl`; asserts `getEngineType()` = `Asm2RdbmsEtlEngine`. See change: split-etl-artifacts. |
| `Asm2RdbmsWorkTest.java` | Tests `Asm2RdbmsWork` step in `WorkFlowEngine` across dialects (hsqldb, oracle) via `new Asm2RdbmsWork(ctx, dialect)`; asserts completion. `testZetaTraceHasNoDuplicateTargets`: Northwind, hsqldb+oracle; no duplicate targets per source, exactly one `RdbmsField` per `EAttribute` (failed before dedupe fix). See change: split-etl-artifacts (esm-itest Zeta fixes). |
