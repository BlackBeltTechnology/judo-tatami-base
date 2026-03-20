## Context

`AbstractDualComparisonTest<S,T>` in `judo-tatami-test-utils` provides `@TestFactory` orchestration for dual ETL/ZETA comparison. Five discovery tests in `judo-tatami-base` duplicate this orchestration by extending `AbstractExternalModelTest` directly. The base class API currently passes only the source model to `executeEtl`/`executeZeta`, making config-dependent tests (dialect, behaviours) awkward.

## Goals / Non-Goals

**Goals:**
- Fix `AbstractDualComparisonTest` API to pass `ExternalModelConfig` to execute methods
- Migrate all 5 discovery comparison tests to use `AbstractDualComparisonTest`
- Maintain identical test behavior (same models discovered, same comparison logic)

**Non-Goals:**
- Migrating tests in other repos (`judo-tatami-jsl`, `judo-tatami-client`) — separate changes
- Migrating non-discovery tests (`Psm2AsmDualTransformationTest`, `AbstractDualTransformationTest`, `Asm2RdbmsInheritanceTest`)
- Changing the `assertDualEquivalent` convenience methods (they pass `null` config, which is fine)

## Decisions

### 1. Add `ExternalModelConfig` parameter to abstract methods

Change signatures from:
```java
protected abstract T executeEtl(S source) throws Exception;
protected abstract T executeZeta(S source) throws Exception;
```
to:
```java
protected abstract T executeEtl(S source, ExternalModelConfig config) throws Exception;
protected abstract T executeZeta(S source, ExternalModelConfig config) throws Exception;
```

**Rationale:** 5 of 9 consumer tests across all repos need config parameters. Passing config explicitly is cleaner than field-stashing. Since the base class is new with no published consumers yet, now is the time.

**Alternative considered:** Keep current API, use `parseSource` to stash config in a field. Rejected because it's error-prone (shared mutable state, not thread-safe) and would need to be documented as a pattern.

### 2. `assertDualEquivalent` passes null config

The inline `@Test` convenience methods don't have an `ExternalModelConfig`. They pass `null`:
```java
protected void assertDualEquivalent(S source, String testName) throws Exception {
    T etlResult = executeEtl(source, null);
    T zetaResult = executeZeta(source, null);
    ...
}
```

Subclasses that use `assertDualEquivalent` must handle `config == null` in their `executeEtl`/`executeZeta`. In practice, inline tests don't need config parameters (they construct sources programmatically).

### 3. Migration order: simple first, config-dependent last

1. `AbstractDualComparisonTest` API change (prerequisite)
2. `Psm2MeasureDiscoveryComparisonTest` (simplest, no config params)
3. `Psm2AsmDiscoveryComparisonTest` (simple, no config params)
4. `Asm2KeycloakDiscoveryComparisonTest` (simple, no config params)
5. `Asm2RdbmsDiscoveryComparisonTest` (needs dialect from config)
6. `Rdbms2LiquibaseDiscoveryComparisonTest` (needs dialect from config)

## Risks / Trade-offs

- **[Risk] Behavioral regression** → Each migrated test is verified by running it against the same external models. The `@TestFactory` output and comparison results must match.
- **[Risk] External models not available in CI** → Tests use `Assumptions.assumeTrue` to skip when models aren't found. This is preserved in the base class.
- **[Trade-off] Null config in assertDualEquivalent** → Acceptable because inline tests construct sources directly and never need config. Documented in Javadoc.
