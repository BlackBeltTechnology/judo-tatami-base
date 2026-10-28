# `reference.eol`

`ASM!EReference` extensions deciding foreign-key, inverse-FK and junction-table naming plus relation-kind dispatch. Exports `toString()`, `isForeign()`, `isMandatory()` (lower and upper bound both 1), `referenceIdentifierSqlName()`, `referenceInverseIdentifierSqlName()`, `referenceFkSqlName()`, `referenceUniFkSqlName()`, `referenceInvFkSqlName()`, `referenceManyToManyTableSqlName()`, `isFirst()`, `ruleMapping()`, `getReferenceFQName()`.
FK column names reserve 3 chars for the `_ID` suffix against `columnNameMaxSize()`; junction-table names are built from BOTH ends when `eOpposite` exists, so both directions must agree — `isFirst()` breaks the tie by lexicographic name compare and returns true for a unidirectional reference.
`ruleMapping()` resolves the `RDBMS!Rule` via `RDBMS!Rules.all.first.getRuleFromReference(self)` and requires exactly one `Rules` root in the RDBMS model.
`isForeign()` calls `ownerReference()`, which this module does NOT define — it must come from the injected Java side.
All `@cached`.