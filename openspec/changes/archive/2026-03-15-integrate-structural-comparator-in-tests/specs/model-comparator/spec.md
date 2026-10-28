# model-comparator Spec Delta

## ADDED Requirements

### Requirement: AbstractExternalModelTest Structural Comparison Support

The AbstractExternalModelTest MUST provide methods for structural model comparison using StructuralModelComparator.

#### Scenario: compareModelsStructural() compares two resources

Given an ETL transformation result Resource
And a ZETA transformation result Resource
When compareModelsStructural(expected, actual) is called
Then it returns a ComparisonResult
And the result indicates whether models are structurally equivalent

#### Scenario: Structural comparison respects system property

Given system property judo.test.comparison.structural=true
When isStructuralComparisonEnabled() is called
Then it returns true

#### Scenario: Structural comparison disabled by default

Given no system property set for structural comparison
When isStructuralComparisonEnabled() is called
Then it returns false

---

### Requirement: AbstractDualTransformationTest Structural Comparison Integration

The AbstractDualTransformationTest MUST support structural comparison when configured.

#### Scenario: compareModels() uses structural comparison when enabled

Given system property judo.test.comparison.structural=true
And ETL and ZETA transformation results
When compareModels() is called in testDualEquivalence()
Then structural comparison is used
And LLM-friendly output is produced on failure

#### Scenario: compareModels() uses ModelComparator when structural disabled

Given system property judo.test.comparison.structural not set
And ETL and ZETA transformation results
When compareModels() is called
Then the existing ModelComparator is used

---

### Requirement: LLM-Friendly Error Output

When structural comparison detects differences, the test MUST output LLM-friendly formatted differences.

#### Scenario: formatForLLM() output on comparison failure

Given two models with structural differences
When compareModelsStructural() detects differences
Then formatForLLM() output is logged
And the output contains XML-structured difference information

---

### Requirement: JSON Export for Debugging

The abstract test classes MUST support optional JSON export of model structures for debugging.

#### Scenario: Export model structure to JSON

Given system property judo.test.structural.exportJson=true
And a model Resource
When exportModelStructure(resource, filename) is called
Then a JSON file is written with the model structure

#### Scenario: JSON export respects output directory

Given system property judo.test.structural.outputDir=target/comparison
When exportModelStructure() writes a file
Then the file is created in target/comparison directory

---

### Requirement: All External Model Tests Support Structural Comparison

All external model test classes that extend AbstractExternalModelTest MUST support structural comparison when enabled.

#### Scenario: Psm2AsmExternalModelTest uses structural comparison

Given system property judo.test.comparison.structural=true
When Psm2AsmExternalModelTest runs model comparison
Then it uses StructuralModelComparator
And produces LLM-friendly output on failure

#### Scenario: Psm2MeasureExternalModelTest uses structural comparison

Given system property judo.test.comparison.structural=true
When Psm2MeasureExternalModelTest runs model comparison
Then it uses StructuralModelComparator
And produces LLM-friendly output on failure

#### Scenario: Asm2RdbmsExternalModelTest uses structural comparison

Given system property judo.test.comparison.structural=true
When Asm2RdbmsExternalModelTest runs model comparison
Then it uses StructuralModelComparator
And produces LLM-friendly output on failure

#### Scenario: Asm2KeycloakExternalModelTest uses structural comparison

Given system property judo.test.comparison.structural=true
When Asm2KeycloakExternalModelTest runs model comparison
Then it uses StructuralModelComparator
And produces LLM-friendly output on failure

#### Scenario: Rdbms2LiquibaseExternalModelTest uses structural comparison

Given system property judo.test.comparison.structural=true
When Rdbms2LiquibaseExternalModelTest runs model comparison
Then it uses StructuralModelComparator
And produces LLM-friendly output on failure
