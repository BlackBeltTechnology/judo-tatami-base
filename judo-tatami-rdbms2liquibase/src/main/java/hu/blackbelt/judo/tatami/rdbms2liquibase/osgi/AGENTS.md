# AGENTS.md — `judo-tatami-rdbms2liquibase/src/main/java/hu/blackbelt/judo/tatami/rdbms2liquibase/osgi`

OSGi declarative service component and tracker managing lifecycle of RDBMS to Liquibase transformations.

| File | Purpose |
| --- | --- |
| `BundleEntryURIHandler.java` | Epsilon-free EMF `URIHandler` resolving `liquibase:` bundle-entry URIs; port of epsilon-runtime-osgi `BundleURIHandler`; used by the OSGi service. See change: split-etl-artifacts. |
| `Rdbms2LiquibaseRdbmsModelTracker.java` | OSGi Declarative Service tracker extending `AbstractModelTracker<RdbmsModel>` to track `RdbmsModel` service events. Delegates transformation execution to `Rdbms2LiquibaseTranformationSerivce` and publishes resulting `LiquibaseModel` services. |
| `Rdbms2LiquibaseTranformationSerivce.java` | DS component; `install(RdbmsModel)` runs `Rdbms2LiquibaseWork` for `hsqldb` (ZETA default; ETL needs `-etl` bundle), populates `LiquibaseModel` using `BundleEntryURIHandler`. No direct Epsilon use. See change: split-etl-artifacts. |
