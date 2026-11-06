# judo-tatami-asm2rdbms/src/main/java/hu/blackbelt/judo/tatami/asm2rdbms/zeta/rules

Zeta port of `asm2rdbms` ETL rule modules. Each class carries `@TransformationContext(source=..., target=...)` and exposes `TransformGuard` / `TransformFunction` factory methods discovered by `TransformationRegistry`. Rule names come from `Asm2RdbmsRuleNames`; shared logic from `Asm2RdbmsHelper`.

| File | Purpose |
|---|---|
| `AttributeRules.java` | Maps ASM `EAttribute` to RDBMS fields; port of `attribute.etl`. Exports guards `isAttributeInEntityType`/`isIdentifierAttributeInEntityType` and rules `eAttributeToRdbmsField`/`eAttributeToTableValueField`/`eAttributeToIndex` → see `AttributeRules.java.AGENTS.md` |
| `ClassRules.java` | Maps entity `EClass` to `RdbmsTable` plus its tracking columns; port of `class.etl`. Exports guard `isEntityType` and rules `eClassToRdbmsTable` + ten `eClassToTable*Field` field rules → see `ClassRules.java.AGENTS.md` |
| `PackageRules.java` | Maps root ASM `EPackage` to the RDBMS model root; port of `package.etl`. Exports guard `isRootPackage` and rules `rootPackegeToModel`/`rootPackegeToConfiguration` (keep `rootPackege` misspelling) → see `PackageRules.java.AGENTS.md` |
| `ReferenceRules.java` | Maps `EReference` to foreign keys and junction tables; port of `reference.etl`. Exports guards `isValidReference`/`needsForeignKey`/`needsInverseForeignKey`/`needsJunctionTable*` and rules `eReferenceToRdbmsTableForeignKey`/`eReferenceToRdbmsTableInverseForeignKey`/`eReferenceToRdbmsJunctionTable*` → see `ReferenceRules.java.AGENTS.md` |
