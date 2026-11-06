# judo-tatami-asm2keycloak-etl — module agent doctrine

ETL add-on for the ASM→Keycloak stage. Epsilon reaches a classpath only through this artifact; core `judo-tatami-asm2keycloak` is Epsilon-free and runs Zeta by default.

## Contents

- `src/main/java/.../asm2keycloak/etl/Asm2Keycloak.java` — ETL entry point, moved from core; package `hu.blackbelt.judo.tatami.asm2keycloak.etl`.
- `src/main/java/.../asm2keycloak/etl/Asm2KeycloakEtlEngineImpl.java` — implements core `Asm2KeycloakEtlEngine` by delegating to `Asm2Keycloak`.
- `src/main/java/.../asm2keycloak/etl/osgi/Asm2KeycloakEtlEngineComponent.java` — DS component; registers the engine in `EtlEngineRegistry` on activate, unregisters on deactivate.
- `src/main/resources/META-INF/services/hu.blackbelt.judo.tatami.asm2keycloak.Asm2KeycloakEtlEngine` — `ServiceLoader` registration.
- `src/main/epsilon/**` — ETL/EOL scripts, moved from core.
- `src/test/java/**` — ETL, dual-engine (ETL vs Zeta) and perf tests, moved from core.

## Discovery

Plain Java and Spring Boot: `ServiceLoader` via `META-INF/services`. Karaf: DS component (`ServiceLoader` does not cross bundle class loaders). Core `Asm2KeycloakWork` resolves the engine only in `ETL`/`DUAL` mode via `EtlEngineRegistry.require(Asm2KeycloakEtlEngine.class, Asm2KeycloakEtlEngine.ARTIFACT_COORDINATES)`; a missing add-on throws `EtlEngineMissingException` naming `hu.blackbelt.judo.tatami:judo-tatami-asm2keycloak-etl`.

## Invariants

- Core never imports `.etl` classes; the dependency points `-etl` → core only.
- Package stays `.etl` (sub-package of core) to avoid an OSGi split package.

Doc discipline: see repo root `../AGENTS.md`. See change: split-etl-artifacts.
