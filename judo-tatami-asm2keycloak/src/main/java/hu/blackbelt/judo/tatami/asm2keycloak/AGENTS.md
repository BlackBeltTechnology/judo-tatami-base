# AGENTS.md — asm2keycloak

ASM→Keycloak transformation entry points: workflow work unit, dual-engine trace, ETL engine contract. Epsilon-free core; ETL driver lives in `judo-tatami-asm2keycloak-etl`.

| File | Purpose |
|---|---|
| `Asm2KeycloakEtlEngine.java` | Typed ETL engine contract. `execute(AsmModel, KeycloakModel, Logger, createTrace, useCache, parallel)` → `Asm2KeycloakTransformationTrace`; `ARTIFACT_COORDINATES = "hu.blackbelt.judo.tatami:judo-tatami-asm2keycloak-etl"`. Implemented by `-etl` module, resolved by `EtlEngineRegistry`. See change: split-etl-artifacts. |
| `Asm2KeycloakTransformationTrace.java` | `TransformationTrace` carrying ETL `Map<EObject, List<EObject>>` trace or Zeta trace; exports `fromModelsAndTrace`, `resolveAsm2KeycloakTrace`; Zeta→legacy map dedupes targets per source; constants misnamed `ASM_2_RDBMS_*` → see `Asm2KeycloakTransformationTrace.java.AGENTS.md`. See change: split-etl-artifacts (esm-itest Zeta fixes). |
| `Asm2KeycloakWork.java` | `AbstractTransformationWork` dispatching `transformationMode`: Zeta runs `Asm2KeycloakZetaTransformation`, ETL/DUAL delegate via `EtlEngineRegistry.require(Asm2KeycloakEtlEngine.class, ...)`; ctor `(ctx)` only → see `Asm2KeycloakWork.java.AGENTS.md`. See change: split-etl-artifacts. |
