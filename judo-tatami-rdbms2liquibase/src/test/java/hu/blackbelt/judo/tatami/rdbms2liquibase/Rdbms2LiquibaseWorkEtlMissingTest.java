package hu.blackbelt.judo.tatami.rdbms2liquibase;

/*-
 * #%L
 * JUDO Tatami :: split-etl-artifacts
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
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


import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineMissingException;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec "ETL add-on absent": without judo-tatami-rdbms2liquibase-etl on the classpath, ETL mode fails fast and names the
 * add-on artifact (this module's test classpath deliberately has no own -etl artifact). See change: split-etl-artifacts.
 */
class Rdbms2LiquibaseWorkEtlMissingTest {

    @Test
    void etlModeWithoutAddOnFailsNamingTheArtifact() {
        TransformationContext context = new TransformationContext("test");
        context.put("rdbms:hsqldb", hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel().build());
        context.put(hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel().name("test").build());
        context.put(Rdbms2LiquibaseWork.Rdbms2LiquibaseWorkParameter.rdbms2LiquibaseWorkParameter().transformationMode(TransformationMode.ETL).build());

        EtlEngineMissingException e = assertThrows(EtlEngineMissingException.class, () -> new Rdbms2LiquibaseWork(context, "hsqldb").execute());

        assertEquals(Rdbms2LiquibaseEtlEngine.class, e.getEngineType());
        assertTrue(e.getMessage().contains("hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl"), e.getMessage());
    }
}
