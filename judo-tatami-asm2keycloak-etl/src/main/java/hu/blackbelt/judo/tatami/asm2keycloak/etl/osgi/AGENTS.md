# judo-tatami-asm2keycloak-etl/src/main/java/hu/blackbelt/judo/tatami/asm2keycloak/etl/osgi
OSGi wiring of the ETL add-on.
| File | Purpose |
|---|---|
| `Asm2KeycloakEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` calls `EtlEngineRegistry.register(Asm2KeycloakEtlEngine.class, new Asm2KeycloakEtlEngineImpl())`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
