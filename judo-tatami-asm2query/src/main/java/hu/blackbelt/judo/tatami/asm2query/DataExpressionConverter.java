package hu.blackbelt.judo.tatami.asm2query;

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

import hu.blackbelt.judo.meta.expression.DataExpression;
import hu.blackbelt.judo.meta.expression.Expression;
import hu.blackbelt.judo.meta.expression.adapters.asm.AsmMeasureProvider;
import hu.blackbelt.judo.meta.expression.adapters.asm.AsmModelAdapter;
import hu.blackbelt.judo.meta.expression.builder.jql.JqlExpressionBuilder;
import hu.blackbelt.judo.meta.measure.support.MeasureModelResourceSupport;
import hu.blackbelt.judo.meta.query.Feature;
import hu.blackbelt.judo.meta.query.FeatureTargetMapping;
import hu.blackbelt.judo.meta.query.Target;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.resource.ResourceSet;

import static hu.blackbelt.judo.meta.query.util.builder.QueryBuilders.newFeatureTargetMappingBuilder;

/**
 * Converts expressions to query features: wires {@link JoinFactory}, {@link FeatureFactory} and the ASM model adapter.
 * Used by the query builder ({@link Asm2Query}) and by the runtime for expressions that are converted per request, no
 * matter whether the query model was built or restored.
 */
@Slf4j
public class DataExpressionConverter {

    @Getter
    private final AsmModelAdapter modelAdapter;

    @Getter
    private final JoinFactory joinFactory;

    @Getter
    private final FeatureFactory featureFactory;

    public DataExpressionConverter(@NonNull final ResourceSet asmResourceSet, final ResourceSet measureResourceSet) {
        final ResourceSet measures = measureResourceSet != null ? measureResourceSet : MeasureModelResourceSupport.createMeasureResourceSet();
        final AsmMeasureProvider measureProvider = new AsmMeasureProvider(measures);

        modelAdapter = new AsmModelAdapter(asmResourceSet, measures);

        final FeatureFactory[] featureFactoryHolder = new FeatureFactory[1];
        joinFactory = new JoinFactory(modelAdapter) {
            @Override
            public Feature expressionToFeature(final Expression expression, final Context context, final FeatureTargetMapping targetMapping) {
                return featureFactoryHolder[0].convert(expression, context, targetMapping);
            }
        };
        featureFactory = new FeatureFactory(joinFactory, modelAdapter, measureProvider);
        featureFactoryHolder[0] = featureFactory;
        joinFactory.setFeatureFactory(featureFactory);
    }

    public Feature dataExpressionToFeature(final DataExpression dataExpression, final Context context, final Target target, final EAttribute targetAttribute) {
        if (log.isTraceEnabled()) {
            log.trace(pad(context.getVariables().size()) + "- converting expression {} of {} to feature {} of {}", new Object[]{dataExpression, context.getVariables().get(JqlExpressionBuilder.SELF_NAME).getAlias(), targetAttribute != null ? targetAttribute.getName() : "undefined", target != null ? target : "undefined"});
        }
        return featureFactory.convert(dataExpression, context, target != null && targetAttribute != null ? newFeatureTargetMappingBuilder()
                .withTarget(target)
                .withTargetAttribute(targetAttribute)
                .build() : null);
    }

    private static String pad(int level) {
        return StringUtils.leftPad("", level * 2, " ");
    }
}
