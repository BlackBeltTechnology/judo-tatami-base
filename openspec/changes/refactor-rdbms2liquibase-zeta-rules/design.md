# Design: Refactor Rdbms2Liquibase Zeta to Use ETL-Based Rules

## Current State Analysis

The current `Rdbms2LiquibaseZetaTransformation` has two patterns mixed:

1. **Procedural Methods** (lines 131-417): Manual `transformTables()`, `transformFields()`, etc. that iterate and create elements directly
2. **Annotated Rules** (lines 469-882): `@TransformRule` annotated methods that are NOT used by the executor

The procedural methods use:
- `addTrace(source, ruleName, target)` - manual trace management
- `getEquivalent(source, ruleName)` - manual equivalent lookup

This doesn't match the ETL pattern where:
- `s.equivalent("TableToCreateTable")` triggers lazy creation and returns the result
- Rules are executed by the framework based on guards and dependencies

## ETL Rule Structure

### table.etl

| Rule | Annotations | Key Pattern |
|------|-------------|-------------|
| `TableToCreateTable` | `@lazy`, `@greedy` | Base table creation, called lazily |
| `TableToCreateTableChangeSet` | `@greedy` | Uses `s.equivalent("TableToCreateTable")` |
| `TableToCreateForeignKeysChangeSet` | `@greedy` | Guard: has FK fields |
| `TableToAddNotNullChangeSet` | `@greedy` | Guard: has mandatory fields |

### field.etl

| Rule | Annotations | Key Pattern |
|------|-------------|-------------|
| `FieldToColumn` | `@abstract`, `@greedy` | Base column creation |
| `IdentifierFieldToCreateTableColumn` | Extends `FieldToColumn` | Uses `s.table().equivalent("TableToCreateTable")` |
| `ValueFieldToCreateTableColumn` | Extends `FieldToColumn` | Uses `s.table().equivalent("TableToCreateTable")` |
| `IdentifierFieldToCreateTableColumnAddPrimaryKeyConstraint` | - | Uses `s.equivalent("IdentifierFieldToCreateTableColumn")` |
| `ForeignKeyFieldToAddForeignKeyConstraint` | `@abstract` | Base FK constraint |
| `ForeignKeyFieldToCreateTableAddForeignKeyConstraint` | Extends above | Uses `s.table().equivalent("TableToCreateForeignKeysChangeSet")` |
| `FieldToAddNotNullConstraint` | `@abstract`, `@greedy` | Base not-null |
| `FieldToCreateTableAddNotNullConstraint` | Extends above, `@greedy` | Uses `s.table().equivalent("TableToAddNotNullChangeSet")` |
| `IndexToCreateIndex` | - | Uses `targetModel.getOrCreateChangeSet()` |
| `AddUniqueConstraints` | - | Uses `targetModel.getOrCreateChangeSet()` |

## Proposed Architecture

### Rule Classes

```
judo-tatami-rdbms2liquibase/src/main/java/
  hu/blackbelt/judo/tatami/rdbms2liquibase/zeta/
    Rdbms2LiquibaseZetaTransformation.java  (orchestrator only)
    Rdbms2LiquibaseHelper.java              (existing - utilities)
    Rdbms2LiquibaseRuleNames.java           (existing - constants)
    rules/
      TableRules.java                       (NEW)
      FieldRules.java                       (NEW)
```

### TableRules.java

```java
@TransformationContext(source = RdbmsTable.class, target = CreateTable.class)
public class TableRules {

    @TransformRule(name = TABLE_TO_CREATE_TABLE)
    @Lazy  // Only executed when equivalent() is called
    @Transform(type = RdbmsTable.class)
    @To(type = CreateTable.class)
    public TransformFunction<RdbmsTable, CreateTable> tableToCreateTable() {
        return (s, ctx) -> {
            CreateTable t = ctx.createTarget(CreateTable.class);
            t.setTableName(s.getSqlName());
            t.setRemarks(s.getUuid());
            return t;
        };
    }

    @TransformRule(name = TABLE_TO_CREATE_TABLE_CHANGESET)
    @Transform(type = RdbmsTable.class)
    @To(type = ChangeSet.class)
    public TransformFunction<RdbmsTable, ChangeSet> tableToCreateTableChangeSet() {
        return (s, ctx) -> {
            ChangeSet t = ctx.createTarget(ChangeSet.class);
            t.setId("create-table-" + s.getSqlName());
            // ...
            // Key pattern: use ctx.equivalent() to trigger lazy TableToCreateTable
            CreateTable createTable = ctx.equivalent(s, TABLE_TO_CREATE_TABLE);
            t.getCreateTable().add(createTable);
            return t;
        };
    }
}
```

### FieldRules.java

```java
@TransformationContext(source = RdbmsField.class, target = Column.class)
public class FieldRules {

    @TransformRule(name = FIELD_TO_COLUMN)
    @Abstract  // Base rule, extended by concrete field type rules
    @Transform(type = RdbmsField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsField, Column> fieldToColumn() {
        return (s, ctx) -> {
            Column t = ctx.createTarget(Column.class);
            t.setName(s.getSqlName());
            t.setType(Rdbms2LiquibaseHelper.toFieldDefinition(s));
            t.setRemarks(s.getUuid());
            return t;
        };
    }

    @TransformRule(name = IDENTIFIER_FIELD_TO_COLUMN)
    @Extends({FIELD_TO_COLUMN})
    @Transform(type = RdbmsIdentifierField.class)
    @To(type = Column.class)
    public TransformFunction<RdbmsIdentifierField, Column> identifierFieldToColumn() {
        return (s, ctx) -> {
            Column t = ctx.executeParentRule(FIELD_TO_COLUMN, s);
            // Add to CreateTable using named equivalent
            CreateTable createTable = ctx.equivalent(getTable(s), TABLE_TO_CREATE_TABLE);
            if (createTable != null) {
                createTable.getColumn().add(t);
            }
            return t;
        };
    }
}
```

### Orchestrator Simplification

```java
public class Rdbms2LiquibaseZetaTransformation {

    public TransformationTrace execute() {
        // Create registry and register rule classes
        TransformationRegistry registry = new TransformationRegistry();
        registry.register(TableRules.class);
        registry.register(FieldRules.class);

        // Create context
        TransformationContext context = createContext(registry);

        // Execute transformation
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .build();

        TransformationResult result = executor.transform();

        // Post-processing: add root databaseChangeLog to resource
        postProcess(context);

        return result.getTrace();
    }
}
```

## Key Differences from Current Implementation

| Aspect | Current | Proposed |
|--------|---------|----------|
| Rule location | Inline in orchestrator | Separate rule classes |
| Trace management | Manual `addTrace()`/`getEquivalent()` | Framework-managed via `ctx.equivalent()` |
| Rule execution | Manual iteration | `TransformationExecutor` |
| Abstract rules | Exist but not used | Used with `@Extends` |
| Lazy rules | Not used | Used for `TableToCreateTable` |

## Implementation Approach

1. **Phase 1**: Create `Rdbms2LiquibaseHelper` utilities if needed (may already exist)
2. **Phase 2**: Create `TableRules.java` with table transformation rules
3. **Phase 3**: Create `FieldRules.java` with field transformation rules
4. **Phase 4**: Refactor orchestrator to use registry/executor pattern
5. **Phase 5**: Run tests and verify output equivalence

## Risk Mitigation

- The existing `Rdbms2LiquibaseHelper.java` already has `toFieldDefinition()` utility
- Rule names already defined in `Rdbms2LiquibaseRuleNames.java`
- Pattern proven in `Asm2RdbmsZetaTransformation` refactoring
