# `rule.eol`

Gives `RDBMS!Rule` a `toString()` dumping the full relation-shape decision record: `symbol`, `lower`/`upper`, `opposite`, `oppositeLower`/`oppositeUpper`, `containment`, `container`, `first`, `foreignKey`, `inverseForeignKey`, `readOnly`, `cascade`, `mandatory`, `rangeConstraint`, `inverseRangeConstraint`, `joinTable`.
Diagnostic only — `reference.eol`'s `ruleMapping()` logs through it, so dropping a field here silently shrinks the transformation log.