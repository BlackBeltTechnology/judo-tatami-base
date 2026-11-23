package hu.blackbelt.judo.tatami.rdbms2liquibase;

/*-
 * #%L
 * Judo :: Tatami :: Rdbms2Liquibase
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

import hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel;
import hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineMissingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static hu.blackbelt.judo.meta.liquibase.runtime.LiquibaseModel.buildLiquibaseModel;
import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entry point of the incremental transformation in this Epsilon-free artifact (this module's test classpath has no
 * {@code -etl} artifact): argument validation of the Zeta engine and the fail-fast of the ETL mode.
 * See change: rdbms-incremental-without-epsilon (TP-18, TP-19).
 */
class Rdbms2LiquibaseIncrementalTransformationTest {

    private final List<LiquibaseModel> phases = Arrays.asList(
            buildLiquibaseModel().name("DbCheckup").build(),
            buildLiquibaseModel().name("DbBackup").build(),
            buildLiquibaseModel().name("BeforeIncremental").build(),
            buildLiquibaseModel().name("UpdateDataBeforeIncremental").build(),
            buildLiquibaseModel().name("Incremental").build(),
            buildLiquibaseModel().name("UpdateDataAfterIncremental").build(),
            buildLiquibaseModel().name("AfterIncremental").build(),
            buildLiquibaseModel().name("DbDropBackup").build());

    private Rdbms2LiquibaseIncrementalParameter.Rdbms2LiquibaseIncrementalParameterBuilder parameter(
            TransformationMode mode) {
        RdbmsModel incremental = buildRdbmsModel().build();
        return Rdbms2LiquibaseIncrementalParameter.rdbms2LiquibaseIncrementalParameter()
                .incrementalRdbmsModel(incremental)
                .dbCheckupLiquibaseModel(phases.get(0))
                .dbBackupLiquibaseModel(phases.get(1))
                .beforeIncrementalLiquibaseModel(phases.get(2))
                .updateDataBeforeIncrementalLiquibaseModel(phases.get(3))
                .incrementalLiquibaseModel(phases.get(4))
                .updateDataAfterIncrementalLiquibaseModel(phases.get(5))
                .afterIncrementalLiquibaseModel(phases.get(6))
                .dbDropBackupLiquibaseModel(phases.get(7))
                .dialect("hsqldb")
                .transformationMode(mode);
    }

    @Test
    void zetaWithoutReviewResolverAndScriptPathFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                Rdbms2LiquibaseIncrementalTransformation.executeRdbms2LiquibaseIncrementalTransformation(
                        parameter(TransformationMode.ZETA)));

        assertEquals("One of ReviewResolver or scriptPath have to be set", e.getMessage());
    }

    @Test
    void zetaIsTheDefaultMode() {
        // no mode given: the Zeta engine runs (and rejects the missing review script source, not a missing add-on)
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                Rdbms2LiquibaseIncrementalTransformation.executeRdbms2LiquibaseIncrementalTransformation(
                        Rdbms2LiquibaseIncrementalParameter.rdbms2LiquibaseIncrementalParameter()
                                .incrementalRdbmsModel(buildRdbmsModel().build())
                                .dbCheckupLiquibaseModel(phases.get(0))
                                .dbBackupLiquibaseModel(phases.get(1))
                                .beforeIncrementalLiquibaseModel(phases.get(2))
                                .updateDataBeforeIncrementalLiquibaseModel(phases.get(3))
                                .incrementalLiquibaseModel(phases.get(4))
                                .updateDataAfterIncrementalLiquibaseModel(phases.get(5))
                                .afterIncrementalLiquibaseModel(phases.get(6))
                                .dbDropBackupLiquibaseModel(phases.get(7))
                                .dialect("hsqldb")));

        assertEquals("One of ReviewResolver or scriptPath have to be set", e.getMessage());
    }

    @Test
    void etlModeWithoutAddOnFailsNamingTheArtifactBeforeTheStage() {
        EtlEngineMissingException e = assertThrows(EtlEngineMissingException.class, () ->
                Rdbms2LiquibaseIncrementalTransformation.executeRdbms2LiquibaseIncrementalTransformation(
                        parameter(TransformationMode.ETL).sqlScriptPath("src/test/resources/review")));

        assertEquals(Rdbms2LiquibaseIncrementalEtlEngine.class, e.getEngineType());
        assertTrue(e.getMessage().contains("hu.blackbelt.judo.tatami:judo-tatami-rdbms2liquibase-etl"), e.getMessage());
        // nothing was transformed
        phases.forEach(model -> assertTrue(model.getResource().getContents().isEmpty(), model.getName()));
    }
}
