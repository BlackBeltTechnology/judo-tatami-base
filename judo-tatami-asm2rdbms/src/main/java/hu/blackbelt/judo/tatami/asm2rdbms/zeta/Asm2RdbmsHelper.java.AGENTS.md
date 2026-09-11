# `Asm2RdbmsHelper.java`

Final, non-instantiable static facade the Zeta rule classes call for every naming, typing and identity decision, ported 1:1 from the EOL operation library under `epsilon/operations/`. Parity with that library is the invariant — a divergence here makes the ZETA and ETL engines emit different schemas for the same ASM model.

## Identity

- `getId(EObject)` — reads the `http://blackbelt.hu/judo/meta/ExtendedMetadata/id` annotation `value` detail; falls back to `ENamedElement.getName()`, then to `System.identityHashCode`. The identity-hash fallback is NOT stable across JVM runs.
- `getResourceId(EObject)` — the XMI ID via `Resource.getURIFragment`, the ETL `self.eResource.getId(self)` equivalent. A fragment starting with `/` is a positional path, not an ID, and is rejected in favour of `getId`.
- Both memoise into static `ConcurrentHashMap` caches (`ELEMENT_ID_CACHE`, `RESOURCE_ID_CACHE`).

## SQL naming

`toUpperSnakeCase`, `sqlLongName`, `sqlName`, `packageSqlName`, `classSqlName`, `tableSqlName`, `fieldSqlName`, `referenceIdentifierSqlName`, `referenceFkSqlName`, `referenceInverseIdentifierSqlName`, `referenceInvFkSqlName`, `referenceUniFkSqlName`, `referenceManyToManyTableSqlName`, `abbreviate(name, maxLength)` (delegates to `AbbreviateUtils` with `_` separator), `md5(input)` (Guava `Hashing`, UTF-8).

Length budgets and prefixes arrive as explicit parameters, not globals — the caller must pass the dialect-resolved `shortNameSize`/`nameSize`/`tableNameMaxSize`/`columnNameMaxSize` and the `T_`/`C_`/`FK_`/`FK_INV_`/`J_` prefixes, mirroring what `Asm2Rdbms` injects into the Epsilon context.

## Typing

- `fillType(RdbmsField, String javaType, EAttribute, Map<String, TypeMapping>, AsmUtils)` — looks the mapping up by Java type name. Unlike the EOL `fillType`, a MISSING mapping is silently ignored rather than thrown.
- A `rdbmsSize`/`rdbmsPrecision`/`rdbmsScale` value of form `#<annotation>:<key>` is resolved off the attribute's extension annotation; every failure path (null attribute — i.e. a system field such as `TYPE` or `CREATE_USERNAME` — missing annotation, malformed `#a:b`, unparseable number) defaults `size` to 255 for ETL compatibility.
- `copyTypeFromField(target, source)` — the `fromType` equivalent.

## Misc

`firstToLowerCase`, `firstToUpperCase`, `isMandatory(EReference)` (lower and upper bound both 1), `getRootPackage(EClassifier)` (walks `eSuperPackage`; a package cycle loops forever).

## Cache management

`clearCaches()` empties both static caches. `Asm2RdbmsZetaTransformation.execute()` calls it first — two transformations run in one JVM without it would reuse the previous model's IDs. Caches are `ConcurrentHashMap`, so the class is safe under parallel rule execution.
