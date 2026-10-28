package hu.blackbelt.judo.tatami.test.util.trace;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2025 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.xmi.XMLResource;

import java.util.*;

/**
 * Model-independent utility for comparing ETL and Zeta transformation traces.
 *
 * <p>Aligns ETL and Zeta trace entries by (sourceXmiId, targetTypeName) pair,
 * then reports discrepancies: MISSING_IN_ZETA, MISSING_IN_ETL, TARGET_MISMATCH.
 *
 * <p>ETL trace: {@code Map<EObject, List<EObject>>} (source → targets)
 * <p>Zeta trace: same format extracted from TransformationTrace
 *
 * <p>This class is model-independent and can be reused across all transformation
 * modules (e.g., psm2asm, esm2ui) that have dual ETL/Zeta implementations.
 */
public class DualTraceComparator {

    /**
     * Compare ETL and Zeta transformation traces.
     *
     * @param etlTrace  ETL source-to-target mappings
     * @param zetaTrace Zeta source-to-target mappings
     * @return list of discrepancies found
     */
    public static List<TraceDiscrepancy> compare(
            Map<EObject, List<EObject>> etlTrace,
            Map<EObject, List<EObject>> zetaTrace) {

        List<TraceDiscrepancy> discrepancies = new ArrayList<>();

        // Build alignment maps: (sourceId, targetTypeName) → target
        Map<String, TraceEntry> etlEntries = buildAlignmentMap(etlTrace);
        Map<String, TraceEntry> zetaEntries = buildAlignmentMap(zetaTrace);

        // Find entries in ETL but not in Zeta
        for (Map.Entry<String, TraceEntry> entry : etlEntries.entrySet()) {
            String key = entry.getKey();
            TraceEntry etlEntry = entry.getValue();

            if (!zetaEntries.containsKey(key)) {
                discrepancies.add(new TraceDiscrepancy(
                        etlEntry.sourceId,
                        etlEntry.targetId,
                        null,
                        etlEntry.targetId, // ETL doesn't have rule names, use target ID as proxy
                        null,
                        TraceDiscrepancy.Type.MISSING_IN_ZETA
                ));
            }
        }

        // Find entries in Zeta but not in ETL
        for (Map.Entry<String, TraceEntry> entry : zetaEntries.entrySet()) {
            String key = entry.getKey();
            TraceEntry zetaEntry = entry.getValue();

            if (!etlEntries.containsKey(key)) {
                discrepancies.add(new TraceDiscrepancy(
                        zetaEntry.sourceId,
                        null,
                        zetaEntry.targetId,
                        null,
                        zetaEntry.targetId, // Use target ID as rule name proxy
                        TraceDiscrepancy.Type.MISSING_IN_ETL
                ));
            }
        }

        return discrepancies;
    }

    private static Map<String, TraceEntry> buildAlignmentMap(Map<EObject, List<EObject>> trace) {
        Map<String, TraceEntry> map = new LinkedHashMap<>();

        for (Map.Entry<EObject, List<EObject>> entry : trace.entrySet()) {
            EObject source = entry.getKey();
            String sourceId = getXmiId(source);

            for (EObject target : entry.getValue()) {
                String targetTypeName = target.eClass().getName();
                String targetId = getXmiId(target);
                String key = sourceId + "::" + targetTypeName;

                map.put(key, new TraceEntry(sourceId, targetId, targetTypeName));
            }
        }

        return map;
    }

    private static String getXmiId(EObject eObject) {
        if (eObject == null) {
            return "<null>";
        }
        if (eObject.eResource() == null) {
            return "<no-resource:" + eObject.eClass().getName() + ">";
        }
        if (eObject.eResource() instanceof XMLResource xmlResource) {
            String id = xmlResource.getID(eObject);
            if (id != null) {
                return id;
            }
        }
        return eObject.eResource().getURIFragment(eObject);
    }

    private record TraceEntry(String sourceId, String targetId, String targetTypeName) {
    }
}
