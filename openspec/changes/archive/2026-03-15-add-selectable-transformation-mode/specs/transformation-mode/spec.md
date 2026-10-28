# Spec: Selectable Transformation Mode

## Overview

This spec defines the transformation mode selection capability for `AbstractExternalModelTest`.

## ADDED Requirements

### REQ-TM-001: System Property for Transformation Mode

The test framework MUST read transformation mode from the system property `judo.test.transformation.mode`.

#### Scenario: ZETA mode via system property
- **Given** system property `judo.test.transformation.mode=ZETA`
- **When** a test extends `AbstractExternalModelTest`
- **Then** `getConfiguredTransformationMode()` returns `TransformationMode.ZETA`
- **And** `shouldRunZeta()` returns `true`
- **And** `shouldRunEtl()` returns `false`
- **And** `shouldCompareResults()` returns `false`

#### Scenario: ETL mode via system property
- **Given** system property `judo.test.transformation.mode=ETL`
- **When** a test extends `AbstractExternalModelTest`
- **Then** `getConfiguredTransformationMode()` returns `TransformationMode.ETL`
- **And** `shouldRunEtl()` returns `true`
- **And** `shouldRunZeta()` returns `false`
- **And** `shouldCompareResults()` returns `false`

#### Scenario: DUAL mode via system property
- **Given** system property `judo.test.transformation.mode=DUAL`
- **When** a test extends `AbstractExternalModelTest`
- **Then** `getConfiguredTransformationMode()` returns `TransformationMode.DUAL`
- **And** `shouldRunEtl()` returns `true`
- **And** `shouldRunZeta()` returns `true`
- **And** `shouldCompareResults()` returns `true`

### REQ-TM-002: Default to DUAL Mode

The test framework MUST default to `DUAL` mode when no system property is set, maintaining backward compatibility.

#### Scenario: No system property set
- **Given** system property `judo.test.transformation.mode` is not set
- **When** a test extends `AbstractExternalModelTest`
- **Then** `getConfiguredTransformationMode()` returns `TransformationMode.DUAL`
- **And** both ETL and ZETA transformations are executed
- **And** results are compared

### REQ-TM-003: Case-Insensitive Property Value

The system property value MUST be case-insensitive for user convenience.

#### Scenario: Lowercase mode value
- **Given** system property `judo.test.transformation.mode=zeta`
- **When** a test extends `AbstractExternalModelTest`
- **Then** `getConfiguredTransformationMode()` returns `TransformationMode.ZETA`

#### Scenario: Mixed case mode value
- **Given** system property `judo.test.transformation.mode=Dual`
- **When** a test extends `AbstractExternalModelTest`
- **Then** `getConfiguredTransformationMode()` returns `TransformationMode.DUAL`

### REQ-TM-004: Invalid Property Value Handling

The test framework MUST default to `DUAL` mode when an invalid property value is provided.

#### Scenario: Invalid mode value
- **Given** system property `judo.test.transformation.mode=invalid`
- **When** a test extends `AbstractExternalModelTest`
- **Then** `getConfiguredTransformationMode()` returns `TransformationMode.DUAL`
- **And** a warning is logged

### REQ-TM-005: Helper Methods

The `AbstractExternalModelTest` class MUST provide helper methods for transformation mode decisions.

#### Scenario: Helper method availability
- **Given** a test class extends `AbstractExternalModelTest`
- **When** the test needs to determine what to execute
- **Then** `getConfiguredTransformationMode()` returns the configured mode
- **And** `shouldRunEtl()` returns `true` if mode is ETL or DUAL
- **And** `shouldRunZeta()` returns `true` if mode is ZETA or DUAL
- **And** `shouldCompareResults()` returns `true` if mode is DUAL

### REQ-TM-006: Profiling Integration

When JVM profiling is enabled, the test framework SHOULD recommend or default to ZETA mode to avoid polluting metrics with ETL overhead.

#### Scenario: Profiling with ZETA mode
- **Given** system property `judo.test.transformation.mode=ZETA`
- **And** JVM profiler is enabled (async-profiler or JFR)
- **When** a test executes
- **Then** only ZETA transformation code appears in profiler output
- **And** no Epsilon ETL interpreter overhead pollutes the metrics

#### Scenario: Profiling with DUAL mode (not recommended)
- **Given** system property `judo.test.transformation.mode=DUAL`
- **And** JVM profiler is enabled
- **When** a test executes
- **Then** a warning is logged: "Profiling in DUAL mode - consider using ZETA mode for accurate metrics"

## MODIFIED Requirements

### REQ-TM-007: Logging Enhancement

The test framework SHOULD log the configured transformation mode at test startup.

#### Scenario: Mode logged on test start
- **Given** system property `judo.test.transformation.mode=ZETA`
- **When** a test starts execution
- **Then** the log contains "Transformation mode: ZETA"
