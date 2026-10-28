# Failed Approaches - PSM2ASM ZETA Transformation

**IMPORTANT: Never delete entries. Read this BEFORE attempting optimizations.**

## Need Something Else?

| If you need to... | Go to |
|-------------------|-------|
| Understand problems | [PROBLEMS.md](PROBLEMS.md) |
| Find working solutions | [SUCCESSFUL_PATTERNS.md](SUCCESSFUL_PATTERNS.md) |
| Profile performance | [PROFILING.md](PROFILING.md) |

---

## Failed Approach: Splitting TransferOperation Rules

**Date:** 2026-01-17

**Hypothesis:**
Splitting 12 abstract `TransferOperation` rules into 24 type-specific rules (`BoundTransferOperation` + `UnboundOperation`) would reduce guard evaluations through more precise type filtering.

**Changes Made:**

| Original Rule | Split Into |
|---------------|------------|
| `createBoundAnnotationForTransferOperation` | `...ForBoundTransferOperation` + `...ForUnboundOperation` |
| `createOperationPermissions` | `...ForBoundTransferOperation` + `...ForUnboundOperation` |
| `createImmutableFlagForTransferOperation` | `...ForBoundTransferOperation` + `...ForUnboundOperation` |
| (9 more rules...) | (18 more split versions...) |

Files Modified:
- `OperationRules.java` - Split 12 rules to 24
- `Psm2AsmRuleNames.java` - Added 28 new constants

**Results:**
```
Before: ZETA 5072ms, Guard evaluations: 80,695, Speedup: 8.0x
After:  ZETA 5078ms, Guard evaluations: 80,695, Speedup: 7.48x
```

**Why It Failed:**
Splitting rules redistributes evaluations but doesn't reduce total:
- Before: 12 rules × N TransferOperation elements = 12N evaluations
- After: 12 BTO rules × M elements + 12 UO rules × K elements = 12N (where M+K=N)

The guard evaluation overhead comes from the *total* number of rule-element pairs, not from any individual rule's performance.

**Revert Instructions:**
```bash
git revert 5f47d24  # or the relevant commit
```

**Lesson Learned:**
Type-based filtering only helps when it reduces the total number of rule-element pairs evaluated. Splitting rules while keeping the same source elements doesn't help.

---

## Failed Approach: Exhaustive Guard Optimization

**Date:** 2026-01-16

**Hypothesis:**
Optimizing individual guard conditions would significantly reduce guard evaluation overhead.

**What Was Tried:**
- Analyzed guard conditions for redundancy
- Looked for expensive operations in guards
- Considered guard result caching

**Results:**
Guards are already simple boolean checks. The overhead is not from guard complexity but from guard *quantity* (80,695 evaluations).

**Why It Failed:**
Each guard evaluation is fast (~0.05ms average), but the sheer number (80,695) accumulates to ~4 seconds. The solution needs to reduce evaluation count, not individual evaluation time.

**Lesson Learned:**
Focus on reducing guard evaluation count, not optimizing individual guards.

---

## Template for New Failed Approaches

```markdown
## Failed Approach: [Descriptive Name]

**Date:** YYYY-MM-DD

**Hypothesis:**
[What did you expect to happen?]

**Changes Made:**
[What was modified?]

**Results:**
[What actually happened? Include metrics.]

**Why It Failed:**
[Root cause analysis]

**Revert Instructions:**
[How to undo the changes]

**Lesson Learned:**
[What should future developers know?]
```
