## ADDED Requirements

### Requirement: Iterator variable names are deterministic per expression
The expression builder SHALL reset the iterator counter before building each independent expression, so that iterator variable names start at `_iterator_1` for every expression regardless of extraction order.

#### Scenario: Same expression produces same iterator names across runs
- **WHEN** the same JQL expression is extracted from two ASM models with different element ordering
- **THEN** both produce identical iterator variable names (e.g., `_iterator_1`, `_iterator_2`)

#### Scenario: Different extraction order does not affect iterator names
- **WHEN** ETL psm2asm produces ASM with entity A before entity B
- **AND** Zeta psm2asm produces ASM with entity B before entity A
- **THEN** expressions for entity A have the same iterator names in both cases

#### Scenario: No iterator name collisions within an expression
- **WHEN** an expression requires multiple iterators (e.g., nested lambda)
- **THEN** each iterator within the expression gets a unique sequential name (`_iterator_1`, `_iterator_2`, ...)
