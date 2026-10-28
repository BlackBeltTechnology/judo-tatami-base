## Context

The psm2measure transformation creates Measure elements (BaseMeasure, DerivedMeasure) containing Unit elements and BaseMeasureTerm elements. Neither ETL nor Zeta sorts output — elements appear in processing order, which differs between the two engines. The `rdbms2liquibase` module already has a post-transformation sort pattern (`sortChangeSetsByLogicalFilePath()`), and the `psm2asm` ETL has a `post {}` block for enrichment.

## Goals / Non-Goals

**Goals:**
- ETL and Zeta produce identically-ordered measure models
- Sort key is element name (alphabetical), consistent and deterministic

**Non-Goals:**
- Changing transformation rule logic or element creation
- Adding sort to other transformations beyond psm2measure

## Decisions

### Decision 1: Post-transformation sort (not insertion-time)

Sort after all rules complete, not during rule execution. The measure model is small and this approach is simpler. Matches the `rdbms2liquibase` pattern.

### Decision 2: Sort by name at three levels

1. `resource.getContents()` — sort Measure elements by `getName()`
2. `measure.getUnits()` — sort Unit elements within each Measure by `getName()`
3. `derivedMeasure.getTerms()` — sort BaseMeasureTerm elements by their referenced base measure's name

### Decision 3: ETL uses `post {}` block, Zeta uses post-process method

**ETL:** Add sorting in the existing `post {}` block in `psmToMeasure.etl` using Epsilon's collection sorting.

**Zeta:** Add a `sortMeasureModel()` method called in `postProcess()` of `Psm2MeasureZetaTransformation.java`, before XMI ID application. This follows the existing code structure where `postProcess()` already handles post-transformation steps.

## Risks / Trade-offs

- **[Risk] EMF list mutation during sort** — EMF ELists don't have a direct `sort()` method. Use clear-and-addAll pattern: copy to ArrayList, sort, clear EList, addAll back. This is safe for containment lists.
