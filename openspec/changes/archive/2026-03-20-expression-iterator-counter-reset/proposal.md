## Why

The asm2expression transformation generates iterator variable names using a cumulative `AtomicInteger` counter (`_iterator_1`, `_iterator_2`, ...) that increments across all expressions in a single extraction run. When ETL and Zeta psm2asm transformations produce ASM models with different element ordering, the expression extractor processes expressions in a different order, causing the counter to land on different values for the same expression (e.g., `_iterator_61` vs `_iterator_14`). This is the last remaining source of comparison failures between ETL and Zeta.

## What Changes

- **`judo-meta-expression` (separate repo)**: Call `builder.resetIteratorCounter()` before each independent `createExpression()` call in `AsmJqlExtractor` / `AdaptableJqlExtractor`. Each expression starts with `_iterator_1` regardless of processing order.
- Both ETL and Zeta use the same `AsmJqlExtractor`, so the fix applies to both automatically.

## Capabilities

### New Capabilities
- `expression-iterator-determinism`: Deterministic iterator variable names in expression model, independent of ASM element processing order

### Modified Capabilities
_(none)_

## Impact

- **judo-meta-expression** (`AsmJqlExtractor` or `AdaptableJqlExtractor`): Add `resetIteratorCounter()` calls before each `createExpression()`
- **judo-tatami-base**: No code changes — but comparison tests should pass fully after the judo-meta-expression fix is released
- **Risk**: Iterator name collisions within the same expression scope. Mitigated because each expression is self-contained (lambda variables are scoped per-expression).
