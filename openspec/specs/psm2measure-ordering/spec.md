# psm2measure-ordering Specification

## Purpose
Ensures that psm2measure transformation produces deterministic ordering of Measures, Units, and BaseMeasureTerms so that ETL and Zeta outputs are identical.

## Requirements

### Requirement: Measures sorted alphabetically by name
After psm2measure transformation completes, the target resource's contents list SHALL be sorted alphabetically by Measure name.

#### Scenario: Measures appear in alphabetical order
- **WHEN** PSM model contains measures named "Time", "Mass", "Length"
- **THEN** the target resource contains measures in order: "Length", "Mass", "Time"

#### Scenario: ETL and Zeta produce same Measure order
- **WHEN** the same PSM model is transformed by both ETL and Zeta
- **THEN** both produce Measure elements in identical order

### Requirement: Units sorted alphabetically by name within each Measure
After psm2measure transformation completes, each Measure's units list SHALL be sorted alphabetically by Unit name.

#### Scenario: Units appear in alphabetical order within a Measure
- **WHEN** PSM measure "Mass" has units "kilogram", "gram", "milligram"
- **THEN** the transformed Measure "Mass" contains units in order: "gram", "kilogram", "milligram"

#### Scenario: ETL and Zeta produce same Unit order
- **WHEN** the same PSM model is transformed by both ETL and Zeta
- **THEN** both produce Unit elements in identical order within each Measure

### Requirement: BaseMeasureTerms sorted by referenced measure name
After psm2measure transformation completes, each DerivedMeasure's terms list SHALL be sorted alphabetically by the referenced base measure's name.

#### Scenario: BaseMeasureTerms appear in alphabetical order
- **WHEN** a DerivedMeasure has terms referencing "Time" and "Length"
- **THEN** the terms list is ordered: term referencing "Length", term referencing "Time"

#### Scenario: ETL and Zeta produce same BaseMeasureTerm order
- **WHEN** the same PSM model is transformed by both ETL and Zeta
- **THEN** both produce BaseMeasureTerm elements in identical order within each DerivedMeasure
