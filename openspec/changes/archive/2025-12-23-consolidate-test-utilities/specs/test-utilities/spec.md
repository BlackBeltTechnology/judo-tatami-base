# Test Utilities Specification

## Overview

The `judo-tatami-test-utils` module provides shared test utilities for all transformation modules. This includes model generators for performance testing and model comparison utilities for verifying transformation equivalence.

## ADDED Requirements

### Requirement: REQ-TU-001 Shared ModelComparator

The test-utils module SHALL provide a shared ModelComparator utility for comparing EMF models across all transformation modules.

#### Scenario: Compare equivalent models
Given two EMF models produced by different transformation engines (ETL and Zeta)
When the ModelComparator.compare() method is called
Then it returns a ComparisonResult indicating structural equivalence

#### Scenario: Compare non-equivalent models
Given two EMF models with structural differences
When the ModelComparator.compare() method is called
Then it returns a ComparisonResult with a list of differences

#### Scenario: Assert model equivalence
Given two EMF models expected to be equivalent
When the ModelComparator.assertEquivalent() method is called
Then it throws AssertionError if models differ, or completes successfully if equivalent

### Requirement: REQ-TU-002 Comparison Modes

The ModelComparator MUST support multiple comparison modes to accommodate different testing scenarios.

#### Scenario: STRICT comparison mode
Given comparison mode set to STRICT
When comparing two models
Then all attributes, references, and annotations must match exactly

#### Scenario: STRUCTURAL comparison mode
Given comparison mode set to STRUCTURAL
When comparing two models
Then element structure must match, annotation differences are tolerated

#### Scenario: LENIENT comparison mode
Given comparison mode set to LENIENT
When comparing two models
Then major structural elements must match, minor differences are allowed

### Requirement: REQ-TU-003 Configuration via System Properties

The ModelComparator MUST support configuration through system properties for CI/CD integration.

#### Scenario: Disable comparison
Given system property `judo.test.comparison.enabled` set to `false`
When comparison is requested
Then comparison is skipped

#### Scenario: Set comparison mode
Given system property `judo.test.comparison.mode` set to `STRICT`
When comparison is performed
Then STRICT mode is used

#### Scenario: Limit reported differences
Given system property `judo.test.comparison.maxDifferences` set to `10`
When comparison finds more than 10 differences
Then only 10 differences are reported

#### Scenario: Output report to file
Given system property `judo.test.comparison.reportFile` set to a path
When comparison completes
Then detailed report is written to the specified file

## MODIFIED Requirements

### Requirement: REQ-TU-004 Module Structure

The test-utils module MUST organize utilities in clear packages.

#### Scenario: Package organization
Given the judo-tatami-test-utils module
Then utilities are organized as:
- `hu.blackbelt.judo.tatami.test` - Model generators (RealisticPsmModelGenerator, etc.)
- `hu.blackbelt.judo.tatami.test.util` - Comparison utilities (ModelComparator)

## Dependencies

The test-utils module depends on:
- EMF/Ecore (for EObject comparison)
- PSM, ASM, RDBMS, Measure model dependencies (for model generation)

Consumer modules depend on test-utils with `<scope>test</scope>`.
