# Model Comparison Capability

This capability provides order-independent comparison of EMF models produced by ETL and Zeta transformations.

## ADDED Requirements

### Requirement: Order-Independent Collection Comparison

The model comparator SHALL compare collections as unordered sets, matching elements by their identifying characteristics rather than position.

#### Scenario: Collections with same elements in different order
Given an ETL transformation produces a model with elements [A, B, C] in a collection
And a Zeta transformation produces a model with elements [C, A, B] in the same collection
When the models are compared
Then the comparison SHALL report the models as equivalent

#### Scenario: Collections with different elements
Given an ETL transformation produces a model with elements [A, B, C] in a collection
And a Zeta transformation produces a model with elements [A, B, D] in the same collection
When the models are compared
Then the comparison SHALL report element C is missing from Zeta result
And the comparison SHALL report element D is extra in Zeta result

### Requirement: Recursive Element Validation

The model comparator SHALL recursively traverse all containment references to compare the entire model graph.

#### Scenario: Nested element comparison
Given an ETL transformation produces a model with a Package containing Classes containing Attributes
And a Zeta transformation produces a structurally identical model
When the models are compared
Then the comparison SHALL verify the Package, all Classes, and all Attributes match

#### Scenario: Missing nested element
Given an ETL transformation produces a model with a Class containing Attribute "name"
And a Zeta transformation produces a model with the same Class but missing the "name" Attribute
When the models are compared
Then the comparison SHALL report the missing Attribute at path "Package.Class.attributes[name]"

### Requirement: Bidirectional Completeness Check

The model comparator SHALL verify that no elements are missing from either model and no extra elements exist.

#### Scenario: Element missing from Zeta result
Given an ETL transformation produces a model with element X
And a Zeta transformation produces a model without element X
When the models are compared
Then the comparison SHALL report element X is missing from Zeta result

#### Scenario: Extra element in Zeta result
Given an ETL transformation produces a model without element Y
And a Zeta transformation produces a model with element Y
When the models are compared
Then the comparison SHALL report element Y is extra in Zeta result

### Requirement: Element Identification

The model comparator SHALL identify elements using name attribute, ID annotation, or type-based matching as fallback.

#### Scenario: Match by name attribute
Given two models with EClass elements having name="Customer"
When the elements are compared
Then the comparator SHALL match them by their name attribute

#### Scenario: Match unnamed elements by type
Given two models with unnamed EAnnotation elements of the same source
When the elements are compared
Then the comparator SHALL match them by their type and source attribute

### Requirement: Attribute Value Comparison

The model comparator SHALL compare all non-transient, non-derived attribute values.

#### Scenario: Matching attribute values
Given two models with an EAttribute where name="age" and type=EInt
When the models are compared
Then the comparison SHALL verify both name and type attributes match

#### Scenario: Mismatching attribute values
Given an ETL model with EAttribute lowerBound=0
And a Zeta model with EAttribute lowerBound=1
When the models are compared
Then the comparison SHALL report the lowerBound mismatch with expected and actual values

### Requirement: Skip Transient and Derived Features

The model comparator SHALL skip features marked as transient or derived during comparison.

#### Scenario: Transient feature difference ignored
Given two models where a transient feature has different values
When the models are compared
Then the comparison SHALL report the models as equivalent

### Requirement: Detailed Difference Reporting

The model comparator SHALL provide detailed reports with element paths for all differences found.

#### Scenario: Difference report format
Given a comparison finds a missing element at path "demo.entities.Customer.attributes[email]"
When the difference report is generated
Then the report SHALL include the full path "demo.entities.Customer.attributes[email]"
And the report SHALL describe the type of difference (missing element)

#### Scenario: Summary report
Given a comparison finds 5 differences
When the summary is requested
Then the summary SHALL report "5 differences found" with counts by type

### Requirement: Comparison Modes

The model comparator SHALL support multiple comparison strictness modes.

#### Scenario: Strict mode comparison
Given comparison mode is set to STRICT
When two models are compared
Then all attribute values including annotations MUST match exactly

#### Scenario: Structural mode comparison
Given comparison mode is set to STRUCTURAL
When two models are compared
Then structural elements MUST match but annotation differences are tolerated

## MODIFIED Requirements

None.

## REMOVED Requirements

None.
