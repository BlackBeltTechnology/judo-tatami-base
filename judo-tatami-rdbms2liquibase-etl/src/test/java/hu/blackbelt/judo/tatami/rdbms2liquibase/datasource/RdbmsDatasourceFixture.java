package hu.blackbelt.judo.tatami.rdbms2liquibase.datasource;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */


import liquibase.database.Database;
import liquibase.database.core.HsqlDatabase;
import liquibase.database.core.PostgresDatabase;
import liquibase.database.jvm.HsqlConnection;
import liquibase.database.jvm.JdbcConnection;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hsqldb.jdbc.JDBCDataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;

@Slf4j
public class RdbmsDatasourceFixture {

    public static final String CONTAINER_NONE = "none";
    public static final String CONTAINER_POSTGRESQL = "postgresql";
    public static final String CONTAINER_YUGABYTEDB = "yugabytedb";
    public static final String DIALECT_HSQLDB = "hsqldb";
    public static final String DIALECT_POSTGRESQL = "postgresql";

    @Getter
    protected String dialect = System.getProperty("dialect", DIALECT_HSQLDB);

    @Getter
    protected String container = System.getProperty("container", CONTAINER_NONE);

    @Getter
    protected DataSource dataSource;

    @Getter
    protected DataSource jooqDataSource;

    @Getter
    protected Database liquibaseDb;

    public JdbcDatabaseContainer sqlContainer;

    public void setupDatasource() {
        if (dialect.equals(DIALECT_POSTGRESQL)) {
            if (container.equals(CONTAINER_NONE) || container.equals(CONTAINER_POSTGRESQL)) {
                sqlContainer =
                        (PostgreSQLContainer) new PostgreSQLContainer().withStartupTimeout(Duration.ofSeconds(600));
            } else if (container.equals(CONTAINER_YUGABYTEDB)) {
                sqlContainer =
                        (YugabytedbSQLContainer) new YugabytedbSQLContainer().withStartupTimeout(Duration.ofSeconds(600));
            }
        }
    }

    public void teardownDatasource() throws Exception {
        if (sqlContainer != null && sqlContainer.isRunning()) {
            sqlContainer.stop();
        }
    }


    @SneakyThrows
    public void prepareDatasources() {
        if (dialect.equals(DIALECT_HSQLDB)) {
            JDBCDataSource hsqldbDataSource = new JDBCDataSource();
            hsqldbDataSource.setUrl("jdbc:hsqldb:mem:memdb");
            hsqldbDataSource.setUser("sa");
            hsqldbDataSource.setPassword("saPassword");
            this.dataSource = hsqldbDataSource;
            liquibaseDb = new HsqlDatabase();
        } else if (dialect.equals(DIALECT_POSTGRESQL)) {
            sqlContainer.start();
            PGSimpleDataSource pgSimpleDataSource = new PGSimpleDataSource();
            pgSimpleDataSource.setServerName("test");
            pgSimpleDataSource.setURL(sqlContainer.getJdbcUrl());
            pgSimpleDataSource.setUser(sqlContainer.getUsername());
            pgSimpleDataSource.setPassword(sqlContainer.getPassword());
            dataSource = pgSimpleDataSource;
            liquibaseDb = new PostgresDatabase();
        } else {
            throw new IllegalStateException("Unsupported dialect: " + dialect);
        }

    }

    @SneakyThrows
    public void setLiquibaseDbDialect(Connection connection) {
        if (DIALECT_HSQLDB.equals(dialect)) {
            liquibaseDb.setConnection(new HsqlConnection(connection));
        } else {
            liquibaseDb.setConnection(new JdbcConnection(connection));
        }
        liquibaseDb.setAutoCommit(false);
    }

}
