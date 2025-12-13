package hu.blackbelt.judo.tatami.psm2measure.zeta.rules;

/*-
 * #%L
 * JUDO Tatami parent
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

import hu.blackbelt.judo.meta.measure.BaseMeasure;
import hu.blackbelt.judo.meta.measure.BaseMeasureTerm;
import hu.blackbelt.judo.meta.measure.Measure;
import hu.blackbelt.judo.meta.measure.MeasureFactory;
import hu.blackbelt.judo.meta.psm.measure.DerivedMeasure;
import hu.blackbelt.judo.meta.psm.measure.MeasureDefinitionTerm;
import hu.blackbelt.judo.zeta.annotation.Abstract;
import hu.blackbelt.judo.zeta.annotation.Extends;
import hu.blackbelt.judo.zeta.annotation.Guard;
import hu.blackbelt.judo.zeta.annotation.To;
import hu.blackbelt.judo.zeta.annotation.Transform;
import hu.blackbelt.judo.zeta.annotation.TransformRule;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EObject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureRuleNames.*;

/**
 * Measure transformation rules from measure.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateMeasure - @Abstract base rule for all measures</li>
 *   <li>CreateBaseMeasure - @Extends CreateMeasure, transforms non-derived measures</li>
 *   <li>CreateDerivedMeasure - @Extends CreateMeasure, transforms derived measures with terms</li>
 * </ul>
 */
@RequiredArgsConstructor
public class MeasureRules {

    private final MeasureFactory measureFactory;
    private final Function<hu.blackbelt.judo.meta.psm.measure.Measure, String> namespaceResolver;
    private final BiFunction<EObject, String, EObject> traceResolver;

    /**
     * @abstract
     * rule CreateMeasure
     *     transform s : JUDOPSM!Measure
     *     to t : MEASURES!Measure
     */
    @TransformRule(name = CREATE_MEASURE, description = "Abstract base rule for measure transformation")
    @Abstract
    @Transform(type = hu.blackbelt.judo.meta.psm.measure.Measure.class)
    @To(type = Measure.class)
    public TransformFunction<hu.blackbelt.judo.meta.psm.measure.Measure, Measure> createMeasure() {
        return (s, ctx) -> {
            Measure t = ctx.createTarget(Measure.class);
            t.setNamespace(namespaceResolver.apply(s));
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());
            return t;
        };
    }

    /**
     * Guard for CreateBaseMeasure: not s.isKindOf(JUDOPSM!DerivedMeasure)
     */
    public boolean isNotDerivedMeasure(hu.blackbelt.judo.meta.psm.measure.Measure measure) {
        return !(measure instanceof DerivedMeasure);
    }

    /**
     * rule CreateBaseMeasure
     *     transform s : JUDOPSM!Measure
     *     to t : MEASURES!BaseMeasure
     *     extends CreateMeasure {
     *         guard: not s.isKindOf(JUDOPSM!DerivedMeasure)
     *     }
     */
    @TransformRule(name = CREATE_BASE_MEASURE, description = "Transform non-derived PSM Measure to BaseMeasure")
    @Extends(CREATE_MEASURE)
    @Guard(method = "isNotDerivedMeasure")
    @Transform(type = hu.blackbelt.judo.meta.psm.measure.Measure.class)
    @To(type = BaseMeasure.class)
    public TransformFunction<hu.blackbelt.judo.meta.psm.measure.Measure, BaseMeasure> createBaseMeasure() {
        return (s, ctx) -> {
            BaseMeasure t = measureFactory.createBaseMeasure();
            t.setNamespace(namespaceResolver.apply(s));
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());
            return t;
        };
    }

    /**
     * rule CreateDerivedMeasure
     *     transform s : JUDOPSM!DerivedMeasure
     *     to t : MEASURES!DerivedMeasure
     *     extends CreateMeasure
     */
    @TransformRule(name = CREATE_DERIVED_MEASURE, description = "Transform DerivedMeasure with base measure terms")
    @Extends(CREATE_MEASURE)
    @Transform(type = DerivedMeasure.class)
    @To(type = hu.blackbelt.judo.meta.measure.DerivedMeasure.class)
    @To(type = BaseMeasureTerm.class)
    public TransformFunction<DerivedMeasure, hu.blackbelt.judo.meta.measure.DerivedMeasure> createDerivedMeasure() {
        return (s, ctx) -> {
            hu.blackbelt.judo.meta.measure.DerivedMeasure t = measureFactory.createDerivedMeasure();
            t.setNamespace(namespaceResolver.apply(s));
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());

            // Get base measures with exponents
            Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> baseMeasures = getBaseMeasures(s);

            for (Map.Entry<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> entry : baseMeasures.entrySet()) {
                hu.blackbelt.judo.meta.psm.measure.Measure m = entry.getKey();
                Integer exponent = entry.getValue();

                BaseMeasureTerm term = measureFactory.createBaseMeasureTerm();
                term.setExponent(exponent);
                term.setBaseMeasure((BaseMeasure) traceResolver.apply(m, CREATE_BASE_MEASURE));

                t.getTerms().add(term);
            }

            return t;
        };
    }

    /**
     * Compute base measures for a derived measure by recursively resolving.
     */
    private Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> getBaseMeasures(DerivedMeasure derivedMeasure) {
        Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> result = new HashMap<>();

        for (MeasureDefinitionTerm term : derivedMeasure.getTerms()) {
            hu.blackbelt.judo.meta.psm.measure.Measure termMeasure = term.getUnit().getMeasure();
            int exponent = term.getExponent();

            if (termMeasure instanceof DerivedMeasure) {
                // Recursively resolve derived measures
                Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> nestedBaseMeasures =
                        getBaseMeasures((DerivedMeasure) termMeasure);
                for (Map.Entry<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> entry :
                        nestedBaseMeasures.entrySet()) {
                    int newExponent = entry.getValue() * exponent;
                    result.merge(entry.getKey(), newExponent, (oldVal, newVal) -> {
                        int sum = oldVal + newVal;
                        return sum == 0 ? null : sum;
                    });
                }
            } else {
                // Base measure - add directly
                result.merge(termMeasure, exponent, (oldVal, newVal) -> {
                    int sum = oldVal + newVal;
                    return sum == 0 ? null : sum;
                });
            }
        }

        return result;
    }
}
