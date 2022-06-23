package hu.blackbelt.judo.tatami.asm2expression;

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


