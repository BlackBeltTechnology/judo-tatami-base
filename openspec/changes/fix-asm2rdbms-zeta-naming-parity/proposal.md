# Fix Asm2Rdbms ZETA Naming Parity

**Status:** draft

## Summary

Update the Asm2RdbmsZetaTransformation to generate SQL names and UUIDs that match the ETL transformation output exactly. This ensures structural equivalence between ETL and ZETA transformations.

## Why

The Asm2RdbmsExternalModelTest currently fails with 2,344 differences because the ZETA transformation generates different:

1. **SQL Table Names**: ZETA uses full FQN with dots replaced by underscores (`t_rackinspect_entities_user`) while ETL uses abbreviated package + class name (`T_ENTITIES_USER`)
2. **SQL Column Names**: ZETA uses simple name + prefix (`C_PRIMARYUSERADDRESS_ID`) while ETL uses `sqlLongName()` with proper abbreviation (`C_PRIMARY_USER_ADDRESS_ID`)
3. **Foreign Key Constraint Names**: ZETA uses MD5 hash of ASM ID (`FK_1abe7e5f...`) while ETL uses abbreviated class+reference name (`FK_ENTITIES_USER_PRIMARY_USER_ADDRESS`)
4. **UUID Generation**: ZETA uses ASM element name (`(asm/User)/Table`) while ETL uses XMI resource ID (`(asm/(psm/(esm/_kKzs0AuCEe6iTZ0mjWdZMg)/EntityType)/Table`)
5. **Field Size Defaults**: ZETA doesn't set size from type mapping annotations properly (size=0 vs size=255)

## What Changes

### 1. Asm2RdbmsHelper.java

Update naming methods to match ETL algorithms:

- `tableSqlName()`: Use `classSqlName()` pattern (package prefix + class name)
- `fieldSqlName()`: Use `sqlLongName()` with proper CamelCase to UPPER_SNAKE_CASE conversion
- `referenceIdentifierSqlName()`: Use `sqlLongName()` with proper abbreviation
- `referenceFkSqlName()`: Use class+reference name abbreviation instead of MD5 hash
- `referenceInvFkSqlName()`: Use class+reference name abbreviation
- `referenceManyToManyTableSqlName()`: Use bidirectional naming pattern for bidirectional refs

### 2. ClassRules.java

Update UUID generation to use XMI resource ID:
- Use `s.eResource().getID(s)` instead of `getId(s)` for UUID path

### 3. AttributeRules.java

- Fix field size handling to properly read from type mapping annotations
- Update UUID generation pattern

### 4. ReferenceRules.java

- Update all UUID generation patterns
- Update FK naming to use abbreviation instead of MD5

### 5. New Helper Methods

Add methods to `Asm2RdbmsHelper`:
- `classSqlName(EClass, boolean createSimpleName)`: Generate package_class SQL name (or just class name when createSimpleName=true)
- `packageSqlName(EPackage)`: Generate abbreviated package SQL name
- `sqlLongName(ENamedElement, AsmUtils)`: Convert to UPPER_SNAKE_CASE, respecting `shortName` annotation
- `sqlName(ENamedElement, int maxSize, AsmUtils)`: Get abbreviated SQL name with `shortName` annotation support
- `toUpperSnakeCase(String)`: CamelCase to UPPER_SNAKE_CASE conversion
- `getResourceId(EObject)`: Get XMI resource ID for UUID generation

### 6. Configuration Support

- Support `createSimpleName` configuration flag (when true, table names omit package prefix)
- Honor `shortName` annotation on elements (overrides default naming)

## Approach

### Phase 1: Add Helper Methods
Add new string transformation methods that match the ETL operations.

### Phase 2: Update SQL Name Generation
Update all `*SqlName()` methods in `Asm2RdbmsHelper` to use the new helper methods.

### Phase 3: Update UUID Generation
Change UUID generation to use XMI resource IDs instead of element names.

### Phase 4: Fix Field Size Defaults
Ensure type mapping annotation values are properly applied.

### Phase 5: Verify Parity
Run Asm2RdbmsExternalModelTest with structural comparison to verify 0 differences.

## Impact

### Modules Affected
- `judo-tatami-asm2rdbms` - Update ZETA transformation helper and rules

### Breaking Changes
- None for consumers (RDBMS model structure is the same)
- ZETA-generated SQL names will change (this is the desired outcome)

### Dependencies
- None

## Risks

### Risk 1: Different Database Schemas
ZETA migrations may produce different DDL than ETL.
**Mitigation**: This change makes them identical.

### Risk 2: Performance Impact
Additional string operations for naming.
**Mitigation**: Cache computed values, impact is minimal (~1% based on profiling).

## Success Criteria

1. Asm2RdbmsExternalModelTest passes with `-Djudo.test.comparison.structural=true`
2. All SQL names match between ETL and ZETA output
3. All UUIDs match between ETL and ZETA output
4. No regressions in other transformation tests

## Analysis Details

### SQL Name Comparison

| Element | ETL Pattern | ZETA Current | ZETA Fixed |
|---------|-------------|--------------|------------|
| Table | `T_<PKG>_<CLASS>` | `t_<fqn>` | `T_<PKG>_<CLASS>` |
| Column | `C_<NAME>` | `c_<name>` | `C_<NAME>` |
| FK Column | `C_<NAME>_ID` | `c_<name>_id` | `C_<NAME>_ID` |
| FK Constraint | `FK_<CLASS>_<REF>` | `FK_<md5>` | `FK_<CLASS>_<REF>` |
| Junction | `J_<CLASS1>_<REF1>_<CLASS2>_<REF2>` | `j_<class>_<ref>` | `J_<CLASS1>_<REF1>_<CLASS2>_<REF2>` |
| Index | `IDX_<md5(uuid)>` | `IDX_<md5(uuid)>` | Same (once UUID is fixed, MD5 will match) |

### UUID Comparison

| Element | ETL Pattern | ZETA Current | ZETA Fixed |
|---------|-------------|--------------|------------|
| Table | `(asm/<xmiId>)/Table` | `(asm/<name>)/Table` | `(asm/<xmiId>)/Table` |
| Field | `(asm/<xmiId>)/RdbmsField` | `(asm/<name>)/RdbmsField` | `(asm/<xmiId>)/RdbmsField` |
| FK | `(asm/<xmiId>)/TableForeignKey` | `(asm/<name>)/TableForeignKey` | `(asm/<xmiId>)/TableForeignKey` |

### Root Cause: getId() Implementation

ETL uses `self.eResource.getId(self)` which returns the XMI ID from the resource.
ZETA uses `Asm2RdbmsHelper.getId()` which checks for an annotation and falls back to element name.

The fix requires using the EMF resource's ID system consistently.
