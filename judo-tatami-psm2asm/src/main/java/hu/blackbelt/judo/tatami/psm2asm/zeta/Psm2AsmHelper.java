package hu.blackbelt.judo.tatami.psm2asm.zeta;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.psm.data.EntityType;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.type.NumericType;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import org.eclipse.emf.ecore.EAnnotation;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EModelElement;
import org.eclipse.emf.ecore.ENamedElement;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class containing shared utility methods for PSM to ASM transformation rules.
 * <p>
 * This class provides common functionality used across all rule files to avoid code duplication:
 * <ul>
 *   <li>ID generation and setting</li>
 *   <li>Annotation creation and manipulation</li>
 *   <li>Qualified name building</li>
 *   <li>Type classification (integer vs decimal)</li>
 *   <li>Java class name resolution</li>
 * </ul>
 * </p>
 * <p>
 * Thread Safety: This class uses ConcurrentHashMaps for all caches and is safe for use
 * in parallel transformation execution.
 * </p>
 */
public final class Psm2AsmHelper {

    // Cache for annotation URIs to avoid repeated string concatenation
    private static final Map<String, String> ANNOTATION_URI_CACHE = new ConcurrentHashMap<>();

    // Cache for namespace element string representations
    private static final Map<NamespaceElement, String> NAMESPACE_ELEMENT_STRING_CACHE = new ConcurrentHashMap<>();

    // Cache for element IDs
    private static final Map<EObject, String> ELEMENT_ID_CACHE = new ConcurrentHashMap<>();

    private Psm2AsmHelper() {
        // Utility class - prevent instantiation
    }

    // =========================================================================
    // ID METHODS
    // =========================================================================

    /**
     * Get the ID for a PSM element.
     * <p>
     * For NamespaceElements, returns the qualified name with underscore separators.
     * For EModelElements, checks for an ID annotation.
     * Falls back to identity hash code for other elements.
     * </p>
     *
     * @param element the element to get the ID for
     * @return the element's ID string
     */
    public static String getId(Object element) {
        if (element instanceof NamespaceElement) {
            return getQualifiedNameWithUnderscore((NamespaceElement) element);
        }
        if (element instanceof EObject) {
            return ELEMENT_ID_CACHE.computeIfAbsent((EObject) element, e -> {
                if (e instanceof EModelElement) {
                    for (EAnnotation ann : ((EModelElement) e).getEAnnotations()) {
                        if ("http://blackbelt.hu/judo/meta/ExtendedMetadata/id".equals(ann.getSource())) {
                            String value = ann.getDetails().get("value");
                            if (value != null) {
                                return value;
                            }
                        }
                    }
                }
                if (e instanceof ENamedElement) {
                    return ((ENamedElement) e).getName();
                }
                return String.valueOf(System.identityHashCode(e));
            });
        }
        return String.valueOf(System.identityHashCode(element));
    }

    // =========================================================================
    // QUALIFIED NAME METHODS
    // =========================================================================

    /**
     * Convert a NamespaceElement to its qualified string representation with dot separators.
     *
     * @param element the namespace element
     * @return the qualified name with . separators (e.g., "model.package.Element")
     */
    public static String namespaceElementToString(NamespaceElement element) {
        return getQualifiedName(element);
    }

    /**
     * Gets the fully qualified name of a namespace element with dot separators.
     * Results are cached to avoid repeated hierarchy traversal.
     *
     * @param element the namespace element
     * @return the qualified name with . separators
     */
    public static String getQualifiedName(NamespaceElement element) {
        if (element == null) {
            return "";
        }
        // Cache stores raw format with ::, we replace to . at return time
        String cached = NAMESPACE_ELEMENT_STRING_CACHE.computeIfAbsent(element, e -> {
            StringBuilder sb = new StringBuilder();
            buildQualifiedName(e, sb);
            return sb.toString();
        });
        return cached.replace("::", ".");
    }

    /**
     * Gets the qualified name with :: separator (matches ETL psmUtils.namespaceElementToString format).
     * Used for actor annotation names where enrichWithAnnotations expects :: format.
     *
     * @param element the namespace element
     * @return the qualified name with :: separators (e.g., "model::package::Element")
     */
    public static String getQualifiedNameWithColons(NamespaceElement element) {
        if (element == null) {
            return "";
        }
        // Cache stores raw format with ::
        return NAMESPACE_ELEMENT_STRING_CACHE.computeIfAbsent(element, e -> {
            StringBuilder sb = new StringBuilder();
            buildQualifiedName(e, sb);
            return sb.toString();
        });
    }

    /**
     * Gets the fully qualified name with underscore separator (for IDs).
     *
     * @param element the namespace element
     * @return the qualified name with _ separators
     */
    public static String getQualifiedNameWithUnderscore(NamespaceElement element) {
        if (element == null) {
            return "";
        }
        // Cache stores raw format with ::, we replace to _ at return time
        String cached = NAMESPACE_ELEMENT_STRING_CACHE.computeIfAbsent(element, e -> {
            StringBuilder sb = new StringBuilder();
            buildQualifiedName(e, sb);
            return sb.toString();
        });
        return cached.replace("::", "_");
    }

    /**
     * Recursively builds the qualified name by traversing the container hierarchy.
     * Handles both NamespaceElement containers and Namespace containers (Package, Model).
     */
    private static void buildQualifiedName(NamespaceElement element, StringBuilder sb) {
        if (element.eContainer() instanceof NamespaceElement) {
            buildQualifiedName((NamespaceElement) element.eContainer(), sb);
            sb.append("::");
        } else if (element.eContainer() instanceof Namespace) {
            // Handle Package or Model container (Namespace but not NamespaceElement)
            // Need to recursively build the namespace's qualified name too
            Namespace namespace = (Namespace) element.eContainer();
            buildQualifiedNameForNamespace(namespace, sb);
            sb.append("::");
        }
        sb.append(element.getName());
    }
    
    /**
     * Recursively builds the qualified name for a Namespace (Package or Model).
     * This handles the case where a Package is nested in another Package or Model.
     */
    private static void buildQualifiedNameForNamespace(Namespace namespace, StringBuilder sb) {
        if (namespace.eContainer() instanceof Namespace) {
            // Package nested in another namespace (Package or Model)
            buildQualifiedNameForNamespace((Namespace) namespace.eContainer(), sb);
            sb.append("::");
        }
        // else: this is the root Model, just add its name
        sb.append(namespace.getName());
    }

    // =========================================================================
    // ANNOTATION METHODS
    // =========================================================================

    /**
     * Gets the annotation URI for a given annotation name, with caching.
     *
     * @param annotationName the annotation name (e.g., "constraints", "documentation")
     * @return the full annotation URI
     */
    public static String getAnnotationUri(String annotationName) {
        return ANNOTATION_URI_CACHE.computeIfAbsent(annotationName, AsmUtils::getAnnotationUri);
    }

    /**
     * Creates an EAnnotation with the given source URI.
     * Note: The id parameter is kept for API compatibility but is no longer used
     * since XMI IDs are now handled automatically by the Zeta framework.
     *
     * @param id     unused (kept for API compatibility)
     * @param source the source URI for the annotation
     * @return the created annotation
     */
    public static EAnnotation createAnnotation(String id, String source) {
        EAnnotation annotation = EcoreFactory.eINSTANCE.createEAnnotation();
        annotation.setSource(source);
        return annotation;
    }

    /**
     * Adds a key-value detail to an annotation.
     *
     * @param annotation the annotation to add the detail to
     * @param key        the detail key
     * @param value      the detail value
     */
    public static void addAnnotationDetail(EAnnotation annotation, String key, String value) {
        if (annotation != null && key != null && value != null) {
            annotation.getDetails().put(key, value);
        }
    }

    // =========================================================================
    // NUMERIC TYPE METHODS
    // =========================================================================

    /**
     * Checks if a NumericType represents an integer (scale == 0).
     *
     * @param numericType the numeric type to check
     * @return true if the type is an integer, false if decimal
     */
    public static boolean isInteger(NumericType numericType) {
        return numericType != null && numericType.getScale() == 0;
    }

    /**
     * Checks if a NumericType represents a decimal (scale > 0).
     *
     * @param numericType the numeric type to check
     * @return true if the type is a decimal, false if integer
     */
    public static boolean isDecimal(NumericType numericType) {
        return numericType != null && numericType.getScale() > 0;
    }

    /**
     * Gets the Java class name for an integer NumericType based on its precision.
     * <ul>
     *   <li>precision <= 9: java.lang.Integer</li>
     *   <li>precision <= 19: java.lang.Long</li>
     *   <li>precision > 19: java.math.BigDecimal</li>
     * </ul>
     *
     * @param numericType the numeric type
     * @return the Java class name
     */
    public static String getIntegerClassName(NumericType numericType) {
        int precision = numericType.getPrecision();
        if (precision <= 9 && precision > 0) {
            return "java.lang.Integer";
        } else if (precision <= 19 && precision > 9) {
            return "java.lang.Long";
        } else {
            return "java.math.BigDecimal";
        }
    }

    /**
     * Gets the Java class name for a decimal NumericType based on its precision and scale.
     * <ul>
     *   <li>precision <= 7 and scale <= 4: java.lang.Float</li>
     *   <li>precision <= 15 and scale <= 4: java.lang.Double</li>
     *   <li>otherwise: java.math.BigDecimal</li>
     * </ul>
     *
     * @param numericType the numeric type
     * @return the Java class name
     */
    public static String getDecimalClassName(NumericType numericType) {
        int precision = numericType.getPrecision();
        int scale = numericType.getScale();
        if (precision <= 7 && precision > 0 && scale <= 4) {
            return "java.lang.Float";
        } else if (precision <= 15 && precision > 7 && scale <= 4) {
            return "java.lang.Double";
        } else {
            return "java.math.BigDecimal";
        }
    }

    // =========================================================================
    // STRING UTILITY METHODS
    // =========================================================================

    /**
     * Capitalizes the first character of a string.
     *
     * @param str the string to capitalize
     * @return the capitalized string, or the original if null/empty
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    // =========================================================================
    // CONTAINER NAVIGATION METHODS
    // =========================================================================

    /**
     * Gets the container package for an element by walking up the container hierarchy
     * to find the nearest Namespace (Model or Package) and looking up its equivalent EPackage.
     * <p>
     * This method is used across multiple rule classes to find the target package
     * for newly created ASM elements.
     * </p>
     *
     * @param element the source PSM element
     * @param ctx     the transformation context for equivalent lookups
     * @return the equivalent EPackage, or null if not found
     */
    public static EPackage getContainerPackage(EObject element, TransformationContext ctx) {
        EObject container = element.eContainer();
        while (container != null) {
            if (container instanceof Namespace) {
                EPackage pkg = ctx.equivalent(container, EPackage.class);
                if (pkg != null) {
                    return pkg;
                }
            }
            container = container.eContainer();
        }
        return null;
    }

    /**
     * Gets the owning EntityType for an element by walking up the container hierarchy.
     * <p>
     * This method is used to find the parent entity for attributes, relations,
     * operations, and other entity members.
     * </p>
     *
     * @param element the element to find the owning entity for
     * @return the owning EntityType, or null if the element is not contained in an entity
     */
    public static EntityType getEntityType(EObject element) {
        EObject container = element.eContainer();
        while (container != null) {
            if (container instanceof EntityType) {
                return (EntityType) container;
            }
            container = container.eContainer();
        }
        return null;
    }

    /**
     * Gets the fully qualified name of an EClassifier by traversing its package hierarchy.
     * <p>
     * The result is in the format "rootPackage.subPackage.ClassName".
     * </p>
     *
     * @param classifier the classifier to get the FQN for
     * @return the fully qualified name, or null if classifier is null
     */
    public static String getClassifierFQName(EClassifier classifier) {
        if (classifier == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        EPackage pkg = classifier.getEPackage();
        while (pkg != null) {
            if (sb.length() > 0) {
                sb.insert(0, ".");
            }
            sb.insert(0, pkg.getName());
            pkg = pkg.getESuperPackage();
        }
        sb.append(".").append(classifier.getName());
        return sb.toString();
    }

    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================

    /**
     * Clears all caches. Call this between transformation runs if reusing the helper.
     */
    public static void clearCaches() {
        ANNOTATION_URI_CACHE.clear();
        NAMESPACE_ELEMENT_STRING_CACHE.clear();
        ELEMENT_ID_CACHE.clear();
    }
}
