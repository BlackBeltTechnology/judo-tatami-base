package hu.blackbelt.judo.tatami.rdbms2liquibase;

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

import hu.blackbelt.judo.tatami.core.workflow.flow.WorkFlow;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import hu.blackbelt.judo.tatami.core.workflow.work.Work;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkReport;
import hu.blackbelt.judo.tatami.core.workflow.work.WorkStatus;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel.buildRdbmsModel;
import static hu.blackbelt.judo.tatami.core.workflow.engine.WorkFlowEngineBuilder.aNewWorkFlowEngine;
import static hu.blackbelt.judo.tatami.core.workflow.flow.SequentialFlow.Builder.aNewSequentialFlow;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Rdbms2LiquibaseIncrementalWorkTest {

    private static final Set<String> DIALECTS = new HashSet<>(asList("hsqldb", "postgres", "oracle"));

    @Test
    public void testSimpleWorkflow() {
        final TransformationContext transformationContext = new TransformationContext("M");
        DIALECTS.forEach(dialect -> transformationContext.put("rdbms-incremental:" + dialect, buildRdbmsModel().build()));

        final Work[] works = DIALECTS.stream().map(d -> new Rdbms2LiquibaseIncrementalWork(transformationContext, d)).toArray(Work[]::new);
        final WorkFlow workFlow = aNewSequentialFlow().execute(works).build();
        final WorkReport workReport = aNewWorkFlowEngine().build().run(workFlow);

        assertEquals(WorkStatus.COMPLETED, workReport.getStatus());
    }

}
