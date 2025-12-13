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
import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.rdbms.*;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMapping;
import hu.blackbelt.judo.meta.rdbmsDataTypes.TypeMappings;
import hu.blackbelt.judo.meta.rdbmsNameMapping.NameMapping;
import hu.blackbelt.judo.meta.rdbmsNameMapping.NameMappings;
import hu.blackbelt.judo.meta.rdbmsRules.Rule;
import hu.blackbelt.judo.meta.rdbmsRules.Rules;
import hu.blackbelt.judo.tatami.asm2rdbms.AbbreviateUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static hu.blackbelt.judo.tatami.asm2rdbms.zeta.Asm2RdbmsRuleNames.*;

/**
 * ASM to RDBMS transformation using Zeta framework patterns.
 * <p>
 * This class implements the equivalent transformation logic as the ETL scripts
 * in src/main/epsilon/transformations/, providing a type-safe Java alternative
 * with better IDE support and debugging capabilities.
 * </p>
 * <p>
 * Transformation rules are documented with @TransformRule annotations in
 * {@link hu.blackbelt.judo.tatami.asm2rdbms.zeta.rules.Asm2RdbmsRules}.
 * The transformation is executed in phases:
 * <ul>
 *   <li>Phase 1: Package transformation (rootPackegeToModel, rootPackegeToConfiguration)</li>
 *   <li>Phase 2: Class transformation (EClassToRdbmsTable, system field rules)</li>
 *   <li>Phase 3: Attribute transformation (EAttributeToTableValueField, EAttributeToIndex)</li>
 *   <li>Phase 4: Reference transformation (foreign keys, junction tables)</li>
 *   <li>Phase 5: Post-processing (name mappings)</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Asm2RdbmsZetaTransformation {

    private final AsmModel asmModel;
    private final hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel;
    private final AsmUtils asmUtils;
    private final ResourceSet asmResourceSet;
    private final RdbmsFactory rdbmsFactory;

    // Configuration parameters
    private final String dialect;
    private final String modelVersion;
    private final int shortNameSize;
    private final int nameSize;
    private final int tableNameMaxSize;
    private final int columnNameMaxSize;
    private final boolean createSimpleName;
    private final String tablePrefix;
    private final String columnPrefix;
    private final String foreignKeyPrefix;
    private final String inverseForeignKeyPrefix;
    private final String junctionTablePrefix;

    // Trace map for source to target element mapping
    private final Map<EObject, Map<String, EObject>> traceMap = new ConcurrentHashMap<>();

    // Type mapping from Excel model (loaded separately)
    private final Map<String, TypeMapping> typeMappings = new HashMap<>();
    private Rules rules;

    @Builder
    public Asm2RdbmsZetaTransformation(
            @NonNull AsmModel asmModel,
            @NonNull hu.blackbelt.judo.meta.rdbms.runtime.RdbmsModel rdbmsModel,
            @NonNull String dialect,
            String modelVersion,
            Integer shortNameSize,
            Integer nameSize,
            Integer tableNameMaxSize,
            Integer columnNameMaxSize,
            Boolean createSimpleName,
            String tablePrefix,
            String columnPrefix,
            String foreignKeyPrefix,
            String inverseForeignKeyPrefix,
            String junctionTablePrefix) {
        this.asmModel = asmModel;
        this.rdbmsModel = rdbmsModel;
        this.asmResourceSet = asmModel.getResourceSet();
        this.asmUtils = new AsmUtils(asmResourceSet);
        this.rdbmsFactory = RdbmsFactory.eINSTANCE;
        this.dialect = dialect;
        this.modelVersion = modelVersion != null ? modelVersion : asmModel.getVersion();

        // Set defaults based on dialect
        boolean isOracle = "oracle".equals(dialect);
        this.shortNameSize = shortNameSize != null && shortNameSize > 0 ? shortNameSize : (isOracle ? 6 : 16);
        this.nameSize = nameSize != null && nameSize > 0 ? nameSize : (isOracle ? 28 : 60);
        this.tableNameMaxSize = tableNameMaxSize != null && tableNameMaxSize > 0 ? tableNameMaxSize : (isOracle ? 30 : 62);
        this.columnNameMaxSize = columnNameMaxSize != null && columnNameMaxSize > 0 ? columnNameMaxSize : (isOracle ? 30 : 58);
        this.createSimpleName = createSimpleName != null ? createSimpleName : false;
        this.tablePrefix = tablePrefix != null ? tablePrefix : "T_";
        this.columnPrefix = columnPrefix != null ? columnPrefix : "C_";
        this.foreignKeyPrefix = foreignKeyPrefix != null ? foreignKeyPrefix : "FK_";
        this.inverseForeignKeyPrefix = inverseForeignKeyPrefix != null ? inverseForeignKeyPrefix : "FK_INV_";
        this.junctionTablePrefix = junctionTablePrefix != null ? junctionTablePrefix : "J_";

        // Load type mappings from the RDBMS model (assumes they're already loaded)
        loadTypeMappings();
        loadRules();
    }

    private void loadTypeMappings() {
        rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(TypeMappings.class::isInstance)
                .map(TypeMappings.class::cast)
                .flatMap(mappings -> mappings.getTypeMappings().stream())
                .forEach(mapping -> typeMappings.put(mapping.getAsmType(), mapping));
    }

    private void loadRules() {
        rules = rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(Rules.class::isInstance)
                .map(Rules.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rules not found in RDBMS model. Make sure mapping model is loaded."));
    }

    /**
     * Helper method to get all elements of a given type from the ASM model.
     */
    private <T> Stream<T> all(Class<T> clazz) {
        return asmUtils.all(clazz);
    }

    /**
     * Execute the transformation.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting ASM to RDBMS Zeta transformation with dialect: {}", dialect);
        long startTime = System.currentTimeMillis();

        // Phase 1: Transform root package to model
        transformRootPackages();

        // Phase 2: Transform entity classes to tables
        transformEntityClasses();

        // Phase 3: Transform attributes to fields
        transformAttributes();

        // Phase 4: Transform references to foreign keys and junction tables
        transformReferences();

        // Phase 5: Post-processing - apply name mappings
        postProcess();

        long duration = System.currentTimeMillis() - startTime;
        log.info("ASM to RDBMS Zeta transformation completed in {}ms", duration);

        return buildTraceResult();
    }

    // =========================================================================
    // PACKAGE TRANSFORMATIONS
    // =========================================================================

    private void transformRootPackages() {
        log.debug("Transforming root packages");
        all(EPackage.class)
                .filter(pkg -> pkg.getESuperPackage() == null)
                .forEach(this::transformRootPackage);
    }

    private void transformRootPackage(EPackage rootPackage) {
        // Create RDBMS Model
        RdbmsModel rdbmsModelElement = rdbmsFactory.createRdbmsModel();
        setId(rdbmsModelElement, "(asm/" + getId(rootPackage) + ")/Model");
        rdbmsModelElement.setVersion(modelVersion);
        rdbmsModelElement.setName(rootPackage.getName());

        rdbmsModel.getResource().getContents().add(rdbmsModelElement);
        addTrace(rootPackage, ROOT_PACKAGE_TO_MODEL, rdbmsModelElement);

        // Create Configuration
        RdbmsConfiguration configuration = rdbmsFactory.createRdbmsConfiguration();
        setId(configuration, "(asm/" + getId(rootPackage) + ")/Configuration");
        configuration.setDialect(dialect);
        rdbmsModelElement.setConfiguration(configuration);
        addTrace(rootPackage, ROOT_PACKAGE_TO_CONFIGURATION, configuration);
    }

    // =========================================================================
    // CLASS TRANSFORMATIONS
    // =========================================================================

    private void transformEntityClasses() {
        log.debug("Transforming entity classes");
        all(EClass.class)
                .filter(this::isEntityType)
                .forEach(this::transformEntityClass);
    }

    private void transformEntityClass(EClass eClass) {
        log.debug("  Add table: {}", asmUtils.getClassifierFQName(eClass));

        // Create table
        RdbmsTable table = rdbmsFactory.createRdbmsTable();
        setId(table, "(asm/" + getId(eClass) + ")/Table");
        table.setSqlName(tableSqlName(eClass));
        table.setName(asmUtils.getClassifierFQName(eClass));
        table.setUuid("(asm/" + getId(eClass) + ")/Table");

        // Add to model
        EPackage rootPackage = getRootPackage(eClass);
        RdbmsModel model = (RdbmsModel) getEquivalent(rootPackage, ROOT_PACKAGE_TO_MODEL);
        if (model != null) {
            model.getRdbmsTables().add(table);
        }

        addTrace(eClass, ECLASS_TO_RDBMS_TABLE, table);

        // Set up inheritance
        for (EClass superType : eClass.getESuperTypes()) {
            if (isEntityType(superType)) {
                RdbmsTable parentTable = (RdbmsTable) getEquivalent(superType, ECLASS_TO_RDBMS_TABLE);
                if (parentTable != null) {
                    table.getParents().add(parentTable);
                }
            }
        }

        // Create system fields
        createTableIdField(eClass, table);
        createTableTypeField(eClass, table);
        createTableVersionField(eClass, table);
        createTableCreateUsernameField(eClass, table);
        createTableCreateUserIdField(eClass, table);
        createTableCreateTimestampField(eClass, table);
        createTableUpdateUsernameField(eClass, table);
        createTableUpdateUserIdField(eClass, table);
        createTableUpdateTimestampField(eClass, table);
    }

    private void createTableIdField(EClass eClass, RdbmsTable table) {
        RdbmsIdentifierField idField = rdbmsFactory.createRdbmsIdentifierField();
        setId(idField, "(asm/" + getId(eClass) + ")/TableIdField");
        idField.setName(asmUtils.getClassifierFQName(eClass) + "#_id");
        idField.setUuid("(asm/" + getId(eClass) + ")/TableIdField");
        idField.setSqlName("ID");
        fillType(idField, "java.util.UUID", null);

        table.getFields().add(idField);
        table.setPrimaryKey(idField);
        addTrace(eClass, ECLASS_TO_TABLE_ID_FIELD, idField);
    }

    private void createTableTypeField(EClass eClass, RdbmsTable table) {
        RdbmsValueField typeField = rdbmsFactory.createRdbmsValueField();
        setId(typeField, "(asm/" + getId(eClass) + ")/TableTypeField");
        typeField.setName(asmUtils.getClassifierFQName(eClass) + "#_type");
        typeField.setUuid("(asm/" + getId(eClass) + ")/TableTypeField");
        typeField.setSqlName("TYPE");
        typeField.setMandatory(true);
        fillType(typeField, "java.lang.String", null);

        table.getFields().add(typeField);
        addTrace(eClass, ECLASS_TO_TABLE_TYPE_FIELD, typeField);
    }

    private void createTableVersionField(EClass eClass, RdbmsTable table) {
        RdbmsValueField versionField = rdbmsFactory.createRdbmsValueField();
        setId(versionField, "(asm/" + getId(eClass) + ")/TableVersionField");
        versionField.setName(asmUtils.getClassifierFQName(eClass) + "#_version");
        versionField.setUuid("(asm/" + getId(eClass) + ")/TableVersionField");
        versionField.setSqlName("VERSION");
        versionField.setMandatory(false);
        fillType(versionField, "java.lang.Integer", null);

        table.getFields().add(versionField);
        addTrace(eClass, ECLASS_TO_TABLE_VERSION_FIELD, versionField);
    }

    private void createTableCreateUsernameField(EClass eClass, RdbmsTable table) {
        RdbmsValueField field = rdbmsFactory.createRdbmsValueField();
        setId(field, "(asm/" + getId(eClass) + ")/TableCreateUsernameField");
        field.setName(asmUtils.getClassifierFQName(eClass) + "#_create_username");
        field.setUuid("(asm/" + getId(eClass) + ")/TableCreateUsernameField");
        field.setSqlName("CREATE_USERNAME");
        field.setMandatory(false);
        fillType(field, "java.lang.String", null);

        table.getFields().add(field);
        addTrace(eClass, ECLASS_TO_TABLE_CREATE_USERNAME_FIELD, field);
    }

    private void createTableCreateUserIdField(EClass eClass, RdbmsTable table) {
        RdbmsValueField field = rdbmsFactory.createRdbmsValueField();
        setId(field, "(asm/" + getId(eClass) + ")/TableCreateUserIdField");
        field.setName(asmUtils.getClassifierFQName(eClass) + "#_create_user_id");
        field.setUuid("(asm/" + getId(eClass) + ")/TableCreateUserIdField");
        field.setSqlName("CREATE_USER_ID");
        field.setMandatory(false);
        fillType(field, "java.util.UUID", null);

        table.getFields().add(field);
        addTrace(eClass, ECLASS_TO_TABLE_CREATE_USER_ID_FIELD, field);
    }

    private void createTableCreateTimestampField(EClass eClass, RdbmsTable table) {
        RdbmsValueField field = rdbmsFactory.createRdbmsValueField();
        setId(field, "(asm/" + getId(eClass) + ")/TableCreateTimestampField");
        field.setName(asmUtils.getClassifierFQName(eClass) + "#_create_timestamp");
        field.setUuid("(asm/" + getId(eClass) + ")/TableCreateTimestampField");
        field.setSqlName("CREATE_TIMESTAMP");
        field.setMandatory(false);
        fillType(field, "java.time.LocalDateTime", null);

        table.getFields().add(field);
        addTrace(eClass, ECLASS_TO_TABLE_CREATE_TIMESTAMP_FIELD, field);
    }

    private void createTableUpdateUsernameField(EClass eClass, RdbmsTable table) {
        RdbmsValueField field = rdbmsFactory.createRdbmsValueField();
        setId(field, "(asm/" + getId(eClass) + ")/TableUpdateUsernameField");
        field.setName(asmUtils.getClassifierFQName(eClass) + "#_update_username");
        field.setUuid("(asm/" + getId(eClass) + ")/TableUpdateUsernameField");
        field.setSqlName("UPDATE_USERNAME");
        field.setMandatory(false);
        fillType(field, "java.lang.String", null);

        table.getFields().add(field);
        addTrace(eClass, ECLASS_TO_TABLE_UPDATE_USERNAME_FIELD, field);
    }

    private void createTableUpdateUserIdField(EClass eClass, RdbmsTable table) {
        RdbmsValueField field = rdbmsFactory.createRdbmsValueField();
        setId(field, "(asm/" + getId(eClass) + ")/TableUpdateUserIdField");
        field.setName(asmUtils.getClassifierFQName(eClass) + "#_update_user_id");
        field.setUuid("(asm/" + getId(eClass) + ")/TableUpdateUserIdField");
        field.setSqlName("UPDATE_USER_ID");
        field.setMandatory(false);
        fillType(field, "java.util.UUID", null);

        table.getFields().add(field);
        addTrace(eClass, ECLASS_TO_TABLE_UPDATE_USER_ID_FIELD, field);
    }

    private void createTableUpdateTimestampField(EClass eClass, RdbmsTable table) {
        RdbmsValueField field = rdbmsFactory.createRdbmsValueField();
        setId(field, "(asm/" + getId(eClass) + ")/TableUpdateTimestampField");
        field.setName(asmUtils.getClassifierFQName(eClass) + "#_update_timestamp");
        field.setUuid("(asm/" + getId(eClass) + ")/TableUpdateTimestampField");
        field.setSqlName("UPDATE_TIMESTAMP");
        field.setMandatory(false);
        fillType(field, "java.time.LocalDateTime", null);

        table.getFields().add(field);
        addTrace(eClass, ECLASS_TO_TABLE_UPDATE_TIMESTAMP_FIELD, field);
    }

    // =========================================================================
    // ATTRIBUTE TRANSFORMATIONS
    // =========================================================================

    private void transformAttributes() {
        log.debug("Transforming attributes");
        all(EAttribute.class)
                .filter(attr -> isEntityType(attr.getEContainingClass()))
                .filter(attr -> !attr.isDerived())
                .forEach(this::transformAttribute);
    }

    private void transformAttribute(EAttribute attr) {
        log.debug("    Add attribute: {}", asmUtils.getAttributeFQName(attr));

        RdbmsValueField field = rdbmsFactory.createRdbmsValueField();
        setId(field, "(asm/" + getId(attr) + ")/TableValueField");
        field.setUuid("(asm/" + getId(attr) + ")/TableValueField");
        field.setName(asmUtils.getAttributeFQName(attr));
        field.setSqlName(fieldSqlName(attr));
        field.setMandatory(false);

        // Set type
        EClassifier eType = attr.getEType();
        if (eType instanceof EEnum) {
            fillType(field, "java.lang.Integer", attr);
        } else if (eType != null) {
            fillType(field, eType.getInstanceClassName(), attr);
        } else {
            log.warn("Could not determine type for: {}#{}", attr.getEContainingClass().getName(), attr.getName());
            fillType(field, "java.lang.Integer", attr);
        }

        // Add to table
        RdbmsTable table = (RdbmsTable) getEquivalent(attr.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
        if (table != null) {
            table.getFields().add(field);
        }

        addTrace(attr, EATTRIBUTE_TO_TABLE_VALUE_FIELD, field);

        // Create index for identifier attributes
        if (asmUtils.isIdentifier(attr)) {
            createAttributeIndex(attr, field, table);
        }
    }

    private void createAttributeIndex(EAttribute attr, RdbmsValueField field, RdbmsTable table) {
        log.debug("    Add index: {}", asmUtils.getAttributeFQName(attr));

        RdbmsIndex index = rdbmsFactory.createRdbmsIndex();
        setId(index, "(asm/" + getId(attr) + ")/Index");
        index.setUuid("(asm/" + getId(attr) + ")/Index");
        index.setName(asmUtils.getAttributeFQName(attr));
        index.setSqlName("IDX_" + md5("(asm/" + getId(attr) + ")/Index"));
        index.getFields().add(field);

        if (table != null) {
            table.getIndexes().add(index);
        }

        addTrace(attr, EATTRIBUTE_TO_INDEX, index);
    }

    // =========================================================================
    // REFERENCE TRANSFORMATIONS
    // =========================================================================

    private void transformReferences() {
        log.debug("Transforming references - pass 1: junction tables");
        // First pass: create all junction tables
        all(EReference.class)
                .filter(ref -> isEntityType(ref.getEReferenceType()))
                .filter(ref -> isEntityType(ref.getEContainingClass()))
                .filter(ref -> !ref.isDerived())
                .forEach(this::createJunctionTableIfNeeded);

        log.debug("Transforming references - pass 2: foreign keys");
        // Second pass: create foreign keys and junction table FKs
        all(EReference.class)
                .filter(ref -> isEntityType(ref.getEReferenceType()))
                .filter(ref -> isEntityType(ref.getEContainingClass()))
                .filter(ref -> !ref.isDerived())
                .forEach(this::transformReferenceKeys);
    }

    private void createJunctionTableIfNeeded(EReference ref) {
        RuleMapping mapping = getRuleMapping(ref);
        if (mapping.joinTable && mapping.first) {
            createJunctionTable(ref);
        }
    }

    private void transformReferenceKeys(EReference ref) {
        RuleMapping mapping = getRuleMapping(ref);
        if (mapping.foreignKey) {
            createForeignKey(ref);
        }
        if (mapping.inverseForeignKey) {
            createInverseForeignKey(ref);
        }
        if (mapping.joinTable) {
            if (ref.getEOpposite() != null) {
                createJunctionTableForeignKeyBidirectional(ref);
            } else {
                createJunctionTableForeignKeyUnidirectional(ref);
            }
        }
    }

    private void createForeignKey(EReference ref) {
        log.debug("    Add foreign key: {}", asmUtils.getReferenceFQName(ref));

        RdbmsForeignKey fk = rdbmsFactory.createRdbmsForeignKey();
        setId(fk, "(asm/" + getId(ref) + ")/TableForeignKey");
        fk.setName(ref.getName());
        fk.setUuid("(asm/" + getId(ref) + ")/TableForeignKey");
        fk.setMandatory(false);
        fk.setSqlName(referenceIdentifierSqlName(ref));
        fk.setForeignKeySqlName(referenceFkSqlName(ref));

        // Set reference to target table's primary key
        RdbmsTable targetTable = (RdbmsTable) getEquivalent(ref.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
        if (targetTable != null && targetTable.getPrimaryKey() != null) {
            fk.setReferenceKey(targetTable.getPrimaryKey());
            copyTypeFromField(fk, targetTable.getPrimaryKey());
        }

        if (ref.isContainer()) {
            fk.setReadOnly(true);
            fk.setDeleteOnCascade(true);
        } else {
            fk.setReadOnly(false);
            fk.setDeleteOnCascade(false);
        }

        // Add to source table
        RdbmsTable sourceTable = (RdbmsTable) getEquivalent(ref.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
        if (sourceTable != null) {
            sourceTable.getFields().add(fk);
        }

        addTrace(ref, EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY, fk);
    }

    private void createInverseForeignKey(EReference ref) {
        log.debug("    Add inverse foreign key: {}", asmUtils.getReferenceFQName(ref));

        RdbmsForeignKey fk = rdbmsFactory.createRdbmsForeignKey();
        setId(fk, "(asm/" + getId(ref) + ")/TableInverseForeignKey");
        fk.setName(firstToLowerCase(ref.getEContainingClass().getName()) + firstToUpperCase(ref.getName()));
        fk.setUuid("(asm/" + getId(ref) + ")/TableInverseForeignKey");
        fk.setMandatory(false);
        fk.setSqlName(referenceInverseIdentifierSqlName(ref));
        fk.setForeignKeySqlName(referenceInvFkSqlName(ref));
        fk.setReadOnly(false);
        fk.setDeleteOnCascade(false);

        // Set reference to source table's primary key
        RdbmsTable sourceTable = (RdbmsTable) getEquivalent(ref.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
        if (sourceTable != null && sourceTable.getPrimaryKey() != null) {
            fk.setReferenceKey(sourceTable.getPrimaryKey());
            copyTypeFromField(fk, sourceTable.getPrimaryKey());
        }

        // Add to target table
        RdbmsTable targetTable = (RdbmsTable) getEquivalent(ref.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
        if (targetTable != null) {
            targetTable.getFields().add(fk);
        }

        addTrace(ref, EREFERENCE_TO_RDBMS_TABLE_INVERSE_FOREIGN_KEY, fk);
    }

    private void createJunctionTable(EReference ref) {
        log.debug("    Add junction table: {}", asmUtils.getReferenceFQName(ref));

        RdbmsJunctionTable junctionTable = rdbmsFactory.createRdbmsJunctionTable();
        setId(junctionTable, "(asm/" + getId(ref) + ")/JunctionTable");
        junctionTable.setSqlName(referenceManyToManyTableSqlName(ref));
        junctionTable.setUuid("(asm/" + getId(ref) + ")/JunctionTable");

        if (ref.getEOpposite() != null) {
            junctionTable.setName(asmUtils.getReferenceFQName(ref) + " to " + asmUtils.getReferenceFQName(ref.getEOpposite()));
        } else {
            junctionTable.setName(asmUtils.getReferenceFQName(ref) + " to " + asmUtils.getClassifierFQName(ref.getEReferenceType()));
        }

        // Add to model
        EPackage rootPackage = getRootPackage(ref.getEReferenceType());
        RdbmsModel model = (RdbmsModel) getEquivalent(rootPackage, ROOT_PACKAGE_TO_MODEL);
        if (model != null) {
            model.getRdbmsTables().add(junctionTable);
        }

        addTrace(ref, EREFERENCE_TO_RDBMS_JUNCTION_TABLE, junctionTable);
        log.debug("Junction table created and traced for: {}", asmUtils.getReferenceFQName(ref));

        // Create primary key for junction table
        RdbmsIdentifierField pk = rdbmsFactory.createRdbmsIdentifierField();
        setId(pk, "(asm/" + getId(ref) + ")/JunctionTablePrimaryKey");
        pk.setName(junctionTable.getName() + "#id");
        pk.setUuid("(asm/" + getId(ref) + ")/JunctionTablePrimaryKey");
        pk.setSqlName("ID");
        fillType(pk, "java.util.UUID", null);

        junctionTable.getFields().add(pk);
        junctionTable.setPrimaryKey(pk);
        addTrace(ref, EREFERENCE_TO_RDBMS_JUNCTION_TABLE_PRIMARY_KEY, pk);
    }

    private void createJunctionTableForeignKeyBidirectional(EReference ref) {
        log.debug("    Add junction foreign bidirectional key: {}", asmUtils.getReferenceFQName(ref));

        // Determine the main reference (alphabetically first) for field assignment
        boolean isFirst = ref.getName().compareTo(ref.getEOpposite().getName()) <= 0;

        // Try to find junction table - it could be traced with either reference
        // depending on which was processed first and had mapping.first=true
        RdbmsJunctionTable junctionTable = (RdbmsJunctionTable) getEquivalent(ref, EREFERENCE_TO_RDBMS_JUNCTION_TABLE);
        if (junctionTable == null) {
            junctionTable = (RdbmsJunctionTable) getEquivalent(ref.getEOpposite(), EREFERENCE_TO_RDBMS_JUNCTION_TABLE);
        }
        if (junctionTable == null) { return; }

        RdbmsForeignKey fk = rdbmsFactory.createRdbmsForeignKey();
        setId(fk, "(asm/" + getId(ref) + ")/JunctionTableForeignKeyBidirectional");
        fk.setName(ref.getName());
        fk.setUuid("(asm/" + getId(ref) + ")/JunctionTableForeignKeyBidirectional");
        fk.setMandatory(isMandatory(ref));
        fk.setSqlName(referenceIdentifierSqlName(ref));
        fk.setForeignKeySqlName(referenceFkSqlName(ref));
        fk.setReadOnly(true);
        fk.setDeleteOnCascade(true);

        // Set reference to target table's primary key
        RdbmsTable targetTable = (RdbmsTable) getEquivalent(ref.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
        if (targetTable != null && targetTable.getPrimaryKey() != null) {
            fk.setReferenceKey(targetTable.getPrimaryKey());
            copyTypeFromField(fk, targetTable.getPrimaryKey());
        }

        junctionTable.getFields().add(fk);
        if (isFirst) {
            junctionTable.setField1(fk);
        } else {
            junctionTable.setField2(fk);
        }

        addTrace(ref, EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_BIDIRECTIONAL, fk);
    }

    private void createJunctionTableForeignKeyUnidirectional(EReference ref) {
        log.debug("    Add junction foreign unidirectional key: {}", asmUtils.getReferenceFQName(ref));

        RdbmsJunctionTable junctionTable = (RdbmsJunctionTable) getEquivalent(ref, EREFERENCE_TO_RDBMS_JUNCTION_TABLE);
        if (junctionTable == null) { return; }

        // FK1 - to target type
        RdbmsForeignKey fk1 = rdbmsFactory.createRdbmsForeignKey();
        setId(fk1, "(asm/" + getId(ref) + ")/JunctionTableForeignKeyUnidirectional1");
        fk1.setName(ref.getName());
        fk1.setUuid("(asm/" + getId(ref) + ")/JunctionTableForeignKeyUnidirectional1");
        fk1.setMandatory(isMandatory(ref));
        fk1.setSqlName(abbreviate(referenceIdentifierSqlName(ref), columnNameMaxSize - 1).toUpperCase() + "1");
        fk1.setForeignKeySqlName(referenceFkSqlName(ref) + "1");
        fk1.setReadOnly(true);
        fk1.setDeleteOnCascade(true);

        RdbmsTable targetTable = (RdbmsTable) getEquivalent(ref.getEReferenceType(), ECLASS_TO_RDBMS_TABLE);
        if (targetTable != null && targetTable.getPrimaryKey() != null) {
            fk1.setReferenceKey(targetTable.getPrimaryKey());
            copyTypeFromField(fk1, targetTable.getPrimaryKey());
        }

        junctionTable.getFields().add(fk1);
        junctionTable.setField1(fk1);
        addTrace(ref, EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_UNIDIRECTIONAL_1, fk1);

        // FK2 - to containing class
        RdbmsForeignKey fk2 = rdbmsFactory.createRdbmsForeignKey();
        setId(fk2, "(asm/" + getId(ref) + ")/JunctionTableForeignKeyUnidirectional2");
        fk2.setName(ref.getEContainingClass().getName() + "#" + ref.getName());
        fk2.setUuid("(asm/" + getId(ref) + ")/JunctionTableForeignKeyUnidirectional2");
        fk2.setMandatory(false);
        fk2.setReadOnly(true);
        fk2.setDeleteOnCascade(true);

        RdbmsTable sourceTable = (RdbmsTable) getEquivalent(ref.getEContainingClass(), ECLASS_TO_RDBMS_TABLE);
        if (sourceTable != null && sourceTable.getPrimaryKey() != null) {
            fk2.setReferenceKey(sourceTable.getPrimaryKey());
            copyTypeFromField(fk2, sourceTable.getPrimaryKey());
            fk2.setSqlName(abbreviate(sqlLongName(ref) + "_" + tableSqlName(ref.getEContainingClass()) + "_" + sourceTable.getPrimaryKey().getSqlName(), columnNameMaxSize - 1).toUpperCase() + "2");
        }
        fk2.setForeignKeySqlName(referenceUniFkSqlName(ref) + "2");

        junctionTable.getFields().add(fk2);
        junctionTable.setField2(fk2);
        addTrace(ref, EREFERENCE_TO_RDBMS_JUNCTION_TABLE_FK_UNIDIRECTIONAL_2, fk2);
    }

    // =========================================================================
    // POST-PROCESSING
    // =========================================================================

    private void postProcess() {
        // Apply name mappings from the model
        rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(NameMappings.class::isInstance)
                .map(NameMappings.class::cast)
                .flatMap(mappings -> mappings.getNameMappings().stream())
                .forEach(this::applyNameMapping);
    }

    private void applyNameMapping(NameMapping mapping) {
        // Find element by UUID and apply the mapped name
        rdbmsModel.getResourceSet().getResources().stream()
                .flatMap(r -> r.getContents().stream())
                .filter(RdbmsModel.class::isInstance)
                .map(RdbmsModel.class::cast)
                .flatMap(model -> getAllRdbmsElements(model).stream())
                .filter(elem -> mapping.getFullyQualifiedName().equals(elem.getUuid()))
                .findFirst()
                .ifPresent(elem -> {
                    log.debug("Replace sqlName in: {} to: {}", elem, mapping.getRdbmsName());
                    elem.setSqlName(mapping.getRdbmsName());
                });
    }

    private List<RdbmsElement> getAllRdbmsElements(RdbmsModel model) {
        List<RdbmsElement> elements = new ArrayList<>();
        for (RdbmsTable table : model.getRdbmsTables()) {
            elements.add(table);
            elements.addAll(table.getFields());
            elements.addAll(table.getIndexes());
            elements.addAll(table.getUniqueConstraints());
        }
        return elements;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private boolean isEntityType(EClass eClass) {
        return eClass != null && asmUtils.isEntityType(eClass);
    }

    private String getId(EObject element) {
        if (element instanceof EModelElement) {
            EAnnotation ann = ((EModelElement) element).getEAnnotation("http://blackbelt.hu/judo/meta/ExtendedMetadata/id");
            if (ann != null) {
                return ann.getDetails().get("value");
            }
        }
        if (element instanceof ENamedElement) {
            return ((ENamedElement) element).getName();
        }
        return String.valueOf(System.identityHashCode(element));
    }

    private void setId(EObject element, String id) {
        // For RDBMS elements, the ID is set via uuid field
    }

    private EPackage getRootPackage(EClassifier classifier) {
        EPackage pkg = classifier.getEPackage();
        while (pkg != null && pkg.getESuperPackage() != null) {
            pkg = pkg.getESuperPackage();
        }
        return pkg;
    }

    private String tableSqlName(EClass eClass) {
        String name = asmUtils.getClassifierFQName(eClass).replace(".", "_").toUpperCase();
        return abbreviate(tablePrefix + name, tableNameMaxSize);
    }

    private String fieldSqlName(EAttribute attr) {
        String name = attr.getName().toUpperCase();
        return abbreviate(columnPrefix + name, columnNameMaxSize);
    }

    private String referenceIdentifierSqlName(EReference ref) {
        String name = ref.getName().toUpperCase() + "_ID";
        return abbreviate(columnPrefix + name, columnNameMaxSize);
    }

    private String referenceFkSqlName(EReference ref) {
        return foreignKeyPrefix + md5("(asm/" + getId(ref) + ")/TableForeignKey");
    }

    private String referenceInverseIdentifierSqlName(EReference ref) {
        String name = ref.getEContainingClass().getName().toUpperCase() + "_" + ref.getName().toUpperCase() + "_ID";
        return abbreviate(columnPrefix + name, columnNameMaxSize);
    }

    private String referenceInvFkSqlName(EReference ref) {
        return inverseForeignKeyPrefix + md5("(asm/" + getId(ref) + ")/TableInverseForeignKey");
    }

    private String referenceUniFkSqlName(EReference ref) {
        return foreignKeyPrefix + md5("(asm/" + getId(ref) + ")/JunctionTableForeignKeyUnidirectional");
    }

    private String referenceManyToManyTableSqlName(EReference ref) {
        String name = ref.getEContainingClass().getName() + "_" + ref.getName();
        return abbreviate(junctionTablePrefix + name.toUpperCase(), tableNameMaxSize);
    }

    private String sqlLongName(EReference ref) {
        return ref.getEContainingClass().getName() + "_" + ref.getName();
    }

    private String abbreviate(String name, int maxLength) {
        return AbbreviateUtils.abbreviate(name, maxLength, "_");
    }

    private String md5(String input) {
        return Hashing.md5().hashString(input, StandardCharsets.UTF_8).toString();
    }

    private String firstToLowerCase(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    private String firstToUpperCase(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private boolean isMandatory(EReference ref) {
        return ref.getLowerBound() > 0;
    }

    private void fillType(RdbmsField field, String javaType, EAttribute attr) {
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

    private void copyTypeFromField(RdbmsField target, RdbmsField source) {
        target.setRdbmsTypeName(source.getRdbmsTypeName());
        target.setSize(source.getSize());
        target.setPrecision(source.getPrecision());
        target.setScale(source.getScale());
        target.setStorageByte(source.getStorageByte());
    }

    /**
     * Rule mapping determines how a reference should be mapped to RDBMS.
     */
    private static class RuleMapping {
        boolean foreignKey;
        boolean inverseForeignKey;
        boolean joinTable;
        boolean first;
    }

    private RuleMapping getRuleMapping(EReference ref) {
        Rule rule = rules.getRuleFromReference(ref);
        RuleMapping mapping = new RuleMapping();
        mapping.foreignKey = rule.isForeignKey();
        mapping.inverseForeignKey = rule.isInverseForeignKey();
        mapping.joinTable = rule.isJoinTable();
        mapping.first = rule.isFirst();
        return mapping;
    }

    private void addTrace(EObject source, String ruleName, EObject target) {
        traceMap.computeIfAbsent(source, k -> new ConcurrentHashMap<>())
                .put(ruleName, target);
    }

    private EObject getEquivalent(EObject source, String ruleName) {
        Map<String, EObject> rules = traceMap.get(source);
        return rules != null ? rules.get(ruleName) : null;
    }

    private Map<EObject, List<EObject>> buildTraceResult() {
        Map<EObject, List<EObject>> result = new HashMap<>();
        for (Map.Entry<EObject, Map<String, EObject>> entry : traceMap.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return result;
    }
}
