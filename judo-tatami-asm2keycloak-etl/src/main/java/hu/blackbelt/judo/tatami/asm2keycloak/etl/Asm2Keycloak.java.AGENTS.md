# `Asm2Keycloak.java`

Drives the ETL engine over `asmToKeycloak.etl`. Lives in `judo-tatami-asm2keycloak-etl`, package `hu.blackbelt.judo.tatami.asm2keycloak.etl`; core `Asm2KeycloakWork` reaches it only via `Asm2KeycloakEtlEngineImpl` / `EtlEngineRegistry`. Exports `executeAsm2KeycloakTransformation(Asm2KeycloakParameter)`, `calculateAsm2KeycloakTransformationScriptURI()`, builder `asm2KeycloakParameter()`, constant `SCRIPT_ROOT_TATAMI_ASM_2_KEYCLOAK = "tatami/asm2keycloak/transformations/"`.
Wraps `AsmModel` and `KeycloakModel` as `WrappedEmfModelContext` entries, injects `AsmUtils` into the Epsilon context, runs an `EtlExecutionContext`, then extracts the ETL trace and returns an `Asm2KeycloakTransformationTrace`. `asmModel` and `keycloakModel` are `@NonNull` on the parameter; script URI resolution assumes the ETL scripts are on the classpath under the script root, MD5-keyed for cache reuse.

See change: split-etl-artifacts: moved from core to `-etl`, package `.etl`.
