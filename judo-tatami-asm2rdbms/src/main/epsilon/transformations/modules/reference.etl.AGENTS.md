# `reference.etl`

Maps `ASM!EReference` onto foreign keys and junction tables, dispatching on the spreadsheet-driven `s.ruleMapping()` `RDBMS!Rule` flags. `EReferenceToRdbmsTableForeignKey` (flag `foreignKey`) adds an `RdbmsForeignKey` to the SOURCE class's table pointing at the target table's primary key, names it `referenceIdentifierSqlName()`/`referenceFkSqlName()`, and sets `readOnly` + `deleteOnCascade` when the reference is a `container`.

`EReferenceToRdbmsTableInverseForeignKey` (flag `inverseForeignKey`) adds the FK to the TARGET class's table instead, named `referenceInverseIdentifierSqlName()`/`referenceInvFkSqlName()`.

`@lazy EReferenceToRdbmsJunctionTable` materialises the many-to-many table only when another rule resolves it, with `EReferenceToRdbmsJunctionTablePrimaryKey` (flags `joinTable` and `first`) creating its key and the bidirectional/unidirectional FK rules split on `s.eOpposite` presence. `first` makes a bidirectional pair produce ONE junction table, not two.

All guards require both ends `isEntityType()` and skip derived references; `mandatory` is forced false everywhere (`isMandatory()` is commented out).