# PSM2Measure Deterministic Ordering

## Overview

Ensure PSM2Measure transformation produces deterministic output by ordering model element iteration.

## ADDED Requirements

### Requirement: Deterministic Element Iteration

ModelProvider implementations SHALL return elements in a deterministic order to ensure reproducible transformation results.

#### Scenario: Consistent measure ordering across runs

**Given** a PSM model with multiple measures (Time, Mass, AmountOfSubstance, etc.)
**When** the PSM2Measure transformation executes
**Then** measures are processed in the same order every run
**And** the resulting Measure model has identical element ordering

#### Scenario: XMI ID-based sorting

**Given** elements have XMI IDs assigned
**When** ModelProvider.getAllContents() is called
**Then** elements are sorted by XMI ID alphabetically
**And** null XMI IDs are sorted to the end

#### Scenario: Fallback to qualified name

**Given** elements without XMI IDs
**When** ModelProvider.getAllContents() is called
**And** XMI ID is null
**Then** elements are sorted by qualified name
**And** null names are sorted to the end

## Related Capabilities

- psm2measure-transformation
- zeta-transformation-framework
