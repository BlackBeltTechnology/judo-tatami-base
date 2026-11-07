## MODIFIED Requirements

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

### Requirement: Asm2Rdbms Zeta MUST produce identical output to ETL

The transformation output MUST remain identical after this fix.

#### Scenario: Refactored Zeta produces same RDBMS model as before

Given the existing Asm2RdbmsZetaTransformation implementation
When the XMI ID fix is applied
Then the generated RDBMS model structure is identical to the ETL output
And all XMI IDs match exactly (assertXmiIdsEquivalent passes)
And the transformation trace contains the same mappings

## ADDED Requirements

### Requirement: Every asm2rdbms Zeta rule MUST register its XMI ID via createTarget

Every rule in `ClassRules`, `AttributeRules`, and `ReferenceRules` that creates a target element with an explicit XMI ID suffix MUST use `ctx.createTarget(Type.class, "(asm/" + ctx.getElementId(s) + ")/<Suffix>")` so the ID is registered in `TransformationContext.pendingXmiIds`.

#### Scenario: ClassRules rules register correct XMI IDs

- **WHEN** `eClassToRdbmsTable()` executes
- **THEN** the RdbmsTable's XMI ID in XMLResource is `"(asm/<asmId>)/Table"`

- **WHEN** `eClassToTableIdField()` executes
- **THEN** the RdbmsIdentifierField's XMI ID is `"(asm/<asmId>)/TableIdField"`

- **WHEN** `eClassToTableTypeField()` executes
- **THEN** the RdbmsValueField's XMI ID is `"(asm/<asmId>)/TableTypeField"`

- **WHEN** audit field rules execute (version, createUsername, createUserId, createTimestamp, updateUsername, updateUserId, updateTimestamp)
- **THEN** each field's XMI ID matches the corresponding ETL suffix

#### Scenario: AttributeRules rules register correct XMI IDs

- **WHEN** `eAttributeToRdbmsField()` (abstract) executes
- **THEN** the RdbmsField's XMI ID is `"(asm/<asmId>)/RdbmsField"`

- **WHEN** `eAttributeToTableValueField()` (concrete, extends) executes
- **THEN** the RdbmsValueField's XMI ID is `"(asm/<asmId>)/TableValueField"` (overrides parent)

- **WHEN** `eAttributeToIndex()` executes
- **THEN** the RdbmsIndex's XMI ID is `"(asm/<asmId>)/Index"`

#### Scenario: ReferenceRules rules register correct XMI IDs

- **WHEN** `eReferenceToTableForeignKey()` executes
- **THEN** the FK's XMI ID is `"(asm/<asmId>)/TableForeignKey"`

- **WHEN** junction table rules execute
- **THEN** junction table, primary key, and FK XMI IDs match ETL suffixes exactly

### Requirement: Dead code MUST be removed from ClassRules

The placeholder comment `// Set XMI ID` followed by an empty line in `eClassToRdbmsTable()` MUST be removed.

#### Scenario: Dead comment removed

- **WHEN** `eClassToRdbmsTable()` is read after this fix
- **THEN** there is no `// Set XMI ID` placeholder comment in the method body
