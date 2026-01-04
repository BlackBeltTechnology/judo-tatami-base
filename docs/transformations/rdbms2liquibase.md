# RDBMS to Liquibase Transformation

## Overview

The RDBMS to Liquibase transformation converts the relational database schema model (RDBMS) into Liquibase changelog format. This transformation generates database migration scripts that can be executed by Liquibase to create and maintain the database schema.

**Module:** `judo-tatami-rdbms2liquibase`  
**Source Model:** RDBMS (hu.blackbelt.judo.meta.rdbms)  
**Target Model:** Liquibase (hu.blackbelt.judo.meta.liquibase)

## ETL Files

| File | Purpose |
|------|---------|
| `rdbmsToLiquibase.etl` | Main transformation for full schema generation |
| `rdbmsIncrementalToLiquibase.etl` | Incremental schema migration transformation |
| `modules/table.etl` | Table to CreateTable changeset transformation |
| `modules/field.etl` | Field to Column transformation |
| `modules/incremental.etl` | Incremental change detection |
| `modules/beforeIncremental.etl` | Pre-incremental operations |
| `modules/afterIncremental.etl` | Post-incremental operations |
| `modules/dataUpdateBeforeIncremental.etl` | Data migration before schema changes |
| `modules/dataUpdateAfterIncremental.etl` | Data migration after schema changes |
| `modules/dbBackup.etl` | Database backup operations |
| `modules/dbCheckup.etl` | Database integrity checks |
| `modules/dbDropBackup.etl` | Drop backup tables |

## Transformation Rules

### Table Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `TableToCreateTable` | RdbmsTable | CreateTable | Create table definition (lazy, greedy) |
| `TableToCreateTableChangeSet` | RdbmsTable | ChangeSet | Create changeset for table creation |
| `TableToCreateForeignKeysChangeSet` | RdbmsTable | ChangeSet | Create changeset for foreign keys |
| `TableToAddNotNullChangeSet` | RdbmsTable | ChangeSet | Create changeset for NOT NULL constraints |

### Field Rules

| Rule Name | Source | Target | Description |
|-----------|--------|--------|-------------|
| `FieldToColumn` | RdbmsField | Column | Abstract rule for field to column |
| `IdentifierFieldToCreateTableColumn` | RdbmsIdentifierField | Column | Transform primary key field |
| `IdentifierFieldToCreateTableColumnAddPrimaryKeyConstraint` | RdbmsIdentifierField | Constraints | Add primary key constraint |
| `ValueFieldToCreateTableColumn` | RdbmsValueField | Column | Transform value field to column |
| `ForeignKeyFieldToAddForeignKeyConstraint` | RdbmsForeignKey | AddForeignKeyConstraint | Abstract FK constraint rule |
| `ForeignKeyFieldToCreateTableAddForeignKeyConstraint` | RdbmsForeignKey | AddForeignKeyConstraint | Create FK constraint |
| `FieldToAddNotNullConstraint` | RdbmsField (mandatory) | AddNotNullConstraint | Abstract NOT NULL rule |
| `FieldToCreateTableAddNotNullConstraint` | RdbmsField (mandatory) | AddNotNullConstraint | Add NOT NULL constraint |
| `IndexToCreateIndex` | RdbmsIndex | CreateIndex | Create index definition |
| `AddUniqueConstraints` | RdbmsUniqueConstraint | AddUniqueConstraint | Create unique constraint |

## ChangeSet Structure

Each changeset has the following properties:

| Property | Description | Example |
|----------|-------------|---------|
| `id` | Unique changeset identifier | `create-table-CUSTOMER` |
| `author` | Author of the changeset | `tatami-rdbms2liquibase` |
| `dbms` | Target database dialect | `postgresql`, `hsqldb`, `oracle` |
| `context` | Execution context | `full and 1.0.0` |
| `logicalFilePath` | Logical grouping | `create-tables`, `create-foreignkeys` |

## Generated ChangeSet Types

### Table Creation
```xml
<changeSet id="create-table-CUSTOMER" author="tatami-rdbms2liquibase" 
           dbms="postgresql" context="full and 1.0.0">
    <createTable tableName="CUSTOMER">
        <column name="ID" type="UUID">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="TYPE" type="VARCHAR(255)"/>
        <column name="VERSION" type="INTEGER"/>
        <column name="NAME" type="VARCHAR(255)"/>
    </createTable>
</changeSet>
```

### Foreign Key Creation
```xml
<changeSet id="create-foreignkeys-ORDER" author="tatami-rdbms2liquibase"
           dbms="postgresql" context="1.0.0">
    <addForeignKeyConstraint 
        baseTableName="ORDER"
        baseColumnNames="CUSTOMER_ID"
        constraintName="FK_ORDER_CUSTOMER"
        referencedTableName="CUSTOMER"
        referencedColumnNames="ID"/>
</changeSet>
```

### Index Creation
```xml
<changeSet id="create-indexes-in-CUSTOMER" author="tatami-rdbms2liquibase"
           dbms="postgresql" context="1.0.0">
    <createIndex tableName="CUSTOMER" indexName="IDX_abc123">
        <column name="EMAIL"/>
    </createIndex>
</changeSet>
```

### NOT NULL Constraint
```xml
<changeSet id="add-not-null-CUSTOMER" author="tatami-rdbms2liquibase"
           dbms="postgresql" context="1.0.0">
    <addNotNullConstraint 
        tableName="CUSTOMER"
        columnName="NAME"
        columnDataType="VARCHAR(255)"/>
</changeSet>
```

## Logical File Paths

ChangeSets are organized by logical file paths for proper ordering:

| Logical Path | Purpose | Order |
|--------------|---------|-------|
| `create-tables` | Table creation | 1 |
| `create-foreignkeys` | Foreign key constraints | 2 |
| `create-indexes` | Index creation | 3 |
| `add-unique-constraints` | Unique constraints | 4 |
| `add-not-null` | NOT NULL constraints | 5 |

## Incremental Transformation

For schema migrations between versions, the incremental transformation handles:

1. **Before Incremental**: Prepare for migration
   - Backup existing data
   - Drop constraints that would block changes

2. **Incremental Changes**: Apply schema modifications
   - Add new tables
   - Add new columns
   - Modify column types
   - Add/remove constraints

3. **After Incremental**: Finalize migration
   - Restore data from backups
   - Re-enable constraints
   - Drop backup tables

## Context Usage

Contexts control when changesets are executed:

| Context | Description |
|---------|-------------|
| `full` | Full schema creation (new database) |
| `{version}` | Apply for specific model version |
| `full and {version}` | Both full creation and version-specific |

## Usage Example

```java
// Create source RDBMS model
RdbmsModel rdbmsModel = RdbmsModel.buildRdbmsModel().build();
// ... populate with tables from ASM2RDBMS transformation

// Create target Liquibase model
LiquibaseModel liquibaseModel = LiquibaseModel.buildLiquibaseModel().build();

// Execute transformation
Rdbms2LiquibaseTransformationTrace trace = executeRdbms2LiquibaseTransformation(
    rdbms2LiquibaseParameter()
        .rdbmsModel(rdbmsModel)
        .liquibaseModel(liquibaseModel)
        .dialect("postgresql")
);

// Access generated changesets
Collection<ChangeSet> changeSets = liquibaseModel.getLiquibaseUtils().all(ChangeSet.class);
```

## Zeta Implementation

The Zeta implementation uses Java classes with `@TransformRule` annotations:

```java
@TransformationContext(
    sourceModel = RdbmsModel.class,
    targetModel = LiquibaseModel.class
)
public class Rdbms2LiquibaseZetaTransformation {
    
    @TransformRule(name = Rdbms2LiquibaseRuleNames.TABLE_TO_CREATE_TABLE)
    @Lazy @Greedy
    public CreateTable transformTableToCreateTable(RdbmsTable table, TransformationContext ctx) {
        CreateTable createTable = LiquibaseFactory.eINSTANCE.createCreateTable();
        createTable.setTableName(table.getSqlName());
        createTable.setRemarks(table.getUuid());
        return createTable;
    }
    
    @TransformRule(name = Rdbms2LiquibaseRuleNames.TABLE_TO_CREATE_TABLE_CHANGESET)
    @Greedy
    public ChangeSet transformTableToChangeSet(RdbmsTable table, TransformationContext ctx) {
        ChangeSet changeSet = LiquibaseFactory.eINSTANCE.createChangeSet();
        changeSet.setId("create-table-" + table.getSqlName());
        changeSet.setAuthor("tatami-rdbms2liquibase");
        changeSet.setDbms(dialect);
        changeSet.setContext("full and " + modelVersion);
        changeSet.setLogicalFilePath("create-tables");
        
        CreateTable createTable = ctx.resolve(table, Rdbms2LiquibaseRuleNames.TABLE_TO_CREATE_TABLE);
        changeSet.getCreateTable().add(createTable);
        
        return changeSet;
    }
    
    @TransformRule(name = Rdbms2LiquibaseRuleNames.IDENTIFIER_FIELD_TO_COLUMN)
    @Greedy
    public Column transformIdentifierField(RdbmsIdentifierField field, TransformationContext ctx) {
        Column column = LiquibaseFactory.eINSTANCE.createColumn();
        column.setName(field.getSqlName());
        column.setType(getFieldDefinition(field));
        column.setRemarks(field.getUuid());
        
        Constraints constraints = LiquibaseFactory.eINSTANCE.createConstraints();
        constraints.setPrimaryKey(true);
        constraints.setNullable(false);
        column.setConstraints(constraints);
        
        return column;
    }
}
```

Rule name constants are defined in `Rdbms2LiquibaseRuleNames`:

```java
public final class Rdbms2LiquibaseRuleNames {
    public static final String TABLE_TO_CREATE_TABLE = "TableToCreateTable";
    public static final String TABLE_TO_CREATE_TABLE_CHANGESET = "TableToCreateTableChangeSet";
    public static final String TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET = "TableToCreateForeignKeysChangeSet";
    public static final String TABLE_TO_ADD_NOT_NULL_CHANGESET = "TableToAddNotNullChangeSet";
    public static final String FIELD_TO_COLUMN = "FieldToColumn";
    public static final String IDENTIFIER_FIELD_TO_COLUMN = "IdentifierFieldToCreateTableColumn";
    public static final String VALUE_FIELD_TO_COLUMN = "ValueFieldToCreateTableColumn";
    public static final String FOREIGN_KEY_TO_CONSTRAINT = "ForeignKeyFieldToCreateTableAddForeignKeyConstraint";
    public static final String INDEX_TO_CREATE_INDEX = "IndexToCreateIndex";
    public static final String ADD_UNIQUE_CONSTRAINTS = "AddUniqueConstraints";
    // ...
}
```

## ETL to Zeta Rule Mapping

### File Structure Mapping

| ETL File | Zeta Class |
|----------|------------|
| `rdbmsToLiquibase.etl` | `Rdbms2LiquibaseZetaTransformation.java` |
| `rdbmsIncrementalToLiquibase.etl` | `Rdbms2LiquibaseIncrementalZetaTransformation.java` |
| `liquibase/modules/table.etl` | Inline in main transformation |
| `liquibase/modules/field.etl` | Inline in main transformation |
| `liquibase/modules/incremental.etl` | `incremental/IncrementalZetaTransformation.java` |
| `liquibase/modules/beforeIncremental.etl` | `incremental/BeforeIncrementalZetaTransformation.java` |
| `liquibase/modules/afterIncremental.etl` | `incremental/AfterIncrementalZetaTransformation.java` |

### Key Rule Mapping

| ETL Rule | Zeta Implementation | Notes |
|----------|---------------------|-------|
| `TableToCreateTable` | `transformTableToCreateTable()` | @Lazy @Greedy |
| `TableToCreateTableChangeSet` | `transformTableToChangeSet()` | @Greedy |
| `IdentifierFieldToColumn` | `transformIdentifierField()` | Primary key with constraints |
| `ValueFieldToColumn` | `transformValueField()` | Type-mapped columns |
| `ForeignKeyToConstraint` | `transformForeignKey()` | FK constraint with cascades |
| `IndexToCreateIndex` | `transformIndex()` | Index changeset |

### Incremental Transformation Phases

The incremental transformation uses sub-transformations:

| Phase | Class | Description |
|-------|-------|-------------|
| Before | `BeforeIncrementalZetaTransformation` | Pre-migration changesets |
| Data Before | `DataUpdateBeforeZetaTransformation` | Data migration before schema |
| Schema | `IncrementalZetaTransformation` | Schema modifications |
| Data After | `DataUpdateAfterZetaTransformation` | Data migration after schema |
| After | `AfterIncrementalZetaTransformation` | Post-migration changesets |
| Backup | `DbBackupZetaTransformation` | Backup operations |
| Checkup | `DbCheckupZetaTransformation` | Validation checks |

For detailed migration guidance, see the [ETL to Zeta Migration Guide](../migration/etl-to-zeta-migration.md).
