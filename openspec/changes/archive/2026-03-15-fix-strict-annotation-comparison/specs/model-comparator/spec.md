## REMOVED Requirements

### Requirement: LENIENT comparison mode
**Reason**: Unused and redundant with SKELETON. No test in the repository passes `ComparisonMode.LENIENT`. Removing it reduces API surface and eliminates dead code paths.
**Migration**: Replace any use of `ComparisonMode.LENIENT` with `ComparisonMode.SKELETON` if annotation-skipping behaviour is still desired, or with `ComparisonMode.STRICT` for full validation.

---

## RENAMED Requirements

### Requirement: STRUCTURAL comparison mode
FROM: `ComparisonMode.STRUCTURAL`
TO: `ComparisonMode.SKELETON`

---

## MODIFIED Requirements

### Requirement: ModelComparator Comparison

The ModelComparator MUST take two ModelNode structures and return a ComparisonResult with differences.
The default comparison mode SHALL be `STRICT`.
In `STRICT` mode the comparator SHALL validate every attribute, every reference, and every EAnnotation.
In `SKELETON` mode the comparator SHALL skip EAnnotation lists entirely (previous STRUCTURAL behaviour).
EAnnotation list comparison in `STRICT` mode SHALL be order-insensitive: two EAnnotation lists are equal when every annotation in one list has a matching annotation in the other, matched by `source` URI. Annotation `details` entries (EStringToStringMapEntry) SHALL be compared as an unordered key→value map.

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
