# judo-tatami-rdbms2liquibase-etl/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase/etl/osgi
OSGi wiring of the ETL add-on.
| File | Purpose |
|---|---|
| `Rdbms2LiquibaseEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` calls `EtlEngineRegistry.register(Rdbms2LiquibaseEtlEngine.class, new Rdbms2LiquibaseEtlEngineImpl())`, `deactivate()` unregisters. Makes the engine visible to core bundle in Karaf. See change: split-etl-artifacts. |
| `Rdbms2LiquibaseIncrementalEtlEngineComponent.java` | DS `@Component(immediate = true)`; `activate()` calls `EtlEngineRegistry.register(Rdbms2LiquibaseIncrementalEtlEngine.class, new Rdbms2LiquibaseIncrementalEtlEngineImpl())`, `deactivate()` unregisters. Makes the incremental engine visible to the core bundle in Karaf. See change: rdbms-incremental-without-epsilon. |
