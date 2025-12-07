package hu.blackbelt.judo.tatami.psm2measure.zeta;

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

import hu.blackbelt.judo.meta.measure.*;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.measure.DerivedMeasure;
import hu.blackbelt.judo.meta.psm.measure.DurationUnit;
import hu.blackbelt.judo.meta.psm.measure.MeasureDefinitionTerm;
import hu.blackbelt.judo.meta.psm.measure.Unit;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureRuleNames.*;

/**
 * Java-based PSM to Measure transformation using Zeta framework patterns.
 * <p>
 * This class implements the equivalent transformation logic as the ETL scripts
 * in src/main/epsilon/transformations/measure/, providing a type-safe Java alternative
 * with better IDE support and debugging capabilities.
 * </p>
 */
@Slf4j
public class Psm2MeasureZetaTransformation {

    private final PsmModel psmModel;
    private final MeasureModel measureModel;
    private final PsmUtils psmUtils;
    private final ResourceSet psmResourceSet;
    private final MeasureFactory measureFactory;

    // Trace map for source to target element mapping
    private final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();

    @Builder
    public Psm2MeasureZetaTransformation(
            @NonNull PsmModel psmModel,
            @NonNull MeasureModel measureModel) {
        this.psmModel = psmModel;
        this.measureModel = measureModel;
        this.psmResourceSet = psmModel.getResourceSet();
        this.psmUtils = new PsmUtils(psmResourceSet);
        this.measureFactory = MeasureFactory.eINSTANCE;
    }

    /**
     * Helper method to get all elements of a given type from the PSM model.
     */
    private <T> Stream<T> all(Class<T> clazz) {
        return psmUtils.all(psmResourceSet, clazz);
    }

    /**
     * Execute the transformation.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting PSM to Measure Zeta transformation");
        long startTime = System.currentTimeMillis();

        // Phase 1: Transform measures (base measures first, then derived)
        transformMeasures();

        // Phase 2: Transform units
        transformUnits();

        long duration = System.currentTimeMillis() - startTime;
        log.info("PSM to Measure Zeta transformation completed in {}ms", duration);

        return buildTraceResult();
    }

    // =========================================================================
    // MEASURE TRANSFORMATIONS
    // =========================================================================

    private void transformMeasures() {
        log.debug("Transforming measures");

        // First pass: create base measures
        all(hu.blackbelt.judo.meta.psm.measure.Measure.class)
                .filter(m -> !(m instanceof DerivedMeasure))
                .forEach(this::transformBaseMeasure);

        // Second pass: create derived measures (need base measures to exist first)
        all(DerivedMeasure.class).forEach(this::transformDerivedMeasure);
    }

    private void transformBaseMeasure(hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure) {
        BaseMeasure baseMeasure = measureFactory.createBaseMeasure();
        setId(baseMeasure, "(psm/" + getId(psmMeasure) + ")/BaseMeasure");

        // Set common measure properties
        baseMeasure.setNamespace(psmUtils.namespaceToString(psmMeasure.getNamespace()));
        baseMeasure.setName(psmMeasure.getName());
        baseMeasure.setSymbol(psmMeasure.getSymbol());

        measureModel.getResource().getContents().add(baseMeasure);
        addTrace(psmMeasure, CREATE_BASE_MEASURE, baseMeasure);
    }

    private void transformDerivedMeasure(DerivedMeasure psmDerivedMeasure) {
        hu.blackbelt.judo.meta.measure.DerivedMeasure derivedMeasure = 
                measureFactory.createDerivedMeasure();
        setId(derivedMeasure, "(psm/" + getId(psmDerivedMeasure) + ")/DerivedMeasure");

        // Set common measure properties
        derivedMeasure.setNamespace(psmUtils.namespaceToString(psmDerivedMeasure.getNamespace()));
        derivedMeasure.setName(psmDerivedMeasure.getName());
        derivedMeasure.setSymbol(psmDerivedMeasure.getSymbol());

        // Get base measures and create terms
        Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> baseMeasures = 
                getBaseMeasures(psmDerivedMeasure);

        for (Map.Entry<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> entry : baseMeasures.entrySet()) {
            hu.blackbelt.judo.meta.psm.measure.Measure psmBaseMeasure = entry.getKey();
            Integer exponent = entry.getValue();

            BaseMeasureTerm term = measureFactory.createBaseMeasureTerm();
            setId(term, "(" + getId(derivedMeasure) + ")_((psm/" + getId(psmBaseMeasure) + ")/BaseMeasureTerm)");
            term.setExponent(exponent);

            // Get the equivalent base measure
            EObject equivalent = getEquivalent(psmBaseMeasure, CREATE_BASE_MEASURE);
            if (equivalent instanceof BaseMeasure) {
                term.setBaseMeasure((BaseMeasure) equivalent);
            }

            derivedMeasure.getTerms().add(term);
            addTrace(psmBaseMeasure, CREATE_BASE_MEASURE_TERM, term);
        }

        measureModel.getResource().getContents().add(derivedMeasure);
        addTrace(psmDerivedMeasure, CREATE_DERIVED_MEASURE, derivedMeasure);
    }

    /**
     * Compute base measures for a derived measure by recursively resolving.
     * This mirrors the getBaseMeasures() operation in the ETL.
     */
    private Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> getBaseMeasures(
            DerivedMeasure derivedMeasure) {
        Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> result = new HashMap<>();

        for (MeasureDefinitionTerm term : derivedMeasure.getTerms()) {
            // Get the measure from the term's unit
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
                        return sum == 0 ? null : sum; // Remove if exponent becomes 0
                    });
                }
            } else {
                // Base measure - add directly
                result.merge(termMeasure, exponent, (oldVal, newVal) -> {
                    int sum = oldVal + newVal;
                    return sum == 0 ? null : sum; // Remove if exponent becomes 0
                });
            }
        }

        return result;
    }

    // =========================================================================
    // UNIT TRANSFORMATIONS
    // =========================================================================

    private void transformUnits() {
        log.debug("Transforming units");

        // Transform regular units
        all(Unit.class)
                .filter(u -> !(u instanceof DurationUnit))
                .forEach(this::transformUnit);

        // Transform duration units
        all(DurationUnit.class).forEach(this::transformDurationUnit);
    }

    private void transformUnit(Unit psmUnit) {
        hu.blackbelt.judo.meta.measure.Unit unit = measureFactory.createUnit();
        setId(unit, "(psm/" + getId(psmUnit) + ")/Unit");

        unit.setName(psmUnit.getName());
        unit.setSymbol(psmUnit.getSymbol());
        unit.setRateDividend(BigDecimal.valueOf(psmUnit.getRateDividend()));
        unit.setRateDivisor(BigDecimal.valueOf(psmUnit.getRateDivisor()));

        // Find the parent measure and add the unit to it
        hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure = findParentMeasure(psmUnit);
        if (psmMeasure != null) {
            Measure targetMeasure = (Measure) getEquivalentMeasure(psmMeasure);
            if (targetMeasure != null) {
                targetMeasure.getUnits().add(unit);
            }
        }

        addTrace(psmUnit, CREATE_UNIT, unit);
    }

    private void transformDurationUnit(DurationUnit psmDurationUnit) {
        hu.blackbelt.judo.meta.measure.DurationUnit durationUnit = 
                measureFactory.createDurationUnit();

        // Set base unit properties
        durationUnit.setName(psmDurationUnit.getName());
        durationUnit.setSymbol(psmDurationUnit.getSymbol());
        durationUnit.setRateDividend(BigDecimal.valueOf(psmDurationUnit.getRateDividend()));
        durationUnit.setRateDivisor(BigDecimal.valueOf(psmDurationUnit.getRateDivisor()));

        // Map duration type
        String idBase = "(psm/" + getId(psmDurationUnit) + ")/DurationUnit";
        hu.blackbelt.judo.meta.psm.measure.DurationType psmType = psmDurationUnit.getUnitType();

        switch (psmType) {
            case NANOSECOND:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.NANOSECOND);
                setId(durationUnit, idBase + "Nanosecond");
                break;
            case MICROSECOND:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.MICROSECOND);
                setId(durationUnit, idBase + "Microsecond");
                break;
            case MILLISECOND:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.MILLISECOND);
                setId(durationUnit, idBase + "Millisecond");
                break;
            case SECOND:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.SECOND);
                setId(durationUnit, idBase + "Second");
                break;
            case MINUTE:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.MINUTE);
                setId(durationUnit, idBase + "Minute");
                break;
            case HOUR:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.HOUR);
                setId(durationUnit, idBase + "Hour");
                break;
            case DAY:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.DAY);
                setId(durationUnit, idBase + "Day");
                break;
            case WEEK:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.WEEK);
                setId(durationUnit, idBase + "Week");
                break;
            case MONTH:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.MONTH);
                setId(durationUnit, idBase + "Month");
                break;
            case YEAR:
                durationUnit.setType(hu.blackbelt.judo.meta.measure.DurationType.YEAR);
                setId(durationUnit, idBase + "Year");
                break;
            default:
                throw new IllegalArgumentException("Missing or unsupported unit type: " + psmType);
        }

        // Find the parent measure and add the unit to it
        hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure = findParentMeasure(psmDurationUnit);
        if (psmMeasure != null) {
            Measure targetMeasure = (Measure) getEquivalentMeasure(psmMeasure);
            if (targetMeasure != null) {
                targetMeasure.getUnits().add(durationUnit);
            }
        }

        addTrace(psmDurationUnit, CREATE_DURATION_UNIT, durationUnit);
    }

    private hu.blackbelt.judo.meta.psm.measure.Measure findParentMeasure(Unit unit) {
        return all(hu.blackbelt.judo.meta.psm.measure.Measure.class)
                .filter(m -> m.getUnits().contains(unit))
                .findFirst()
                .orElse(null);
    }

    private EObject getEquivalentMeasure(hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure) {
        if (psmMeasure instanceof DerivedMeasure) {
            return getEquivalent(psmMeasure, CREATE_DERIVED_MEASURE);
        } else {
            return getEquivalent(psmMeasure, CREATE_BASE_MEASURE);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private String getId(EObject element) {
        if (element instanceof hu.blackbelt.judo.meta.psm.namespace.NamespaceElement) {
            hu.blackbelt.judo.meta.psm.namespace.NamespaceElement named = 
                    (hu.blackbelt.judo.meta.psm.namespace.NamespaceElement) element;
            return psmUtils.namespaceElementToString(named).replace("::", "_");
        }
        // Fall back to hash code if no ID annotation
        return String.valueOf(System.identityHashCode(element));
    }

    private void setId(EObject element, String id) {
        // For measure model elements, we might need to set ID differently
        // The measure metamodel might have its own ID mechanism
        // For now, we rely on the tracing mechanism
    }

    private void addTrace(EObject source, String ruleName, EObject target) {
        traceMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
                .put(ruleName, target);
    }

    private EObject getEquivalent(EObject source, String ruleName) {
        Map<String, EObject> rules = traceMap.get(source);
        return rules != null ? rules.get(ruleName) : null;
    }

    private Map<EObject, List<EObject>> buildTraceResult() {
        Map<EObject, List<EObject>> result = new HashMap<>();
        for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }
}
