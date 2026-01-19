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
import org.eclipse.emf.ecore.resource.Resource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

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

    // Cache for resource IDs (XMI IDs)
    private static final Map<EObject, String> RESOURCE_ID_CACHE = new ConcurrentHashMap<>();

    // Patterns for CamelCase to UPPER_SNAKE_CASE conversion
    private static final Pattern CAMEL_CASE_PATTERN_1 = Pattern.compile("(.)([A-Z][a-z]+)");
    private static final Pattern CAMEL_CASE_PATTERN_2 = Pattern.compile("([a-z0-9])([A-Z])");

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

    /**
     * Get the XMI resource ID for an element.
     * <p>
     * This returns the ID as stored in the EMF resource (XMI ID), which is what
     * the ETL transformation uses via {@code self.eResource.getId(self)}.
     * </p>
     *
     * @param element the element to get the resource ID for
     * @return the XMI resource ID, or null if not available
     */
    public static String getResourceId(EObject element) {
        return RESOURCE_ID_CACHE.computeIfAbsent(element, e -> {
            Resource resource = e.eResource();
            if (resource != null) {
                String id = resource.getURIFragment(e);
                // The URI fragment is the XMI ID for XMI resources
                if (id != null && !id.startsWith("/")) {
                    return id;
                }
            }
            // Fallback to annotation-based ID
            return getId(e);
        });
    }

    // =========================================================================
    // STRING CONVERSION METHODS
    // =========================================================================

    /**
     * Convert a CamelCase string to UPPER_SNAKE_CASE.
     * <p>
     * This matches the ETL's {@code toUpperSnakeCase()} operation.
     * Examples:
     * <ul>
     *   <li>primaryUserAddress → PRIMARY_USER_ADDRESS</li>
     *   <li>firstName → FIRST_NAME</li>
     *   <li>XMLParser → XML_PARSER</li>
     * </ul>
     * </p>
     *
     * @param str the CamelCase string
     * @return the UPPER_SNAKE_CASE string
     */
    public static String toUpperSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String result = CAMEL_CASE_PATTERN_1.matcher(str).replaceAll("$1_$2");
        result = CAMEL_CASE_PATTERN_2.matcher(result).replaceAll("$1_$2");
        return result.toUpperCase();
    }

    /**
     * Get the SQL long name for an ENamedElement.
     * <p>
     * This matches the ETL's {@code sqlLongName()} operation.
     * Checks for a {@code shortName} annotation first, falling back to the element name
     * with abbreviation.
     * </p>
     *
     * @param element    the named element
     * @param nameSize   maximum size for abbreviation (sqlNameSize)
     * @param asmUtils   AsmUtils instance for annotation access
     * @return the SQL long name
     */
    public static String sqlLongName(ENamedElement element, int nameSize, AsmUtils asmUtils) {
        // Check for shortName annotation
        if (element instanceof EModelElement) {
            Optional<String> shortName = asmUtils.getExtensionAnnotationValue((EModelElement) element, "shortName", false);
            if (shortName.isPresent()) {
                return toUpperSnakeCase(shortName.get());
            }
        }
        // Fall back to abbreviated name - apply toUpperSnakeCase for proper CamelCase to UPPER_SNAKE_CASE conversion
        String upperSnake = toUpperSnakeCase(element.getName());
        return abbreviate(upperSnake, nameSize);
    }

    /**
     * Get the abbreviated SQL name for an ENamedElement.
     * <p>
     * This matches the ETL's {@code sqlName()} operation.
     * Uses different abbreviation size based on createSimpleName flag.
     * </p>
     *
     * @param element          the named element
     * @param shortNameSize    maximum size when createSimpleName is false
     * @param nameSize         maximum size when createSimpleName is true
     * @param createSimpleName whether to use simple naming
     * @param asmUtils         AsmUtils instance for annotation access
     * @return the SQL name
     */
    public static String sqlName(ENamedElement element, int shortNameSize, int nameSize,
                                  boolean createSimpleName, AsmUtils asmUtils) {
        // Check for shortName annotation
        if (element instanceof EModelElement) {
            Optional<String> shortName = asmUtils.getExtensionAnnotationValue((EModelElement) element, "shortName", false);
            if (shortName.isPresent()) {
                return toUpperSnakeCase(shortName.get());
            }
        }
        // Fall back to abbreviated name with appropriate size - apply toUpperSnakeCase for proper conversion
        int maxSize = createSimpleName ? nameSize : shortNameSize;
        String upperSnake = toUpperSnakeCase(element.getName());
        return abbreviate(upperSnake, maxSize);
    }

    /**
     * Get the SQL name for an EPackage.
     * <p>
     * This matches the ETL's {@code packageSqlName()} operation.
     * </p>
     *
     * @param pkg              the package
     * @param shortNameSize    maximum size when createSimpleName is false
     * @param nameSize         maximum size when createSimpleName is true
     * @param createSimpleName whether to use simple naming
     * @param asmUtils         AsmUtils instance for annotation access
     * @return the package SQL name
     */
    public static String packageSqlName(EPackage pkg, int shortNameSize, int nameSize,
                                         boolean createSimpleName, AsmUtils asmUtils) {
        return sqlName(pkg, shortNameSize, nameSize, createSimpleName, asmUtils);
    }

    /**
     * Get the SQL class name for an EClass.
     * <p>
     * This matches the ETL's {@code classSqlName()} operation.
     * When createSimpleName is false, includes the package prefix.
     * </p>
     *
     * @param eClass           the entity class
     * @param createSimpleName whether to use simple naming (no package prefix)
     * @param shortNameSize    maximum size for name abbreviation
     * @param nameSize         maximum size for name abbreviation
     * @param asmUtils         AsmUtils instance for annotation access
     * @return the class SQL name
     */
    public static String classSqlName(EClass eClass, boolean createSimpleName, int shortNameSize,
                                       int nameSize, AsmUtils asmUtils) {
        if (createSimpleName) {
            return sqlLongName(eClass, nameSize, asmUtils);
        } else {
            String pkgName = packageSqlName(eClass.getEPackage(), shortNameSize, nameSize, createSimpleName, asmUtils);
            String className = sqlLongName(eClass, nameSize, asmUtils);
            return pkgName + "_" + className;
        }
    }

    // =========================================================================
    // SQL NAME METHODS
    // =========================================================================

    /**
     * Generate SQL table name for an EClass.
     * <p>
     * This matches the ETL's {@code tableSqlName()} operation:
     * {@code tablePrefix + abbreviate(classSqlName(), tableNameMaxSize - tablePrefix.length).toUpperCase()}
     * </p>
     *
     * @param eClass           the entity class
     * @param tableNameMaxSize maximum table name size
     * @param tablePrefix      table name prefix (e.g., "T_")
     * @param createSimpleName whether to use simple naming (no package prefix)
     * @param shortNameSize    maximum size for name abbreviation
     * @param nameSize         maximum size for name abbreviation
     * @param asmUtils         AsmUtils instance for FQN generation
     * @return the SQL table name
     */
    public static String tableSqlName(EClass eClass, int tableNameMaxSize, String tablePrefix,
                                       boolean createSimpleName, int shortNameSize, int nameSize, AsmUtils asmUtils) {
        String classNamePart = classSqlName(eClass, createSimpleName, shortNameSize, nameSize, asmUtils);
        return tablePrefix + abbreviate(classNamePart, tableNameMaxSize - tablePrefix.length()).toUpperCase();
    }

    /**
     * Generate SQL field name for an EAttribute.
     * <p>
     * This matches the ETL's {@code fieldSqlName()} operation:
     * {@code columnPrefix + abbreviate(sqlLongName(), columnNameMaxSize - columnPrefix.length).toUpperCase()}
     * </p>
     *
     * @param attr              the attribute
     * @param columnNameMaxSize maximum column name size
     * @param columnPrefix      column name prefix (e.g., "C_")
     * @param nameSize          maximum size for name abbreviation
     * @param asmUtils          AsmUtils instance for annotation access
     * @return the SQL field name
     */
    public static String fieldSqlName(EAttribute attr, int columnNameMaxSize, String columnPrefix,
                                       int nameSize, AsmUtils asmUtils) {
        // Handle reserved names when no prefix
        if (columnPrefix.trim().isEmpty()) {
            String lowerName = attr.getName().toLowerCase();
            if ("id".equals(lowerName)) {
                return "DOMAIN_ID";
            } else if ("type".equals(lowerName)) {
                return "DOMAIN_TYPE";
            } else if ("version".equals(lowerName)) {
                return "DOMAIN_VERSION";
            }
        }
        String namePart = sqlLongName(attr, nameSize, asmUtils);
        return columnPrefix + abbreviate(namePart, columnNameMaxSize - columnPrefix.length()).toUpperCase();
    }

    /**
     * Generate SQL identifier name for an EReference.
     * <p>
     * This matches the ETL's {@code referenceIdentifierSqlName()} operation:
     * {@code columnPrefix + abbreviate(sqlLongName(), columnNameMaxSize - columnPrefix.length - 3).toUpperCase() + "_ID"}
     * </p>
     *
     * @param ref               the reference
     * @param columnNameMaxSize maximum column name size
     * @param columnPrefix      column name prefix (e.g., "C_")
     * @param nameSize          maximum size for name abbreviation
     * @param asmUtils          AsmUtils instance for annotation access
     * @return the SQL identifier name
     */
    public static String referenceIdentifierSqlName(EReference ref, int columnNameMaxSize, String columnPrefix,
                                                     int nameSize, AsmUtils asmUtils) {
        String namePart = sqlLongName(ref, nameSize, asmUtils);
        return columnPrefix + abbreviate(namePart, columnNameMaxSize - columnPrefix.length() - 3).toUpperCase() + "_ID";
    }

    /**
     * Generate SQL foreign key name for an EReference.
     * <p>
     * This matches the ETL's {@code referenceFkSqlName()} operation:
     * {@code foreignKeyPrefix + abbreviate(classSqlName + "_" + sqlLongName, columnNameMaxSize - junctionTablePrefix.length).toUpperCase()}
     * </p>
     *
     * @param ref                 the reference
     * @param foreignKeyPrefix    foreign key prefix (e.g., "FK_")
     * @param junctionTablePrefix junction table prefix (for max length calculation)
     * @param columnNameMaxSize   maximum column name size
     * @param createSimpleName    whether to use simple naming
     * @param shortNameSize       maximum size for name abbreviation
     * @param nameSize            maximum size for name abbreviation
     * @param asmUtils            AsmUtils instance for annotation access
     * @return the SQL foreign key name
     */
    public static String referenceFkSqlName(EReference ref, String foreignKeyPrefix, String junctionTablePrefix,
                                             int columnNameMaxSize, boolean createSimpleName, int shortNameSize,
                                             int nameSize, AsmUtils asmUtils) {
        String classNamePart = classSqlName(ref.getEContainingClass(), createSimpleName, shortNameSize, nameSize, asmUtils);
        String refNamePart = sqlLongName(ref, nameSize, asmUtils);
        return foreignKeyPrefix + abbreviate(classNamePart + "_" + refNamePart, columnNameMaxSize - junctionTablePrefix.length()).toUpperCase();
    }

    /**
     * Generate SQL inverse identifier name for an EReference.
     * <p>
     * This matches the ETL's {@code referenceInverseIdentifierSqlName()} operation:
     * {@code columnPrefix + abbreviate(classSqlName + "_" + sqlLongName, columnNameMaxSize - columnPrefix.length - 3).toUpperCase() + "_ID"}
     * </p>
     *
     * @param ref               the reference
     * @param columnNameMaxSize maximum column name size
     * @param columnPrefix      column name prefix (e.g., "C_")
     * @param createSimpleName  whether to use simple naming
     * @param shortNameSize     maximum size for name abbreviation
     * @param nameSize          maximum size for name abbreviation
     * @param asmUtils          AsmUtils instance for annotation access
     * @return the SQL inverse identifier name
     */
    public static String referenceInverseIdentifierSqlName(EReference ref, int columnNameMaxSize, String columnPrefix,
                                                            boolean createSimpleName, int shortNameSize, int nameSize,
                                                            AsmUtils asmUtils) {
        String classNamePart = classSqlName(ref.getEContainingClass(), createSimpleName, shortNameSize, nameSize, asmUtils);
        String refNamePart = sqlLongName(ref, nameSize, asmUtils);
        return columnPrefix + abbreviate(classNamePart + "_" + refNamePart, columnNameMaxSize - columnPrefix.length() - 3).toUpperCase() + "_ID";
    }

    /**
     * Generate SQL inverse foreign key name for an EReference.
     * <p>
     * This matches the ETL's {@code referenceInvFkSqlName()} operation:
     * {@code inverseForeignKeyPrefix + abbreviate(classSqlName + "_" + sqlLongName, columnNameMaxSize - inverseForeignKeyPrefix.length).toUpperCase()}
     * </p>
     *
     * @param ref                     the reference
     * @param inverseForeignKeyPrefix inverse foreign key prefix (e.g., "FK_INV_")
     * @param columnNameMaxSize       maximum column name size
     * @param createSimpleName        whether to use simple naming
     * @param shortNameSize           maximum size for name abbreviation
     * @param nameSize                maximum size for name abbreviation
     * @param asmUtils                AsmUtils instance for annotation access
     * @return the SQL inverse foreign key name
     */
    public static String referenceInvFkSqlName(EReference ref, String inverseForeignKeyPrefix, int columnNameMaxSize,
                                                boolean createSimpleName, int shortNameSize, int nameSize,
                                                AsmUtils asmUtils) {
        String classNamePart = classSqlName(ref.getEContainingClass(), createSimpleName, shortNameSize, nameSize, asmUtils);
        String refNamePart = sqlLongName(ref, nameSize, asmUtils);
        return inverseForeignKeyPrefix + abbreviate(classNamePart + "_" + refNamePart, columnNameMaxSize - inverseForeignKeyPrefix.length()).toUpperCase();
    }

    /**
     * Generate SQL unidirectional foreign key name for an EReference.
     * <p>
     * This matches the ETL's {@code referenceUniFkSqlName()} operation:
     * {@code foreignKeyPrefix + abbreviate(classSqlName + "_" + sqlLongName, columnNameMaxSize - foreignKeyPrefix.length).toUpperCase()}
     * </p>
     *
     * @param ref              the reference
     * @param foreignKeyPrefix foreign key prefix (e.g., "FK_")
     * @param columnNameMaxSize maximum column name size
     * @param createSimpleName whether to use simple naming
     * @param shortNameSize    maximum size for name abbreviation
     * @param nameSize         maximum size for name abbreviation
     * @param asmUtils         AsmUtils instance for annotation access
     * @return the SQL unidirectional foreign key name
     */
    public static String referenceUniFkSqlName(EReference ref, String foreignKeyPrefix, int columnNameMaxSize,
                                                boolean createSimpleName, int shortNameSize, int nameSize,
                                                AsmUtils asmUtils) {
        String classNamePart = classSqlName(ref.getEContainingClass(), createSimpleName, shortNameSize, nameSize, asmUtils);
        String refNamePart = sqlLongName(ref, nameSize, asmUtils);
        return foreignKeyPrefix + abbreviate(classNamePart + "_" + refNamePart, columnNameMaxSize - foreignKeyPrefix.length()).toUpperCase();
    }

    /**
     * Generate SQL junction table name for a many-to-many reference.
     * <p>
     * This matches the ETL's {@code referenceManyToManyTableSqlName()} operation.
     * For bidirectional references: {@code J_ + f1.eReferenceType.classSqlName + "_" + f1.sqlLongName + "_" + f2.eReferenceType.classSqlName + "_" + f2.sqlLongName}
     * For unidirectional references: {@code J_ + f1.eContainingClass.classSqlName + "_" + f1.sqlLongName}
     * </p>
     *
     * @param ref                  the reference
     * @param tableNameMaxSize     maximum table name size
     * @param junctionTablePrefix  junction table prefix (e.g., "J_")
     * @param createSimpleName     whether to use simple naming
     * @param shortNameSize        maximum size for name abbreviation
     * @param nameSize             maximum size for name abbreviation
     * @param asmUtils             AsmUtils instance for annotation access
     * @return the SQL junction table name
     */
    public static String referenceManyToManyTableSqlName(EReference ref, int tableNameMaxSize, String junctionTablePrefix,
                                                          boolean createSimpleName, int shortNameSize, int nameSize,
                                                          AsmUtils asmUtils) {
        EReference opposite = ref.getEOpposite();
        String name;
        if (opposite != null) {
            // Bidirectional: use reference types
            String f1Class = classSqlName(ref.getEReferenceType(), createSimpleName, shortNameSize, nameSize, asmUtils);
            String f1Ref = sqlLongName(ref, nameSize, asmUtils);
            String f2Class = classSqlName(opposite.getEReferenceType(), createSimpleName, shortNameSize, nameSize, asmUtils);
            String f2Ref = sqlLongName(opposite, nameSize, asmUtils);
            name = f1Class + "_" + f1Ref + "_" + f2Class + "_" + f2Ref;
        } else {
            // Unidirectional: use containing class
            String containingClass = classSqlName(ref.getEContainingClass(), createSimpleName, shortNameSize, nameSize, asmUtils);
            String refName = sqlLongName(ref, nameSize, asmUtils);
            name = containingClass + "_" + refName;
        }
        return junctionTablePrefix + abbreviate(name, tableNameMaxSize - junctionTablePrefix.length()).toUpperCase();
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
                if (rdbmsSize.startsWith("#")) {
                    // Annotation-based size (e.g., #constraints:maxLength)
                    if (attr != null) {
                        String[] parts = rdbmsSize.substring(1).split(":", 2);
                        if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                            String annotation = parts[0];
                            String annotationKey = parts[1];
                            Optional<String> maxLength = asmUtils.getExtensionAnnotationCustomValue(attr, annotation, annotationKey, false);
                            if (maxLength.isPresent()) {
                                try {
                                    field.setSize(Integer.parseInt(maxLength.get()));
                                } catch (NumberFormatException e) {
                                    // Default to 255 for VARCHAR if annotation value is invalid
                                    field.setSize(255);
                                }
                            } else {
                                // Default to 255 for VARCHAR when no annotation is present (ETL compatibility)
                                field.setSize(255);
                            }
                        } else {
                            // Default to 255 for VARCHAR if annotation format is invalid
                            field.setSize(255);
                        }
                    } else {
                        // attr is null (system fields like TYPE, CREATE_USERNAME) - use default 255
                        field.setSize(255);
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
        RESOURCE_ID_CACHE.clear();
    }
}
