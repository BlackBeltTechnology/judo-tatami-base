## MODIFIED Requirements

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
