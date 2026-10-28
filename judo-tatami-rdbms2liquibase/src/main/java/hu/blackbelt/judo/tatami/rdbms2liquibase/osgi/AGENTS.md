# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase/osgi`

OSGi declarative service component and tracker managing lifecycle of RDBMS to Liquibase transformations.

| File | Purpose |
| --- | --- |
| `Rdbms2LiquibaseRdbmsModelTracker.java` | OSGi Declarative Service tracker extending `AbstractModelTracker<RdbmsModel>` to track `RdbmsModel` service events. Delegates transformation execution to `Rdbms2LiquibaseTranformationSerivce` and publishes resulting `LiquibaseModel` services. |
| `Rdbms2LiquibaseTranformationSerivce.java` | OSGi Declarative Service component running `rdbmsToLiquibase.etl` transformations. Exports `install(RdbmsModel)` creating and populating `LiquibaseModel` with bundle URI handlers and namespace fixers. |
