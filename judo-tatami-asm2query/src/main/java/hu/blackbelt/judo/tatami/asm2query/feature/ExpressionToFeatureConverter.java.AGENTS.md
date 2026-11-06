# `ExpressionToFeatureConverter.java`

Abstract base for all converters.
Exports `convert(E, Context, FeatureTargetMapping)`, `applyMeasure`, `applyMeasureByUnit` (wraps feature in `MULTIPLE_DECIMAL` `Function` with unit-rate constant), `getConstraints` (precision/scale/maxLength `FunctionConstraint`s from extension annotations), `getSourceByVariableName`, `getBaseVariableName`, `getCollectionIterator` (creates `_iterator` on collection).
`getSourceByVariableName` throws `IllegalStateException` when the variable is absent from `context`.

## getCollectionIterator (persisted-query-model)

`getCollectionIterator(CollectionExpression)` creates a missing iterator variable with `createIterator`, which scans the
whole expression resource and **attaches** the iterator to it. Two changes: creation is `synchronized` on the expression
resource (the build runs from parallel streams; concurrent creators iterated a resource another one was modifying), and the
created expression is reported to `FeatureFactory.iteratorCreated(...)` so a build with `preserveExpressionModel` removes
the iterators again (`Asm2QueryWork` must not modify the `ExpressionModel`). See change: persisted-query-model.

