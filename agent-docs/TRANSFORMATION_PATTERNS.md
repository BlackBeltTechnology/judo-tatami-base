# ZETA Transformation Patterns Documentation

This document captures learnings about ZETA transformation patterns for PSM2ASM. **Always check here before attempting fixes.**

---

## What Are You Trying To Do?

### Investigating a Problem?
| If you see... | Read this |
|---------------|-----------|
| Performance issues (slow transformation) | [Problems → Performance](patterns/PROBLEMS.md#problem-group-performance-bottlenecks) |
| Guard evaluation overhead | [Problems → Guard Evaluation](patterns/PROBLEMS.md#problem-group-guard-evaluation-overhead) |
| Post-processing failures | [Problems → Post-Processing](patterns/PROBLEMS.md#problem-group-post-processing-issues) |
| Test assertion failures | [Problems](patterns/PROBLEMS.md) |
| ZETA output differs from ETL | [ETL vs ZETA Differences](patterns/ETL_VS_ZETA_DIFFERENCES.md) |

### Implementing a Fix?
| If you need to... | Read this |
|-------------------|-----------|
| Know what NOT to try | [Failed Approaches](patterns/FAILED_APPROACHES.md) |
| Find proven solutions | [Successful Patterns](patterns/SUCCESSFUL_PATTERNS.md) |
| Run tests correctly | [Testing Guide](patterns/TESTING.md) |
| Profile performance | [Performance Profiling](patterns/PROFILING.md) |

### Understanding the Codebase?
| If you want to... | Read this |
|-------------------|-----------|
| Understand rule architecture | [Rule Architecture](patterns/RULE_ARCHITECTURE.md) |
| Learn ZETA framework | [ZETA.md](ZETA.md) |
| See post-processing steps | [Post-Processing](patterns/POST_PROCESSING.md) |

---

## Quick Reference: Problem Status

| Problem | Status | Details |
|---------|--------|---------|
| Guard Evaluation Overhead | ACTIVE | 80,695 evaluations, type filtering working. [Details](patterns/PROBLEMS.md#problem-group-guard-evaluation-overhead) |
| TransferOperation Rule Split | COMPLETED | 12 rules split to BTO/UO variants, no perf improvement. [Details](patterns/PROBLEMS.md#problem-group-transferoperation-rule-optimization) |
| Post-Processing eType Fixup | RESOLVED | Extension types fixed in step 6. [Details](patterns/PROBLEMS.md#problem-group-null-etype-in-extension-types) |

---

## Quick Reference: Performance Metrics

| Metric | Value | Notes |
|--------|-------|-------|
| ZETA vs ETL Speedup | ~7.5x | Consistent across test runs |
| Guard Evaluations | 80,695 | After type filtering (was 2M+) |
| Post-Processing Time | ~479ms | 6 steps total |
| Transformation Time | ~5000ms | For rackinspect model (22,370 elements) |

---

## Quick Test Commands

```bash
# Phase 1: Compile check
mvn compile -pl judo-tatami-psm2asm -q

# Phase 2: Run benchmark with performance profile
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest -Pperformance

# Phase 3: Strict mode with XMI ID comparison
mvn test -pl judo-tatami-psm2asm -Dtest=Psm2AsmExternalModelTest \
    -Djudo.test.comparison.mode=STRICT \
    -Djudo.test.comparison.xmiIds=true \
    -Pperformance
```

---

## Key Rules

1. **Check TRANSFORMATION_PATTERNS.md first** - problem/solution may be documented
2. **Run benchmark after changes** - verify no regression
3. **Use strict XMI ID comparison** - simple tests are not enough
4. **Document all findings** - successes AND failures
5. **DO NOT commit automatically** - wait for user review

---

## Archived Documentation

| File | Purpose | Resolution |
|------|---------|------------|
| `ZETA_OPTIMIZATION_PLAN.md` | Optimization plan for judo-zeta | Type filtering implemented |

---

## Version History

| Date | Author | Changes |
|------|--------|---------|
| 2026-01-16 | Claude | Initial performance analysis with profiler |
| 2026-01-16 | Claude | Created ZETA_OPTIMIZATION_PLAN.md |
| 2026-01-17 | Claude | Split TransferOperation rules (no perf improvement) |
| 2026-01-17 | Claude | Restructured to agent-docs pattern |
