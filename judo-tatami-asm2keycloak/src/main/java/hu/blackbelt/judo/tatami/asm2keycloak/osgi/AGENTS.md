# AGENTS.md — asm2keycloak/osgi

OSGi Declarative Services wrapper around the ASM→Keycloak transformation.

| File | Purpose |
|---|---|
| `Asm2KeycloakTransformationAsmModelTracker.java` | `AbstractModelTracker<AsmModel>` DS component registering `KeycloakModel` service via `Asm2KeycloakTransformationSerivce.install`, keyed by `asmModel.getName()` → see `Asm2KeycloakTransformationAsmModelTracker.java.AGENTS.md` |
| `Asm2KeycloakTransformationSerivce.java` | OSGi `@Component` wrapper: `install(AsmModel) : KeycloakModel` runs `Asm2KeycloakWork` (ZETA default; ETL needs `-etl` bundle); misspelled name wiring-stable → see `Asm2KeycloakTransformationSerivce.java.AGENTS.md`. See change: split-etl-artifacts. |
