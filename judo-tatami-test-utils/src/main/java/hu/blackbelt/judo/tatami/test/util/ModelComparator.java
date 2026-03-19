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
 *   <li><b>STRICT</b> - All attributes, references and annotations must match exactly; EAnnotation lists are compared as order-insensitive sets</li>
 *   <li><b>SKELETON</b> - Element structure must match, annotation differences are tolerated</li>
 * </ul>
 *
 * <p>Note: Derived and transient features are always skipped as they contain computed values.</p>
 *
 * <h2>Configuration</h2>
 * The comparator can be configured via system properties:
 * <ul>
 *   <li>{@code judo.test.comparison.enabled} - Enable/disable comparison (default: true)</li>
 *   <li>{@code judo.test.comparison.mode} - Comparison mode (default: STRICT)</li>
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
    private static final ComparisonMode DEFAULT_MODE = ComparisonMode.STRICT;
    private static final double EPSILON = 1e-9;

    /**
     * Comparison modes for model equivalence checking.
     */
    public enum ComparisonMode {
        /**
         * All attributes, references and EAnnotations must match exactly.
         * EAnnotation lists are compared as order-insensitive sets keyed by source URI.
         */
        STRICT,

        /**
         * Element structure (attributes and references) must match; EAnnotation lists are skipped entirely.
         */
        SKELETON
    }

    /**
     * Functional interface for custom XMI ID extraction.
     * <p>
     * Consumer projects can implement this interface to provide custom XMI ID
     * extraction logic for their specific model types during comparison.
     * </p>
     * <p>
     * Example usage:
     * <pre>
     * XmiIdExtractor customExtractor = obj -> {
     *     if (obj instanceof MyDomainElement) {
     *         return ((MyDomainElement) obj).getBusinessKey();
     *     }
     *     // Return null to fall back to default XMI ID extraction
     *     return null;
     * };
     * ModelComparator.compareXmiIds(expected, actual, customExtractor);
     * </pre>
     */
    @FunctionalInterface
    public interface XmiIdExtractor {
        /**
         * Extracts XMI ID for an EObject for comparison purposes.
         *
         * @param obj the object to get XMI ID for
         * @return XMI ID string, or null to use default extraction via {@link #defaultXmiId(EObject)}
         */
        String extractXmiId(EObject obj);
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
     * @return the configured ComparisonMode (default: STRICT)
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
     * Uses the default STRICT comparison mode.
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
     * Uses the default STRICT comparison mode.
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
     * Uses the default STRICT comparison mode.
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

    /**
     * Compares two Resources for structural equivalence (order-independent).
     * Uses identifier-based matching for root elements to handle different element ordering.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @return a ComparisonResult indicating whether the resources are equivalent
     */
    public static ComparisonResult compare(Resource expected, Resource actual) {
        return compare(expected, actual, getConfiguredMode());
    }

    /**
     * Compares two Resources for structural equivalence (order-independent).
     * Uses identifier-based matching for root elements to handle different element ordering.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @param mode the comparison mode to use
     * @return a ComparisonResult indicating whether the resources are equivalent
     */
    public static ComparisonResult compare(Resource expected, Resource actual, ComparisonMode mode) {
        return compare(expected, actual, mode, null);
    }

    /**
     * Compares two Resources for structural equivalence (order-independent).
     * Uses identifier-based matching for root elements to handle different element ordering.
     * <p>
     * When a custom XMI ID extractor is provided, it is used for element identification
     * in addition to XMI ID comparison. If the extractor returns null for an element,
     * the default identifier logic is used.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @param mode the comparison mode to use
     * @param extractor custom XMI ID extractor for element identification, or null to use default
     * @return a ComparisonResult indicating whether the resources are equivalent
     */
    public static ComparisonResult compare(Resource expected, Resource actual, ComparisonMode mode, XmiIdExtractor extractor) {
        List<Difference> differences = new ArrayList<>();
        int maxDifferences = getConfiguredMaxDifferences();

        // Check content count
        if (expected.getContents().size() != actual.getContents().size()) {
            differences.add(new ValueMismatch("contents.size",
                    String.valueOf(expected.getContents().size()),
                    String.valueOf(actual.getContents().size())));
        }

        // Use order-independent comparison by matching elements by identifier
        Map<String, EObject> expectedMap = new LinkedHashMap<>();
        Map<String, EObject> actualMap = new LinkedHashMap<>();

        for (EObject obj : expected.getContents()) {
            String id = getIdentifierWithExtractor(obj, extractor);
            if (id != null) {
                expectedMap.put(id, obj);
            }
        }
        for (EObject obj : actual.getContents()) {
            String id = getIdentifierWithExtractor(obj, extractor);
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

            for (String key : allKeys) {
                if (shouldStop(differences, maxDifferences)) break;

                EObject exp = expectedMap.get(key);
                EObject act = actualMap.get(key);

                if (exp == null) {
                    differences.add(new ExtraElement("", key));
                } else if (act == null) {
                    differences.add(new MissingElement("", key));
                } else {
                    compareObjects(exp, act, key, differences, new IdentityHashMap<>(), mode, maxDifferences);
                }
            }
        } else {
            // Fall back to positional comparison
            int minSize = Math.min(expected.getContents().size(), actual.getContents().size());
            for (int i = 0; i < minSize && !shouldStop(differences, maxDifferences); i++) {
                compareObjects(expected.getContents().get(i), actual.getContents().get(i),
                        "[" + i + "]", differences, new IdentityHashMap<>(), mode, maxDifferences);
            }
        }

        return new ComparisonResult(differences, maxDifferences);
    }

    /**
     * Gets identifier for an object, using custom extractor if provided.
     * Falls back to default getIdentifier() if extractor returns null.
     */
    private static String getIdentifierWithExtractor(EObject obj, XmiIdExtractor extractor) {
        if (extractor != null) {
            String customId = extractor.extractXmiId(obj);
            if (customId != null) {
                return customId;
            }
        }
        return getIdentifier(obj);
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
        // Always skip derived and transient features - they are computed values
        // that depend on object identity or other derived data
        if (feature.isDerived() || feature.isTransient()) {
            return true;
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

            // Skip EAnnotation comparison in SKELETON mode
            // This must happen BEFORE the size check to avoid reporting annotation count differences
            if (mode == ComparisonMode.SKELETON && !list1.isEmpty() && isEAnnotation(list1.get(0))) {
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

            // Special handling for EAnnotation lists in STRICT mode - compare as sets
            // This handles the case where annotations have the same content but different ordering
            if (mode == ComparisonMode.STRICT && !list1.isEmpty() && isEAnnotation(list1.get(0))) {
                compareAnnotationSets(list1, list2, path, differences, mode);
                return;
            }

            // Special handling for EAnnotation details (EStringToStringMapEntry)
            // Compare as multimap (key -> set of values) to handle duplicate keys like multiple 'owner' entries
            if (!list1.isEmpty() && isAnnotationDetailEntry(list1.get(0))) {
                compareAnnotationDetails(list1, list2, path, differences, mode);
                return;
            }

            // Try to match elements by identifier (order-independent)
            Map<String, EObject> map1 = mapByIdentifier(list1);
            Map<String, EObject> map2 = mapByIdentifier(list2);
            
            // Check if we can use identifier-based matching
            if (map1.size() == list1.size() && map2.size() == list2.size()) {
                // All elements have unique identifiers - compare by identifier (order-independent)
                // Use LinkedHashSet to ensure deterministic iteration order for consistent error messages
                Set<String> allKeys = new LinkedHashSet<>();
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

                // Use LinkedHashSet to ensure deterministic iteration order for consistent error messages
                Set<String> allTypes = new LinkedHashSet<>();
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

    /**
     * Checks if an EObject is an EAnnotation.
     * Uses eClass().getName() for reliable EMF type detection across different class loaders.
     */
    private static boolean isEAnnotation(EObject obj) {
        if (obj == null) {
            return false;
        }
        // Check by interface first (most reliable)
        if (obj instanceof EAnnotation) {
            return true;
        }
        // Fall back to name-based check for proxies or cross-class-loader scenarios
        return "EAnnotation".equals(obj.eClass().getName());
    }

    /**
     * Checks if an EObject is an annotation detail entry (has 'key' and 'value' attributes).
     * Works with both interface (Map.Entry) and Ecore implementations.
     */
    private static boolean isAnnotationDetailEntry(EObject obj) {
        if (obj == null) {
            return false;
        }
        // Check by interface - EAnnotation.details contains Map.Entry<String, String>
        if (obj instanceof Map.Entry) {
            return true;
        }
        // Check by structural feature presence (key and value attributes)
        EClass eClass = obj.eClass();
        return eClass.getEStructuralFeature("key") != null
                && eClass.getEStructuralFeature("value") != null
                && eClass.getName().contains("StringToString");
    }

    /**
     * Compares annotation details (EStringToStringMapEntry) in an order-independent manner.
     * Handles duplicate keys by comparing sets of values for each key.
     */
    @SuppressWarnings("unchecked")
    private static void compareAnnotationDetails(EList<EObject> list1, EList<EObject> list2,
                                                  String path, List<Difference> differences,
                                                  ComparisonMode mode) {
        // Build multimap: key -> set of values for each list
        Map<String, Set<String>> map1 = new LinkedHashMap<>();
        Map<String, Set<String>> map2 = new LinkedHashMap<>();

        for (EObject entry : list1) {
            String key = getAttributeValue(entry, "key");
            String value = getAttributeValue(entry, "value");
            if (key != null) {
                map1.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value != null ? value : "");
            }
        }

        for (EObject entry : list2) {
            String key = getAttributeValue(entry, "key");
            String value = getAttributeValue(entry, "value");
            if (key != null) {
                map2.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value != null ? value : "");
            }
        }

        // Compare keys and their value sets
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(map1.keySet());
        allKeys.addAll(map2.keySet());

        for (String key : allKeys) {
            Set<String> values1 = map1.getOrDefault(key, Collections.emptySet());
            Set<String> values2 = map2.getOrDefault(key, Collections.emptySet());

            if (values1.isEmpty()) {
                // Key only in actual
                differences.add(new ExtraElement(path + "[key=" + key + "]",
                        "values=" + values2));
            } else if (values2.isEmpty()) {
                // Key only in expected
                differences.add(new MissingElement(path + "[key=" + key + "]",
                        "values=" + values1));
            } else if (!values1.equals(values2)) {
                // Values differ for this key - compare as sets
                Set<String> missing = new LinkedHashSet<>(values1);
                missing.removeAll(values2);
                Set<String> extra = new LinkedHashSet<>(values2);
                extra.removeAll(values1);

                if (!missing.isEmpty() || !extra.isEmpty()) {
                    StringBuilder desc = new StringBuilder();
                    if (!missing.isEmpty()) {
                        desc.append("missing=").append(missing);
                    }
                    if (!extra.isEmpty()) {
                        if (desc.length() > 0) desc.append(", ");
                        desc.append("extra=").append(extra);
                    }
                    differences.add(new ValueMismatch(path + "[key=" + key + "]",
                            values1.toString(), values2.toString()));
                }
            }
        }
    }

    /**
     * Compares two lists of EAnnotation as sets (order-independent).
     * Uses annotation signature (source + sorted details) for matching.
     */
    @SuppressWarnings("unchecked")
    private static void compareAnnotationSets(EList<EObject> list1, EList<EObject> list2,
                                               String path, List<Difference> differences,
                                               ComparisonMode mode) {
        // Build signature sets for each list
        Set<String> sigs1 = new LinkedHashSet<>();
        Set<String> sigs2 = new LinkedHashSet<>();

        for (EObject obj : list1) {
            sigs1.add(getAnnotationSignatureFromEObject(obj));
        }
        for (EObject obj : list2) {
            sigs2.add(getAnnotationSignatureFromEObject(obj));
        }

        // Compare as sets
        Set<String> missing = new LinkedHashSet<>(sigs1);
        missing.removeAll(sigs2);
        Set<String> extra = new LinkedHashSet<>(sigs2);
        extra.removeAll(sigs1);

        // Report differences
        for (String sig : missing) {
            differences.add(new MissingElement(path, "EAnnotation: " + sig));
        }
        for (String sig : extra) {
            differences.add(new ExtraElement(path, "EAnnotation: " + sig));
        }
    }

    /**
     * Gets a signature string for an EAnnotation from a generic EObject.
     * Handles both EAnnotation interface and EMF-based annotation objects.
     * Used for set-based comparison of annotations.
     */
    @SuppressWarnings("unchecked")
    private static String getAnnotationSignatureFromEObject(EObject obj) {
        StringBuilder sig = new StringBuilder();

        // Get source attribute - try interface first, then reflective access
        String source = null;
        if (obj instanceof EAnnotation) {
            source = ((EAnnotation) obj).getSource();
        } else {
            source = getAttributeValue(obj, "source");
        }
        sig.append(source != null ? source : "");

        // Get details - try interface first, then reflective access
        List<Map.Entry<String, String>> detailEntries = new ArrayList<>();
        if (obj instanceof EAnnotation) {
            org.eclipse.emf.common.util.EMap<String, String> details = ((EAnnotation) obj).getDetails();
            if (details != null && !details.isEmpty()) {
                detailEntries.addAll(details.entrySet());
            }
        } else {
            // Fallback: access details via reflection on the EObject
            EStructuralFeature detailsFeature = obj.eClass().getEStructuralFeature("details");
            if (detailsFeature != null) {
                Object detailsVal = obj.eGet(detailsFeature);
                if (detailsVal instanceof EList) {
                    EList<EObject> detailsList = (EList<EObject>) detailsVal;
                    for (EObject entry : detailsList) {
                        String key = getAttributeValue(entry, "key");
                        String value = getAttributeValue(entry, "value");
                        if (key != null) {
                            final String k = key;
                            final String v = value != null ? value : "";
                            detailEntries.add(new Map.Entry<String, String>() {
                                @Override public String getKey() { return k; }
                                @Override public String getValue() { return v; }
                                @Override public String setValue(String value) { throw new UnsupportedOperationException(); }
                            });
                        }
                    }
                }
            }
        }

        // Sort details by key, then by value for entries with same key
        if (!detailEntries.isEmpty()) {
            detailEntries.sort(Comparator.comparing((Map.Entry<String, String> e) -> e.getKey())
                                         .thenComparing(e -> e.getValue() != null ? e.getValue() : ""));
            for (Map.Entry<String, String> entry : detailEntries) {
                sig.append("|").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        return sig.toString();
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

            // Check if all elements have proper identifiers (not identity-hash fallbacks)
            boolean allHaveIdentifiers = list1.stream().allMatch(o -> getIdentifier(o) != null)
                    && list2.stream().allMatch(o -> getIdentifier(o) != null);

            if (allHaveIdentifiers) {
                // Use identifier-based set comparison for references (order-independent)
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
            } else {
                // Elements don't have proper identifiers - use content-based comparison
                // to avoid false positives from identity hashCode differences between equivalent objects
                if (list1.size() != list2.size()) {
                    differences.add(new ValueMismatch(path, "size=" + list1.size(), "size=" + list2.size()));
                } else {
                    // Match by content signature (order-independent)
                    Map<String, Integer> sig1 = new LinkedHashMap<>();
                    Map<String, Integer> sig2 = new LinkedHashMap<>();
                    for (EObject obj : list1) {
                        sig1.merge(getContentSignature(obj), 1, Integer::sum);
                    }
                    for (EObject obj : list2) {
                        sig2.merge(getContentSignature(obj), 1, Integer::sum);
                    }
                    if (!sig1.equals(sig2)) {
                        for (String sig : sig1.keySet()) {
                            int c1 = sig1.getOrDefault(sig, 0);
                            int c2 = sig2.getOrDefault(sig, 0);
                            if (c1 > c2) {
                                differences.add(new MissingElement(path, sig + " (x" + (c1 - c2) + ")"));
                            }
                        }
                        for (String sig : sig2.keySet()) {
                            int c1 = sig1.getOrDefault(sig, 0);
                            int c2 = sig2.getOrDefault(sig, 0);
                            if (c2 > c1) {
                                differences.add(new ExtraElement(path, sig + " (x" + (c2 - c1) + ")"));
                            }
                        }
                    }
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
     * Gets a unique identifier for an object (tries name, id, uuid, source, key attributes).
     */
    private static String getIdentifier(EObject obj) {
        // For EAnnotation, use 'source' attribute + details as identifier
        // This ensures annotations with same source but different details are matched correctly
        if (obj instanceof EAnnotation) {
            EAnnotation ann = (EAnnotation) obj;
            String source = ann.getSource();
            if (source != null) {
                StringBuilder id = new StringBuilder("EAnnotation:").append(source);
                // Include details in identifier for uniqueness (e.g., behavior annotations with different owners)
                // Sort by key, then by value for entries with same key to ensure consistent identifiers
                org.eclipse.emf.common.util.EMap<String, String> details = ann.getDetails();
                if (details != null && !details.isEmpty()) {
                    List<Map.Entry<String, String>> sorted = new ArrayList<>(details.entrySet());
                    sorted.sort(Comparator.comparing((Map.Entry<String, String> e) -> e.getKey())
                                          .thenComparing(e -> e.getValue() != null ? e.getValue() : ""));
                    for (Map.Entry<String, String> entry : sorted) {
                        id.append("|").append(entry.getKey()).append("=").append(entry.getValue());
                    }
                }
                return id.toString();
            }
        }

        // For EStringToStringMapEntry (annotation details), use 'key' attribute as identifier
        // This enables order-independent comparison of annotation details
        String key = getAttributeValue(obj, "key");
        if (key != null && obj.eClass().getName().equals("EStringToStringMapEntry")) {
            return "EStringToStringMapEntry:" + key;
        }

        // For Expression model Binding objects (AttributeBinding, ReferenceBinding, FilterBinding):
        // these have no 'name' attribute, but are uniquely identified by typeName + feature + role.
        // Without this, ModelComparator falls back to positional comparison and reports 50 false
        // ordering differences when ETL and Zeta produce the same bindings in different resource order.
        String bindingId = getExpressionBindingIdentifier(obj);
        if (bindingId != null) {
            return bindingId;
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
     * Builds a composite identifier for Expression model Binding objects.
     * <p>
     * {@code AttributeBinding}, {@code ReferenceBinding}, and {@code FilterBinding} have no
     * {@code name} attribute, so {@link #getIdentifier} would return {@code null} and fall back
     * to positional comparison — causing 50 false ordering differences between ETL and Zeta
     * Expression models that are structurally identical but produced in different resource order.
     * <p>
     * Identity key: {@code className:namespace/typeName#featureName#role}
     *
     * @return composite identifier, or {@code null} if the object is not a recognised Binding type
     */
    private static String getExpressionBindingIdentifier(EObject obj) {
        String className = obj.eClass().getName();
        if (!className.equals("AttributeBinding") && !className.equals("ReferenceBinding")
                && !className.equals("FilterBinding")) {
            return null;
        }

        // Resolve typeName reference (containment reference on Binding)
        EStructuralFeature typeNameFeature = obj.eClass().getEStructuralFeature("typeName");
        if (typeNameFeature == null) return null;
        Object typeNameObj = obj.eGet(typeNameFeature);
        if (!(typeNameObj instanceof EObject)) return null;
        EObject typeName = (EObject) typeNameObj;

        String tnName = getAttributeValue(typeName, "name");
        String tnNamespace = getAttributeValue(typeName, "namespace");
        if (tnName == null) return null;

        String qualifier = (tnNamespace != null ? tnNamespace + "/" : "") + tnName;

        if (className.equals("FilterBinding")) {
            return "FilterBinding:" + qualifier;
        }

        // AttributeBinding has 'attributeName', ReferenceBinding has 'referenceName'
        String featureName = className.equals("AttributeBinding")
                ? getAttributeValue(obj, "attributeName")
                : getAttributeValue(obj, "referenceName");
        String role = getAttributeValue(obj, "role");

        if (featureName == null) return null;
        return className + ":" + qualifier + "#" + featureName + (role != null ? "#" + role : "");
    }

    /**
     * Creates a content signature for an object based on its key attributes.
     * For objects without standard identifiers, includes all attributes and single-valued references.
     */
    private static String getContentSignature(EObject obj) {
        StringBuilder sb = new StringBuilder();
        sb.append(obj.eClass().getName());

        // Special handling for EAnnotation - include source and all details
        if (obj instanceof EAnnotation) {
            EAnnotation ann = (EAnnotation) obj;
            String source = ann.getSource();
            if (source != null) {
                sb.append("|source=").append(source);
            }
            // Include all details in signature for uniqueness
            // Sort by key, then by value for entries with same key
            org.eclipse.emf.common.util.EMap<String, String> details = ann.getDetails();
            if (details != null && !details.isEmpty()) {
                List<Map.Entry<String, String>> sorted = new ArrayList<>(details.entrySet());
                sorted.sort(Comparator.comparing((Map.Entry<String, String> e) -> e.getKey())
                                      .thenComparing(e -> e.getValue() != null ? e.getValue() : ""));
                for (Map.Entry<String, String> entry : sorted) {
                    sb.append("|").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }
            return sb.toString();
        }

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
     * Default XMI ID extraction for an EObject.
     * <p>
     * This method is exposed for use in custom {@link XmiIdExtractor} implementations
     * that need to fall back to the default behavior for certain element types.
     * </p>
     * <p>
     * Example usage in custom extractor:
     * <pre>
     * XmiIdExtractor customExtractor = obj -> {
     *     if (obj instanceof MyType) {
     *         return ((MyType) obj).getCustomId();
     *     }
     *     // Fall back to default for other types
     *     return ModelComparator.defaultXmiId(obj);
     * };
     * </pre>
     *
     * @param obj the object to get the XMI ID for
     * @return the XMI ID, or null if not set
     * @see #getXmiId(EObject)
     */
    public static String defaultXmiId(EObject obj) {
        return getXmiId(obj);
    }

    /**
     * Extracts XMI ID using custom extractor with fallback to default.
     *
     * @param obj the object to get the XMI ID for
     * @param extractor custom extractor, or null to use default
     * @return the XMI ID, or null if not set
     */
    private static String extractXmiId(EObject obj, XmiIdExtractor extractor) {
        if (extractor != null) {
            String customId = extractor.extractXmiId(obj);
            if (customId != null) {
                return customId;
            }
        }
        return getXmiId(obj);
    }

    /**
     * Builds a map of XMI ID -> EObject for all elements in a resource.
     * Only includes elements that have explicit XMI IDs set.
     *
     * @param resource the resource to scan
     * @return map of XMI ID to EObject
     */
    public static Map<String, EObject> buildXmiIdMap(Resource resource) {
        return buildXmiIdMap(resource, null);
    }

    /**
     * Builds a map of XMI ID -> EObject for all elements in a resource.
     * Uses custom extractor if provided, with fallback to default XMI ID extraction.
     *
     * @param resource the resource to scan
     * @param extractor custom XMI ID extractor, or null to use default
     * @return map of XMI ID to EObject
     */
    public static Map<String, EObject> buildXmiIdMap(Resource resource, XmiIdExtractor extractor) {
        Map<String, EObject> map = new LinkedHashMap<>();
        if (resource != null) {
            TreeIterator<EObject> iter = resource.getAllContents();
            while (iter.hasNext()) {
                EObject obj = iter.next();
                String id = extractXmiId(obj, extractor);
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
     * Compares XMI IDs between two resources using default XMI ID extraction.
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
        return compareXmiIds(expected, actual, null);
    }

    /**
     * Compares XMI IDs between two resources using custom XMI ID extraction.
     * Uses flexible matching where rule names can be substrings of each other.
     * <p>
     * This overload allows consumer projects to provide custom XMI ID extraction logic
     * for their specific model types. When the extractor returns null for an element,
     * the default XMI ID extraction is used as fallback.
     * <p>
     * Example usage:
     * <pre>
     * XmiIdExtractor customExtractor = obj -> {
     *     if (obj instanceof MyDomainElement) {
     *         return ((MyDomainElement) obj).getBusinessKey();
     *     }
     *     return null; // Fall back to default
     * };
     * List&lt;Difference&gt; diffs = ModelComparator.compareXmiIds(expected, actual, customExtractor);
     * </pre>
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @param extractor custom XMI ID extractor, or null to use default extraction
     * @return list of XMI ID differences
     */
    public static List<Difference> compareXmiIds(Resource expected, Resource actual, XmiIdExtractor extractor) {
        List<Difference> differences = new ArrayList<>();

        Map<String, EObject> expectedIds = buildXmiIdMap(expected, extractor);
        Map<String, EObject> actualIds = buildXmiIdMap(actual, extractor);

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
                // XMI ID exists in both - verify element types and containers match
                EObject actualObj = actualIds.get(expectedXmiId);
                verifyMatchedElements(expectedXmiId, expectedObj, actualObj, differences);
            } else {
                // Try flexible matching with rule name substring comparison
                String matchedActualId = findMatchingXmiId(expectedXmiId, actualIds.keySet(), matchedActualIds);
                if (matchedActualId != null) {
                    matchedActualIds.add(matchedActualId);
                    // Verify element types and containers match
                    EObject actualObj = actualIds.get(matchedActualId);
                    verifyMatchedElements(expectedXmiId, expectedObj, actualObj, differences);
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
     * Verifies that two matched elements have the same type and container.
     *
     * @param xmiId the XMI ID being compared
     * @param expectedObj the expected element
     * @param actualObj the actual element
     * @param differences list to add any differences to
     */
    private static void verifyMatchedElements(String xmiId, EObject expectedObj, EObject actualObj,
                                               List<Difference> differences) {
        // Check type match
        String expectedType = expectedObj.eClass().getName();
        String actualType = actualObj.eClass().getName();
        if (!expectedType.equals(actualType)) {
            differences.add(new XmiIdTypeMismatch(xmiId, expectedType, actualType));
            return; // Don't check container if types don't match
        }

        // Check container match
        String expectedContainer = getContainerIdentifier(expectedObj);
        String actualContainer = getContainerIdentifier(actualObj);
        if (!containersMatch(expectedContainer, actualContainer)) {
            differences.add(new XmiIdContainerMismatch(xmiId, expectedType, expectedContainer, actualContainer));
        }
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
     * Known ETL to Zeta rule name equivalences.
     * Maps ETL rule names (key) to their Zeta equivalents (value).
     * This handles cases where rule naming conventions differ between transformations.
     */
    private static final Map<String, String> ETL_TO_ZETA_RULE_MAPPINGS = Map.ofEntries(
            // ActorType rules - ETL uses short names, Zeta uses Create prefix
            Map.entry("ActorType", "CreateActorType"),
            Map.entry("ActorTypeWithoutPrincipal", "CreateActorTypeWithoutPrincipal"),
            Map.entry("MappedActorType", "CreateMappedActorType"),

            // Package rules
            Map.entry("ExtensionPackage", "CreateExtensionPackage"),
            Map.entry("ExtensionRootPackage", "CreateExtensionRootPackage"),

            // Type rules
            Map.entry("AutoGeneratedBooleanType", "CreateAutoGeneratedBooleanType"),
            Map.entry("AutoGeneratedIntegerType", "CreateAutoGeneratedIntegerType"),
            Map.entry("AutoGeneratedStringType", "CreateAutoGeneratedStringType"),
            Map.entry("MetadataSecurityType", "CreateMetadataSecurityType"),
            Map.entry("MetadataType", "CreateMetadataType"),
            Map.entry("QueryFilterType", "CreateQueryFilterType"),
            Map.entry("UploadTokenType", "CreateUploadTokenType"),

            // Enumeration rules
            Map.entry("BooleanOperationEnumeration", "CreateBooleanOperationEnumeration"),
            Map.entry("EnumerationOperationEnumeration", "CreateEnumerationOperationEnumeration"),
            Map.entry("NumericOperationEnumeration", "CreateNumericOperationEnumeration"),
            Map.entry("StringOperationEnumeration", "CreateStringOperationEnumeration"),

            // AssociationEnd rules - ETL uses WithPartner/WithoutPartner suffix
            Map.entry("AssociationEndWithPartner", "AssociationEnd"),
            Map.entry("AssociationEndWithoutPartner", "AssociationEnd"),

            // TransferObjectRelation rules
            Map.entry("AccessTransferObjectRelationWithBinding", "CreateAccessTransferObjectRelationWithBinding"),
            Map.entry("AccessTransferObjectRelationWithoutBinding", "CreateAccessTransferObjectRelationWithoutBinding"),

            // Operation rules - ETL uses different naming pattern
            Map.entry("AddReferenceTransferOperationForRelationFeature", "CreateAddReferenceTransferOperationForRelationFeature"),
            Map.entry("CreateTransferOperationForRelationFeature", "CreateCreateTransferOperationForRelationFeature"),
            Map.entry("GetRangeReferenceTransferOperationForRelationFeature", "CreateGetRangeReferenceTransferOperationForRelationFeature"),
            Map.entry("RemoveReferenceTransferOperationForRelationFeature", "CreateRemoveReferenceTransferOperationForRelationFeature"),
            Map.entry("SetReferenceTransferOperationForRelationFeature", "CreateSetReferenceTransferOperationForRelationFeature"),
            Map.entry("UnsetReferenceTransferOperationForRelationFeature", "CreateUnsetReferenceTransferOperationForRelationFeature"),
            Map.entry("ValidateCreateTransferOperationForRelationFeature", "CreateValidateCreateTransferOperationForRelationFeature")
    );

    /**
     * Reverse mapping from Zeta to ETL rule names (computed from ETL_TO_ZETA_RULE_MAPPINGS).
     */
    private static final Map<String, String> ZETA_TO_ETL_RULE_MAPPINGS;
    static {
        Map<String, String> reverse = new HashMap<>();
        for (Map.Entry<String, String> entry : ETL_TO_ZETA_RULE_MAPPINGS.entrySet()) {
            reverse.put(entry.getValue(), entry.getKey());
        }
        ZETA_TO_ETL_RULE_MAPPINGS = Collections.unmodifiableMap(reverse);
    }

    /**
     * Normalizes a rule name by stripping common prefixes like "Create".
     * This allows matching ETL patterns (e.g., "ExtensionPackage") with
     * Zeta patterns (e.g., "CreateExtensionPackage").
     */
    private static String normalizeRuleName(String name) {
        if (name == null) {
            return null;
        }
        // Strip "Create" prefix if present (Zeta often adds this)
        if (name.startsWith("Create") && name.length() > 6) {
            return name.substring(6);
        }
        return name;
    }

    /**
     * Checks if two rule names match using normalization, mapping table and substring comparison.
     * <p>
     * Matching logic:
     * <ol>
     *   <li>Exact match (case-insensitive)</li>
     *   <li>Normalized match (strip "Create" prefix, case-insensitive)</li>
     *   <li>Known ETL-to-Zeta mapping equivalence</li>
     *   <li>Substring containment (shorter in longer, case-insensitive)</li>
     * </ol>
     * <p>
     * For example: "Package" matches "NamespaceToPackage", "ModelToPackage"
     *              "ExtensionPackage" matches "CreateExtensionPackage" (via normalization)
     *              "ActorType" matches "CreateActorType" (via normalization or mapping)
     */
    private static boolean isRuleNameMatch(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return name1 == null && name2 == null;
        }

        // Exact match (case-insensitive)
        if (name1.equalsIgnoreCase(name2)) {
            return true;
        }

        // Normalized match - strip "Create" prefix and compare
        String normalized1 = normalizeRuleName(name1);
        String normalized2 = normalizeRuleName(name2);
        if (normalized1.equalsIgnoreCase(normalized2)) {
            return true;
        }
        // Also check normalized vs original
        if (normalized1.equalsIgnoreCase(name2) || name1.equalsIgnoreCase(normalized2)) {
            return true;
        }

        // Check known mappings (ETL -> Zeta)
        String mapped1 = ETL_TO_ZETA_RULE_MAPPINGS.get(name1);
        if (mapped1 != null && mapped1.equalsIgnoreCase(name2)) {
            return true;
        }

        // Check reverse mappings (Zeta -> ETL)
        String mapped2 = ZETA_TO_ETL_RULE_MAPPINGS.get(name1);
        if (mapped2 != null && mapped2.equalsIgnoreCase(name2)) {
            return true;
        }

        // Also check the other direction
        mapped1 = ETL_TO_ZETA_RULE_MAPPINGS.get(name2);
        if (mapped1 != null && mapped1.equalsIgnoreCase(name1)) {
            return true;
        }
        mapped2 = ZETA_TO_ETL_RULE_MAPPINGS.get(name2);
        if (mapped2 != null && mapped2.equalsIgnoreCase(name1)) {
            return true;
        }

        // Fall back to substring matching
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
     * Gets a descriptive identifier for an element's container.
     * Uses the container's XMI ID if available, otherwise falls back to type:name format.
     *
     * @param obj the object whose container to identify
     * @return a string identifying the container, or "root" if no container
     */
    private static String getContainerIdentifier(EObject obj) {
        if (obj == null) {
            return "null";
        }
        EObject container = obj.eContainer();
        if (container == null) {
            return "root";
        }

        // Try to get XMI ID of container
        String containerXmiId = getXmiId(container);
        if (containerXmiId != null) {
            return containerXmiId;
        }

        // Fall back to type:name format
        String name = getAttributeValue(container, "name");
        if (name != null) {
            return container.eClass().getName() + ":" + name;
        }

        return container.eClass().getName();
    }

    /**
     * Checks if two container identifiers match.
     * Uses flexible matching for XMI IDs (rule name substring comparison).
     *
     * @param expectedContainer the expected container identifier
     * @param actualContainer the actual container identifier
     * @return true if containers match
     */
    private static boolean containersMatch(String expectedContainer, String actualContainer) {
        if (expectedContainer == null && actualContainer == null) {
            return true;
        }
        if (expectedContainer == null || actualContainer == null) {
            return false;
        }
        if (expectedContainer.equals(actualContainer)) {
            return true;
        }

        // Try flexible XMI ID matching (for ETL vs Zeta rule name differences)
        ParsedXmiId expectedParsed = parseXmiId(expectedContainer);
        ParsedXmiId actualParsed = parseXmiId(actualContainer);

        if (expectedParsed != null && actualParsed != null) {
            // Both are structured XMI IDs - use flexible matching
            if (!expectedParsed.sourcePath.equals(actualParsed.sourcePath)) {
                return false;
            }
            return isRuleNameMatch(expectedParsed.ruleName, actualParsed.ruleName);
        }

        // For non-XMI ID containers, use exact match (already checked above)
        return false;
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

    /**
     * Indicates elements with same XMI ID have different containers/owners.
     */
    public static class XmiIdContainerMismatch extends Difference {
        private final String xmiId;
        private final String elementType;
        private final String expectedContainer;
        private final String actualContainer;

        public XmiIdContainerMismatch(String xmiId, String elementType, String expectedContainer, String actualContainer) {
            super("xmiId");
            this.xmiId = xmiId;
            this.elementType = elementType;
            this.expectedContainer = expectedContainer;
            this.actualContainer = actualContainer;
        }

        public String getXmiId() {
            return xmiId;
        }

        public String getElementType() {
            return elementType;
        }

        public String getExpectedContainer() {
            return expectedContainer;
        }

        public String getActualContainer() {
            return actualContainer;
        }

        @Override
        public String describe() {
            return "XMI ID '" + xmiId + "' (" + elementType + ") container mismatch: expected '" + expectedContainer + "' but was '" + actualContainer + "'";
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
