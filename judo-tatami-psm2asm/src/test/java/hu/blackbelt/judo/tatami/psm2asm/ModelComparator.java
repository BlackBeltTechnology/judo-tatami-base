package hu.blackbelt.judo.tatami.psm2asm;

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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for comparing EMF models.
 * <p>
 * This class provides methods to compare two EMF models for structural equivalence,
 * which is useful for verifying that different transformation engines produce
 * equivalent output.
 * </p>
 */
public class ModelComparator {

    /**
     * Asserts that two EObjects are structurally equivalent.
     *
     * @param expected the expected model
     * @param actual the actual model
     * @throws AssertionError if the models are not equivalent
     */
    public static void assertEquivalent(EObject expected, EObject actual) {
        ComparisonResult result = compare(expected, actual);
        if (!result.isEquivalent()) {
            throw new AssertionError("Models are not equivalent:\n" + result.getDifferences());
        }
    }

    /**
     * Asserts that two Resources are structurally equivalent.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @throws AssertionError if the resources are not equivalent
     */
    public static void assertEquivalent(Resource expected, Resource actual) {
        if (expected.getContents().size() != actual.getContents().size()) {
            throw new AssertionError("Resources have different number of root elements: " +
                    expected.getContents().size() + " vs " + actual.getContents().size());
        }
        
        for (int i = 0; i < expected.getContents().size(); i++) {
            assertEquivalent(expected.getContents().get(i), actual.getContents().get(i));
        }
    }

    /**
     * Compares two EObjects for structural equivalence.
     *
     * @param obj1 the first object
     * @param obj2 the second object
     * @return a ComparisonResult indicating whether the objects are equivalent
     */
    public static ComparisonResult compare(EObject obj1, EObject obj2) {
        List<String> differences = new ArrayList<>();
        compareObjects(obj1, obj2, "", differences, new HashSet<>());
        return new ComparisonResult(differences);
    }

    private static void compareObjects(EObject obj1, EObject obj2, String path,
                                       List<String> differences, Set<EObject> visited) {
        if (obj1 == null && obj2 == null) {
            return;
        }
        
        if (obj1 == null) {
            differences.add(path + ": expected null but was " + obj2.eClass().getName());
            return;
        }
        
        if (obj2 == null) {
            differences.add(path + ": expected " + obj1.eClass().getName() + " but was null");
            return;
        }

        // Avoid infinite loops
        if (visited.contains(obj1)) {
            return;
        }
        visited.add(obj1);

        // Compare classes
        if (!obj1.eClass().getName().equals(obj2.eClass().getName())) {
            differences.add(path + ": different classes - " + 
                    obj1.eClass().getName() + " vs " + obj2.eClass().getName());
            return;
        }

        // Compare structural features
        for (EStructuralFeature feature : obj1.eClass().getEAllStructuralFeatures()) {
            String featurePath = path.isEmpty() ? feature.getName() : path + "." + feature.getName();
            
            if (feature.isDerived() || feature.isTransient()) {
                continue;
            }

            Object val1 = obj1.eGet(feature);
            Object val2 = obj2.eGet(feature);

            if (feature instanceof EAttribute) {
                compareAttributes(val1, val2, featurePath, differences);
            } else if (feature instanceof EReference) {
                EReference ref = (EReference) feature;
                if (ref.isContainment()) {
                    compareContainment(val1, val2, featurePath, differences, visited);
                } else {
                    compareReference(val1, val2, featurePath, differences);
                }
            }
        }
    }

    private static void compareAttributes(Object val1, Object val2, String path, List<String> differences) {
        if (!Objects.equals(val1, val2)) {
            differences.add(path + ": " + val1 + " vs " + val2);
        }
    }

    @SuppressWarnings("unchecked")
    private static void compareContainment(Object val1, Object val2, String path,
                                           List<String> differences, Set<EObject> visited) {
        if (val1 instanceof EList && val2 instanceof EList) {
            EList<EObject> list1 = (EList<EObject>) val1;
            EList<EObject> list2 = (EList<EObject>) val2;
            
            if (list1.size() != list2.size()) {
                differences.add(path + ": different sizes - " + list1.size() + " vs " + list2.size());
                return;
            }
            
            // Try to match elements by name first
            Map<String, EObject> map1 = mapByName(list1);
            Map<String, EObject> map2 = mapByName(list2);
            
            if (map1.size() == list1.size() && map2.size() == list2.size()) {
                // All elements have unique names, compare by name
                for (String name : map1.keySet()) {
                    if (!map2.containsKey(name)) {
                        differences.add(path + ": missing element with name '" + name + "'");
                    } else {
                        compareObjects(map1.get(name), map2.get(name), 
                                path + "[" + name + "]", differences, visited);
                    }
                }
                for (String name : map2.keySet()) {
                    if (!map1.containsKey(name)) {
                        differences.add(path + ": unexpected element with name '" + name + "'");
                    }
                }
            } else {
                // Fall back to positional comparison
                for (int i = 0; i < list1.size(); i++) {
                    compareObjects(list1.get(i), list2.get(i), 
                            path + "[" + i + "]", differences, visited);
                }
            }
        } else if (val1 instanceof EObject && val2 instanceof EObject) {
            compareObjects((EObject) val1, (EObject) val2, path, differences, visited);
        } else if (val1 != null || val2 != null) {
            differences.add(path + ": type mismatch - " + 
                    (val1 != null ? val1.getClass().getSimpleName() : "null") + " vs " +
                    (val2 != null ? val2.getClass().getSimpleName() : "null"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void compareReference(Object val1, Object val2, String path, List<String> differences) {
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
                differences.add(path + ": different references - " + refs1 + " vs " + refs2);
            }
        } else if (val1 instanceof EObject && val2 instanceof EObject) {
            String id1 = getObjectIdentifier((EObject) val1);
            String id2 = getObjectIdentifier((EObject) val2);
            if (!Objects.equals(id1, id2)) {
                differences.add(path + ": " + id1 + " vs " + id2);
            }
        } else if ((val1 == null) != (val2 == null)) {
            differences.add(path + ": " + 
                    (val1 != null ? getObjectIdentifier((EObject) val1) : "null") + " vs " +
                    (val2 != null ? getObjectIdentifier((EObject) val2) : "null"));
        }
    }

    private static Map<String, EObject> mapByName(EList<EObject> list) {
        Map<String, EObject> map = new LinkedHashMap<>();
        for (EObject obj : list) {
            String name = getName(obj);
            if (name != null) {
                map.put(name, obj);
            }
        }
        return map;
    }

    private static String getName(EObject obj) {
        EStructuralFeature nameFeature = obj.eClass().getEStructuralFeature("name");
        if (nameFeature != null) {
            Object name = obj.eGet(nameFeature);
            return name != null ? name.toString() : null;
        }
        return null;
    }

    private static String getObjectIdentifier(EObject obj) {
        if (obj == null) {
            return "null";
        }
        String name = getName(obj);
        if (name != null) {
            return obj.eClass().getName() + ":" + name;
        }
        return obj.eClass().getName() + "@" + System.identityHashCode(obj);
    }

    /**
     * Result of a model comparison.
     */
    public static class ComparisonResult {
        private final List<String> differences;

        public ComparisonResult(List<String> differences) {
            this.differences = differences != null ? differences : Collections.emptyList();
        }

        /**
         * Returns true if the compared models are equivalent.
         */
        public boolean isEquivalent() {
            return differences.isEmpty();
        }

        /**
         * Returns a formatted string of all differences found.
         */
        public String getDifferences() {
            if (differences.isEmpty()) {
                return "No differences";
            }
            return String.join("\n", differences);
        }

        /**
         * Returns the list of differences.
         */
        public List<String> getDifferenceList() {
            return Collections.unmodifiableList(differences);
        }
    }
}
