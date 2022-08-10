package hu.blackbelt.judo.tatami.asm2expression;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.expression.builder.jql.JqlExpressionBuilderConfig;
import hu.blackbelt.judo.meta.expression.builder.jql.JqlExtractor;
import hu.blackbelt.judo.meta.expression.builder.jql.asm.AsmJqlExtractor;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.measure.support.MeasureModelResourceSupport;
import lombok.Builder;
import lombok.NonNull;
import org.eclipse.emf.ecore.resource.ResourceSet;

public class Asm2Expression {

    @Builder(builderMethodName = "asm2ExpressionParameter")
    public static class Asm2ExpressionParameter {
        @NonNull
        AsmModel asmModel;
        MeasureModel measureModel;
        @NonNull
        ExpressionModel expressionModel;
        @Builder.Default
        Asm2ExpressionConfiguration config = new Asm2ExpressionConfiguration();
    }

    public static void executeAsm2Expression(Asm2ExpressionParameter.Asm2ExpressionParameterBuilder builder) {
        executeAsm2Expression(builder.build());
    }

    public static void executeAsm2Expression(Asm2ExpressionParameter parameter) {
        ResourceSet measureResourceSet;
        if (parameter.measureModel == null) {
            measureResourceSet = MeasureModelResourceSupport.createMeasureResourceSet();
        } else  {
            measureResourceSet = parameter.measureModel.getResourceSet();
        }

        JqlExtractor jqlExtractor = new AsmJqlExtractor(parameter.asmModel.getResourceSet(), measureResourceSet,
                parameter.expressionModel.getResourceSet(), createExpressionBuilderConfig(parameter.config));
        jqlExtractor.extractExpressions();
    }

    private static JqlExpressionBuilderConfig createExpressionBuilderConfig(Asm2ExpressionConfiguration asm2ExpressionConfiguration) {
        JqlExpressionBuilderConfig expressionBuilderConfig = new JqlExpressionBuilderConfig();
        expressionBuilderConfig.setResolveOnlyCurrentLambdaScope(asm2ExpressionConfiguration.isResolveOnlyCurrentLambdaScope());
        return expressionBuilderConfig;
    }
}


