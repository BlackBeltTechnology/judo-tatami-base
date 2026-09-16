# AGENTS.md — asm2keycloak/osgi

OSGi Declarative Services wrapper around the ASM→Keycloak transformation.

| File | Purpose |
|---|---|
| `Asm2KeycloakTransformationAsmModelTracker.java` | `AbstractModelTracker<AsmModel>` DS component registering `KeycloakModel` service via `Asm2KeycloakTransformationSerivce.install`, keyed by `asmModel.getName()` → see `Asm2KeycloakTransformationAsmModelTracker.java.AGENTS.md` |
| `Asm2KeycloakTransformationSerivce.java` | OSGi `@Component(service = ...)` wrapper: `install(AsmModel) : KeycloakModel` via `Asm2Keycloak.executeAsm2KeycloakTransformation` from bundle entry `asmToKeycloak.etl`; misspelled name wiring-stable → see `Asm2KeycloakTransformationSerivce.java.AGENTS.md` |
