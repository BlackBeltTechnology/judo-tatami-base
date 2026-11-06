# `FieldRules.java`

Zeta transformation rules mapping `RdbmsField` instances into Liquibase `Column` elements, primary keys, foreign keys, not-null constraints, and indices. Context bounds `source = RdbmsField.class, target = Column.class`.

## Exported rules

- `fieldToColumn()`
- `identifierFieldToColumn()`
- `identifierFieldToPkConstraint()`
- `valueFieldToColumn()`
- `foreignKeyFieldToCreateTableAddFkConstraint()`
- `fieldToCreateTableAddNotNullConstraint()`
- `indexToCreateIndex()`
- `addUniqueConstraints()`
- `isMandatoryField()`