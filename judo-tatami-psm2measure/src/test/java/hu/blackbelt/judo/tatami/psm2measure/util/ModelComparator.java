package hu.blackbelt.judo.tatami.psm2measure.util;

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

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for comparing EMF models with order-independent comparison.
 * <p>
 * This class provides methods to compare two EMF models for structural equivalence,
 * which is useful for verifying that different transformation engines produce
 * equivalent output. The comparison is order-independent, meaning that elements
 * in collections are matched by their identifiers (name, id) rather than position.
 * </p>
 * 
 * <h2>Comparison Modes</h2>
 * <ul>
 *   <li><b>STRICT</b> - All attributes and references must match exactly</li>
 *   <li><b>STRUCTURAL</b> - Element structure must match, annotation differences tolerated</li>
 *   <li><b>LENIENT</b> - Major structural elements must match, minor differences allowed</li>
 * </ul>
 * 
 * <h2>Configuration</h2>
 * The comparator can be configured via system properties:
 * <ul>
 *   <li>{@code judo.test.comparison.enabled} - Enable/disable comparison (default: true)</li>
 *   <li>{@code judo.test.comparison.mode} - Comparison mode (default: STRUCTURAL)</li>
 *   <li>{@code judo.test.comparison.maxDifferences} - Max differences to report (default: 50)</li>
 *   <li>{@code judo.test.comparison.reportFile} - Output file for diff report (optional)</li>
 * </ul>
 */
public class ModelComparator {

    // System property keys
    public static final String PROP_COMPARISON_ENABLED = "judo.test.comparison.enabled";
    public static final String PROP_COMPARISON_MODE = "judo.test.comparison.mode";
    public static final String PROP_MAX_DIFFERENCES = "judo.test.comparison.maxDifferences";
    public static final String PROP_REPORT_FILE = "judo.test.comparison.reportFile";

    // Default values
    private static final int DEFAULT_MAX_DIFFERENCES = 50;
    private static final ComparisonMode DEFAULT_MODE = ComparisonMode.STRUCTURAL;
    private static final double EPSILON = 1e-9;

    /**
     * Comparison modes for model equivalence checking.
     */
    public enum ComparisonMode {
        STRICT,
        STRUCTURAL,
        LENIENT
    }

    public static boolean isComparisonEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_COMPARISON_ENABLED, "true"));
    }

    public static ComparisonMode getConfiguredMode() {
        String modeStr = System.getProperty(PROP_COMPARISON_MODE);
        if (modeStr != null) {
            try {
                return ComparisonMode.valueOf(modeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Fall through to default
            }
        }
        return DEFAULT_MODE;
    }

    public static int getConfiguredMaxDifferences() {
        String maxStr = System.getProperty(PROP_MAX_DIFFERENCES);
        if (maxStr != null) {
            try {
                return Integer.parseInt(maxStr);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return DEFAULT_MAX_DIFFERENCES;
    }

    public static String getConfiguredReportFile() {
        return System.getProperty(PROP_REPORT_FILE);
    }

    public static void assertEquivalent(EObject expected, EObject actual) {
        assertEquivalent(expected, actual, getConfiguredMode());
    }

    public static void assertEquivalent(EObject expected, EObject actual, ComparisonMode mode) {
        if (!isComparisonEnabled()) {
            return;
        }
        
        ComparisonResult result = compare(expected, actual, mode);
        writeReportIfConfigured(result);
        
        if (!result.isEquivalent()) {
            throw new AssertionError("Models are not equivalent:\n" + result.getDetailedReport());
        }
    }

    public static void assertEquivalent(Resource expected, Resource actual) {
        assertEquivalent(expected, actual, getConfiguredMode());
    }

    public static void assertEquivalent(Resource expected, Resource actual, ComparisonMode mode) {
        if (!isComparisonEnabled()) {
            return;
        }
        
        if (expected.getContents().size() != actual.getContents().size()) {
            throw new AssertionError("Resources have different number of root elements: " +
                    expected.getContents().size() + " vs " + actual.getContents().size());
        }
        
        // Use order-independent comparison by matching elements by identifier
        Map<String, EObject> expectedMap = new LinkedHashMap<>();
        Map<String, EObject> actualMap = new LinkedHashMap<>();
        
        for (EObject obj : expected.getContents()) {
            String id = getIdentifier(obj);
            if (id != null) {
                expectedMap.put(id, obj);
            }
        }
        for (EObject obj : actual.getContents()) {
            String id = getIdentifier(obj);
            if (id != null) {
                actualMap.put(id, obj);
            }
        }
        
        // If all elements have identifiers, use order-independent matching
        if (expectedMap.size() == expected.getContents().size() && 
            actualMap.size() == actual.getContents().size()) {
            
            Set<String> allKeys = new LinkedHashSet<>();
            allKeys.addAll(expectedMap.keySet());
            allKeys.addAll(actualMap.keySet());
            
            List<String> missingInActual = new ArrayList<>();
            List<String> extraInActual = new ArrayList<>();
            
            for (String key : allKeys) {
                EObject exp = expectedMap.get(key);
                EObject act = actualMap.get(key);
                
                if (exp == null) {
                    extraInActual.add(key);
                } else if (act == null) {
                    missingInActual.add(key);
                } else {
                    assertEquivalent(exp, act, mode);
                }
            }
            
            if (!missingInActual.isEmpty() || !extraInActual.isEmpty()) {
                StringBuilder sb = new StringBuilder("Root element mismatch:");
                if (!missingInActual.isEmpty()) {
                    sb.append(" missing=").append(missingInActual);
                }
                if (!extraInActual.isEmpty()) {
                    sb.append(" extra=").append(extraInActual);
                }
                throw new AssertionError(sb.toString());
            }
        } else {
            // Fall back to positional comparison
            for (int i = 0; i < expected.getContents().size(); i++) {
                assertEquivalent(expected.getContents().get(i), actual.getContents().get(i), mode);
            }
        }
    }

    public static ComparisonResult compare(EObject obj1, EObject obj2) {
        return compare(obj1, obj2, getConfiguredMode());
    }

    public static ComparisonResult compare(EObject obj1, EObject obj2, ComparisonMode mode) {
        List<Difference> differences = new ArrayList<>();
        int maxDifferences = getConfiguredMaxDifferences();
        compareObjects(obj1, obj2, "", differences, new IdentityHashMap<>(), mode, maxDifferences);
        return new ComparisonResult(differences, maxDifferences);
    }

    private static void writeReportIfConfigured(ComparisonResult result) {
        String reportFile = getConfiguredReportFile();
        if (reportFile != null && !result.isEquivalent()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
                writer.println(result.getDetailedReport());
            } catch (IOException e) {
                System.err.println("Failed to write comparison report to " + reportFile + ": " + e.getMessage());
            }
        }
    }

    private static boolean shouldStop(List<Difference> differences, int maxDifferences) {
        return maxDifferences > 0 && differences.size() >= maxDifferences;
    }

    private static void compareObjects(EObject obj1, EObject obj2, String path,
                                       List<Difference> differences, Map<EObject, EObject> visited,
                                       ComparisonMode mode, int maxDifferences) {
        if (shouldStop(differences, maxDifferences)) {
            return;
        }
        
        if (obj1 == null && obj2 == null) {
            return;
        }
        
        if (obj1 == null) {
            differences.add(new ExtraElement(path, obj2.eClass().getName()));
            return;
        }
        
        if (obj2 == null) {
            differences.add(new MissingElement(path, obj1.eClass().getName()));
            return;
        }

        if (visited.containsKey(obj1)) {
            return;
        }
        visited.put(obj1, obj2);

        if (!obj1.eClass().getName().equals(obj2.eClass().getName())) {
            differences.add(new TypeMismatch(path, obj1.eClass().getName(), obj2.eClass().getName()));
            return;
        }

        for (EStructuralFeature feature : obj1.eClass().getEAllStructuralFeatures()) {
            if (shouldStop(differences, maxDifferences)) {
                return;
            }
            
            String featurePath = path.isEmpty() ? feature.getName() : path + "." + feature.getName();
            
            if (shouldSkipFeature(feature, mode)) {
                continue;
            }

            Object val1 = obj1.eGet(feature);
            Object val2 = obj2.eGet(feature);

            if (feature instanceof EAttribute) {
                compareAttributes(val1, val2, featurePath, differences, mode);
            } else if (feature instanceof EReference) {
                EReference ref = (EReference) feature;
                if (ref.isContainment()) {
                    compareContainment(val1, val2, featurePath, differences, visited, mode, maxDifferences);
                } else {
                    compareReference(val1, val2, featurePath, differences, mode);
                }
            }
        }
    }

    private static boolean shouldSkipFeature(EStructuralFeature feature, ComparisonMode mode) {
        if (feature.isDerived() || feature.isTransient()) {
            return true;
        }
        
        if (mode == ComparisonMode.LENIENT) {
            String name = feature.getName();
            if (name.equals("documentation") || name.equals("comment") || name.equals("description")) {
                return true;
            }
        }
        
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void compareAttributes(Object val1, Object val2, String path, 
                                          List<Difference> differences, ComparisonMode mode) {
        if (path.endsWith(".mixed")) {
            if (val1 instanceof List && val2 instanceof List) {
                List<?> list1 = (List<?>) val1;
                List<?> list2 = (List<?>) val2;
                if (list1.size() != list2.size()) {
                    differences.add(new ValueMismatch(path, 
                            "size=" + list1.size(), "size=" + list2.size()));
                }
            }
            return;
        }
        
        if (isEffectivelyEmpty(val1) && isEffectivelyEmpty(val2)) {
            return;
        }
        
        if (val1 instanceof List && val2 instanceof List) {
            List<?> list1 = (List<?>) val1;
            List<?> list2 = (List<?>) val2;
            
            if (list1.size() != list2.size()) {
                differences.add(new ValueMismatch(path, 
                        "size=" + list1.size(), "size=" + list2.size()));
                return;
            }
            
            if (!list1.isEmpty() && list1.get(0) instanceof EObject) {
                for (int i = 0; i < list1.size(); i++) {
                    EObject obj1 = (EObject) list1.get(i);
                    EObject obj2 = (EObject) list2.get(i);
                    if (!obj1.eClass().getName().equals(obj2.eClass().getName())) {
                        differences.add(new TypeMismatch(path + "[" + i + "]", 
                                obj1.eClass().getName(), obj2.eClass().getName()));
                    }
                }
                return;
            }
        }
        
        if (val1 instanceof Double && val2 instanceof Double) {
            if (Math.abs((Double) val1 - (Double) val2) > EPSILON) {
                differences.add(new ValueMismatch(path, String.valueOf(val1), String.valueOf(val2)));
            }
            return;
        }
        if (val1 instanceof Float && val2 instanceof Float) {
            if (Math.abs((Float) val1 - (Float) val2) > EPSILON) {
                differences.add(new ValueMismatch(path, String.valueOf(val1), String.valueOf(val2)));
            }
            return;
        }
        
        if (!Objects.equals(val1, val2)) {
            differences.add(new ValueMismatch(path, 
                    val1 != null ? val1.toString() : "null",
                    val2 != null ? val2.toString() : "null"));
        }
    }

    private static boolean isEffectivelyEmpty(Object val) {
        if (val == null) {
            return true;
        }
        if (val instanceof Collection && ((Collection<?>) val).isEmpty()) {
            return true;
        }
        if (val instanceof String && ((String) val).isEmpty()) {
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void compareContainment(Object val1, Object val2, String path,
                                           List<Difference> differences, Map<EObject, EObject> visited,
                                           ComparisonMode mode, int maxDifferences) {
        if (shouldStop(differences, maxDifferences)) {
            return;
        }
        
        if (val1 instanceof EList && val2 instanceof EList) {
            EList<EObject> list1 = (EList<EObject>) val1;
            EList<EObject> list2 = (EList<EObject>) val2;
            
            if (list1.isEmpty() && list2.isEmpty()) {
                return;
            }
            
            if (list1.size() != list2.size()) {
                differences.add(new ValueMismatch(path, 
                        "size=" + list1.size(), "size=" + list2.size()));
            }
            
            if (list1.isEmpty() || list2.isEmpty()) {
                for (EObject elem : list1) {
                    differences.add(new MissingElement(path, getObjectIdentifier(elem)));
                    if (shouldStop(differences, maxDifferences)) return;
                }
                for (EObject elem : list2) {
                    differences.add(new ExtraElement(path, getObjectIdentifier(elem)));
                    if (shouldStop(differences, maxDifferences)) return;
                }
                return;
            }
            
            if (mode != ComparisonMode.STRICT && !list1.isEmpty() && list1.get(0) instanceof EAnnotation) {
                return;
            }
            
            Map<String, EObject> map1 = mapByIdentifier(list1);
            Map<String, EObject> map2 = mapByIdentifier(list2);
            
            if (map1.size() == list1.size() && map2.size() == list2.size()) {
                Set<String> allKeys = new HashSet<>();
                allKeys.addAll(map1.keySet());
                allKeys.addAll(map2.keySet());
                
                for (String key : allKeys) {
                    if (shouldStop(differences, maxDifferences)) return;
                    
                    EObject elem1 = map1.get(key);
                    EObject elem2 = map2.get(key);
                    
                    if (elem1 == null) {
                        differences.add(new ExtraElement(path, key));
                    } else if (elem2 == null) {
                        differences.add(new MissingElement(path, key));
                    } else {
                        compareObjects(elem1, elem2, path + "[" + key + "]", differences, visited, mode, maxDifferences);
                    }
                }
            } else {
                Map<String, List<EObject>> byType1 = groupByTypeSignature(list1);
                Map<String, List<EObject>> byType2 = groupByTypeSignature(list2);
                
                Set<String> allTypes = new HashSet<>();
                allTypes.addAll(byType1.keySet());
                allTypes.addAll(byType2.keySet());
                
                for (String type : allTypes) {
                    if (shouldStop(differences, maxDifferences)) return;
                    
                    List<EObject> elems1 = byType1.getOrDefault(type, Collections.emptyList());
                    List<EObject> elems2 = byType2.getOrDefault(type, Collections.emptyList());
                    
                    if (elems1.size() != elems2.size()) {
                        differences.add(new ValueMismatch(path + "[" + type + "]", 
                                "count=" + elems1.size(), "count=" + elems2.size()));
                    } else {
                        matchAndCompareByContent(elems1, elems2, path + "[" + type + "]", 
                                differences, visited, mode, maxDifferences);
                    }
                }
            }
        } else if (val1 instanceof EObject && val2 instanceof EObject) {
            compareObjects((EObject) val1, (EObject) val2, path, differences, visited, mode, maxDifferences);
        } else if (val1 != null || val2 != null) {
            differences.add(new TypeMismatch(path, 
                    val1 != null ? val1.getClass().getSimpleName() : "null",
                    val2 != null ? val2.getClass().getSimpleName() : "null"));
        }
    }

    private static void matchAndCompareByContent(List<EObject> list1, List<EObject> list2, 
                                                  String path, List<Difference> differences, 
                                                  Map<EObject, EObject> visited,
                                                  ComparisonMode mode, int maxDifferences) {
        if (list1.isEmpty()) {
            return;
        }
        
        Map<String, EObject> sig1 = new LinkedHashMap<>();
        Map<String, EObject> sig2 = new LinkedHashMap<>();
        Map<String, Integer> sigCount1 = new HashMap<>();
        Map<String, Integer> sigCount2 = new HashMap<>();
        
        for (EObject obj : list1) {
            String sig = getContentSignature(obj);
            int count = sigCount1.getOrDefault(sig, 0);
            sig1.put(sig + "_" + count, obj);
            sigCount1.put(sig, count + 1);
        }
        for (EObject obj : list2) {
            String sig = getContentSignature(obj);
            int count = sigCount2.getOrDefault(sig, 0);
            sig2.put(sig + "_" + count, obj);
            sigCount2.put(sig, count + 1);
        }
        
        Set<String> matched = new HashSet<>();
        for (Map.Entry<String, EObject> entry : sig1.entrySet()) {
            if (shouldStop(differences, maxDifferences)) return;
            
            if (sig2.containsKey(entry.getKey())) {
                compareObjects(entry.getValue(), sig2.get(entry.getKey()), 
                        path + "[" + entry.getKey().split("_")[0] + "]", 
                        differences, visited, mode, maxDifferences);
                matched.add(entry.getKey());
            }
        }
        
        List<EObject> unmatched1 = new ArrayList<>();
        List<EObject> unmatched2 = new ArrayList<>();
        for (Map.Entry<String, EObject> entry : sig1.entrySet()) {
            if (!matched.contains(entry.getKey())) {
                unmatched1.add(entry.getValue());
            }
        }
        for (Map.Entry<String, EObject> entry : sig2.entrySet()) {
            if (!matched.contains(entry.getKey())) {
                unmatched2.add(entry.getValue());
            }
        }
        
        int minSize = Math.min(unmatched1.size(), unmatched2.size());
        for (int i = 0; i < minSize; i++) {
            if (shouldStop(differences, maxDifferences)) return;
            compareObjects(unmatched1.get(i), unmatched2.get(i), path + "[" + i + "]", 
                    differences, visited, mode, maxDifferences);
        }
    }

    @SuppressWarnings("unchecked")
    private static void compareReference(Object val1, Object val2, String path, 
                                         List<Difference> differences, ComparisonMode mode) {
        if (val1 instanceof EList && val2 instanceof EList) {
            EList<EObject> list1 = (EList<EObject>) val1;
            EList<EObject> list2 = (EList<EObject>) val2;
            
            Set<String> refs1 = list1.stream()
                    .map(ModelComparator::getObjectIdentifier)
                    .collect(Collectors.toSet());
            Set<String> refs2 = list2.stream()
                    .map(ModelComparator::getObjectIdentifier)
                    .collect(Collectors.toSet());
            
            if (!refs1.equals(refs2)) {
                Set<String> missing = new HashSet<>(refs1);
                missing.removeAll(refs2);
                Set<String> extra = new HashSet<>(refs2);
                extra.removeAll(refs1);
                
                for (String ref : missing) {
                    differences.add(new MissingElement(path, ref));
                }
                for (String ref : extra) {
                    differences.add(new ExtraElement(path, ref));
                }
            }
        } else if (val1 instanceof EObject && val2 instanceof EObject) {
            String id1 = getObjectIdentifier((EObject) val1);
            String id2 = getObjectIdentifier((EObject) val2);
            if (!Objects.equals(id1, id2)) {
                differences.add(new ValueMismatch(path, id1, id2));
            }
        } else if ((val1 == null) != (val2 == null)) {
            differences.add(new ValueMismatch(path, 
                    val1 != null ? getObjectIdentifier((EObject) val1) : "null",
                    val2 != null ? getObjectIdentifier((EObject) val2) : "null"));
        }
    }

    private static Map<String, EObject> mapByIdentifier(EList<EObject> list) {
        Map<String, EObject> map = new LinkedHashMap<>();
        for (EObject obj : list) {
            String id = getIdentifier(obj);
            if (id != null && !map.containsKey(id)) {
                map.put(id, obj);
            }
        }
        return map;
    }

    private static Map<String, List<EObject>> groupByTypeSignature(EList<EObject> list) {
        Map<String, List<EObject>> map = new LinkedHashMap<>();
        for (EObject obj : list) {
            String type = obj.eClass().getName();
            map.computeIfAbsent(type, k -> new ArrayList<>()).add(obj);
        }
        return map;
    }

    private static String getIdentifier(EObject obj) {
        if (obj instanceof EAnnotation) {
            String source = ((EAnnotation) obj).getSource();
            if (source != null) {
                return "EAnnotation:" + source;
            }
        }
        
        String name = getAttributeValue(obj, "name");
        if (name != null) {
            return obj.eClass().getName() + ":" + name;
        }
        
        String id = getAttributeValue(obj, "id");
        if (id != null) {
            return obj.eClass().getName() + "#" + id;
        }
        
        String uuid = getAttributeValue(obj, "uuid");
        if (uuid != null) {
            return obj.eClass().getName() + "@" + uuid;
        }
        
        String source = getAttributeValue(obj, "source");
        if (source != null) {
            return obj.eClass().getName() + ":" + source;
        }
        
        return null;
    }

    private static String getAttributeValue(EObject obj, String attrName) {
        EStructuralFeature feature = obj.eClass().getEStructuralFeature(attrName);
        if (feature instanceof EAttribute) {
            Object value = obj.eGet(feature);
            return value != null ? value.toString() : null;
        }
        return null;
    }

    private static String getContentSignature(EObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append(obj.eClass().getName());
        
        // First try common identifier attributes
        for (String attr : Arrays.asList("name", "id", "uuid", "sqlName", "logicalFilePath")) {
            String val = getAttributeValue(obj, attr);
            if (val != null) {
                sb.append("|").append(attr).append("=").append(val);
            }
        }
        
        // If no identifier found, include all attributes and single-valued references
        // to create a unique content-based signature
        if (sb.toString().equals(obj.eClass().getName())) {
            // Include all attribute values
            for (EStructuralFeature feature : obj.eClass().getEAllStructuralFeatures()) {
                if (feature.isDerived() || feature.isTransient()) {
                    continue;
                }
                if (feature instanceof EAttribute) {
                    Object value = obj.eGet(feature);
                    if (value != null) {
                        sb.append("|").append(feature.getName()).append("=").append(value);
                    }
                } else if (feature instanceof EReference) {
                    EReference ref = (EReference) feature;
                    if (!ref.isMany() && !ref.isContainment()) {
                        // Single-valued non-containment reference
                        EObject refTarget = (EObject) obj.eGet(ref);
                        if (refTarget != null) {
                            String refId = getIdentifier(refTarget);
                            if (refId != null) {
                                sb.append("|").append(feature.getName()).append("=").append(refId);
                            }
                        }
                    }
                }
            }
        }
        
        return sb.toString();
    }

    private static String getObjectIdentifier(EObject obj) {
        if (obj == null) {
            return "null";
        }
        String id = getIdentifier(obj);
        if (id != null) {
            return id;
        }
        return obj.eClass().getName() + "@" + System.identityHashCode(obj);
    }

    // ==================== Difference Classes ====================

    public abstract static class Difference {
        protected final String path;

        protected Difference(String path) {
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        public abstract String describe();

        @Override
        public String toString() {
            return describe();
        }
    }

    public static class MissingElement extends Difference {
        private final String elementDescription;

        public MissingElement(String path, String elementDescription) {
            super(path);
            this.elementDescription = elementDescription;
        }

        public String getElementDescription() {
            return elementDescription;
        }

        @Override
        public String describe() {
            return path + ": missing element '" + elementDescription + "'";
        }
    }

    public static class ExtraElement extends Difference {
        private final String elementDescription;

        public ExtraElement(String path, String elementDescription) {
            super(path);
            this.elementDescription = elementDescription;
        }

        public String getElementDescription() {
            return elementDescription;
        }

        @Override
        public String describe() {
            return path + ": unexpected element '" + elementDescription + "'";
        }
    }

    public static class ValueMismatch extends Difference {
        private final String expectedValue;
        private final String actualValue;

        public ValueMismatch(String path, String expectedValue, String actualValue) {
            super(path);
            this.expectedValue = expectedValue;
            this.actualValue = actualValue;
        }

        public String getExpectedValue() {
            return expectedValue;
        }

        public String getActualValue() {
            return actualValue;
        }

        @Override
        public String describe() {
            return path + ": " + expectedValue + " vs " + actualValue;
        }
    }

    public static class TypeMismatch extends Difference {
        private final String expectedType;
        private final String actualType;

        public TypeMismatch(String path, String expectedType, String actualType) {
            super(path);
            this.expectedType = expectedType;
            this.actualType = actualType;
        }

        public String getExpectedType() {
            return expectedType;
        }

        public String getActualType() {
            return actualType;
        }

        @Override
        public String describe() {
            return path + ": type mismatch - expected " + expectedType + " but was " + actualType;
        }
    }

    // ==================== ComparisonResult ====================

    public static class ComparisonResult {
        private final List<Difference> differences;
        private final int maxDifferences;
        private final boolean truncated;

        public ComparisonResult(List<Difference> differences, int maxDifferences) {
            this.differences = differences != null ? differences : Collections.emptyList();
            this.maxDifferences = maxDifferences;
            this.truncated = maxDifferences > 0 && this.differences.size() >= maxDifferences;
        }

        public ComparisonResult(List<String> differences) {
            this.differences = differences != null 
                    ? differences.stream().map(s -> new ValueMismatch("", s, "")).collect(Collectors.toList())
                    : Collections.emptyList();
            this.maxDifferences = DEFAULT_MAX_DIFFERENCES;
            this.truncated = false;
        }

        public boolean isEquivalent() {
            return differences.isEmpty();
        }

        public boolean isTruncated() {
            return truncated;
        }

        public int getDifferenceCount() {
            return differences.size();
        }

        public String getSummary() {
            if (differences.isEmpty()) {
                return "Models are equivalent";
            }
            
            long missingCount = differences.stream().filter(d -> d instanceof MissingElement).count();
            long extraCount = differences.stream().filter(d -> d instanceof ExtraElement).count();
            long valueCount = differences.stream().filter(d -> d instanceof ValueMismatch).count();
            long typeCount = differences.stream().filter(d -> d instanceof TypeMismatch).count();
            
            StringBuilder sb = new StringBuilder();
            sb.append(differences.size()).append(" difference(s)");
            if (truncated) {
                sb.append(" (truncated at ").append(maxDifferences).append(")");
            }
            sb.append(":");
            if (missingCount > 0) sb.append(" ").append(missingCount).append(" missing");
            if (extraCount > 0) sb.append(" ").append(extraCount).append(" extra");
            if (valueCount > 0) sb.append(" ").append(valueCount).append(" value mismatch");
            if (typeCount > 0) sb.append(" ").append(typeCount).append(" type mismatch");
            
            return sb.toString();
        }

        public String getDifferences() {
            if (differences.isEmpty()) {
                return "No differences";
            }
            return differences.stream()
                    .map(Difference::describe)
                    .collect(Collectors.joining("\n"));
        }

        public String getDetailedReport() {
            if (differences.isEmpty()) {
                return "No differences - models are equivalent";
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append(getSummary()).append("\n\n");
            
            for (Difference diff : differences) {
                sb.append("  ").append(diff.describe()).append("\n");
            }
            
            if (truncated) {
                sb.append("\n  ... (output truncated, increase ")
                  .append(PROP_MAX_DIFFERENCES).append(" to see more)\n");
            }
            
            return sb.toString();
        }

        public List<Difference> getDifferenceList() {
            return Collections.unmodifiableList(differences);
        }

        public <T extends Difference> List<T> getDifferencesOfType(Class<T> type) {
            return differences.stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .collect(Collectors.toList());
        }
    }
}
