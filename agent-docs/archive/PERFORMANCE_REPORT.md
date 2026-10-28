# Performance Analysis Report: PSM2ASM Transformation

**Date:** 2026-01-14
**Model:** RackInspect (4MB PSM, 22,370 elements)
**Test:** Psm2AsmExternalModelTest

---

## Executive Summary

| Metric | ETL | ZETA | Improvement |
|--------|-----|------|-------------|
| **Total Time** | 41,925ms | 4,584ms | **9.15x faster** |
| **Throughput** | 534 elem/s | 4,880 elem/s | **9.1x higher** |
| **Classifiers Produced** | 1,284 | 1,284 | Identical |
| **Model Equivalence** | ✅ | ✅ | Verified |

**Conclusion:** ZETA is production-ready and significantly outperforms ETL.

---

## ZETA Performance Breakdown

### Time Distribution (4,099ms total)

| Component | Time | Percentage | Status |
|-----------|------|------------|--------|
| **Cache ops (exclusive)** | 3,646ms | **88.9%** | ⚠️ **BOTTLENECK** |
| Greedy rule execution | 263ms | 6.4% | ✅ Acceptable |
| Model iteration | 159ms | 3.9% | ✅ Good |
| equivalent() total | 67ms | 1.6% | ✅ Excellent |
| createTarget() | 55ms | 1.3% | ✅ Excellent |
| Rule matching | 4ms | 0.1% | ✅ Excellent |

### Cache Performance

| Metric | Value |
|--------|-------|
| equivalent() calls | 39,283 |
| Cache hits | 36,073 (91.8%) |
| Cache misses | 3,211 (8.2%) |
| createTarget() calls | 30,012 |
| Rule iterations | 20,765 |

**Cache hit rate of 91.8% is excellent**, but cache operations themselves are slow.

---

## Identified Bottleneck: Cache Operations

### Problem Analysis

The metrics show that **88.9% of transformation time is spent in cache operations**. This is unexpected given the high cache hit rate (91.8%).

After analyzing `ElementResolutionCache.java`, the root causes are:

1. **CacheKey Object Allocation** - Every `getOrCreate()` call creates a new `CacheKey` object
2. **Double CacheKey Creation** - `getOrCreate()` creates CacheKey twice (line 211 and via `getLockFor` at line 224)
3. **System.identityHashCode() Calls** - Each CacheKey constructor calls `System.identityHashCode(source)`
4. **Objects.hash() Overhead** - CacheKey.hashCode() calls `Objects.hash()` which creates varargs array
5. **ConcurrentHashMap Memory Barriers** - Even for cache hits, CHM has memory barrier overhead
6. **Lock Allocation** - `getLockFor()` uses `computeIfAbsent()` which can allocate even on hits

### Code Evidence (ElementResolutionCache.java)

```java
// Line 211 - First CacheKey allocation
CacheKey key = new CacheKey(source, ruleName);

// Line 224 - Second CacheKey allocation (inside getLockFor)
ReentrantLock lock = getLockFor(source, ruleName);

// CacheKey constructor - expensive operations
CacheKey(EObject source, String ruleName) {
    this.sourceIdentity = System.identityHashCode(source);  // Native call
    this.ruleName = ruleName;
}

@Override
public int hashCode() {
    return Objects.hash(sourceIdentity, ruleName);  // Creates Object[] array
}
```

### Metrics Evidence

```
Cache ops (exclusive):          3,646 ms ( 88.9%)
Lock acquisitions:              3,210
Lock wait:                      1 ms (2.8% of equivalent() time)
createTarget() calls:           30,012
```

Lock wait time is minimal (1ms), confirming the issue is **object allocation and hash computation overhead**, not contention.

---

## Top 10 Slowest Rules

### Lazy Rules (via equivalent())

| Rule | Time | Calls | Avg/call |
|------|------|-------|----------|
| CreateUnmappedTransferObjectTypeClass | 11ms | 691 | 0.017ms |
| CreateBoundTransferOperation | 7ms | 960 | 0.008ms |
| CreateBoundOperation | 3ms | 900 | 0.004ms |
| CreateEntityClass | 3ms | 70 | 0.047ms |
| CreateMappedTransferObjectTypeClass | 2ms | 172 | 0.016ms |

### Greedy Rules

| Rule | Time | Calls | Avg/call |
|------|------|-------|----------|
| CreateTransferObjectTypeAnnotationClass | 53ms | 864 | 0.062ms |
| CreateTransferObjectRelation | 23ms | 2,238 | 0.010ms |
| CreateTransferObjectRelationPermissions | 14ms | 2,238 | 0.006ms |
| CreateOutputParameterName | 12ms | 1,014 | 0.013ms |
| CreateBoundAnnotationForTransferOperation | 11ms | 1,259 | 0.009ms |

**All rules are fast** - the bottleneck is not in rule execution.

---

## Recommendations for Improvement

### Priority 1: Optimize Cache Key Operations (High Impact)

**Current state:** 3,646ms (88.9% of total time)
**Target:** <500ms (<15% of total time)
**Potential speedup:** 3-4x overall

#### Option A: Two-Level Cache (Recommended)

Replace `Map<CacheKey, Object>` with `Map<EObject, Map<String, Object>>`:

```java
// Current: Single-level cache with composite key (SLOW)
private final Map<CacheKey, ReentrantLock> ruleLocks = new ConcurrentHashMap<>();

// Proposed: Two-level cache using identity (FAST)
private final Map<EObject, Map<String, ReentrantLock>> ruleLocks =
    new IdentityHashMap<>();

// getOrCreate fast path:
public <T extends EObject> T getOrCreate(EObject source, String ruleName, ...) {
    // Fast path: no CacheKey allocation
    Map<String, EObject> ruleMap = ruleCache.get(source);  // Identity lookup
    if (ruleMap != null) {
        EObject cached = ruleMap.get(ruleName);  // String.equals() is fast
        if (cached != null) return (T) cached;
    }
    // ... slow path with locking
}
```

Benefits:
- Eliminates CacheKey object allocation (saves ~60 bytes per call)
- Uses identity comparison for EObject (no hashCode needed)
- String.equals() is highly optimized by JVM

#### Option B: Cache hashCode in CacheKey

```java
private static class CacheKey {
    private final int sourceIdentity;
    private final String ruleName;
    private final int cachedHash;  // Pre-computed

    CacheKey(EObject source, String ruleName) {
        this.sourceIdentity = System.identityHashCode(source);
        this.ruleName = ruleName;
        this.cachedHash = 31 * sourceIdentity + ruleName.hashCode();  // Inline
    }

    @Override
    public int hashCode() {
        return cachedHash;  // No allocation
    }
}
```

#### Option C: Use HashMap in Sequential Mode

```java
// In TransformationExecutor constructor:
if (!parallel) {
    // Sequential mode: no need for ConcurrentHashMap overhead
    this.elementCache = new IdentityHashMap<>();
    this.lockingEnabled = false;
} else {
    this.elementCache = new ConcurrentHashMap<>();
    this.lockingEnabled = true;
}
```

#### Option D: Eliminate Double CacheKey Creation

```java
// Current: Creates CacheKey twice
CacheKey key = new CacheKey(source, ruleName);
if (rejectedKeys.contains(key)) return null;
...
ReentrantLock lock = getLockFor(source, ruleName);  // Creates another CacheKey!

// Fix: Reuse the same key
CacheKey key = new CacheKey(source, ruleName);
if (rejectedKeys.contains(key)) return null;
...
ReentrantLock lock = ruleLocks.computeIfAbsent(key, k -> new ReentrantLock());
```

### Priority 2: Profile JVM with async-profiler (Medium Impact)

Run without JaCoCo to enable async-profiler:

```bash
mvn test -pl judo-tatami-psm2asm \
    -Dtest=Psm2AsmExternalModelTest \
    -Pperformance \
    -Djacoco.skip=true \
    -Djudo.test.profiler.enabled=true
```

This will provide CPU flame graphs showing exact hotspots.

### Priority 3: Memory Profiling (Low Impact)

Check for GC pressure:

```bash
mvn test -pl judo-tatami-psm2asm \
    -Dtest=Psm2AsmExternalModelTest \
    -Pperformance \
    "-DargLine=-Xlog:gc*:file=gc.log"
```

---

## Post-Processing Breakdown

| Step | Time | Description |
|------|------|-------------|
| Add root packages | 92ms | Good |
| Set EOpposite | 4ms | Excellent |
| Set TransferObjectRelation types | 10ms | Excellent |
| Reference class inheritance | 5ms | Excellent |
| enrichWithAnnotations | 219ms | Acceptable |
| Fix null eType | 98ms | Good |
| **Total** | 428ms | Good |

Post-processing is well-optimized. No action needed.

---

## Comparison: ETL vs ZETA Architecture

| Aspect | ETL | ZETA |
|--------|-----|------|
| Engine | Epsilon Transformation Language | Custom Java implementation |
| Execution | Interpreted rules | Compiled rules |
| Caching | Limited | Aggressive (91.8% hit rate) |
| Parallelism | None | Optional (disabled by default) |
| Metrics | None | Comprehensive built-in |

---

## Action Items

1. **[HIGH]** Investigate cache implementation in `TransformationExecutor`
   - Profile cache key hashCode/equals methods
   - Consider IdentityHashMap for source objects
   - Benchmark HashMap vs ConcurrentHashMap in sequential mode

2. **[MEDIUM]** Run with async-profiler (JaCoCo disabled)
   - Generate flame graph for cache operations
   - Identify exact hotspot methods

3. **[LOW]** Add cache statistics to metrics
   - Track time spent in hashCode/equals
   - Track cache resize events
   - Track memory usage

---

## Conclusion

ZETA delivers **9.15x performance improvement** over ETL while producing identical results. The main optimization opportunity is in **cache operations**, which consume 88.9% of execution time despite a 91.8% cache hit rate. Optimizing the cache implementation could potentially achieve another **3-4x speedup**, bringing ZETA performance to ~1 second for the RackInspect model.
