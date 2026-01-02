# Proposal: Refactor Rdbms2Liquibase Zeta to Use ETL-Based Rules

## Summary

Refactor `Rdbms2LiquibaseZetaTransformation` to use declarative rule classes with Zeta annotations, matching the ETL structure exactly. Currently the implementation uses inline methods and manual tracing instead of proper Zeta patterns.

## Why

The current `Rdbms2LiquibaseZetaTransformation` implementation:
1. Uses inline procedural methods (`transformTable()`, `transformFields()`, etc.) instead of separate rule methods in rule classes
2. Manually manages trace with `addTrace()`/`getEquivalent()` instead of using `ctx.equivalent()`
3. Has duplicated rule methods inside the same class that are not actually used by the executor
4. Does not use `TransformationRegistry` and `TransformationExecutor` for rule execution
5. Named equivalents don't match ETL patterns exactly

### ETL Rules to Match

The ETL uses these rule files:
- `table.etl`: `TableToCreateTable` (@lazy), `TableToCreateTableChangeSet`, `TableToCreateForeignKeysChangeSet`, `TableToAddNotNullChangeSet`
- `field.etl`: `FieldToColumn` (@abstract), `IdentifierFieldToCreateTableColumn` (extends), `ValueFieldToCreateTableColumn` (extends), `ForeignKeyFieldToCreateTableAddForeignKeyConstraint`, `FieldToCreateTableAddNotNullConstraint`

### Key ETL Patterns to Replicate

1. **`@lazy` annotation on `TableToCreateTable`**: The table is created lazily when needed
   ```etl
   @lazy
   @greedy
   rule TableToCreateTable
       transform s : RDBMS!RdbmsTable
       to t : LIQUIBASE!CreateTable
   ```

2. **Named `equivalent()` calls**: ETL uses `s.equivalent("RuleName")` for lookups
   ```etl
   t.createTable.add(s.equivalent("TableToCreateTable"));
   s.table().equivalent("TableToCreateTable").column.add(t);
   s.table().equivalent("TableToCreateForeignKeysChangeSet").addForeignKeyConstraint.add(t);
   ```

3. **`@abstract` annotation with `extends`**: Base rule extended by concrete rules
   ```etl
   @abstract
   rule FieldToColumn
       transform s : RDBMS!RdbmsField
       to t : LIQUIBASE!Column

   rule IdentifierFieldToCreateTableColumn
       transform s : RDBMS!RdbmsIdentifierField
       to t : LIQUIBASE!Column
       extends FieldToColumn
   ```

## What Changes

1. Create rule classes in `rules/` subdirectory:
   - `TableRules.java` - table transformation rules
   - `FieldRules.java` - field transformation rules (columns, FK constraints, not-null)

2. Use proper Zeta annotations matching ETL:
   - `@TransformRule(name = "...")` for each rule
   - `@Lazy` for `TableToCreateTable`
   - `@Abstract` for `FieldToColumn`
   - `@Extends` for rule inheritance
   - `@Guard(method = "...")` for guard conditions

3. Use `ctx.equivalent(source, "ruleName")` for named lookups matching ETL:
   - `ctx.equivalent(table, TABLE_TO_CREATE_TABLE)` instead of manual `getEquivalent()`
   - `ctx.equivalent(field.eContainer(), TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET)`

4. Refactor orchestrator to use `TransformationRegistry` and `TransformationExecutor`

## Scope

- **In Scope**:
  - Refactor `Rdbms2LiquibaseZetaTransformation` to use rule classes
  - Match ETL rule structure exactly
  - Use proper Zeta annotations
  - Use named equivalent lookups

- **Out of Scope**:
  - Changing transformation behavior
  - Refactoring incremental transformations (separate proposal)
  - Adding new transformation rules
  - Modifying ETL implementation

## Success Criteria

1. Zeta produces identical Liquibase model output as ETL
2. Rule names match ETL exactly
3. Named equivalent lookups match ETL patterns
4. All annotations (@Lazy, @Abstract, @Extends) are used correctly
5. Existing tests continue to pass

## Related Specs

- `zeta-transformations` - Zeta transformation requirements
- `asm2rdbms-zeta-rules` - Similar refactoring pattern (reference)
