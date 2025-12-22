# Zeta Dual Transformation System Specification

## ADDED Requirements

### Requirement: REQ-ZETA-001 Zeta Framework Dependency
The project MUST include the Zeta framework as a managed dependency with a configurable version property.

#### Scenario: Zeta version property in parent POM
Given the parent pom.xml file
When the properties section is examined
Then a property `judo-zeta-version` exists with value `1.0.0.20251207_081454_0779b890_develop`

#### Scenario: Zeta dependency management
Given the parent pom.xml file
When the dependencyManagement section is examined
Then entries exist for `zeta-annotations`, `transformation-core`, and `validation-core` using `${judo-zeta-version}`

### Requirement: REQ-ZETA-002 Dual Transformation Mode
Each transformation module MUST support both ETL and Zeta transformation engines, selectable at runtime.

#### Scenario: ETL transformation execution
Given a PSM model as input
When the transformation is executed with mode ETL
Then the Epsilon ETL engine processes the transformation
And an ASM model is produced

#### Scenario: Zeta transformation execution
Given a PSM model as input
When the transformation is executed with mode ZETA
Then the Zeta Java transformation engine processes the transformation
And an ASM model is produced

#### Scenario: Dual mode comparison
Given a PSM model as input
When the transformation is executed with mode DUAL
Then both ETL and Zeta transformations execute
And the output models are compared for structural equivalence
And any differences are reported

### Requirement: REQ-ZETA-003 Rule Name Constants
All Zeta transformation rules MUST use constants for rule names, defined in a dedicated constants class per module.

#### Scenario: Rule constants class exists
Given the judo-tatami-psm2asm module
When examining the Zeta transformation code
Then a class `Psm2AsmRuleNames` exists
And it contains public static final String constants for each rule name

#### Scenario: Rule annotation uses constants
Given a Zeta transformation method
When the @TransformRule annotation is applied
Then the `name` attribute references a constant from the rule names class

### Requirement: REQ-ZETA-004 Java Transformation Implementation
Each ETL transformation file MUST have a corresponding Java implementation using Zeta annotations.

#### Scenario: PSM2ASM namespace rules
Given the file `transformations/asm/modules/namespace.etl`
When examining the Zeta implementation
Then equivalent Java methods exist with @TransformRule annotations
And the transformation logic produces equivalent output

#### Scenario: PSM2ASM type rules
Given the file `transformations/asm/modules/type.etl`
When examining the Zeta implementation
Then equivalent Java methods exist with @TransformRule annotations
And the transformation logic produces equivalent output

#### Scenario: PSM2ASM data rules
Given the file `transformations/asm/modules/data.etl`
When examining the Zeta implementation
Then equivalent Java methods exist with @TransformRule annotations
And the transformation logic produces equivalent output

### Requirement: REQ-ZETA-005 Parameterized Dual Testing
All existing transformation tests MUST be converted to parameterized tests that run with both ETL and Zeta engines.

#### Scenario: Test runs with ETL engine
Given an existing transformation test
When executed with TransformationType.ETL parameter
Then the test uses the ETL transformation engine
And all assertions pass

#### Scenario: Test runs with Zeta engine
Given an existing transformation test
When executed with TransformationType.ZETA parameter
Then the test uses the Zeta transformation engine
And all assertions pass

#### Scenario: Model equivalence verification
Given an existing transformation test
When both ETL and Zeta transformations complete
Then the output models are compared
And structural equivalence is verified

### Requirement: REQ-ZETA-006 Performance Benchmarks
Performance tests MUST be added to compare ETL and Zeta transformation execution times on large models.

#### Scenario: Large model generation
Given a performance test
When model generation is requested
Then a model with 10,000 elements is created
And the model is valid for transformation

#### Scenario: Performance measurement
Given a large model with 10,000 elements
When both ETL and Zeta transformations execute
Then execution times are measured and logged
And the output models are verified equivalent

#### Scenario: Performance reporting
Given completed performance tests
When reviewing test output
Then ETL execution time is logged in milliseconds
And Zeta execution time is logged in milliseconds
And the performance ratio is calculated

### Requirement: REQ-ZETA-007 Documentation Conversion
All AsciiDoc documentation MUST be converted to Markdown format with diagram conversions.

#### Scenario: README conversion
Given the file `README.adoc`
When documentation conversion is complete
Then a file `README.md` exists with equivalent content
And GitHub badges render correctly

#### Scenario: CONTRIBUTING conversion
Given the file `CONTRIBUTING.adoc`
When documentation conversion is complete
Then a file `CONTRIBUTING.md` exists with equivalent content

#### Scenario: CIFLOW conversion with Mermaid
Given the file `.github/CIFLOW.adoc`
When documentation conversion is complete
Then a file `.github/CIFLOW.md` exists
And PlantUML diagrams are converted to Mermaid format

### Requirement: REQ-ZETA-008 Transformation Rule Documentation
All transformation rules MUST be documented in the `docs/transformations/` directory.

#### Scenario: PSM2ASM documentation
Given the judo-tatami-psm2asm module
When examining `docs/transformations/psm2asm.md`
Then all transformation rules are documented
And each rule has a description of source and target elements
And transformation logic is explained

#### Scenario: Documentation completeness
Given all transformation modules
When examining `docs/transformations/`
Then a markdown file exists for each module
And a README.md provides an overview of all transformations

## MODIFIED Requirements

### Requirement: REQ-MOD-001 Test Base Classes
Existing test classes MUST be refactored to extend a dual-transformation test base.

#### Scenario: Test inheritance
Given an existing test class like `Psm2AsmTest`
When the test is modified for dual transformation
Then it extends `AbstractDualTransformationTest` or uses parameterized tests
And both transformation modes are tested

### Requirement: REQ-MOD-002 Work Class Enhancement
Existing Work classes MUST support transformation mode selection.

#### Scenario: Work class mode parameter
Given the `Psm2AsmWork` class
When a transformation mode parameter is supported
Then the work class can execute ETL, Zeta, or both transformations
