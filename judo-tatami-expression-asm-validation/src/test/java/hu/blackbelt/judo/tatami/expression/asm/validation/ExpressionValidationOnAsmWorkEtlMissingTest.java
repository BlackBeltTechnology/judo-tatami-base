package hu.blackbelt.judo.tatami.expression.asm.validation;

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

import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.core.etl.EtlEngineMissingException;
import hu.blackbelt.judo.tatami.core.workflow.work.TransformationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ETL mode without the judo-tatami-expression-asm-validation-etl add-on fails fast, naming the artifact. See change: split-etl-artifacts. */
class ExpressionValidationOnAsmWorkEtlMissingTest {

    @Test
    void etlModeWithoutAddOnFailsNamingTheArtifact() {
        TransformationContext context = new TransformationContext("test");
        context.put(hu.blackbelt.judo.meta.expression.runtime.ExpressionModel.buildExpressionModel().name("test").build());
        context.put(hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel().name("test").build());
        context.put(hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel().build());
        context.put(ExpressionValidationOnAsmWork.ExpressionValidationOnAsmWorkParameter.expressionValidationOnAsmWorkParameter().transformationMode(TransformationMode.ETL).build());

        EtlEngineMissingException e = assertThrows(EtlEngineMissingException.class,
                () -> new ExpressionValidationOnAsmWork(context).execute());
        assertEquals(ExpressionValidationOnAsmEtlEngine.class, e.getEngineType());
        assertTrue(e.getMessage().contains("hu.blackbelt.judo.tatami:judo-tatami-expression-asm-validation-etl"), e.getMessage());
    }

    @Test
    void zetaModeRunsWithoutAddOn() {
        TransformationContext context = new TransformationContext("test");
        context.put(hu.blackbelt.judo.meta.expression.runtime.ExpressionModel.buildExpressionModel().name("test").build());
        context.put(hu.blackbelt.judo.meta.measure.runtime.MeasureModel.buildMeasureModel().name("test").build());
        context.put(hu.blackbelt.judo.meta.asm.runtime.AsmModel.buildAsmModel().build());
        context.put(ExpressionValidationOnAsmWork.ExpressionValidationOnAsmWorkParameter.expressionValidationOnAsmWorkParameter().transformationMode(TransformationMode.ZETA).build());

        assertDoesNotThrow(() -> new ExpressionValidationOnAsmWork(context).execute());
    }
}
