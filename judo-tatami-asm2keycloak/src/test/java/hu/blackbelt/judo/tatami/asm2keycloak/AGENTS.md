# AGENTS.md — `judo-tatami-asm2keycloak/src/test/java/hu/blackbelt/judo/tatami/asm2keycloak`

Unit and integration tests for ASM to Keycloak metamodel transformation engines and workflows.

| File | Purpose |
| --- | --- |
| `Asm2KeycloakTest.java` | Tests ASM to Keycloak transformation across ETL and Zeta engines. Exports `testAsm2KeycloakTransformation()`, `testRealmCreation()`, `testEtlAndZetaEquivalence()`. Compares generated Keycloak models using `ModelComparator` across `TransformationMode` variants. |
| `Asm2KeycloakWorkTest.java` | Tests `Asm2KeycloakWork` task step execution within a `WorkFlowEngine` sequential workflow pipeline. Asserts successful execution (`WorkStatus.COMPLETED`) on Northwind demo models. |
| `Asm2KeycloakZetaTraceTest.java` | Tests `TransformationTrace` content and serialization produced by `Asm2KeycloakZetaTransformation`. Asserts source EClass to target Keycloak Client/Realm mapping entries and verifies trace round-trip save/load via `ElementResolutionCache`. |
