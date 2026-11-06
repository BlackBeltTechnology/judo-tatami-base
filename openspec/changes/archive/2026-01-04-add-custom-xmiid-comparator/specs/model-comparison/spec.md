# Model Comparison Custom XMI ID Extractor Support

## Overview

Enable consumer projects to provide custom XMI ID extraction logic for model comparison.

## ADDED Requirements

### Requirement: Custom XMI ID Extractor Interface

ModelComparator SHALL support custom XMI ID extraction via a functional interface.

#### Scenario: Custom extractor for domain-specific types

**Given** a consumer project with domain-specific model types
**When** comparing models with custom XmiIdExtractor
**Then** the extractor is called for each element during XMI ID comparison
**And** custom IDs are used for matching elements

#### Scenario: Null fallback to default extraction

**Given** a custom extractor that returns null for an element
**When** comparing XMI IDs
**Then** the default XMI ID extraction logic is used for that element
**And** comparison proceeds normally

### Requirement: Recursive Application

Custom XMI ID extractor SHALL be applied to all elements recursively.

#### Scenario: Deep comparison with custom extractor

**Given** a model with nested elements
**When** comparing XMI IDs with custom extractor
**Then** the extractor is called for root elements
**And** the extractor is called for all nested elements
**And** all levels use custom extraction logic

### Requirement: Backward Compatible API

New XMI ID comparison methods SHALL not break existing code.

#### Scenario: Existing code without custom extractor

**Given** existing code calling compareXmiIds()
**When** no XmiIdExtractor parameter provided
**Then** behavior is identical to current implementation
**And** no code changes required

#### Scenario: Default XMI ID exposed for composition

**Given** a custom extractor implementation
**When** needing to delegate to default logic
**Then** `ModelComparator.defaultXmiId(obj)` is available
**And** returns the same result as current internal extraction

## Related Capabilities

- zeta-transformations
