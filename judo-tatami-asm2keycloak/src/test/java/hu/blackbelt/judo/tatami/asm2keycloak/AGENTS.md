# AGENTS.md — `judo-tatami-asm2keycloak/src/test/java/hu/blackbelt/judo/tatami/asm2keycloak`

Core (Epsilon-free, Zeta) tests for ASM to Keycloak: workflow execution, Zeta trace, ETL-missing failure. ETL and dual-engine tests live in `judo-tatami-asm2keycloak-etl`.

| File | Purpose |
| --- | --- |
| `Asm2KeycloakWorkEtlMissingTest.java` | ETL mode without add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-asm2keycloak-etl`; asserts `getEngineType()` = `Asm2KeycloakEtlEngine`. See change: split-etl-artifacts. |
| `Asm2KeycloakWorkTest.java` | Tests `Asm2KeycloakWork` step in a sequential `WorkFlowEngine` pipeline via `new Asm2KeycloakWork(ctx)`; asserts `WorkStatus.COMPLETED` on Northwind demo models. See change: split-etl-artifacts. |
| `Asm2KeycloakZetaTraceTest.java` | Tests `TransformationTrace` content and serialization produced by `Asm2KeycloakZetaTransformation`. Asserts source EClass to target Keycloak Client/Realm mapping entries and verifies trace round-trip save/load via `ElementResolutionCache`. |
