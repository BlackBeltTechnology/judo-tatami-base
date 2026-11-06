# judo-tatami-asm2keycloak-etl/src/main/java/hu/blackbelt/judo/tatami/asm2keycloak/etl
ETL entry point of the ASM→Keycloak stage plus its engine adapter. Sub-package `.etl` keeps core and add-on split-package free.
| File | Purpose |
|---|---|
| `Asm2Keycloak.java` | ETL driver over `asmToKeycloak.etl`: `executeAsm2KeycloakTransformation(Asm2KeycloakParameter)`, `EtlExecutionContext` + `AsmUtils` injection, returns `Asm2KeycloakTransformationTrace`. Package `.etl` (moved from core) → see `Asm2Keycloak.java.AGENTS.md`. See change: split-etl-artifacts. |
| `Asm2KeycloakEtlEngineImpl.java` | Implements core `Asm2KeycloakEtlEngine`; `execute(...)` builds `Asm2KeycloakParameter` and calls `Asm2Keycloak.executeAsm2KeycloakTransformation`; `log` may be `null` (buffered SLF4J logger). Loaded by `ServiceLoader` or `Asm2KeycloakEtlEngineComponent`. See change: split-etl-artifacts. |
