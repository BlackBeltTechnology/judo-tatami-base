# AGENTS.md — asm2keycloak

ASM→Keycloak transformation entry points: ETL driver, workflow work unit, dual-engine trace.

| File | Purpose |
|---|---|
| `Asm2Keycloak.java` | ETL driver over `asmToKeycloak.etl`: `executeAsm2KeycloakTransformation(Asm2KeycloakParameter)`, `EtlExecutionContext` + `AsmUtils` injection, returns `Asm2KeycloakTransformationTrace` → see `Asm2Keycloak.java.AGENTS.md` |
| `Asm2KeycloakTransformationTrace.java` | `TransformationTrace` carrying ETL `Map<EObject, List<EObject>>` trace or Zeta trace; exports `fromModelsAndTrace`, `resolveAsm2KeycloakTrace`; constants misnamed `ASM_2_RDBMS_*` → see `Asm2KeycloakTransformationTrace.java.AGENTS.md` |
| `Asm2KeycloakWork.java` | `AbstractTransformationWork` dispatching `Asm2KeycloakWorkParameter.transformationMode` to `Asm2KeycloakZetaTransformation` or `Asm2Keycloak.executeAsm2KeycloakTransformation` → see `Asm2KeycloakWork.java.AGENTS.md` |
