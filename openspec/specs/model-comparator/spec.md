# model-comparator Specification

## Purpose
TBD - created by archiving change enhance-structural-reference-validation. Update Purpose after archive.
## Requirements
### Requirement: ModelChecksumCalculator Structure Building

The ModelChecksumCalculator MUST build a ModelNode tree that mirrors the EMF model's containment hierarchy, separating containment references from non-containment references.

#### Scenario: calculate() builds containment tree

Given an EMF Resource with EPackage containing EClass elements
When calculate(resource) is called
Then it returns a ModelNode root
And the root has containments for the EPackage
And the EPackage node has containments for EClass elements

#### Scenario: Containments separate from references

Given an EClass with eSuperTypes (non-containment) and eStructuralFeatures (containment)
When calculate() builds the ModelNode
Then eStructuralFeatures appear in containments map
And eSuperTypes appear in references map

---

### Requirement: ModelNode EObject Resolution

Each ModelNode MUST store a direct reference to the source EObject for later resolution.

#### Scenario: getEObject() returns source object

Given a ModelNode created for an EClass
When getEObject() is called
Then it returns the original EClass instance

#### Scenario: findByPath() finds nested nodes

Given a ModelNode tree with nested structure
When findByPath("EPackage:pkg/EClass:MyClass") is called
Then it returns the ModelNode for MyClass

---

### Requirement: ReferenceNode Resolution

Non-containment references MUST be represented as ReferenceNode objects that can resolve to the target ModelNode and EObject.

#### Scenario: resolve() returns target node

Given a ReferenceNode with targetPath
When resolve(root) is called
Then it returns the ModelNode at that path

#### Scenario: resolveEObject() returns target EObject

Given a ReferenceNode for target "Base"
When resolveEObject(root) is called
Then it returns the EObject for "Base"

---

### Requirement: Checksum Calculation

The ModelChecksumCalculator MUST compute structural checksums that include type, sorted attributes, containment checksums, and reference target checksums.

#### Scenario: Identical elements produce same checksum

Given two separate models with identical structure
When calculate() is called on both
Then the root checksums are equal

#### Scenario: Different attributes produce different checksum

Given two models differing only in one attribute value
When calculate() is called on both
Then the root checksums differ

#### Scenario: Multi-valued reference order does not affect checksum

Given Child1 with superTypes [Base1, Base2]
And Child2 with superTypes [Base2, Base1]
When calculate() is called on both
Then the checksums are equal

---

### Requirement: Bidirectional Reference Handling

For bidirectional references, only the owner side MUST be included in checksum calculation.

#### Scenario: Container side is owner

Given Parent with containment "children" to Child
And Child with container reference "parent" back
When checksums are calculated
Then Parent includes "children" in checksum
And Child does NOT include "parent" in checksum

---

### Requirement: ModelComparator Comparison

The ModelComparator MUST take two ModelNode structures and return a ComparisonResult with differences.
The default comparison mode SHALL be `STRICT`.
In `STRICT` mode the comparator SHALL validate every attribute, every reference, and every EAnnotation.
In `SKELETON` mode the comparator SHALL skip EAnnotation lists entirely (previous STRUCTURAL behaviour).
EAnnotation list comparison in `STRICT` mode SHALL be order-insensitive: two EAnnotation lists are equal when every annotation in one list has a matching annotation in the other, matched by `source` URI. Annotation `details` entries (EStringToStringMapEntry) SHALL be compared as an unordered key→value map.
When XMI ID comparison is enabled, the comparator SHALL use exact string equality (`String.equals()`) to match XMI IDs between expected and actual models. The comparator SHALL NOT use flexible rule-name matching, substring containment, prefix stripping, or rule-name mapping tables.

#### Scenario: compare() with identical structures
- **WHEN** two ModelNode roots with equal checksums are compared
- **THEN** isMatch() returns true and getDifferences() is empty

#### Scenario: compare() detects missing element
- **WHEN** expected has [A, B] and actual has [A]
- **THEN** a MISSING difference is reported for B

#### Scenario: compare() detects extra element
- **WHEN** expected has [A] and actual has [A, B]
- **THEN** an EXTRA difference is reported for B

#### Scenario: compare() detects attribute mismatch
- **WHEN** expected has Class.abstract=true and actual has Class.abstract=false
- **THEN** an ATTRIBUTE_MISMATCH difference is reported containing "abstract", "true", "false"

#### Scenario: compare() detects reference mismatch
- **WHEN** expected references pkg1/Base and actual references pkg2/Base
- **THEN** a REFERENCE_MISMATCH difference is reported

#### Scenario: STRICT mode validates annotations
- **WHEN** STRICT mode is active and expected has an EAnnotation with source "http://foo" but actual does not
- **THEN** a difference is reported for the missing annotation

#### Scenario: STRICT mode is order-insensitive for annotations
- **WHEN** STRICT mode is active and expected has annotations [A, B] and actual has annotations [B, A]
- **THEN** no difference is reported

#### Scenario: STRICT mode detects annotation detail mismatch
- **WHEN** STRICT mode is active and two annotations share the same source URI but differ in a detail entry value
- **THEN** a difference is reported identifying the mismatched detail key

#### Scenario: SKELETON mode skips annotations
- **WHEN** SKELETON mode is active and the two models differ only in their EAnnotation lists
- **THEN** no difference is reported

#### Scenario: Default mode is STRICT
- **WHEN** no comparison mode is configured via system property
- **THEN** getConfiguredMode() returns ComparisonMode.STRICT

#### Scenario: XMI ID comparison uses exact string matching
- **WHEN** XMI ID comparison is enabled and expected element has XMI ID `(psm/_abc)/EntityClass`
- **AND** actual element has XMI ID `(psm/_abc)/CreateEntityClass`
- **THEN** the IDs are reported as mismatched (not treated as equivalent)

#### Scenario: Identical XMI IDs match exactly
- **WHEN** XMI ID comparison is enabled and both expected and actual elements have XMI ID `(psm/_abc)/EntityClass`
- **THEN** the IDs are treated as matching

---

### Requirement: Checksum Optimization

The ModelComparator MUST use checksum comparison to skip detailed comparison of identical subtrees.

#### Scenario: Identical subtrees not traversed

Given two models with 1000 elements and 1 difference
When compare() is called
Then only the differing path is traversed in detail

---

### Requirement: Difference EObject Resolution

Each Difference MUST provide methods to resolve the expected and actual EObjects directly.

#### Scenario: getExpectedObject() returns EObject

Given a MISSING difference for element "MyClass"
When getExpectedObject() is called
Then it returns the EClass from expected model

#### Scenario: getActualObject() returns null for MISSING

Given a MISSING difference
When getActualObject() is called
Then it returns null

#### Scenario: getExpectedObject() returns null for EXTRA

Given an EXTRA difference
When getExpectedObject() is called
Then it returns null

---

### Requirement: JSON Export

The ModelChecksumCalculator MUST provide toJson() method for serializing ModelNode structures.

#### Scenario: toJson() includes core properties

Given a ModelNode with type, identifier, path, checksum
When toJson() is called
Then JSON contains all these fields

#### Scenario: toJson() includes containments

Given a ModelNode with children
When toJson() is called
Then JSON contains "containments" with nested children

#### Scenario: toJson() includes references

Given a ModelNode with non-containment references
When toJson() is called
Then JSON contains "references" with target paths and checksums

#### Scenario: JsonOptions controls output

Given JsonOptions with includeAttributes=false
When toJson(node, options) is called
Then JSON does not include "attributes"

---

### Requirement: DifferenceType Enumeration

The Difference class MUST use a DifferenceType enum to categorize differences.

#### Scenario: DifferenceType has required values

Given the DifferenceType enum
Then it includes MISSING, EXTRA, ATTRIBUTE_MISMATCH, REFERENCE_MISMATCH, TYPE_MISMATCH

---

### Requirement: ComparisonResult Filtering

The ComparisonResult MUST provide methods to filter differences by type.

#### Scenario: getDifferences(type) filters results

Given a ComparisonResult with MISSING and ATTRIBUTE_MISMATCH differences
When getDifferences(MISSING) is called
Then only MISSING differences are returned

---

### Requirement: Custom Identifier Resolution

The ModelChecksumCalculator MUST support custom identifier resolution via CalculatorOptions.

#### Scenario: Custom identifier resolver used

Given CalculatorOptions with custom identifierResolver
When calculate() builds ModelNode paths
Then paths use identifiers from the custom resolver

#### Scenario: Default identifier uses name attribute

Given an EObject with a "name" attribute set to "MyElement"
When default identifier resolver is used
Then the identifier is "MyElement"

#### Scenario: Default identifier falls back to type and index

Given an EObject without a "name" attribute
When default identifier resolver is used
Then the identifier is "EClassName_index"

---

### Requirement: Cross-Resource Reference Handling

The ModelChecksumCalculator MUST skip cross-resource references.

#### Scenario: Cross-resource reference ignored

Given an EObject with a reference to another Resource
When addReferences() processes the reference
Then the reference is not included in the checksum

---

### Requirement: Derived and Transient Feature Exclusion

The ModelChecksumCalculator MUST exclude derived and transient features from checksums.

#### Scenario: Derived attribute excluded

Given an EClass with a derived attribute
When calculate() builds the ModelNode
Then the derived attribute is not included in attributes map

#### Scenario: Transient reference excluded

Given an EClass with a transient reference
When calculate() processes references
Then the transient reference is not included

---

### Requirement: Proxy Resolution

The ModelChecksumCalculator MUST resolve proxy EObjects lazily before including in checksums.

#### Scenario: Proxy resolved successfully

Given a proxy reference to an EObject in the same Resource
When addReferences() processes the reference
Then the proxy is resolved and included in checksum

#### Scenario: Unresolved proxy skipped with warning

Given a proxy reference that cannot be resolved
When addReferences() processes the reference
Then the reference is skipped and a warning is logged

---

### Requirement: Bidirectional Reference Ownership

For bidirectional references, only the owner side MUST be included in checksum calculation to avoid duplication.

#### Scenario: Container side is owner for containment bidirectional

Given Parent with containment "children" to Child
And Child with container reference "parent" back to Parent
When checksums are calculated
Then Parent includes "children" in checksum
And Child does NOT include "parent" in checksum

#### Scenario: Alphabetically first is owner for non-containment bidirectional

Given EReference "left" with opposite "right"
When checksums are calculated
Then "left" is included in the checksum
And "right" is NOT included in the checksum

#### Scenario: Same checksum regardless of traversal direction

Given a bidirectional reference between A and B
When calculating checksum starting from A
And calculating checksum starting from B
Then both calculations produce the same root checksum

---

### Requirement: Multi-Layer Containment Support

The ModelChecksumCalculator MUST correctly build paths and propagate checksums through deep containment hierarchies.

#### Scenario: Deep containment path correctness

Given EPackage "root" containing EPackage "sub" containing EClass "MyClass"
When calculate() builds the ModelNode tree
Then MyClass node path is "EPackage:root/EPackage:sub/EClass:MyClass"

#### Scenario: Checksum propagation from leaf to root

Given a deep containment hierarchy
When an attribute changes at the leaf level
Then all parent node checksums in the chain also change

#### Scenario: Difference detection at any level

Given two models with a difference at the middle containment level
When compare() is called
Then the difference reports the correct path to the differing element

---

### Requirement: Inheritance Reference Support

The ModelChecksumCalculator MUST correctly handle inheritance (eSuperTypes) references with order-independent comparison.

#### Scenario: Single inheritance produces consistent checksum

Given Child extends Parent
When calculate() is called on identical models
Then the checksums are equal

#### Scenario: Multiple inheritance order-independent

Given Child extends [Base1, Base2, Base3] in one model
And Child extends [Base3, Base1, Base2] in another
When calculate() is called on both
Then the checksums are equal

#### Scenario: Diamond inheritance pattern

Given D extends B and C, where B and C both extend A
When calculate() is called
Then the checksum is consistent and reflects the full inheritance graph

#### Scenario: Inheritance chain differences detected

Given inheritance chain A -> B -> C -> D in expected
And inheritance chain A -> B -> D (missing C) in actual
When compare() is called
Then differences are reported for the missing/changed inheritance

---

### Requirement: Feature Ignore Support

The ModelChecksumCalculator MUST support ignoring specific features during checksum calculation via CalculatorOptions.

#### Scenario: Ignored attribute excluded from checksum

Given two models differing only in attribute "abstract"
And CalculatorOptions with ignore("ecore.EClass#abstract")
When calculate(resource, options) is called on both
Then the checksums are equal

#### Scenario: Ignored reference excluded from checksum

Given two models differing only in reference "eSuperTypes"
And CalculatorOptions with ignore("ecore.EClass#eSuperTypes")
When calculate(resource, options) is called on both
Then the checksums are equal

---

### Requirement: Feature Pattern Wildcards

The FeaturePattern MUST support wildcards for package, class, and feature names.

#### Scenario: Package wildcard matches any package

Given pattern "*.EClass#abstract"
When matching EClass from any package
Then the pattern matches

#### Scenario: Class wildcard matches any class

Given pattern "ecore.*#name"
When matching any class in ecore package
Then the pattern matches for the "name" feature

#### Scenario: Global wildcard matches all features

Given pattern "*.*#uuid"
When matching any class with "uuid" feature
Then the pattern matches

---

### Requirement: Ignore Match Warning

The ModelChecksumCalculator MUST log a warning when an ignore pattern matches many elements.

#### Scenario: Warning logged for broad pattern

Given CalculatorOptions with ignore("*.*#name")
When calculate() processes a model with 500+ elements
Then a warning is logged about the broad match

---

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

---

