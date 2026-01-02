package hu.blackbelt.judo.tatami.test.util;

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
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMLResource;

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
    public static final String PROP_XMI_ID_COMPARISON = "judo.test.comparison.xmiIds";

    // Default values
    private static final int DEFAULT_MAX_DIFFERENCES = 50;
    private static final ComparisonMode DEFAULT_MODE = ComparisonMode.STRUCTURAL;
    private static final double EPSILON = 1e-9;

    /**
     * Comparison modes for model equivalence checking.
     */
    public enum ComparisonMode {
        /**
         * All attributes and references must match exactly.
         */
        STRICT,
        
        /**
         * Element structure must match, annotation differences are tolerated.
         */
        STRUCTURAL,
        
        /**
         * Major structural elements must match, minor differences are allowed.
         */
        LENIENT
    }

    /**
     * Checks if comparison is enabled via system property.
     * 
     * @return true if comparison is enabled (default: true)
     */
    public static boolean isComparisonEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_COMPARISON_ENABLED, "true"));
    }

    /**
     * Gets the configured comparison mode from system property.
     * 
     * @return the configured ComparisonMode (default: STRUCTURAL)
     */
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

    /**
     * Gets the configured maximum differences from system property.
     * 
     * @return the max differences to report (default: 50)
     */
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

    /**
     * Gets the configured report file path from system property.
     *
     * @return the report file path, or null if not configured
     */
    public static String getConfiguredReportFile() {
        return System.getProperty(PROP_REPORT_FILE);
    }

    /**
     * Checks if XMI ID comparison is enabled via system property.
     *
     * @return true if XMI ID comparison is enabled (default: false)
     */
    public static boolean isXmiIdComparisonEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_XMI_ID_COMPARISON, "false"));
    }

    /**
     * Asserts that two EObjects are structurally equivalent (order-independent).
     * Uses the default STRUCTURAL comparison mode.
     *
     * @param expected the expected model
     * @param actual the actual model
     * @throws AssertionError if the models are not equivalent
     */
    public static void assertEquivalent(EObject expected, EObject actual) {
        assertEquivalent(expected, actual, getConfiguredMode());
    }

    /**
     * Asserts that two EObjects are structurally equivalent (order-independent).
     *
     * @param expected the expected model
     * @param actual the actual model
     * @param mode the comparison mode to use
     * @throws AssertionError if the models are not equivalent
     */
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

    /**
     * Asserts that two Resources are structurally equivalent (order-independent).
     * Uses the default STRUCTURAL comparison mode.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @throws AssertionError if the resources are not equivalent
     */
    public static void assertEquivalent(Resource expected, Resource actual) {
        assertEquivalent(expected, actual, getConfiguredMode());
    }

    /**
     * Asserts that two Resources are structurally equivalent (order-independent).
     * Uses identifier-based matching when possible, falls back to positional comparison.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @param mode the comparison mode to use
     * @throws AssertionError if the resources are not equivalent
     */
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

        // XMI ID comparison (if enabled via system property)
        assertXmiIdsEquivalent(expected, actual);
    }

    /**
     * Compares two EObjects for structural equivalence (order-independent).
     * Uses the default STRUCTURAL comparison mode.
     *
     * @param obj1 the first object
     * @param obj2 the second object
     * @return a ComparisonResult indicating whether the objects are equivalent
     */
    public static ComparisonResult compare(EObject obj1, EObject obj2) {
        return compare(obj1, obj2, getConfiguredMode());
    }

    /**
     * Compares two EObjects for structural equivalence (order-independent).
     *
     * @param obj1 the first object
     * @param obj2 the second object
     * @param mode the comparison mode to use
     * @return a ComparisonResult indicating whether the objects are equivalent
     */
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

        // Avoid infinite loops
        if (visited.containsKey(obj1)) {
            return;
        }
        visited.put(obj1, obj2);

        // Compare classes
        if (!obj1.eClass().getName().equals(obj2.eClass().getName())) {
            differences.add(new TypeMismatch(path, obj1.eClass().getName(), obj2.eClass().getName()));
            return;
        }

        // Compare structural features
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
        // Always skip derived and transient features
        if (feature.isDerived() || feature.isTransient()) {
            return true;
        }
        
        // In LENIENT mode, skip certain features
        if (mode == ComparisonMode.LENIENT) {
            String name = feature.getName();
            // Skip documentation and metadata-like features
            if (name.equals("documentation") || name.equals("comment") || name.equals("description")) {
                return true;
            }
        }
        
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void compareAttributes(Object val1, Object val2, String path, 
                                          List<Difference> differences, ComparisonMode mode) {
        // Skip 'mixed' feature comparison as it contains FeatureMap entries with object identity issues
        if (path.endsWith(".mixed")) {
            // For mixed content, just check sizes match
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
        
        // Handle empty vs null as equivalent
        if (isEffectivelyEmpty(val1) && isEffectivelyEmpty(val2)) {
            return;
        }
        
        // Handle lists (like 'mixed' feature which may contain EObjects)
        if (val1 instanceof List && val2 instanceof List) {
            List<?> list1 = (List<?>) val1;
            List<?> list2 = (List<?>) val2;
            
            if (list1.size() != list2.size()) {
                differences.add(new ValueMismatch(path, 
                        "size=" + list1.size(), "size=" + list2.size()));
                return;
            }
            
            // For mixed content lists containing EObjects, compare by content not identity
            if (!list1.isEmpty() && list1.get(0) instanceof EObject) {
                // Compare EObjects by their class and key attributes
                for (int i = 0; i < list1.size(); i++) {
                    EObject obj1 = (EObject) list1.get(i);
                    EObject obj2 = (EObject) list2.get(i);
                    if (!obj1.eClass().getName().equals(obj2.eClass().getName())) {
                        differences.add(new TypeMismatch(path + "[" + i + "]", 
                                obj1.eClass().getName(), obj2.eClass().getName()));
                    }
                    // Don't recurse into these objects to avoid over-reporting
                }
                return;
            }
        }
        
        // Handle floating point comparison with epsilon
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
            
            // Handle empty vs null as equivalent
            if (list1.isEmpty() && list2.isEmpty()) {
                return;
            }
            
            if (list1.size() != list2.size()) {
                differences.add(new ValueMismatch(path, 
                        "size=" + list1.size(), "size=" + list2.size()));
                // Continue to report individual differences
            }
            
            if (list1.isEmpty() || list2.isEmpty()) {
                // One is empty, other is not - report missing/extra elements
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
            
            // Skip EAnnotation comparison in STRUCTURAL and LENIENT modes
            if (mode != ComparisonMode.STRICT && !list1.isEmpty() && list1.get(0) instanceof EAnnotation) {
                return;
            }
            
            // Try to match elements by identifier (order-independent)
            Map<String, EObject> map1 = mapByIdentifier(list1);
            Map<String, EObject> map2 = mapByIdentifier(list2);
            
            // Check if we can use identifier-based matching
            if (map1.size() == list1.size() && map2.size() == list2.size()) {
                // All elements have unique identifiers - compare by identifier (order-independent)
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
                // Try matching by type signature for elements without unique names
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
                        // Match elements of same type by their content hash
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
        
        // Try to match by content signature first
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
        
        // Match by signature
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
        
        // Report unmatched as positional comparison fallback
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
        
        // Positional comparison for remaining
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
            
            // Use set comparison for references (order-independent)
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

    /**
     * Maps elements by a unique identifier (name, id, or uuid attribute).
     */
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

    /**
     * Groups elements by their type signature (class name).
     */
    private static Map<String, List<EObject>> groupByTypeSignature(EList<EObject> list) {
        Map<String, List<EObject>> map = new LinkedHashMap<>();
        for (EObject obj : list) {
            String type = obj.eClass().getName();
            map.computeIfAbsent(type, k -> new ArrayList<>()).add(obj);
        }
        return map;
    }

    /**
     * Gets a unique identifier for an object (tries name, id, uuid, source attributes).
     */
    private static String getIdentifier(EObject obj) {
        // For EAnnotation, use 'source' attribute as identifier
        if (obj instanceof EAnnotation) {
            String source = ((EAnnotation) obj).getSource();
            if (source != null) {
                return "EAnnotation:" + source;
            }
        }
        
        // Try 'name' attribute
        String name = getAttributeValue(obj, "name");
        if (name != null) {
            return obj.eClass().getName() + ":" + name;
        }
        
        // Try 'id' attribute
        String id = getAttributeValue(obj, "id");
        if (id != null) {
            return obj.eClass().getName() + "#" + id;
        }
        
        // Try 'uuid' attribute
        String uuid = getAttributeValue(obj, "uuid");
        if (uuid != null) {
            return obj.eClass().getName() + "@" + uuid;
        }
        
        // Try 'source' attribute (for annotation-like elements)
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

    /**
     * Creates a content signature for an object based on its key attributes.
     * For objects without standard identifiers, includes all attributes and single-valued references.
     */
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

    // ==================== XMI ID Comparison Methods ====================

    /**
     * Gets the XMI ID for an EObject from its containing resource.
     * Returns null if the object has no XMI ID or is not in a resource.
     *
     * @param obj the object to get the XMI ID for
     * @return the XMI ID, or null if not set
     */
    public static String getXmiId(EObject obj) {
        if (obj == null) {
            return null;
        }
        Resource resource = obj.eResource();
        if (resource instanceof XMLResource) {
            return ((XMLResource) resource).getID(obj);
        }
        return null;
    }

    /**
     * Builds a map of XMI ID -> EObject for all elements in a resource.
     * Only includes elements that have explicit XMI IDs set.
     *
     * @param resource the resource to scan
     * @return map of XMI ID to EObject
     */
    public static Map<String, EObject> buildXmiIdMap(Resource resource) {
        Map<String, EObject> map = new LinkedHashMap<>();
        if (resource instanceof XMLResource) {
            XMLResource xmlResource = (XMLResource) resource;
            TreeIterator<EObject> iter = resource.getAllContents();
            while (iter.hasNext()) {
                EObject obj = iter.next();
                String id = xmlResource.getID(obj);
                if (id != null) {
                    map.put(id, obj);
                }
            }
        }
        return map;
    }

    /**
     * Filters out secondary elements from an XMI ID map.
     * Secondary elements are those created via ctx.create() that may not have structured IDs.
     *
     * @param map the original XMI ID map
     * @return a new map with secondary elements removed
     */
    private static Map<String, EObject> filterOutSecondaryElements(Map<String, EObject> map) {
        Map<String, EObject> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, EObject> entry : map.entrySet()) {
            String typeName = entry.getValue().eClass().getName();
            if (!SECONDARY_ELEMENT_TYPES.contains(typeName)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * Set of element types that are considered "secondary" - created via ctx.create()
     * rather than ctx.createTarget(), and thus may not have structured XMI IDs in Zeta.
     * We skip XMI ID comparison for these types since:
     * 1. Their parent elements' IDs are already being compared
     * 2. Structural comparison already verifies they exist with correct values
     */
    private static final Set<String> SECONDARY_ELEMENT_TYPES = Set.of(
            "EEnumLiteral",           // Enum member literals
            "EStringToStringMapEntry" // Annotation details
    );

    /**
     * Compares XMI IDs between two resources.
     * Uses flexible matching where rule names can be substrings of each other.
     * For example, ETL ID "(psm/_xxx)/Package" matches Zeta ID "(psm/_xxx)/NamespaceToPackage"
     * because "Package" is a substring of "NamespaceToPackage".
     * <p>
     * Secondary elements (EEnumLiteral, EStringToStringMapEntry) are skipped since they
     * may not have structured XMI IDs in Zeta transformations.
     * <p>
     * Returns differences where:
     * <ul>
     *   <li>An XMI ID exists in expected but not in actual (MissingXmiId)</li>
     *   <li>An XMI ID exists in actual but not in expected (ExtraXmiId)</li>
     *   <li>Elements with matching XMI IDs have different types (XmiIdTypeMismatch)</li>
     * </ul>
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @return list of XMI ID differences
     */
    public static List<Difference> compareXmiIds(Resource expected, Resource actual) {
        List<Difference> differences = new ArrayList<>();

        Map<String, EObject> expectedIds = buildXmiIdMap(expected);
        Map<String, EObject> actualIds = buildXmiIdMap(actual);

        // Filter out secondary element types from both maps
        expectedIds = filterOutSecondaryElements(expectedIds);
        actualIds = filterOutSecondaryElements(actualIds);

        // If neither has XMI IDs, they are equivalent in terms of XMI IDs
        if (expectedIds.isEmpty() && actualIds.isEmpty()) {
            return differences;
        }

        // Track which actual IDs have been matched
        Set<String> matchedActualIds = new HashSet<>();

        // Find missing XMI IDs (in expected but not in actual)
        for (Map.Entry<String, EObject> entry : expectedIds.entrySet()) {
            String expectedXmiId = entry.getKey();
            EObject expectedObj = entry.getValue();

            // First try exact match
            if (actualIds.containsKey(expectedXmiId)) {
                matchedActualIds.add(expectedXmiId);
                // XMI ID exists in both - verify element types match
                EObject actualObj = actualIds.get(expectedXmiId);
                if (!expectedObj.eClass().getName().equals(actualObj.eClass().getName())) {
                    differences.add(new XmiIdTypeMismatch(expectedXmiId,
                            expectedObj.eClass().getName(),
                            actualObj.eClass().getName()));
                }
            } else {
                // Try flexible matching with rule name substring comparison
                String matchedActualId = findMatchingXmiId(expectedXmiId, actualIds.keySet(), matchedActualIds);
                if (matchedActualId != null) {
                    matchedActualIds.add(matchedActualId);
                    // Verify element types match
                    EObject actualObj = actualIds.get(matchedActualId);
                    if (!expectedObj.eClass().getName().equals(actualObj.eClass().getName())) {
                        differences.add(new XmiIdTypeMismatch(expectedXmiId,
                                expectedObj.eClass().getName(),
                                actualObj.eClass().getName()));
                    }
                } else {
                    differences.add(new MissingXmiId(expectedXmiId, getObjectIdentifier(expectedObj)));
                }
            }
        }

        // Find extra XMI IDs (in actual but not matched to any expected)
        for (Map.Entry<String, EObject> entry : actualIds.entrySet()) {
            String xmiId = entry.getKey();
            if (!matchedActualIds.contains(xmiId)) {
                differences.add(new ExtraXmiId(xmiId, getObjectIdentifier(entry.getValue())));
            }
        }

        return differences;
    }

    /**
     * Finds a matching XMI ID using flexible rule name comparison.
     * XMI IDs have format: "(sourcePath)/RuleName" or "(sourcePath)/RuleName/SubPath"
     * Matching is successful if:
     * <ul>
     *   <li>Source paths are identical</li>
     *   <li>The shorter rule name is a substring of the longer one (case-insensitive)</li>
     * </ul>
     *
     * @param expectedId the expected XMI ID to find a match for
     * @param actualIds all actual XMI IDs to search
     * @param alreadyMatched set of actual IDs already matched (to avoid double-matching)
     * @return the matching actual XMI ID, or null if no match found
     */
    private static String findMatchingXmiId(String expectedId, Set<String> actualIds, Set<String> alreadyMatched) {
        ParsedXmiId expected = parseXmiId(expectedId);
        if (expected == null) {
            return null;
        }

        for (String actualId : actualIds) {
            if (alreadyMatched.contains(actualId)) {
                continue;
            }

            ParsedXmiId actual = parseXmiId(actualId);
            if (actual == null) {
                continue;
            }

            // Source paths must match exactly
            if (!expected.sourcePath.equals(actual.sourcePath)) {
                continue;
            }

            // Rule names must have substring relationship (case-insensitive)
            if (isRuleNameMatch(expected.ruleName, actual.ruleName)) {
                // If there are sub-paths, they must also match
                if (expected.subPath == null && actual.subPath == null) {
                    return actualId;
                }
                if (expected.subPath != null && actual.subPath != null) {
                    if (isRuleNameMatch(expected.subPath, actual.subPath)) {
                        return actualId;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks if two rule names match using substring comparison.
     * The shorter name must be a substring of the longer name (case-insensitive).
     * For example: "Package" matches "NamespaceToPackage", "ModelToPackage"
     *              "Enumeration" matches "CreateEnumeration"
     */
    private static boolean isRuleNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return name1 == null && name2 == null;
        }
        String lower1 = name1.toLowerCase();
        String lower2 = name2.toLowerCase();
        return lower1.contains(lower2) || lower2.contains(lower1);
    }

    /**
     * Parsed representation of a structured XMI ID.
     */
    private static class ParsedXmiId {
        final String sourcePath;  // e.g., "(psm/_xxx)"
        final String ruleName;    // e.g., "Package" or "NamespaceToPackage"
        final String subPath;     // e.g., "Literal1" (optional)

        ParsedXmiId(String sourcePath, String ruleName, String subPath) {
            this.sourcePath = sourcePath;
            this.ruleName = ruleName;
            this.subPath = subPath;
        }
    }

    /**
     * Parses a structured XMI ID into its components.
     * Handles two formats:
     * <ul>
     *   <li>ETL format: "(psm/_xxx)/RuleName" or "(psm/_xxx)/RuleName/SubPath"</li>
     *   <li>Zeta format: "ElementName/(psm/_xxx)/RuleName"</li>
     * </ul>
     * Also handles non-structured IDs (returns null).
     *
     * @param xmiId the XMI ID to parse
     * @return parsed components, or null if not a structured ID
     */
    private static ParsedXmiId parseXmiId(String xmiId) {
        if (xmiId == null) {
            return null;
        }

        // Check for Zeta format: "ElementName/(psm/_xxx)/RuleName" or "ElementName/(source/_xxx)/RuleName"
        int sourceStart = xmiId.indexOf("/(psm/");
        if (sourceStart < 0) {
            sourceStart = xmiId.indexOf("/(source/");
        }
        if (sourceStart >= 0) {
            // Zeta format: extract the source ID part
            int closeParenIndex = xmiId.indexOf(')', sourceStart);
            if (closeParenIndex < 0) {
                return null;
            }
            // Extract source path: "(psm/_xxx)" or "(source/_xxx)"
            String sourcePath = xmiId.substring(sourceStart + 1, closeParenIndex + 1);

            // The rule name comes after the closing parenthesis
            String rest = xmiId.substring(closeParenIndex + 1);
            if (rest.isEmpty() || !rest.startsWith("/")) {
                return null;
            }
            rest = rest.substring(1);  // Remove leading "/"

            // Split remaining path for sub-paths
            int slashIndex = rest.indexOf('/');
            String ruleName;
            String subPath = null;
            if (slashIndex < 0) {
                ruleName = rest;
            } else {
                ruleName = rest.substring(0, slashIndex);
                subPath = rest.substring(slashIndex + 1);
            }

            return new ParsedXmiId(sourcePath, ruleName, subPath);
        }

        // Check for ETL format: "(psm/_xxx)/RuleName"
        if (!xmiId.startsWith("(")) {
            return null;  // Not a structured ID
        }

        int closeParenIndex = xmiId.indexOf(')');
        if (closeParenIndex < 0) {
            return null;
        }

        String sourcePath = xmiId.substring(0, closeParenIndex + 1);  // "(psm/_xxx)"

        // Rest after source path
        String rest = xmiId.substring(closeParenIndex + 1);
        if (rest.isEmpty() || !rest.startsWith("/")) {
            return null;
        }

        rest = rest.substring(1);  // Remove leading "/"

        // Split remaining path
        int slashIndex = rest.indexOf('/');
        String ruleName;
        String subPath = null;

        if (slashIndex < 0) {
            ruleName = rest;
        } else {
            ruleName = rest.substring(0, slashIndex);
            subPath = rest.substring(slashIndex + 1);
        }

        return new ParsedXmiId(sourcePath, ruleName, subPath);
    }

    /**
     * Asserts that two Resources have equivalent XMI IDs.
     * Throws AssertionError if XMI IDs differ.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @throws AssertionError if XMI IDs differ
     */
    public static void assertXmiIdsEquivalent(Resource expected, Resource actual) {
        if (!isXmiIdComparisonEnabled()) {
            return;
        }

        List<Difference> xmiDifferences = compareXmiIds(expected, actual);
        if (!xmiDifferences.isEmpty()) {
            StringBuilder sb = new StringBuilder("XMI ID comparison failed:\n");
            sb.append(xmiDifferences.size()).append(" XMI ID difference(s):\n");
            for (Difference diff : xmiDifferences) {
                sb.append("  ").append(diff.describe()).append("\n");
            }
            throw new AssertionError(sb.toString());
        }
    }

    // ==================== Difference Classes ====================

    /**
     * Abstract base class for all difference types.
     */
    public abstract static class Difference {
        protected final String path;

        protected Difference(String path) {
            this.path = path;
        }

        /**
         * Gets the path where this difference occurred.
         */
        public String getPath() {
            return path;
        }

        /**
         * Gets a human-readable description of this difference.
         */
        public abstract String describe();

        @Override
        public String toString() {
            return describe();
        }
    }

    /**
     * Indicates an element is missing from the actual model.
     */
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

    /**
     * Indicates an unexpected element in the actual model.
     */
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

    /**
     * Indicates a value mismatch between expected and actual.
     */
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

    /**
     * Indicates a type mismatch between expected and actual elements.
     */
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

    // ==================== XMI ID Difference Classes ====================

    /**
     * Indicates an XMI ID is missing from the actual model.
     */
    public static class MissingXmiId extends Difference {
        private final String xmiId;
        private final String elementDescription;

        public MissingXmiId(String xmiId, String elementDescription) {
            super("xmiId");
            this.xmiId = xmiId;
            this.elementDescription = elementDescription;
        }

        public String getXmiId() {
            return xmiId;
        }

        public String getElementDescription() {
            return elementDescription;
        }

        @Override
        public String describe() {
            return "XMI ID missing: '" + xmiId + "' for element " + elementDescription;
        }
    }

    /**
     * Indicates an unexpected XMI ID in the actual model.
     */
    public static class ExtraXmiId extends Difference {
        private final String xmiId;
        private final String elementDescription;

        public ExtraXmiId(String xmiId, String elementDescription) {
            super("xmiId");
            this.xmiId = xmiId;
            this.elementDescription = elementDescription;
        }

        public String getXmiId() {
            return xmiId;
        }

        public String getElementDescription() {
            return elementDescription;
        }

        @Override
        public String describe() {
            return "XMI ID unexpected: '" + xmiId + "' for element " + elementDescription;
        }
    }

    /**
     * Indicates elements with same XMI ID have different types.
     */
    public static class XmiIdTypeMismatch extends Difference {
        private final String xmiId;
        private final String expectedType;
        private final String actualType;

        public XmiIdTypeMismatch(String xmiId, String expectedType, String actualType) {
            super("xmiId");
            this.xmiId = xmiId;
            this.expectedType = expectedType;
            this.actualType = actualType;
        }

        public String getXmiId() {
            return xmiId;
        }

        public String getExpectedType() {
            return expectedType;
        }

        public String getActualType() {
            return actualType;
        }

        @Override
        public String describe() {
            return "XMI ID '" + xmiId + "' type mismatch: expected " + expectedType + " but was " + actualType;
        }
    }

    // ==================== ComparisonResult ====================

    /**
     * Result of a model comparison, containing all differences found.
     */
    public static class ComparisonResult {
        private final List<Difference> differences;
        private final int maxDifferences;
        private final boolean truncated;

        public ComparisonResult(List<Difference> differences, int maxDifferences) {
            this.differences = differences != null ? differences : Collections.emptyList();
            this.maxDifferences = maxDifferences;
            this.truncated = maxDifferences > 0 && this.differences.size() >= maxDifferences;
        }

        /**
         * Legacy constructor for backwards compatibility.
         */
        public ComparisonResult(List<String> differences) {
            this.differences = differences != null 
                    ? differences.stream().map(s -> new ValueMismatch("", s, "")).collect(Collectors.toList())
                    : Collections.emptyList();
            this.maxDifferences = DEFAULT_MAX_DIFFERENCES;
            this.truncated = false;
        }

        /**
         * Returns true if the compared models are equivalent.
         */
        public boolean isEquivalent() {
            return differences.isEmpty();
        }

        /**
         * Returns true if the difference list was truncated due to max limit.
         */
        public boolean isTruncated() {
            return truncated;
        }

        /**
         * Gets the number of differences found.
         */
        public int getDifferenceCount() {
            return differences.size();
        }

        /**
         * Gets a summary of the comparison result.
         */
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

        /**
         * Returns a formatted string of all differences found (legacy method).
         */
        public String getDifferences() {
            if (differences.isEmpty()) {
                return "No differences";
            }
            return differences.stream()
                    .map(Difference::describe)
                    .collect(Collectors.joining("\n"));
        }

        /**
         * Returns a detailed report with summary and all differences.
         */
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

        /**
         * Returns the list of differences.
         */
        public List<Difference> getDifferenceList() {
            return Collections.unmodifiableList(differences);
        }

        /**
         * Gets differences filtered by type.
         */
        public <T extends Difference> List<T> getDifferencesOfType(Class<T> type) {
            return differences.stream()
                    .filter(type::isInstance)
                    .map(type::cast)
                    .collect(Collectors.toList());
        }
    }
}
