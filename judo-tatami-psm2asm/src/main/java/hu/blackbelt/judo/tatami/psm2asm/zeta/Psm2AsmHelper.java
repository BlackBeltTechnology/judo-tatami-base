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
import hu.blackbelt.judo.meta.psm.namespace.Model;
import hu.blackbelt.judo.meta.psm.namespace.Namespace;
import hu.blackbelt.judo.meta.psm.namespace.NamespaceElement;
import hu.blackbelt.judo.meta.psm.namespace.NamedElement;
import hu.blackbelt.judo.meta.psm.namespace.Package;
import hu.blackbelt.judo.meta.psm.service.TransferObjectType;
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
    // NAMESPACE URI METHODS
    // =========================================================================

    /**
     * Computes the full nsURI for a PSM Package by traversing the source hierarchy.
     * This is used instead of relying on the target EPackage's nsURI which may not
     * be set yet in parallel transformation execution.
     *
     * @param pkg     the PSM Package to compute nsURI for
     * @param baseUri the base URI prefix (from context "nsURI" attribute)
     * @return the full namespace URI
     */
    public static String computeNsUriFromSource(Package pkg, String baseUri) {
        StringBuilder sb = new StringBuilder();
        buildNsUriPath(pkg, sb);
        // The path starts from the model name, so we prepend the baseUri
        return baseUri + sb.toString();
    }

    /**
     * Recursively builds the nsURI path from PSM Package hierarchy.
     * Path format: /modelName/package1/package2/.../packageN
     */
    private static void buildNsUriPath(Namespace namespace, StringBuilder sb) {
        if (namespace.eContainer() instanceof Namespace) {
            buildNsUriPath((Namespace) namespace.eContainer(), sb);
        }
        sb.append("/").append(namespace.getName());
    }

    /**
     * Computes the full nsPrefix for a PSM Package by traversing the source hierarchy.
     * This is used instead of relying on the target EPackage's nsPrefix which may not
     * be set yet in parallel transformation execution.
     *
     * @param pkg        the PSM Package to compute nsPrefix for
     * @param basePrefix the base prefix (from context "nsPrefix" attribute)
     * @return the full namespace prefix
     */
    public static String computeNsPrefixFromSource(Package pkg, String basePrefix) {
        StringBuilder sb = new StringBuilder();
        buildNsPrefixPath(pkg, sb);
        // The path starts from the model name (capitalized), so we prepend the basePrefix
        return basePrefix + sb.toString();
    }

    /**
     * Recursively builds the nsPrefix path from PSM Package hierarchy.
     * Capitalizes each namespace name segment.
     */
    private static void buildNsPrefixPath(Namespace namespace, StringBuilder sb) {
        if (namespace.eContainer() instanceof Namespace) {
            buildNsPrefixPath((Namespace) namespace.eContainer(), sb);
        }
        sb.append(capitalize(namespace.getName()));
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
     * Gets the rule name for transforming a classifier type to EClass.
     * Returns CREATE_ENTITY_CLASS for EntityType, CREATE_TRANSFER_OBJECT_TYPE_CLASS for TransferObjectType.
     *
     * @param type the type to get the rule name for
     * @return the rule name constant for the appropriate transformation
     */
    public static String getEClassRuleName(NamedElement type) {
        if (type instanceof EntityType) {
            return "CreateEntityClass";
        } else if (type instanceof TransferObjectType) {
            return "CreateTransferObjectTypeClass";
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
    // THREAD-SAFE COLLECTION OPERATIONS
    // =========================================================================
    //
    // IMPORTANT: We synchronize on the CONTAINER OBJECT, not the EList itself.
    // EMF's EList.add() internally calls contains() which iterates through all elements.
    // Synchronizing only on the list doesn't prevent concurrent modification during
    // iteration because EMF may access the underlying array directly.
    // By synchronizing on the container, we serialize ALL operations on that object's
    // collections, preventing race conditions in EMF's internal iteration logic.
    //

    /**
     * Thread-safe add of a classifier to a package.
     * EMF ELists are not thread-safe, so concurrent adds from parallel transformation
     * threads can cause ArrayIndexOutOfBoundsException or ConcurrentModificationException.
     * <p>
     * This method also prevents duplicate classifiers with the same name, which can occur
     * when parallel threads race to transform the same source element before the framework's
     * cache is updated.
     *
     * @param pkg the target package
     * @param classifier the classifier to add
     */
    public static void addClassifier(EPackage pkg, EClassifier classifier) {
        if (pkg != null && classifier != null) {
            synchronized (pkg) {
                // Prevent duplicate classifiers - can occur due to race conditions in parallel execution
                // where multiple threads transform the same source element before cache is updated
                String name = classifier.getName();
                if (name != null) {
                    boolean exists = pkg.getEClassifiers().stream()
                            .anyMatch(c -> name.equals(c.getName()));
                    if (exists) {
                        return; // Skip duplicate
                    }
                }
                pkg.getEClassifiers().add(classifier);
            }
        }
    }

    /**
     * Thread-safe add of an operation to a class.
     *
     * @param eClass the target class
     * @param operation the operation to add
     */
    public static void addOperation(org.eclipse.emf.ecore.EClass eClass, org.eclipse.emf.ecore.EOperation operation) {
        if (eClass != null && operation != null) {
            synchronized (eClass) {
                eClass.getEOperations().add(operation);
            }
        }
    }

    /**
     * Thread-safe add of a structural feature (attribute or reference) to a class.
     *
     * @param eClass the target class
     * @param feature the structural feature to add
     */
    public static void addStructuralFeature(org.eclipse.emf.ecore.EClass eClass, org.eclipse.emf.ecore.EStructuralFeature feature) {
        if (eClass != null && feature != null) {
            synchronized (eClass) {
                eClass.getEStructuralFeatures().add(feature);
            }
        }
    }

    /**
     * Thread-safe add of a sub-package to a package.
     *
     * @param parent the parent package
     * @param child the child package to add
     */
    public static void addSubPackage(EPackage parent, EPackage child) {
        if (parent != null && child != null) {
            synchronized (parent) {
                parent.getESubpackages().add(child);
            }
        }
    }

    /**
     * Thread-safe add of a super type to a class.
     *
     * @param eClass the target class
     * @param superType the super type to add
     */
    public static void addSuperType(org.eclipse.emf.ecore.EClass eClass, org.eclipse.emf.ecore.EClass superType) {
        if (eClass != null && superType != null) {
            synchronized (eClass) {
                eClass.getESuperTypes().add(superType);
            }
        }
    }

    /**
     * Thread-safe add of an annotation to a model element.
     *
     * @param element the target element
     * @param annotation the annotation to add
     */
    public static void addAnnotation(org.eclipse.emf.ecore.EModelElement element, EAnnotation annotation) {
        if (element != null && annotation != null) {
            synchronized (element) {
                // Insert annotation in sorted position to ensure deterministic ordering
                // in parallel transformation execution
                insertAnnotationSorted(element.getEAnnotations(), annotation);
            }
        }
    }

    /**
     * Inserts an annotation into a list in sorted order.
     * Sorting is based on source URI, then by details content (specifically 'owner' for behavior annotations).
     * This ensures deterministic ordering regardless of parallel execution order.
     */
    private static void insertAnnotationSorted(org.eclipse.emf.common.util.EList<EAnnotation> annotations, EAnnotation newAnnotation) {
        String newKey = getAnnotationSortKey(newAnnotation);

        // Find the correct insertion position
        int insertPos = 0;
        for (int i = 0; i < annotations.size(); i++) {
            String existingKey = getAnnotationSortKey(annotations.get(i));
            if (newKey.compareTo(existingKey) > 0) {
                insertPos = i + 1;
            } else {
                break;
            }
        }

        if (insertPos >= annotations.size()) {
            annotations.add(newAnnotation);
        } else {
            annotations.add(insertPos, newAnnotation);
        }
    }

    /**
     * Gets a sort key for an annotation based on its source and details.
     * For behavior annotations, includes the 'owner' detail to ensure consistent ordering.
     */
    private static String getAnnotationSortKey(EAnnotation annotation) {
        StringBuilder key = new StringBuilder();
        key.append(annotation.getSource() != null ? annotation.getSource() : "");

        // Include details in sort key, especially 'owner' for behavior annotations
        if (annotation.getDetails() != null && !annotation.getDetails().isEmpty()) {
            // Sort details by key for consistent comparison
            annotation.getDetails().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> key.append("|").append(entry.getKey()).append("=").append(entry.getValue()));
        }

        return key.toString();
    }

    /**
     * Thread-safe add of an enum literal to an enum.
     *
     * @param eEnum the target enum
     * @param literal the literal to add
     */
    public static void addEnumLiteral(org.eclipse.emf.ecore.EEnum eEnum, org.eclipse.emf.ecore.EEnumLiteral literal) {
        if (eEnum != null && literal != null) {
            synchronized (eEnum) {
                eEnum.getELiterals().add(literal);
            }
        }
    }

    /**
     * Thread-safe add of a parameter to an operation.
     *
     * @param operation the target operation
     * @param parameter the parameter to add
     */
    public static void addParameter(org.eclipse.emf.ecore.EOperation operation, org.eclipse.emf.ecore.EParameter parameter) {
        if (operation != null && parameter != null) {
            synchronized (operation) {
                operation.getEParameters().add(parameter);
            }
        }
    }

    // =========================================================================
    // TYPE RESOLUTION
    // =========================================================================

    /**
     * Resolve an EClassifier by name from the target model.
     * Used as fallback when ctx.equivalent() returns null due to object identity mismatch
     * (e.g., extension transfer object types whose dataType references are copies/proxies).
     *
     * @param typeName the name of the type to find
     * @param ctx      the transformation context providing access to target resources
     * @return the matching EClassifier, or null if not found
     */
    public static EClassifier resolveTypeByName(String typeName, TransformationContext ctx) {
        if (typeName == null) {
            return null;
        }
        for (var resource : ctx.getTargetResourceSet().getResources()) {
            var it = resource.getAllContents();
            while (it.hasNext()) {
                var obj = it.next();
                if (obj instanceof EClassifier classifier && typeName.equals(classifier.getName())) {
                    return classifier;
                }
            }
        }
        return null;
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
