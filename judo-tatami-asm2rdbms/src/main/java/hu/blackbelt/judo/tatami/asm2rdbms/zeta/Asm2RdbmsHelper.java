package hu.blackbelt.judo.tatami.asm2rdbms.zeta;

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

import com.google.common.hash.Hashing;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.rdbms.RdbmsField;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMapping;
import hu.blackbelt.judo.tatami.asm2rdbms.AbbreviateUtils;
import org.eclipse.emf.ecore.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class containing shared utility methods for ASM to RDBMS transformation rules.
 * <p>
 * This class provides common functionality used across all rule files to avoid code duplication:
 * <ul>
 *   <li>ID generation from annotations or element names</li>
 *   <li>SQL name generation for tables, columns, foreign keys</li>
 *   <li>Type filling from type mappings</li>
 *   <li>MD5 hashing for SQL names</li>
 *   <li>String utilities (case conversion, abbreviation)</li>
 * </ul>
 * </p>
 * <p>
 * Thread Safety: This class uses ConcurrentHashMaps for all caches and is safe for use
 * in parallel transformation execution.
 * </p>
 */
public final class Asm2RdbmsHelper {

    // Cache for element IDs
    private static final Map<EObject, String> ELEMENT_ID_CACHE = new ConcurrentHashMap<>();

    private Asm2RdbmsHelper() {
        // Utility class - prevent instantiation
    }

    // =========================================================================
    // ID METHODS
    // =========================================================================

    /**
     * Get the ID for an ASM element.
     * <p>
     * For EModelElements, checks for the ExtendedMetadata/id annotation.
     * For ENamedElements, falls back to the element name.
     * Falls back to identity hash code for other elements.
     * </p>
     *
     * @param element the element to get the ID for
     * @return the element's ID string
     */
    public static String getId(EObject element) {
        return ELEMENT_ID_CACHE.computeIfAbsent(element, e -> {
            if (e instanceof EModelElement) {
                EAnnotation ann = ((EModelElement) e).getEAnnotation("http://blackbelt.hu/judo/meta/ExtendedMetadata/id");
                if (ann != null) {
                    String value = ann.getDetails().get("value");
                    if (value != null) {
                        return value;
                    }
                }
            }
            if (e instanceof ENamedElement) {
                return ((ENamedElement) e).getName();
            }
            return String.valueOf(System.identityHashCode(e));
        });
    }

    // =========================================================================
    // SQL NAME METHODS
    // =========================================================================

    /**
     * Generate SQL table name for an EClass.
     *
     * @param eClass           the entity class
     * @param tableNameMaxSize maximum table name size
     * @param tablePrefix      table name prefix (e.g., "T_")
     * @param asmUtils         AsmUtils instance for FQN generation
     * @return the SQL table name
     */
    public static String tableSqlName(EClass eClass, int tableNameMaxSize, String tablePrefix, AsmUtils asmUtils) {
        String name = asmUtils.getClassifierFQName(eClass).replace(".", "_").toUpperCase();
        return abbreviate(tablePrefix + name, tableNameMaxSize);
    }

    /**
     * Generate SQL field name for an EAttribute.
     *
     * @param attr              the attribute
     * @param columnNameMaxSize maximum column name size
     * @param columnPrefix      column name prefix (e.g., "C_")
     * @return the SQL field name
     */
    public static String fieldSqlName(EAttribute attr, int columnNameMaxSize, String columnPrefix) {
        String name = attr.getName().toUpperCase();
        return abbreviate(columnPrefix + name, columnNameMaxSize);
    }

    /**
     * Generate SQL identifier name for an EReference.
     *
     * @param ref               the reference
     * @param columnNameMaxSize maximum column name size
     * @param columnPrefix      column name prefix (e.g., "C_")
     * @return the SQL identifier name
     */
    public static String referenceIdentifierSqlName(EReference ref, int columnNameMaxSize, String columnPrefix) {
        String name = ref.getName().toUpperCase() + "_ID";
        return abbreviate(columnPrefix + name, columnNameMaxSize);
    }

    /**
     * Generate SQL foreign key name for an EReference.
     *
     * @param ref              the reference
     * @param foreignKeyPrefix foreign key prefix (e.g., "FK_")
     * @return the SQL foreign key name
     */
    public static String referenceFkSqlName(EReference ref, String foreignKeyPrefix) {
        return foreignKeyPrefix + md5("(asm/" + getId(ref) + ")/TableForeignKey");
    }

    /**
     * Generate SQL inverse identifier name for an EReference.
     *
     * @param ref               the reference
     * @param columnNameMaxSize maximum column name size
     * @param columnPrefix      column name prefix (e.g., "C_")
     * @return the SQL inverse identifier name
     */
    public static String referenceInverseIdentifierSqlName(EReference ref, int columnNameMaxSize, String columnPrefix) {
        String name = ref.getEContainingClass().getName().toUpperCase() + "_" + ref.getName().toUpperCase() + "_ID";
        return abbreviate(columnPrefix + name, columnNameMaxSize);
    }

    /**
     * Generate SQL inverse foreign key name for an EReference.
     *
     * @param ref                    the reference
     * @param inverseForeignKeyPrefix inverse foreign key prefix (e.g., "FK_INV_")
     * @return the SQL inverse foreign key name
     */
    public static String referenceInvFkSqlName(EReference ref, String inverseForeignKeyPrefix) {
        return inverseForeignKeyPrefix + md5("(asm/" + getId(ref) + ")/TableInverseForeignKey");
    }

    /**
     * Generate SQL unidirectional foreign key name for an EReference.
     *
     * @param ref              the reference
     * @param foreignKeyPrefix foreign key prefix (e.g., "FK_")
     * @return the SQL unidirectional foreign key name
     */
    public static String referenceUniFkSqlName(EReference ref, String foreignKeyPrefix) {
        return foreignKeyPrefix + md5("(asm/" + getId(ref) + ")/JunctionTableForeignKeyUnidirectional");
    }

    /**
     * Generate SQL junction table name for a many-to-many reference.
     *
     * @param ref                  the reference
     * @param tableNameMaxSize     maximum table name size
     * @param junctionTablePrefix  junction table prefix (e.g., "J_")
     * @return the SQL junction table name
     */
    public static String referenceManyToManyTableSqlName(EReference ref, int tableNameMaxSize, String junctionTablePrefix) {
        String name = ref.getEContainingClass().getName() + "_" + ref.getName();
        return abbreviate(junctionTablePrefix + name.toUpperCase(), tableNameMaxSize);
    }

    /**
     * Generate SQL long name for a reference (used in FK column names).
     *
     * @param ref the reference
     * @return the long name (ContainingClass_referenceName)
     */
    public static String sqlLongName(EReference ref) {
        return ref.getEContainingClass().getName() + "_" + ref.getName();
    }

    // =========================================================================
    // TYPE METHODS
    // =========================================================================

    /**
     * Fill RDBMS type information from type mapping.
     *
     * @param field        the RDBMS field to fill
     * @param javaType     the Java type name (e.g., "java.lang.String")
     * @param attr         the source attribute (may be null)
     * @param typeMappings the type mapping table
     * @param asmUtils     AsmUtils instance for annotation access
     */
    public static void fillType(RdbmsField field, String javaType, EAttribute attr,
                                 Map<String, TypeMapping> typeMappings, AsmUtils asmUtils) {
        TypeMapping typeMapping = typeMappings.get(javaType);
        if (typeMapping != null) {
            field.setRdbmsTypeName(typeMapping.getRdbmsType());

            String rdbmsSize = typeMapping.getRdbmsSize();
            if (rdbmsSize != null && !rdbmsSize.isEmpty()) {
                if (rdbmsSize.startsWith("#") && attr != null) {
                    // Annotation-based size (e.g., #constraints:maxLength)
                    String[] parts = rdbmsSize.substring(1).split(":", 2);
                    if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                        String annotation = parts[0];
                        String annotationKey = parts[1];
                        Optional<String> maxLength = asmUtils.getExtensionAnnotationCustomValue(attr, annotation, annotationKey, false);
                        if (maxLength.isPresent()) {
                            try {
                                field.setSize(Integer.parseInt(maxLength.get()));
                            } catch (NumberFormatException e) {
                                // Ignore invalid size from annotation
                            }
                        }
                    }
                } else {
                    try {
                        field.setSize((int) Double.parseDouble(rdbmsSize));
                    } catch (NumberFormatException e) {
                        // Ignore invalid size
                    }
                }
            }

            String rdbmsPrecision = typeMapping.getRdbmsPrecision();
            if (rdbmsPrecision != null && !rdbmsPrecision.isEmpty()) {
                if (rdbmsPrecision.startsWith("#") && attr != null) {
                    String[] parts = rdbmsPrecision.substring(1).split(":", 2);
                    if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                        String annotation = parts[0];
                        String annotationKey = parts[1];
                        Optional<String> precision = asmUtils.getExtensionAnnotationCustomValue(attr, annotation, annotationKey, false);
                        if (precision.isPresent()) {
                            try {
                                field.setPrecision(Integer.parseInt(precision.get()));
                            } catch (NumberFormatException e) {
                                // Ignore invalid precision from annotation
                            }
                        }
                    }
                } else {
                    try {
                        field.setPrecision((int) Double.parseDouble(rdbmsPrecision));
                    } catch (NumberFormatException e) {
                        // Ignore invalid precision
                    }
                }
            }

            String rdbmsScale = typeMapping.getRdbmsScale();
            if (rdbmsScale != null && !rdbmsScale.isEmpty()) {
                if (rdbmsScale.startsWith("#") && attr != null) {
                    String[] parts = rdbmsScale.substring(1).split(":", 2);
                    if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                        String annotation = parts[0];
                        String annotationKey = parts[1];
                        Optional<String> scale = asmUtils.getExtensionAnnotationCustomValue(attr, annotation, annotationKey, false);
                        if (scale.isPresent()) {
                            try {
                                field.setScale(Integer.parseInt(scale.get()));
                            } catch (NumberFormatException e) {
                                // Ignore invalid scale from annotation
                            }
                        }
                    }
                } else {
                    try {
                        field.setScale((int) Double.parseDouble(rdbmsScale));
                    } catch (NumberFormatException e) {
                        // Ignore invalid scale
                    }
                }
            }

            field.setStorageByte(-1);
        }
    }

    /**
     * Copy type information from one field to another.
     *
     * @param target the target field
     * @param source the source field
     */
    public static void copyTypeFromField(RdbmsField target, RdbmsField source) {
        target.setRdbmsTypeName(source.getRdbmsTypeName());
        target.setSize(source.getSize());
        target.setPrecision(source.getPrecision());
        target.setScale(source.getScale());
        target.setStorageByte(source.getStorageByte());
    }

    // =========================================================================
    // STRING UTILITY METHODS
    // =========================================================================

    /**
     * Abbreviate a string to a maximum length.
     *
     * @param name      the string to abbreviate
     * @param maxLength maximum length
     * @return the abbreviated string
     */
    public static String abbreviate(String name, int maxLength) {
        return AbbreviateUtils.abbreviate(name, maxLength, "_");
    }

    /**
     * Calculate MD5 hash of a string.
     *
     * @param input the input string
     * @return the MD5 hash as a hex string
     */
    public static String md5(String input) {
        return Hashing.md5().hashString(input, StandardCharsets.UTF_8).toString();
    }

    /**
     * Convert first character to lowercase.
     *
     * @param str the string
     * @return the string with first character lowercase
     */
    public static String firstToLowerCase(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Convert first character to uppercase.
     *
     * @param str the string
     * @return the string with first character uppercase
     */
    public static String firstToUpperCase(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    // =========================================================================
    // REFERENCE UTILITY METHODS
    // =========================================================================

    /**
     * Check if a reference is mandatory (lower bound > 0).
     *
     * @param ref the reference
     * @return true if mandatory
     */
    public static boolean isMandatory(EReference ref) {
        return ref.getLowerBound() > 0;
    }

    // =========================================================================
    // PACKAGE NAVIGATION METHODS
    // =========================================================================

    /**
     * Get the root package for a classifier.
     *
     * @param classifier the classifier
     * @return the root EPackage
     */
    public static EPackage getRootPackage(EClassifier classifier) {
        EPackage pkg = classifier.getEPackage();
        while (pkg != null && pkg.getESuperPackage() != null) {
            pkg = pkg.getESuperPackage();
        }
        return pkg;
    }

    // =========================================================================
    // CACHE MANAGEMENT
    // =========================================================================

    /**
     * Clears all caches. Call this between transformation runs if reusing the helper.
     */
    public static void clearCaches() {
        ELEMENT_ID_CACHE.clear();
    }
}
