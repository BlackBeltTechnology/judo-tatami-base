# Fix Parallel Transformation Issues

## Summary

Fix three parallel execution issues discovered during Zeta transformation benchmarking:

1. **PSM2Measure ordering**: Test flakiness due to non-deterministic comparison in ModelComparator
2. **RDBMS2Liquibase changeSet**: Race conditions in parallel execution causing inconsistent changeSet counts
3. **ASM2RDBMS parallel**: EMF thread safety issues (already fixed by disabling parallel)

## Motivation

- **Reliability**: Flaky tests reduce confidence in transformation correctness
- **Performance**: ASM2RDBMS and RDBMS2Liquibase cannot use parallel execution due to race conditions
- **Correctness**: All transformations now produce equivalent output to ETL reference

## Prerequisites

- judo-zeta 1.0.0-SNAPSHOT with parallel execution support
- Understanding of EMF resource/container thread safety constraints

## Issue Analysis (Investigation Results)

### 1. PSM2Measure Non-Deterministic Ordering

**Initial Hypothesis**: `psmUtils.all()` returns elements in non-deterministic order

**Actual Root Cause**: `ModelComparator.java` used `HashSet` at lines 551 and 574 for collecting keys during comparison. HashSet iteration order is non-deterministic, causing different comparison results across runs.

**Evidence**: Test failures showed different first measures across runs (AmountOfSubstance, Time, Mass)

**Solution Implemented**:
1. Changed `HashSet` to `LinkedHashSet` at lines 551 and 574 for deterministic iteration
2. Added `compare(Resource, Resource)` method for order-independent comparison of root elements
3. Updated `Psm2MeasureExternalModelTest` to use Resource-level comparison

**Result**: Test passes consistently (verified with 3 consecutive runs)

### 2. RDBMS2Liquibase Extra ChangeSet

**Initial Hypothesis**: Zeta produces 1 extra changeSet consistently (232 vs 231)

**Actual Root Cause**: Parallel execution (line 127: `.parallel(true)`) causes race conditions in changeSet creation, despite using ConcurrentHashMap and synchronized methods. Tests produced varying counts:
- Run 1: 231 changeSets (PASS)
- Run 2: 233 changeSets (FAIL)
- Run 3: 232 changeSets (FAIL)
- Run 4: 231 changeSets (PASS)

**Solution Implemented**: Disabled parallel execution by changing line 127 from `.parallel(true)` to `.parallel(false)` with explanatory comment.

**Result**: Test passes consistently (verified with 3 consecutive runs, always 231 changeSets)

### 3. ASM2RDBMS Parallel Execution NPE

**Initial Hypothesis**: EMF thread safety issues in postProcess prevent parallel execution

**Actual Finding**: Parallel execution was already disabled at line 173 with comment explaining the issue. No additional fix required.

**Result**: Test passes (13.53x faster than ETL, EQUIVALENT status)

## Why Parallel Execution Fails

### Root Cause: EMF is NOT Thread-Safe

EMF (Eclipse Modeling Framework) collections (`EList`) and internal iterators are not designed for concurrent access. When multiple threads modify EMF model elements simultaneously, race conditions occur.

### Comparison: Why PSM2ASM Works vs Others Fail

#### PSM2ASM (Parallel WORKS)

**Pattern Used**: Fine-grained synchronized helpers in `Psm2AsmHelper.java`

```java
public static void addClassifier(EPackage pkg, EClassifier classifier) {
    if (pkg != null && classifier != null) {
        synchronized (pkg) {  // Lock on the specific EMF object
            // Check for duplicates (race condition prevention)
            boolean exists = pkg.getEClassifiers().stream()
                    .anyMatch(c -> name.equals(c.getName()));
            if (exists) return;
            pkg.getEClassifiers().add(classifier);
        }
    }
}
```

**Key Features**:
1. `synchronized(object)` on each EMF element being modified
2. Duplicate detection inside the synchronized block
3. ConcurrentHashMap for caches
4. All EList modifications go through synchronized helpers

#### RDBMS2Liquibase (Parallel FAILS)

**Problem**: `getOrCreateChangeSet()` uses `ConcurrentHashMap.computeIfAbsent()` but:

| Issue | Explanation |
|-------|-------------|
| Race in lambda | Multiple threads can enter the lambda before first completes |
| Global lock contention | `synchronized` method locks ALL additions, but doesn't protect EMF tree |
| EMF iterator invalidation | While one thread adds to EList, another's iterator may be traversing it |
| Non-atomic check-then-act | Cache check and EList add are not atomic together |

#### ASM2RDBMS (Parallel FAILS)

**Problem**: `postProcess()` calls `context.addToResource(model)` which triggers EMF's `attached()` method.

```
java.lang.NullPointerException: Cannot invoke "InternalEObject.eDirectResource()"
  because "this.preparedResult" is null
  at EcoreUtil$ProperContentIterator.hasNext(EcoreUtil.java:1369)
  at ResourceImpl.attached(ResourceImpl.java:891)
```

| Issue | Explanation |
|-------|-------------|
| Resource attachment not synchronized | `addToResource()` triggers EMF's internal `attached()` method |
| EMF iterator corruption | `ProperContentIterator` has internal state that becomes null |
| Concurrent tree traversal | Adding to resource iterates containment tree; concurrent modification causes NPE |

## Future Work: Enabling Parallel Execution

To enable parallel execution for ASM2RDBMS and RDBMS2Liquibase, the following changes would be needed:

### Option 1: Synchronized Helpers (Local Fix)

Follow PSM2ASM pattern - create helper classes with synchronized methods for all EList modifications:

```java
public static void addField(RdbmsTable table, RdbmsField field) {
    if (table != null && field != null) {
        synchronized (table) {
            // Check for duplicates
            boolean exists = table.getFields().stream()
                    .anyMatch(f -> field.getName().equals(f.getName()));
            if (exists) return;
            table.getFields().add(field);
        }
    }
}
```

**Pros**: No framework changes needed
**Cons**: Requires refactoring all EList access points in each transformation

### Option 2: Framework-Level Synchronization (judo-zeta)

Add thread-safe resource attachment in `TransformationContext`:

```java
public synchronized void addToResource(EObject element) {
    if (element != null && !targetResource.getContents().contains(element)) {
        targetResource.getContents().add(element);
    }
}
```

**Pros**: One-time fix benefits all transformations
**Cons**: Requires judo-zeta release

### Option 3: Deferred Resource Attachment

Collect all elements during parallel execution, then attach sequentially in postProcess:

```java
// During parallel execution - just store in thread-safe collection
private final Set<EObject> pendingElements = ConcurrentHashMap.newKeySet();

// In postProcess - sequential attachment
pendingElements.forEach(element -> context.addToResource(element));
```

**Pros**: Clear separation of parallel/sequential phases
**Cons**: Requires refactoring transformation architecture

### Recommendation

**Short-term**: Keep parallel disabled (current state) - transformations are already 13-40x faster than ETL

**Long-term**: Implement Option 2 (framework-level fix) in judo-zeta for consistent thread-safe behavior across all transformations

## Final Benchmark Results

All 5 transformations now pass:

| Transformation  | ETL Time | Zeta Time | Speedup | Parallel | Status |
|-----------------|----------|-----------|---------|----------|--------|
| PSM2ASM         | 47,704ms | 10,296ms  | 4.64x   | Yes      | PASS   |
| PSM2Measure     | 1,731ms  | 841ms     | 2.06x   | Yes      | PASS   |
| ASM2RDBMS       | 4,728ms  | 307ms     | 15.39x  | No       | PASS   |
| RDBMS2Liquibase | 4,653ms  | 117ms     | 39.77x  | No       | PASS   |
| ASM2Keycloak    | 1,389ms  | 106ms     | 13.11x  | Yes      | PASS   |

## Scope

### In Scope
- Fix ModelComparator to use deterministic iteration order
- Add Resource-level comparison for order-independent matching
- Disable RDBMS2Liquibase parallel execution to prevent race conditions
- Document requirements for future parallel execution enablement

### Out of Scope
- Implementing parallel execution fixes for RDBMS2Liquibase and ASM2RDBMS (future work)
- Changes to judo-zeta framework
- Changes to ETL implementations

## Success Criteria

1. **ACHIEVED**: PSM2Measure external model test passes consistently
2. **ACHIEVED**: RDBMS2Liquibase produces exactly 231 changeSets matching ETL output
3. **ACHIEVED**: All 5 transformation tests pass with EQUIVALENT status
