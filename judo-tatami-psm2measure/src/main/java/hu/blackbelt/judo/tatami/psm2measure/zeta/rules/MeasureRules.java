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
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.measure.DerivedMeasure;
import hu.blackbelt.judo.meta.psm.measure.MeasureDefinitionTerm;
import hu.blackbelt.judo.zeta.annotation.Guard;
import hu.blackbelt.judo.zeta.annotation.To;
import hu.blackbelt.judo.zeta.annotation.Transform;
import hu.blackbelt.judo.zeta.annotation.TransformRule;
import hu.blackbelt.judo.zeta.transformation.core.TransformFunction;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.ecore.xmi.XMLResource;

import static hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureRuleNames.*;

/**
 * Measure transformation rules from measure.etl.
 * <p>
 * Rules:
 * <ul>
 *   <li>CreateBaseMeasure - transforms non-derived measures to BaseMeasure</li>
 *   <li>CreateDerivedMeasure - transforms derived measures with terms</li>
 * </ul>
 */
@hu.blackbelt.judo.zeta.annotation.TransformationContext(source = hu.blackbelt.judo.meta.psm.measure.Measure.class, target = Measure.class)
public class MeasureRules {

    /**
     * Default constructor required for TransformationRegistry.
     */
    public MeasureRules() {
    }

    // =========================================================================
    // GUARDS
    // =========================================================================

    /**
     * Guard for CreateBaseMeasure: not s.isKindOf(JUDOPSM!DerivedMeasure)
     */
    public boolean isNotDerivedMeasure(EObject source, TransformationContext ctx) {
        return !(source instanceof DerivedMeasure);
    }

    // =========================================================================
    // TRANSFORMATION RULES
    // =========================================================================

    /**
     * rule CreateBaseMeasure
     *     transform s : JUDOPSM!Measure
     *     to t : MEASURES!BaseMeasure
     *     guard: not s.isKindOf(JUDOPSM!DerivedMeasure)
     */
    @TransformRule(name = BASE_MEASURE, description = "Transform non-derived PSM Measure to BaseMeasure")
    @Guard(method = "isNotDerivedMeasure")
    @Transform(type = hu.blackbelt.judo.meta.psm.measure.Measure.class)
    @To(type = BaseMeasure.class)
    public TransformFunction<hu.blackbelt.judo.meta.psm.measure.Measure, BaseMeasure> createBaseMeasure() {
        return (s, ctx) -> {
            BaseMeasure t = ctx.createTarget(BaseMeasure.class);
            PsmUtils psmUtils = (PsmUtils) ctx.getAttribute("psmUtils");
            t.setNamespace(psmUtils.namespaceToString(s.getNamespace()));
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());

            // Store XMI ID to match ETL format: (psm/<sourceId>)/BaseMeasure
            String sourceId = getSourceXmiId(s);
            if (sourceId != null) {
                String xmiId = "(psm/" + sourceId + ")/" + BASE_MEASURE;
                storeCustomXmiId(ctx, t, xmiId);
            }

            // Add to target resource
            Resource targetResource = (Resource) ctx.getAttribute("measureResource");
            if (targetResource != null) {
                targetResource.getContents().add(t);
            }

            return t;
        };
    }

    /**
     * rule CreateDerivedMeasure
     *     transform s : JUDOPSM!DerivedMeasure
     *     to t : MEASURES!DerivedMeasure
     */
    @TransformRule(name = DERIVED_MEASURE, description = "Transform DerivedMeasure with base measure terms")
    @Transform(type = DerivedMeasure.class)
    @To(type = hu.blackbelt.judo.meta.measure.DerivedMeasure.class)
    public TransformFunction<DerivedMeasure, hu.blackbelt.judo.meta.measure.DerivedMeasure> createDerivedMeasure() {
        return (s, ctx) -> {
            hu.blackbelt.judo.meta.measure.DerivedMeasure t = ctx.createTarget(hu.blackbelt.judo.meta.measure.DerivedMeasure.class);
            PsmUtils psmUtils = (PsmUtils) ctx.getAttribute("psmUtils");
            t.setNamespace(psmUtils.namespaceToString(s.getNamespace()));
            t.setName(s.getName());
            t.setSymbol(s.getSymbol());

            // Store XMI ID to match ETL format: (psm/<sourceId>)/DerivedMeasure
            String sourceId = getSourceXmiId(s);
            String derivedMeasureId = null;
            if (sourceId != null) {
                derivedMeasureId = "(psm/" + sourceId + ")/" + DERIVED_MEASURE;
                storeCustomXmiId(ctx, t, derivedMeasureId);
            }

            // Get base measures with exponents
            Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> baseMeasures = getBaseMeasures(s);

            for (Map.Entry<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> entry : baseMeasures.entrySet()) {
                hu.blackbelt.judo.meta.psm.measure.Measure m = entry.getKey();
                Integer exponent = entry.getValue();

                BaseMeasureTerm term = ctx.createTarget(BaseMeasureTerm.class);
                term.setExponent(exponent);

                // Get equivalent base measure from context
                BaseMeasure equivalentMeasure = ctx.equivalent(m, BaseMeasure.class);
                if (equivalentMeasure != null) {
                    term.setBaseMeasure(equivalentMeasure);
                }

                // Store custom XMI ID for BaseMeasureTerm to match ETL format
                // Format: (<derivedMeasureId>)_((psm/<sourceBaseMeasureId>)/BaseMeasureTerm)
                String sourceBaseMeasureId = getSourceXmiId(m);
                if (derivedMeasureId != null && sourceBaseMeasureId != null) {
                    String termId = "(" + derivedMeasureId + ")_((psm/" + sourceBaseMeasureId + ")/" + BASE_MEASURE_TERM + ")";
                    storeCustomXmiId(ctx, term, termId);
                }

                t.getTerms().add(term);
            }

            // Add to target resource
            Resource targetResource = (Resource) ctx.getAttribute("measureResource");
            if (targetResource != null) {
                targetResource.getContents().add(t);
            }

            return t;
        };
    }

    /**
     * Gets the XMI ID of a source PSM element from its resource.
     */
    private String getSourceXmiId(EObject source) {
        if (source.eResource() instanceof XMLResource) {
            return ((XMLResource) source.eResource()).getID(source);
        }
        return null;
    }

    /**
     * Stores a custom XMI ID for an element in the context's customXmiIds map.
     * This ID will be applied in post-processing.
     */
    @SuppressWarnings("unchecked")
    private void storeCustomXmiId(TransformationContext ctx, EObject element, String xmiId) {
        Map<EObject, String> customXmiIds = (Map<EObject, String>) ctx.getAttribute("customXmiIds");
        if (customXmiIds == null) {
            customXmiIds = new HashMap<>();
            ctx.setAttribute("customXmiIds", customXmiIds);
        }
        customXmiIds.put(element, xmiId);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

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
