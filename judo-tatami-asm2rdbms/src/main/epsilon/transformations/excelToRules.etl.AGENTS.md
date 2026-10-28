# `excelToRules.etl`

Loads the relation-shape decision table: rule `Excel2Rule` transforms `RULEMAPPING!TableMappingRule` to `RDBMS!Rule` into a fresh `RDBMS!Rules` root.
Copies `symbol`, `opposite`, `lower`/`upper`, `container`, `containment`, `first`, `foreignKey`, `inverseForeignKey`, `rangeConstraint`, `inverseRangeConstraint`, `joinTable`; defaults missing `readOnly`/`cascade`/`mandatory` to false, and sets `oppositeLower`/`oppositeUpper` to sentinel `-2` when `opposite` is false.
Rows without `symbol` are skipped. `reference.eol`'s `ruleMapping()` resolves against `RDBMS!Rules.all.first`, so exactly one `Rules` root must exist.
Bug: `cascade` is read from `t.cascade` (target) not `s.cascade`, so the spreadsheet's cascade column never reaches the model.