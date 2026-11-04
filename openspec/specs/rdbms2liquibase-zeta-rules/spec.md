# rdbms2liquibase-zeta-rules Specification

## Purpose
TBD - created by archiving change refactor-rdbms2liquibase-zeta-rules. Update Purpose after archive.
## Requirements
### Requirement: Rdbms2Liquibase Zeta MUST use declarative rule classes matching ETL structure

The RDBMS to Liquibase Zeta transformation MUST use declarative rule classes with Zeta annotations, organized to match the ETL file structure (table.etl, field.etl).

#### Scenario: TableRules transforms RdbmsTable to CreateTable and ChangeSets

Given an RDBMS model with RdbmsTable elements
When the Zeta transformation executes TableRules
Then the `TableToCreateTable` rule (marked @Lazy) creates CreateTable elements on demand
And the `TableToCreateTableChangeSet` rule uses `ctx.equivalent(s, "TableToCreateTable")` to get the CreateTable
And the `TableToCreateForeignKeysChangeSet` rule has a guard for tables with FK fields
And the `TableToAddNotNullChangeSet` rule has a guard for tables with mandatory fields

#### Scenario: FieldRules uses abstract rule inheritance for columns

Given an RDBMS model with various field types
When the Zeta transformation executes FieldRules
Then the `FieldToColumn` rule (marked @Abstract) provides base column setup
And the `IdentifierFieldToCreateTableColumn` rule extends it using `ctx.executeParentRule()`
And the `ValueFieldToCreateTableColumn` rule extends it using `ctx.executeParentRule()`
And columns are added using `ctx.equivalent(table, "TableToCreateTable")`

#### Scenario: FieldRules uses abstract rule inheritance for FK constraints

Given an RDBMS model with RdbmsForeignKey elements
When the Zeta transformation executes FieldRules
Then the `ForeignKeyFieldToAddForeignKeyConstraint` rule (marked @Abstract) provides base FK constraint
And the `ForeignKeyFieldToCreateTableAddForeignKeyConstraint` rule extends it
And FK constraints are added using `ctx.equivalent(table, "TableToCreateForeignKeysChangeSet")`

#### Scenario: FieldRules uses abstract rule inheritance for not-null constraints

Given an RDBMS model with mandatory fields
When the Zeta transformation executes FieldRules
Then the `FieldToAddNotNullConstraint` rule (marked @Abstract) provides base not-null constraint
And the `FieldToCreateTableAddNotNullConstraint` rule extends it with guard for mandatory
And not-null constraints are added using `ctx.equivalent(table, "TableToAddNotNullChangeSet")`

### Requirement: Zeta rule annotations MUST match ETL annotations

Each ETL annotation MUST have a corresponding Zeta annotation used consistently.

#### Scenario: Lazy annotation marks CreateTable rule for on-demand execution

Given the ETL rule `@lazy rule TableToCreateTable`
When implementing in Zeta
Then the rule method has `@Lazy` annotation
And the rule is only executed when another rule calls `ctx.equivalent()` for it

#### Scenario: Greedy annotation enables subtype matching

Given the ETL rule `@greedy rule FieldToColumn`
When implementing in Zeta
Then the rule method has `@Greedy` annotation
And the rule matches RdbmsField and all its subtypes (RdbmsValueField, RdbmsIdentifierField, RdbmsForeignKey)

#### Scenario: Abstract annotation marks base column rule

Given the ETL rule `@abstract rule FieldToColumn`
When implementing in Zeta
Then the rule method has `@Abstract` annotation
And concrete field type rules use `@Extends` and `ctx.executeParentRule()`

#### Scenario: Extends annotation enables rule inheritance

Given the ETL pattern `rule IdentifierFieldToCreateTableColumn extends FieldToColumn`
When implementing in Zeta
Then the rule method has `@Extends({FIELD_TO_COLUMN})`
And the rule calls `ctx.executeParentRule(FIELD_TO_COLUMN, s)` to inherit behavior

### Requirement: Named equivalent lookups MUST match ETL patterns

Zeta MUST use named equivalent lookups matching ETL `s.equivalent("RuleName")` patterns.

#### Scenario: ChangeSet rule uses named lookup to get CreateTable

Given the ETL pattern `t.createTable.add(s.equivalent("TableToCreateTable"))`
When implementing in Zeta
Then the code uses `CreateTable ct = ctx.equivalent(s, TABLE_TO_CREATE_TABLE)`
And the constant `TABLE_TO_CREATE_TABLE` matches the ETL rule name exactly

#### Scenario: Column rule uses named lookup to get CreateTable from table

Given the ETL pattern `s.table().equivalent("TableToCreateTable").column.add(t)`
When implementing in Zeta
Then the code uses `ctx.equivalent(getTable(field), TABLE_TO_CREATE_TABLE)`
And columns are added to the retrieved CreateTable element

#### Scenario: FK constraint rule uses named lookup for FK ChangeSet

Given the ETL pattern `s.table().equivalent("TableToCreateForeignKeysChangeSet").addForeignKeyConstraint.add(t)`
When implementing in Zeta
Then the code uses `ctx.equivalent(getTable(field), TABLE_TO_CREATE_FOREIGN_KEYS_CHANGESET)`
And FK constraints are added to the retrieved ChangeSet

### Requirement: Rdbms2Liquibase Zeta MUST produce identical output to ETL

The transformation output MUST remain identical after refactoring.

#### Scenario: Refactored Zeta produces same Liquibase model as before

Given the existing Rdbms2LiquibaseZetaTransformation implementation
When refactored to use rule classes
Then the generated Liquibase databaseChangeLog is structurally identical
And all ChangeSet IDs match exactly
And all Column, CreateTable, and constraint elements match

