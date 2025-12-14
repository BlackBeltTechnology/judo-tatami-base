# ASM to RDBMS Transformation

## Overview

The ASM to RDBMS transformation converts the Abstract Semantic Model (ASM) into a relational database schema model (RDBMS). This transformation maps entity classes to database tables, attributes to fields, and references to foreign keys or junction tables.

**Module:** `judo-tatami-asm2rdbms`  
**Source Model:** ASM (hu.blackbelt.judo.meta.asm / Ecore)  
**Target Model:** RDBMS (hu.blackbelt.judo.meta.rdbms)

## ETL Files

| File | Purpose |
|------|---------|
| `asmToRdbms.etl` | Main orchestrator, imports all modules |
| `modules/package.etl` | Root package to model/configuration transformation |
| `modules/class.etl` | Entity class to table transformation |
| `modules/attribute.etl` | Attribute to field transformation |
| `modules/reference.etl` | Reference to foreign key/junction table transformation |
| `excelToTypeMapping.etl` | Excel-based type mapping configuration |
| `excelToRules.etl` | Excel-based transformation rules |
| `excelToNameMapping.etl` | Excel-based name mapping configuration |

## Transformation Rules

### Package Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `rootPackegeToModel` | EPackage (root) | RdbmsModel | Transform root package to RDBMS model |
| `rootPackegeToConfiguration` | EPackage (root) | RdbmsConfiguration | Create RDBMS configuration with dialect |

### Class Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `EClassToRdbmsTable` | EClass (entity) | RdbmsTable | Transform entity class to table |
| `EClassToTableIdField` | EClass (entity) | RdbmsIdentifierField | Create primary key field (ID) |
| `EClassToTableTypeField` | EClass (entity) | RdbmsValueField | Create type discriminator field |
| `EClassToTableVersionField` | EClass (entity) | RdbmsValueField | Create optimistic locking version field |
| `EClassToTableCreateUsernameField` | EClass (entity) | RdbmsValueField | Create audit field for creator username |
| `EClassToTableCreateUserIdField` | EClass (entity) | RdbmsValueField | Create audit field for creator user ID |
| `EClassToTableCreateTimestampField` | EClass (entity) | RdbmsValueField | Create audit field for creation timestamp |
| `EClassToTableUpdateUsernameField` | EClass (entity) | RdbmsValueField | Create audit field for updater username |
| `EClassToTableUpdateUserIdField` | EClass (entity) | RdbmsValueField | Create audit field for updater user ID |
| `EClassToTableUpdateTimestampField` | EClass (entity) | RdbmsValueField | Create audit field for update timestamp |

### Attribute Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `EAttributeToRdbmsField` | EAttribute | RdbmsField | Abstract rule for attribute transformation |
| `EAttributeToTableValueField` | EAttribute (non-derived) | RdbmsValueField | Transform attribute to value field |
| `EAttributeToIndex` | EAttribute (identifier) | RdbmsIndex | Create index for identifier attributes |

### Reference Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `EReferenceToRdbmsTableForeignKey` | EReference | RdbmsForeignKey | Create foreign key for references |
| `EReferenceToRdbmsTableInverseForeignKey` | EReference | RdbmsForeignKey | Create inverse foreign key |
| `EReferenceToRdbmsJunctionTable` | EReference | RdbmsJunctionTable | Create junction table for many-to-many |
| `EReferenceToRdbmsJunctionTablePrimaryKey` | EReference | RdbmsIdentifierField | Create junction table primary key |
| `EReferenceToRdbmsJunctionTableForeignKeyBidirectional` | EReference (bidirectional) | RdbmsForeignKey | Create junction FK for bidirectional refs |
| `EReferenceToRdbmsJunctionTableForeignKeyUnidirectional` | EReference (unidirectional) | RdbmsForeignKey (2x) | Create junction FKs for unidirectional refs |

## Generated Table Structure

Each entity table includes these standard fields:

| Field | SQL Name | Type | Description |
|-------|----------|------|-------------|
| Primary Key | `ID` | UUID | Unique identifier |
| Type | `TYPE` | String | Entity type discriminator (mandatory) |
| Version | `VERSION` | Integer | Optimistic locking version |
| Create Username | `CREATE_USERNAME` | String | Creator's username |
| Create User ID | `CREATE_USER_ID` | UUID | Creator's user ID |
| Create Timestamp | `CREATE_TIMESTAMP` | LocalDateTime | Creation timestamp |
| Update Username | `UPDATE_USERNAME` | String | Last updater's username |
| Update User ID | `UPDATE_USER_ID` | UUID | Last updater's user ID |
| Update Timestamp | `UPDATE_TIMESTAMP` | LocalDateTime | Last update timestamp |

## Type Mapping

| Java Type | RDBMS Field Type |
|-----------|------------------|
| `java.util.UUID` | UUID field |
| `java.lang.String` | String field |
| `java.lang.Integer` | Integer field |
| `java.lang.Long` | Long field |
| `java.lang.Float` | Float field |
| `java.lang.Double` | Double field |
| `java.math.BigDecimal` | Decimal field |
| `java.lang.Boolean` | Boolean field |
| `java.time.LocalDate` | Date field |
| `java.time.LocalDateTime` | Timestamp field |
| `java.time.LocalTime` | Time field |
| `byte[]` | Binary field |
| EEnum | Integer field |

## Reference Mapping Strategy

References are mapped based on cardinality and bidirectionality:

| Source Cardinality | Target Cardinality | Bidirectional | Strategy |
|--------------------|--------------------|---------------|----------|
| 0..1 / 1 | 0..1 / 1 | Yes/No | Foreign key on source table |
| 0..1 / 1 | 0..* | Yes | Foreign key on target table |
| 0..* | 0..1 / 1 | Yes | Foreign key on source table |
| 0..* | 0..* | Yes | Junction table |
| 0..* | 0..* | No | Junction table with both FKs |

## ID Generation

| Element Type | ID Pattern |
|--------------|------------|
| RdbmsModel | `(asm/{packageId})/Model` |
| RdbmsConfiguration | `(asm/{packageId})/Configuration` |
| RdbmsTable | `(asm/{classId})/Table` |
| RdbmsIdentifierField (PK) | `(asm/{classId})/TableIdField` |
| RdbmsValueField | `(asm/{attributeId})/TableValueField` |
| RdbmsForeignKey | `(asm/{referenceId})/TableForeignKey` |
| RdbmsJunctionTable | `(asm/{referenceId})/JunctionTable` |
| RdbmsIndex | `(asm/{attributeId})/Index` |

## SQL Name Generation

SQL names are generated with abbreviation and uniqueness:

- Table names: Derived from fully qualified class name
- Field names: Derived from attribute/reference name
- Foreign key names: Generated with `FK_` prefix and MD5 hash for uniqueness
- Index names: Generated with `IDX_` prefix and MD5 hash

## Usage Example

```java
// Create source ASM model
AsmModel asmModel = AsmModel.buildAsmModel().build();
// ... populate ASM model with entities

// Create target RDBMS model
RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();

// Execute transformation
Asm2RdbmsTransformationTrace trace = executeAsm2RdbmsTransformation(
    asm2RdbmsParameter()
        .asmModel(asmModel)
        .rdbmsModel(rdbmsModel)
        .dialect("postgresql")
        .modelVersion("1.0.0")
);

// Access generated tables
Collection<RdbmsTable> tables = rdbmsModel.getRdbmsUtils().all(RdbmsTable.class);
```

## Zeta Implementation

The Zeta implementation uses Java classes with `@TransformRule` annotations:

```java
@TransformationContext(
    sourceModel = AsmModel.class,
    targetModel = RdbmsModel.class
)
public class Asm2RdbmsZetaTransformation {
    
    @TransformRule(name = Asm2RdbmsRuleNames.ECLASS_TO_RDBMS_TABLE)
    @Primary
    public RdbmsTable transformEntityClass(EClass eClass, TransformationContext ctx) {
        if (!asmUtils.isEntityType(eClass)) {
            return null;
        }
        RdbmsTable table = RdbmsFactory.eINSTANCE.createRdbmsTable();
        table.setId("(asm/" + eClass.getId() + ")/Table");
        table.setSqlName(calculateTableSqlName(eClass));
        table.setName(asmUtils.getClassifierFQName(eClass));
        table.setUuid("(asm/" + eClass.getId() + ")/Table");
        return table;
    }
    
    @TransformRule(name = Asm2RdbmsRuleNames.ECLASS_TO_TABLE_ID_FIELD)
    public RdbmsIdentifierField transformEntityClassToIdField(EClass eClass, TransformationContext ctx) {
        if (!asmUtils.isEntityType(eClass)) {
            return null;
        }
        RdbmsIdentifierField idField = RdbmsFactory.eINSTANCE.createRdbmsIdentifierField();
        idField.setId("(asm/" + eClass.getId() + ")/TableIdField");
        idField.setName(asmUtils.getClassifierFQName(eClass) + "#_id");
        idField.setSqlName("ID");
        // ... set type to UUID
        return idField;
    }
}
```

Rule name constants are defined in `Asm2RdbmsRuleNames`:

```java
public final class Asm2RdbmsRuleNames {
    public static final String ROOT_PACKAGE_TO_MODEL = "rootPackegeToModel";
    public static final String ROOT_PACKAGE_TO_CONFIGURATION = "rootPackegeToConfiguration";
    public static final String ECLASS_TO_RDBMS_TABLE = "EClassToRdbmsTable";
    public static final String ECLASS_TO_TABLE_ID_FIELD = "EClassToTableIdField";
    public static final String ECLASS_TO_TABLE_TYPE_FIELD = "EClassToTableTypeField";
    public static final String ECLASS_TO_TABLE_VERSION_FIELD = "EClassToTableVersionField";
    public static final String EATTRIBUTE_TO_TABLE_VALUE_FIELD = "EAttributeToTableValueField";
    public static final String EATTRIBUTE_TO_INDEX = "EAttributeToIndex";
    public static final String EREFERENCE_TO_RDBMS_TABLE_FOREIGN_KEY = "EReferenceToRdbmsTableForeignKey";
    public static final String EREFERENCE_TO_RDBMS_JUNCTION_TABLE = "EReferenceToRdbmsJunctionTable";
    // ...
}
```

## ETL to Zeta Rule Mapping

### File Structure Mapping

| ETL File | Zeta Class |
|----------|------------|
| `asmToRdbms.etl` | `Asm2RdbmsZetaTransformation.java` |
| `modules/package.etl` | Inline in main transformation |
| `modules/class.etl` | Inline in main transformation |
| `modules/attribute.etl` | Inline in main transformation |
| `modules/reference.etl` | Inline in main transformation |
| `excelToTypeMapping.etl` | Type mapping loaded programmatically |
| `excelToRules.etl` | Rules applied programmatically |
| `excelToNameMapping.etl` | Name mapping loaded programmatically |

### Key Rule Mapping

| ETL Rule | Zeta Implementation | Notes |
|----------|---------------------|-------|
| `rootPackegeToModel` | Phase 1: Package transformation | Model created first |
| `rootPackegeToConfiguration` | Phase 1: Package transformation | Configuration with dialect |
| `EClassToRdbmsTable` | Phase 2: Class transformation | Includes system fields |
| `EClassToTableIdField` | Phase 2: Class transformation | UUID primary key |
| `EClassToTableTypeField` | Phase 2: Class transformation | Discriminator field |
| `EAttributeToTableValueField` | Phase 3: Attribute transformation | Type-mapped value fields |
| `EAttributeToIndex` | Phase 3: Attribute transformation | Identifier indexes |
| `EReferenceToRdbmsTableForeignKey` | Phase 4: Reference transformation | Foreign key creation |
| `EReferenceToRdbmsJunctionTable` | Phase 4: Reference transformation | Many-to-many handling |

### Transformation Phases

The Zeta implementation executes in 5 phases:

| Phase | Description |
|-------|-------------|
| Phase 1 | Package transformation (model, configuration) |
| Phase 2 | Class transformation (tables, system fields) |
| Phase 3 | Attribute transformation (value fields, indexes) |
| Phase 4 | Reference transformation (foreign keys, junction tables) |
| Phase 5 | Post-processing (name mapping, validation) |

For detailed migration guidance, see the [ETL to Zeta Migration Guide](../migration/etl-to-zeta-migration.md).
