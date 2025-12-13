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

import hu.blackbelt.judo.meta.measure.BaseMeasure;
import hu.blackbelt.judo.meta.measure.Measure;
import hu.blackbelt.judo.meta.measure.MeasureFactory;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.measure.DerivedMeasure;
import hu.blackbelt.judo.meta.psm.measure.DurationUnit;
import hu.blackbelt.judo.meta.psm.measure.MeasureDefinitionTerm;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2measure.zeta.rules.MeasureRules;
import hu.blackbelt.judo.tatami.psm2measure.zeta.rules.UnitRules;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static hu.blackbelt.judo.tatami.psm2measure.zeta.Psm2MeasureRuleNames.*;

/**
 * PSM to Measure transformation orchestrator using Zeta framework.
 * <p>
 * This class orchestrates the transformation by delegating to rule classes:
 * <ul>
 *   <li>{@link MeasureRules} - measure.etl rules (CreateMeasure, CreateBaseMeasure, CreateDerivedMeasure)</li>
 *   <li>{@link UnitRules} - unit.etl rules (CreateUnit, CreateDurationUnit)</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Psm2MeasureZetaTransformation {

    private final PsmModel psmModel;
    private final MeasureModel measureModel;
    private final PsmUtils psmUtils;
    private final ResourceSet psmResourceSet;
    private final MeasureFactory measureFactory;

    // Rule classes
    private final MeasureRules measureRules;
    private final UnitRules unitRules;

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

        // Initialize rule classes with dependencies
        this.measureRules = new MeasureRules(
                measureFactory,
                m -> psmUtils.namespaceToString(m.getNamespace()),
                this::getEquivalent
        );
        this.unitRules = new UnitRules(
                measureFactory,
                this::findEquivalentMeasure
        );
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
    // MEASURE TRANSFORMATIONS (delegated to MeasureRules)
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
        baseMeasure.setNamespace(psmUtils.namespaceToString(psmMeasure.getNamespace()));
        baseMeasure.setName(psmMeasure.getName());
        baseMeasure.setSymbol(psmMeasure.getSymbol());

        measureModel.getResource().getContents().add(baseMeasure);
        addTrace(psmMeasure, CREATE_BASE_MEASURE, baseMeasure);
    }

    private void transformDerivedMeasure(DerivedMeasure psmDerivedMeasure) {
        hu.blackbelt.judo.meta.measure.DerivedMeasure derivedMeasure =
                measureFactory.createDerivedMeasure();
        derivedMeasure.setNamespace(psmUtils.namespaceToString(psmDerivedMeasure.getNamespace()));
        derivedMeasure.setName(psmDerivedMeasure.getName());
        derivedMeasure.setSymbol(psmDerivedMeasure.getSymbol());

        // Get base measures with exponents
        Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> baseMeasures =
                getBaseMeasures(psmDerivedMeasure);

        for (Map.Entry<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> entry : baseMeasures.entrySet()) {
            hu.blackbelt.judo.meta.psm.measure.Measure psmBaseMeasure = entry.getKey();
            Integer exponent = entry.getValue();

            hu.blackbelt.judo.meta.measure.BaseMeasureTerm term = measureFactory.createBaseMeasureTerm();
            term.setExponent(exponent);

            EObject equivalent = getEquivalent(psmBaseMeasure, CREATE_BASE_MEASURE);
            if (equivalent instanceof BaseMeasure) {
                term.setBaseMeasure((BaseMeasure) equivalent);
            }

            derivedMeasure.getTerms().add(term);
        }

        measureModel.getResource().getContents().add(derivedMeasure);
        addTrace(psmDerivedMeasure, CREATE_DERIVED_MEASURE, derivedMeasure);
    }

    private Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> getBaseMeasures(DerivedMeasure derivedMeasure) {
        Map<hu.blackbelt.judo.meta.psm.measure.Measure, Integer> result = new HashMap<>();

        for (MeasureDefinitionTerm term : derivedMeasure.getTerms()) {
            hu.blackbelt.judo.meta.psm.measure.Measure termMeasure = term.getUnit().getMeasure();
            int exponent = term.getExponent();

            if (termMeasure instanceof DerivedMeasure) {
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
                result.merge(termMeasure, exponent, (oldVal, newVal) -> {
                    int sum = oldVal + newVal;
                    return sum == 0 ? null : sum;
                });
            }
        }

        return result;
    }

    // =========================================================================
    // UNIT TRANSFORMATIONS (delegated to UnitRules)
    // =========================================================================

    private void transformUnits() {
        log.debug("Transforming units");

        // Transform regular units
        all(hu.blackbelt.judo.meta.psm.measure.Unit.class)
                .filter(u -> !(u instanceof DurationUnit))
                .forEach(this::transformUnit);

        // Transform duration units
        all(DurationUnit.class).forEach(this::transformDurationUnit);
    }

    private void transformUnit(hu.blackbelt.judo.meta.psm.measure.Unit psmUnit) {
        hu.blackbelt.judo.meta.measure.Unit unit = measureFactory.createUnit();
        unit.setName(psmUnit.getName());
        unit.setSymbol(psmUnit.getSymbol());
        unit.setRateDividend(BigDecimal.valueOf(psmUnit.getRateDividend()));
        unit.setRateDivisor(BigDecimal.valueOf(psmUnit.getRateDivisor()));

        Measure targetMeasure = findEquivalentMeasure(psmUnit);
        if (targetMeasure != null) {
            targetMeasure.getUnits().add(unit);
        }

        addTrace(psmUnit, CREATE_UNIT, unit);
    }

    private void transformDurationUnit(DurationUnit psmDurationUnit) {
        hu.blackbelt.judo.meta.measure.DurationUnit durationUnit =
                measureFactory.createDurationUnit();
        durationUnit.setName(psmDurationUnit.getName());
        durationUnit.setSymbol(psmDurationUnit.getSymbol());
        durationUnit.setRateDividend(BigDecimal.valueOf(psmDurationUnit.getRateDividend()));
        durationUnit.setRateDivisor(BigDecimal.valueOf(psmDurationUnit.getRateDivisor()));

        mapDurationType(durationUnit, psmDurationUnit.getUnitType());

        Measure targetMeasure = findEquivalentMeasure(psmDurationUnit);
        if (targetMeasure != null) {
            targetMeasure.getUnits().add(durationUnit);
        }

        addTrace(psmDurationUnit, CREATE_DURATION_UNIT, durationUnit);
    }

    private void mapDurationType(hu.blackbelt.judo.meta.measure.DurationUnit t,
                                  hu.blackbelt.judo.meta.psm.measure.DurationType psmType) {
        switch (psmType) {
            case NANOSECOND:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.NANOSECOND);
                break;
            case MICROSECOND:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.MICROSECOND);
                break;
            case MILLISECOND:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.MILLISECOND);
                break;
            case SECOND:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.SECOND);
                break;
            case MINUTE:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.MINUTE);
                break;
            case HOUR:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.HOUR);
                break;
            case DAY:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.DAY);
                break;
            case WEEK:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.WEEK);
                break;
            case MONTH:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.MONTH);
                break;
            case YEAR:
                t.setType(hu.blackbelt.judo.meta.measure.DurationType.YEAR);
                break;
            default:
                throw new IllegalArgumentException("Missing or unsupported unit type: " + psmType);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Measure findEquivalentMeasure(hu.blackbelt.judo.meta.psm.measure.Unit unit) {
        hu.blackbelt.judo.meta.psm.measure.Measure psmMeasure = all(hu.blackbelt.judo.meta.psm.measure.Measure.class)
                .filter(m -> m.getUnits().contains(unit))
                .findFirst()
                .orElse(null);

        if (psmMeasure != null) {
            if (psmMeasure instanceof DerivedMeasure) {
                return (Measure) getEquivalent(psmMeasure, CREATE_DERIVED_MEASURE);
            } else {
                return (Measure) getEquivalent(psmMeasure, CREATE_BASE_MEASURE);
            }
        }
        return null;
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
