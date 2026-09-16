# AGENTS.md — `judo-tatami-rdbms2liquibase/src/test/java/hu/blackbelt/judo/tatami/rdbms2liquibase/datasource`

Test datasource fixtures and testcontainers extensions supporting HSQLDB, PostgreSQL, and YugabyteDB integration tests.

| File | Purpose |
| --- | --- |
| `RdbmsDatasourceByClassExtension.java` | JUnit 5 extension implementing `BeforeAllCallback`, `AfterAllCallback`, and `ParameterResolver` to manage lifecycle and injection of `RdbmsDatasourceFixture`. |
| `RdbmsDatasourceFixture.java` | Manages test `DataSource` and Liquibase `Database` instances across HSQLDB (in-memory) and containerized PostgreSQL / YugabyteDB dialects based on system properties. |
| `YugabytedbSQLContainer.java` | Custom Testcontainers `JdbcDatabaseContainer` for `yugabytedb/yugabyte` image configuring port 5433, credentials, and wait strategies. |
