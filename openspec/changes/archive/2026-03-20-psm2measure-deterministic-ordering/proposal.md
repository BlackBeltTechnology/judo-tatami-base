## Why

The psm2measure transformation produces elements in non-deterministic order — ETL and Zeta iterate PSM source elements differently, resulting in different orderings of Measures in `resource.getContents()`, Units within each Measure's `units` list, and BaseMeasureTerms within DerivedMeasure's `terms` list. This causes comparison failures even when the elements are structurally identical. Neither ETL nor Zeta currently sorts output elements.

## What Changes

- **ETL (`psmToMeasure.etl`)**: Add sorting logic in the existing `post {}` block to sort Measures by name, Units within each Measure by name, and BaseMeasureTerms within each DerivedMeasure by base measure name.
- **Zeta (`Psm2MeasureZetaTransformation.java`)**: Add a post-processing step (after rule execution, before XMI ID application) that sorts Measures, Units, and BaseMeasureTerms by name. Follows the pattern established by `Rdbms2LiquibaseZetaTransformation.sortChangeSetsByLogicalFilePath()`.

## Capabilities

### New Capabilities
- `psm2measure-ordering`: Deterministic alphabetical ordering of measure model elements after transformation

### Modified Capabilities
_(none)_

## Impact

- **ETL**: `psmToMeasure.etl` — add sorting in `post {}` block
- **Zeta**: `Psm2MeasureZetaTransformation.java` — add post-processing sort
- **No metamodel changes** — just reordering existing containment lists
