# Zeta Transformation Trace Support

## ADDED Requirements

### Requirement: Zeta Transformations SHALL Return Native TransformationTrace

All Zeta transformation `execute()` methods SHALL return Zeta's `TransformationTrace` from `TransformationResult.getTrace()`.

#### Scenario: execute() returns TransformationTrace
Given a Zeta transformation class
When execute() completes
Then it returns TransformationResult.getTrace()
And the return type is TransformationTrace (not Map)

#### Scenario: Trace contains entries after transformation
Given a completed Zeta transformation
When accessing trace.getEntries()
Then entries exist for each source-to-target mapping
And each entry contains ruleName, source, target, primary, discriminator

### Requirement: TransformationTrace Classes SHALL Support Dual Trace Storage

The `*TransformationTrace` classes SHALL store either ETL trace (`Map`) or Zeta trace (`TransformationTrace`), not both.

#### Scenario: Zeta trace stored in zetaTrace field
Given a Zeta transformation produces TransformationTrace
When building Psm2AsmTransformationTrace
Then zetaTrace field is set to the TransformationTrace
And trace field is null

#### Scenario: ETL trace stored in trace field
Given an ETL transformation produces Map<EObject, List<EObject>>
When building Psm2AsmTransformationTrace
Then trace field is set to the Map
And zetaTrace field is null

### Requirement: TransformationTrace Classes SHALL Provide Zeta Trace Accessor

The `*TransformationTrace` classes SHALL provide `getZetaTrace()` method returning the native Zeta trace.

#### Scenario: getZetaTrace() returns Zeta trace
Given a Psm2AsmTransformationTrace with Zeta trace
When calling getZetaTrace()
Then the Zeta TransformationTrace is returned

#### Scenario: getZetaTrace() returns null for ETL
Given a Psm2AsmTransformationTrace with ETL trace
When calling getZetaTrace()
Then null is returned

### Requirement: Legacy getTransformationTrace() SHALL Return Empty Map for Zeta

The `getTransformationTrace()` method SHALL return empty map when Zeta trace is used.

#### Scenario: getTransformationTrace() returns empty map for Zeta
Given a Psm2AsmTransformationTrace with Zeta trace
When calling getTransformationTrace()
Then Collections.emptyMap() is returned

#### Scenario: getTransformationTrace() returns Map for ETL
Given a Psm2AsmTransformationTrace with ETL trace
When calling getTransformationTrace()
Then the ETL trace Map is returned

### Requirement: TransformationTrace Classes SHALL Provide isZetaTrace() Check

The `*TransformationTrace` classes SHALL provide `isZetaTrace()` method to distinguish trace type.

#### Scenario: isZetaTrace() returns true for Zeta
Given a transformation trace created from Zeta
When calling isZetaTrace()
Then true is returned

#### Scenario: isZetaTrace() returns false for ETL
Given a transformation trace created from ETL
When calling isZetaTrace()
Then false is returned

### Requirement: Zeta Trace SHALL Be Loadable via TransformationTraceLoader

Zeta trace JSON files SHALL be loadable using tatami-core's `TransformationTraceLoader`.

#### Scenario: Load trace from JSON file
Given a Zeta trace saved to JSON file
When calling TransformationTraceLoader.loadTrace(file, resourceSets)
Then List<TraceEntry> is returned
And entries are resolved to EObjects by id attribute

#### Scenario: Unresolved element throws TraceLoadException
Given a trace JSON with element id not in ResourceSets
When loading via TransformationTraceLoader
Then TraceLoadException is thrown (fail-fast)

## MODIFIED Requirements

### Requirement: Work Classes SHALL Handle Dual Code Paths

Work classes SHALL have separate code paths for ETL and Zeta trace handling.

#### Scenario: Zeta path uses TransformationTrace
Given Psm2AsmWork executing with Zeta engine
When transformation completes
Then TransformationTrace from execute() is stored in zetaTrace field

#### Scenario: ETL path uses Map
Given Psm2AsmWork executing with ETL engine
When transformation completes
Then Map<EObject, List<EObject>> is stored in trace field
