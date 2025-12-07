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

/**
 * Constants for PSM to ASM transformation rule names.
 * <p>
 * These constants ensure consistency between transformation rules
 * and any references to them (e.g., in traces, logs, or validations).
 * </p>
 */
public final class Psm2AsmRuleNames {
    
    private Psm2AsmRuleNames() {
        // Prevent instantiation
    }
    
    // =====================================================
    // Namespace Rules (namespace.etl)
    // =====================================================
    
    /** Abstract rule for transforming Namespace to EPackage. */
    public static final String NAMESPACE_TO_PACKAGE = "NamespaceToPackage";
    
    /** Transform Model to root EPackage. */
    public static final String MODEL_TO_PACKAGE = "ModelToPackage";
    
    /** Create version annotation for Model. */
    public static final String MODEL_TO_PACKAGE_VERSION = "ModelToPackageVersion";
    
    /** Transform Package to EPackage (sub-package). */
    public static final String PACKAGE_TO_PACKAGE = "PackageToPackage";
    
    /** Abstract rule for creating documentation annotations. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION = "CreateDocumentationAnnotation";
    
    // =====================================================
    // Type Rules (type.etl)
    // =====================================================
    
    /** Transform EnumerationType to EEnum. */
    public static final String CREATE_ENUMERATION = "CreateEnumeration";
    
    /** Transform StringType to EDataType. */
    public static final String CREATE_STRING_TYPE = "CreateStringType";
    
    /** Transform NumericType (integer) to EDataType. */
    public static final String CREATE_INTEGER_TYPE = "CreateIntegerType";
    
    /** Transform NumericType (decimal) to EDataType. */
    public static final String CREATE_DECIMAL_TYPE = "CreateDecimalType";
    
    /** Create measured annotation for numeric types. */
    public static final String CREATE_MEASURED_ANNOTATION_OF_INTEGER_TYPE = "CreateMeasuredAnnotationOfIntegerType";
    
    /** Transform BooleanType to EDataType. */
    public static final String CREATE_BOOLEAN_TYPE = "CreateBooleanType";
    
    /** Transform PasswordType to EDataType. */
    public static final String CREATE_PASSWORD_TYPE = "CreatePasswordType";
    
    /** Transform BinaryType to EDataType. */
    public static final String CREATE_BINARY_TYPE = "CreateBinaryType";
    
    /** Transform XMLType to EDataType. */
    public static final String CREATE_XML_TYPE = "CreateXMLType";
    
    /** Transform DateType to EDataType. */
    public static final String CREATE_DATE_TYPE = "CreateDateType";
    
    /** Transform TimestampType to EDataType. */
    public static final String CREATE_TIMESTAMP_TYPE = "CreateTimestampType";
    
    /** Transform TimeType to EDataType. */
    public static final String CREATE_TIME_TYPE = "CreateTimeType";
    
    /** Transform CustomType to EDataType. */
    public static final String CREATE_CUSTOM_TYPE = "CreateCustomType";
    
    // =====================================================
    // Data Rules (data.etl)
    // =====================================================
    
    /** Create entity annotation for EClass. */
    public static final String CREATE_ENTITY_ANNOTATION_CLASS = "CreateEntityAnnotationClass";
    
    /** Create documentation annotation for EntityType. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_ENTITY_TYPE = "CreateDocumentationAnnotationForEntityType";
    
    /** Transform EntityType to EClass. */
    public static final String CREATE_ENTITY_CLASS = "CreateEntityClass";
    
    /** Create default representation annotation for entity. */
    public static final String CREATE_ENTITY_DEFAULT_REPRESENTATION_ANNOTATION = "CreateEntityDefaultRepresentationAnnotation";
    
    /** Abstract rule for adding attribute constraints. */
    public static final String ADD_ATTRIBUTE_CONSTRAINTS = "AddAttributeConstraints";
    
    /** Create documentation annotation for attributes. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_ATTRIBUTES = "CreateDocumentationAnnotationForAtrributes";
    
    /** Add string attribute constraints. */
    public static final String ADD_STRING_ATTRIBUTE_CONSTRAINTS = "AddStringAttributeConstraints";
    
    /** Add custom attribute constraints. */
    public static final String ADD_CUSTOM_ATTRIBUTE_CONSTRAINTS = "AddCustomAttributeConstraints";
    
    /** Abstract rule for numeric attribute constraints. */
    public static final String ADD_ABSTRACT_NUMERIC_ATTRIBUTE_CONSTRAINTS = "AddAbstractNumericAttributeConstraints";
    
    /** Add numeric attribute constraints. */
    public static final String ADD_NUMERIC_ATTRIBUTE_CONSTRAINTS = "AddNumericAttributeConstraints";
    
    /** Add measured attribute constraints. */
    public static final String ADD_MEASURED_ATTRIBUTE_CONSTRAINTS = "AddMeasuredAttributeConstraints";
    
    /** Transform Attribute to EAttribute. */
    public static final String CREATE_ATTRIBUTE = "CreateAttribute";
    
    /** Create identifier annotation for attribute. */
    public static final String CREATE_IDENTIFIER_ANNOTATION_FOR_ATTRIBUTE = "CreateIdentifierAnnotationForAttribute";
    
    /** Abstract rule for creating relation. */
    public static final String CREATE_RELATION = "CreateRelation";
    
    /** Transform AssociationEnd to EReference. */
    public static final String CREATE_ASSOCIATION_END_RELATION = "CreateAssociationEndRelation";
    
    /** Transform Containment to EReference. */
    public static final String CREATE_CONTAINMENT_RELATION = "CreateContainmentRelation";
    
    /** Add unmapped default only annotation for attribute. */
    public static final String ADD_UNMAPPED_DEFAULT_ONLY_ATTRIBUTE_ANNOTATION = "AddUnmappedDefaultOnlyAttributeAnnotation";
    
    /** Add unmapped default only annotation for reference. */
    public static final String ADD_UNMAPPED_DEFAULT_ONLY_REFERENCE_ANNOTATION = "AddUnmappedDefaultOnlyReferenceAnnotation";
    
    /** Create documentation annotation for association end relation. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_ASSOCIATION_END_RELATION = "CreateDocumentationAnnotationForAssociationEndRelation";
    
    /** Create documentation annotation for containment relation. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_CONTAINMENT_RELATION = "CreateDocumentationAnnotationForContainmentRelation";
    
    /** Abstract rule for creating sequence. */
    public static final String CREATE_SEQUENCE = "CreateSequence";
    
    /** Create namespace sequence annotation. */
    public static final String CREATE_NAMESPACE_SEQUENCE = "CreateNamespaceSequence";
    
    /** Create entity sequence annotation. */
    public static final String CREATE_ENTITY_SEQUENCE = "CreateEntitySequence";
    
    // =====================================================
    // Derived Rules (derived.etl)
    // =====================================================
    
    /** Create derived attribute. */
    public static final String CREATE_DERIVED_ATTRIBUTE = "CreateDerivedAttribute";
    
    /** Create data property for derived attribute. */
    public static final String CREATE_DATA_PROPERTY_FOR_DERIVED_ATTRIBUTE = "CreateDataPropertyForDerivedAttribute";
    
    /** Create static navigation for derived attribute. */
    public static final String CREATE_STATIC_NAVIGATION_FOR_DERIVED_ATTRIBUTE = "CreateStaticNavigationForDerivedAttribute";
    
    /** Create static data for derived attribute. */
    public static final String CREATE_STATIC_DATA_FOR_DERIVED_ATTRIBUTE = "CreateStaticDataForDerivedAttribute";
    
    // =====================================================
    // Operation Rules (operation.etl)
    // =====================================================
    
    /** Create bound operation. */
    public static final String CREATE_BOUND_OPERATION = "CreateBoundOperation";
    
    /** Create unbound operation. */
    public static final String CREATE_UNBOUND_OPERATION = "CreateUnboundOperation";
    
    // =====================================================
    // Transfer Object Rules (transferObject.etl)
    // =====================================================
    
    /** Create transfer object type class. */
    public static final String CREATE_TRANSFER_OBJECT_TYPE_CLASS = "CreateTransferObjectTypeClass";
    
    /** Create mapped transfer object type class. */
    public static final String CREATE_MAPPED_TRANSFER_OBJECT_TYPE_CLASS = "CreateMappedTransferObjectTypeClass";
    
    /** Create unmapped transfer object type class. */
    public static final String CREATE_UNMAPPED_TRANSFER_OBJECT_TYPE_CLASS = "CreateUnmappedTransferObjectTypeClass";
    
    /** Create transfer attribute. */
    public static final String CREATE_TRANSFER_ATTRIBUTE = "CreateTransferAttribute";
    
    /** Create transfer object attribute. */
    public static final String CREATE_TRANSFER_OBJECT_ATTRIBUTE = "CreateTransferObjectAttribute";
    
    /** Create transfer relation. */
    public static final String CREATE_TRANSFER_RELATION = "CreateTransferRelation";
    
    /** Create transfer object relation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION = "CreateTransferObjectRelation";
    
    // =====================================================
    // Actor Rules (actor.etl)
    // =====================================================
    
    /** Create actor type class. */
    public static final String CREATE_ACTOR_TYPE_CLASS = "CreateActorTypeClass";
    
    /** Create access point annotation. */
    public static final String CREATE_ACCESS_POINT_ANNOTATION = "CreateAccessPointAnnotation";
    
    /** Create principal annotation. */
    public static final String CREATE_PRINCIPAL_ANNOTATION = "CreatePrincipalAnnotation";
    
    // =====================================================
    // Static Rules (static.etl)
    // =====================================================
    
    /** Create static query. */
    public static final String CREATE_STATIC_QUERY = "CreateStaticQuery";
    
    /** Create static data. */
    public static final String CREATE_STATIC_DATA = "CreateStaticData";
}
