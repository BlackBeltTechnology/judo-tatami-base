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
    
    /** Create navigation property for derived attribute. */
    public static final String CREATE_NAVIGATION_PROPERTY = "CreateNavigationProperty";
    
    /** Create primitive accessor expression annotation. */
    public static final String CREATE_PRIMITIVE_ACCESSOR_EXPRESSION_ANNOTATION = "CreatePrimitiveAccessorExpressionAnnotation";
    
    /** Create reference accessor expression annotation. */
    public static final String CREATE_REFERENCE_ACCESSOR_EXPRESSION_ANNOTATION = "CreateReferenceAccessorExpressionAnnotation";
    
    // =====================================================
    // Operation Rules (operation.etl)
    // =====================================================
    
    /** Create bound operation. */
    public static final String CREATE_BOUND_OPERATION = "CreateBoundOperation";
    
    /** Create unbound operation. */
    public static final String CREATE_UNBOUND_OPERATION = "CreateUnboundOperation";
    
    /** Create bound transfer operation. */
    public static final String CREATE_BOUND_TRANSFER_OPERATION = "CreateBoundTransferOperation";
    
    /** Create initializer annotation. */
    public static final String CREATE_INITIALIZER_ANNOTATION = "CreateInitializerAnnotation";
    
    /** Add behaviour annotation. */
    public static final String ADD_BEHAVIOUR_ANNOTATION = "AddBehaviourAnnotation";
    
    /** Create output parameter name annotation. */
    public static final String CREATE_OUTPUT_PARAMETER_NAME = "CreateOutputParameterName";
    
    /** Create custom implementation annotation on operation. */
    public static final String CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_OPERATION = "CreateCustomImplementationAnnotationOnOperation";
    
    /** Create custom implementation annotation on bound operation. */
    public static final String CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_BOUND_OPERATION = "CreateCustomImplementationAnnotationOnBoundOperation";
    
    /** Create custom implementation annotation on unbound operation. */
    public static final String CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION_ON_UNBOUND_OPERATION = "CreateCustomImplementationAnnotationOnUnboundOperation";
    
    /** Create stateful annotation on operation. */
    public static final String CREATE_STATEFUL_ANNOTATION_ON_OPERATION = "CreateStatefulAnnotationOnOperation";
    
    /** Create instance representation annotation for bound operation. */
    public static final String CREATE_INSTANCE_REPRESENTATION_OF_BOUND_OPERATION = "CreateInstanceRepresentationOfBoundOperation";
    
    /** Create bound operation annotation. */
    public static final String CREATE_BOUND_OPERATION_ANNOTATION = "CreateBoundOperationAnnotation";
    
    /** Create script body annotation for bound operation. */
    public static final String CREATE_SCRIPT_BODY_ANNOTATION_FOR_BOUND_OPERATION = "CreateScriptBodyAnnotationForBoundOperation";
    
    /** Create abstract annotation for bound operation. */
    public static final String CREATE_ABSTRACT_ANNOTATION_FOR_BOUND_OPERATION = "CreateAbstractAnnotationForBoundOperation";
    
    /** Create script body annotation for unbound operation. */
    public static final String CREATE_SCRIPT_BODY_ANNOTATION_FOR_UNBOUND_OPERATION = "CreateScriptBodyAnnotationForUnboundOperation";
    
    /** Create operation permissions annotation. */
    public static final String CREATE_OPERATION_PERMISSIONS = "CreateOperationPermissions";
    
    /** Create immutable flag for transfer operation. */
    public static final String CREATE_IMMUTABLE_FLAG_FOR_TRANSFER_OPERATION = "CreateImmutableFlagForTransferOperation";
    
    /** Create transfer operation input range annotation. */
    public static final String CREATE_TRANSFER_OPERATION_INPUT_RANGE_ANNOTATION = "CreateTransferOperationInputRangeAnnotation";
    
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
    
    /** Add transient annotation to transfer attribute. */
    public static final String ADD_TRANSIENT_ANNOTATION_TO_TRANSFER_ATTRIBUTE = "AddTransientAnnotationToTransferAttribute";
    
    /** Create transfer object attribute binding annotation. */
    public static final String CREATE_TRANSFER_OBJECT_ATTRIBUTE_BINDING_ANNOTATION = "CreateTransferObjectAttributeBindingAnnotation";
    
    /** Add transient annotation to transfer object relation. */
    public static final String ADD_TRANSIENT_ANNOTATION_TO_TRANSFER_OBJECT_RELATION = "AddTransientAnnotationToTransferObjectRelation";
    
    /** Create transfer object relation binding annotation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION_BINDING_ANNOTATION = "CreateTransferObjectRelationBindingAnnotation";
    
    /** Create transfer object relation access annotation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION_ACCESS_ANNOTATION = "CreateTransferObjectRelationAccessAnnotation";
    
    /** Create transfer object relation range annotation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION_RANGE_ANNOTATION = "CreateTransferObjectRelationRangeAnnotation";
    
    /** Create transfer object relation embedded flags annotation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION_EMBEDDED_FLAGS = "CreateTransferObjectRelationEmbeddedFlags";
    
    /** Create transfer object relation permissions annotation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION_PERMISSIONS = "CreateTransferObjectRelationPermissions";
    
    /** Create transfer attribute claim annotation. */
    public static final String CREATE_TRANSFER_ATTRIBUTE_CLAIM_ANNOTATION = "CreateTransferAttributeClaimAnnotation";
    
    /** Add default annotation to transfer attribute. */
    public static final String ADD_DEFAULT_ANNOTATION_TO_TRANSFER_ATTRIBUTE = "AddDefaultAnnotationToTransferAttribute";
    
    /** Add default annotation to transfer object relation. */
    public static final String ADD_DEFAULT_ANNOTATION_TO_TRANSFER_OBJECT_RELATION = "AddDefaultAnnotationToTransferObjectRelation";
    
    /** Add string transfer attribute constraints. */
    public static final String ADD_STRING_TRANSFER_ATTRIBUTE_CONSTRAINTS = "AddStringTransferAttributeConstraints";
    
    /** Add custom transfer attribute constraints. */
    public static final String ADD_CUSTOM_TRANSFER_ATTRIBUTE_CONSTRAINTS = "AddCustomTransferAttributeConstraints";
    
    /** Add numeric transfer attribute constraints. */
    public static final String ADD_NUMERIC_TRANSFER_ATTRIBUTE_CONSTRAINTS = "AddNumericTransferAttributeConstraints";
    
    /** Add measured transfer attribute constraints. */
    public static final String ADD_MEASURED_TRANSFER_ATTRIBUTE_CONSTRAINTS = "AddMeasuredTransferAttributeConstraints";
    
    /** Create reference class for entity type. */
    public static final String CREATE_REFERENCE_CLASS_FOR_ENTITY_TYPE = "CreateReferenceClassForEntityType";
    
    /** Create query customizer annotation. */
    public static final String CREATE_QUERY_CUSTOMIZER_ANNOTATION = "CreateQueryCustomizerAnnotationForQueryCustomizerClass";
    
    /** Create metadata annotation for metadata class. */
    public static final String CREATE_METADATA_ANNOTATION = "CreateMetadataAnnotationForMetadataClass";
    
    /** Create get range input annotation. */
    public static final String CREATE_GET_RANGE_INPUT_ANNOTATION = "CreateGetRangeInputAnnotationForGetRangeInputClass";
    
    /** Create transfer object type annotation class. */
    public static final String CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_CLASS = "CreateTransferObjectTypeAnnotationClass";
    
    /** Create transfer object type annotation class for reference class. */
    public static final String CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_CLASS_FOR_REFERENCE_CLASS = "CreateTransferObjectTypeAnnotationClassForReferenceClass";
    
    /** Create mapped entity type annotation on mapped transfer object. */
    public static final String CREATE_MAPPED_ENTITY_TYPE_ANNOTATION_ON_MAPPED_TRANSFER_OBJECT = "CreateMappedEntityTypeAnnotationOnMappedTransferObject";
    
    /** Create mapped entity type annotation on reference class for entity type. */
    public static final String CREATE_MAPPED_ENTITY_TYPE_ANNOTATION_ON_REFERENCE_CLASS_FOR_ENTITY_TYPE = "CreateMappedEntityTypeAnnotationOnReferenceClassForEntityType";
    
    /** Create annotation on reference class for entity type. */
    public static final String CREATE_ANNOTATION_ON_REFERENCE_CLASS_FOR_ENTITY_TYPE = "CreateAnnotationOnReferenceClassForEntityType";
    
    /** Create navigation reference binding. */
    public static final String CREATE_NAVIGATION_REFERENCE_BINDING = "CreateNavigationReferenceBinding";
    
    /** Create data reference binding. */
    public static final String CREATE_DATA_REFERENCE_BINDING = "CreateDataReferenceBinding";
    
    /** Create transfer attribute parameterized annotation. */
    public static final String CREATE_TRANSFER_ATTRIBUTE_PARAMETERIZED_ANNOTATION = "CreateTransferAttributeParameterizedAnnotation";
    
    /** Create transfer object relation parameterized annotation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION_PARAMETERIZED_ANNOTATION = "CreateTransferObjectRelationParameterizedAnnotation";
    
    // =====================================================
    // Actor Rules (actor.etl)
    // =====================================================
    
    /** Create actor type class. */
    public static final String CREATE_ACTOR_TYPE_CLASS = "CreateActorTypeClass";

    /** Create mapped actor type class. */
    public static final String CREATE_MAPPED_ACTOR_TYPE_CLASS = "CreateMappedActorTypeClass";

    /** Create actor annotation. */
    public static final String CREATE_ACTOR_ANNOTATION = "CreateActorAnnotation";

    /** Create mapped actor type annotation. */
    public static final String CREATE_MAPPED_ACTOR_TYPE_ANNOTATION = "CreateMappedActorTypeAnnotation";
    
    /** Create actor type annotation. */
    public static final String CREATE_ACTOR_TYPE_ANNOTATION = "CreateActorTypeAnnotation";
    
    /** Create realm type annotation. */
    public static final String CREATE_REALM_TYPE_ANNOTATION = "CreateRealmTypeAnnotation";
    
    /** Create documentation annotation for actor type. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_ACTOR_TYPE = "CreateDocumentationAnnotationForActorType";
    
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
    
    /** Create unmapped transfer object for static data. */
    public static final String CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_DATA = "CreateUnmappedTransferObjectForStaticData";
    
    /** Create transfer object type annotation for static data. */
    public static final String CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_FOR_STATIC_DATA = "CreateTransferObjectTypeAnnotationClassForStaticData";
    
    /** Create static data query annotation. */
    public static final String CREATE_STATIC_DATA_QUERY_ANNOTATION = "CreateStaticDataQueryAnnotation";
    
    /** Create static query attribute. */
    public static final String CREATE_STATIC_QUERY_ATTRIBUTE = "CreateStaticQueryAttribute";
    
    /** Create unmapped transfer object for static navigation. */
    public static final String CREATE_UNMAPPED_TRANSFER_OBJECT_FOR_STATIC_NAVIGATION = "CreateUnmappedTransferObjectForStaticNavigation";
    
    /** Create transfer object type annotation for static navigation. */
    public static final String CREATE_TRANSFER_OBJECT_TYPE_ANNOTATION_FOR_STATIC_NAVIGATION = "CreateTransferObjectTypeAnnotationClassForStaticNavigation";
    
    /** Create static navigation query annotation. */
    public static final String CREATE_STATIC_NAVIGATION_QUERY_ANNOTATION = "CreateStaticNavigationQueryAnnotation";
    
    /** Create static query navigation. */
    public static final String CREATE_STATIC_QUERY_NAVIGATION = "CreateStaticQueryNavigation";
    
    // =====================================================
    // Additional Operation Rules
    // =====================================================

    /** Create input parameter. */
    public static final String CREATE_INPUT_PARAMETER = "CreateInputParameter";

    /** Create output parameter name for bound operation. */
    public static final String CREATE_OUTPUT_PARAMETER_NAME_FOR_BOUND_OPERATION = "CreateOutputParameterNameForBoundOperation";

    /** Create bound operation input parameter. */
    public static final String CREATE_BOUND_OPERATION_INPUT_PARAMETER = "CreateBoundOperationInputParameter";

    /** Create transfer operation behaviour annotation. */
    public static final String CREATE_TRANSFER_OPERATION_BEHAVIOUR_ANNOTATION = "CreateTransferOperationBehaviourAnnotation";

    /** Create stateful annotation default. */
    public static final String CREATE_STATEFUL_ANNOTATION_DEFAULT = "CreateStatefulAnnotationDefault";

    /** Create custom implementation annotation. */
    public static final String CREATE_CUSTOM_IMPLEMENTATION_ANNOTATION = "CreateCustomImplementationAnnotation";

    /** Create bound annotation for transfer operation. */
    public static final String CREATE_BOUND_ANNOTATION_FOR_TRANSFER_OPERATION = "CreateBoundAnnotationForTransferOperation";

    /** Create stateful annotation with behaviour. */
    public static final String CREATE_STATEFUL_ANNOTATION_WITH_BEHAVIOUR = "CreateStatefulAnnotationWithBehaviour";
    
    // =====================================================
    // Documentation Annotation Rules
    // =====================================================
    
    /** Create documentation annotation for transfer object type. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OBJECT_TYPE = "CreateDocumentationAnnotationForTransferObjectType";
    
    /** Create documentation annotation for transfer attribute. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_ATTRIBUTE = "CreateDocumentationAnnotationForTransferAttribute";
    
    /** Create documentation annotation for transfer object relation. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OBJECT_RELATION = "CreateDocumentationAnnotationForTransferObjectRelation";
    
    /** Create documentation annotation for transfer operation. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_TRANSFER_OPERATION = "CreateDocumentationAnnotationForTransferOperation";
    
    /** Create documentation annotation for bound operation. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_BOUND_OPERATION = "CreateDocumentationAnnotationForBoundOperation";
    
    /** Create documentation annotation for data property. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_DATA_PROPERTY = "CreateDocumentationAnnotationForDataProperty";
    
    /** Create documentation annotation for navigation property. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_NAVIGATION_PROPERTY = "CreateDocumentationAnnotationForNavigationProperty";
    
    // =====================================================
    // Primitive Accessor Constraint Rules
    // =====================================================
    
    /** Add string constraints for primitive accessor. */
    public static final String ADD_STRING_PRIMITIVE_ACCESSOR_CONSTRAINTS = "AddStringPrimitiveAccessorConstraints";
    
    /** Add custom type constraints for primitive accessor. */
    public static final String ADD_CUSTOM_PRIMITIVE_ACCESSOR_CONSTRAINTS = "AddCustomPrimitiveAccessorConstraints";
    
    /** Add numeric constraints for primitive accessor. */
    public static final String ADD_NUMERIC_PRIMITIVE_ACCESSOR_CONSTRAINTS = "AddNumericPrimitiveAccessorConstraints";
    
    /** Add measured constraints for primitive accessor. */
    public static final String ADD_MEASURED_PRIMITIVE_ACCESSOR_CONSTRAINTS = "AddMeasuredPrimitiveAccessorConstraints";
    
    // =====================================================
    // Navigation and Accessor Rules
    // =====================================================
    
    /** Transform PrimitiveAccessor to EAttribute. */
    public static final String CREATE_PRIMITIVE_ACCESSOR = "CreatePrimitiveAccessor";
    
    /** Transform ReferenceAccessor to EReference. */
    public static final String CREATE_REFERENCE_ACCESSOR = "CreateReferenceAccessor";
    
    // =====================================================
    // Additional Static Binding Rules
    // =====================================================
    
    /** Create transfer attribute parameterized annotation for static data. */
    public static final String CREATE_TRANSFER_ATTRIBUTE_PARAMETERIZED_ANNOTATION_FOR_STATIC_DATA = "CreateTransferAttributeParameterizedAnnotationForStaticData";
    
    /** Create data reference binding for static data. */
    public static final String CREATE_DATA_REFERENCE_BINDING_FOR_STATIC_DATA = "CreateDataReferenceBindingForStaticData";
    
    /** Create navigation reference binding for static navigation. */
    public static final String CREATE_NAVIGATION_REFERENCE_BINDING_FOR_STATIC_NAVIGATION = "CreateNavigationReferenceBindingForStaticNavigation";
    
    /** Create transfer object relation parameterized annotation for static navigation. */
    public static final String CREATE_TRANSFER_OBJECT_RELATION_PARAMETERIZED_ANNOTATION_FOR_STATIC_NAVIGATION = "CreateTransferObjectRelationParameterizedAnnotationForStaticNavigation";
    
    // =====================================================
    // Additional Operation Stateful Rules
    // =====================================================
    
    /** Create stateful annotation on operation without implementation and behaviour. */
    public static final String CREATE_STATEFUL_ANNOTATION_ON_OPERATION_WITHOUT_IMPLEMENTATION_AND_BEHAVIOUR = "CreateStatefulAnnotationOnOperationWithoutImplementationAndBehaviour";
    
    /** Create stateful annotation on operation with behaviour. */
    public static final String CREATE_STATEFUL_ANNOTATION_ON_OPERATION_WITH_BEHAVIOUR = "CreateStatefulAnnotationOnOperationWithBehaviour";
    
    // =====================================================
    // Additional Operation Parameter Rules
    // =====================================================
    
    /** Create parameter for operation. */
    public static final String CREATE_PARAMETER = "CreateParameter";
    
    /** Create documentation annotation for input parameter. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_INPUT_PARAMETER = "CreateDocumentationAnnotationForInputParameter";
    
    /** Create documentation annotation for output parameter. */
    public static final String CREATE_DOCUMENTATION_ANNOTATION_FOR_OUTPUT_PARAMETER = "CreateDocumentationAnnotationForOutputParameter";
}
