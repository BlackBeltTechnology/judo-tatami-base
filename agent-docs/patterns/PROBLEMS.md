# Problem Registry - Detailed Documentation

**IMPORTANT: Never delete entries from this registry. Problems may resurface.**

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Know what NOT to try | [FAILED_APPROACHES.md](FAILED_APPROACHES.md) |
| Find working solutions | [SUCCESSFUL_PATTERNS.md](SUCCESSFUL_PATTERNS.md) |
| Profile performance | [PROFILING.md](PROFILING.md) |

---

## Problem Group: Guard Evaluation Overhead

**Status:** ACTIVE - Type filtering working, but 80,695 evaluations still occur

**Related Components:**
- `judo-zeta` framework - `TransformationExecutor`
- `OperationRules.java` - 12 TransferOperation rules (now split to 24)
- `Psm2AsmZetaTransformation.java` - Transformation orchestration

**Metrics:**
```
Guard evaluations:            80,695
Guard evaluation time:        ~3,970 ms (87% of transformation time)
Rejection rate:               96.3%
```

**Root Cause:**
The judo-zeta framework evaluates guards for all registered rules against candidate source elements. Even with type-based filtering (25x reduction from 2M+ to 80k), the guard evaluation overhead dominates transformation time.

**Investigation History:**

| Date | Finding |
|------|---------|
| 2026-01-16 | Profiler identified guard evaluation as 87% of transformation time |
| 2026-01-16 | Type filtering confirmed working (2M → 80k evaluations) |
| 2026-01-17 | Split TransferOperation rules - no improvement (same 80,695) |

**Why Splitting Rules Didn't Help:**
Splitting 12 `TransferOperation` rules into 24 type-specific rules (BTO/UO) redistributes evaluations but doesn't reduce total:
- Before: 12 rules × N TransferOperation elements = 12N evaluations
- After: 12 BTO rules × M elements + 12 UO rules × K elements = 12N (where M+K=N)

**Potential Optimizations (Not Yet Implemented):**
1. Guard result caching in judo-zeta framework
2. Lazy guard evaluation (only when needed)
3. Rule clustering by common guard conditions
4. Pre-computed guard lookup tables

**Current Resolution:**
Accepted as baseline. Performance is still 7.5x faster than ETL.

---

## Problem Group: TransferOperation Rule Optimization

**Status:** COMPLETED - No performance improvement observed

**Related Files:**
- `OperationRules.java` - 12 rules split to 24
- `Psm2AsmRuleNames.java` - 28 new constants added

**Original Hypothesis:**
Splitting abstract `TransferOperation` rules into concrete `BoundTransferOperation` and `UnboundOperation` rules would enable more precise type-based filtering, reducing guard evaluations.

**Changes Made (2026-01-17):**

| Original Rule | Split Into |
|---------------|------------|
| `createBoundAnnotationForTransferOperation` | `createBoundAnnotationForBoundTransferOperation` + `createBoundAnnotationForUnboundOperation` |
| `createOperationPermissions` | `createOperationPermissionsForBoundTransferOperation` + `createOperationPermissionsForUnboundOperation` |
| `createImmutableFlagForTransferOperation` | `createImmutableFlagForBoundTransferOperation` + `createImmutableFlagForUnboundOperation` |
| (9 more rules...) | (18 more split versions...) |

**Test Results:**
```
Before: ZETA 5072ms, Guard evaluations: 80,695, Speedup: 8.0x
After:  ZETA 5078ms, Guard evaluations: 80,695, Speedup: 7.48x
```

**Conclusion:**
No measurable improvement. The optimization adds code complexity without benefit.

**Revert Instructions:**
See commit `5f47d24` for the changes. Revert via:
```bash
git revert 5f47d24
```

---

## Problem Group: Null eType in Extension Types

**Status:** RESOLVED - Fixed in postProcess step 6

**Related Files:**
- `Psm2AsmZetaTransformation.java` - `fixNullAttributeTypes()` method
- Extension types: `_default_`, `_binding_` patterns

**Symptoms:**
- EAttributes in extension transfer object types have `null` eType
- Cross-resource type references not resolved by `ctx.equivalent()`

**Root Cause:**
Extension transfer object types (`_default_`, `_binding_`) have attributes whose `dataType` references don't match the type instances transformed by TypeRules. This causes `ctx.equivalent()` to return null during parallel execution.

**Resolution:**
Post-processing step 6 (`fixNullAttributeTypes()`) fixes these by:
1. Building type lookup map by name
2. Finding EAttributes with null eType in `_default_` / `_binding_` classes
3. Looking up PSM source attribute to get type name
4. Setting eType from the lookup map

**Typical Fix Count:** ~16 attributes per transformation

---

## Problem Group: Post-Processing Issues

**Status:** RESOLVED - All 6 steps working correctly

**Post-Processing Steps:**

| Step | Name | Time | Purpose |
|------|------|------|---------|
| 1 | Add root packages | ~110ms | Add EPackages to ASM resource, apply XMI IDs |
| 2 | Set EOpposite | ~5ms | Bidirectional associations |
| 3 | Set TransferObjectRelation types | ~10ms | EReference.eType for relations |
| 4 | Reference class inheritance | ~5ms | `__Reference` class supertypes |
| 5 | enrichWithAnnotations | ~239ms | exposedBy, etc. |
| 6 | Fix null eType | ~108ms | Extension type fixup |

**Why Post-Processing is Needed:**
These operations require **all elements to exist first** because they set cross-references between elements created by different rules during parallel execution.

---

## Problem Group: EMF Iterator Overhead

**Status:** ACTIVE - Identified but not addressed

**Profiler Findings:**
```
Top CPU Hotspots:
1. EContentsEList$FeatureIteratorImpl.hasNext - EMF traversal
2. Guard evaluation - ZETA framework
3. ElementResolutionCache lookups
```

**Root Cause:**
EMF's model traversal (`eAllContents()`, feature iteration) is CPU-intensive due to reflection-based access patterns.

**Potential Optimizations:**
1. Cache traversal results
2. Pre-index elements by type at transformation start
3. Use parallel streams for large collections

**Current Resolution:**
Accepted as EMF overhead. Not specific to ZETA.

---

## Template for New Problem Groups

```markdown
### Problem Group: [Descriptive Name]

**Status:** ACTIVE | RESOLVED | RECURRING

**Related Files:**
- [File 1]
- [File 2]

**Symptoms:**
- [Observable behavior 1]
- [Observable behavior 2]

**Root Cause:**
[Explanation of why the problem occurs]

**Solutions Tried:**
| Solution | Outcome | Side Effects |
|----------|---------|--------------|
| [Solution 1] | [Result] | [Any regressions] |

**Current Resolution:**
[What's working now, or "Not resolved"]
```
