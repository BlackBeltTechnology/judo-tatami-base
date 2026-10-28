# Capability: ASM to RDBMS Zeta Rule-Based Transformation

## ADDED Requirements

### Requirement: Asm2Rdbms Zeta MUST use declarative rule classes matching ETL structure

The ASM to RDBMS Zeta transformation MUST use declarative rule classes with Zeta annotations, organized to match the ETL file structure (package.etl, class.etl, attribute.etl, reference.etl).

#### Scenario: PackageRules transforms root package to RdbmsModel and RdbmsConfiguration

Given an ASM model with a root EPackage
When the Zeta transformation executes PackageRules
Then the `rootPackegeToModel` rule creates an RdbmsModel
And the `rootPackegeToConfiguration` rule creates an RdbmsConfiguration
And the configuration is linked using `ctx.equivalent(s, "rootPackegeToModel")`

#### Scenario: ClassRules transforms EClass to RdbmsTable with system fields

Given an ASM model with entity EClasses
When the Zeta transformation executes ClassRules
Then the `EClassToRdbmsTable` rule (marked @Primary) creates RdbmsTable for each entity
And system field rules create ID, type, version, and audit fields
And each field rule uses `ctx.equivalent(s, "EClassToRdbmsTable")` to access the table

#### Scenario: AttributeRules uses abstract rule inheritance

Given an ASM model with entity attributes
When the Zeta transformation executes AttributeRules
Then the `EAttributeToRdbmsField` rule (marked @Abstract) provides base field setup
And the `EAttributeToTableValueField` rule extends it using `ctx.executeParentRule()`
And the field is added using `ctx.equivalent(s.getEContainingClass(), "EClassToRdbmsTable")`

#### Scenario: ReferenceRules uses lazy junction table creation

Given an ASM model with entity references requiring junction tables
When the Zeta transformation executes ReferenceRules
Then the `EReferenceToRdbmsJunctionTable` rule (marked @Lazy) creates tables on demand
And the `EReferenceToRdbmsJunctionTablePrimaryKey` rule triggers lazy creation using `ctx.equivalent(s, "EReferenceToRdbmsJunctionTable")`
And junction FK rules handle bidirectional and unidirectional cases

### Requirement: Zeta rule annotations MUST match ETL annotations

Each ETL annotation MUST have a corresponding Zeta annotation used consistently.

#### Scenario: Primary annotation marks main entity transformation rule

Given the ETL rule `@primary rule EClassToRdbmsTable`
When implementing in Zeta
Then the rule method has `@Primary` annotation
And this rule is used as the primary equivalent lookup for EClass

#### Scenario: Abstract annotation marks base attribute rule

Given the ETL rule `@abstract rule EAttributeToRdbmsField`
When implementing in Zeta
Then the rule method has `@Abstract` annotation
And concrete rules use `@Extends` and `ctx.executeParentRule()`

#### Scenario: Lazy annotation marks junction table rule

Given the ETL rule `@lazy rule EReferenceToRdbmsJunctionTable`
When implementing in Zeta
Then the rule method has `@Lazy` annotation
And the rule is only executed when another rule calls `ctx.equivalent()` for it

### Requirement: Named equivalent lookups MUST match ETL patterns

Zeta MUST use named equivalent lookups matching ETL `s.equivalent("RuleName")` patterns.

#### Scenario: Field rules use named lookup to get parent table

Given the ETL pattern `var table = s.equivalent("EClassToRdbmsTable")`
When implementing in Zeta
Then the code uses `RdbmsTable table = ctx.equivalent(s, ECLASS_TO_RDBMS_TABLE)`
And the constant `ECLASS_TO_RDBMS_TABLE` matches the ETL rule name exactly

#### Scenario: Index rule uses named lookup to get value field

Given the ETL pattern `u.fields.add(s.equivalent("EAttributeToTableValueField"))`
When implementing in Zeta
Then the code uses `ctx.equivalent(s, EATTRIBUTE_TO_TABLE_VALUE_FIELD)`
And the field is retrieved from the same source element

#### Scenario: Junction FK rules use named lookup for junction table

Given the ETL pattern `s.equivalent("EReferenceToRdbmsJunctionTable")`
When implementing in Zeta
Then the code uses `ctx.equivalent(s, EREFERENCE_TO_RDBMS_JUNCTION_TABLE)`
And this triggers lazy execution of the junction table rule if not yet executed

### Requirement: Asm2Rdbms Zeta MUST produce identical output to ETL

The transformation output MUST remain identical after refactoring.

#### Scenario: Refactored Zeta produces same RDBMS model as before

Given the existing Asm2RdbmsZetaTransformation implementation
When refactored to use rule classes
Then the generated RDBMS model XMI is byte-identical to the original
And all XMI IDs match exactly
And the transformation trace contains the same mappings
