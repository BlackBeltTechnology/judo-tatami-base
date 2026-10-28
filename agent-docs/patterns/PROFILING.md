# Performance Profiling Guide - PSM2ASM ZETA Transformation

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Understand problems | [PROBLEMS.md](PROBLEMS.md) |
| Find working solutions | [SUCCESSFUL_PATTERNS.md](SUCCESSFUL_PATTERNS.md) |
| Run tests correctly | [TESTING.md](TESTING.md) |

---

## Profiling Infrastructure

### JVM Profiler Integration

The `judo-tatami-test-utils` module provides a `Profiler` class for CPU and memory profiling.

**Location:** `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/profiler/`

### Enabling Profiling in Tests

```java
import hu.blackbelt.judo.tatami.test.profiler.Profiler;

// Start profiling before transformation
Profiler.startProfiling("my_profile");

// Run transformation
runZetaTransformation();

// Stop and save results
Profiler.stopProfiling("target/profiler-output/my_profile.txt");
```

---

## Running Profiled Tests

### With Performance Profile
```bash
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance
```

### ZETA-Only Profiling (CRITICAL)

**Always use ZETA transformation mode when profiling** to avoid polluting JVM metrics:

```bash
# Recommended: Profile ZETA transformation only
mvn test -Djudo.test.transformation.mode=ZETA -Pperformance

# This produces clean flamegraphs without ETL noise
```

**Why this matters:**

Running in DUAL mode (default) will pollute your profiler output with:
- Epsilon ETL engine initialization (~200ms)
- EOL/ETL script parsing and interpretation
- Epsilon context creation and management
- Reflection-heavy script execution

These ETL artifacts appear in flamegraphs and CPU samples, making it impossible to accurately identify ZETA bottlenecks.

### Transformation Mode Options

| Mode | Command | Use Case |
|------|---------|----------|
| ZETA only | `-Djudo.test.transformation.mode=ZETA` | **Profiling (recommended)**, production |
| ETL only | `-Djudo.test.transformation.mode=ETL` | Legacy testing, debugging |
| Both | `-Djudo.test.transformation.mode=DUAL` | Validation, parity testing |

---

## Profiler Output Analysis

### Output Location
```
judo-tatami-psm2asm/target/profiler-output/rackinspect_zeta_only.txt
```

### Sample Output Structure
```
=== CPU Profiling Results ===
Top Hotspots:
1. EContentsEList$FeatureIteratorImpl.hasNext - EMF traversal
2. Guard evaluation - ZETA framework
3. ElementResolutionCache lookups

=== Memory Allocation ===
Total allocated: XXX MB
Top allocators: [list of classes]
```

---

## Known Hotspots

### 1. Guard Evaluation (87% of transformation time)
- **Cause:** 80,695 guard evaluations with 96% rejection rate
- **Location:** ZETA TransformationExecutor
- **Status:** Type filtering reduces from 2M+ to 80k, but still dominant

### 2. EMF Iterator Overhead
- **Cause:** Reflection-based model traversal
- **Location:** `EContentsEList$FeatureIteratorImpl.hasNext`
- **Status:** EMF baseline overhead, not ZETA-specific

### 3. ElementResolutionCache
- **Cause:** Cache lookups during transformation
- **Status:** Required for correctness

---

## Performance Baseline

| Metric | Value | Notes |
|--------|-------|-------|
| Total ZETA time | ~5000ms | rackinspect model |
| Guard evaluation | ~3970ms | 87% of total |
| Post-processing | ~479ms | 6 steps |
| Rule execution | ~551ms | Actual transformations |

---

## Optimization Opportunities

### Potential (Not Implemented)

1. **Guard Result Caching** - Cache guard results in judo-zeta framework
2. **Lazy Guard Evaluation** - Only evaluate when needed
3. **Rule Clustering** - Group rules by common guard conditions
4. **Pre-computed Lookup Tables** - Index elements by guard predicates

### Why Not Implemented
These require changes to the judo-zeta framework, not judo-tatami-base.

---

## Profiling Checklist

Before analyzing performance:
- [ ] Run baseline benchmark first
- [ ] Use `-Pperformance` Maven profile
- [ ] Profile ZETA transformation only (not ETL)
- [ ] Record guard evaluation count
- [ ] Check post-processing timings
- [ ] Compare against baseline metrics in [PROBLEMS.md](PROBLEMS.md)
